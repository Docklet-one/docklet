# Docklet STATUS

> 更新时间：2026-11-15 02:40（Asia/Shanghai）  
> 本轮目标：按 `NEXT.md` 完成 P0/P1：CrashGuard、network、远程下载、BackgroundService、files/clipboard/openExternal  
> 结论：**P0/P1、UI v2 两阶段完成；V3 双槽运行基础（:m1 / :m2）与进程级预加载已落地。内容级预热、instrumentation、真机未做。**

## 1. 本轮新增

### P0-1 CrashGuard 安全模式

- 新增纯 JVM `CrashGuard`，状态持久化在 `files/crash_guard.json`。
- 启动前写 `launching=<id>`；模块 ready 后清除并重置失败计数。
- ready 超时、主文档加载错误、render gone、启动异常都记录为一次失败；单次运行只记一次。
- 壳进程在启动完成前死亡：下次启动检测到 `launching`，自动计一次失败。
- 连续 2 次失败进入安全模式：
  - 模块卡片显示“上次启动失败”或“安全模式”；
  - 点击卡片打开失败面板，显示失败次数、最近时间、原因；
  - 面板提供 **重试启动 / 清除模块数据 / 卸载模块**；
  - 安全模式下通知点击也不会自动打开该模块。
- 卸载或清数据会同步清理 CrashGuard 状态。

### P0-2 network

- 声明 `network` 的模块：
  - WebView mixed content 设为 `ALWAYS_ALLOW`；
  - CSP `connect-src` 放行 `http:` / `https:`；
  - App 允许 cleartext HTTP。
- 未声明 `network` 的模块保持 `MIXED_CONTENT_NEVER_ALLOW`，CSP 只允许 self。
- 新增 `netlab`（网络实验）示例：
  - GET 任意 HTTP 端点；
  - SSE（EventSource）；
  - 文件、剪贴板、外链能力按钮。
- 新增 `tools/network-test-server.mjs`：
  - `/health` JSON + CORS；
  - `/events` SSE + CORS；
  - `/catalog.json` / `/modules/*.pkg` 远程模块源；
  - 默认 8788，端口占用时可用 `PORT=8789`。
- 实测：
  - `http://10.0.2.2:8788/health`（现有 phone-relay）返回 200；
  - 8789 测试服务器 `/health` 返回 200；
  - 8789 `/events` 建立 SSE，连续收到 3 条事件；
  - 响应 `Access-Control-Allow-Origin` 正常。

### P0-3 远程 catalog / 下载

- 新增纯 JVM `RemoteCatalog` / `RemoteModule` / `PackageDownloader`。
- 远程 catalog 契约：
  - `spec: 1`；
  - 每个模块必须有 `id`、`name`、`version`、`url`、`sha256`；
  - `url` 支持相对路径，按 catalog URL 解析；
  - HTTPS catalog 不允许降级到 HTTP 包。
- 下载规则：
  - 下载到 `cache/remote-modules/`；
  - 边写边算 SHA-256，校验失败立即丢弃临时文件；
  - 校验通过后调用 `ModuleManager.install(pkg, expectedId, expectedVersion)`；
  - 包内 id/version 与 catalog 不一致直接拒绝；
  - 安装失败不修改 `current`。
- 壳 UI：
  - 首页右上角新增远程模块按钮；
  - 可编辑/保存 catalog URL；
  - 加载后显示模块列表及“安装 / 更新 / 已安装”状态；
  - Debug 默认填入 `http://10.0.2.2:8789/catalog.json`。
- 实测：
  - 从 catalog 安装 `calc 1.0.0` 成功；
  - `demo 1.0.0 → 1.0.1` 更新成功；
  - 错误 sha256 更新失败，提示 `REMOTE_HASH_MISMATCH`，`demo/current` 仍为 `1.0.0`。

### P1-2 BackgroundService 后台消息

- 新增 `DockletBackgroundService`：
  - `dataSync` 前台服务；
  - `startForeground` + 常驻通知；
  - 独立通知渠道「后台任务」「后台消息」。
- 最小 SSE 连接：
  - 每个 `data:` 行解析 JSON；
  - `title` + `text/body/message` 生成通知；
  - 非 JSON 原文也作为正文；
  - 断线 5 秒重连。
- 通知点击：
  - PendingIntent 携带模块 id；
  - 点击打开对应模块。
