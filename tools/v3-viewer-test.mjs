// Unit test for the photo viewer redesign (0.3.84+).
//
// The viewer is now a full-black column: an image stage (clipped, never covered
// by controls) plus a bottom-right bar holding the counter, save and close.
// Two-finger pinch scales the current image inside the stage; the scale is
// clamped to 1..4.

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

const clamp = new Function(`${extractFunction(source, "clampViewerScale")}\nreturn clampViewerScale;`)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("scale clamps low", clamp(0.3) === 1, String(clamp(0.3)));
check("scale clamps high", clamp(9) === 4, String(clamp(9)));
check("scale passes through", clamp(2.5) === 2.5, String(clamp(2.5)));
check("scale tolerates garbage", clamp("x") === 1 && clamp(null) === 1);

check("viewer has a clipped stage", source.includes('class="v3viewstage" id="viewerStage"'));
check("viewer controls in a bottom bar",
  source.includes("v3viewer-bar") && source.indexOf("id=\"viewerCount\"") > source.indexOf("v3viewer-bar"));
check("counter, save, close order", /id="viewerCount"[^]*id="saveViewer"[^]*id="closeViewer"/.test(source));
check("pinch gesture wired", source.includes("viewerTouchStart") && source.includes("viewerTouchMove"));
check("zoom clamps in the handler", source.includes("clampViewerScale(vz.base * d / vz.dist)"));
check("swipe disabled while zoomed", source.includes('track.style.overflowX = vz.scale > 1 ? "hidden" : "auto"'));
check("reset on open", /viewer\.hidden = !1;\s*viewerReset\(\)/.test(source));
check("counter is centered", source.includes(".v3viewer-count{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)"));
check("buttons keep a 14px gap", source.includes("justify-content:flex-end;gap:14px"));
check("icons never eat the tap", source.includes(".v3viewer-save svg,.v3viewer-close svg{pointer-events:none}"));
check("save hit uses closest", source.includes('s.target.closest("#saveViewer")'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall viewer cases passed");
