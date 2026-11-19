import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const root = "D:\\docklet";
const pub = path.join(root, "release", "publish", "dsh-phone-console");
const appSrc = path.join(root, "apps", "dsh-console");
const fragments = path.join(root, "src", "v3");
const pluginSrc = "C:\\Users\\95891\\Documents\\deepseek-harness\\default-workspace\\gamehub\\plugins\\phone-relay";
const pluginFiles = ["index.js", "client.js", "events.js", "interaction-bridge.js", "events.test.mjs", "package.json", "cordis.patch.yml", "icon.svg", "README.md"];
const textExt = new Set([".js", ".mjs", ".json", ".html", ".css", ".md", ".yml", ".yaml", ".svg", ".txt"]);

function copyFile(from, to) {
  fs.mkdirSync(path.dirname(to), { recursive: true });
  if (textExt.has(path.extname(from).toLowerCase())) {
    const text = fs.readFileSync(from, "utf8").replace(/\r\n/g, "\n");
    fs.writeFileSync(to, text, "utf8");
  } else {
    fs.copyFileSync(from, to);
  }
}

for (const name of fs.readdirSync(appSrc)) {
  const from = path.join(appSrc, name);
  const to = path.join(pub, "app", name);
  if (fs.statSync(from).isDirectory()) fs.cpSync(from, to, { recursive: true });
  else copyFile(from, to);
}
fs.mkdirSync(path.join(pub, "app", "src", "v3"), { recursive: true });
for (const name of fs.readdirSync(fragments)) {
  if (name.endsWith(".js")) copyFile(path.join(fragments, name), path.join(pub, "app", "src", "v3", name));
}
for (const name of pluginFiles) copyFile(path.join(pluginSrc, name), path.join(pub, "plugin", name));
fs.cpSync(path.join(pluginSrc, "locale"), path.join(pub, "plugin", "locale"), { recursive: true });

for (const file of [path.join(pub, "app", "v3.js"), path.join(pub, "app", "src", "v3", "02-client.js")]) {
  let s = fs.readFileSync(file, "utf8");
  s = s.replace(/const ra = ``, re = "http:\/\/[^"]+", Oe = \{/, 'const ra = ``, re = "", Oe = {');
  fs.writeFileSync(file, s, "utf8");
}

const attrs = path.join(pub, ".gitattributes");
if (!fs.existsSync(attrs)) fs.writeFileSync(attrs, "* text=auto eol=lf\n*.png binary\n*.apk binary\n*.pkg binary\n", "utf8");

console.log("synced app + plugin ->", pub);
try {
  console.log(execFileSync("git", ["-C", pub, "status", "--short"], { encoding: "utf8" }));
} catch {}