- 桥接口（2026-11-19 起；旧 `background.start/stop/status` 已删除）：
  - `backgroundMessages.subscribe({endpoint, headers, eventTypes, foreground, ...})`
  - `backgroundMessages.unsubscribe()`
  - `backgroundMessages.setActiveSession(sessionIdOrNull)`
  - `backgroundMessages.getState()`
  - 需要 `background` 能力；订阅走 HTTP 时另需 `network`。
- 约束：
  - 最多 8 个模块同时订阅，每个模块一条；
  - 需要通知权限，未授权返回 `PERMISSION_REQUESTED`；
  - 卸载模块会自动取消它的订阅；
  - 连接失败/断开时前台通知显示重连状态。
- netlab 1.0.1 增加「后台消息 / 停止后台」；1.0.2 增加文件选择/分享/下载按钮。
- 实测：
  - 启动后服务处于 `isForeground=true`；
  - `/events` 每 2 秒消息生成通知；
  - 通知栏出现多条「网络实验 · 后台 + hello from docklet-network-test」；
  - 点击通知打开 netlab；
  - 停止后台后 ServiceRecord 消失，按钮状态回到「未运行」。

### P1-1 files / clipboard / openExternal / 文件选择分享下载

- 新增纯 JVM `ModuleFiles`，根目录 `data/<id>/files/`。
- `files.write/read/list/delete/exists`：
  - UTF-8 / base64；
  - 相对路径，canonical 校验，拒绝 `..`、反斜杠、盘符；
  - 单文件 5 MB，模块总量 50 MB，目录最多 2000 项；
  - 非空目录删除需要 `recursive=true`。
- `clipboard.read/write`：
  - 主线程执行，2 秒超时；
  - 上限 200,000 字符。
- `openExternal`：
  - 只允许 `http` / `https` / `mailto` / `tel`；
  - 实测打开 `https://docklet.cn` 进入 Chrome。
- SDK 新增：
  - `docklet.files.*`
  - `docklet.clipboard.*`
  - `docklet.openExternal(url)`
- 文件选择/分享/下载：
  - `files.pick`：系统 `ACTION_OPEN_DOCUMENT`，复制到 `files/picked/`；
  - `files.share`：只读 `ModuleFileProvider` + `ACTION_SEND`；
  - `files.download`：API 29+ 写入 `MediaStore Downloads/Docklet`；
  - 单文件上限 5 MB，路径仍走 canonical 校验。
- 新增 `netlab` 示例用于现场验收这些能力。

### V3 双槽运行基础

- `ModuleActivity` → `:m1`，`ModuleActivity2` → `:m2`；
- `ModuleSlotManager` 轮流分配槽位，同一时间只允许一个前台模块；
- `MainActivity` 不再创建 WebView，只负责库、安装、CrashGuard 与调度；
- 模块进程本地初始化 `ModuleManager` / `StorageService` / `ModuleFiles` / `ModuleRuntimeHost`；
- 两个进程分别调用 `WebView.setDataDirectorySuffix()`，解决 Chromium 多进程数据目录冲突；
- 壳通过 `startActivityForResult` 接收 ready/cancelled/failure；
- ready 额外发同应用广播，壳在 Activity result 前清除 launching；
- `processDeathCheck` 只在模块 Activity 不在顶层时记失败；
- 后台服务状态写入 `SharedPreferences`，模块进程可读到壳进程的后台模块；
- ready 后写 `files/slot_session`，壳重启时用槽进程存活状态决定是否误计失败；
- 通知携带 `moduleId + sessionId`；`shell.info().initial.sessionId` 注入模块；
- BackgroundService 解析 V3 SSE `event:`/`data:` envelope，只通知 assistant/turn.end；通知 tag 含 session + eventId，seen events 持久化去重；
- 通知点击经 `ModuleRouterActivity` 路由，同模块新 session 走 `docklet:session` 事件，不重开 WebView；
- `background.stop` 会同时清 SharedPreferences、发 ACTION_STOP 并 stopService，避免服务已死但状态卡在“后台运行中”；
- `stopActiveModule(after)` 广播 STOP_REQUEST，等待 Activity result 后执行清除/卸载/安装更新；文件导入和示例安装都会先停槽；
- 设置页可开启预加载：只预热 `:m2` 进程和 WebView 数据目录，不创建第二 WebView；默认关闭，<256MB 或 `onTrimMemory` 时跳过/停止；
- 添加模块改为「模块仓库 / 本地安装」两 Tab：内置示例进模块仓库；本地安装合并 URL 与本地 .pkg，SHA-256 可选，安装前显示来源警告并二次确认；临时包必须保留 `.pkg` 扩展名，否则会误报 PACKAGE_CORRUPT；
- 首页改为快速入口：深色 logo + 大标题；最近使用最多 6 个、收藏最多 6 个，透明图标+名称；
- 顶部移除 `+`，底部导航改为「模块 / 活动 / 安装 / 设置」；「安装」打开添加页，不参与选中态；
- 长按首页图标收藏/取消收藏；
- 底部「安装」页三个 Tab：已装模块 / 模块仓库 / 本地安装；公共搜索在顶部；已装默认分类显示最近使用 + 未分类，默认可改名不可删，分类最多 10 个、名称最多 4 字，删除分类前提示应用回到未分类；
- 模块图标底色改为浅色混合，避免图标与同色深底叠在一起像“蒙了一层”；
- 内置示例除 DSH/V3 外全部 `chrome:none`：无壳工具栏，WebView 全屏铺满，保留系统状态栏；壳注入安全区变量，模块自行保留内容边距；V3 同模式；
- 内置示例版本提升（1.0.1，netlab 1.0.3），已安装旧版可在模块仓库里更新，否则旧包仍保留壳工具栏；
- 首页模块索引改为 `ModuleInventory` 后台缓存：首次加载后，文件系统扫描、manifest 读取和 data 大小统计不再阻塞 UI 线程；
- 设置页新增「运行内存」：PSS 拆分壳进程、模块槽位、WebView 子进程，并显示系统可用/总内存和当前 BUILD_STAMP；
- 壳进程移除 `WebStorage` 调用，避免卸载模块时把 Chromium 初始化进壳进程；模拟器壳 PSS 从约 86MB 降到约 39MB；
- `MainActivity` 不再用 `ModuleActivity.class` 作为 Intent target，改为类名字符串，避免壳进程提前加载模块 Activity；
- 模块 API 新增通用 `download.save`（SDK `docklet.download(url, filename)`），声明 `download` 能力后 URL 直接写入 Downloads/Docklet；
- WebView 新增原生 `onShowFileChooser`，标准 `<input type=file>` 可直接选文件。

