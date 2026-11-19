# V3 Beta 完整 UI 验证

> 把现有完整 V3 UI 包成模块，验证壳 + SQLite + 网络 + SSE。  
> 通过壳的 **URL 安装** 分发，不再预置进壳。  
> 这是实验包，不是最终版本。

## 包

```text
D:\docklet\dist\dsh-console-beta-0.6.19.pkg
96,096 B
SHA256 7737F6E440EBC2F401F2763F4F1FA221E56CE176718051A7886CAFC474873666
```

构建：2026-11-16 23:07（包内 6 项：`index.html` / `module.json` / `v3.js` /
`avatars/` / `avatars/*.png`，与 `apps/dsh-console-beta` 源文件逐字节一致）。

安装：

```text
壳 -> 添加模块 -> 链接 -> 填 .pkg 地址
```

本机测试地址：

```text
http://192.168.2.6:8791/dsh-console-beta-0.6.19.pkg
```

服务器带 `Cache-Control: no-store`，换版本后手机重新拉即可，不需要清缓存。

## 已完成

```text
chrome:none 全屏
edge-to-edge、状态栏、IME safe-area
键盘弹起滚到最后一条
发送消息后滚到最后一条
会话切换不再整页 reload
任意会话通知（壳侧 /sessions 轮询）
通知开关关闭后真正停服务
URL 安装、APK 内不再预装 V3（见 V3_SHELL_GAPS 待壳确认项）
附件使用原生 onShowFileChooser
上传 / 下载能力
设置和缓存映射到 SQLite
恢复初始化清 SQLite 和 localStorage
v3.cache 只保留最近 24 份
审批/问答在消息流内，按 at/seq 插回时间线
审批/问答历史卡：同款卡片、状态、时间
审批/问答卡片已套 designs/v3-cards 的样式
turnActive latch：停止按钮和 working 气泡共用同一状态
working 气泡改为常驻节点：思考/输出同一个 DOM，固定 min-height
流式只更新气泡正文，不再每 150ms 整页重渲染
已删除「同步会话」页面和阻塞判断
所有入口创建空 state，消息按 id 合并
打开模块 -> 未配置直接是「设置」；已配置直接是对话页，不闪配置页
长回复（> 1.2 屏）气泡右下角有「回到开头」按钮，不占位、半透明
消息图片 blob 缓存上限 24，超出按最旧回收并 revoke
换网时局域网优先，外部地址自动兜底（LAN 回来就切回 LAN）
会话列表进缓存：先渲染上次的快照，再后台刷新
离线也能点进任何会话：有缓存显缓存，没缓存给提示
断线不弹黄条，标题从会话列表缓存回填
标题在渲染时兜底：titles 表 + 列表缓存双源
事件门不再卡 protocolVersion；回前台快照 30s 节流
多图按 1/2/3-4/5+ 分档网格；附件改成 chips
查看器支持同一气泡内左右滑动（计数 1/N）
查看器一次只滑一张，右上角有下载图标（无文案）
markdown 图片/链接解析改为线性扫描，修掉一行多图导致的整页卡死
对话里显示本轮失败原因（欠费 / 请求失败 / 出错）
失败后思考气泡立即收掉、停止按钮回退
键盘收起后收到新消息不再自动弹起
停止按钮与发送按钮合并，不再插拔闪现（同为 46×46，过渡 0.25s）
主动停止落一条本机缓存记录，渲染成助手消息行（头像/时间/复制，浅琥珀气泡）
多图消息里没进网格的孤图收成 160px 缩略图
查看器按「整条气泡」收图，孤图之间也能左右滑
查看器改成舞台+右下角控制条，双指缩放
查看器计数居中；下载按钮点图标也生效
图片在切会话/回前台时立即唤醒，失败有界重试
交付卡片进时间线且不再被新消息清空；清掉工具卡片死 CSS
交付按 afterId 定位 + 每回合一张折叠卡
时间线改为一次排好：消息+注记（审批/停止/交付），不再画完再挪 DOM
交付折叠规则：3 个以内平铺，超过 3 个露 3 个、其余折叠
交付锚到本轮的助手消息（不再挂到用户消息下面）
会话操作加「分支会话」；归档改为搜索框右侧按钮进入独立页面
接入壳 backgroundMessages：后台连接交给壳，页面可销毁
问答卡选项自动换行；「不回答」保留卡片，右上角标「已拒绝」
审批卡去掉不可点的 tool chip
交付卡片在快照合并时保留；卡片随 turn.end 立即出现
加载气泡分「思考/执行/输出」；设置页服务器行显示实际在用家里还是外部；对话页头「已连接」也带家里/外部
模型胶囊显示模型·推理等级，点击可切换；面板只保留「模型」标题和「保存后下次生效」提示
页头去掉状态块，已连接前加呼吸灯；点网络标签可快速切换
网络面板：模式间距拉大，测速同款边框；页头网络标签加背景
锁定家里/外部后，自动探测不再把你切回去
切会话不再闪「重连中」
两层判断；设置页手风琴默认关闭
回合产物；过程折叠；发送即显示；重复气泡修复
归档行补内边距；截图 v3-sessions / v3-archived
断线时页头呼吸灯与「重连中」一起闪色，输入框禁用并闪色提示
通知模式（回合结束/每条消息）随 /sessions 的 mode 参数交给壳
```

## 当前限制

```text
通知正文只有「有新消息」，没有消息预览          -> 壳侧，V3_SHELL_GAPS P0-4
前台正在看的会话仍可能收到后台通知               -> 壳侧，V3_SHELL_GAPS P0-5
通知模式壳侧还没读 mode（V3 已在 URL 里传）       -> 壳侧，V3_SHELL_GAPS P0-6
旧 action 记录没有 afterId / details，历史卡位置和内容不完整（新记录正常）
V3 bundle 仍是旧 minified bundle + localStorage shim，不是正式源码构建
```

## 0.3.45 气泡修复

旧实现的两个问题：

```text
working 气泡在 xe() 里是「正在输出 / 正在思考」两套模板，切换就换 DOM
$() 每次都 t.innerHTML = he()，150ms 一次整页重建
```

现在：

```text
#list 里拆出常驻的 #working
一轮只用同一个 .v3work 节点，data-state 在 thinking / streaming 间切换
固定 min-height 56px；思考态和输出态行高一致
流式只更新气泡正文，不触发整页/整列表重渲染
只有消息/审批/问答/交付结构变化才整页重渲染
turnActive 只在 stream.ended 由 false 变 true 时清掉
  工具间隙不会熄灭，turn 结束后也不会永久卡住
```

顺带修掉：连接状态变化没有触发重渲染，导致连上后发送按钮一直是禁用。

验证（本地 mock + headless Chrome）：

```text
thinking -> 7 次 delta -> 最终消息
工作节点只创建 1 次
思考/输出高度都是 94px
停止按钮在 turn 结束后消失
无 JS 异常
```

真机待测。

## 0.3.46-0.3.48 返回重进贴底

现象：返回首页再进模块，聊天不在底部，也不在上次位置；有时对有时不对。

根因是几个异步时序叠在一起：

```text
1. 进入时先滚到底，聊天里的图片是异步加载的
   图片加载完列表变高，滚动位置不动，就停在离底部一张图的高度
2. 键盘/安全区 inset 在 bridge ready 后到达，列表可视高度变化
   已经贴底的列表会重新出现一段底部空隙
3. turn.end 落点原来是「最后一条 assistant 对齐顶部」
4. 切换会话没有重置滚动状态，新会话沿用上一个会话的 scrollTop
```

修法：

```text
#list 里的内容包一层 #rows
#rows 和 #list 用 ResizeObserver 观察
  内容变高（图片/字体/工具卡）或可视高度变化（键盘/inset）
  只要本来贴底（e.stickBottom），就重新贴底
图片 onload 也重新贴底（ResizeObserver 不可用时仍有覆盖）
turn.end 落点改成贴底
切换会话重置 stickBottom / anchorOlder / unread / 计数
```

验证（真实 DSH 会话 + headless Chrome）：

```text
图片故意延迟 1.5s：加载前 gap=0，加载后 gap=0
可视高度 733 -> 453 -> 733（模拟键盘）：全程 gap=0
阅读态（滚到 30%）再改高度：不会被拉回底部
首次进入 / 连续 reload 3 次：gap=0
A 切 B、B 切回 A：gap=0
气泡回归：节点只 1 个，高度 94px
无 JS 异常
```

## 0.3.49 输入时跳动

现象：在底部输入，换行后继续打字，列表会隔一会往上被拉回一段，反复跳。

根因：输入框每次 input 都重新量高度：

```text
fe.style.height = "auto"      <- 先塌回 46px
fe.style.height = scrollHeight
```

中间那一瞬输入区变矮、列表变高，浏览器会把列表 scrollTop 按旧的最大值夹一次；
随后输入区再变高，列表变矮，但 scrollTop 不会自动补回来。ResizeObserver 只看
到首尾尺寸相同（421 -> 421），不会触发，所以停在夹过的位置。

修法：输入后如果本来贴底，直接把列表重新贴底一次。

验证（真实会话 + 模拟键盘 320px）：

```text
连续 6 次多行输入：每次输入后 60ms / 560ms 都是 gap=0
单行连续输入：gap=0
不再出现 21px / 44px 的回拉
```

## 0.3.50 已处理的问答又冒出来

现象：电脑端已经拒绝/答过的问题，手机上往下拉刷新后又出现一张「需要回答」的卡。

根因：`_t` 合并快照时，只要 cursor 变了（刷新、重连、新事件），走的是：

```text
prompts: a.prompts
```

也就是保留本地 prompts，不看这次快照返回的 `pending`。服务端 `pending` 已经空了，
本地还留着 pending 项，于是卡被复活。下拉刷新、回到前台、重连都会触发。

修法：

```text
合并时 prompts 以快照的 pending 为准
只保留 seq 大于快照 cursor 的本地 pending 项
（快照请求发出后才到达的事件，避免误删新问题）
```

验证（mock：服务端先给一张待回答卡，再置空 pending，然后 resnapshot）：

```text
0.3.49：初始 1 张，刷新后还是 1 张（复现）
0.3.50：初始 1 张，刷新后 0 张
新来的 pending 项不会被误删
```

## 0.3.51 进入会话的贴底意图

前面 0.3.46-0.3.49 修的是「内容变高后没有重新贴底」。还是偶尔进入不在底部，排查后
发现更根本的问题：

```text
进入会话时，代码没有"这次要停在底部"的显式状态
贴不贴底全靠两个运行时推断：
  p        = 生成新 DOM 前，旧列表离底部是不是 <90
  stickBottom = 任意一次 scroll 事件里，gap 是不是 <120
```

进入时有多次异步变化（缓存渲染、快照、图片、inset、键盘）。只要在某一刻读到
"列表不在底部"，`stickBottom` 就会被置 false；之后图片/键盘再变高，观察器也不会贴，
`Le` 还会按旧 z 恢复，于是停在中间，看起来随机。

修法：加一个显式的 `pinBottom`：

```text
进入（boot / 连接 / 切会话 / 从设置-会话页回聊天）时 pinBottom = true
pinBottom 期间：Le、ResizeObserver、图片 onload、turn.end、working 都贴底
只有用户真的在列表上 touch/wheel/pointer 时 pinBottom = false
```

这样"打开会话 = 看最新"由状态决定，不再依赖进入瞬间的 DOM 猜测；用户一上手滑动
就交还给正常阅读逻辑。

验证（真实会话 + headless Chrome）：

```text
进入 gap=0
模拟用户手势滚到 30% 再改可视高度：不被拉回（gap 保持）
reload：gap=0
内容再异步变高 400px：仍 gap=0
```

说明：桌面 headless 无法复现真机上"偶尔"的那次时序；0.3.51 不是再调时序，
而是去掉对那次时序的依赖。

## 0.3.52 启动落地页

> 0.3.55 起「未配置」的落地页改成设置页，独立配置页已删除；本节的
> 「未配置→配置页」只描述 0.3.52–0.3.54。

规则：

```text
没配置（没有 token/session） -> 打开就是连接配置页
已配置（有 token/session）   -> 打开直接是对话页，不再闪配置页
```

实现：

```text
isConfigured() = 有 token + 有 session，且不是主动断开
mount 时已配置 -> 直接 Re()，Re 创建 client 之后才第一次渲染
$() 的「没有 client」分支：
  已配置 -> 连接中…（不再是配置表单）
  未配置 -> 配置页
设置里断开连接（Ke）-> forceSetup，回到配置页
连接失败 -> forceSetup，配置页带错误
```

验证（headless Chrome + 帧采样）：

```text
已配置首次进入：t=72ms 首屏就是对话页（DSH / 重连中 / 开始和 DSH 对话），
                没有「连接 DSH」
已配置带缓存重进：首屏直接是对话（缓存消息），没有配置页
未配置：t=27ms 就是「连接 DSH」配置页
```

## 0.3.53 通知模式传给壳

现象（推演 + 契约核对，未在真机复现）：V3 设置里的「回合结束 / 每条消息」只影响
页面内的通知，壳的后台轮询永远按「每条消息」发通知，切到「回合结束」也没用。

根因在模块侧：`index.html` 的 `startWatchService()` 只把 `token` 拼进
`/sessions` URL，`policy.mode`（v3.js 的 `change` / 缺省）没有离开页面。
壳只能看到会话列表的 `lastMessageAt` 变化，拿不到用户的模式选择，
所以 V3_SHELL_GAPS P0-6 就算壳改完也不会生效。

修法（只改 URL 构造，不动 UI）：

```text
mode = policy.mode 是 change / message -> message
       其它（v3.js 的 turn 分支没有 mode 字段） -> turn
/sessions?token=<token>&mode=<turn|message>
token 为空时也带 mode（模式与鉴权无关）
```

壳侧契约：`mode=turn` 只在会话 `running` 由 true 变 false（或 turn.end）时通知；
`mode=message` 每条 assistant 消息都通知。参数缺失按 `turn` 处理。

验证：

