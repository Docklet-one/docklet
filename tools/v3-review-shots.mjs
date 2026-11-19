import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8796;
const cdpPort = 9337;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-0049ebef-38ee-420f-879c-1f5b4dccbaca";
const profile = "D:\\docklet\\.tmp\\chrome-shots";
const origin = `http://127.0.0.1:${httpPort}`;
const outDir = "D:\\docklet\\docs\\screenshots";
fs.mkdirSync(outDir, { recursive: true });

const seed = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(session)});
location.replace("/index.html");
</script>`;

const mimeOf = (rel) => rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".css") ? "text/css" : rel.endsWith(".json") ? "application/json" : "text/html; charset=utf-8";
const server = http.createServer((req, res) => {
  const url = new URL(req.url, origin);
  if (url.pathname === "/seed.html") { res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }); res.end(seed); return; }
  if (url.pathname === "/__docklet/sdk.js") { res.writeHead(200, { "Content-Type": "text/javascript", "Cache-Control": "no-store" }); res.end(fs.readFileSync(sdkPath)); return; }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file)) { res.writeHead(404); res.end("no"); return; }
  res.writeHead(200, { "Content-Type": mimeOf(rel), "Cache-Control": "no-store" });
  res.end(fs.readFileSync(file));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

fs.rmSync(profile, { recursive: true, force: true });
fs.mkdirSync(profile, { recursive: true });
await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`,
  "--no-first-run", "--no-default-browser-check", "--disable-gpu", "--window-size=420,900", "about:blank"], { stdio: "ignore" });
try {
  for (let i = 0; i < 100; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/version`); if (r.ok) break; } catch {} await sleep(200); }
  const list = await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json();
  const page = list.find((t) => t.type === "page");
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true }); return r.result?.result?.value; };
  const shot = async (name) => {
    const r = await send("Page.captureScreenshot", { format: "png" });
    fs.writeFileSync(path.join(outDir, name), Buffer.from(r.result.data, "base64"));
    console.log("shot", name);
  };
  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#connState')").catch(() => false)) break; await sleep(300); }
  await sleep(3000);
  await ev("const l=document.querySelector('#list'); l.scrollTop=Math.max(0, l.scrollHeight-l.clientHeight*2); 1");
  await sleep(100);
  await shot("v3-review-chat-mid.png");
  await ev("const l2=document.querySelector('#list'); l2.scrollTop=l2.scrollHeight; 1");
  await sleep(100);
  await shot("v3-review-chat-bottom.png");
  await ev("document.querySelector('#settings').click()");
  await sleep(900);
  await shot("v3-review-settings.png");
  await ev("document.querySelector('[data-acc-toggle=\"server\"]').click()");
  await sleep(500);
  await shot("v3-review-settings-server.png");
  await ev("document.querySelector('[data-acc-toggle=\"server\"]').click()");
  await sleep(300);
  await ev("document.querySelector('[data-acc-toggle=\"notify\"]').click()");
  await sleep(500);
  await shot("v3-review-settings-notify.png");
  await ev("document.querySelector('[data-acc-toggle=\"notify\"]').click()");
  await sleep(300);
  await ev("document.querySelector('#backChat').click()");
  await sleep(600);
  await ev("document.querySelector('#settings').click()");
  await sleep(600);
  await ev("document.querySelector('[data-acc-toggle=\"avatar\"]').click()");
  await sleep(500);
  await shot("v3-review-settings-avatar.png");
  await ev("document.querySelector('[data-acc-toggle=\"avatar\"]').click()");
  await sleep(300);
  await ev("document.querySelector('#backChat').click()");
  await sleep(500);
  await ev("document.querySelector('#modelChip').click()");
  await sleep(600);
  await shot("v3-review-model.png");
  await ev("(document.querySelector('#modelCancel')||{click(){}}).click()");
  await sleep(300);
  await ev("document.querySelector('#connChip').click()");
  await sleep(500);
  await shot("v3-review-network.png");
  await ev("(document.querySelector('#connClose')||{click(){}}).click()");
  await sleep(300);
  await ev("document.querySelector('#sessionList').click()");
  await sleep(900);
  await shot("v3-review-sessions.png");
} finally {}
process.exit(0);
