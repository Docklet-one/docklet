// End-to-end test for the V3 "LAN first, WAN when away" network switching.
//
// Set-up:
//   - real phone relay on 127.0.0.1:8788 acts as the "external" address
//   - a small proxy on 127.0.0.1:8801 forwards to it and acts as the "LAN"
//     address, so the LAN can be taken down / brought back instantly
//   - the module page is served from apps/dsh-console with the real SDK
//
// Cases:
//   1. both reachable -> connects over the LAN (LAN priority)
//   2. LAN goes away  -> switches to the external address (going out)
//   3. LAN comes back -> switches back to the LAN (coming home)
//
// Usage: node tools/v3-net-switch-test.mjs

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.join(here, "..", "apps", "dsh-console");
const sdkPath = path.join(here, "..", "shell", "app", "src", "main", "assets", "docklet-sdk.js");
const chrome = process.env.CHROME_BIN || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const pagePort = Number(process.env.NET_PAGE_PORT || 8795);
const cdpPort = Number(process.env.NET_CDP_PORT || 9337);
const lanPort = Number(process.env.NET_LAN_PORT || 8801);
const relayPort = Number(process.env.NET_RELAY_PORT || 8788);
const relayBase = `http://127.0.0.1:${relayPort}`;
const lanBase = `http://127.0.0.1:${lanPort}`;
const origin = `http://127.0.0.1:${pagePort}`;
const profile = path.join(here, "..", ".tmp", "chrome-v3-net");

let token = process.env.DOCKLET_RELAY_TOKEN || "";
if (!token) {
  try {
    token = fs.readFileSync(path.join(process.env.USERPROFILE || "", ".dsh", "phone-relay", "token"), "utf8").trim();
  } catch (_) {
    token = "";
  }
}

