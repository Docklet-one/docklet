import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Local-only preview of apps/demo with the shell SDK in mock mode.
// This is a design/dev aid; it is not the Android runtime.
const here = path.dirname(fileURLToPath(import.meta.url));
const demoDir = path.resolve(here, "../apps/demo");
const sdkFile = path.resolve(here, "../shell/app/src/main/assets/docklet-sdk.js");
const port = Number(process.env.PORT || 4173);

const mime = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "127.0.0.1"}`);
    if (url.pathname === "/__docklet/sdk.js") {
      const body = await readFile(sdkFile);
      res.writeHead(200, { "content-type": "application/javascript; charset=utf-8" });
      res.end(body);
      return;
    }
    let pathname = decodeURIComponent(url.pathname);
    if (pathname === "/") pathname = "/index.html";
    const file = path.resolve(demoDir, `.${pathname}`);
    if (!file.startsWith(demoDir + path.sep)) {
      res.writeHead(403);
      res.end("forbidden");
      return;
    }
    const body = await readFile(file);
    res.writeHead(200, {
      "content-type": mime[path.extname(file)] || "application/octet-stream",
      "cache-control": "no-store",
    });
    res.end(body);
  } catch (_) {
    res.writeHead(404);
    res.end("not found");
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`demo preview: http://127.0.0.1:${port}/`);
});
