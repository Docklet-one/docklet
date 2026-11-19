const port = process.env.CDP_PORT || 9334;

async function main() {
  let target;
  for (let i = 0; i < 30; i += 1) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      target = targets.find((item) => item.type === "page");
      if (target) break;
    } catch (_) {
      // wait for forwarded devtools
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  if (!target) throw new Error("no WebView devtools target");
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
  async function evaluate(expression, awaitPromise = false) {
    const result = await send("Runtime.evaluate", {
      expression,
      awaitPromise,
      returnByValue: true,
    });
    if (result.result?.exceptionDetails) throw new Error(JSON.stringify(result.result.exceptionDetails));
    return result.result?.result?.value;
  }
  await send("Runtime.enable");
  console.log("files.exists before:", await evaluate(
    "docklet.files.exists('lab/test.txt')",
    true
  ));
  console.log("files.write:", await evaluate(
    "docklet.files.write('lab/test.txt', 'delete-me').then(r => JSON.stringify(r))",
    true
  ));
  console.log("files.exists after write:", await evaluate(
    "docklet.files.exists('lab/test.txt')",
    true
  ));
  console.log("files.delete:", await evaluate(
    "docklet.files.delete('lab/test.txt').then(r => JSON.stringify(r))",
    true
  ));
  console.log("files.exists after delete:", await evaluate(
    "docklet.files.exists('lab/test.txt')",
    true
  ));
  await evaluate("document.getElementById('url').value='http://10.0.2.2:8789/health'");
  await evaluate("document.getElementById('sse').click()");
  await new Promise((resolve) => setTimeout(resolve, 5500));
  console.log("log:", (await evaluate("document.getElementById('log').textContent")).slice(-900));
  socket.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