```text
node tools/v3-watch-url-test.mjs
  6 个用例通过（message / message 显式 / turn 缺省 / 无 token / /health 带斜杠
  / endpoint 为空不发 start 调用）
  用例直接 eval 包内 index.html 的 startWatchService 源码，不复制逻辑
curl /sessions?token=…&mode=turn|message -> 200（服务端忽略该参数，不报错）
包内文件与源文件逐字节一致；8791 下载 SHA256 与磁盘一致
真机：待验收（需要壳先读 mode）
```

## 0.3.54 冷启动不拉起后台通知（0.3.52 的回归）

现象（headless 冒烟测试发现，未上真机）：模块已配置时冷启动直接进对话页、
状态显示「已连接」，但壳始终没收到 `background.start`；通知监听要等用户进设置
切一次「通知」或重连（走 `Ge()`）才会起来。

根因：0.3.52 的「已配置 → 直接进对话页」走 `Re()`。`Re()` 建 client 后挂的
`onStatus` 只有：

```js
e.connected = !!k.connected, e.route === "chat" && (W(), d());
```

而同步通知监听的 `K()` 只在 `Ge()` 的两个 `onStatus` 里调用：

```js
e.connected = !!k.connected, i.connected && (e.error = "", K()), e.route === "chat" && (W(), d());
```

所以启动路径永远不会同步通知状态。设置页里切通知/模式（1890/1897）和重连
（`Ge()`）都会调 `K()`，掩盖了这个洞——只有「配置好之后重启 App」这一条路失效。

修法：`Re()` 的 `onStatus` 与 `Ge()` 对齐，连接成功后调用 `K()`：

```js
e.connected = !!k.connected, k.connected && (e.error = "", K()), e.route === "chat" && (W(), d());
```

`K()` 内部用 `base|token|enabled|mode` 做签名，重复调用不会重复 start。

验证（`tools/v3-smoke.mjs`，headless Chrome + 真实 relay 8788）：

```text
0.3.53：对话页已连接，但 window.__bgCalls 为空 -> 复现
0.3.54：7/7 通过
  未配置 -> 「连接 DSH」配置页，无对话头
  已配置 -> 直接对话页，无配置页
  docklet.background.start 收到 /sessions?token=***&mode=message
  改为 message/turn 两种 localStorage 值，URL 里的 mode 跟着变
真机：待测（配置后冷启动，通知应直接进入监听）
```

冒烟测试怎么跑：

```powershell
node tools\v3-smoke.mjs
# 需要本机 8791/8788 可访问；token 自动读 ~/.dsh/phone-relay/token
```

它从 `apps/dsh-console-beta` 起静态服务、加载真实 `docklet-sdk.js`（mock 模式），
用 `Page.addScriptToEvaluateOnNewDocument` 在 SDK 赋值 `window.docklet` 的瞬间包住
`background.start` 记录 URL，因此验证的是真实页面行为，不是复制的逻辑。

## 0.3.55 去掉连接页：未配置直接进设置

规则（2026-11-15 03:05 用户截图确认）：

```text
首次打开、未配置 -> 直接进「设置」页，服务器分组默认展开
已配置           -> 每次打开直接进对话页
独立「连接 DSH」配置页删除
```

改法：

```text
$() 的无 client 分支：未配置不再渲染配置页，改为 e.route="settings" + Te()
设置页未连接时隐藏「返回」（此时返回无处可去）
删 ee() 模板、#connect/#base/#token 绑定、showConnectError、
  installConnectGuard、9 条 .v3setup CSS（index.html 少了 2.9 KB）
「保存并重新连接」改成 async：先 pickSession() 取 /sessions 的 current/第一条，
  再 Ge()。这段逻辑原来在被删掉的连接守卫里，删页面不能把「首次连接要选会话」
  一起删掉，否则首次连接没有 session，对话页是空的
```

验证（`tools/v3-smoke.mjs`，17/17）：

```text
未配置 -> 设置页，无 #backChat，无「连接 DSH」
未配置 -> 填家里地址+令牌 -> 保存并重新连接 -> 对话页
已配置 -> 直接对话页，不闪设置
mode=message / mode=turn 仍到达 docklet.background.start
截图 docs/screenshots/v3-landing-settings.png（设置页）
     docs/screenshots/v3-landing-chat.png（对话页）
真机：待测
```

启动帧采样（0.3.58 复核，MutationObserver 记录每一次重绘）：

```text
未配置：empty -> settings（从未出现 setup / chat）
已配置：empty -> chat @63ms（从未出现 settings / setup）
```

所以 0.3.55 之后「连接 DSH」页只可能来自旧版本模块：设置页底部会显示当前版本号
（v0.3.xx），先核对它。

## 0.3.56 长回复回到开头

需求（2026-11-15 03:30 用户圈图）：回复内容超过 1.2 屏时，在**气泡右下角**出现一个
向上按钮，点一下滚到这条回复的头像处。明确不做浮动按钮（不能压在正文上）。

实现：

```text
updateToTop(scope)：量 bubble.offsetHeight > list.clientHeight * 1.2 才加按钮
按钮 append 到 .v3bubble 末尾（0.3.56 是流内右对齐，0.3.57 改成右下角覆盖）
调用点：$() 全量渲染、paintWorking 流式气泡、图片 onload
点击：根节点委托的 click 分支，滚到 row.top - 8，并清 pinBottom / stickBottom /
      lastUserActionAt，否则 turn.end 的 800ms 自动贴底会把页面拽回底部
只对 assistant 消息（.row.me 跳过）
```

为什么用高度阈值而不是字数：代码块、表格、图片都算长度，同样的 800 字差别很大。

验证（`tools/v3-smoke.mjs`，13/13）：

```text
真实会话 3 条长回复：listH=677，气泡 894 / 2089 / 1125 —— 都在 1.2 屏以上
按钮数 = 长回复数 = 3，且都在 .v3bubble 内、position=static（不是浮层）
点第一条按钮：scrollTop 4771 -> 498，行顶部落在列表顶 +7.5px（头像贴顶）
截图 docs/screenshots/v3-to-top.png
真机：待测
```

## 0.3.57 按钮不占位

反馈（2026-11-15 03:36）：0.3.56 的按钮在流内，32px + 8px margin 把气泡底部撑空，
长回复末尾看着空一大块。

改法：

```text
.v3bubble 加 position:relative
.v3toTop 改成 position:absolute;right:7px;bottom:7px
背景 rgba(255,255,255,.66) + 半透明边框；深色模式 rgba(17,26,32,.62)
气泡高度不再包含按钮，底边就是正文底边
```

代价：会压住最后一行右侧的一两个字。半透明后仍能读出来；正常情况下最后一行只有
一两个字碰到按钮，可接受（用户确认）。

验证（`tools/v3-smoke.mjs`，13/13）：

```text
按钮断言从 position=static 改为：
  position=absolute、offsetParent=.v3bubble、背景 alpha < 1
真实会话 3 条长回复：按钮数=3，点击 scrollTop 6379 -> 474，
  行顶落在列表顶 +8.3px
截图 docs/screenshots/v3-to-top.png（气泡右下角覆盖，无占位空白）
真机：待测
```

## 0.3.58 图片 blob 缓存回收

缺陷：消息图片用 `URL.createObjectURL` 给了 blob URL，缓存在一个 Map 里（`r`），
只 `set` 从不删，也没有 `revokeObjectURL` —— 一个会话看过的图片会一直占内存，
越聊越多。

修法：

```text
IMAGE_CACHE_LIMIT = 24（约一屏 + 附近消息，估 6–8 MB）
pruneImageCache(cache, limit, isLive)：按插入顺序淘汰最旧的，revoke 掉
isLive = document.images 里还有 <img> 正在用这个 URL 就跳过
  包括聊天气泡和查看器里放大的那张，避免正在看的图变空白
命中缓存时 delete + set 刷新顺序（LRU）
revoke 抛异常吞掉（重复 revoke 不应中断回收）
```

验证（`node tools/v3-image-cache-test.mjs`，9/9）：

```text
30 条 / 上限 24：保留最新 24，revoke 最旧 6 条，最新的没被动
最旧那条仍在屏上：跳过它，改 revoke 第二旧
全部在屏上：一条都不删（不会为了凑数把可见图弄没）
未到上限 / limit=0：不动
revoke 抛异常：仍然把缓存降到 24，不中断
用例直接 eval 包内 pruneImageCache 源码，不复制逻辑
```

回归：`node tools/v3-smoke.mjs` 13/13（聊天、落地页、mode、长回复按钮）。
真机：待测（多图会话滚动，再回看旧图应能重新加载）。

## 0.3.59 全局返回手势接入（壳对话改的）

链路：

```text
壳：MainActivity 全局返回栈 + BackStep 接口
    ModuleRuntimeHost.dispatchBack(callback)
      evaluateJavascript("window.__dockletBack&&window.__dockletBack()?1:0")
      1 -> 消费；0 -> 先试 WebView 历史，再交给 ModuleActivity 关模块
    ModuleActivity.backPending 防抖：等待期间再按一次直接关
SDK：docklet.onBack(handler)，window.__dockletBack 从后往前调用，返回 true 表示已消费
V3：docklet.onBack(tt)
    tt()：图片查看器 -> 个人资料 -> 动作/新建 -> 会话列表/设置 -> 聊天
         都不是则返回 false，让壳关模块
```

验证（`tools/v3-smoke.mjs`，20/20，新增三条）：

```text
SDK 暴露 window.__dockletBack
在设置页调用 -> 返回 true 且回到对话页（消费）
在对话页调用 -> 返回 false（壳随后关模块）
```

已知不同步：0.3.59 包里 `module.json` 是 0.3.59，但 `index.html` 的
`mount({manifest:{version}})` 还是 0.3.58，所以**设置页底部显示 v0.3.58**。
功能不受影响；看模块详情页的版本才准。下次构建把两处对齐。

## 0.3.60 局域网优先的换网

需求：平时优先走局域网；出门后自动切到外部地址；回家再切回来。

方案（借 v1 的简单模型：一条连接、断了就重连，只多一步选地址）：

```text
pickBase()：LAN(1.2s) 与 WAN(2.5s) 并行探 /health，两边都通时 LAN 赢
rediscover(reason)：选中且和当前不同才换；换 = 写 v3.base + Ge() 重建 client
触发：navigator.connection.change / online / 回到前台 /
      SSE 断开后 1.2s / 20s 定时（未连接，或已连外部时探一次家里）/ 启动后 1.2s
退避：300 -> 600 -> 1200 -> 2000ms（v1 是固定 3s）
去掉了原来 15s 的健康轮询和串行 5s 的 WAN 探测
```

顺带修掉一个会让页面卡在「连接中…」的 bug：`Ge()` 原来先 `$()` 再创建 client，
`$()` 那一刻 client 还是 null，于是渲染加载页；等快照回来 `chatSig` 和上一次相同，
`d()` 只走 `paintWorking()` 不做整页重渲染，页面就永远停在加载页。
现在 `Ge()` 先建 client 再 `$()`，并且加载分支会清空 `renderSig`。

验证（`tools/v3-net-switch-test.mjs`，真实 relay + 本地代理模拟 LAN，3/3）：

```text
两边都通 -> 连在 LAN 地址（局域网优先）
掐掉 LAN 代理 -> 1296ms 切到外部地址并恢复连接
恢复 LAN 代理 + online 事件 -> 1578ms 切回 LAN 并恢复连接
```

回归：`node tools/v3-smoke.mjs` 20/20。

已知限制：

```text
切换仍会整页重渲染一次（Ge 里的 $()），长会话有一帧闪烁；后续可改成只换 transport
页面在后台时不探测，回到前台才扫
```

## 0.3.61 会话列表缓存

现象：会话列表每次打开都是先空一下（`e.sessionsLoading` 时整页「加载中…」），
断网打开更是空列表；聊天内容有缓存，唯独列表没有。

实现：

```text
localStorage("v3.cache.sessions") = { at, items[≤60] }
写：Ce() 拉取成功后落盘（只留列表要用的字段）
读：mount 时 e.sessions = readSessionCache()，打开列表直接有内容
加载态：只有缓存为空才显示「加载中…」
失败：有缓存就不覆盖，也不把错误横幅盖在列表上
恢复初始化：v3.* 全清，缓存一起没
```

缓存键沿用 `v3.cache.` 前缀，所以会被 localStorage shim 持久化进 SQLite；
现有 `pruneCache()` 只保留最近 24 个 `v3.cache.*` 键，会话列表缓存更新频繁，不会被挤掉。

验证（`tools/v3-smoke.mjs`，22/22）：

```text
首次打开列表 -> 写入 9 条缓存
刷新页面后打开列表 -> 2ms 内渲染出缓存内容（不等网络）
换网 3/3、其余 20 项回归不变
```

真机：待测（断网打开列表应还能看到上次的会话）。

## 0.3.62 离线点会话：能进、有提示

现象：断网时点会话列表，有的会话没反应；能进去的也是空白，看起来像坏了。

根因两条：

```text
Pe() 切换会话后不自己渲染，等新 client 的回调去 d()；断网时回调迟迟不来 -> 停在列表
Pe() 开头有 !e.client 的硬门槛，client 为 null 时点了直接 return
```

修法：

```text
Pe()：去掉 !e.client 门槛；先写 state/route 并立即 $()
      有缓存就渲染缓存，没缓存渲染空态；离线时直接 return，不建 client
空态：!connected 且（offline 或 navigator.onLine === false）时
      📡 连接不到网络 / 也没最近缓存 + 重试按钮
      已连接时仍是「开始和 DSH 对话」
离线：offline 事件里 client.stop() + connected=false
上线：client 不存在就 Re()，连过就 rediscover，否则 client.start()
重试：空态按钮与底部网络条都先看 navigator.onLine，没网只提示，有网才重连
```

验证（`tools/v3-smoke.mjs`，25/25，新增三条 + 截图）：

```text
CDP 断网 -> 点无缓存会话 -> 立即出现「连接不到网络 / 也没最近缓存」
断网点有缓存会话 -> 缓存消息照常显示
恢复网络 -> 重新连上（已连接）
截图 docs/screenshots/v3-offline-empty.png
换网 3/3 回归不变
```

