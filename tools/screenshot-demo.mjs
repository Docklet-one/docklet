import { writeFile } from "node:fs/promises";

const port = process.env.CDP_PORT || 9222;
const targetUrl = process.env.PREVIEW_URL || "http://127.0.0.1:4173/";
const output = process.env.OUTPUT || "D:/docklet/docs/screenshots/demo-home.png";
const width = Number(process.env.VIEWPORT_WIDTH || 390);
const height = Number(process.env.VIEWPORT_HEIGHT || 844);

async function main() {
  let targets;
  for (let i = 0; i < 40; i += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`);
      targets = await response.json();
      if (targets.length) break;
    } catch (_) {
      // wait for chrome
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  if (!targets || !targets.length) throw new Error("no CDP target");
  const target = targets.find((item) => item.type === "page") || targets[0];

  const socket = new WebSocket(target.webSocketDebuggerUrl);
  let nextId = 1;
  const pending = new Map();
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message);
      pending.delete(message.id);
    }
  });
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });

  function send(method, params = {}) {
    const id = nextId++;
    socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve) => pending.set(id, resolve));
  }

  await send("Page.enable");
  await send("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 2,
    mobile: true,
    screenWidth: width,
    screenHeight: height,
  });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  await send("Page.navigate", { url: targetUrl });
  await new Promise((resolve) => setTimeout(resolve, 1600));

  const metrics = await send("Page.getLayoutMetrics");
  const content = metrics.result?.cssContentSize || { width, height };
  const clipHeight = Math.max(height, Math.ceil(content.height));
  const shot = await send("Page.captureScreenshot", {
    format: "png",
    fromSurface: true,
    captureBeyondViewport: true,
    clip: { x: 0, y: 0, width, height: clipHeight, scale: 1 },
  });
  if (!shot.result?.data) throw new Error("screenshot failed");
  await writeFile(output, Buffer.from(shot.result.data, "base64"));
  console.log(`wrote ${output} (${width}x${clipHeight} CSS @2x)`);
  socket.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
