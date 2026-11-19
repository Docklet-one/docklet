# Docklet 交接文档

> 生成时间：2026-11-18 17:40 左右  
> 目标：供下一个对话直接接续开发，不依赖本次对话上下文。

## 1. 当前状态

项目目录：

```text
D:\docklet
```

当前最新 APK：

```text
D:\docklet\dist\docklet-shell-0.1.0-debug.apk
859,692 B
SHA256: 1A548A97E3E956CB1DA300410785C7E0502AEA4DCBECF0F4BE745417826E2C09
BUILD 20261118-174013
lint 0 errors, 2 warnings
```

本地下载服务：

```text
http://192.168.2.6:8791/docklet-shell-0.1.0-debug.apk
```

当前已安装设备：

```text
USB: 10AF5L1H1K0019J
机型: V2463A
```

无线 ADB 之前用过的地址是 `192.168.2.3:33917`，当前以 USB 设备为准。

## 2. 重要：仓库与发布

### 源码与发布工作区

```text
D:\docklet              壳 + 模块源码。不是 Git 仓库，不要当 Git 项目操作
D:\docklet\deploy      发布工作区，走 Git 推送（唯一通道）
```

发布工作区按目录映射站点：

```text
deploy/                      项目发布目录
├─ apps/         →  apps.docklet.cn
├─ website/      →  docklet.cn
├─ docklet/      →  壳源码（独立仓库 Docklet-one/docklet）
├─ dsh-console/  →  V3 源码（独立仓库 Docklet-one/dsh-console）
└─ tools/          工具包
```

推送目标：

```text
仓库   https://github.com/Docklet-one/docklet-deploy.git
本地   D:\docklet\deploy（仓库根，已 git init）

deploy/apps/     →  仓库 apps/        （wrangler.apps.toml → apps.docklet.cn）
deploy/website/  →  仓库 website/     （wrangler.toml    → docklet.cn）
```

嵌套的两个独立仓库（外层 .gitignore 忽略，不随 deploy 提交）：

```text
deploy/docklet/       →  Docklet-one/docklet（分支 shell）    壳源码
deploy/dsh-console/   →  Docklet-one/dsh-console              V3 源码（app/ + plugin/）
```

`backups/` 被 `.gitignore` 忽略，不会推送。

### 三个对话、三个区域（互不越界）

壳、模块、发布**分三个对话处理**，各自只改自己的目录、只推自己的仓库：

| 对话 | 可以改 | 可以推 |
|---|---|---|
| 壳 | `D:\docklet\shell`、`tools`、根目录文档 | `deploy/docklet` → `Docklet-one/docklet` |
| 模块（V3） | `D:\docklet\src\v3`、`src\plugin`、`apps\dsh-console` | `deploy/dsh-console` → `Docklet-one/dsh-console` |
| 发布 | `deploy/apps`、`deploy/website` | `deploy/` → `Docklet-one/docklet-deploy` |

**壳仓库排除整个 `apps/` 目录**：`dsh-console`、`game2048`、`racer` 都是独立模块，
不属于壳的源码仓库。壳构建时本地仍需要 `apps/`，但它不进 `docklet` 仓库。

**壳仓库只收最纯粹的源码**，规则写在 `deploy/docklet/.gitignore`：

```text
收      shell/ 的源码与构建配置（gradle 文件、gradlew、wrapper）
        tools/ 里的 .ps1 / .mjs / .py 脚本
        根目录的 *.md 文档

不收    apps/（独立模块）
        shell/app/build、shell/.gradle、dist、*.apk、*.pkg
        shell/local.properties（本机 SDK 路径）
        .tools/、.gradle-home/、.android/（工具链与缓存）
        *.exe / *.dll / *.so（adb、sqlite3 这类可执行文件）
        日志、设计稿、备份
```

### 推送口令