实测：

```text
打开模块 -> :m1 进程
关闭后再开 -> :m2 进程，WebView 正常加载
kill -9 :m2 后壳回到首页，CrashGuard 记录一次失败
kill 壳进程（模块仍活）-> 重启壳，slot_session 清空且 CrashGuard 不增加
quiz 运行时点 netlab 通知 -> quiz 保存关闭，netlab 打开，CrashGuard 不增加
STOP_REQUEST -> 当前模块保存关闭，active_module 清空
预加载开启 -> :m2 进程出现；随后打开 m2 模块 WebView 正常
RUNNING_CRITICAL -> killBackgroundProcesses 回收 :m2，壳继续运行
槽进程仍存活但 Activity 被系统重建 -> 壳最多重试 3 次，不立即记失败
```

API 26–27 没有 `WebView.setDataDirectorySuffix`，槽位管理器会退回只使用 `:m1`。

详细交接见 [V3_HANDOFF.md](V3_HANDOFF.md)。

### UI v2 阶段一

设计稿：`designs/hybrid/index.html`（Warm Library），Java 已落地：

- 三 Tab 底栏：模块 / 活动 / 设置；
- 首页：品牌 + 大标题、最近使用、收藏；
- 活动页：后台任务、CrashGuard、通知权限；
- 设置页：catalog、通知、后台、存储、关于；
- 模块行点击打开，右侧箭头打开详情 Sheet；
- 危险操作仍留在详情 Sheet，避免首页误触。

### UI v2 阶段二

- 添加模块改为全屏页面：模块仓库 / 本地安装；
- 模块详情改为全屏页面：运行状态 / 存储 / 危险区；
- 首页保留在 overlay 下方，返回不重建、不丢 Tab；
- 模块仓库当前列内置示例；默认远程源等发布时配置。

```text
docs/screenshots/add-repo.png
docs/screenshots/add-local.png
docs/screenshots/ui-detail.png
```

## 2. 运行时验证（实际执行）

环境：Android 14 / API 34 x86_64 模拟器，1080×2400，`com.docklet.shell.debug`。

