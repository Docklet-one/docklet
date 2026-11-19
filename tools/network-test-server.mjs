import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const port = Number(process.env.PORT || 8788);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dist = path.join(root, "dist");

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function catalog(tamper = false) {
  tamper = tamper || process.env.TAMPER === "1";
  const demos = JSON.parse(fs.readFileSync(path.join(dist, "demos.json"), "utf8"));
  return {
    spec: 1,
    name: "Docklet local test catalog",
    modules: demos.apps.map((app, index) => {
      const updateFile = path.join(dist, `${app.id}-1.0.1.pkg`);
      const asset =
        app.id === "demo" && fs.existsSync(updateFile)
          ? "demo-1.0.1.pkg"
          : app.asset;
      const version = asset === "demo-1.0.1.pkg" ? "1.0.1" : app.version;
      const file = path.join(dist, asset);
      return {
        id: app.id,
        name: app.name,
        kind: app.kind,
        version,
        description: app.description,
        accent: app.accent,
        url: `modules/${asset}`,
        sha256:
          tamper && index === 0
            ? "0".repeat(64)
            : sha256(file),
        size: fs.statSync(file).size,
      };
    }),
  };
}

function cors(res, origin) {
  res.setHeader("Access-Control-Allow-Origin", origin || "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  res.setHeader("Access-Control-Max-Age", "600");
}

const server = http.createServer((req, res) => {
  const origin = req.headers.origin;
  cors(res, origin);

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url || "/", `http://${req.headers.host || "127.0.0.1"}`);

  if (url.pathname === "/catalog.json" || url.pathname === "/catalog-badsha.json") {
    const body = JSON.stringify(catalog(url.pathname === "/catalog-badsha.json"), null, 2);
    res.writeHead(200, { "content-type": "application/json; charset=utf-8" });
    res.end(body);
    return;
  }

  if (url.pathname.startsWith("/modules/")) {
    const name = decodeURIComponent(url.pathname.slice("/modules/".length));
    if (!/^[a-z0-9][a-z0-9.-]*\.pkg$/i.test(name)) {
      res.writeHead(400, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify({ ok: false, error: "bad package name" }));
      return;
    }
    const file = path.join(dist, name);
    if (!file.startsWith(dist + path.sep) || !fs.existsSync(file)) {
      res.writeHead(404, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify({ ok: false, error: "package not found", name }));
      return;
    }
    res.writeHead(200, {
      "content-type": "application/octet-stream",
      "content-length": fs.statSync(file).size,
      "cache-control": "no-store",
    });
    fs.createReadStream(file).pipe(res);
    return;
  }

  if (url.pathname === "/health") {
    const body = JSON.stringify(
      {
        ok: true,
        service: "docklet-network-test",
        time: new Date().toISOString(),
        origin: origin || null,
        message: "network capability is working",
      },
      null,
      2
    );
    res.writeHead(200, { "content-type": "application/json; charset=utf-8" });
    res.end(body);
    return;
  }

  if (url.pathname === "/events") {
    res.writeHead(200, {
      "content-type": "text/event-stream; charset=utf-8",
      "cache-control": "no-cache, no-transform",
      connection: "keep-alive",
      "x-accel-buffering": "no",
    });
    res.write("retry: 2000\n\n");
    // 页面里的 SSE 演示用 2s；后台通知演示可以传 ?interval=8000 放慢，避免连响。
    const interval = Math.min(60000, Math.max(1000, Number(url.searchParams.get("interval")) || 2000));
    let sequence = 0;
    const send = () => {
      sequence += 1;
      res.write("event: message\n");
      res.write(
        `data: ${JSON.stringify({
          type: "message",
          eventId: "evt-" + sequence,
          text: "hello from docklet-network-test",
          sequence,
          time: new Date().toISOString(),
          origin: origin || null,
          message: "hello from docklet-network-test",
        })}\n\n`
      );
    };
    send();
    const timer = setInterval(send, interval);
    req.on("close", () => clearInterval(timer));
    return;
  }

  res.writeHead(404, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify({ ok: false, error: "not found", path: url.pathname }));
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Docklet network test server listening on 0.0.0.0:${port}`);
  console.log(`Emulator URL: http://10.0.2.2:${port}/health`);
  console.log(`SSE URL:      http://10.0.2.2:${port}/events`);
  console.log(`Catalog:      http://10.0.2.2:${port}/catalog.json`);
  console.log(`Bad catalog:  http://10.0.2.2:${port}/catalog-badsha.json`);
  const nets = os.networkInterfaces();
  for (const entries of Object.values(nets)) {
    for (const net of entries || []) {
      if (net.family === "IPv4" && !net.internal) {
        console.log(`LAN URL:      http://${net.address}:${port}/health`);
      }
    }
  }
});
