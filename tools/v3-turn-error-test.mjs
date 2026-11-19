// Unit test for surfacing failed turns (upstream 402 / 欠费 / invalid request).
//
// The relay reports failures as `message.error` events, e.g.
//   { code: "INVALID_REQUEST", status: 400, hint: "执行失败",
//     message: "Content Exists Risk (...)", retryable: true }
// V3 used to only flip the activity dot to error; the text is now kept in
// state.turnError and rendered as a card in the chat.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "apps", "dsh-console", "v3.js"), "utf8");

function extractFunction(text, name) {
  const start = text.search(new RegExp(`(?:async )?function ${name}\\(`));
  if (start < 0) throw new Error(`function ${name} not found`);
  let depth = 0;
  let body = false;
  for (let i = start; i < text.length; i += 1) {
    if (text[i] === "{") { depth += 1; body = true; }
    else if (text[i] === "}") { depth -= 1; if (body && depth === 0) return text.slice(start, i + 1); }
  }
  throw new Error(`function ${name} is not balanced`);
}

const turnErrorText = new Function(`${extractFunction(source, "turnErrorText")}\nreturn turnErrorText;`)();
const b = (value) => String(value == null ? "" : value)
  .replace(/[&<>"]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[ch]));

function render(turnError) {
  const e = { state: turnError ? { turnError } : {} };
  const factory = new Function("e", "b", `${extractFunction(source, "turnErrorHtml")}\nreturn turnErrorHtml;`);
  return factory(e, b)();
}

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const balance = turnErrorText({ code: "INSUFFICIENT_BALANCE", status: 402, message: "余额不足，请充值", hint: "执行失败" }, "2026-11-15T17:00:00Z");
check("keeps the upstream message", balance.message === "余额不足，请充值", balance.message);
check("keeps the error code", balance.code === "INSUFFICIENT_BALANCE", balance.code);
check("keeps the time", balance.at === "2026-11-15T17:00:00Z", balance.at);

const hintOnly = turnErrorText({ hint: "执行失败", status: 500 }, "");
check("falls back to hint", hintOnly.message === "执行失败", hintOnly.message);
check("falls back to status as code", hintOnly.code === "500", hintOnly.code);

const empty = turnErrorText(null, "");
check("missing payload still has a message", empty.message === "执行失败", empty.message);

const html = render(balance);
check("card renders the title", html.includes("本轮失败"));
check("card renders the message", html.includes("余额不足，请充值"));
check("card renders the code", html.includes("INSUFFICIENT_BALANCE"));

const escaped = render(turnErrorText({ message: "<b>坏</b>" }, ""));
check("card escapes the message", escaped.includes("&lt;b&gt;") && !escaped.includes("<b>坏</b>"));

check("no error -> no card", render(null) === "");

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall turn error cases passed");