### CrashGuard

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 故意让 `crashy` 不调用 ready | 通过 | 10 秒后错误页，`crash_guard.json` count=1 |
| 再失败一次 | 通过 | count=2，卡片变“安全模式” |
| force-stop 后重启 | 通过 | 壳回到模块库，不自动进模块；安全模式状态仍在 |
| 点击失败模块 | 通过 | 面板显示失败次数/时间/原因 |
| 清除模块数据 | 通过 | `data/crashy` 删除，count 清空，卡片警告消失 |
| 卸载测试模块 | 通过 | `crashy` 从 modules 移除，CrashGuard 清空 |
| 正常模块 ready | 通过 | 现有 demo/netlab 正常启动 |

测试 fixture：`tools/fixtures/crashy/`，构建产物 `dist/fixtures/crashy-1.0.0.pkg`，不随 APK 内置。

### network / files / clipboard / openExternal

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 8788 `/health` GET | 通过 | netlab 日志 `200 OK`，JSON 响应 |
| 8789 `/health` GET | 通过 | `200 OK` |
| 8789 `/events` SSE | 通过 | `SSE connected`，连续 3 条事件 |
| 混合内容 https → http | 通过 | 声明 network 后 fetch 成功 |
| files.write/read/list | 通过 | `lab/last-log.txt` 1620 B；CDP 读取成功 |
| files.delete/exists | 通过 | 删除后 exists=false |
| clipboard.write/read | 通过 | 写入 `docklet-clip-test` 后读回一致 |
| openExternal | 通过 | Chrome 被拉起 |
| 未声明 network 的模块 | 未破坏 | 仍保持严格 mixed-content 策略 |
| 远程 catalog 加载 | 通过 | 7 个模块，状态正确 |
| 远程安装 calc | 通过 | 下载、sha256、安装均成功 |
| 远程更新 demo 1.0.0→1.0.1 | 通过 | `current=1.0.1`，保留 1.0.0 |
| 远程错误 sha256 | 通过 | 错误面板 `REMOTE_HASH_MISMATCH`，current 仍 1.0.0 |
| 后台服务启动 | 通过 | `isForeground=true`，常驻通知「Docklet 后台任务」 |
| 后台 SSE 消息 | 通过 | `/events` 每 2 秒生成通知 |
| 通知点击 | 通过 | 回到 netlab，页面显示「后台运行中」 |
| 后台停止 | 通过 | ServiceRecord 消失，状态「未运行」 |
| files.pick | 通过 | 选择 Downloads/pick-test.txt，复制为 `picked/…-pick-test.txt`，35 B |
| files.share | 通过 | 系统分享面板打开，预览可读取文件 |
| files.download | 通过 | `/sdcard/Download/Docklet/…-pick-test.txt`，35 B |

### 回归

- 6 个旧示例仍可安装、打开、保存/恢复；netlab 正常启动并完成能力验收。
- `calc` 仍不创建 `app.db`（`data/calc` 不存在）。
- 1.0.1 更新/回滚能力仍保留。
- 通知点击回模块仍正常。

## 3. 测试与检查

- **53 个 JVM 单元测试全部通过**（2026-11-14 22:15）。
  - 新增 `CrashGuardTest` 6 个；
  - 新增 `ModuleFilesTest` 7 个；
  - 新增 `RemoteCatalogTest` 5 个；
  - 新增 `PackageDownloaderTest` 4 个；
  - 新增 ModuleManager 远程 id/version 校验 1 个；
  - 原有测试 30 个。
- BackgroundService 通过 Android 14 runtime 验证；没有 JVM 单测（前台服务/NotificationManager 依赖 Android）。
- UI v2 阶段一通过模拟器人工走查；暂无 instrumentation/UI 测试。
- **Android lint：0 errors，2 warnings**：
  - `dataExtractionRules` 是 API 31 属性（minSdk 26，预期）；
  - `mipmap-anydpi-v26` 与 minSdk 26 重复（标准 adaptive icon 目录）。

报告：

```text
shell/core/build/reports/tests/test/index.html
shell/app/build/reports/lint-results-debug.html
```

## 4. 产物

```text
D:\docklet\dist\demo-1.0.0.pkg                  7,212 B
D:\docklet\dist\demo-1.0.1.pkg                  7,215 B
D:\docklet\dist\notes-1.0.0.pkg                 6,442 B
D:\docklet\dist\focus-1.0.0.pkg                 6,307 B
D:\docklet\dist\canvas-1.0.0.pkg                6,032 B
D:\docklet\dist\calc-1.0.0.pkg                  4,734 B
D:\docklet\dist\quiz-1.0.0.pkg                  5,706 B
D:\docklet\dist\netlab-1.0.2.pkg                7,174 B
D:\docklet\dist\demos.json                      3,264 B
D:\docklet\dist\docklet-shell-0.1.0-debug.apk   859,595 B
D:\docklet\dist\fixtures\crashy-1.0.0.pkg       2,005 B
```

