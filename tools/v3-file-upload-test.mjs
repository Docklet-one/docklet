const port = Number(process.env.CDP_PORT || 9334);

async function main() {
  let target;
  for (let i = 0; i < 30; i += 1) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      target = targets.find((item) => item.type === "page");
      if (target) break;
    } catch (_) {
      // wait for adb forward
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
  console.log("write:", await evaluate(
    "docklet.files.write('upload/test.txt','hello-v3-upload').then(()=>JSON.stringify({ok:true}))",
    true
  ));
  console.log("read:", await evaluate(
    "docklet.files.read('upload/test.txt',{encoding:'base64'}).then(v=>JSON.stringify({bytes:v.length,head:v.slice(0,16)}))",
    true
  ));
  console.log("upload:", await evaluate(
    "(async()=>{const v=await docklet.files.read('upload/test.txt',{encoding:'base64'});"
      + "const r=await fetch('http://10.0.2.2:8789/upload',{method:'POST',body:v});"
      + "return await r.text();})()",
    true
  ));
  console.log("snapshot:", await evaluate(
    "fetch('http://10.0.2.2:8789/snapshot?session=sess-demo&turns=3').then(r=>r.text())",
    true
  ));
  console.log("send:", await evaluate(
    "fetch('http://10.0.2.2:8789/send',{method:'POST',headers:{'content-type':'application/json'},"
      + "body:JSON.stringify({sessionId:'sess-demo',text:'hello from v3 test'})}).then(r=>r.text())",
    true
  ));
  socket.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
