// Regression test for "keyboard pops up when a new bubble arrives".
//
// $() re-renders the whole shell on every incoming event and restores focus to
// the composer when it was focused. On Android, pressing back hides the
// keyboard but the textarea keeps focus, so the next re-render called
// textarea.focus() and reopened the keyboard. Focus is now only restored when
// the keyboard is actually open (tracked via visualViewport height).

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

const shouldRestoreFocus = new Function(
  `${extractFunction(source, "shouldRestoreFocus")}\nreturn shouldRestoreFocus;`
)();

const el = { id: "text" };
const docFocused = { activeElement: el };
const docElsewhere = { activeElement: {} };

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("focused + keyboard open -> restore", shouldRestoreFocus(docFocused, el, true) === true);
check("focused + keyboard hidden -> do not restore", shouldRestoreFocus(docFocused, el, false) === false);
check("focused + unknown keyboard -> restore (desktop)", shouldRestoreFocus(docFocused, el, undefined) === true);
check("not focused -> do not restore", shouldRestoreFocus(docElsewhere, el, true) === false);
check("no textarea -> do not restore", shouldRestoreFocus(docFocused, null, true) === false);

// The keyboard state must be tracked from visualViewport + focusin.
check("tracks visualViewport", source.includes("visualViewport") && source.includes("keyboardOpen"));
check("$ uses shouldRestoreFocus", source.includes("shouldRestoreFocus(document, g, e.keyboardOpen)"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall focus cases passed");
