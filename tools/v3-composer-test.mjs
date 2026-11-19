// Unit test for composer drafts and empty-state suggestions (0.6.2).

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

const b = (value) => String(value ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const makeEmpty = (e, online = true) => new Function(
  "e", "b", "navigator",
  `${extractFunction(source, "emptyChatHtml")}\nreturn emptyChatHtml;`
)(e, b, { onLine: online });

{
  const html = makeEmpty({ connected: true, attempt: 0 })();
  check("empty chat offers suggestions", html.includes("v3suggest") && html.includes("总结一下当前项目"));
  check("suggestions are buttons", (html.match(/data-suggest=/g) || []).length === 3);
}
{
  const html = makeEmpty({ connected: false, offline: true, attempt: 2 }, false)();
  check("offline empty keeps retry", html.includes("重试") && html.includes("v3empty-off"));
}
{
  const html = makeEmpty({ connected: false, attempt: 0 })();
  check("connecting empty stays simple", html.includes("连接中") && !html.includes("v3suggest"));
}

// draft storage helper
const store = new Map();
const localStorageStub = { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
const sessionDraft = new Function("localStorage", `${extractFunction(source, "sessionDraft")}\nreturn sessionDraft;`)(localStorageStub);
store.set("v3.draft.s1", "hello");
check("draft reads per session", sessionDraft("s1") === "hello");
check("missing draft is empty", sessionDraft("s2") === "");

check("session switch restores the draft", source.includes("e.draft = sessionDraft(o)"));
check("input persists the draft", source.includes("persistDraft(fe.value)"));
check("send clears the draft key", source.includes('localStorage.removeItem("v3.draft."'));
check("suggestion click fills the composer", source.includes('closest("[data-suggest]")'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall composer cases passed");
