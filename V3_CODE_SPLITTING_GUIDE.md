# V3 代码拆分与按需加载建议

> 读者：V3 / 壳开发  
> 目的：不要把所有逻辑塞进单一 `v3.js`，按功能拆文件、按需加载，降低首屏解析和主线程压力。  
> 当前 V3 包：`v3.js` 约 198 KB，单文件。

## 1. 先明确

拆分不是目的。

```text
拆分能改善：
  首屏解析
  首次执行
  代码维护
  按页面懒加载

拆分不能自动改善：
  消息列表重渲染
  长任务
  DOM 数量
  点击后的同步计算
```

如果点击慢，拆分之外还要做：

```text
列表虚拟化
增量 DOM
Worker 解析
合并高频消息
避免 layout thrash
```

## 2. 推荐目录

```text
apps/dsh-console-beta/
  index.html
  src/
    main.js              启动、ready、路由
    router.js            页面切换
    store.js             会话/消息状态
    api.js               relay / fetch / SSE
    bridge.js            Docklet SDK 适配
    ui/
      dom.js             小工具
      shell.js           顶部栏/导航
      chat.js            聊天页
      sessions.js        会话列表
      settings.js        设置页（懒加载）
      avatar.js          头像/图片（懒加载）
      archived.js        归档页（懒加载）
    lib/
      markdown.js        解析（可放 Worker）
      format.js          时间/文本格式化
    workers/
      parse.js           大文本/JSON/Markdown 解析
  styles/
    base.css
    chat.css
    settings.css
```

原则：

```text
index.html 只加载启动入口
首屏需要的进 main.js
设置、头像、归档等页面用 import() 懒加载
```

## 3. 入口

```html
<script type="module" src="./src/main.js"></script>
```

`main.js`：

```js
import { createRouter } from "./router.js";
import { createStore } from "./store.js";
import { bootBridge } from "./bridge.js";

const store = createStore();
const router = createRouter({ store });

// 先渲染首屏
router.start();

// 首屏可交互后再标记 ready
await bootBridge();
docklet.ready();
```

不要：

```text
等所有页面模块下载完再 ready
在启动时同步解析全部大文件
```

## 4. 按需加载

设置页：

```js
async function openSettings() {
  const { mountSettings } = await import("./ui/settings.js");
  return mountSettings(store);
}
```

头像页：

```js
const { mountAvatar } = await import("./ui/avatar.js");
```

归档页：

```js
const { mountArchived } = await import("./ui/archived.js");
```

首屏后空闲预取：

```js
function prefetch() {
  import("./ui/settings.js").catch(() => {});
  import("./ui/avatar.js").catch(() => {});
}

if ("requestIdleCallback" in window) {
  requestIdleCallback(prefetch, { timeout: 2000 });
} else {
  setTimeout(prefetch, 1200);
}
```

第一次点击设置仍可能等待加载，所以预取很重要。

## 5. 拆分粒度

推荐：

```text
首屏入口        20–50 KB
聊天页          30–60 KB
设置页          15–40 KB
头像/归档       10–30 KB
```

不要：

```text
每个函数一个文件
50 个 2 KB 小文件
把首屏必需代码懒加载
拆完不做预取
```

本地模块每个请求都要走壳的 `shouldInterceptRequest` 和文件读取。  
**拆分收益来自“懒加载”和“首屏更小”，不是文件越多越好。**

建议控制在 8–12 个 JS 文件以内。

## 6. Worker

大解析不要放主线程：

```js
const worker = new Worker("./workers/parse.js", { type: "module" });
worker.postMessage({ text });
worker.onmessage = (event) => render(event.data);
```

适合：

```text
Markdown 渲染
大 JSON 解析
长文本分段
搜索结果构建
```

不适合：

```text
直接操作 DOM
依赖 window 的代码
小到几毫秒的格式化
```

Worker 也要按需创建；不要页面一打开就常驻多个 Worker。

## 7. 消息列表

V3 是多会话消息应用，点击延迟通常来自消息渲染。

优先做：

```text
只追加新消息，不重建整个列表
历史消息分页
长列表虚拟化
滚动位置保持
输入框和发送按钮不随列表重渲染
```

避免：

```text
每条消息到达都 innerHTML 全量替换
每次 render 重算全部时间/头像/Markdown
在滚动事件里同步读写 layout
```

## 8. CSS 拆分

首屏：

```text
base.css
chat.css
```

懒加载页面：

```text
settings.css
avatar.css
archived.css
```

可以用：

```js
import("./ui/settings.js");
```

由 settings.js 自己 `import("../styles/settings.css")`，但当前模块运行时不处理 CSS import 注入；  
更简单的是由页面 JS 动态创建 `<link rel="stylesheet">`。

首屏 CSS 不要拆得过细，避免样式闪烁。

## 9. 壳侧不需要改

模块服务器已经支持：

```text
.js / .mjs -> application/javascript
相对路径 import
同 origin 模块
```

CSP 允许：

```text
script-src 'self' 'unsafe-inline' 'unsafe-eval'
```

所以：

```js
import { x } from "./api.js";
await import("./ui/settings.js");
```

可以工作。

当前模块资源带：

```text
Cache-Control: no-store
```

所以拆分后不会依赖浏览器缓存；本地文件读取仍然很快，但不要拆成大量小文件。

## 10. 和“点击不跟手”的关系

拆分只解决：

```text
首屏 JS 太大
启动时解析太多
设置页拖慢聊天页
```

如果点击发生在聊天页，而点击处理里做了：

```text
解析 Markdown
重建整个消息列表
同步大量 DOM
等待网络
```

拆分不会救。

必须同时做：

```text
点击先给视觉反馈
重活放 requestAnimationFrame / setTimeout / Worker
只更新变化节点
长列表虚拟化
```

## 11. 迁移顺序

不要一次性重构。

```text
1. 从单文件抽出 api.js / store.js / router.js
2. 抽出 settings / avatar / archived
3. 设置页改 import()
4. 首屏后 idle 预取
5. Markdown/大解析移 Worker
6. 消息列表改增量渲染
7. 最后再做虚拟化
```

每步都要测：

```text
冷启动时间
首屏可点击时间
点击到视觉反馈时间
内存
```

## 12. 验收

```text
[ ] index.html 只加载一个入口
[ ] 首屏不加载设置/头像/归档代码
[ ] 第一次点设置无明显白屏
[ ] 首屏 JS 明显小于原 v3.js
[ ] Worker 不常驻多个
[ ] 聊天列表不再全量重渲染
[ ] 点击有即时视觉反馈
[ ] 冷启动没有比现在更慢
```

## 13. 结论

```text
可以拆
应该按页面/功能拆
应该懒加载
应该预取
不应该拆成几十个小文件
拆分不能替代渲染优化
```

V3 的多会话和消息列表，真正的点击手感来自：

```text
更小的首屏
更少的 DOM
更少的全量重渲染
更少的主线程长任务
```

拆分是基础，不是全部。