## 0.3.63 断线无感 + 标题回填

现象（2026-11-15 14:37 真机截图）：

```text
断线时底部弹出黄色「连接断开，正在重连…」+ 大号重试按钮，太吵
空对话仍显示「开始和 DSH 对话」，没有离线提示
会话列表里有标题，点进来标题却是 DSH
```

三个根因：

```text
Y() 对「重连中/网络已断开」也显示条 —— 页头本来就有「重连中」，重复了
离线空态只在 navigator.onLine===false 时判定；手机开着 VPN/4G 但中继不可达时
  navigator.onLine 是 true，所以提示不出现
聊天标题取 e.state.session.title；无消息缓存时 emptySessionState 的 title 是空，
  而标题其实在会话列表缓存里
```

修法：

```text
Y()：只有 e.error（发送失败/下载失败等）才显示，断线一律不弹条
空态：offline || navigator.onLine===false || attempt>0 才显示离线提示；
      首次连接（attempt=0）显示「连接中…」，避免正常连接时闪提示
status 回调统一到 onStatusUpdate()，用 offlineHint 标记只在提示切换时重渲染一次
      （避免每次退避重试都刷整页）
标题：applySessionTitle(id) 从 v3.cache.sessions 回填 session.title，
      Pe/Ge/Re 三处都调用
```

验证（`tools/v3-smoke.mjs`，27/27）：

```text
断网进无缓存会话 -> 提示正常；标题等于列表缓存里的标题（不再是 DSH）
断网时 #netBar 保持 hidden（无黄条）
断网进有缓存会话 -> 消息正常；恢复网络自动连回
换网 3/3 回归不变
```

## 0.3.64 标题渲染时兜底

现象（0.3.63 真机）：断网从列表点进有缓存的会话，标题仍是 DSH，列表里明明有标题。

分析：0.3.63 只在 `Pe/Ge/Re` 给 `e.state.session.title` 回填一次。只要状态不是这三条
路径产生的（或者状态里的 session 先被别的写入覆盖），渲染就回到 `i.title || "DSH"`。

改法：标题不再依赖「状态里有没有」，渲染时兜底：

```text
sessionTitleOf(id)：先查 v3.cache.titles（Ce() 成功时顺手写的 id->title 表），
                    再退回 v3.cache.sessions 的 items
头部渲染：i.title || sessionTitleOf(i.id || e.session) || "DSH"
applySessionTitle 也改成用 sessionTitleOf（两个来源都能用）
```

验证（`tools/v3-smoke.mjs`，29/29，新增冷启动一条）：

```text
离线从列表进有缓存会话 -> 标题 = 列表缓存标题
离线进无缓存会话 -> 标题 = 列表缓存标题 + 离线提示
离线冷启动（fetch/EventSource 全失败）-> 标题仍来自缓存
```

## 0.3.65 断线呼吸灯 + 输入框提示

需求（2026-11-15 14:56）：页头「重连中」太干，加呼吸灯；输入框给
「网络连接中，请检查网络配置」并禁用。

实现：

```text
页头：connState 未连接时渲染 <i class="v3condot"></i>重连中
      .v3condot 复用 @keyframes v3pulse（1.2s 透明度呼吸，琥珀色）
      W() 改成写 innerHTML，避免 textContent 把呼吸灯冲掉
输入框：!connected 时 placeholder=「网络连接中，请检查网络配置」+ disabled
      「＋」附件按钮与发送按钮同步禁用
样式：textarea:disabled 虚线边框 + 浅灰底
重绘：onStatusUpdate 里 connected 变化时整页重绘一次（带焦点恢复），
      否则占位符和禁用态不会跟着变
```

验证（`tools/v3-smoke.mjs`，32/32）：

```text
断网 -> #text 禁用且占位符是「网络连接中，请检查网络配置」
断网 -> #connState 里出现 .v3condot 呼吸灯
恢复网络 -> 输入框重新可用
截图 docs/screenshots/v3-offline-empty.png
```

## 0.3.66 呼吸灯尺寸对齐 + 输入框提示闪色

```text
.v3condot 6px -> 7px，和左侧状态灯 .v3dot 一致（margin 也统一成 4px）
textarea:disabled::placeholder 加 v3phflash 动画：灰(#9aa6b2) <-> 琥珀(#d9a441)，1.6s
```

验证（`tools/v3-smoke.mjs`，34/34）：

```text
占位符 computed animationName = v3phflash
.v3condot 宽度 = .v3dot 宽度 = 7px
```

## 0.3.67 提示改真元素闪色 + 灯对齐

真机反馈（2026-11-15 15:06）：

`	ext
输入框占位符不闪：::placeholder 上的 color 动画在 Android WebView 里不生效
呼吸灯比左侧状态灯低一点：.v3sub 是 flex，但 connState 里的 <i> 靠 vertical-align 排，基线不同
「重连中」文字不醒目
`

改法：

`	ext
不再依赖 ::placeholder 动画：输入框外包 .v3textwrap，
  断线且没有草稿时叠一个 .v3connhint 真元素（pointer-events:none），在它身上跑 v3phflash
对齐：.v3sub > span 改成 inline-flex + align-items:center + gap:4px，
  去掉两个灯各自的 margin/vertical-align
文字：重连中包一层 .v3conntext，同样跑 v3phflash（灰 <-> 琥珀）
`

验证（	ools/v3-smoke.mjs，35/35）：

`	ext
.v3connhint 存在且 animationName = v3phflash
.v3conntext animationName = v3phflash
呼吸灯与状态灯的垂直中心完全一致（73.734375 vs 73.734375）
断线禁用、恢复可用、标题、缓存、换网等回归全过
`

## 0.3.68 两端兼容性审计后的 V3 侧修正

2026-11-15 15:33 对 phone-relay 插件做了只读审计（不动数据），结论：

```text
对得上：鉴权、/sessions 字段、/snapshot 的 events/cursor/hasMore/oldestSeq/pending/activity、
        /options 的 presets/permissions/models.groups、SSE 只有 data: 行的格式、
        事件类型（message.*/tool.*/turn.end/session.meta/approval/input/deliverable）、
        写接口（/target + /send + /stop + 审批/回答，真机已在用）
插件已改：turns 生效了（turns=1 返回 6 条，turns=2 返回 49 条，turns=3 返回 76 条）
插件仍缺：after 不生效（after/afterSeq/since/from/afterCursor/cursor 六种写法都返回整段尾巴）
未验证：/rename、/new、/session/archive 的请求体参数名（需要改数据，没试）
冗余：module.json 声明了 files/clipboard，V3 实际不用（不冲突）
```

V3 侧两处修正：

```text
Rt() 不再要求 protocolVersion 严格等于 1.0
  插件哪天升级到 1.1，老写法会静默丢掉所有事件；现在只看 sessionId / eventId / type
visibilitychange 的快照加 30s 节流
  after 不生效时每次回前台都要拉 78 KB 尾巴，切几次 App 就白拉几百 KB
```

验证：

```text
node tools/v3-event-gate-test.mjs   8/8（1.0 / 1.1 / 缺失 / 会话不符 / 缺 id / 缺 type / null）
node tools/v3-smoke.mjs             35/35
node tools/v3-net-switch-test.mjs   3/3
```

给插件侧的建议（未做）：

```text
/snapshot 支持 after（增量），或提供 /snapshot?since=<seq>
before 分页也按 turns 限条数（现在 turns=3 已经是 76 条，一次 78 KB）
```

## 0.3.69 多图与附件展示（补完 v1 的规格）

溯源：v1 的 `na()` 已经按图片数量分好四档类名
（`is-one / is-two / is-few / is-many`），但 CSS 只有一条 `repeat(2,1fr)`，
四档从没实现；消息图片又是每张一个 `<img class="v3img">` 直接摞进气泡，
markdown 单图还是 `max-width:100%`，所以长截图会铺满整屏。

现在补完的规则：

```text
1 张     一行，最大 320px，保持原始比例，不裁切
2 张     两列，1:1 裁切（object-fit:cover）
3-4 张   两列
5+ 张    三列，多行
markdown 单图   max-width: min(320px, 72%)
消息图片和 markdown 图片共用 .chat-md-gallery 容器，点击仍打开查看器
附件（files） 一行 chips：图标 + 文件名（超长省略） + 大小，自动换行
```

实现：

```text
galleryHtml(images)：按数量选 is-* 类，data-count 便于以后调
filesHtml(files)：chip 容器，替代原来一行一个块级链接
CSS：四档 grid + 方形裁切 + is-one 还原比例 + chips 样式（含深色）
```

验证：

```text
node tools/v3-gallery-test.mjs   14/14（1/2/3/4/5 张类名、count、image-id、chip、转义）
node tools/v3-smoke.mjs          38/38（单图 320px；两张并排；五张三列两行）
node tools/v3-net-switch-test.mjs 3/3
```

取舍：2 张以上用 1:1 裁切，长截图会被裁掉上下；如果更想保留完整内容，
把 `aspect-ratio:1/1` 改成 `4/3` 或改回 `object-fit:contain` 即可。

## 0.3.70 查看器左右滑动

反馈：同一个气泡里的多张图片只能单张看，v1 是可以左右滑动的。

原因：查看器只有一张 `#viewer-img`，点哪张就赋哪张的 src，没有图集。

改法：

```text
#viewer 拆成 X 轴 scroll-snap 的 #viewerTrack + 计数 #viewerCount
点任意图片 -> openViewer(img)
  取它所在 .chat-md-gallery 的全部 img（没有 gallery 就它自己）
  生成 N 个 .v3viewer-page，先滚到被点的那一页
  滑动时用 scrollLeft / clientWidth 更新「2 / 5」
关闭时清空 track（释放 DOM 与 blob 引用）
单张时计数隐藏，行为与以前一致
```

验证（`tools/v3-smoke.mjs`，41/41，新增三条）：

```text
点第 1 张 -> 查看器打开且有两页
滑到第 2 页 -> 计数 1/2 变成 2/2
关闭 -> viewer 隐藏且 track 清空
```

## 0.3.71 查看器：逐张停住 + 保存

反馈：滑动太灵敏，轻轻一划就跑好几张；顶部关闭按钮左边要一个保存。

改法：

```text
滑动：.v3viewer-page 加 scroll-snap-stop:always
  一次手势最多停到下一张，不会一划越过好几页
保存：#saveViewer 放在关闭按钮左边（right:58px，同样 36px 高）
  取当前页的图片 blob -> base64 -> docklet.files.write('downloads/dsh-<时间戳>.<ext>')
  -> docklet.files.download(path, {mime})，壳写进 Downloads/Docklet
  文件名 dsh-YYYYMMDDHHmmss.png/jpg/webp/gif
  没有 files 能力时退回 docklet.download(blob url)，再不行提示「当前壳没有保存能力」
  成功后 toast「已保存到 Downloads/Docklet」
```

index.html 里补了 mount 选项 `saveImage(data, name, mime)`，V3 不直接碰全局 docklet。

验证（`tools/v3-smoke.mjs`，44/44，新增四条）：

```text
.v3viewer-page 的 scrollSnapStop = always
保存按钮在关闭按钮左边（rect 不重叠）
点保存 -> toast「已保存到 Downloads/Docklet」
查看器两页、计数、关闭清空都正常
```

限制：走 files 能力，单文件上限 5 MB（壳 ModuleFiles 的配额），超大原图会失败并提示。

## 0.3.72 保存按钮改成图标

```text
「保存」两个字换成下载图标（SVG：箭头 + 托盘），36x36 圆形，和关闭按钮同款
aria-label 仍是「保存图片」，功能不变
```

验证：smoke 45/45（新增「按钮里只有 svg、没有文字」一条）。

## 0.3.74 一行多图导致整页卡死（markdown 灾难性回溯）

现象（2026-11-15 16:45 真机）：显示「重连中」，点什么都点不动，像卡死；电脑侧 8788 其实正常。

复现：headless 打开同一会话，页面主线程 100% CPU，CDP 的 Runtime.evaluate /
Page.captureScreenshot 全部超时。用「第二个页面读 localStorage 痕迹」的办法定位到
最后进入的函数是 `yt()`，再用 worker + 超时把 43 条消息逐条跑，锁定一条：

```text
![1](docklet-file:.../mark-bee.png) ![2](...) ![3](...) ![4](...)
```

根因：判断「整行是不是一张图」的正则

```js
const Je = /^\s*!\[([^\]]*)\]\(((?:[^()\s]+|\([^()\s]*\))+)\)\s*$/
```

URL 部分是嵌套量词 `(?:A|B)+`，一行 4 张图时整行匹配失败，回溯是指数级；
主线程被占满后网络回调也停，于是页面一直显示重连、点击全无响应。
`be()`（行内渲染）里的图片/链接子模式有同样问题，长文本未闭合时也会卡。

修法：

```text
新增 scanInlineParen / parseImageLine / parseImageRun，用线性扫描替代 Je
  （括号配平、禁止空白，语义与原正则一致）
be()：先用线性扫描取出图片/链接换成占位符，剩下的 code/bold/italic/URL 用一个
  不含嵌套量词的简化正则处理，最后还原占位符
一行 ≥2 张图（空格分隔）也直接进 gallery，按数量分档
```

验证：

```text
node tools/v3-markdown-test.mjs   10/10
  四图一行：不再超时，且渲染成 is-few 画廊
  五图一行：is-many；单图：is-one；两行两图：is-two
  未闭合图片 400 字 / 未配平链接：不卡
  加粗/行内代码/链接/图文混排正常
node tools/v3-smoke.mjs           45/45（原先会卡死的会话现在能正常渲染）
node tools/v3-net-switch-test.mjs 3/3
```

