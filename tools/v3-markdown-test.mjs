// Regression test for the markdown renderer (the 0.3.73 freeze).
//
// A message that put several `![alt](src)` images on ONE line made the old
// `Je` regex backtrack exponentially (nested quantifiers + `$` anchor), which
// blocked the WebView main thread and froze the whole module UI.
//
// The fix parses image lines linearly (parseImageLine / parseImageRun) and
// `be()` only uses a non-nested inline regex. This test runs the shipped
// parser in a worker with a hard timeout, so a regression shows up as a
// failed case instead of a hung test run.

import { readFileSync } from "node:fs";
import { Worker } from "node:worker_threads";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "apps", "dsh-console", "v3.js"), "utf8");
const sliceStart = source.indexOf("function D(a)");
const sliceEnd = source.indexOf("function oa({");
if (sliceStart < 0 || sliceEnd <= sliceStart) throw new Error("parser slice not found");

const workerCode = `${source.slice(sliceStart, sliceEnd)}
const { parentPort } = require("worker_threads");
parentPort.once("message", (job) => {
  try {
    if (job.mode === "yt") parentPort.postMessage(JSON.stringify({ html: yt(job.text) }));
    else parentPort.postMessage(JSON.stringify({ html: be(job.text) }));
  } catch (error) {
    parentPort.postMessage(JSON.stringify({ error: String((error && error.message) || error) }));
  }
});`;

function run(mode, text, timeoutMs = 2000) {
  return new Promise((resolve) => {
    const worker = new Worker(workerCode, { eval: true });
    const timer = setTimeout(async () => {
      await worker.terminate();
      resolve({ timeout: true });
    }, timeoutMs);
    worker.once("message", async (raw) => {
      clearTimeout(timer);
      await worker.terminate();
      resolve(JSON.parse(raw));
    });
    worker.once("error", async (error) => {
      clearTimeout(timer);
      await worker.terminate();
      resolve({ error: String(error && error.message) });
    });
    worker.postMessage({ mode, text });
  });
}

const img = (n) => `![${n}](docklet-file:public/icons/bee/${n}.png)`;
const fourOnALine = [img("a"), img("b"), img("c"), img("d")].join(" ");
const fiveOnALine = [img("a"), img("b"), img("c"), img("d"), img("e")].join(" ");
const twoLines = `${img("a")}\n${img("b")}`;

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

// 1. the exact shape that used to freeze: four images on one line
{
  const out = await run("yt", fourOnALine);
  check("four images on one line return", !out.timeout && !out.error, out.timeout ? "TIMEOUT" : out.error || "");
  check("four images become a gallery", !!out.html && out.html.includes("chat-md-gallery is-few") && (out.html.match(/<img/g) || []).length === 4);
}

// 2. single image keeps the is-one layout
{
  const out = await run("yt", img("solo"));
  check("single image is is-one", !!out.html && out.html.includes("is-one"));
}

// 3. five images on one line -> three columns
{
  const out = await run("yt", fiveOnALine);
  check("five images on one line use is-many", !!out.html && out.html.includes("is-many") && (out.html.match(/<img/g) || []).length === 5);
}

// 4. two images on separate lines -> is-two
{
  const out = await run("yt", twoLines);
  check("two image lines form is-two", !!out.html && out.html.includes("is-two"));
}

// 5. pathological inline input must not backtrack
{
  const out = await run("be", "![a](" + "a".repeat(400));
  check("unclosed image does not hang", !out.timeout && !out.error, out.timeout ? "TIMEOUT" : out.error || "");
  check("unclosed image stays text", !!out.html && out.html.includes("![a]("));
}
{
  const out = await run("be", "[a](http://x(" + "(".repeat(30));
  check("unbalanced link does not hang", !out.timeout && !out.error, out.timeout ? "TIMEOUT" : out.error || "");
}

// 6. regular inline formatting still works
{
  const out = await run("be", "**粗体** `code` [链接](https://example.com/x)");
  check("inline formatting intact",
    !!out.html && out.html.includes("<strong>") && out.html.includes("chat-inline-code") && out.html.includes("chat-link"),
    out.html || out.error || "TIMEOUT");
}

// 7. image inside a paragraph still renders as an inline image
{
  const out = await run("yt", `前面文字 ${img("inline")} 后面文字`);
  check("inline image inside text", !!out.html && out.html.includes("chat-md-image") && out.html.includes("chat-md-gallery") === false);
}

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall markdown cases passed");
