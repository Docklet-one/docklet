import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Concatenate the ordered V3 source fragments into the shipped single module.
// Runtime semantics are unchanged: every fragment lands in the same module scope.
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const srcDir = path.join(root, "src", "v3");
const outFile = path.join(root, "apps", "dsh-console", "v3.js");

const files = fs.readdirSync(srcDir)
  .filter((name) => /^\d+-.*\.js$/.test(name))
  .sort((a, b) => a.localeCompare(b, "en", { numeric: true }));

if (!files.length) throw new Error("no source fragments in src/v3");

const source = files.map((name) => fs.readFileSync(path.join(srcDir, name), "utf8")).join("");
fs.writeFileSync(outFile, source, "utf8");
console.log(`built v3.js from ${files.length} fragments (${source.length} bytes)`);
