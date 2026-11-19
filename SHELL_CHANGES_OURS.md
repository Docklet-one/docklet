# 壳侧补充改动（给合并方读）

> 对应文件：`SHELL_CHANGES.md`  
> 当前工作区不是 git；不要直接覆盖 `shell/`。  
> 本文只记录最近这轮补充改动，合并时请和 `SHELL_CHANGES.md` 一起核对。

## 0. 当前 APK

```text
D:\docklet\dist\docklet-shell-0.1.0-debug.apk
859,595 B
SHA256: 5D40DC515AA93D03DAFC9C37C1BF9303777D68DD559D4ACBCBFB8726B6E436B8
```

已包含：

```text
SHELL_CHANGES.md 里的 chrome/insets/sessions/stop/预置
+
本次补充的 URL 安装、通用 download、sessionId/SSE、去重、停止修复
```

V3 包：`assets/dsh-console-beta-0.3.8.pkg` 已在 APK 内。

## 1. 添加模块：模块仓库 / 本地安装

涉及：

```text
shell/app/src/main/java/com/docklet/shell/ui/AddModuleScreen.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
shell/core/src/main/java/com/docklet/platform/PackageDownloader.java
```

行为：

```text
添加模块 -> 模块仓库 / 本地安装
模块仓库：内置示例进入仓库；单源阶段不显示源筛选
本地安装：URL + 本地 .pkg 合并在同一页
SHA-256 可选，两者共用
HTTP/HTTPS
来源确认弹窗
下载临时 .pkg -> manager.install
失败不动当前版本
包体上限 50 MB
```

关键实现：

- `PackageDownloader.downloadUrl(url, sha256, cacheDir)`
- 临时文件名必须是 `.pkg`；用 `.download` 会被 `ModuleManager` 判定为 PACKAGE_CORRUPT
- `MainActivity.confirmUrlInstall()` / `installFromUrl()`
- `AddModuleScreen.Listener.onUrl()`

注意：

- 模块仓库当前只列内置示例；远程默认源等发布时配置；
- 本地安装与远程 catalog 是两条链路；
- 不在 catalog 中创建记录；
- 没有新增模块包签名校验证书，只做包结构 + 可选 sha256。

## 2. 通用 download 能力（不是 V3 专用）

涉及：

```text
shell/core/src/main/java/com/docklet/platform/ModuleManifest.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBridge.java
shell/app/src/main/assets/docklet-sdk.js
docs/module-spec.md
```

新增能力白名单：

```text
download
```

SDK：

```js
await docklet.download(url, filename, { headers: {} });
// { started: true, name }
```

原生：

```text
DockletBridge.download.save
Android 10+
写入 MediaStore Downloads/Docklet
单文件 200 MB
异步线程，不阻塞 WebView
完成/失败发系统通知
```

保留原有：

```text
files.download(path)  模块本地文件导出
```

两者不要合并语义：

```text
files.download  = 本地模块文件 -> Downloads
download        = 远程 URL    -> Downloads
```

### 重要：V3 当前还不能直接用

V3 0.3.1 的 `module.json` 没有：

```json
"download"
```

所以它会从“接口不存在”变成：

```text
CAPABILITY_DENIED
```

V3 包侧如果要使用，需要加：

```json
"capabilities": ["network", "files", "download", ...]
```

不建议在壳里偷偷给 `network + files` 做兼容，否则能力模型会变模糊。

## 2b. 原生 `<input type=file>`

涉及：

```text
shell/app/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
```

新增 `WebChromeClient.onShowFileChooser`：

```text
标准 HTML <input type=file>
accept / multiple
系统文件选择器
取消返回 null
Activity 重建/销毁时取消回调
createIntent 失败时回退 ACTION_OPEN_DOCUMENT
```

保留 `files.pick`：

```text
onShowFileChooser = 标准浏览器文件输入，返回 content URI
files.pick        = 导入到模块 files/，返回 path
```

`onShowFileChooser` 不要求 `files` 能力。

## 2c. 首页顶部构建号

涉及：

```text
shell/app/build.gradle
shell/app/src/main/java/com/docklet/shell/ui/HomeScreen.java
```

`build.gradle` 每次构建生成：

```groovy
def buildStamp = new Date().format("yyyyMMdd-HHmmss")
buildConfigField "String", "BUILD_STAMP", "\"${buildStamp}\""
```

首页 Docklet 下方显示：

```text
BUILD 20261118-154029
```

用于区分手机上的包是不是最新构建。  
不是 applicationId，也不要恢复成 `LOCAL MODULE OS`。

