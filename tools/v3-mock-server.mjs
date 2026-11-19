import http from "node:http";

const port = Number(process.env.PORT || 8788);
const sessions = new Map([
  ["sess-demo", { title: "演示会话", messages: [] }],
]);

function json(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "content-type",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (req.method === "OPTIONS") return json(res, 204, {});
  if (url.pathname === "/health") {
    return json(res, 200, { ok: true, service: "docklet-v3-mock" });
  }
  if (url.pathname === "/snapshot") {
    const id = url.searchParams.get("session") || "sess-demo";
    const session = sessions.get(id) || { title: id, messages: [] };
    return json(res, 200, {
      protocolVersion: 1,
      sessionId: id,
      title: session.title,
      turns: session.messages.slice(-3),
    });
  }
  if (url.pathname === "/send" && req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 64 * 1024) req.destroy();
    });
    req.on("end", () => {
      let parsed = {};
      try { parsed = JSON.parse(body || "{}"); } catch (_) {}
      const id = parsed.sessionId || "sess-demo";
      const session = sessions.get(id) || { title: id, messages: [] };
      session.messages.push({ role: "user", text: parsed.text || "" });
      sessions.set(id, session);
      json(res, 200, { ok: true, sessionId: id });
    });
    return;
  }
  if (url.pathname === "/upload" && req.method === "POST") {
    let bytes = 0;
    req.on("data", (chunk) => { bytes += chunk.length; });
    req.on("end", () => json(res, 200, { ok: true, bytes }));
    return;
  }
  if (url.pathname === "/events") {
    res.writeHead(200, {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "Access-Control-Allow-Origin": "*",
    });
    res.write("retry: 5000\n\n");
    let seq = 0;
    const timer = setInterval(() => {
      seq += 1;
      const id = `m-${seq}`;
      const payload = {
        protocolVersion: 1,
        type: "message.assistant",
        sessionId: "sess-demo",
        id,
        text: `后台第 ${seq} 条消息`,
      };
      res.write("event: message\n");
      res.write(`data: ${JSON.stringify(payload)}\n\n`);
    }, 2500);
    req.on("close", () => clearInterval(timer));
    return;
  }
  json(res, 404, { error: "not_found" });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`V3 mock server on http://0.0.0.0:${port}`);
});
