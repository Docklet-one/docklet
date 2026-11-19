// Unit test for session actions (0.3.92): branch and unarchive.
//
// The relay already exposes POST /fork {id, atSeq?} -> {sessionId} and
// POST /session/unarchive {id}; GET /sessions returns {sessions, archived}.
// V3 now surfaces "分支会话" in the session sheet and an "已归档" section with
// a restore button in the session list.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "apps", "dsh-console", "v3.js"), "utf8");

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("transport forks sessions", source.includes('n + "/fork"') && source.includes("async function forkSession("));
check("transport unarchives", source.includes('n + "/session/unarchive"') && source.includes("async function unarchiveSession("));
check("fetch keeps atSeq", source.includes("Number.isSafeInteger(Number(w))"));
check("client exposes fork", source.includes("forkSession: (l, u) => n.forkSession(l, u)"));
check("client exposes unarchive", source.includes("unarchiveSession: (l) => n.unarchiveSession(l)"));
check("sessions payload keeps archived", source.includes("archived: Array.isArray(w.archived) ? w.archived : []"));
check("list loader reads archived", source.includes("e.archivedSessions = Array.isArray(o && o.archived)"));
check("sheet has a branch action", source.includes('id="sessionFork"') && source.includes("分支失败"));
check("branch switches to the new session", source.includes("p ? Pe(p) : H(\"sessions\")"));
check("archive page lists archived sessions", source.includes('v3page-title">已归档') && source.includes("没有已归档的会话") && source.includes("v3session-restore"));
check("archive is its own route", source.includes('e.route === "archived"') && source.includes("function archivePage()"));
check("back leaves the archive page", source.includes('"archived" ? (H("sessions"), !0)'));
check("search row holds new + archive", source.includes('id="newSession" aria-label="新建会话">＋') && source.includes('id="archivedToggle"'));
check("header no longer has new session", !source.includes('<h1 class="v3title v3page-title">会话</h1><button class="v3gear" id="newSession"'));
check("restore calls unarchive", source.includes("e.client.unarchiveSession(id).then(() => Ce())") || source.includes("e.client.unarchiveSession(r.dataset.restore).then(() => Ce())"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall session action cases passed");