## 2d. 首页模块索引异步化

涉及：

```text
shell/app/src/main/java/com/docklet/shell/runtime/ModuleInventory.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
```

问题：

```text
showHome() 直接 manager.listInstalled()
  -> 扫 files/modules
  -> 每个模块读 manifest
  -> FileOps.sizeOf(code/data)
```

这些都在主线程，模块多时会卡。

现在：

```text
ModuleInventory 缓存 snapshot
首次后台加载
showHome 先用缓存渲染
后台强制刷新
列表变化才重新渲染
```

`showAddScreen` 也改用 inventory snapshot，避免重复扫盘。

后续可把 snapshot 持久化为壳数据库 registry；当前先做内存缓存地基。

## 2e. 设置：运行内存

涉及：

```text
shell/app/src/main/java/com/docklet/shell/runtime/MemoryUsage.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
shell/app/src/main/java/com/docklet/shell/ui/HomeScreen.java
```

设置 -> 运行 -> 运行内存：

```text
PSS 拆分：
  壳进程
  模块槽位 :m1/:m2
  WebView 子进程（有才显示）
  Docklet 总计
  系统可用 / 总内存
```

读取在 `io` 线程完成，不阻塞设置页。

壳进程移除了 `WebStorage.getInstance().deleteOrigin()`：这个调用会把 Chromium 初始化进壳进程。模块卸载时不再从壳清理 WebView storage；模块关闭时 `ModuleRuntimeHost` 已经清理自己的 origin。

`MainActivity.launchModule()` 也改成：

```java
new Intent().setClassName(this, "com.docklet.shell.ModuleActivity")
```

避免使用 `ModuleActivity.class` 触发壳进程类加载。`moduleActivityOnTop()` 用类名字符串判断 m1/m2。

## 2f. 首页快速入口

涉及：

```text
shell/app/src/main/java/com/docklet/shell/ui/HomeScreen.java
shell/app/src/main/java/com/docklet/shell/runtime/RecentModules.java
```

首页结构：

```text
顶部：原深色 logo，搜索/加号同为浅色
大标题：你的模块，都在这里。
最近使用：最多 6 个，图标 + 下一行名称，透明无背景
已安装：保留长条列表
点击：打开模块
长按/右侧箭头：模块详情
```

模块图标背景统一改为 `UiKit.blend(accent, WHITE, 0.14f)`，避免深色底和同色图标叠在一起。

`RecentModules.record()` 在 `MainActivity.launchModule()` 记录最近模块；首页读取最近 6 个。

## 2g. 内置模块全屏 + 状态栏

涉及：

```text
apps/{calc,canvas,demo,focus,netlab,notes,quiz}/module.json
shell/core/src/main/java/com/docklet/platform/ModuleManifest.java
shell/core/src/main/java/com/docklet/platform/InstalledModule.java
shell/core/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
docs/module-spec.md
```

内置模块（除 DSH/V3）统一：

```json
"chrome": "none"
```

行为：

```text
无壳顶部工具栏
WebView 全屏铺满
系统状态栏保留
模块背景延伸到状态栏/导航栏后面
壳注入 safe-area CSS 变量和 docklet:insets 事件
```

内置示例版本同步提升（1.0.1，netlab 1.0.3），避免已安装旧 manifest 继续显示工具栏。

V3 同样 `chrome:none`，自己处理 `--v3-top` / `--v3-bottom` / `--v3-ime`。

## 2h. 首页快速入口 + 底部安装

涉及：

```text
shell/app/src/main/java/com/docklet/shell/ui/HomeScreen.java
shell/app/src/main/java/com/docklet/shell/ui/AddModuleScreen.java
shell/app/src/main/java/com/docklet/shell/runtime/FavoriteModules.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
```

首页：

```text
深色 logo + 大标题
最近使用：最多 6 个（透明图标 + 名称）
收藏：最多 6 个（长按图标切换）
```

底栏：

```text
模块 | 活动 | 安装 | 设置
```

- 「安装」是动作入口，不进 tabs 列表，没有选中态；
- 点击打开原添加页；
- 顶部不再有 `+`，搜索按钮打开完整模块库。

安装页（底部「安装」）：

```text
Tab：已装模块 / 模块仓库 / 本地安装
已装模块：每个模块独立白色卡片 + 间距
点击打开；右侧箭头详情
```

新增 `FavoriteModules`，prefs key `favorite_modules`，上限 24。

## 3. 通知 sessionId / SSE / 去重

涉及：