教训：markdown 解析里不要再写 `(?:[^x]+|\(...\))+_# V3 Beta 完整 UI 验证

> 把现有完整 V3 UI 包成模块，验证壳 + SQLite + 网络 + SSE。  
> 通过壳的 **URL 安装** 分发，不再预置进壳。  
> 这是实验包，不是最终版本。

## 0.3.75 失败返回显示在对话里

需求（v1 行为）：模型调用失败（欠费、400、限流等）时，控制台会把失败原因返回给用户；
V3 只把状态点变红，聊天里什么都没有，看起来像「没反应」。

协议：relay 的失败是一条 `message.error` 事件，payload 形如
`{ code, status, hint, message, retryable }`；另外 `turn.end` 的
`reason.kind` 可能是 `error` / `aborted`。

实现：

```text
session 状态加 turnError: { message, code, at }
Ct()：message.error -> turnErrorText(payload)（message 优先，退回 hint，再退回「执行失败」）
       turn.end 且 reason.kind==="error" 且没有 message.error -> 补一条「执行失败」
       aborted 不动；message.user 清空
发送时乐观清空 turnError
聊天列表末尾渲染红色卡片：本轮失败 / code / 正文（暗色适配）
chatSig 带上 turnError，状态变化能触发重渲染
```

顺带修：换网 6s 冷却期内的触发改成排队重试（之前直接丢，回家要等 20s 定时器）。

验证：

```text
node tools/v3-turn-error-test.mjs  11/11
  402 余额不足：message / code / 时间都保留
  只有 hint：退回「执行失败」；只有 status：code 用 status
  没有 payload：仍显示「执行失败」
  卡片渲染、HTML 转义、无错误不渲染
node tools/v3-smoke.mjs            45/45
node tools/v3-net-switch-test.mjs  3/3（回 LAN 6.2s，含冷却排队）
```

## 0.3.76 失败后气泡一直在闪

现象（2026-11-15 17:25 真机截图）：模型 TRANSPORT 失败，底部红卡已出现、电脑端已停止，
但「DSH助手 正在思考」气泡一直闪，输入框旁边的「停止」也不退。

根因：`applyTurnEvent()` 只在「未结束 -> 结束」的跳变里清 `turnActive`：

```js
if (ended && !e.turnEnded) e.turnActive = false;
e.turnEnded = ended;
```

上一轮成功结束后 `turnEnded` 一直是 `true`；本轮若直接失败（TRANSPORT，没有任何
`message.assistant.delta`），没有任何事件把它重置，于是 `turnActive` 卡在 `true`，
`workingState()` 一直返回 `"thinking"`。

为什么以前没发现：中途失败会先有 delta（`ended=false`），`turnEnded` 被重置，
下一次 `ended=true` 就是真跳变，所以只在这条「一上来就失败」的路径上复现。

修法：

```text
applyTurnEvent：只要 ended 为真就 e.turnActive = false（不再依赖跳变）
workingState：activity.status === "error" 直接返回空（错误卡已说明问题）
发送时（Ze）：e.turnEnded = false，让“结束”回到每轮一次
chatSig：带上 e.turnActive 和 activity.status，否则气泡收了、停止按钮还在
```

验证：

```text
node tools/v3-turn-active-test.mjs  8/8
  上一轮已结束 + 本轮 error：turnActive 清掉、气泡为空
  activity=error 即使 turnActive 卡住也不显示思考
  正常 running 仍显示 thinking；流式文本优先 streaming
  delta 会把 turnEnded 重置
  chatSig 含 turnActive / activity.status
node tools/v3-smoke.mjs             42/42（本次会话最后 3 轮较短，to-top 三项 SKIP）
node tools/v3-net-switch-test.mjs   3/3（回 LAN 6.2s，含冷却排队）
```

## 0.3.77 收到新气泡时键盘自动弹起

现象（2026-11-15 17:43 真机）：在电脑端对话，手机只是放在旁边；新气泡一出现，
手机的软键盘就被顶起来了。

根因：`$()` 每次重绘都会“恢复焦点”：

```js
const g = t.querySelector("#text"), v = g && document.activeElement === g, ...
if (v) fe.focus();
```

Android 上用返回键收起键盘时，输入框的**焦点还在**（activeElement 没变）。
电脑端一发消息 → 手机收到事件 → `$()` 重绘 → `v` 为真 → `fe.focus()` → 键盘又弹出来。

修法：只在键盘确实开着时才恢复焦点。

```text
trackKeyboard()（每次 $() 调一次，只装一次监听）
  visualViewport.resize：vv.height + 120 < innerHeight 视为键盘打开
  focusin（#text）：点击输入框即视为打开，避免首帧 resize 未到
shouldRestoreFocus(doc, el, keyboardOpen)
  el 存在 && 焦点在 el && keyboardOpen !== false
$() 与 onStatusUpdate 的重新聚焦都改用它
```

验证：

```text
node tools/v3-focus-test.mjs        7/7
  焦点在 + 键盘开 -> 恢复；键盘关 -> 不恢复；未知（桌面）-> 恢复
  焦点不在 / 没有输入框 -> 不恢复；代码里确实用 visualViewport 跟踪
node tools/v3-smoke.mjs             42/42（本次会话最后 3 轮较短，to-top 三项 SKIP）
node tools/v3-net-switch-test.mjs   3/3（6248ms 回 LAN；重复跑偶发卡在收尾，是脚本自身问题）
```

## 0.3.78 停止按钮不再闪

现象（2026-11-15 17:55 真机）：红色「停止」按钮出现和消失时非常闪。

两个原因叠在一起：

```text
1. 0.3.76 为了让停止按钮跟上状态，把 e.turnActive / activity.status 放进了 chatSig
   -> 活动状态一变就整页 $() 重绘，按钮节点被销毁重建，视觉上就是闪
2. 停止按钮本来是插在发送按钮旁边的独立节点（active 插入、结束删除）
   -> 位置和宽度突变，一次交互里能闪两下
```

修法：

```text
合并成一个 #send 按钮，在「↑ / … / 停止」之间变形（class is-stop + 文本）
  颜色/内边距用 CSS transition，节点始终存在，不再插拔
chatSig 去掉 turnActive / activity.status，回到「只有内容变化才整页重绘」
paintWorking 本来就原地同步：syncChatHeader / syncSendButton / syncStopButton
删除 #stop 节点、它的绑定和 .v3stop 样式
```

验证：

```text
node tools/v3-turn-active-test.mjs  8/8（更新断言：chatSig 不因活动状态重建；
                                    paintWorking 原地同步 header/send/stop）
node tools/v3-focus-test.mjs        7/7
node tools/v3-smoke.mjs             42/42
```

## 0.3.79 停止按钮与发送按钮同尺寸 + 过渡 0.25s

上一条把两个按钮合并后，红色「停止」是 `width:auto` + 内边距，宽度比发送按钮宽
（46 -> 约 60），视觉上仍然像“换了个按钮”。

改法：

```text
#send 固定 46x46，两种状态都不改宽高（只改背景/文字）
display:flex + center，保证「↑」和「停止」都垂直居中
停止文字 13px（26px 宽），46px 内放得下
transition: background/color 0.25s
```

验证（`tools/v3-smoke.mjs`，44/44，新增两条）：

```text
stop button keeps the send size  46x46 -> 46x46
stop morph animates 0.25s        0.25s, 0.25s
```

## 0.3.80 主动停止留一条本地记录

需求：我停止之后，手机端要能看出来「这一轮是被我停的」，电脑端停止也一样；
重新打开 App 时也要在，否则「我说了一句、你没有回复」看起来像断线。
但**不能污染上下文**——只是把返回的 `turn.end(aborted)` 变成一个静态展示。

实现：

```text
Ct()：turn.end 且 reason.kind === "aborted" -> state.stoppedTurn = { turn, at }
subscribe 回调：noteStops(state)
  key = "stop-" + turn，去重
  by = 最近 10s 内点过停止 ? "phone" : "pc"
  落 localStorage["v3.stops.<session>"]，上限 40 条
  stops 里带 afterId（停止时最后一条消息），渲染时插在它后面
```

文案：

```text
手机点停止   你已停止本轮
电脑端停止   已在电脑端停止本轮
判断不出来   本轮已停止
```

展示：助手侧一条浅灰小字（■ + 文案 + 时间），没有复制、没有气泡尖角，像「这一轮到此为止」。

边界：

```text
不进 state.messages / 快照缓存（`Pt` 只存 session/messages/prompts/deliverables/activity）
不发 /stop 以外的东西，模型看不到
手机点停止后由 turn.end 事件落库，避免「本地点一次 + 事件再来一次」两条
换会话/重连时重置 stopNoteSig，不会把旧 turn 的键套到新会话
```

验证：

```text
node tools/v3-stop-test.mjs   16/16
  三档文案、按 id 去重、上限 40、afterId 保留、渲染锚点、本机/远端判定
  记录只写 v3.stops.*，不碰 messages / state
  源码层面确认 Ct 记录 aborted、三处 subscribe 调 noteStops、渲染走 placeStopRecords
node tools/v3-smoke.mjs        47/47（含停止按钮 46×46 / 0.25s）
```

## 0.3.81 停止提示伪装成正常消息

反馈：灰字一行的停止提示太不像对话，应该和正常回复一样——头像、名字、时间、复制按钮都在，
只是气泡换一个颜色提醒这是状态。

改法：

```text
stopsHtml() 改成完整消息行：
  <div class="v3row v3stoprow" data-stop-after>
    <div class="v3meta-row"><头像><DSH助手><时间><复制></div>
    <div class="v3bubble v3stopbubble">你已主动停止本轮</div>
  </div>
复用 xe() 的 v3row / v3meta-row / v3av / v3bubble / v3copy 结构
复制按钮走现有的 [data-copy-message] 委托（复制气泡文本）
气泡：浅琥珀 #fdf5e4 / 边 #f0e0bb / 字 #8a6d3b；暗色 #2a2418 / #4a3d22 / #d8c08a
placeStopRecords 改为移动 .v3stoprow
```

仍然不污染上下文：记录只存在 `v3.stops.<session>`，不进 messages、不进快照、不发给模型。

验证：

```text
node tools/v3-stop-test.mjs  18/18（新增：消息行结构/头像/复制/琥珀气泡；旧灰行已删）
node tools/v3-smoke.mjs      49/49（注入一条 stop 记录后重载：行结构、复制按钮、气泡颜色）
截图 docs/screenshots/v3-stop-note.png
```

## 0.3.82 多图消息里的孤图收成缩略图

现象（2026-11-15 18:54 真机）：三张图之间有空行、前后还有说明文字，结果每张图各自成块，
渲染成三个 320px 的单图，一列堆下来，像三张奇形怪状的大图。

原因：`wt()` 先按空行切块，每个块只剩一张图，`yt()` 里的多图合并逻辑够不到——
所以是「同一组图被拆成多个单图块」。

规则（不合并、只收尺寸，最小改动）：

```text
一条消息里出现 ≥2 个「孤图」（各自成块的 is-one）
  -> 全部加 is-thumb，宽高上限 160 x 200，object-fit:contain（不裁切）
  -> 点击仍然打开查看器看原图
只有一张图的消息
  -> 保持原样，最大 320px
天然小于上限的小图
  -> 不受影响（max-width 只封顶，不放大）
```

实现：

```text
markLoneThumbs(html)：数 chat-md-gallery is-one 的个数，>1 就替换成 is-one is-thumb
用于 xe()（消息列表）和 paintWorking()（流式气泡）
CSS：.chat-md-gallery.is-one.is-thumb{grid-template-columns:minmax(0,160px)}
     .chat-md-gallery.is-thumb img{max-width:160px;max-height:200px;aspect-ratio:auto;object-fit:contain}
```

验证：

```text
node tools/v3-thumb-test.mjs  9/9（单图不动、两个孤图都缩、网格不受影响、空值安全、接线检查）
node tools/v3-smoke.mjs       51/51
  注入 400x300 的 SVG：is-thumb 实测 160x120；普通 is-one 实测 320
```

已知取舍：孤图仍然是一张占一行（只是变小），不会横排；要横排得再做「纯图块合并」。
relay 侧规整空行 + 提示词约束仍是后续建议，这一版只做客户端兜底。

## 0.3.83 查看器按气泡收图

现象（2026-11-15 19:23 真机）：一条消息里两张图被空行拆成两个孤图，点开只能看一张，
左右滑不动（计数 1/1）。

原因：`openViewer()` 的收集范围是「点击那张图所在的 gallery」：

```js
const gallery = img.closest(".chat-md-gallery");
const all = gallery ? [...gallery.querySelectorAll("img")] : [img];
```

孤图各自是一个 gallery，所以永远只收到 1 张。

改法：范围扩到整条消息的气泡。

```js
const scope = img.closest(".v3bubble") || img.closest(".chat-md-gallery") || img;
const all = [...scope.querySelectorAll("img")].filter((x) => x && x.src);
```

范围仍是「同一条消息」，不跨消息；头像在 meta-row 外，不会被误收。

验证：

```text
node tools/v3-smoke.mjs  50/50
  两个独立 is-one 图：点第一张 -> 打开、2 页、计数 1/2
  滑到第二页 -> 2/2
  缩略图 160x120、普通 is-one 320 不变
```

## 0.3.84 查看器：图片舞台 + 右下角控制条 + 双指缩放

反馈：控件压在图片上，白图看不清计数；希望图片始终在按钮范围内，放大靠双指。

改法：

```text
结构：.v3viewer（纯黑、纵向 flex）
  .v3viewstage（flex:1、overflow:hidden、touch-action:pan-x）
    .v3viewer-track（横向 scroll-snap）
      .v3viewer-page > img（contain、overflow 裁剪）
  .v3viewer-bar（右下角一行）
    计数胶囊 1/N（<2 张时隐藏）、下载、关闭
```

- 图片永远在舞台内，舞台底部在控制条之上，控件不会压图
- 控制条深色胶囊/圆底，白图上也清楚
- 双指捏合缩放 1x..4x（clampViewerScale），缩放时单指平移并 clamp 在舞台内
- 缩放 >1 时禁用横向滑页（overflowX=hidden），缩回 1x 恢复切图
- 打开/关闭/切图时重置缩放

验证：

```text
node tools/v3-viewer-test.mjs  12/12（clamp 1..4、舞台结构、控制条顺序、缩放接线、重置）
node tools/v3-smoke.mjs        56/56
  控制条贴右下（barRight=0, closeRight=14）
  计数 -> 下载 -> 关闭 顺序
  舞台底边 <= 控制条顶边（不重叠）
  孤图之间仍可滑 1/2 -> 2/2