SHA256：

```text
E0C664E49E8EE940A09FFA559BA6BDDFD04A40682806398E5B5AF639E4805B03  demo-1.0.0.pkg
E8956AF234912475D687078D119CE865A48641674925C0A272D8B2F9B9DAE363  demo-1.0.1.pkg
EC0905CC755A075D97346AB8E07CB843739BCBF2BEB9D2EA3AABB383F9BBFA87  notes-1.0.0.pkg
FA3B202D18B7F689FBD66AAFC085290390E92E11C1B95536952EC83E6CC3F78F  focus-1.0.0.pkg
540FD723C8827541BDB4D4E4607010F4610CB59688325A254CF0EC4AD35D709B  canvas-1.0.0.pkg
214129584B22CFF625491A66E19D45DB16EDEEBAF41EFE189F5146C1F0BC61AD  calc-1.0.0.pkg
863F6D15C3A25FCF810E45D020F0E8F422367761E65B595716EB6D979EC60CE3  quiz-1.0.0.pkg
36CE4F6796FB41843CB2A1095D5FAA5899DC21DEFD8F2749B5A19BA9C84451E6  netlab-1.0.2.pkg
DE0CC59C83A5140362F75411D1D639E00E3EEF5E0DD29AED6333A2176DDD8108  demos.json
5D40DC515AA93D03DAFC9C37C1BF9303777D68DD559D4ACBCBFB8726B6E436B8  docklet-shell-0.1.0-debug.apk
```

## 5. 如何运行 / 验证

### 构建 + 测试 + lint

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\build.ps1 -Lint
```

### 安装

```powershell
D:\docklet\.tools\sdk\platform-tools\adb.exe install -r D:\docklet\dist\docklet-shell-0.1.0-debug.apk
```

### 网络验收

```powershell
# 如果 8788 已被其他本地服务占用，用 8789：
$env:PORT=8789
node D:\docklet\tools\network-test-server.mjs
```

netlab 打开后：

```text
emulator: http://10.0.2.2:8789/health
SSE:      自动把 /health 换成 /events
真机:     http://<电脑局域网 IP>:8789/health
```

已用现有 8788 phone-relay 和 8789 测试服务器分别验证过 GET；8789 的 SSE 连续事件已验证。

### 远程模块验收

先启动测试服务器：

```powershell
$env:PORT=8789
node D:\docklet\tools\network-test-server.mjs
```

首页右上角下载按钮 → 加载 `http://10.0.2.2:8789/catalog.json`。测试服务器把 demo 标成 1.0.1 用于更新；`catalog-badsha.json` 或 `TAMPER=1` 用于错误 sha 验收。

### 后台消息验收

```powershell
adb shell pm grant com.docklet.shell.debug android.permission.POST_NOTIFICATIONS
```

打开 netlab，把 URL 设为 `http://10.0.2.2:8789/health`，点「后台消息」，再按 Home。通知栏应持续出现「网络实验 · 后台」；点击通知回到 netlab。点「停止后台」后：

```powershell
adb shell dumpsys activity services com.docklet.shell.debug
```

不再出现 `DockletBackgroundService`。