```text
shell/app/src/main/java/com/docklet/shell/ModuleRouterActivity.java
shell/app/src/main/java/com/docklet/shell/ModuleActivity.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
shell/app/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBridge.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBackgroundService.java
```

### 3.1 sessionId

```text
ModuleRouterActivity.EXTRA_SESSION_ID
MainActivity.EXTRA_OPEN_SESSION
ModuleActivity.EXTRA_SESSION_ID / EXTRA_NEXT_SESSION
```

模块通过：

```js
docklet.shellInfo().then(info => info.initial.sessionId)
```

拿到初始 session。

同一模块收到另一个 session：

```text
不关闭 WebView
-> docklet:session 事件
-> window.__dockletSessionId
```

不同模块：

```text
NEXT_MODULE + NEXT_SESSION
```

### 3.2 SSE

`DockletBackgroundService` 当前支持：

```text
event:
data:
忽略 id/retry
JSON envelope -> type/sessionId/id
```

只通知：

```text
message
message.assistant
turn
turn.end
message.completed
```

通知 tag：

```text
docklet-event-<module>-<session>-<eventId>
```

### 3.3 去重

```text
seen_events 持久化到 SharedPreferences
notificationId = hash(module|session|eventId)
服务重连/重启后同一 eventId 不再通知
```

前台 `notify.show` 传相同 `sessionId + eventId` 时，命中同一 tag/id，视觉上覆盖。

## 4. background.stop 修复

涉及：

```text
shell/app/src/main/java/com/docklet/shell/runtime/DockletBridge.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBackgroundService.java
```

`DockletBridge.backgroundStop()` 现在：

```text
clearState
startService(ACTION_STOP)
stopService
```

不再提前返回。

`ACTION_STOP` 到达服务时：

```text
active = false
disconnect
clearState
stopForeground
stopSelf
```

这修复了：

```text
通知开关已关但服务仍在
prefs 残留 module_id
UI 一直显示后台运行中
```

## 5. 合并时必须保留的细节

### 5.1 不要回退 `dispatch` 签名

当前是：

```java
dispatch(String data, String eventName)
```

`/sessions` 轮询调用：

```java
dispatch(payload, "message");
```

如果只保留旧的 `dispatch(String)`，会编译失败或丢失 SSE 过滤。

### 5.2 不要回退 `background.stop`

当前没有：

```java
if (running == null) return success(...);
```

删掉提前返回是有意的。

### 5.3 不要覆盖 URL 入口

`AddModuleScreen` 的 4 个 tab：

```text
示例 / 远程 / 文件 / 链接
```

`MainActivity` 需要保留：

```text
confirmUrlInstall
installFromUrl
```

### 5.4 不要覆盖 session 结果

`ModuleActivity.onModuleClosed()` 会带：

```text
EXTRA_NEXT_MODULE
EXTRA_NEXT_SESSION
```

`MainActivity.onActivityResult()` 需要读取并转给下一次 `launchModule`。

### 5.5 预置 V3 是 V3 专用逻辑

`MainActivity.preinstallBundledV3()` 会在首次启动自动安装 DSH Beta。

如果壳后续要面向通用应用，建议改为：

```text
不自动安装
或作为示例/可选安装
```

这条不是本次改动的阻塞项，但合并后值得决策。

## 6. 建议合并顺序

```text
1. 先确认 SHELL_CHANGES.md 的 chrome/insets/sessions/预置已在
2. 合 URL 安装：AddModuleScreen + MainActivity + PackageDownloader
3. 合 download 能力：ModuleManifest + DockletBridge + SDK + docs
4. 合 session/SSE：Router/Activity/MainActivity/Runtime/Bridge/BackgroundService
5. 最后确认 background.stop 没有提前返回
6. 重新构建，用 V3 Beta 验证：
   - 状态栏无壳留白
   - 键盘弹起
   - 通知带 session
   - 点通知进对应会话
   - 停止后台后 UI 变“未运行”
   - URL 安装可进确认页
```

## 7. 验证记录

