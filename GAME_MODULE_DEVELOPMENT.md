# Docklet 小游戏模块开发说明

> 读者：负责写小游戏的另一个对话  
> 目标：做一个能安装、能保存、能暂停恢复、能返回的小游戏模块，用来测试 Docklet 的模块生命周期、SQLite、返回键和安全区。  
> 推荐题目：**2048**。棋盘状态小，适合验证保存/恢复，不需要外部库。

---

## 1. 交付物

在下面新建一个模块目录：

```text
D:\docklet\apps\game2048\
  module.json
  index.html
  app.css
  app.js
  assets/           可选，尽量不用大图
```

如果代码超过约 500 行，不要继续堆 `app.js`，按功能拆：

```text
src/
  main.js
  game.js
  render.js
  storage.js
  input.js
```

要求：

```text
index.html 只加载 src/main.js
首屏只加载必要模块
设置/帮助/排行榜等用 import() 懒加载
不要拆成几十个小文件
```

示例：

```js
const { openHelp } = await import("./src/help.js");
```

不要修改 `D:\docklet\shell`，也不要修改 `gamehub`。  
先只交付一个独立模块目录。

推荐 id：

```text
game2048
```

## 2. module.json

```json
{
  "id": "game2048",
  "name": "2048",
  "kind": "游戏",
  "glyph": "spark",
  "version": "1.0.0",
  "entry": "index.html",
  "chrome": "none",
  "minShell": "1.0.0",
  "capabilities": ["storage"],
  "data": "game2048",
  "description": "滑动合并数字，状态保存在本地",
  "accent": "#6B7F5E",
  "order": 20
}
```

约束：

```text
id 只能 [a-z][a-z0-9-]{1,31}
version 必须是 x.y.z
entry 必须是包内相对路径
chrome 用 none，游戏自己画全屏头部
capabilities 第一版只要 storage
data 必须等于 id
```

不要加 `network`。  
不要引用 CDN、远程字体或外部 JS。

## 3. HTML 骨架

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport"
        content="width=device-width, initial-scale=1, viewport-fit=cover" />
  <meta name="theme-color" content="#f6f1e8" />
  <title>2048</title>
  <link rel="stylesheet" href="app.css" />
</head>
<body>
  <main class="page">
    <header class="hero">
      <div class="hero-top">
        <span class="eyebrow">NUMBER GAME</span>
        <span class="tag">2048 · 01</span>
      </div>
      <h1>把相同的数字，<br /><em>慢慢合在一起。</em></h1>
      <p class="lede">滑动棋盘。离开游戏会保存，回来接着玩。</p>
    </header>

    <section class="score-row" aria-label="分数">
      <div class="score-box"><span>分数</span><strong id="score">0</strong></div>
      <div class="score-box"><span>最高</span><strong id="best">0</strong></div>
      <button id="newGame" class="ghost">重开</button>
    </section>

    <section class="board-wrap">
      <div id="board" class="board" role="grid" aria-label="2048 棋盘"></div>
      <div id="overlay" class="overlay" hidden>
        <p id="overlayTitle">没有可合并的数字了</p>
        <button id="retry" class="primary">再来一局</button>
      </div>
    </section>

    <footer class="foot">
      <span class="status"><i id="statusDot"></i><span id="statusText">准备中…</span></span>
      <span>本地保存 · 无网络</span>
    </footer>
  </main>

  <script src="/__docklet/sdk.js"></script>
  <script src="app.js"></script>
