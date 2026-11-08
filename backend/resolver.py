import json
import os
import re
import subprocess
import urllib.parse
import urllib.request
from typing import Iterator

import yt_dlp
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

app = FastAPI(title="Media HUB Resolver", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

ALLOWED_HOST_SUFFIXES = (
    "youtube.com", "youtu.be",
    "facebook.com", "fb.com", "fb.watch",
    "instagram.com",
    "threads.net", "threads.com",
    "tiktok.com", "x.com", "twitter.com",
    "reddit.com", "redd.it", "pinterest.com", "pin.it",
)

def validate_url(raw: str) -> str:
    try:
        parsed = urllib.parse.urlparse(raw.strip())
        host = (parsed.hostname or "").lower()
    except Exception:
        raise HTTPException(400, "Invalid URL")
    if parsed.scheme not in ("http", "https") or not host:
        raise HTTPException(400, "Please provide a valid public http/https URL")
    if not any(host == d or host.endswith("." + d) for d in ALLOWED_HOST_SUFFIXES):
        raise HTTPException(400, "This platform is not supported yet")
    return raw.strip()

def resolve_redirect(raw: str) -> str:
    """Follow public share-link redirects before handing the URL to yt-dlp."""
    request = urllib.request.Request(
        raw,
        headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
            "Range": "bytes=0-0",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            final_url = response.geturl()
            response.read(1)
        return validate_url(final_url)
    except HTTPException:
        raise
    except Exception:
        # Some platforms reject range probes; let yt-dlp try the original URL.
        return raw


def ydl_options():
    return {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "extractor_retries": 2,
        "fragment_retries": 2,
        "retries": 2,
        "socket_timeout": 15,
        # Prefer public/embed-capable clients that do not require account cookies.
        # YouTube may still reject cloud-hosted IPs; this is a best-effort fallback.
        "extractor_args": {"youtube": {"player_client": ["web_embedded", "android_vr"]}},
    }

@app.get("/health")
def health():
    return {"ok": True, "service": "Media HUB Resolver"}

@app.get("/info")
def info(url: str = Query(..., min_length=8, max_length=2048)):
    url = resolve_redirect(validate_url(url))
    try:
        with yt_dlp.YoutubeDL(ydl_options()) as ydl:
            data = ydl.extract_info(url, download=False)
        if not data:
            raise HTTPException(422, "Could not read this public video")
        formats = []
        for f in data.get("formats", []):
            if f.get("url") and f.get("vcodec") != "none":
                formats.append({
                    "format_id": f.get("format_id"),
                    "ext": f.get("ext"),
                    "height": f.get("height"),
                    "filesize": f.get("filesize") or f.get("filesize_approx"),
                })
        return {
            "ok": True,
            "title": data.get("title") or "Video",
            "thumbnail": data.get("thumbnail"),
            "duration": data.get("duration"),
            "extractor": data.get("extractor_key"),
            "formats": formats[:30],
            "download_url": "/download?url=" + urllib.parse.quote(url, safe=""),
        }
    except HTTPException:
        raise
    except Exception as e:
        message = str(e).splitlines()[0][:240]
        raise HTTPException(422, "Video could not be resolved. It may be private, login-only, restricted, unsupported, or temporarily unavailable. " + message)

@app.get("/download")
def download(url: str = Query(..., min_length=8, max_length=2048)):
    url = resolve_redirect(validate_url(url))
    # Resolve first so errors are returned before a download response starts.
    try:
        with yt_dlp.YoutubeDL(ydl_options()) as ydl:
            data = ydl.extract_info(url, download=False)
        title = data.get("title") or "Media-HUB-video"
    except Exception as e:
        message = str(e).splitlines()[0][:180]
        raise HTTPException(422, "Cannot download this video. Check that it is public and accessible, then retry. " + message)

    safe_title = re.sub(r"[^A-Za-z0-9._ -]", "_", title).strip()[:90] or "Media-HUB-video"
    command = [
        "yt-dlp", "--quiet", "--no-warnings", "--no-progress", "--no-playlist",
        "-f", "best[ext=mp4]/best",
        "-o", "-", "--socket-timeout", "20", url,
    ]
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    except Exception:
        raise HTTPException(500, "Download engine could not start")

    def stream() -> Iterator[bytes]:
        try:
            assert process.stdout is not None
            while True:
                chunk = process.stdout.read(1024 * 256)
                if not chunk:
                    break
                yield chunk
            process.wait()
        finally:
            if process.poll() is None:
                process.kill()
            if process.stdout:
                process.stdout.close()

    headers = {
        "Content-Disposition": 'attachment; filename="' + safe_title.replace('"', "") + '.mp4"',
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
    }
    return StreamingResponse(stream(), media_type="video/mp4", headers=headers)
