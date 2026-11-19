# Docklet

Docklet 是一个封闭的 HTML 应用平台：小坞负责安装、运行、存储与恢复，应用只提供自己的界面和业务。

当前仓库只做**壳**，不内置业务应用。配套的 7 个 demo 应用只用于验收平台契约。

![Docklet 示例应用总览](docs/screenshots/apps-overview.png)

## 示例应用

| 应用 | 类型 | 演示点 |
| --- | --- | --- |
| 示例应用 | 专注 / 便签 | 计数、输入、SQLite 保存恢复、通知、更新回滚 |
| 清单 | 效率 | 增删改查、筛选、完成状态持久化 |
| 专注时钟 | 专注 | 25/5 计时、进度环、离开暂停、状态恢复 |
| 画板 | 创作 | Canvas 指针绘制、撤销/清空、笔画 JSON 持久化 |
| 小算盘 | 工具 | 纯计算、无 SQLite、`capabilities: []` |
| 一日一问 | 学习 | 知识卡、答案揭示、学习进度持久化 |
| 网络实验 | 网络 / 工具 | fetch、SSE、后台消息、files、clipboard、openExternal、选择/分享/下载 |

![示例应用目录](docs/screenshots/multi-catalog.png)

### CrashGuard

连续 2 次启动失败后，应用卡片会标记安全模式；点击后可以重试、清除应用数据或卸载。

![CrashGuard 安全模式](docs/screenshots/crash-sheet.png)

### 能力实验

网络实验应用同时演示 GET、SSE、后台消息、应用文件、系统文件选择/分享/下载、剪贴板和系统外链。

![网络实验](docs/screenshots/netlab-sse.png)

### 远程应用

首页右上角的下载按钮打开远程 catalog：填写 URL、加载、安装或更新。每个包先校验 sha256，再原子安装；失败不会替换当前版本。

![远程应用](docs/screenshots/remote-catalog.png)

### 后台消息

应用声明 `background` 与 `network` 后，可以启动一个前台服务保持 SSE 连接；每条消息变成系统通知，点击回到应用。

![后台消息通知](docs/screenshots/bg-notifications.png)

## 现在有什么

```text
apps/
  demo/                 专注便签
  notes/                清单
  focus/                专注时钟
  canvas/               画板
  calc/                 小算盘（无存储）
  quiz/                 一日一问
  netlab/               网络实验（network/files/clipboard/openExternal/background + pick/share/download）
shell/                  Android 应用小坞（Java，无第三方 UI 依赖）
  core/                 纯 JVM 应用管理核心，可单测
  app/                  Android UI、WebView RuntimeHost、SQLite StorageService
docs/                   应用规范、构建说明、决策记录
dist/                   构建产物（7 个 .pkg、catalog、APK、测试 fixture）
STATUS.md               已完成 / 未完成 / 如何验证
```

## 快速构建

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\build.ps1
```

带 lint：

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\build.ps1 -Lint
```

构建任务会扫描 `apps/*/module.json`，自动生成：

```text
dist/demo-1.0.0.pkg
dist/notes-1.0.0.pkg
dist/focus-1.0.0.pkg
dist/canvas-1.0.0.pkg
dist/calc-1.0.0.pkg
dist/quiz-1.0.0.pkg
dist/netlab-1.0.2.pkg
dist/demos.json
dist/docklet-shell-0.1.0-debug.apk
```

安装：

```powershell
adb install -r D:\docklet\dist\docklet-shell-0.1.0-debug.apk
```

已在 Android 14（API 34）x86_64 模拟器上验证安装、启动、SQLite 保存/恢复、force-stop 恢复、更新、回滚、卸载、通知、通知点击，以及：

- CrashGuard 连续失败安全模式、重试/清数据/卸载
- network GET + SSE（mixed content / CORS）
- files 读写列表删除、clipboard 读写、openExternal
- 远程 catalog 下载、sha256 校验、更新；校验失败旧版本保留
- 后台 SSE 消息、前台服务、通知点击回应用
- 系统文件选择、ACTION_SEND 分享、MediaStore 下载到 Downloads

真机/OEM WebView 尚未覆盖。

更完整的说明见 [docs/build.md](docs/build.md)，当前进度与验证结果见 [STATUS.md](STATUS.md)。

## 使用

1. 打开 Docklet，首页为空时点“浏览示例应用”。
2. 在“添加应用”里选择任意示例应用；每个应用独立安装、独立 origin、独立数据。
3. 打开应用，修改状态后退出；Docklet 会先触发 save。
4. 再进入应用，状态从各自的 `data/<id>/app.db` 恢复。
5. 详情面板可回滚上一版（安装第二版后）或卸载；卸载时可选择保留数据。
6. “示例应用”的“模拟一条消息”会申请通知权限并发送通知；点击通知回到 demo。
7. “网络实验”可测 GET/SSE、后台消息、应用文件、系统选择/分享/下载、剪贴板和系统外链；测试服务器见 [docs/build.md](docs/build.md)。