</body>
</html>
```

## 4. 设计风格

演示应用统一是“暖纸面、安静的日常工具”风格，不是高饱和游戏风。

### 颜色

```css
:root {
  --bg: #f6f1e8;
  --surface: #fffdf9;
  --surface-soft: #f3ece1;
  --ink: #241f19;
  --muted: #7d7367;
  --faint: #a99e90;
  --line: #e9e0d3;
  --accent: #6b7f5e;
  --accent-deep: #4f6344;
  --accent-soft: #e8eee2;
  --danger: #b4453c;
  --shadow: 0 18px 50px rgba(78, 58, 33, 0.08);
}
```

背景：

```css
body {
  color: var(--ink);
  background:
    radial-gradient(110% 60% at 88% 0%, rgba(238, 180, 113, 0.22), transparent 56%),
    radial-gradient(80% 48% at 0% 100%, rgba(214, 201, 176, 0.26), transparent 62%),
    var(--bg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}
```

### 布局

```css
.page {
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
  padding:
    calc(24px + env(safe-area-inset-top))
    22px
    calc(30px + env(safe-area-inset-bottom));
}
```

`chrome:none` 只表示壳不画顶部工具栏，不再自动等于“隐藏系统栏”。

游戏建议声明：

```json
"display": {
  "mode": "edge-to-edge",
  "orientation": "portrait",
  "statusBarIcons": "dark"
}
```

如果是赛车这类真正全屏游戏：

```json
"display": {
  "mode": "immersive",
  "orientation": "portrait",
  "statusBarIcons": "light",
  "safeContent": false
}
```

如果不想自己处理刘海：

```json
"display": {
  "mode": "immersive",
  "orientation": "portrait",
  "safeContent": true
}
```

壳会保留安全边距，模块不需要算刘海。

不要写死 24px 顶栏；edge-to-edge / immersive 下使用 `env(safe-area-inset-*)` 或壳注入的 `--safe-top` / `--safe-bottom`。

### 字体节奏

```text
eyebrow      10–11px，700，字距 0.16em，大写
tag          10px，胶囊
h1           36–46px，700，字距 -0.035em
h1 em        强调色，不是斜体
lede         13–14px，1.7 行高，muted
正文         13–14px
辅助文字     11–12px
```

数字用：

```css
font-variant-numeric: tabular-nums;
```

### 卡片和按钮

```css
.card {
  background: var(--surface);
  border: 1px solid rgba(233, 224, 211, 0.8);
  border-radius: 26px;
  box-shadow: var(--shadow);
}
```

按钮：

```text
主按钮：accent 填充，白字，52px 高，圆角 16–18px
次按钮：surface-soft 背景，ink 文字
文字按钮：muted / accent-deep
触摸目标至少 44px
不要 hover 依赖
```

动画：

```text
150–220ms
只做 transform / opacity
不要无限循环动画
支持 prefers-reduced-motion
```

## 5. 棋盘视觉

棋盘建议做成 4×4 的暖色方格：

```text
空格：--surface-soft
2/4：浅米色
8/16：浅橙
32/64：橙
128+：深橙或深绿
数字：白色或深色，按对比度切换
```

要求：

- 棋盘整体 26px 圆角
- 格子 12–14px 圆角
- 格子之间 8–10px 间距
- 数字使用 tabular-nums
- 2048 格子有明确高亮
- 棋盘 `touch-action: none`，防止页面跟随滑动

不要使用大背景图。  
不要为每个数字加载图片。  
全部用 CSS 和文本渲染。

## 6. 小游戏逻辑要求

推荐实现完整 2048：

```text
4×4 棋盘
上下左右滑动
相同数字合并一次
每次移动生成 2 或 4
分数累计
最高分
胜利提示
无路可走提示
重开
保存/恢复
```

手势：

```text
touchstart / touchend 判断主轴
阈值 30–40px
只处理水平/垂直主方向
滑动时阻止页面滚动
桌面预览额外支持方向键
```

不要用长按、双击或复杂手势，先保证稳定。

## 7. SDK 接入

页面必须加载：

```html
<script src="/__docklet/sdk.js"></script>
```

初始化：

```js
const sdk = window.docklet;

async function boot() {
  await sdk.storage.open();
  await ensureSchema();
  const state = await loadState();
  render(state);
  await sdk.ready();
}
```

### ready

```js
await docklet.ready();
```

必须在首屏可交互后调用。  
超过 10 秒未 ready，壳会认为启动失败。

### 生命周期

```js
docklet.lifecycle.setHandler(async (action) => {
  if (action === "pause") pauseGame();
  if (action === "resume") resumeGame();
  if (action === "save") await saveState();
});
```

规则：

```text
pause：停掉 requestAnimationFrame / 定时器，停止输入
resume：恢复输入，不重复创建 DOM
save：把棋盘、分数、最高分写入 SQLite
```

不要在 `pause` 里做重活。  
保存上限约 500ms。

### 返回键

有弹窗、确认框、结算层时：

```js
docklet.onBack(() => {
  if (!overlay.hidden) {
    closeOverlay();
    return true;
  }
  return false;
});
```

返回 `true` 表示消费本次返回。  
没有历史时壳会关闭模块。

## 8. SQLite 保存

使用一张简单的状态表即可：

```js
async function ensureSchema() {
  await sdk.storage.exec(
    `CREATE TABLE IF NOT EXISTS game_state (
       key TEXT PRIMARY KEY,
       value TEXT NOT NULL
     )`,
    []
  );
}
```

保存整个棋盘 JSON：

```js
async function saveState() {
  const payload = JSON.stringify({
    cells: state.cells,
    score: state.score,
    best: state.best,
    over: state.over,
    won: state.won,
    savedAt: Date.now()
  });

  await sdk.storage.transaction([
    {
      sql: "INSERT OR REPLACE INTO game_state(key, value) VALUES (?, ?)",
      params: ["current", payload]
    }
  ]);
}
```

读取：

```js
async function loadState() {
  const rows = await sdk.storage.query(
    "SELECT value FROM game_state WHERE key = ?",
    ["current"]
  );
  if (!rows.length) return initialState();
  try {
    return normalize(JSON.parse(rows[0].value));
  } catch (_) {
    return initialState();
  }
}
```

规则：

```text
只访问自己的 data/game2048/app.db
SQL 单条、参数化
不要 ATTACH / DETACH / VACUUM INTO
不要把 localStorage 当唯一数据源
每次有效移动后保存，或 1–2 秒防抖保存
```

## 9. 暂停和恢复

动画循环：

```js
let raf = 0;

function pauseGame() {
  if (raf) cancelAnimationFrame(raf);
  raf = 0;
  inputEnabled = false;
  saveState().catch(() => {});
}

function resumeGame() {
  inputEnabled = true;
  if (!raf && needsAnimation) {
    raf = requestAnimationFrame(loop);
  }
}
```

如果游戏没有动画，只需要：

```text
pause：inputEnabled = false，保存
resume：inputEnabled = true
```

不要让定时器在后台继续跑。

## 10. 状态栏与安全区

`chrome:none` 只是不画壳的顶部工具栏，不等于隐藏系统栏。

三种选择：

```text
display.mode = "safe"
  状态栏/导航栏可见
  壳自动避让刘海和系统栏
  模块不用写 safe-area

display.mode = "edge-to-edge"
  系统栏透明
  内容延伸到系统栏后面
  模块自己处理安全区

display.mode = "immersive"
  隐藏状态栏和导航栏
  边缘滑动可唤出
  模块处理 HUD，或 safeContent=true 让壳留边距
```

如果使用 `edge-to-edge` 或 `immersive` 且自己布局：

```css
.page {
  padding-top: calc(20px + env(safe-area-inset-top));
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}
```

并设置：

```html
<meta name="viewport"
      content="width=device-width, initial-scale=1, viewport-fit=cover" />
```

如果使用 `safe`，不要重复加一大段顶部 padding，否则会离状态栏太远。

不要自己画假状态栏。

## 11. 视觉禁止项

```text
不要外部 CDN
不要远程字体
不要 emoji 当主要图标
不要大面积高饱和渐变
不要玻璃拟态
不要持续发光/脉冲动画
不要大图背景
不要多层弹窗
不要依赖 hover
```

## 12. 安装与测试

### 打包模块

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\pack-module.ps1 `
  -Source D:\docklet\apps\game2048 `
  -Out D:\docklet\dist\game2048-1.0.0.pkg
```

### 安装

在 Docklet「安装」里选择本地 `.pkg`，或把包交给壳的远程目录。

### 验收

```text
[ ] 安装后能打开
[ ] 首次进入生成 2 个数字
[ ] 四方向滑动正确
[ ] 合并、计分正确
[ ] 重开后分数和棋盘重置
[ ] 最高分保留
[ ] 返回首页再打开，棋盘恢复
[ ] 强制停止 App 后打开，棋盘恢复
[ ] 切后台再回来，计时/动画暂停
[ ] 有弹窗时返回键先关闭弹窗
[ ] 状态栏和安全区不遮挡内容
[ ] 没有外部网络请求
[ ] 页面没有持续后台定时器
```

## 13. 交付标准

代码要有：

```text
index.html
app.css
app.js
module.json
```

不要引入构建工具。  
不要使用 npm 包。  
不要生成巨大资源。

完成后请给出：

```text
模块目录
module.json
打包命令
手动测试结果
```

## 14. 可选：测试通知

如果还想测试壳的通知链路，可以加一个不显眼的测试入口。

`module.json`：

```json
"capabilities": ["storage", "notify"]
```

页脚或设置区加一个小按钮：

```js
document.getElementById("testNotify").addEventListener("click", async () => {
  try {
    await docklet.notify.show({
      title: "2048",
      body: "这是一条测试通知"
    });
  } catch (error) {
    console.warn(error.code || error.message);
  }
});
```

这只是测试通知，不需要 `background` 能力，也不要让游戏在后台跑连接。

## 15. 参考

- 模块规范：`D:\docklet\docs\module-spec.md`
- SDK：`D:\docklet\shell\app\src\main\assets\docklet-sdk.js`
- 设计参考：`D:\docklet\apps\demo`、`apps\focus`、`apps\canvas`
- 颜色：`D:\docklet\designs\tokens.css`
