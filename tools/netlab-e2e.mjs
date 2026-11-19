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
    if (result.result?.exceptionDetails) {
      throw new Error(JSON.stringify(result.result.exceptionDetails));
    }
    return result.result?.result?.value;
  }

  await send("Runtime.enable");
  console.log("title:", await evaluate("document.title"));
  console.log("files.write:", await evaluate(
    "docklet.files.write('lab/test.txt', 'hello-from-cdp').then(r => JSON.stringify(r))",
    true
  ));
  console.log("files.read:", await evaluate(
    "docklet.files.read('lab/test.txt')",
    true
  ));
  console.log("files.list:", await evaluate(
    "docklet.files.list('lab').then(r => JSON.stringify(r))",
    true
  ));
  console.log("clipboard.write:", await evaluate(
    "docklet.clipboard.write('docklet-clip-test').then(r => JSON.stringify(r))",
    true
  ));
  console.log("clipboard.read:", await evaluate(
    "docklet.clipboard.read()",
    true
  ));
  console.log("health:", await evaluate(
    "fetch('http://10.0.2.2:8789/health').then(r => r.text())",
    true
  ));
  await evaluate("document.getElementById('url').value='http://10.0.2.2:8789/health'");
  await evaluate("document.getElementById('get').click()");
  await new Promise((resolve) => setTimeout(resolve, 800));
  console.log("log tail:", (await evaluate("document.getElementById('log').textContent")).slice(-700));
  await evaluate("document.getElementById('sse').click()");
  await new Promise((resolve) => setTimeout(resolve, 4500));
  console.log("sse log tail:", (await evaluate("document.getElementById('log').textContent")).slice(-500));
  await evaluate("document.getElementById('sse').click()");
  console.log("openExternal:", await evaluate(
    "docklet.openExternal('https://docklet.cn').then(r => JSON.stringify(r))",
    true
  ));
  socket.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