```text
「推壳」    → 同步 shell / tools / 文档 → deploy/docklet → commit + push
「推模块」  → 同步 src/v3、src/plugin、apps/dsh-console → deploy/dsh-console → commit + push
「推发布」  → deploy/apps、website → deploy/（docklet-deploy）→ commit + push
```

每次只动对应的那一个仓库，其它仓库不碰。

### 快照与同步（重要）

`D:\docklet` 是**真正的工作区**，`deploy/docklet`、`deploy/dsh-console` 是**快照**：

```text
改代码   →  D:\docklet 下改
推送前   →  跑 tools/sync-repos.ps1 把源码同步进快照目录
推送     →  只推有变化的仓库
```

**忘记同步的后果**：远端仓库停留在旧版本，而且没有任何提示。
所以每次说“推壳 / 推模块”时，先同步再提交，这一步不能省。


**发布只有一条路：Git 推送。** 没有 wrangler 直传、没有 FTP、没有手动上传。

### 模块目录站（apps/）

```text
apps/
├─ catalog.json     壳的在线目录（CatalogSource.CATALOG_URL）
├─ _headers         CF Pages 响应头
├─ downloads/       当前版本分发文件，固定文件名
│  ├─ docklet.apk   壳
│  ├─ dsh-console.pkg
│  ├─ game2048.pkg
│  └─ racer.pkg
├─ backups/         本地历史备份（.gitignore 忽略，不推送）
├─ 404.html / robots.txt
└─ README.md        发布流程与回滚
```

规则：

```text
1. 文件名固定，不带版本号；每版直接覆盖同名文件
2. 版本身份 = catalog.json 的 version + sha256（不是文件名）
3. 壳不预置任何模块：没有远程目录就是空列表
4. _headers 里 .pkg 必须 no-cache（固定名不能用 immutable，否则永远拿到旧包）
5. 改包必须同步改 catalog 的 version / sha256 / size，否则客户端按旧 sha 校验失败
6. 不要手工改 .pkg，重新打包
```

### 官网（website/）

```text
website/           只有网页，不放任何二进制
下载按钮           → https://apps.docklet.cn/downloads/docklet.apk
index.html 的 SHA256 行   ← 必须与该 APK 的实际哈希一致
```

- 壳包只有一份（`apps/downloads/docklet.apk`），官网不再存副本
- 命名：坞内单位一律叫「模块」，品牌保留「应用小坞」，系统设置里的「未知来源应用」不改
- 官网与壳是两套源码，壳的改动不会自动同步到官网，文案要手动改

### 发布检查清单

模块新版本：

```text
1. 备份   旧 downloads/<id>.pkg → backups/<id>-<旧版本>.pkg
2. 覆盖   新包 → downloads/<id>.pkg（文件名不变）
3. 改目录 catalog.json：该条目的 version / sha256 / size
4. 推送   git commit + push
```

壳新版本：

```text
1. 构建   build.ps1 → dist/docklet-shell-0.1.0-debug.apk
2. 备份   旧 apps/downloads/docklet.apk → apps/backups/docklet-shell-<日期>.apk
3. 覆盖   新 APK → apps/downloads/docklet.apk（文件名不变）
4. 改数字 website/index.html 的 SHA256 行
5. 推送   git commit + push
```

回滚：从对应 `backups/` 取回旧包覆盖回去；模块还要把 `catalog.json` 的
`version` / `sha256` / `size` 改回旧值。

### 生效时间

```text
网页         CF 部署完成后立即生效
壳 APK       apps/downloads/docklet.apk，no-cache，下载即最新
模块目录      客户端默认缓存 6 小时；点在线页右上角 ⟳ 立即刷新
模块包        固定名 + no-cache，刷新目录后下一次安装就是新版
```

### 提交约定

提交说明从简：推送时不需要逐条列出改了哪里。如果有特殊要求
（commit message 格式、拆分提交、指定分支等），会另行告知。

## 3. 命名约定

已经确定并应继续使用：

