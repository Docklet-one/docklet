import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8801, cdpPort = 9342;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-cf63f7bb-ca89-449a-b094-4b1ac0c59175";
const profile = "D:\\docklet\\.tmp\\chrome-ime";
const origin = `http://127.0.0.1:${httpPort}`;
const seed = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(session)});
location.replace("/index.html");
</script>`;
const mime = (rel) => rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".json") ? "application/json" : "text/html; charset=utf-8";
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
let failed = 0;
try {
  for (let i = 0; i < 100; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/version`); if (r.ok) break; } catch {} await sleep(200); }
  const page = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); if (r.result && r.result.exceptionDetails) console.error("eval error", JSON.stringify(r.result.exceptionDetails).slice(0, 400)); return r.result?.result?.value; };
  const ime = (value) => ev(`window.dispatchEvent(new MessageEvent("message", { data: { type: "docklet:insets", detail: { top: 0, bottom: 0, ime: ${value} } } })), document.querySelector('#list').scrollTop`);

  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#connState')").catch(() => false)) break; await sleep(300); }
  await sleep(2500);
  console.log("page", await ev("location.href"), await ev("document.contentType"), JSON.stringify(await ev("document.body.innerText.slice(0, 80)")));

  // 1) tapping the input returns to the latest message
  await ev("document.querySelector('#list').scrollTop = 0");
  await ev("document.querySelector('#text').focus()");
  await ime(320);
  await sleep(400);
  const focusInfo = JSON.parse(await ev(`(() => {
    const l = document.querySelector('#list');
    return JSON.stringify({ gap: l.scrollHeight - l.scrollTop - l.clientHeight });
  })()`));
  const focusOk = focusInfo.gap < 130;
  if (!focusOk) failed += 1;
  console.log(focusOk ? "PASS" : "FAIL", "tapping the input returns to the latest message", JSON.stringify(focusInfo));

  // 2) at the bottom: keyboard keeps the latest message visible
  await ev("document.querySelector('#list').scrollTop = document.querySelector('#list').scrollHeight");
  await sleep(100);
  await ime(0);
  await sleep(100);
  await ime(320);
  await sleep(400);
  const bottomInfo = JSON.parse(await ev(`(() => {
    const l = document.querySelector('#list');
    return JSON.stringify({ top: l.scrollTop, gap: l.scrollHeight - l.scrollTop - l.clientHeight });
  })()`));
  const bottomOk = bottomInfo.gap < 130;
  if (!bottomOk) failed += 1;
  console.log(bottomOk ? "PASS" : "FAIL", "keyboard keeps the latest message in view", JSON.stringify(bottomInfo));
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
