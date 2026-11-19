import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8798, cdpPort = 9339;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const sessionA = "session-15210c8b-6f8c-4bb0-9b27-23d9d3ddfb97";
const sessionB = "session-cf63f7bb-ca89-449a-b094-4b1ac0c59175";
const profile = "D:\\docklet\\.tmp\\chrome-switch-probe";
const origin = `http://127.0.0.1:${httpPort}`;
const seed = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(sessionA)});
location.replace("/index.html");
</script>`;
const mime = (rel) => rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".css") ? "text/css" : rel.endsWith(".json") ? "application/json" : "text/html; charset=utf-8";
const server = http.createServer((req, res) => {
  const url = new URL(req.url, origin);
  if (url.pathname === "/seed.html") { res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }); res.end(seed); return; }
  if (url.pathname === "/__docklet/sdk.js") { res.writeHead(200, { "Content-Type": "text/javascript", "Cache-Control": "no-store" }); res.end(fs.readFileSync(sdkPath)); return; }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file)) { res.writeHead(404); res.end("no"); return; }
  res.writeHead(200, { "Content-Type": mime(rel), "Cache-Control": "no-store" });
  res.end(fs.readFileSync(file));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.rmSync(profile, { recursive: true, force: true });
fs.mkdirSync(profile, { recursive: true });
await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, "--no-first-run", "--no-default-browser-check", "--disable-gpu", "--window-size=420,900", "about:blank"], { stdio: "ignore" });
const seen = [];
try {
  for (let i = 0; i < 100; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/version`); if (r.ok) break; } catch {} await sleep(200); }
  const page = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true }); return r.result?.result?.value; };
  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#connState')").catch(() => false)) break; await sleep(300); }
  await sleep(2000);
  const readSub = `JSON.stringify({ html: document.querySelector('.v3sub').innerHTML, dots: document.querySelectorAll('.v3sub .v3condot').length, chips: document.querySelectorAll('.v3sub .v3connchip').length })`;
  console.log("before", await ev(readSub));
  // 固定尺寸：切换连接状态只改文字/圆点颜色，芯片宽高不变
  const box = await ev(`(() => {
    const chip = document.querySelector('#connChip');
    const dot = chip.querySelector('.v3condot');
    const before = chip.getBoundingClientRect();
    const text = chip.querySelector('#connState');
    const oldText = text.textContent;
    text.textContent = '重连中';
    dot.classList.remove('ok');
    const after = chip.getBoundingClientRect();
    const color = getComputedStyle(dot).backgroundColor;
    text.textContent = oldText;
    dot.classList.add('ok');
    return JSON.stringify({ w1: before.width, w2: after.width, h1: before.height, h2: after.height, color });
  })()`);
  const boxInfo = JSON.parse(box);
  const boxOk = Math.abs(boxInfo.w1 - boxInfo.w2) < 0.5 && Math.abs(boxInfo.h1 - boxInfo.h2) < 0.5 && boxInfo.color === "rgb(180, 69, 60)";
  console.log(boxOk ? "PASS" : "FAIL", "conn chip keeps size and turns red when reconnecting", box);
  const anim = await ev(`JSON.stringify({
    connected: getComputedStyle(document.querySelector('#connState')).animationName,
    reconnectMarkup: document.querySelector('#connChip').outerHTML.includes('is-reconnect')
  })`);
  const animInfo = JSON.parse(anim);
  console.log(animInfo.connected === "none" && animInfo.reconnectMarkup === false ? "PASS" : "FAIL",
    "normal connection does not blink", anim);
  await ev(`window.__dockletSwitchSession(${JSON.stringify(sessionB)})`);
  for (const wait of [50, 250, 800, 2000]) {
    await sleep(wait);
    const snapshot = await ev(readSub);
    seen.push(JSON.parse(snapshot));
    console.log(`after +${wait}`, snapshot);
  }
} finally {
  child.kill();
  server.close();
}
const failed = seen.filter((x) => x.dots !== 1 || x.chips !== 1);
console.log(failed.length ? `FAIL nested conn chip during switch: ${JSON.stringify(failed)}` : "PASS single conn chip during switch");
process.exit(failed.length ? 1 : 0);
