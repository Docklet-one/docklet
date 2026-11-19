// Unit test for the flat session list (0.7.0).

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

const b = (v) => String(v ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const makeN = (e) => new Function(
  "e", "b", "ia", "kt", "qe", "sessionTime",
  `${extractFunction(source, "N")}\nreturn N;`
)(e, b, () => "今天", () => "09:12", (state) => state && state.session, () => "09:12");

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const sessions = [
  { id: "s1", title: "会话一", snippet: "最近一句", at: 1, running: true },
  { id: "s2", title: "会话二", at: 2, online: true },
  { id: "s3", title: "会话三", snippet: "旧的", at: 3 }
];

{
  const html = makeN({ sessions, session: "s1", state: { session: { id: "s1" } } })();
  check("one continuous container", (html.match(/class="v3session-list"/g) || []).length === 1);
  check("no date headers", !html.includes("v3sheet-title"));
  check("shows title and snippet", html.includes("会话一") && html.includes("最近一句"));
  check("running session shows a dot", html.includes('class="v3dot run"'));
  check("online is not shown", !html.includes("在线"));
  check("time is rendered", html.includes(">09:12<"));
  check("no 当前 label", !html.includes("当前"));
}
{
  const html = makeN({ sessions: [], sessionQuery: "" })();
  check("empty list invites creating", html.includes("还没有会话") && html.includes("＋"));
}
{
  const html = makeN({ sessions, sessionQuery: "不存在" })();
  check("empty search result", html.includes("没有匹配的会话"));
}
{
  const html = makeN({ sessions: [], sessionsLoading: true })();
  check("loading state uses skeleton", html.includes("v3skel-row"));
}
{
  const dup = makeN({ sessions: [{ id: "s9", title: "同一句", snippet: "同一句", at: 1 }] })();
  check("snippet equal to title is hidden", !dup.includes('v3session-snippet">同一句'));
}
check("session time helper exists", source.includes("function sessionTime("));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall session list cases passed");