```text
53 个 JVM 测试通过
lint 0 errors / 2 warnings
APK 859,595 B
SHA256 5D40DC515AA93D03DAFC9C37C1BF9303777D68DD559D4ACBCBFB8726B6E436B8

Background message v0.2:
- new DockletMessageService (single FGS, multi-module subscriptions)
- JS window.docklet.backgroundMessages
- SSE + headers/query token + eventTypes filtering
- turn.end status filtering (completed/empty only)
- /sessions fallback: lastAssistantSeq/lastSeq + running == false
- foreground + activeSession notification suppression
- persistence, START_STICKY, BOOT_COMPLETED
- fixed per-module "有新消息" notification
- snapshot before change: backups/pre-background-messages-20261115-211826

Display foundation:
- module.json optional display { mode, orientation, statusBarIcons, safeContent }
- safe / edge-to-edge / immersive
- portrait/landscape orientation lock
- immersive hides system bars with transient swipe reveal
- status bar / nav bar icon light/dark
- safeContent pads WebView inside cutout/system bars
- window.docklet.display.set / getState runtime API
- default: chrome standard -> safe, chrome none -> edge-to-edge
- exits restore shell window; no kiosk lock

Uninstall:
- removes module category assignment and category ordering
- reinstall starts in 未分类, not the previous category
- uninstall from Add keeps the Add page and current category
- uninstall from Home keeps the current Home tab
- category swipe uses one canonical railOrder + index; no customList re-derive or default fallback
- CategoryScrollView.dispatchTouchEvent sees gestures that start on a clickable module row
- gesture ownership is decided at ACTION_DOWN:
  scrollable list starts -> scroll only; no category switch even at edge
  starts at edge -> one category switch; screen-wide consumed guard
- on-device verified: first swipe scrolls to bottom, second swipe switches to 音乐
- category title is now inside the list ScrollView, so 未分类 and custom categories scroll identically
- long-press installed module menu has 收藏 / 已收藏
- favoriting stays on the Add page; Home favorites refresh after the overlay closes
- repository list rows open a full-screen 模块详情 page; install/update is explicit there
- closing repository detail restores the same Add/repository page
- detail pages use a pinned bottom action bar; 打开模块 / 安装模块 is always at the bottom
- repository detail for an installed module has 卸载; it reuses confirmUninstall
- install/uninstall update the repository detail in place; no jump to Home or repository
- installed detail switches to 未安装 after uninstall; install switches back to 已安装
- both repository and installed detail follow v1: horizontal icon+name, 3-column stats, 简介, 权限, capability tags, footer
- installed detail no longer leaves an empty gap where the old action row was
- Settings 状态 card has a 网络 row: subtitle Wi-Fi / 移动网络 / 以太网 / VPN / 未连接
- network row uses the same 30dp icon, 8.5sp subtitle and 8.5sp right value as other settings rows
- module data subtitle is only "N 个模块"; size moved to the right value
- notification permission subtitle is 全部关闭 / 全部打开 / 部分允许
- turning all notifications off cancels posted app notifications and stops DockletMessageService/DockletBackgroundService
- turning all notifications on restarts DockletMessageService immediately when subscriptions exist
- the foreground notification 7401 can finally disappear; cancelAll alone could not remove an ongoing service notification
- the 没有后台任务 info card no longer has a border
- Add page swipe now uses onInterceptTouchEvent + system touchSlop + axis lock
- intercepted swipes cancel the child touch, so they do not open the module; one switch per gesture
- modules opened from Add return to the same Add page; modules opened from repository/installed detail return to that detail
- module switch/chained launch keeps the original return target
- unclassified rail icon is the supplied signpost mark, red on a soft red tile
- unclassified title is red; hint explains auto-collection and long-press move
- copy: 底部安装→应用；已装模块→已装应用；模块仓库→在线应用；最近使用→最近
- repository install detail now follows the screenshot layout: author card, permission rows, checkmarked ability chips
- bottom action bar uses a dark 打开 button and a red trash action; author/contact/updatedAt/permissions/size are generated into demos.json
- capability chips use a FlowLayout with natural widths and automatic wrapping
- Settings status card no longer shows the network row; 预加载槽位→预载槽位
- default rail icon is the calendar-heart mark with 2.0 stroke
- EditText / Switch / Button / CheckBox / RadioButton / clickable TextView still do not start a page swipe
- the shell network watcher starts only while Settings is visible and stops on leave
- DemoApp carries permissions, size, updatedAt from catalog
- fixes second MOVE stream on the newly created list switching unclassified back to default
```

已实测：

```text
URL 链接安装入口 + 来源确认 UI
shell.info().initial.sessionId = sess-demo
SSE notification tag = docklet-event-netlab-sess-demo-m-3
background.stop -> {running:false}，服务与 prefs 清空
```

## 8. 边界

- 本文件只记录补充改动；`SHELL_CHANGES.md` 里已有改动仍以当前工作区为准。
- 没有 git diff，不要按旧版 APK 覆盖当前源码。
- V3 包侧（`apps/dsh-console-beta`、`dist/*.pkg`）不在这份壳改动范围内。