截图 docs/screenshots/v3-viewer.png
```

## 0.3.85 查看器计数居中 + 下载点图标也生效

反馈：

```text
1. 计数还是放中间
2. 点下载不太灵敏，像是只有图标背景能点
```

第 2 条是真 bug：点击处理判断的是 `s.target.id === "saveViewer"`，而保存按钮的内容是
`<svg><path>`——点在图标上时 target 是 path/svg，id 为空，匹配不到；只有点在按钮圆底
的空白处才命中。关闭按钮是文字 `×`，文字节点被命中时 target 仍是 button，所以一直正常。

修法：

```css
.v3viewer-bar{position:relative;justify-content:flex-end;gap:14px}   /* 按钮间距加大 */
.v3viewer-count{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)}
.v3viewer-save svg,.v3viewer-close svg{pointer-events:none}          /* 图标不再吃点击 */
```

```js
s.target.closest("#saveViewer")    // 点 svg/path 也命中
s.target.closest("#closeViewer")
```

验证：

```text
node tools/v3-viewer-test.mjs  16/16（含：计数居中、14px 间距、图标不吃点击、closest 命中）
node tools/v3-smoke.mjs        56/56
  计数中心偏移 0px
  下载/关闭间距 14px
  直接点 <svg> 也能触发保存（toast「已保存到 Downloads/Docklet」）
```

## 0.3.86 图片唤醒时机：切会话就抓，不再等发消息

现象（2026-11-15 20:10 真机）：点进会话，图片全是灰块，一直不出来；
随便发一条消息，图片立刻全部显示。

原因：`Ue()`（图片加载器）只在 `$()` 末尾执行一次，而 `Pe()` 的 `$()` 发生在
**新 client 创建之前**，首轮用的还是上一个会话的 client；失败只写 `alt`，不重试。
下次 `$()` 要等 chatSig 变化——也就是你发消息。

修法：

```text
Pe：e.client.start() 后立刻 Ue()（不等 SSE 连上，fetch 只要 base+token）
Ue：e.client 为空直接 return（冷启动不再抛）
    记录 loading / retries / failed，失败按 0.9s、1.8s 退避重试，3 次后定稿
    已有 src 的跳过，避免重复抓
回前台（visibilitychange）：retryImages() 清掉 failed 再补跑
加载更早一页后：Ue()
成功时清掉 retries；失败时 alt = 图片加载失败
```

验证：

```text
node tools/v3-image-wake-test.mjs  11/11（守卫、去重、退避、定稿、三处唤醒点）
node tools/v3-image-cache-test.mjs  全部通过（blob 回收未受影响）
node tools/v3-smoke.mjs            59/59
```

## 0.3.87 交付卡片持久化 + 清掉工具卡片死代码

两件事：

### 1. 交付卡片不再被新消息清空

原因：`Ct()` 在 `message.user` 时把 `deliverables` 清空（和 tools 一样），而且渲染
是底部一坨 `ye(deliverables)`，所以只显示最后一轮。

改法：

```text
mergeDeliverables(existing, files, seq)：累加、按 path:seq:index 去重、按 seq 排序、上限 20
message.user 不再清空 deliverables
每张卡片一个 .v3deliverable-slot[data-after-seq]
placeDeliverables(rows)：插到「seq 小于等于它」的最后一条消息之后，同一锚点保持顺序
点击仍走 presentedUrl，缩略图仍走 data-thumb-url
```

### 2. 工具卡片确认是「V3 要求彻底删除」

v1 有「本轮 N 个工具调用」，V3 明确不要；之前渲染删了，但 CSS（`.v3tools / .v3tool`）
和 `state.tools` 管线还在。这次清掉死 CSS，保留 state 管线（快照本来会带 tool 事件，
删了要动 Ct/_t，收益不大），并在文档里写明「工具卡片不展示，避免下次被当 bug 补回来」。

验证：

```text
node tools/v3-deliverable-test.mjs  15/15（合并/去重/排序/上限/默认值/源码接线/工具 CSS 已清）
node tools/v3-smoke.mjs             59/59
```

## 0.3.88 交付卡片定位修正 + 按回合聚合

反馈：交付卡片全堆到最后一条消息下面，越积越多，快把正文挤没了。

原因：0.3.87 用的是「最后一条 seq <= 卡片 seq 的消息」当锚点，但缓存消息/部分快照消息的
`data-seq` 是 0 或缺失，于是所有卡片都插到最后一条消息后面。

改法：

```text
Ct：deliverable.present 时记下当时最后一条消息 id（afterId），随卡片存进 v3 状态
渲染：每个卡片槽带 data-after-id + data-after-seq
placeDeliverables：优先 data-mid === afterId 插入；找不到再退回 seq；再找不到留在末尾
聚合：groupDeliverables 按 afterId 分组
  1 个文件 -> 直接一张卡片
  多个文件 -> <details> 折叠卡「本轮交付 N 个文件」，展开看列表
```

这样：
- 卡片出现在它那一轮的消息后面，不再堆到底部
- 一轮交付多个文件只占一张折叠卡，不再一排排铺满
- 上限仍是 20 个文件；缓存里的旧记录没有 afterId 时退回 seq

验证：

```text
node tools/v3-deliverable-test.mjs  23/23（afterId 保留、优先 afterId、seq 回退、分组、折叠卡）
node tools/v3-smoke.mjs             56/56
```

## 0.3.89 时间线重构：一次排好，不挪 DOM

背景：审批卡片会突然出现在最后一条消息下面（锚点消息不在当前窗口，`placeActionRecords` 就
append 到末尾）；交付、停止各有一套定位逻辑，规则不一致，属于“打补丁”。

这次按「打好基础」重做渲染：

```text
timelineRows()：
  1. 消息按顺序生成 row（含日期分隔）
  2. 注记（审批/问答卡、停止记录、交付卡片）各自带 afterId / seq
  3. 定位优先级：afterId 命中消息 -> 插它后面
                 否则 seq 落到最后一个 seq <= 它的消息后
                 都不命中 -> 不渲染（上下文已不在窗口，挂末尾只会误导）
  4. 按位置合并成一段 HTML，一次 render
xe() 只调用 timelineRows()，不再有 placeActionRecords/placeStopRecords/placeDeliverables
删掉三个旧的“画完再挪”函数
```

顺带修掉的坑：上一次替换误把传输层的 `xe()`（file URL 构造器）当成聊天渲染覆盖了，
这次把传输层恢复，新逻辑放回 mount 内（`messageRowHtml`/`timelineRows` 在 mount）。

验证：

```text
node tools/v3-deliverable-test.mjs  23/23
node tools/v3-stop-test.mjs         全部通过
node tools/v3-viewer-test.mjs       全部通过
node tools/v3-smoke.mjs             59/59（注入停止记录后仍能按 afterId 渲染）
```

包已归档：`dist/archive/`（0.2.0 - 0.3.89 共 99 个），`dist/dsh-console-beta-0.3.88.keep.pkg`
为重构前最后一版。

## 0.3.90 交付折叠阈值：3 个以内不平铺折叠

反馈（2026-11-15 21:09 真机）：交付已经能正确回到对话里，但 3 个也折叠了；
希望**超过 3 个才折叠**，比如 8 个先展示 3 个，其余展开看。

改法：

```text
deliverableGroupHtml：
  <= 3 个 -> .v3dgroup-list 直接平铺，没有 “本轮交付 N 个文件” 这行
  >  3 个 -> 先平铺前 3 个，下面一张 details「展开其余 N-3 个文件」
```

验证：

```text
node tools/v3-deliverable-test.mjs  26/26
  3 个平铺无 details
  8 个：8 张卡片 + details + 「展开其余 5 个文件」
node tools/v3-smoke.mjs             56/56
```

## 0.3.91 交付卡片锚到助手消息

反馈（2026-11-15 21:14 真机）：三张交付卡片出现在用户消息「返回3个交付」下面，
而正确的归属应该是助手这一轮。

原因：`deliverable.present` 发生在回合进行中，那时最后一条消息还是用户消息，
`afterId` 就记成了用户消息；卡片自然插在用户气泡下面。

改法：

```text
mergeDeliverables：新条目先标记 pending: true（不渲染）
turn.end / message.error 时：把 pending 交付的 afterId 改成当时的最后一条消息
（也就是本轮的助手消息），pending: false
timelineRows：跳过 pending 的交付，避免回合途中先出现在用户下面再跳走
```

快照重放时事件顺序不变（present 在 turn.end 之前），所以重开 App 后锚点也会算对。

验证：

```text
node tools/v3-deliverable-test.mjs  29/29（pending 标记、turn-end 锚定、pending 不渲染、折叠阈值）
node tools/v3-smoke.mjs             59/59
```

## 0.3.92 会话操作：分支 + 归档恢复

需求：会话操作页只有归档，补上分支；归档的会话要能恢复。

先看了 relay 源码（`@local/phone-relay/index.js`），确认接口已经存在：

```text
GET  /sessions            -> { current, sessions, archived }（archived 带 title/at/sessionId）
POST /session/archive     { id }
POST /session/unarchive   { id }
POST /fork                { id, atSeq? } -> { ok, sessionId }
```

所以分支**能实现**，而且 DSH 的 controller.fork 支持 atSeq（从某条事件处分叉），
这一版先整会话分叉，atSeq 留在接口里以后做“从这条消息分支”。

V3 改动：

```text
传输层：新增 forkSession(id, atSeq?)、unarchiveSession(id)
       sessions() 改为返回 { sessions, archived, current }（以前只回数组）
客户端：暴露 forkSession / unarchiveSession
会话操作页：加「分支会话」按钮 -> fork 成功后刷新列表并切到新会话
会话列表：底部「已归档 N」分组，每条一个「恢复」按钮 -> unarchive 后刷新
```

验证：

```text
node tools/v3-session-actions-test.mjs  11/11（接口接线、payload、分支后切会话、恢复）
node tools/v3-smoke.mjs                 59/59
当前 relay 实测：sessions 10 条，archived 7 条
```

## 0.3.93 归档改成搜索框左侧按钮，修正排版

反馈（2026-11-15 21:34 真机）：归档列表一直展开太长；把归档做成按钮放在搜索框左边，
点了才出现。另外「已归档 N」标题顶到左边背景框。

改法：

```text
搜索行改为 flex：左侧一个 🗂 按钮（aria-pressed 表示状态），右侧搜索框
点按钮 -> e.showArchived 取反 -> 只重绘 #sessionListBody
归档分区只在 showArchived 为真时渲染，默认隐藏
「已归档 N」标题 padding-left 10px，与卡片文字对齐；上间距 16px
```

顺带修掉一个重构遗留：`deliverableCard` 被重复定义了两份（后一份覆盖前一份），
删掉多余的一份。

验证：

```text
node tools/v3-session-actions-test.mjs  13/13（开关、默认隐藏、恢复接线、分支）
node tools/v3-smoke.mjs                 59/59
```

## 0.3.94 归档独立成页；新建挪到搜索行

反馈：归档按钮放在搜索框左边容易被当成搜索；新建在头部太挤；归档内联展开让会话页标题太多。

改法：

```text
会话页头部：只有返回 + 「会话」（去掉 ＋）
搜索行：[搜索…][＋][🗂]
  ＋ 新建会话（主色）
  🗂 进入「已归档」独立页面
归档页：route = "archived"
  头部 返回 + 「已归档」
  列表：标题 + 时间 + 恢复按钮；空态「没有已归档的会话」
  恢复 -> /session/unarchive -> 刷新
返回键：archived -> sessions -> chat（tt() 里补了 archived）
删掉 e.showArchived 和会话页内联归档区
```

验证：

```text
node tools/v3-session-actions-test.mjs  15/15（路由、返回链、搜索行按钮、恢复）
node tools/v3-smoke.mjs                 59/59
```

## 0.3.95 归档行补内边距（0.3.94 的收尾）

0.3.94 把归档独立成页后，归档行直接复用 `v3session-row`，标题贴着卡片左边缘。
这一版给归档行加 `v3archrow`（padding 11px 12px、gap 10px），标题/时间/恢复按钮
与卡片内容区对齐。

同轮截图归档：

```text
docs/screenshots/v3-sessions.png   会话页：搜索框 + ＋ + 🗂，头部只有返回和标题
docs/screenshots/v3-archived.png   已归档页：标题 + 时间 + 恢复
```

验证：`node tools/v3-smoke.mjs` 59/59。

## 0.3.96 接入壳的 backgroundMessages（v0.2）

壳已提供 `docklet.backgroundMessages`（前台服务、订阅持久化、开机恢复）。
V3 做 feature-detect，有就走新接口，没有继续用旧的 `background.start`。

### 接入方式

```text
index.html（唯一改动点，V3 不直接碰 window.docklet）：
  backgroundMessagesApi() 检测接口
  watch.start -> backgroundMessages.subscribe({
    endpoint: <base>/sessions?token=...&mode=message,
    transport: "sse",
    headers: { Authorization: "Bearer <token>" },
    eventTypes: ["turn.end"],
    foreground: "notify",
    sessionField: "session",
    dedupeBy: "eventId",
    persist: true,
  })
  watch.stop -> backgroundMessages.unsubscribe()
  setActiveSession(id|null) 暴露给 V3
