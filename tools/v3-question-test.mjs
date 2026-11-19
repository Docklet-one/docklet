// Unit test for the question card (0.3.98).
//
// 1. Option labels must wrap instead of being clipped by nowrap.
// 2. "不回答" keeps the card as an unanswered history card. There is no
//    protocol-level decline, so skipping still ends the turn, but it must not
//    leave a "你已停止本轮" note — the unanswered card is the feedback.

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

check("option labels wrap", source.includes(".v3opt-label{flex:1;min-width:0;font-size:12px;line-height:1.45;white-space:normal;word-break:break-word"));
check("options keep vertical padding", source.includes("min-height:44px;margin:0;padding:8px 12px;"));
check("skip button rendered", source.includes('data-respond-skip="${b(o.id)}"') && source.includes("不回答"));
check("submit still there", source.includes('data-respond="${b(o.id)}" class="is-wide"'));
check("skip ends the turn", source.includes("await e.client.interrupt();") && source.includes("e.skipStopNote = !0"));
check("skip records an unanswered card", source.includes('decision: "unanswered"') && source.includes('answer: "未回答"'));
check("unanswered card uses the reject tag", source.includes('is-reject" : ""}') && source.includes('CARD_ICONS.x + "已拒绝"'));
check("body keeps 未回答", source.includes('answer: "未回答"'));
check("skip hides the pending prompt", source.includes('status: "resolved"'));
check("stop note suppressed for skip", source.includes("e.skipStopNote") && source.includes("stampSkippedTurn"));
check("turn.end resolves pending prompts", source.includes("promptsAfterEnd") && source.includes('status: "resolved"'));
check("skip failure is reported", source.includes('"结束失败："'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall question card cases passed");