```text
产品名：Docklet 应用小坞
名称：小坞（品牌），Docklet（宿主）
动作：放进坞内
位置：坞内
内容：模块
```

对外不要再用：

```text
壳          → 用 Docklet / 小坞
应用（指坞内单位时）→ 用 模块
```

说明：「应用」在系统层面指手机上的 App，坞内单位若也叫「应用」会混淆
（「无法启动应用」到底是 Docklet 起不来还是某个模块起不来），因此坞内单位统一叫 **模块**。

内部代码可以继续叫 `module`、`ModuleManager`、`module.json`，不要为了文案重命名协议或 Java 类。
内外一致：坞内单位 = 模块 = module。

开发者文档里写：`Docklet 宿主 / 模块`。

页面名（底部导航与页面标题）仍是：首页 / 活动 / 应用 / 设置，
其中「应用」页里列出的单位叫「模块」。

## 4. 壳当前页面状态

### 首页

当前标题栏：

```text
Docklet
—应用小坞
```

当前首屏：

```text
● 已安装 · 15 个应用
你的应用，都在这里。
```

已删除：

- 主标题下面的说明小字
- 底部「独立 ORIGIN · 数据隔离 · 本地优先」

底部导航：

```text
首页 / 活动 / 应用 / 设置
```

### 活动页

- 「最近发生」下面的提示行已删除
- 当前仍有一些旧的“模块”文案，例如：
  - 「模块离开后不会继续运行…」
  - 「模块消息」
  
  这些是待统一项。

### 设置页

- 状态卡片中的「网络」行已删除
- 「预加载槽位」已改为「预载槽位」
- 当前状态卡顺序：
  ```text
  模块数据
  仓库线路
  预载槽位
  运行内存
  ```

### 应用页

顶部 Tab：

```text
已装应用 / 在线应用 / 本地安装
```

左侧分类栏：

- 「首页」和「未分类」属于内置分组
- 两者与下面自定义分类之间有分割线
- 未分类图标不再显示气泡或数字
- 未分类图标仍是红色路牌
- 未分类提示当前为：
  ```text
  新安装和未分类的应用都会显示在这里
  ```

已安装应用列表：

- 默认「最近 / 收藏」使用图标 + 名称网格
- 未分类和自定义分类现在也统一使用图标 + 名称网格
- 长按卡片打开应用菜单
- 卡片增加了触摸保护：滑动超过系统 `touchSlop` 后不会触发打开

### 应用详情

#### `DemoDetailScreen`

在线应用/仓库安装详情，当前为目标布局：

```text
应用详情
图标 + 应用名
当前版本 / 文件大小 / 更新时间
简介
作者（名称 + 联系方式）
权限
能力（✓ 绿色标签，FlowLayout 自动换行）
底部：深色「打开」 + 红色垃圾桶
```

`FlowLayout.java` 用于能力标签自然宽度换行。

#### `ModuleDetailScreen`

已安装应用详情，仍是旧结构，包含：

```text
运行状态
存储
危险区
清除数据 / 卸载
```

如果后续要求“已安装应用详情也改成截图布局”，需要单独改这个类。

## 5. 官网

源目录：

```text
D:\docklet\deploy\website\site
```

页面：

```text
index.html
apps.html
dev.html
404.html
dev.md
README.md
assets/
downloads/
```

特点：

- 顶部导航：首页 / 应用 / 文档
- 顶部下载按钮已移除
- Hero 使用 CSS 骨架手机，不再使用真机截图
- Hero 下方有仓库地址、QQ 群、邮箱
- 首页有「装应用，有三种方式」：
  - 在线应用
  - 本地安装
  - 从链接安装
- 应用页标题为「在线应用」
- 应用详情为弹层，包含能力、信息、权限、安装步骤、同类应用
- 第三方开发文档为 `dev.html`，Markdown 版为 `dev.md`
- 官网 APK 目前是旧版 `859,595 B`，需要同步

