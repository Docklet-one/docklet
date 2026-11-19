// Unit test for V3's multi-image / attachment markup (the v1 gallery spec).
//
// Rules recovered from v1:
//   1 image  -> is-one   (single, capped, keeps aspect ratio)
//   2 images -> is-two   (2 columns)
//   3-4      -> is-few   (2 columns)
//   5+       -> is-many  (3 columns)
// Files render as chips (icon + name + size) instead of one link per line.
//
// The test evaluates the shipped galleryHtml/filesHtml source, with `b`, `$t`,
// `ca` and the transport url helper stubbed.

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
    const ch = text[i];
    if (ch === "{") { depth += 1; body = true; }
    else if (ch === "}") { depth -= 1; if (body && depth === 0) return text.slice(start, i + 1); }
  }
  throw new Error(`function ${name} is not balanced`);
}

const b = (value) => String(value == null ? "" : value)
  .replace(/[&<>"]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[ch]));
const stubE = { client: { fileUrl: (id) => `http://relay/file/${id}` } };
const $t = (name) => `[${String(name).split(".").pop()}]`;
const ca = (size) => `${Math.round(size / 1024)} KB`;

const make = (code) => new Function("b", "e", "$t", "ca", `${code}\nreturn { galleryHtml, filesHtml };`)(
  b, stubE, $t, ca
);
const { galleryHtml, filesHtml } = make(
  `${extractFunction(source, "galleryHtml")}\n${extractFunction(source, "filesHtml")}`
);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const img = (i) => ({ id: `att-${i}` });

check("no images -> nothing", galleryHtml([]) === "");
check("1 image -> is-one", galleryHtml([img(1)]).includes('chat-md-gallery is-one'));
check("2 images -> is-two", galleryHtml([img(1), img(2)]).includes("is-two"));
check("3 images -> is-few", galleryHtml([img(1), img(2), img(3)]).includes("is-few"));
check("4 images -> is-few", galleryHtml([img(1), img(2), img(3), img(4)]).includes("is-few"));
check("5 images -> is-many", galleryHtml([1, 2, 3, 4, 5].map(img)).includes("is-many"));
check("count is exposed", galleryHtml([img(1), img(2)]).includes('data-count="2"'));
check("images keep data-image-id",
  /data-image-id="att-1"/.test(galleryHtml([img(1)])), galleryHtml([img(1)]).slice(0, 120));
check("single image is not an empty wrapper", galleryHtml([img(1)]).includes("<img"));

check("no files -> nothing", filesHtml([]) === "");
const chips = filesHtml([
  { id: "f1", name: "报告.pdf", size: 2048 },
  { id: "f2", name: "notes.txt" },
]);
check("files render as chips", chips.includes('class="v3file"') && chips.includes('class="v3file-name"'));
check("file chip keeps href", chips.includes("http://relay/file/f1"));
check("file chip shows size when known", chips.includes("2 KB"));
check("file name is escaped", filesHtml([{ id: "x", name: "<b>.txt" }]).includes("&lt;b&gt;"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall gallery/attachment cases passed");