旧壳没有接口时，退回原来的 background.start(url, title)
```

V3 侧：

```text
syncActiveSession()：route==chat 且页面可见时给会话 id，否则 null
调用点：Pe（切会话）、H（切路由）、visibilitychange
```

### 为什么先走 B（/sessions 轮询）而不是 A

relay 的 `/events?all=1` 已经存在，但实测事件体是 `{id,seq,type,at,payload}`，
**没有顶层 session**（session 在 payload.session.id 里），也没有 `eventId` 字段。
壳的 A 模式要求事件带 `session` 和 `eventId`，现在还不满足，所以先走 B；
等 relay 把 session/eventId 提到事件顶层，再切 A（只需改 endpoint 和参数）。

### 验证

```text
node tools/v3-background-test.mjs  15/15（feature-detect/参数/回退/activeSession/路由同步）
node tools/v3-smoke.mjs            62/62
  订阅端点 /sessions?mode=message
  Authorization: Bearer
  eventTypes ["turn.end"]
  foreground notify
  切通知模式后重新订阅为 mode=turn
```

### 说明

V3 打开时仍自己做 resnapshot；后台提醒只当“有新消息”的铃，不依赖通知正文。
通知正文/未读数/历史队列仍不在壳侧。

## 0.3.97 问答卡：选项换行 + 不回答

反馈（2026-11-15 23:01 真机）：问答卡选项文字被截断；另外想问必须回答吗，能不能不回答。

### 选项换行

原因：`.v3opt-label` 写死了 `white-space:nowrap`，两列布局下长选项直接溢出被裁。

```css
.v3opt-label{flex:1;min-width:0;white-space:normal;word-break:break-word;overflow-wrap:anywhere;line-height:1.45}
.v3card .v3opt{min-height:44px;padding:8px 12px}   /* 多行时上下有留白 */
```

### 不回答

协议里没有“拒绝回答”：input.request 必须给一个 response 才能继续。
所以“不回答”的语义做成**结束本轮**：

```text
问答卡底部：[不回答]（次要） [提交回答]（主要）
点不回答 -> client.interrupt()
          本地把该 prompt 标 resolved 收掉卡片
          toast「已结束本轮」
          服务端随后发 turn.end(aborted)，时间线出现停止记录
```

这样既不用被迫回答，也不会把半截回合挂在“等待操作”里。

验证：

```text
node tools/v3-question-test.mjs  7/7（换行 CSS、不回答按钮、interrupt、本地收起、失败提示）
node tools/v3-smoke.mjs          59/59
```

## 0.3.98 不回答保留「未回答」卡片

反馈（2026-11-15 23:06 真机）：0.3.97 点「不回答」后显示的是「已停止本轮」，
希望保留问答卡片，右上角标「未回答」。

改法：

```text
点「不回答」：
  e.skipStopNote = true（这一轮的 aborted 不生成停止记录）
  client.interrupt() 结束本轮（协议没有“拒绝回答”，只能结束）
  本地把 prompt 标 resolved，卡片立刻收起
  addAction({ kind:"question", decision:"unanswered",
              details: 每个问题 answer:"未回答" })
  -> 时间线渲染一张「需要回答」卡片，右上角「未回答」

turn.end / message.error：把仍 pending 的 prompt 全部标 resolved
  （aborted 回合不会发 input.result，否则重开会话卡片又冒出来）

noteStops：skipStopNote 为真时跳过本轮停止记录，并把该 turn 写进未回答卡片
  重开 App 快照重放时按 turn 匹配，仍然不再生成停止记录
```

样式：

```text
.v3done-tag.is-unanswered  浅琥珀底、深琥珀字
已回答仍是绿色；未回答用 × 图标
```

验证：

```text
node tools/v3-question-test.mjs  11/11（换行、未回答卡片、停止抑制、prompt 收口）
node tools/v3-smoke.mjs          62/62
```

修掉一个重构遗留：0.3.98 第一次构建时 `stopItemHtml` 被误删，页面启动报
`stopItemHtml is not defined` 并退到设置页；已恢复。

## 0.3.99 未回答标签改成「已拒绝」外观

反馈：问答卡右上角的「未回答」会被当成还能再点一次；「已拒绝」明确表示已经是结果。

只改右上角：

```text
标签文字：已拒绝
图标：复用 CARD_ICONS.x（没有复制第二份 SVG）
样式：复用 .v3done-tag.is-reject（浅玫瑰底、深红字）
删除 .v3done-tag.is-unanswered 这条 CSS
```

卡片其它部分不动：标题仍是「需要回答」，正文每道题答案仍是「未回答」。

验证：

```text
node tools/v3-question-test.mjs  12/12（reject 标签、正文未回答、换行、停止抑制）
node tools/v3-smoke.mjs          59/59
```

## 0.4.0 审批卡去掉 tool chip

反馈（2026-11-15 23:21 真机）：待审批卡片正文下面有个 `tool write` 的小胶囊，
点不了，v1 也没有，建议删掉。

改动：

```text
删除审批卡里的 ${o.tool ? `<div class="v3chips">...` : ""} 这一行
连带删掉 .v3chips / .v3chip / .v3chip .k / .v3chip .v 四条已无引用的 CSS
已处理的审批/问答卡不受影响：正文仍是 reason（没有 reason 时退回 tool）
```

验证：

```text
node tools/v3-question-test.mjs  12/12
node tools/v3-smoke.mjs          62/62
包内无 v3chip 残留
```

## 0.4.1 交付卡片过一会丢失

现象（2026-11-15 23:32 真机）：交付卡片刚出来时在，过一会再点进会话就没了。

原因：交付只存在 state 里，而 state 依赖快照事件重建：

```text
快照只带最近几轮事件
deliverable.present 很快滚出窗口
Ve(cache, snapshot) 合并时直接用了 snapshot 的 deliverables（空）
-> 之前拿到的卡片被覆盖掉
```

改法：

```text
新增 unionDeliverables(previous, incoming)
  按 id 合并、新字段覆盖旧字段、按 seq/index 排序、上限 20
Ve 的三条合并路径都带上 union 结果：
  无旧消息 / 无新消息 / 消息有交集
缓存里的旧卡片和快照里的新卡片取并集，不再被空快照覆盖
```

缓存本身（`Pt`）已经保存 deliverables，所以进程重启后也能先画出来再合并。

验证：

```text
node tools/v3-deliverable-union-test.mjs  8/8
  union 合并/覆盖/上限
  空 deliverables 快照保留旧卡片
  消息无交集时保留旧卡片
node tools/v3-smoke.mjs                  62/62
```

## 0.4.2 交付卡片要等下一次重绘才出现

现象（2026-11-15 23:35 真机）：电脑端已经交付完，手机正文出来了但卡片没有；
再发一条消息、或者返回再进来，卡片才出现。和之前图片不加载是同一类问题。

原因：`chatSig()` 里交付的签名只包含 `seq / index / name`：

```text
deliverable.present -> 条目 pending=true -> 签名变化 -> 重绘（卡片被过滤，不显示）
turn.end            -> 锚到助手消息、pending=false -> 签名不变
                    -> d() 只调 paintWorking()，不整页重绘
                    -> 卡片要等下一次整页渲染（发消息/切会话）才出来
```

改法：签名加上 pending 和 afterId：

```text
de += seq + ":" + index + ":" + name + ":" + (pending ? 1 : 0) + ":" + afterId
```

turn.end 让 pending/afterId 变化 -> chatSig 变化 -> 立即重绘 -> 卡片出现。

验证：

```text
node tools/v3-deliverable-union-test.mjs  9/9（新增：签名包含 pending/afterId）
node tools/v3-smoke.mjs                   59/59
```

## 0.4.3 加载气泡：思考 / 执行 / 输出

反馈（2026-11-16 01:52 真机）：有时已经在执行、停止按钮也正常，但没有思考气泡；
希望有气泡降低等待焦虑。

原因：`workingState()` 只在“最后一条消息是用户消息”时返回 thinking。工具回合里
助手已经输出过一条消息，`last.role === "assistant"`，气泡被这条限制挡掉；
而停止按钮只看 `turnActive || running`，两者条件不同源。

改法：

```text
workingState() 返回三种状态：
  waiting_input / error -> ""（有卡片/红卡，不再挂气泡）
  stream.text || reasoning -> "streaming"
  未激活 -> ""
  有工具 status=running -> "executing"
  否则 -> "thinking"
去掉“最后一条必须是用户消息”的限制
气泡文字：正在思考 / 正在执行 / 正在输出
executing 保持三点动画，隐藏空的正文区（不显示工具名）
```

这样工具回合中、助手中间消息之后都有气泡，turn.end 后自动消失；等待审批/回答时
不显示气泡，避免和卡片抢注意力。

验证：

```text
node tools/v3-working-state-test.mjs  10/10
  助手消息之后仍显示 thinking（回归）
  工具 running -> executing
  流式文本/思考 -> streaming
  waiting_input / error / 空闲 -> 不显示
  文案与 CSS 接线
node tools/v3-turn-active-test.mjs    8/8
node tools/v3-smoke.mjs               62/62
```

## 0.4.4 设置页显示实际使用的地址

反馈（2026-11-16 02:17 真机）：设置里「服务器 · 已连接 · 自动」看不出现在走的是家里
还是外部，希望像「如家里」这样标出来。

改法：

```text
currentBaseLabel()：
  base 与 lanBase 相同 -> 家里
  base 与 wanBase 相同 -> 外部
  其它（手填/自定义） -> 自定义
  比较前去掉尾部斜杠
设置页服务器行：
  已连接 · 自动 · 家里
```

验证：

```text
node tools/v3-base-label-test.mjs  6/6（LAN/WAN/自定义/尾斜杠/空/接线）
node tools/v3-smoke.mjs            59/59
```

## 0.4.5 对话页头也显示家里/外部

反馈（2026-11-16 02:20 真机）：设置页显示了「已连接 · 自动 · 家里」，
对话页头的「已连接」也希望跟一个「· 家里 / · 外部」。

改法：`connStateHtml()` 在已连接时带上 `currentBaseLabel()`：

```text
已连接 · 家里
已连接 · 外部
已连接 · 自定义
未连接仍然是「重连中」+ 琥珀点
```

设置页和页头共用同一个判断，不会出现两处不一致。

验证：

```text
node tools/v3-conn-state-test.mjs  4/4（家里/外部/无标签/未连接）
node tools/v3-smoke.mjs            59/59
```

## 0.4.6 模型胶囊 + 模型与推理面板

反馈（2026-11-16 02:28 真机）：模型想像 v1 一样显示，点一下能切换。

### 页头

```text
● 空闲  已连接 · 家里  [deepseek-flash · high]
```

模型从纯文本改成胶囊按钮 `#modelChip`，显示「模型 · 推理等级」；
`syncChatHeader()` 局部刷新时也保留这个按钮。

### 面板

点胶囊弹出底部面板（v1 同款结构）：

```text
模型与推理
  DeepSeek Account
  [DeepSeek-V4.1-Flash] [DeepSeek-V4-Pro]
  推理等级
  [Off] [Low] [High] [Max]
  权限
  [仅可查看] [工作区内修改] [完全权限]
  切换后从下一次操作开始生效；完全权限不再询问。
  [应用] [取消]
```

数据来自 `/options`：`models.groups[].models[].reasoning.efforts`、`permissions`。
选中项高亮，点「应用」：

```text
POST /model       { sessionId, provider, model, reasoningEffort }
POST /permission  { preset }（权限变了才发）
成功后关闭面板并 resnapshot("model")
```

### 验证

```text
node tools/v3-model-sheet-test.mjs   12/12（胶囊文案、传输、面板、接线）
node tools/v3-smoke.mjs             62/62
  点胶囊打开面板：标题、2 个模型、4 个推理等级、3 个权限
  取消后关闭
```

## 0.4.7 模型面板中文化 + 选项说明

反馈（2026-11-16 02:33 真机）：模型面板除模型名外都要中文，并且像新建会话那样
给选项加说明。

对齐新建会话的映射：

```text
提供方：DeepSeek Account -> DeepSeek 账号（其它 provider 原样）
模型名：保持英文（DeepSeek-V4.1-Flash / DeepSeek-V4-Pro）
推理等级：Off/Low/High/Max -> 关闭/低/高/最高
  说明：不需要推理的简单任务。/ 常规或对延迟敏感的任务。/
        大多数任务的默认平衡。/ 最难、最看重质量的任务。
权限：read-only/workspace-write/danger-full-access
  -> 只读 / 仅工作区 / 完全访问（与新建会话同一套文案）
  说明：只能看，不能改。/ 只能改工作区。/ 不再询问，直接执行。
```

面板结构：每块下面一行灰色说明，跟着选中项变化；模型切换时推理等级自动回到该模型
的默认值。页头胶囊里的推理等级也显示为中文（deepseek-flash · 高）。

验证：

```text
node tools/v3-model-sheet-test.mjs  15/15
node tools/v3-smoke.mjs             63/63
  面板文案：关闭 / 只读 / DeepSeek 账号
```

## 0.4.8 模型面板回退：只留「模型」和一条提示

反馈（2026-11-16 02:40 真机）：中文化映射不对，这里只是快捷入口，全部按原来的来；
顶部英文改成「模型」；除“保存后下次生效”外的提示都去掉。

改动：

```text
顶部 DeepSeek Account -> 模型
推理等级恢复 Off / Low / High / Max
权限恢复 仅可查看 / 工作区内修改 / 完全权限
模型名保持 DeepSeek-V4.1-Flash / DeepSeek-V4-Pro
删除模型描述、推理等级说明、权限说明
底部只留一句：保存后下次生效
页头胶囊恢复英文等级：deepseek-flash · high
```

验证：

```text
node tools/v3-model-sheet-test.mjs  15/15
node tools/v3-smoke.mjs             63/63
  面板实测：Off / 仅可查看 / 模型
```

## 0.4.9 页头：去掉状态、呼吸灯、网络快速切换

反馈（2026-11-16 02:43 真机）：左边顶上角的状态不要；网络连接前加呼吸灯（正常绿、
重连中已有颜色）；网络标签做成快速切换入口。

### 页头

```text
去掉：● 空闲 / 执行中
保留：🟢 已连接 · 家里   [deepseek-flash · high]
```

- 已连接：绿色 `.v3condot.ok`，`v3breath` 2.6s 呼吸
- 重连中：沿用原来的琥珀色 `v3pulse`
- 文本仍包在 `#connState` 里，网络切换测试和旧选择器不受影响

### 快速切换

