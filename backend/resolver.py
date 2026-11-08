import os
import re
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path

import yt_dlp
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

app = FastAPI(title="Media HUB Resolver", version="1.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

ALLOWED_HOST_SUFFIXES = (
    "youtube.com", "youtu.be", "facebook.com", "fb.com", "fb.watch",
    "instagram.com", "threads.net", "threads.com", "tiktok.com",
    "x.com", "twitter.com", "reddit.com", "redd.it", "pinterest.com", "pin.it",
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
        "socket_timeout": 20,
        "extractor_args": {"youtube": {"player_client": ["web_embedded", "android_vr"]}},
    }


def safe_filename(title: str) -> str:
    name = re.sub(r"[^A-Za-z0-9._ -]", "_", title).strip(" ._")[:90]
    return name or "Mint-Mind-video"


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

        # Offer only progressive formats (video + audio in one file). This avoids
        # requiring a server-side ffmpeg merge and ensures the downloaded file is playable.
        formats = []
        seen = set()
        for f in data.get("formats", []):
            if not f.get("url") or f.get("vcodec") == "none" or f.get("acodec") == "none":
                continue
            fmt_id = str(f.get("format_id") or "")
            if not fmt_id or fmt_id in seen:
                continue
            seen.add(fmt_id)
            formats.append({
                "format_id": fmt_id,
                "ext": f.get("ext") or "mp4",
                "height": f.get("height"),
                "filesize": f.get("filesize") or f.get("filesize_approx"),
                "label": (str(f.get("height")) + "p") if f.get("height") else (f.get("ext") or "Video").upper(),
            })
        formats.sort(key=lambda x: (x.get("height") or 0, x["format_id"]), reverse=True)
        if not formats:
            formats = [{"format_id": "best[ext=mp4]/best", "ext": "mp4", "height": None, "filesize": None, "label": "Best available"}]
        return {
            "ok": True,
            "title": data.get("title") or "Video",
            "thumbnail": data.get("thumbnail"),
            "duration": data.get("duration"),
            "extractor": data.get("extractor_key"),
            "formats": formats[:20],
        }
    except HTTPException:
        raise
    except Exception as e:
        message = str(e).splitlines()[0][:220]
        raise HTTPException(422, "Video could not be resolved. It may be private, login-only, restricted, unsupported, or temporarily unavailable. " + message)


@app.get("/download")
def download(
    url: str = Query(..., min_length=8, max_length=2048),
    format_id: str = Query("best[ext=mp4]/best", min_length=1, max_length=80),
):
    url = resolve_redirect(validate_url(url))
    # Format IDs are selected from /info. Reject command-line syntax and arbitrary expressions.
    if format_id != "best[ext=mp4]/best" and not re.fullmatch(r"[A-Za-z0-9._-]{1,40}", format_id):
        raise HTTPException(400, "Invalid video quality selection")

    try:
        with yt_dlp.YoutubeDL(ydl_options()) as ydl:
            data = ydl.extract_info(url, download=False)
        title = safe_filename(data.get("title") or "Mint-Mind-video")
        allowed = {str(f.get("format_id")) for f in data.get("formats", []) if f.get("url") and f.get("vcodec") != "none" and f.get("acodec") != "none"}
        if format_id != "best[ext=mp4]/best" and format_id not in allowed:
            raise HTTPException(400, "That quality is no longer available. Please check the video again.")
    except HTTPException:
        raise
    except Exception as e:
        message = str(e).splitlines()[0][:180]
        raise HTTPException(422, "Cannot download this video. It may be blocked or unavailable. " + message)

    # Download to a temporary file before responding. This prevents returning a
    # successful-looking empty stream when the upstream extractor fails midway.
    tmpdir = tempfile.mkdtemp(prefix="mintmind-")
    output_template = str(Path(tmpdir) / "%(title).80s.%(ext)s")
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "retries": 2,
        "fragment_retries": 2,
        "socket_timeout": 30,
        "format": format_id,
        "outtmpl": output_template,
        "overwrites": True,
        "extractor_args": {"youtube": {"player_client": ["web_embedded", "android_vr"]}},
    }
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            ydl.download([url])
        files = list(Path(tmpdir).glob("*"))
        if not files:
            raise RuntimeError("Downloader did not create an output file")
        path = files[0]
        ext = path.suffix.lower() or ".mp4"
        media_type = "video/mp4" if ext == ".mp4" else "video/" + ext.lstrip(".")
        return FileResponse(
            path,
            media_type=media_type,
            filename=title + ext,
            headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"},
            background=None,
        )
    except HTTPException:
        raise
    except Exception as e:
        import shutil
        shutil.rmtree(tmpdir, ignore_errors=True)
        message = str(e).splitlines()[0][:200]
        raise HTTPException(422, "Download failed. The source may block server downloads or this format may be unavailable. " + message)