const mime = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".json": "application/json; charset=utf-8", ".png": "image/png" };
const results = [];
function check(name, pass, detail = "") {
  results.push({ name, pass });
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

// ---------------------------------------------------------------- static page
const pageServer = http.createServer((req, res) => {
  const url = new URL(req.url || "/", origin);
  if (url.pathname === "/__docklet/sdk.js") {
    res.writeHead(200, { "Content-Type": mime[".js"], "Cache-Control": "no-store" });
    res.end(fs.readFileSync(sdkPath));
    return;
  }
  if (url.pathname === "/seed.html") {
    const session = url.searchParams.get("session") || "";
    const body = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(lanBase)});
localStorage.setItem("v3.wan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(lanBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(session)});
localStorage.setItem("v3.notify", "0");
location.replace("/index.html");
</script>`;
    res.writeHead(200, { "Content-Type": mime[".html"], "Cache-Control": "no-store" });
    res.end(body);
    return;
  }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404);
    res.end("not found");
    return;
  }
  res.writeHead(200, { "Content-Type": mime[path.extname(file)] || "application/octet-stream", "Cache-Control": "no-store" });
  res.end(fs.readFileSync(file));
});

// --------------------------------------------------------------- LAN proxy
let lanServer = null;
const lanSockets = new Set();

function startLan() {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const upstream = http.request(
        { host: "127.0.0.1", port: relayPort, path: req.url, method: req.method, headers: { ...req.headers, host: `127.0.0.1:${relayPort}` } },
        (up) => {
          res.writeHead(up.statusCode || 502, up.headers);
          up.pipe(res);
        }
      );
      upstream.on("error", () => {
        try { res.writeHead(502); res.end("lan proxy error"); } catch (_) {}
      });
      req.pipe(upstream);
    });
    server.on("connection", (socket) => {
      lanSockets.add(socket);
      socket.on("close", () => lanSockets.delete(socket));
    });
    server.once("error", reject);
    server.listen(lanPort, "127.0.0.1", () => {
      lanServer = server;
      resolve();
    });
  });
}

function stopLan() {
  return new Promise((resolve) => {
    const server = lanServer;
    lanServer = null;
    if (!server) return resolve();
    for (const socket of lanSockets) socket.destroy();
    lanSockets.clear();
    server.close(() => resolve());
  });
}

// ----------------------------------------------------------------- CDP glue
class Cdp {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.id && this.pending.has(message.id)) {
        this.pending.get(message.id)(message);
        this.pending.delete(message.id);
      }
    });
  }
  static async connect(wsUrl) {
    const socket = new WebSocket(wsUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    return new Cdp(socket);
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve) => this.pending.set(id, resolve));
  }
  async evaluate(expression, awaitPromise = false) {
    const result = await this.send("Runtime.evaluate", { expression, awaitPromise, returnByValue: true });
    if (result.result?.exceptionDetails) throw new Error(JSON.stringify(result.result.exceptionDetails).slice(0, 300));
    return result.result?.result?.value;
  }
}

async function waitFor(fn, timeoutMs, label) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const value = await fn();
      if (value) return { value, elapsed: Date.now() - started };
    } catch (_) {}
    await new Promise((resolve) => setTimeout(resolve, 120));
  }
  throw new Error(`timeout: ${label}`);
}

async function pickSession() {
  const response = await fetch(`${relayBase}/sessions?token=${encodeURIComponent(token)}`);
  const body = await response.json();
  const sessions = Array.isArray(body.sessions) ? body.sessions : [];
  return String((sessions.find((item) => item && item.online && !item.running) || sessions[0] || {}).id || "");
}

async function main() {
  if (!fs.existsSync(chrome)) throw new Error(`chrome not found: ${chrome}`);
  const session = await pickSession();
  if (!session) throw new Error("relay has no session");
  fs.rmSync(profile, { recursive: true, force: true });
  fs.mkdirSync(profile, { recursive: true });
  await new Promise((resolve) => pageServer.listen(pagePort, "127.0.0.1", resolve));
  await startLan();

  const child = spawn(
    chrome,
    ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, "--no-first-run",
      "--no-default-browser-check", "--disable-gpu", "--disable-extensions", "--window-size=420,900", "about:blank"],
    { stdio: "ignore" }
  );

  try {
    await waitFor(() => fetch(`http://127.0.0.1:${cdpPort}/json/version`).then((r) => r.ok), 20000, "chrome");
    const created = await fetch(`http://127.0.0.1:${cdpPort}/json/new?about:blank`, { method: "PUT" });
    const target = await created.json();
    const cdp = await Cdp.connect(target.webSocketDebuggerUrl);
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
    await cdp.send("Page.addScriptToEvaluateOnNewDocument", {
      source: `window.__errors = []; window.__f = [];
        window.addEventListener("error", (e) => window.__errors.push(String(e.message || e.error)));
        window.addEventListener("unhandledrejection", (e) => window.__errors.push("rej: " + String((e.reason && e.reason.message) || e.reason)));
        (function(){ const orig = window.fetch.bind(window);
          window.fetch = function (u, o) { const label = String(u).replace(/^https?:\\/\\//, "").slice(0, 46);
            const p = orig(u, o); p.then((r) => window.__f.push([label, r.status])).catch((e) => window.__f.push([label, "ERR " + (e && e.message)])); return p; };
        })();`,
    });

    await cdp.send("Page.navigate", { url: `${origin}/seed.html?session=${encodeURIComponent(session)}` });
    await waitFor(() => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('#connState')"), 15000, "chat");
    await waitFor(async () => String(await cdp.evaluate("(document.querySelector('#connState')||{}).textContent||''")).includes("已连接"), 15000, "connected");

    const base = () => cdp.evaluate("localStorage.getItem('v3.base')");
    const connected = async () => String(await cdp.evaluate("(document.querySelector('#connState')||{}).textContent||''")).includes("已连接");

    // 1. LAN priority while both are reachable
    check("connected over LAN first", (await base()) === lanBase, await base());

    // 2. LAN disappears -> switch to the external address
    const t1 = Date.now();
    await stopLan();
    const out = await waitFor(async () => (await base()) === relayBase && (await connected()), 12000, "switch to WAN");
    check("switches to external when LAN is gone", true, `${Date.now() - t1}ms`);

    // 3. LAN is back -> switch home, even though the external address still works
    await startLan();
    const lanHealth = await fetch(`${lanBase}/health`, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.status).catch((e) => String(e));
    const t2 = Date.now();
    await cdp.evaluate("window.dispatchEvent(new Event('online'))");
    try {
      await waitFor(async () => (await base()) === lanBase && (await connected()), 10000, "switch back to LAN");
      check("switches back to LAN when reachable", true, `${Date.now() - t2}ms`);
    } catch (error) {
      const diag = await cdp.evaluate(`(async () => {
        const out = { dbg: window.__dbg || null, base: localStorage.getItem('v3.base'), lan: localStorage.getItem('v3.lan'), wan: localStorage.getItem('v3.wan'), session: (localStorage.getItem('v3.session')||'').slice(0,24), conn: (document.querySelector('#connState')||{}).textContent || '', loading: !!document.querySelector('.v3loading'), setup: !!document.querySelector('#saveServer'), errors: window.__errors || [], recent: (window.__f||[]).slice(-8), body: (document.body.innerText||'').slice(0,80).replace(/\\s+/g,' ') };
        try { const r = await fetch(localStorage.getItem('v3.lan') + '/health', { headers: { Authorization: 'Bearer ' + (localStorage.getItem('v3.token')||'') } }); out.lanProbe = r.status; } catch (e) { out.lanProbe = 'ERR ' + (e && e.message); }
        try { const r = await fetch(localStorage.getItem('v3.lan') + '/snapshot?session=' + encodeURIComponent(localStorage.getItem('v3.session')||'') + '&turns=1', { headers: { Authorization: 'Bearer ' + (localStorage.getItem('v3.token')||'') } }); out.snapProbe = r.status; } catch (e) { out.snapProbe = 'ERR ' + (e && e.message); }
        return JSON.stringify(out);
      })()`, true);
      console.log("      diagnostics: " + diag + " | node lan health: " + lanHealth);
      throw error;
    }

    void out;
    await fetch(`http://127.0.0.1:${cdpPort}/json/close/${target.id}`).catch(() => {});
  } finally {
    child.kill();
    await stopLan();
    await new Promise((resolve) => pageServer.close(resolve));
  }

  const failed = results.filter((item) => !item.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  if (failed) process.exit(1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