点「已连接 · 家里」弹出小面板：

```text
网络
[自动] [家里] [外部]
当前：家里（自动）
[取消]
```

选一个立即生效：

```text
写 v3.mode / v3.base
pickSession() 选会话（失败也继续）
Ge() 重连
外部地址为空时禁用「外部」，防止切过去连不上
```

验证：

```text
node tools/v3-conn-switch-test.mjs  9/9
node tools/v3-conn-state-test.mjs   7/7
node tools/v3-smoke.mjs             68/68
  页头无状态点；绿色呼吸灯；面板 自动/家里/外部；取消关闭
```

## 0.5.0 网络面板：拉开间距、测速、保存生效

反馈（2026-11-16 02:49 真机）：三个按钮太近；右侧加「测速」；取消上面加「保存」，
保存后才生效。

改法：

```text
模式按钮：flex:1 等宽撑满，间距 8px
标题行右侧：测速（右上角对齐）
点测速：并发请求 家里/外部 的 /health，3 秒超时
  结果显示在「当前」下面：家里 12ms · 外部 超时
模式选择改成草稿：点了只高亮，不立即切换
[保存] 才写入 v3.mode / v3.base 并重连
[取消] 直接丢弃草稿
```

验证：

```text
node tools/v3-conn-switch-test.mjs  9/9
node tools/v3-smoke.mjs             70/70
  面板三项、保存/测速按钮、取消关闭
```

## 0.5.1 网络面板：测速与模式同排

反馈（2026-11-16 02:59 真机）：测速和另外三个按钮放在一排，三个按钮左对齐，
测速右对齐。

调整：

```text
去掉单独的「模式」标签行
一行：[自动] [家里] [外部] ............ 测速
三个模式按钮按内容宽度左对齐，间距 8px
测速 margin-left:auto 推到最右
下面是 当前 / 测速结果 / [保存] / [取消]
```

验证：

```text
node tools/v3-smoke.mjs  71/71
  新增：测速与模式按钮同一行，右侧留白 10-24px，模式左留白 10-24px
```

## 0.5.2 网络面板间距与样式、页头标签背景

反馈（2026-11-16 03:01 真机）：模式按钮间距再大一点；测速要相同的边框和文字颜色；
页头「已连接 · 家里」加背景，和模型标签一致。

调整：

```text
.v3conn-row gap 8px -> 12px
测速复用 .v3ms-chip（同边框、同文字色、同高度），只保留 margin-left:auto
.v3connchip 加浅灰底 #eef2f5 + 圆角，内边距 3px 9px，与 .v3modelchip 同款
```

验证：

```text
node tools/v3-smoke.mjs  71/71
  测速仍在同一行、右边留白 16px
```

## 0.5.3 锁定家里/外部：探测尊重模式

现象：快速切换里选了「外部」保存，过一会又自己回到家里。

原因：`pickBase()` 永远“局域网优先”，20 秒定时器和启动回扫也只认 LAN：

```text
connectMode 只影响初始地址，不约束 rediscover
家里可达 -> 外部被自动切回家 -> 看起来“切外部不对”
```

改法：

```text
pickBase()：
  mode=lan  -> 只探家里，探不到返回空（不回落外部）
  mode=wan  -> 只探外部，探不到返回空（不回落家里）
  mode=auto -> 原来的并行探、家里优先

20 秒回扫家里、启动后回扫，都只在 auto 模式执行
```

手动选外部后，只有改回自动/家里才会变；连接失败会保持当前地址重试。

验证：

```text
node tools/v3-connect-mode-test.mjs  10/10
node tools/v3-smoke.mjs              71/71
```

## 0.5.4 切会话不再闪「重连中」

反馈（2026-11-16 13:56 真机）：用外部网络已连上，切换对话时有小停顿，页头闪「重连中」。

原因：`Pe()` 切换会话时直接 `e.connected = !1` 并立刻重绘，然后才新建 SSE 连接。
WAN 下首帧要几百毫秒，这段时间页头只能显示重连中；LAN 太快所以不明显。

改法：

```text
Pe() 不再清掉 connected，改为记录 switchingUntil = now + 6s
onStatusUpdate 在切换窗口内忽略 attempt=0 的 connecting
  （不是掉线，只是新连接在握手的瞬态）
真正连上 -> 清掉 switchingUntil
真的失败（attempt 开始增长）-> 正常显示重连中
离线时（e.offline）不抑制
```

验证：

```text
node tools/v3-conn-switch-test.mjs  12/12
node tools/v3-smoke.mjs             74/74
```

## 0.5.5 断网检测加快

反馈（2026-11-16 13:56 真机）：外部网络下把关掉手机网络，页头仍显示「已连接」，
断开发现得太慢。

原因：浏览器看不到 TCP 被断开，SSE 会一直保持“打开”状态；而 relay 的心跳事件在
传输层就被丢掉了（`type === "heartbeat" → return`），页面没有保活信号可判断。

改法（不依赖心跳事件）：

```text
记录 e.lastBeatAt：收到任何事件、连上状态、探活成功都刷新
每 5 秒检查一次：
  navigator.onLine === false 且显示已连接 -> 立即置断开
  已连接且超过 15 秒没有任何事件 -> 探一次 /health（2.5s 超时）
    探通 -> 刷新 lastBeatAt（可能只是空闲）
    探不通 -> connected=false，停止客户端，800ms 后 rediscover
切会话的 6 秒窗口内跳过，避免误判
```

最坏发现时间从“永不”降到约 15–18 秒；关掉网络后最多一轮检查就会掉线。

验证：

```text
node tools/v3-freshness-test.mjs  10/10
node tools/v3-smoke.mjs           74/74
```

## 0.5.6 断网判断改成“和插件通不通”

讨论后确认：判“手机有没有网”不如判“和 relay 通不通”，两者会分叉：

```text
有网但插件不可达（Tailscale 掉、电脑睡、relay 停）
没网但插件可达（Wi-Fi 没外网，家里 relay 在局域网）
```

### 主信号：SSE 心跳

传输层原来把 `heartbeat` 直接丢掉，现在透传到 `onPing`：

```text
Me({ onPing }) -> Yt -> Ye -> Ge()/Pe() 的客户端
每次心跳刷新 e.lastBeatAt
```

### 判定

```text
20 秒没有任何信号（心跳 15 秒一次）-> 探当前 base 的 /health
  探通   -> 刷新 lastBeatAt，仍算连着（可能只是空闲）
  探不通 -> connected=false，停止 SSE，800ms 后 rediscover

navigator.onLine 不再直接判定：
  offline 事件 -> 立即探一次当前 base
  探通就保持连接（家里 LAN 场景）
```

### 文案

```text
没网且探测失败 -> 网络已断开，请检查网络
有网但探测失败 -> 连不到电脑，请检查 relay 或网络
重新连上 -> 清空原因
```

### 顺带修正

`Pe()` 原来 `if (e.offline) return`，LAN-only（无外网但 relay 可达）时切会话会不建新连接；
改成 `if (e.offline && !e.connected) return`。

验证：

```text
node tools/v3-freshness-test.mjs  11/11
node tools/v3-smoke.mjs           71/71
```

## 0.5.7 接入壳的 network 接口（两层判断）

壳已实现 `docklet.network.getState/watch`（模块规范 §8 网络状态），V3 接入：

```text
第一层：设备有没有网（壳原生，亚秒级）
  getState().online === false -> 立刻「网络已断开」，停 SSE
  online -> 进入第二层
第二层：插件通不通（V3 自己探 /health）
  通   -> 已连接
  不通 -> 「连不到电脑，请检查 relay 或网络」
```

细节：

```text
validated=false（连路由器没外网）仍然探插件，不直接判死
watch 回调：
  掉网 -> 立刻断开
  来网 -> 立刻探一次，不等 20 秒心跳
回到前台：refreshNetworkState()
旧壳没有 network 接口：保持心跳+/health 那套，功能不退化
```

验证：

```text
node tools/v3-network-layer-test.mjs  12/12
node tools/v3-smoke.mjs               74/74
```

## 0.5.8 修启动覆盖 lanBase（外部连接慢的根因）

现象：切外部后连接时间偏久，家里/外部标签也可能不对。

根因：`Re()`（启动/保存后连接）把当前 base 无条件写进 `e.lanBase`：

```text
启动时 e.base = 上次用的外部地址
Re() 里 e.lanBase = e.base -> 家里地址被改成了外部地址
后果：
  currentBaseLabel 永远显示“家里”
  pickBase 的“家里”其实在探外部地址
  auto 模式下回扫/切换判断全部失真
```

改法：

```text
只有用户真的在设置页输入了地址（#base 有值）才更新 lanBase
启动时保留 localStorage 里原来的 v3.lan
v3.base 仍按当前地址写入
```

顺带的提速：

```text
快速切换保存时，如果已有会话缓存，跳过 /sessions 这次 WAN 往返，直接 Ge()
```

验证：

```text
node tools/v3-network-layer-test.mjs  14/14
node tools/v3-smoke.mjs               74/74
```

## 0.5.9 未配置时返回打转 + 启动失败误踢配置页

现象（2026-11-16 14:50 真机）：打开模块落在设置页，提示未配置；重新填完才进来。
未配置前按返回没反应，只能回桌面重开模块。

### 返回打转的根因

`$()` 在“没有 client 且未配置”时会强制：

```text
e.route = "settings"; Te()
```

而返回键 `tt()` 对设置页执行 `H("chat")` → `$()` 又因为未配置跳回设置页，
形成死循环，看起来就是“返回没反应”。

修法：未配置时 `tt()` 直接返回 false，交给壳关闭模块（这是唯一能离开的路径）。

### 启动失败不再误踢配置页

`Re()` 原来任何异常都 `e.forceSetup = true`，一次网络错误/地址格式问题就会把用户
送到配置页。现在：

```text
令牌、会话、地址都在 -> 留在对话页显示错误
确实缺配置         -> 才进配置页
```

### 地址规范化

设置页保存和启动连接统一走 `normalizeBase()`：

```text
没写 scheme -> 补 http://
去掉尾部斜杠
空串 -> 空
```

避免 `new URL` 抛错导致连接失败/配置页误判。保存时如果已有会话缓存，也跳过
多余的 /sessions 往返。

验证：

```text
node tools/v3-boot-guard-test.mjs  9/9
node tools/v3-smoke.mjs            74/74
```

## 0.5.10 设置页手风琴默认关闭

反馈：设置里的折叠项默认展开太吵，默认都关掉。

改动：

```text
$() 里未配置时原来强制 e.openSection = "server"
改成 e.openSection = ""
设置页首次进入所有 section 收起，只显示标题 + 摘要
点击标题再展开
```

验证：

```text
node tools/v3-smoke.mjs  74/74
```

## 0.5.11 交付卡片对齐参考实现（Agents Anywhere）

参考仓库：anywhere-labs/Agents-Anywhere（Apache-2.0）。
它的做法：交付不是单独事件，而是从助手消息里的文件链接/行内代码提取，渲染成紧凑文件行：

```text
Android GeneratedFileCards.kt
  行高 padding 10dp/12dp，图标 36dp，间距 6dp
  文件名 14sp、单行（中间省略）
  副标题：扩展名大写 + 打开预览
  右侧 chevron，最大宽度 420dp，最多 4 个
```

V3 的数据源是 relay 的 deliverable.present，改的是呈现：

```text
卡片：padding 8/10，图标 32，圆角 12
文件名 13.5sp 单行省略
副标题「APK · 打开预览」代替原来的整段描述
完整描述放进 title（长按/悬停可见）
同一轮同名文件只留一张
每轮最多平铺 4 张，超出折叠「展开其余 N 个文件」
```

没有改数据来源：跨轮次同名文件仍是每轮一张（参考实现也是按消息展示）。

验证：

```text
node tools/v3-deliverable-test.mjs  11/11
node tools/v3-smoke.mjs             74/74
```

## 0.5.12 交付卡不再堆到对话末尾

现象（2026-11-16 22:06 真机）：8 张同名 APK 卡片连续堆在底部，中间没有消息。

根因（不是样式，是锚点）：

```text
交付卡片是「消息的附件」，定位靠 afterId
消息缓存只留最近 30 条，交付缓存留 20 条
旧交付的锚点消息被裁掉 -> afterId 找不到
timelineRows 的兜底：按 seq 落到“最后一条 <= 它的消息”
但消息对象没有 seq（都是 0）-> 所有失锚卡片都落到对话末尾
-> 一屏 8 张重复卡片
```

参考实现（Agents Anywhere）的思路：交付属于某条消息，锚点没了就不该出现在
时间线上；它按消息展示，同名文件在同一消息内去重。

改法：

```text
1. 交付组只在 afterId 命中已渲染消息时进入时间线
2. 锚点缺失或消息已滚出窗口 -> 收进一个「更早的交付 · N 个文件」折叠块
   同名文件跨轮次只留最新一次，最多 10 个
3. 不再用 seq 兜底把交付卡片塞到末尾
```

验证：

```text
node tools/v3-deliverable-test.mjs  13/13
node tools/v3-smoke.mjs             74/74
真实会话（session-0049…）用无头浏览器跑：失锚 8 条 -> 折叠块 1 个
```

## 0.6.0 交付模型重构：回合产物

参考：
- anywhere-labs/Agents-Anywhere：交付是消息的一部分（链接/代码路径），天然锚定
- ccch1mneyyy/dsh-Go：`TurnArtifacts { turn, endSeq, files }`，present 声明在回合结束读取

V3 旧模型的问题：

```text
present 用「最后一条消息」当锚点；消息缓存裁剪后失锚
seq 兜底时消息没有 seq，全部落到末尾 -> 一屏重复卡片
```

新模型：

```text
message 增加 seq = event.sequence
deliverable.present 记 turn，pending
turn.end 把该轮 pending 交付钉在 endSeq = turn.end 的 sequence
同一轮按 path 去重（保留最后一次）
渲染：插到最后一条 seq <= endSeq 的消息之后
窗口外：进「更早的交付」折叠块，按文件名去重
不再使用 afterId 猜锚点
```

