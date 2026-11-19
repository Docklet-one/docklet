// Regression test for the frozen "正在思考" bubble after a failed turn.
//
// Bug: applyTurnEvent only cleared turnActive on the not-ended -> ended
// transition (`ended && !e.turnEnded`). After a successful turn turnEnded
// stays true, so a turn that fails without any delta (TRANSPORT error) never
// cleared turnActive, workingState() kept returning "thinking", and the stop
// button stayed on screen.
//
// Fix: clear turnActive whenever ended, hide the bubble for error activity,
// reset turnEnded on send, and include turnActive/activity in chatSig.

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

const makeApply = (e) => new Function("e", `${extractFunction(source, "applyTurnEvent")}\nreturn applyTurnEvent;`)(e);
const makeWorking = (e) => new Function(
  "e", "Gt", "gt", "Jt", "vt",
  `${extractFunction(source, "workingState")}\nreturn workingState;`
)(
  e,
  (st) => st.stream || { text: "", reasoning: "" },
  (st) => st.messages || [],
  (st) => st.activity && st.activity.status === "running",
  (st) => st.activity || { status: "idle" }
);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

// 1. the exact failure: previous turn ended, this turn fails without deltas
{
  const state = {
    stream: { ended: true },
    messages: [{ role: "user", id: "u1" }],
    activity: { status: "error" }
  };
  const e = { turnActive: true, turnEnded: true, state };
  makeApply(e)(state);
  check("error clears a stuck turnActive", e.turnActive === false, String(e.turnActive));
  check("error hides the thinking bubble", makeWorking(e)() === "", JSON.stringify(makeWorking(e)()));
}

// 2. sticky turnActive alone must not animate when activity is error
{
  const state = { stream: { ended: true }, messages: [{ role: "user", id: "u1" }], activity: { status: "error" } };
  const e = { turnActive: true, turnEnded: false, state };
  check("error activity never shows thinking", makeWorking(e)() === "");
}

// 3. a real running turn still shows the bubble
{
  const state = { stream: { ended: false }, messages: [{ role: "user", id: "u1" }], activity: { status: "running" } };
  const e = { turnActive: true, turnEnded: false, state };
  check("running turn shows thinking", makeWorking(e)() === "thinking");
}

// 4. streaming text wins over thinking
{
  const state = { stream: { text: "部分输出", ended: false }, messages: [{ role: "user", id: "u1" }], activity: { status: "running" } };
  const e = { turnActive: true, turnEnded: false, state };
  check("streaming stays streaming", makeWorking(e)() === "streaming");
}

// 5. deltas reset turnEnded so the next end is a real transition
{
  const state = { stream: { ended: false } };
  const e = { turnActive: true, turnEnded: true };
  makeApply(e)(state);
  check("delta keeps turnActive", e.turnActive === true && e.turnEnded === false);
}

// 6. activity changes must NOT trigger a full re-render (that made the stop
//    button flash); they are synced in place by paintWorking() instead.
{
  const start = source.indexOf("function chatSig()");
  const end = source.indexOf("async function se()", start);
  const chatSig = source.slice(start, end);
  const paintStart = source.indexOf("function paintWorking()");
  const paint = source.slice(paintStart, start);
  check("chatSig stays stable on activity", !chatSig.includes("e.turnActive") && !chatSig.includes("vt(st).status"));
  check("paintWorking syncs header/stop in place", paint.includes("syncChatHeader()") && paint.includes("syncStopButton(active)"));
}

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall turn-active cases passed");
