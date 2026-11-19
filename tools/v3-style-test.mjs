// Unit test for the screenshot-driven style fixes (0.6.10).

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

check("code language is uppercased", source.includes(".v3bubble .chat-code-lang{text-transform:uppercase"));
check("destructive settings row is red", source.includes('[data-acc="reset"] .v3acc-title{color:#b4453c}'));
check("to-top reserves space", source.includes(".v3bubble.has-toTop{padding-bottom:42px}"));
check("to-top adds the class", source.includes('bubble.classList.add("has-toTop")'));
check("to-top removes the class", source.includes('bubble.classList.remove("has-toTop")'));
check("session snippet dedupes title", source.includes('rawSnippet !== String(session.title || "").trim()'));
check("network test button is neutral", source.includes(".v3opt.v3test-btn{margin-left:auto;background:#edf2f6"));
check("notify is turn-only", !source.includes("data-notify-mode") && source.includes('mode: "turn"'));
check("avatar hint sits with preset", source.includes("点头像可更换图片") && source.includes("v3avatar-hint"));
check("jump button lifts with the keyboard", source.includes("bottom:calc(92px + var(--v3-ime, 0px))"));
check("unread count is capped", source.includes('e.unread > 99 ? "99+" : e.unread'));
check("meta avatars are readable", source.includes(".v3meta-row .v3av{width:30px;height:30px"));
check("keyboard scroll respects the reader", source.includes("if (!force && !e.stickBottom) return;"));
check("reconnect flash only on reconnect", source.includes(".v3connchip.is-reconnect .v3conntext{animation:v3phflash"));
check("connected chip has no reconnect class", !source.includes('v3condot ok"></i><span class="v3conntexts"') || !source.includes('class="v3connchip is-reconnect" id="connChip"><i class="v3condot ok"'));
check("folds carry a stable key", source.includes('data-fold="p:${b(String(process.turn'));
check("fold open state re-applied", source.includes("applyFoldState(t)"));
check("folds hold the scroll position", source.includes("function holdScrollPosition"));
check("deliverable url uses the state session", source.includes("const sid = String(stateSession && stateSession.id || e.session"));
check("pull only when more history exists", source.includes("s.hasMore && !olderLimit ? '<div class=\"v3ptr\""));
check("pull gesture checks canPull", source.includes("const canPull = !!(e.state && e.state.sync && e.state.sync.hasMore"));
check("running dot has a glow", source.includes("animation:v3glow") && source.includes("@keyframes v3glow"));
check("chat updates render in place", source.includes("function renderChatUpdate") && source.includes('id="msgs"'));
check("message html is cached", source.includes("const msgHtmlCache") && source.includes("msgHtmlCache.set(cacheKey"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall style cases passed");