想验证更新/回滚，可把 `dist/demo-1.0.1.pkg` 通过“从文件安装”装进去，再在详情里回滚到 1.0.0；数据会保留。

想验证远程下载，先运行：

```powershell
$env:PORT=8789
node D:\docklet\tools\network-test-server.mjs
```

然后点首页右上角的下载按钮，加载 `http://10.0.2.2:8789/catalog.json`。

后台消息验收：打开「网络实验」，把地址改成 `http://10.0.2.2:8789/health`，点「后台消息」，再按 Home 离开；通知栏会持续收到消息，点击可回到应用。

想验证 CrashGuard，可用 `tools/build-crash-fixture.ps1` 生成故意不 ready 的测试包，安装并打开两次。

## 界面设计

方向：D 的列表 + A 的暖色材质 + 一个运行中 Spotlight。设计稿：

- [Warm Library 交互稿](designs/hybrid/index.html)
- [五屏总览](designs/hybrid/shots/overview.png)

已落地的第一阶段：

- 底部“应用 / 活动 / 设置”三 Tab
- 首页最近使用 + 收藏；安装页包含已装应用、应用仓库、本地安装
- 活动页（后台任务 / CrashGuard / 通知权限）
- 设置页（catalog / 通知 / 后台 / 存储 / 关于）

![新首页](docs/screenshots/home-nav.png)

![新活动页](docs/screenshots/ui-activity.png)

已落地的第二阶段：

- 全屏添加应用页：示例 / 远程 / 从文件
- 全屏应用详情页：状态 / 存储 / 危险区
- 远程 Tab 仍打开独立远程面板，后续可完全并入

![应用仓库](docs/screenshots/add-repo.png)

![本地安装](docs/screenshots/add-local.png)

![全屏详情](docs/screenshots/ui-detail.png)

### V3 双槽基础

- `ModuleActivity` 运行在 `:m1`，`ModuleActivity2` 运行在 `:m2`
- 同一时间只允许一个前台槽，`ModuleSlotManager` 轮流分配
- 两个进程使用独立 `WebView` 数据目录后缀
- 壳 `MainActivity` 不再创建 WebView
- 应用本地持有 StorageService / ModuleFiles
- 已用 `kill -9 :m2` 验证壳恢复与 CrashGuard 计数
- ready marker 避免壳重启时把仍存活的槽误计为失败
- 通知点击经 `ModuleRouterActivity` 保存并切换应用
- `stopActiveModule(after)` 提供停槽后清除/卸载/安装更新入口
- 设置页可选进程级预加载：默认关闭，只预热 `:m2` 进程，不创建第二个 WebView
- 低内存 `RUNNING_CRITICAL` 时回收后台槽进程
- 通知携带 `sessionId`，应用通过 `shell.info().initial.sessionId` 接收
- 后台 SSE 支持 V3 envelope，按 `sessionId + eventId` 持久化去重
- `backgroundMessages.*` 取代旧 `background.start/stop/status`；订阅、去重、连接状态都落盘
- 添加应用支持「链接」直接下载 .pkg；SHA-256 可选，安装前显示来源警告
- 新增通用下载能力：`docklet.download(url, filename)` → Downloads/Docklet
- 标准 `<input type=file>` 走原生 `onShowFileChooser`，不再依赖 files.pick
- 首页应用索引由 `ModuleInventory` 后台缓存，扫描/大小统计不再阻塞 UI
- 首页改为快速入口：最近使用 6 + 收藏 6；安装入口在底部导航
- 内置应用除 V3 外全屏运行，保留系统状态栏，小坞负责安全区 padding
- 设置里可查看壳进程、应用槽位、WebView 子进程的 PSS 内存
- 壳进程不初始化 WebView/WebStorage，卸载应用不会把 Chromium 拉进壳

V3 迁移交接见 [V3_HANDOFF.md](V3_HANDOFF.md)。

## 平台契约

- 应用包：`.pkg`（zip），根目录必须有 `module.json` 和合法 `entry`。
- 每个应用：独立 origin `https://<id>.module.local`、独立 WebView、独立 SQLite。
- 版本单调递增；更新保留上一版；启动失败可回滚。
- 生命周期：`ready` / `pause` / `resume` / `save`；保存超时 500ms。
- 能力白名单：`storage`、`files`、`clipboard`、`openExternal`、`notify`、`background`、`network`。
- `calc` 不声明 `storage`，因此壳不会为它创建数据库；其余示例各自使用独立的 `data/<id>/app.db`。
- `network` 应用允许 HTTPS origin 直接访问 HTTP 端点；目标服务必须返回 CORS。应用文件限制在 `data/<id>/files/` 内。
- 远程 catalog 的每个包必须带 sha256；HTTPS catalog 不允许下载 HTTP 包。当前不做发布者签名。
- 后台消息同一时间只允许一个应用连接；需要通知权限，前台服务类型为 `dataSync`，停服务/卸载应用会释放连接。

详细规范见 [docs/module-spec.md](docs/module-spec.md)。

## 边界

这是封闭平台，只安装自研应用。没有第三方市场、微信级 DSL、双线程渲染、原生组件或每应用一个 APK。
