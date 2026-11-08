const BACKEND = "https://media-hub-resolver.onrender.com";

const PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#e9fff5"><meta name="description" content="Media HUB by PaliaAPK HUB — save public videos from Facebook, Instagram and Threads.">
<title>Media HUB — PaliaAPK HUB</title>
<style>
:root{--mint:#dff8ed;--green:#0b9b70;--deep:#087653;--ink:#152b25;--muted:#71827b;--line:#e3eee9;--white:#fff;--bg:#f7fbf9}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 Inter,ui-sans-serif,system-ui,-apple-system,Segoe UI,sans-serif}
header{height:72px;background:#ffffffeb;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;padding:0 max(22px,calc((100% - 1120px)/2));position:sticky;top:0;z-index:4;backdrop-filter:blur(12px)}
.brand{display:flex;gap:11px;align-items:center;font-weight:850;font-size:19px;letter-spacing:-.5px}.logo{height:40px;width:40px;border-radius:13px;background:linear-gradient(145deg,#19b887,#087653);display:grid;place-items:center;color:white;font-size:21px;box-shadow:0 6px 15px #0b9b7030}
.brand small{display:block;font-size:10px;color:var(--muted);font-weight:650;letter-spacing:1.2px}
nav{display:flex;gap:24px;color:#50645c;font-size:13px;font-weight:650}nav a{color:inherit;text-decoration:none}
main{max-width:1020px;margin:0 auto;padding:58px 22px 70px}.eyebrow{display:inline-flex;gap:8px;align-items:center;background:#e6f8ef;border:1px solid #c8eedc;border-radius:99px;padding:7px 12px;color:var(--deep);font-size:11px;font-weight:800;letter-spacing:.8px}
h1{font-size:clamp(36px,6vw,58px);line-height:1.05;letter-spacing:-2.5px;margin:20px 0 14px;text-align:center}.accent{color:var(--green)}.sub{max-width:610px;margin:0 auto;color:var(--muted);text-align:center;font-size:16px}
.panel{max-width:760px;margin:34px auto 0;background:white;border:1px solid var(--line);border-radius:24px;padding:24px;box-shadow:0 18px 55px #1b61430b}
.platforms{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:22px}.platform{border:1px solid var(--line);background:#fff;border-radius:13px;padding:12px 8px;display:flex;align-items:center;justify-content:center;gap:8px;font-weight:750;color:#4c6259;cursor:pointer}.platform.active{background:#e9faf2;border-color:#74d5ad;color:#087653;box-shadow:inset 0 0 0 1px #74d5ad}.picon{font-size:19px}
label{display:block;font-weight:750;font-size:13px;margin:0 0 9px}.urlrow{display:flex;gap:10px}.urlrow input{flex:1;min-width:0;height:54px;border:1px solid #dce9e2;border-radius:13px;padding:0 15px;font:inherit;outline:none;background:#fbfdfc}.urlrow input:focus{border-color:#39bd8c;box-shadow:0 0 0 3px #39bd8c20}.btn{border:0;border-radius:13px;background:var(--green);color:white;padding:0 23px;font:750 14px inherit;cursor:pointer;min-height:52px;box-shadow:0 7px 16px #0b9b7028}.btn:hover{background:var(--deep)}.btn:disabled{opacity:.55;cursor:wait}
.hint{color:#91a098;font-size:12px;margin:11px 2px 0}.status{display:none;margin-top:16px;padding:12px 14px;border-radius:12px;font-size:13px}.status.show{display:block}.status.error{background:#fff1f0;color:#a93228}.status.loading{background:#effaf5;color:#087653}
.result{display:none;margin-top:20px;border:1px solid var(--line);border-radius:17px;padding:14px;gap:15px;align-items:center}.result.show{display:flex}.thumb{width:142px;aspect-ratio:16/10;object-fit:cover;border-radius:11px;background:#eef4f0}.rmeta{flex:1;min-width:0}.rtitle{font-weight:800;line-height:1.3;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.rsub{font-size:12px;color:var(--muted);margin-top:7px}.download{margin-top:12px;display:inline-flex;align-items:center;justify-content:center;text-decoration:none;min-height:42px;padding:0 17px}
.features{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;max-width:900px;margin:42px auto 0}.feature{background:white;border:1px solid var(--line);border-radius:17px;padding:19px}.ficon{width:38px;height:38px;border-radius:12px;background:#e9faf2;display:grid;place-items:center;font-size:19px}.feature b{display:block;margin:12px 0 4px}.feature p{margin:0;color:var(--muted);font-size:13px}
footer{border-top:1px solid var(--line);text-align:center;padding:24px;color:#83928b;font-size:12px}footer b{color:#087653}
@media(max-width:620px){header{height:64px;padding:0 17px}nav{gap:12px;font-size:11px}main{padding:42px 15px 52px}.panel{padding:17px;border-radius:19px}.urlrow{flex-direction:column}.btn{width:100%}.features{grid-template-columns:1fr;gap:10px;margin-top:26px}.result{align-items:flex-start}.thumb{width:105px}.download{width:100%}.platform{font-size:12px;gap:5px}}
</style></head>
<body>
<header><div class="brand"><div class="logo">▶</div><div>Media HUB<small>BY PALIAAPK HUB</small></div></div><nav><a href="#downloader">Downloader</a><a href="#how">How it works</a></nav></header>
<main>
<div style="text-align:center"><span class="eyebrow">✦ SIMPLE · FAST · FREE</span><h1>Save your favourite<br><span class="accent">social videos.</span></h1><p class="sub">Paste a public video link from Facebook, Instagram or Threads. Preview it, then download when available.</p></div>
<section class="panel" id="downloader">
<div class="platforms" role="group" aria-label="Choose platform"><button class="platform active" data-platform="facebook"><span class="picon">ⓕ</span> Facebook</button><button class="platform" data-platform="instagram"><span class="picon">◎</span> Instagram</button><button class="platform" data-platform="threads"><span class="picon">＠</span> Threads</button></div>
<label for="url">Paste your video link</label><div class="urlrow"><input id="url" type="url" placeholder="https://www.facebook.com/..."><button class="btn" id="load">Load Videos&nbsp; →</button></div>
<p class="hint">Supports public posts and videos only. Do not submit private or login-restricted content.</p>
<div id="status" class="status"></div>
<div id="result" class="result"><img id="thumb" class="thumb" alt="Video thumbnail"><div class="rmeta"><div id="title" class="rtitle"></div><div id="meta" class="rsub"></div><a id="download" class="btn download" href="#" target="_blank" rel="noopener">↓ Download Video</a></div></div>
</section>
<section class="features" id="how"><article class="feature"><div class="ficon">🔗</div><b>1. Copy link</b><p>Copy the link of a public Facebook, Instagram or Threads post.</p></article><article class="feature"><div class="ficon">⚡</div><b>2. Load video</b><p>Paste the link and let Media HUB check whether the video is available.</p></article><article class="feature"><div class="ficon">⬇</div><b>3. Download</b><p>Preview the result and save the video when a downloadable file is available.</p></article></section>
</main><footer>© 2026 <b>Media HUB</b> · Developer by shanpalia · Use content only with permission.</footer>
<script>
const input=document.getElementById('url'),load=document.getElementById('load'),statusBox=document.getElementById('status'),result=document.getElementById('result');let platform='facebook';
const placeholders={facebook:'https://www.facebook.com/...',instagram:'https://www.instagram.com/reel/...',threads:'https://www.threads.com/@user/post/...'};
document.querySelectorAll('.platform').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('.platform').forEach(x=>x.classList.remove('active'));b.classList.add('active');platform=b.dataset.platform;input.placeholder=placeholders[platform]}));
function showStatus(msg,type){statusBox.textContent=msg;statusBox.className='status show '+type}
function detect(u){try{const h=new URL(u).hostname.toLowerCase();if(h.includes('facebook.com')||h==='fb.watch')return'facebook';if(h.includes('instagram.com'))return'instagram';if(h.includes('threads.com')||h.includes('threads.net'))return'threads'}catch{}return''}
load.addEventListener('click',async()=>{const url=input.value.trim();if(!url){showStatus('Please paste a video link first.','error');return}const detected=detect(url);if(!detected){showStatus('Please enter a valid Facebook, Instagram or Threads link.','error');return}if(detected!==platform){platform=detected;document.querySelectorAll('.platform').forEach(x=>x.classList.toggle('active',x.dataset.platform===platform))}
load.disabled=true;result.classList.remove('show');showStatus('Checking public post… this can take a little while.','loading');
try{const res=await fetch('/api/info?url='+encodeURIComponent(url));const data=await res.json();if(!res.ok||!data.ok)throw new Error(data.detail||data.error||'This post could not be loaded.');document.getElementById('title').textContent=data.title||'Video ready';document.getElementById('meta').textContent=(data.extractor||platform)+' · '+(data.duration?Math.round(data.duration)+' sec':'Video');const img=document.getElementById('thumb');if(data.thumbnail){img.src=data.thumbnail;img.style.display='block'}else{img.style.display='none'}document.getElementById('download').href='/api/download?url='+encodeURIComponent(url);result.classList.add('show');showStatus('Video found. You can download it now.','loading')}catch(e){showStatus(e.message||'Something went wrong. Please try again.','error')}finally{load.disabled=false}});
input.addEventListener('keydown',e=>{if(e.key==='Enter')load.click()});
</script></body></html>`;

function json(data,status=200){return new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}})}
function validUrl(raw){try{const u=new URL(raw);const h=u.hostname.toLowerCase();return ["facebook.com","fb.com","fb.watch","instagram.com","threads.com","threads.net"].some(d=>h===d||h.endsWith("."+d))&&["http:","https:"].includes(u.protocol)}catch{return false}}
export default {async fetch(request){
 const url=new URL(request.url);
 if(url.pathname==="/")return new Response(PAGE,{headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-cache","x-content-type-options":"nosniff","referrer-policy":"strict-origin-when-cross-origin"}});
 if(url.pathname==="/health")return json({ok:true,service:"Media HUB Cloudflare frontend"});
 if(url.pathname==="/api/info"||url.pathname==="/api/download"){
  const target=url.searchParams.get("url")||"";
  if(!validUrl(target))return json({detail:"Only public Facebook, Instagram and Threads URLs are supported."},400);
  const endpoint=url.pathname==="/api/info"?"/info":"/download";
  try{
   const upstream=await fetch(BACKEND+endpoint+"?url="+encodeURIComponent(target),{headers:{"Accept":request.headers.get("Accept")||"*/*"}});
   const headers=new Headers(upstream.headers);headers.set("cache-control","no-store");headers.set("x-content-type-options","nosniff");
   return new Response(upstream.body,{status:upstream.status,statusText:upstream.statusText,headers});
  }catch{return json({detail:"Media service is temporarily unavailable. Please retry shortly."},502)}
 }
 return new Response("Not found",{status:404});
}};