本地预览：

```powershell
cd D:\docklet\deploy\website\site
npx serve .
```

## 6. 构建与安装

环境：

```text
JAVA_HOME=D:\docklet\.tools\jdk17
GRADLE_USER_HOME=D:\docklet\.gradle-home
Gradle=D:\docklet\.tools\gradle-8.2.1\bin\gradle.bat
ADB=D:\docklet\.tools\sdk\platform-tools\adb.exe
```

构建：

```powershell
powershell -ExecutionPolicy Bypass -File D:\docklet\tools\build.ps1 -Lint
```

安装：

```powershell
adb -s 10AF5L1H1K0019J install --no-streaming -r D:\docklet\dist\docklet-shell-0.1.0-debug.apk
```

## 7. 关键源码

```text
shell/app/src/main/java/com/docklet/shell/ui/HomeScreen.java
shell/app/src/main/java/com/docklet/shell/ui/AddModuleScreen.java
shell/app/src/main/java/com/docklet/shell/ui/DemoDetailScreen.java
shell/app/src/main/java/com/docklet/shell/ui/ModuleDetailScreen.java
shell/app/src/main/java/com/docklet/shell/ui/FlowLayout.java
shell/app/build.gradle
apps/*/module.json
```

`shell/app/build.gradle` 会生成 `demos.json`，目前已经包含：

```text
tagline
author
authorContact
minShell
entry
permissions
updatedAt
size
```

`DemoCatalog` / `DemoApp` 已支持读取这些字段。

## 8. 待办 / 风险

1. 发壳版本时按这个顺序（细节见 §2 的发布检查清单）：
   ```text
   1. build.ps1 构建
   2. 旧 APK → deploy/apps/backups/docklet-shell-<日期>.apk
   3. 新 APK → deploy/apps/downloads/docklet.apk
   4. 改 deploy/website/index.html 的 SHA256 行
   5. 在 D:\docklet\deploy 执行 git add -A && commit && push
   ```

2. 网站版本号与壳版本号不一致（网站侧仍是 1.0.3，壳是 0.1.0 debug）。
   发正式版前先决定版本策略，不要直接把 version.json 降级到 0.1.0。

3. `apps/` 与 `website/` **属于同一个仓库**（docklet-deploy），一起提交推送；
   不要再“只 stage website”。`apps/backups/` 已被忽略，不会误提交。

4. 活动页、模块详情页的部分空状态仍有旧文案，未做全量命名清理。

5. `D:\docklet` 是工作区，不是 Git 仓库；三个发布仓库分别是：
   ```text
   deploy/docklet       →  Docklet-one/docklet（分支 shell）
   deploy/dsh-console   →  Docklet-one/dsh-console
   deploy/             →  Docklet-one/docklet-deploy
   ```
   不要把 `D:\docklet` 的改动直接当成可推送提交，先跑同步脚本。

6. `deploy/docklet`、`deploy/dsh-console` 是快照，源码在 `D:\docklet` 下改；
   忘记同步会导致远端停在旧版本（见 §2「快照与同步」）。

## 9. 最近验证过

- 最新壳 APK 已安装到 `V2463A`
- 首页标题、活动页提示删除、设置网络行删除、预载槽位文案已在真机确认
- 未分类无气泡、分类页图标+名称网格、滑动防误触已在真机确认
- 官网桌面/移动端页面本地 Chrome 截图检查过
- 官网内部链接、APK 下载路径、JS 语法检查过

## 10. 编辑约定（重要，务必遵守）

**不要用 PowerShell 编辑源码文件。** 不要用 `Get-Content` / `Set-Content` / `-replace` / `Out-File`
这类管道去读写 `.java`、`.xml`、`.json`、`.md` 等含中文的源文件。

