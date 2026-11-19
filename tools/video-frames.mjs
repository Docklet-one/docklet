import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const video = "C:\\Users\\95891\\.dsh\\attachments\\v1\\files\\11\\11e121b5055a2d5bbbe8b25af4025311f4d02a4cacf48ec5ecfb43e7edf2bf97\\video_20261117_050900.mp4";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8811, cdpPort = 9351;
const profile = "D:\\docklet\\.tmp\\chrome-video2";
const outDir = "D:\\docklet\\.tmp\\vidframes2";
fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });

const page = `<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:#111}canvas{width:100%;display:block}</style><video id="v" src="/video.mp4" muted playsinline preload="auto" style="display:none"></video><canvas id="c"></canvas><script>
const v = document.querySelector('#v'), c = document.querySelector('#c');
function draw() {
  if (!v.videoWidth) return;
  c.width = v.videoWidth; c.height = v.videoHeight;
  c.getContext('2d').drawImage(v, 0, 0, c.width, c.height);
}
v.addEventListener('loadeddata', draw);
window.__seek = (t) => new Promise((resolve) => {
  const done = () => { draw(); requestAnimationFrame(() => resolve(true)); };
  v.addEventListener('seeked', done, { once: true });
  v.currentTime = t;
});
window.__ready = () => v.readyState >= 2 && v.videoWidth > 0;
</script>`;
const server = http.createServer((req, res) => {
  if (req.url === "/") { res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" }); res.end(page); return; }
  if (req.url === "/video.mp4") {
    const stat = fs.statSync(video);
    res.writeHead(200, { "Content-Type": "video/mp4", "Content-Length": stat.size, "Accept-Ranges": "none" });
    fs.createReadStream(video).pipe(res);
    return;
  }
  res.writeHead(404); res.end("no");
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.rmSync(profile, { recursive: true, force: true });
fs.mkdirSync(profile, { recursive: true });
await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, "--no-first-run", "--disable-gpu", "--autoplay-policy=no-user-gesture-required", "--window-size=430,950", "about:blank"], { stdio: "ignore" });
try {
  for (let i = 0; i < 100; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/version`); if (r.ok) break; } catch {} await sleep(200); }
  const target = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); return r.result?.result?.value; };
  await send("Page.enable");
  await send("Page.navigate", { url: `http://127.0.0.1:${httpPort}/` });
  for (let i = 0; i < 120; i += 1) { if (await ev("window.__ready && window.__ready()")) break; await sleep(250); }
  console.log("ready", await ev("JSON.stringify({ w: document.querySelector('#v').videoWidth, dur: document.querySelector('#v').duration })"));
  const duration = Number(await ev("document.querySelector('#v').duration"));
  const step = 0.3;
  const count = Math.min(40, Math.ceil(duration / step));
  for (let i = 0; i < count; i += 1) {
    const t = Math.min(duration - 0.05, i * step);
    await ev(`window.__seek(${t})`);
    await sleep(80);
    const shot = await send("Page.captureScreenshot", { format: "jpeg", quality: 60 });
    fs.writeFileSync(path.join(outDir, `f${String(i).padStart(2, "0")}-${t.toFixed(2)}.jpg`), Buffer.from(shot.result.data, "base64"));
  }
  console.log("frames", count);
} finally {
  child.kill();
  server.close();
}
process.exit(0);