验证：

```text
v3-deliverable-test.mjs        24/24
v3-smoke.mjs                   74/74
真实会话 session-0049…（无头浏览器）：
  交付卡位于对应助手消息之后，而不是堆在末尾
```

参考笔记：`docs/V3_REFERENCE_NOTES.md`

## 0.6.1 过程折叠（dsh-Go 的做法）

参考 dsh-Go：一轮里的工具调用收成「过程」摘要，主界面只留最终回复和产物，
点开才看细节。

实现：

```text
message.user：记 turnStartSeq
tool.start/update/end：沿用 r.tools
turn.end：生成 { turn, startSeq, endSeq, at, tools[] }，存进 processes（上限 40）
缓存：processes 随 state 落盘；快照合并 unionProcesses
渲染：<details>「过程 · N 步」插在该轮用户消息之后
  失败工具标红计数；工具行显示 name + detail + 状态
  起点消息滚出窗口时该过程不渲染（不堆到末尾）
```

真实会话验证（无头浏览器，session-0049…）：

```text
user(s28267) -> 过程 · 30 步 -> assistant(s28429) -> 交付卡
user(s28437) -> 过程 · 11 步 -> assistant(s28515)
user(s28522) -> 过程 · 24 步 -> assistant(s28637)
```

验证：

```text
node tools/v3-process-test.mjs     12/12
node tools/v3-deliverable-test.mjs 24/24
node tools/v3-smoke.mjs            74/74
```

## 0.6.2 草稿与会话绑定 + 空态快捷提示

```text
草稿：输入时 250ms 防抖写入 v3.draft.<session>
      切会话时恢复该会话草稿（不再清空）
      发送成功删除、发送失败写回
空态：开始对话时给 3 个快捷提示
      总结一下当前项目 / 列出最近的会话 / 帮我写一个小脚本
      点一下填入输入框并聚焦
```

验证：

```text
node tools/v3-composer-test.mjs  11/11
node tools/v3-smoke.mjs          74/74
```

## 0.6.3 会话列表重排 + 缓存配额兜底

会话列表：

```text
按日期分组：今天 / 昨天 / 具体日期
双行：标题（运行中带绿点）+ 最近一句摘要
右侧：时间 + 当前会话标记「当前」
空态：还没有会话 -> 提示点 ＋ 新建
搜索无结果单独提示
```

数据层：

```text
localStorage 写满时，消息按 30 → 20 → 12 → 6 逐级减少重试
交付、过程、提示始终保留，不再整份缓存被放弃
```

验证：

```text
node tools/v3-sessions-test.mjs  13/13
node tools/v3-cache-test.mjs     11/11
node tools/v3-smoke.mjs          74/74
node tools/v3-session-actions-test.mjs  通过
```

## 0.6.4 历史窗口 30 → 80 条

```text
缓存每会话消息 30 -> 80 条（SQLite 支撑的 v3_settings，不是 5MB web 配额）
快照合并窗口同步 80 条
配额兜底档位改为 80 / 40 / 20 / 10
交付、过程、提示仍始终保留
```

验证：

```text
node tools/v3-cache-test.mjs   11/11（触发回退到 20 条）
node tools/v3-smoke.mjs        74/74
```

## 0.6.5 分页状态随缓存保存

```text
缓存增加 sync：hasMore / oldestSeq
重进会话后「加载更早」仍然可用（之前恢复时被重置为 false/0）
cursor 仍重置为 seq:0，让下一次快照重新对齐
旧缓存（无 sync）安全降级为 no more
```

验证：

```text
node tools/v3-history-test.mjs  9/9
node tools/v3-cache-test.mjs    11/11
node tools/v3-smoke.mjs         74/74
```

## 0.6.6 回到会话时恢复滚动位置

```text
滚动时按会话记录 { top, nearBottom }
切换会话：离开时在底部 -> 停在底部
            停在历史 -> 回到原位置
渲染时先恢复位置，再处理锚点/底部逻辑
```

验证：

```text
node tools/v3-scroll-test.mjs  10/10
node tools/v3-smoke.mjs        74/74
```

## 0.6.7 长回复折叠

```text
助手回复超过 1200 字：气泡限制 340px，底部渐隐
下方「展开全文 / 收起」按钮
直接切 class，不重新渲染，滚动位置不跳
展开状态按消息 id 记住，切回来仍是展开
用户消息不折叠
```

验证：

```text
node tools/v3-clamp-test.mjs  9/9
node tools/v3-smoke.mjs       74/74
```

## 0.6.8 日期分隔、乱序消息、会话骨架屏

```text
日期分隔改为居中的胶囊（之前靠 align-self 没生效，实际靠左）
消息合并按 revision（event sequence）有序插入
  补历史/延迟事件乱序到达时不再排错
会话列表加载中显示 6 行骨架，不再是一行「加载中…」
```

验证：

```text
node tools/v3-message-order-test.mjs  8/8
node tools/v3-sessions-test.mjs       13/13
node tools/v3-smoke.mjs               74/74
```

## 0.6.9 视觉细节（截图复查）

用无头浏览器截了聊天/会话页复查，修了三处：

```text
1. 长回复右下角的「↑ 回到开头」按钮会压住一行字
   给带按钮的气泡预留 42px 底部内边距
2. 代码块语言标签统一大写（text -> TEXT）
3. 会话列表里摘要与标题相同时不再重复显示
```

验证：

```text
node tools/v3-sessions-test.mjs  14/14
node tools/v3-smoke.mjs          74/74
截图：docs/screenshots/v3-review-*.png
```

## 0.6.10 设置页/代码块截图复查

```text
代码块语言标签之前其实没有样式（text-transform 没生效）
  补 .v3bubble .chat-code-lang：大写、字距、11px 灰
恢复初始化：标题与摘要改危险色，和普通设置行区分
截图脚本修正：先截聊天/设置/模型/网络，最后再进会话列表
```

验证：

```text
node tools/v3-style-test.mjs  6/6
node tools/v3-smoke.mjs       74/74
截图：docs/screenshots/v3-review-{chat-mid,chat-bottom,settings,model,network,sessions}.png
```

## 0.6.11 会话列表状态简化

```text
去掉每行的「在线」：几乎所有行都成立，纯噪音
只保留「运行中」作为状态，配橙色圆点
没有摘要的行保留 16px 行高，列表节奏一致
smoke 的「离开设置页」断言从正文文本改为 DOM 检查
  （会话内容里本来就可能出现“恢复初始化”四个字）
```

截图：docs/screenshots/v3-review-sessions.png

验证：

```text
node tools/v3-sessions-test.mjs  14/14
node tools/v3-smoke.mjs          74/74
```

## 0.6.12 设置页展开态复查

```text
截了服务器 section 展开态：字段、令牌、保存按钮都正常
「网络测试」按钮原来是粉红底，像危险操作
  改为和次要按钮一致的浅灰蓝
```

截图：docs/screenshots/v3-review-settings-server.png

验证：

```text
node tools/v3-style-test.mjs  7/7
node tools/v3-smoke.mjs       74/74
```

## 0.6.13 通知 section：关闭时收起提醒方式

```text
提醒开关关闭时：只显示一行说明 + 发送测试提醒
  （之前仍显示「回合结束/每条消息」两个选项，像已经生效）
开启后才显示提醒方式和对应说明
```

截图：docs/screenshots/v3-review-settings-notify.png

验证：

```text
node tools/v3-style-test.mjs  8/8
node tools/v3-smoke.mjs       74/74
```

## 0.6.14 失败回合可重试

```text
「本轮失败」卡片增加「重试」
  取最后一条用户消息，填回输入框并发送
  没有用户消息 / 正在执行时不显示
```

验证：

```text
node tools/v3-retry-test.mjs  7/7
node tools/v3-smoke.mjs       74/74
```

## 0.6.15 发送即显示（乐观用户气泡）

```text
点发送后立刻插入一条本机用户气泡，不等 SSE 回显
真实的 message.user 到达时按「同文本」替换占位，不重复
发送失败：删掉占位并恢复草稿
15 秒没等到回显：删占位并 resnapshot 对齐
占位消息不进缓存（重开不会残留）
```

验证：

```text
node tools/v3-message-order-test.mjs  11/11
node tools/v3-cache-test.mjs          12/12
node tools/v3-smoke.mjs               74/74
```

## 0.6.16 头像 section 排版整理

```text
「恢复预设」原来浮在头像上方，像独立入口
改成头像行下方的一行：左侧提示「点头像可更换图片」，右侧「恢复预设」
再下面是取消/保存
```

截图：docs/screenshots/v3-review-settings-avatar.png

验证：

```text
node tools/v3-style-test.mjs  9/9
node tools/v3-smoke.mjs       74/74
```

## 0.6.17 回到底部按钮与键盘避让

```text
「↓ 回到底部」按钮随键盘抬升：bottom = 92px + var(--v3-ime)
未读数封顶显示 99+
```

验证：

```text
node tools/v3-style-test.mjs  11/11
node tools/v3-smoke.mjs       74/74
```

## 0.6.18 消息头（头像/时间）与键盘滚动

```text
消息 meta 头像 34px -> 24px，圆角 8，消息头更轻
打开键盘时不再无条件跳到底部：
  已在底部 -> 跟到最新
  正在看历史 -> 保持位置
rAF 回调显式包裹，避免时间戳被当成 force 参数
```

验证：

```text
node tools/v3-style-test.mjs  13/13
node tools/v3-smoke.mjs       74/74
```

## 0.6.19 修复重复用户气泡（真机截图定位）

通过 adb 抓真机截图发现：同一条用户消息出现两次。
relay 快照确认只有一条 message.user，所以是客户端问题。

根因：

```text
乐观气泡加在 mount 的 e.state 上
SSE 状态机（Yt）内部状态不知道这个占位
真实 message.user 到达时，两边合并 -> 占位和真消息同时存在
之前把「替换占位」写在 Ct（SSE 内部）里，永远不会命中
```

修法：

```text
在 mount 级合并 Ve 里做 reconcileOptimistic：
  真实用户消息文本（空白归一化）与占位相同 -> 丢弃占位
Ve 的四条返回路径都应用
发送成功后不再取消清理定时器，15 秒未回显仍会移除占位并 resnapshot
```

验证：

```text
node tools/v3-optimistic-test.mjs  8/8
node tools/v3-smoke.mjs            74/74
真机截图：.tmp/phone-now.png（adb screencap）
```

## 0.6.34 聊天增量渲染（第一步）

```text
聊天页拆出 #msgs，局部更新时不再重建整页
消息 HTML 按 id + 内容签名缓存，未变化的消息不重新解析 Markdown
流式只替换最后一条消息行，其余 DOM 不动
输入框/键盘不再随消息更新被销毁
整页重建只在切会话、浮层开关、连接状态变化等结构变化时发生
```

验证：

```text
tools/v3-fast-render-test.mjs
  强制刷新后 #text 节点不变、焦点保持、full 计数不增加
v3-smoke.mjs 71/71
```

## 0.6.35 输入框复位、首页 10 轮

```text
发送后 textarea 高度复位成一行（之前还留着多行高度）
发送失败时草稿写回输入框并重新计算高度
首次 snapshot 3 轮 -> 10 轮，和每页 10 轮一致
```

验证：

```text
v3-smoke.mjs 71/71
v3-fast-render-test.mjs PASS
v3-history/scroll/composer 测试通过
```

## 0.6.39 渲染窗口（最近 10 轮）

```text
进入会话：即使缓存里有 35 轮，也只渲染最近 10 轮
首次 snapshot 仍然只取 10 轮
往上翻到顶：从内存/缓存把更早的 10 轮补进来，不请求 relay
回到底部：裁剪回最近 10 轮
超出窗口的交付/过程/停止记录不再渲染，也不会堆到末尾
```

验证：

```text
v3-window-test.mjs
  进入渲染 20 行（10 轮）
  往上翻扩到 40 行（20 轮）
  回到底部裁回 20 行
v3-fast-render-test.mjs / v3-smoke.mjs 通过
```

## 0.7.0 壳风格主题（浅色）

```text
暖米色背景、近白卡片、陶土橙强调
聊天气泡、代码块、过程行、交付卡、输入区换色
会话列表、搜索、设置卡片同步换色
深色模式保持原样（主题只作用于 prefers-color-scheme: light）
布局未动：头部高度、消息顺序、渲染窗口都保持 0.6.40 的行为
0.6.40 保留在 dist/archive，可回滚
```

验证：

```text
v3-smoke.mjs 71/71
v3-style-test.mjs 全过
截图：docs/screenshots/v3-review-{chat-mid,settings,sessions}.png
```

## 打包格式坑（0.3.53 遇到）

PowerShell 5.1 的 `Compress-Archive` 会把条目名写成反斜杠
（`avatars\robot-purple.png`）。壳的 `ModuleManager.extractPackage` 对含
`\` 的条目直接抛 `UNSAFE_ENTRY`，安装失败且不会进模块列表；包体积也会随
条目数变化，容易让人以为是内容问题。

所以 V3 包固定用：

```text
powershell -ExecutionPolicy Bypass -File tools\pack-module.ps1 `
  -Source apps\dsh-console-beta -Out dist\dsh-console-beta-<版本>.pkg
```

它只写 `/` 分隔的条目、保留 `avatars/` 目录项，与 0.3.52 的包结构一致。
发布前解压回比一次（本版本 5/5 文件 SHA256 一致）。

## 测试

```text
1. 装壳 APK（已加 URL 安装），首页确认 BUILD 号
2. 添加模块 -> 链接 -> 填包地址
3. 打开 DSH 控制台 Beta
4. 填地址/令牌连接
5. 检查聊天、设置、会话
6. 退出再打开，看设置是否恢复
7. 设置 -> 恢复初始化，确认旧设置不会被 seed 回来
```

通过标准：

```text
能连接
能收消息
能发消息
设置重启不丢
恢复初始化真的清空
顶部没有壳工具栏
状态栏下无壳留白
键盘弹起输入框上移
任意会话通知可点进对应会话
```