### CrashGuard 验收

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\build-crash-fixture.ps1
adb push D:\docklet\dist\fixtures\crashy-1.0.0.pkg /sdcard/Download/
```

在壳里“从文件安装”该包，打开两次（每次等 10 秒 ready 超时），即可看到安全模式。测试完可在失败面板清除数据/卸载。

## 6. 未完成 / 下一步

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 远程 Tab 完全内联 | 部分 | 目前全屏添加页的远程 Tab 仍打开独立远程面板 |
| 发布者签名 | 未做 | 远程包当前依赖 catalog + sha256 + HTTPS，无签名校验 |
| 后台消息持久化 | 未做 | 连接断开只重试，不做消息去重/离线队列；进程被杀不自动恢复 |
| 多后台连接 | 未做 | 同一时间只允许一个模块持有后台连接 |
| V3 预加载 | 部分 | 已做进程级预热（默认关闭，不创建 WebView）；内容级后台 WebView 预热未做 |
| V3 通知切换 | 已做 | ModuleRouterActivity + SWITCH_TO + NEXT_MODULE |
| V3 停槽等待 | 已做 | stopActiveModule(after)；清除/卸载已接入 |
| V3 壳死亡 UI 恢复 | 部分 | ready marker 已避免误计失败；壳重启后恢复槽 Activity 的 UI 路由未做 |
| 真机验证 | 未做 | 仅 Android 14 x86_64 模拟器；OEM WebView 未覆盖 |
| 完整 ApiBridge | 部分 | files/clipboard/openExternal 已有；background/network 代理未做 |

## 7. 关键决策

- CrashGuard 用 JSON 文件 + 纯 JVM 实现，方便故障注入和单测。
- 网络采用方案 A：声明 `network` 后允许 mixed content + cleartext；不经壳代理，目标服务负责 CORS。
- files 使用相对路径 + canonical 校验 + 配额；模块文件与 SQLite 共用 `data/<id>/`，但文件在 `files/` 子目录。
- 远程模块使用 catalog + sha256 + 原子安装；包内 id/version 必须匹配 catalog，HTTPS 不允许降级 HTTP；暂不做发布者签名。
- `calc` 不声明 storage，壳不创建数据库；`netlab` 声明 network/files/clipboard/openExternal/background，用于能力验收（含系统文件选择/分享/下载）。
- 设计细节见 `docs/decisions.md`。

## 8. 截图

```text
docs/screenshots/apps-overview.png      7 个示例总览
docs/screenshots/multi-catalog.png      7 个示例目录
docs/screenshots/multi-library.png      模块库
docs/screenshots/home-nav.png           新首页（最近 + 收藏 + 底栏安装）

docs/screenshots/demo-full.png          内置模块全屏 + 状态栏
docs/screenshots/ui-activity.png        UI v2 活动页
docs/screenshots/ui-settings.png        UI v2 设置页
docs/screenshots/add-repo.png           模块仓库
docs/screenshots/add-local.png          本地安装
docs/screenshots/inventory.png          后台模块索引
docs/screenshots/memory.png             运行内存
docs/screenshots/ui-detail.png          全屏详情
docs/screenshots/slot-module.png        :m1 模块进程
docs/screenshots/slot-m2.png            :m2 模块进程
docs/screenshots/ui-switch.png          通知切换后的 netlab
docs/screenshots/settings-preload.png  预加载设置
docs/screenshots/preload-m2.png         预加载后 m2 模块
docs/screenshots/netlab-get.png         network GET 200
docs/screenshots/netlab-sse.png         后台消息 + SSE
docs/screenshots/netlab-files.png       系统文件选择结果
docs/screenshots/files-share.png        系统分享面板
docs/screenshots/bg-notifications.png   后台消息通知
docs/screenshots/bg-clicked.png         通知点击回模块
docs/screenshots/remote-catalog.png     远程 catalog / 更新状态
docs/screenshots/remote-hash-error.png  sha256 失败，旧版本保留
docs/screenshots/crash-home2.png        安全模式卡片
docs/screenshots/crash-sheet.png        重试/清数据/卸载面板
docs/screenshots/crash-cleared.png      清数据后
docs/screenshots/notes-final.png
docs/screenshots/focus-running.png
docs/screenshots/canvas-drawn.png
docs/screenshots/calc-key.png
docs/screenshots/quiz-answer.png
docs/screenshots/shell-module-saved.png
docs/screenshots/shell-home.png
docs/screenshots/demo-home.png
```

## 9. 验收点对照

| # | PROJECT.md 验收点 | 当前 |
| --- | --- | --- |
| 1 | demo 按需安装、启动 | **通过**（7 个示例目录） |
| 2 | 重启壳后模块仍在 | **通过** |
| 3 | 清 WebView 缓存后 SQLite 还在 | **通过** |
| 4 | 模块崩溃后壳回列表 | 部分：错误页 + 安全模式；未自动回列表 |
| 5 | 连续崩溃不自动打开 | **通过**：连续 2 次进安全模式，重启不自动进模块 |
| 6 | 更新失败能回滚 | 部分：更新+回滚通过；失败注入仍靠单测 |
| 7 | 卸载只删自己的模块和数据 | **通过** |
| 8 | 跳转前当前模块保存 | **通过** |
| 9 | 返回后恢复状态 | **通过** |
| 10 | 通知能打开目标模块 | **通过** |
| 11 | 远程 catalog 下载/更新 | **通过**（sha256、失败保留旧版） |
| 12 | 后台消息 / 通知点击 | **通过**（SSE、前台服务、PendingIntent） |
| 13 | 文件选择 / 分享 / 下载 | **通过**（OPEN_DOCUMENT / ACTION_SEND / MediaStore） |
