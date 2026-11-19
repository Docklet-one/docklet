// Unit test for persisted "turn was stopped" notes (0.3.80).
//
// A stop must be visible after reopening the app (otherwise a user message with
// no reply looks like a dropped connection), but it must NOT become a chat
// message: no state.messages, no snapshot cache, nothing sent to the model.
// It lives in `v3.stops.<session>` on the phone and renders as a muted date
// line in the timeline.

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

const names = ["stopKey", "readStops", "writeStops", "stopLabel", "addStop", "stopBy", "stopItemHtml", "stopsHtml"];
const build = (e, storage) => new Function(
  "e", "localStorage", "b", "kt", "ae",
  `${names.map((n) => extractFunction(source, n)).join("\n")}
   return { readStops, addStop, stopLabel, stopBy, stopsHtml, stopKey };`
)(
  { profile: { aiName: "DSH助手" }, ...e },
  storage,
  (v) => String(v == null ? "" : v).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])),
  (ms) => new Date(Number(ms) || 0).toISOString().slice(11, 16),
  () => '<span class="v3av"></span>'
);

const memory = () => {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v))
  };
};

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("phone wording", build({ session: "s" }, memory()).stopLabel("phone") === "你已停止本轮");
check("pc wording", build({ session: "s" }, memory()).stopLabel("pc") === "已在电脑端停止本轮");
check("unknown wording", build({ session: "s" }, memory()).stopLabel("") === "本轮已停止");

{
  const e = { session: "s" };
  const h = build(e, memory());
  h.addStop({ id: "stop-1", turn: 1, at: 1000, by: "phone", afterId: "m1" });
  h.addStop({ id: "stop-1", turn: 1, at: 1000, by: "phone", afterId: "m1" });
  check("dedupes by id", h.readStops().length === 1, String(h.readStops().length));
  check("keeps insertion anchor", h.readStops()[0].afterId === "m1");
}

{
  const e = { session: "s" };
  const h = build(e, memory());
  for (let i = 0; i < 45; i += 1) h.addStop({ id: "stop-" + i, turn: i, at: i, by: "pc", afterId: "" });
  check("caps at 40", h.readStops().length === 40, String(h.readStops().length));

  const html = h.stopsHtml();
  check("renders a message row", html.includes("v3row v3stoprow") && html.includes("v3meta-row"));
  check("renders assistant avatar/name", html.includes("v3av") && html.includes("DSH助手"));
  check("renders a copy button", html.includes('class="v3copy"') && html.includes("data-copy-message"));
  check("renders a tinted bubble", html.includes("v3bubble v3stopbubble"));
  check("renders note with anchor", html.includes('data-stop-id="stop-39"') && html.includes("v3stoprow"));
  check("renders pc wording", html.includes("已在电脑端停止本轮"));
  check("old gray line is gone", !html.includes("v3stopnote"));
}

{
  const e = { session: "s", localStopAt: Date.now() };
  check("recent local stop -> phone", build(e, memory()).stopBy() === "phone");
  const e2 = { session: "s", localStopAt: 0 };
  check("no local stop -> pc", build(e2, memory()).stopBy() === "pc");
}

// The note must never enter the message/snapshot state.
{
  const e = { session: "s" };
  const h = build(e, memory());
  h.addStop({ id: "stop-9", turn: 9, at: 9, by: "phone", afterId: "m9" });
  check("stops live in their own key", h.stopKey() === "v3.stops.s");
  check("addStop does not touch messages", !("messages" in e) && !("state" in e));
}

// Source-level checks for the wiring that cannot be unit-tested here.
check("Ct records aborted turns", source.includes('stoppedTurn: reason === "aborted"'));
check("subscribe paths note stops", (source.match(/noteStops\(e\.state\)/g) || []).length >= 3);
check("render places stop notes", source.includes("stopItemHtml") && source.includes("timelineRows()"));
check("stop handler marks local stop", source.includes("e.localStopAt = Date.now()"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall stop-note cases passed");