原因：这个环境的 PowerShell 会用 ANSI/GBK 读入、再按 UTF-8 写出，中文会被整体写成乱码；
如果带 `-replace`，还可能把多字节字符的最后一个字节替换成 `?`，导致文件变成非法 UTF-8、
javac 报「未结束的字符串字面量」。已经发生过两次（`AddModuleScreen.java`），
恢复都靠「把乱码按 GBK 反查字节 + 用编译产物里的常量池逐条对回去」，代价很大。

正确做法：

- 改源码一律用 harness 的 `edit` / `write` 工具（按字面量精确替换）
- 需要批量替换时，用 Python 脚本（`D:\docklet\.tools\...` 自带的 python 也可以），
  读写成 `encoding='utf-8'`，并显式 `newline=''`
- 需要跑 shell 的，只做构建、adb、查询，不碰源文件内容

### 10.1 界面规范（用户明确要求过，照做）

- **不要边框线**：按钮、卡片、弹窗一律靠**底色**区分层级，不加 `stroke` / `border` / `outline`。
  次要按钮用实底（`#EFE8DB` + 深灰字），危险按钮用浅红底红字（`#FBEDEA` + `#B4453C`），
  不要画 1px 描边。
- 弹窗统一结构：42dp 图标块 + 标题 17/600 + 副标题 12 + 正文 12.5（关键句加粗）+ 两个 44dp 等宽按钮；
  卡片圆角 20、内边距 22/20。
- 输入框用底 `#F7F2E8` + 圆角 12，聚焦时变白，不加描边以外的东西。

## 11. 2026-11-19 接口收敛（平台契约 1.1.0）

这一轮只做接口收敛，不改 UI。

删除：

```text
DockletBackgroundService 整类 + manifest 声明
docklet.background.start / stop / status（SDK + 桥 + 事件常量）
V3 的旧回退分支、4 秒 background.status 轮询、?session= 读取、iframe docklet:back
window.__dockletSessionId
--v3-top / --v3-bottom / --v3-ime
tools/v3-bg-stop-test.mjs、tools/netlab-bg-test.mjs
```

改成：

```text
安全区：--safe-top / --safe-bottom / --safe-left / --safe-right / --safe-ime + docklet:insets
首页/活动页的运行状态、停止、卸载全部走 MessageSubscriptionStore / DockletMessageService
后台服务：去重落盘、getState().state 默认 offline、没有订阅不唤醒服务
V3 0.10.38，minShell 1.1.0；netlab 1.1.0 改用 backgroundMessages
netlab 测试端点 /events 增加 event: message 与 type/eventId
```

验证：V3 smoke 74/74、window 3/3、pending 全过；壳 `:core:test` + `assembleDebug` 通过。

注意：平台契约已到 1.1.0，旧模块（minShell 1.0.0 且用 background.*）在
新 APK 上会收到 `UNKNOWN_METHOD`；手机端允许卸载重装，不需要兼容层。

### 后续两版（V3）

```text
0.10.39  模型胶囊单独缓存：v3.model.<会话id> = 显示串（如 "deepseek-flash · high"）
         断网/重连/切会话时胶囊不消失，只灰显（.v3modelchip.stale）；点灰胶囊提示"连接后才能切换模型"
         缓存写在 localStorage 的 v3.model. 前缀，index.html 的 persistKey 认领它 → 进 SQLite
0.10.40  断网提示收敛成一句：连接已断开，检查网络和配置。
         原来 5 句（3 句输入区提示 + 2 句 toast）全部走 connectionHintText()
         理由：分类越多越容易猜错（出现过手机没网却提示"有网，检查 relay"）
         恢复时顺手撤掉"已断开"横幅，避免一正一反同时挂着
```

手机安装方式：`dist\dsh-console-beta-0.10.40.pkg` 解包后 `run-as` 拷进
`files/modules/dsh-console-beta/0.10.40/` 并写 `current`（本次实际做法）；
也可以用「应用 → 本地安装 → 链接」配合 `tools/serve-dist.mjs`。

