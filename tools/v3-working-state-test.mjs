// Unit test for the loading bubble (0.4.3).
//
// Before: the bubble only showed when the last message was a user message, so a
// tool-running turn after an assistant message had a stop button but no bubble.
// Now: thinking / executing / streaming, hidden while waiting for the user.

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

const make = (e) => new Function(
  "e", "vt", "Gt", "Jt",
  `${extractFunction(source, "workingState")}\nreturn workingState;`
)(
  e,
  (st) => st.activity || { status: "idle" },
  (st) => st.stream || { text: "", reasoning: "" },
  (st) => (st.activity && st.activity.status) === "running"
);

const msg = (role) => ({ id: "m-" + role, role });

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const thinking = make({
  turnActive: true,
  state: { messages: [msg("user"), msg("assistant")], activity: { status: "running" }, stream: { text: "", reasoning: "" }, tools: [] }
});
check("bubble shows after an assistant message while tools run", thinking() === "thinking");

const executing = make({
  turnActive: true,
  state: { messages: [msg("user"), msg("assistant")], activity: { status: "running" }, stream: { text: "", reasoning: "" }, tools: [{ status: "running" }] }
});
check("running tool -> executing", executing() === "executing");

const streaming = make({
  turnActive: true,
  state: { messages: [msg("user"), msg("assistant")], activity: { status: "running" }, stream: { text: "部分", reasoning: "" }, tools: [] }
});
check("stream text -> streaming", streaming() === "streaming");

const waiting = make({
  turnActive: true,
  state: { messages: [msg("user")], activity: { status: "waiting_input" }, stream: { text: "", reasoning: "" }, tools: [] }
});
check("waiting_input hides the bubble", waiting() === "");

const errored = make({
  turnActive: true,
  state: { messages: [msg("user")], activity: { status: "error" }, stream: { text: "", reasoning: "" }, tools: [] }
});
check("error hides the bubble", errored() === "");

const idle = make({
  turnActive: false,
  state: { messages: [msg("user"), msg("assistant")], activity: { status: "idle" }, stream: { text: "", reasoning: "" }, tools: [] }
});
check("idle shows nothing", idle() === "");

const reasoning = make({
  turnActive: true,
  state: { messages: [msg("user")], activity: { status: "running" }, stream: { text: "", reasoning: "想一想" }, tools: [] }
});
check("reasoning -> streaming bubble", reasoning() === "streaming");

check("label switches to 正在执行", source.includes('state === "executing" ? "正在执行"'));
check("executing keeps the dots", source.includes('.v3work[data-state="executing"] .v3work-body'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall working state cases passed");
