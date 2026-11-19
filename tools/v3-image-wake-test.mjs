// Regression test for image wake-up (0.3.86).
//
// Images used to load only once, during the render that happens *before* the new
// session client exists; a failed fetch was never retried, so images appeared
// only after the user sent a message (which forces a fresh render).
//
// Now images are kicked right after the client is created, retried with bounds,
// and re-kicked on foreground/older-page.

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

function extractFunction(name) {
  const start = source.search(new RegExp(`(?:async )?function ${name}\\(`));
  if (start < 0) throw new Error(`missing ${name}`);
  let depth = 0;
  let body = false;
  for (let i = start; i < source.length; i += 1) {
    if (source[i] === "{") { depth += 1; body = true; }
    else if (source[i] === "}") { depth -= 1; if (body && depth === 0) return source.slice(start, i + 1); }
  }
  throw new Error(`unbalanced ${name}`);
}

const ue = extractFunction("Ue");
check("loader bails without a client", /if \(!e\.client\) return;/.test(ue));
check("no duplicate in-flight fetch", ue.includes('i.dataset.loading === "1"'));
check("skips permanently failed images", ue.includes('i.dataset.failed === "1"'));
check("skips already loaded images", ue.includes('i.getAttribute("src")'));
check("bounded retries with backoff", ue.includes("tried >= 3") && ue.includes("900 * tried"));
check("marks failed after retries", ue.includes('i.dataset.failed = "1"'));
check("clears retry state on success", ue.includes("delete i.dataset.retries"));

check("session switch wakes images", /e\.client\.start\(\), Ue\(\);/.test(source));
check("foreground retries images", /retryImages\(\);/.test(source));
check("older page wakes images", /e\.loadingOlder = !1;\s*Ue\(\);/.test(source));
check("retryImages clears failure", /function retryImages\(\)[\s\S]*?delete img\.dataset\.failed/.test(source));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall image wake-up cases passed");
