# V3 迁移需要壳补的能力

> **2026-11-19 订正：本文件的 P0-4（通知正文）与 P0-6（通知模式）已作废。**
> 通知内容以 `BACKGROUND_MESSAGES_API.md` §7 的约定为准：每个模块固定一条通知，
> 标题为模块名，正文为「有新消息」，不显示消息正文、发送人、会话名或未读数。
> `background.start / stop / status` 已从平台契约 1.1.0 删除，V3 只使用
> `docklet.backgroundMessages.*`。

> **边界（2026-11-15 订正）**：本文件只写「V3 需要壳做什么」的契约；
> 壳（`shell/`）由**另一个对话**负责改，V3 这边不改壳代码。
> 模块侧该配合的已在 `apps/dsh-console-beta` 落地，见每项「模块侧」。
>
> 双槽、通知切模块、stopAndWait、CrashGuard 已通。  
> P0-1/P0-2/P0-3 已实现；P1-1/P1-2 已用 mock server 验证；P1-3 已出包。  
> 现在缺的 P0 全在壳的通知服务：**通知正文、前台会话抑制、通知模式**。
>
> 最近验证（2026-11-14）：
> - 通知 tag 为 `docklet-event-netlab-sess-demo-m-3`
> - `shell.info().initial.sessionId === "sess-demo"`
> - `/health`、`/events`（V3 envelope）、`/upload` 均连通
> - `background.stop` 返回 `{running:false}`，服务与 prefs 均清空
> - 通用下载 API 已提供：`download` 能力 + `docklet.download(url, filename)`；V3 不特殊处理，后续应用需在 module.json 声明 `download`
>
> 最近验证（2026-11-16 23:07，模块侧）：
> - `dsh-console-beta-0.6.19.pkg`（96,096 B，SHA256 `7737F6E4…`）已构建并发布到
>   `http://192.168.2.6:8791/dsh-console-beta-0.6.19.pkg`，`demos.json` 已指向它
> - 0.3.59 起接上壳的全局返回：`docklet.onBack(tt)`；0.3.60 换网局域网优先；
>   0.3.61 会话列表缓存；0.3.62 离线能进会话；0.3.63 断线无感；0.3.64 标题兜底；
>   0.3.65 断线呼吸灯 + 输入框禁用提示；0.3.66 灯尺寸对齐 + 提示闪色；0.3.67 提示真元素闪色 + 灯垂直对齐；0.3.68 事件门放宽 + 快照节流；0.3.69 多图分档网格 + 附件 chips；0.3.70 查看器左右滑动；0.3.71 逐张停住 + 保存按钮；0.3.72 保存改图标；0.3.73 探测校验令牌 + 切换冷却；0.3.74 修 markdown 卡死；0.3.75 失败原因显示在对话里；0.3.76 失败后气泡收尾；0.3.77 键盘不自动弹起；0.3.78 停止按钮变形不闪；0.3.79 同尺寸 46×46 + 0.25s；0.3.80 停止记录本地展示；0.3.81 停止提示走消息行样式；0.3.82 多图孤图缩略图；0.3.83 查看器按气泡收图；0.3.84 舞台+控制条+双指缩放；0.3.85 计数居中+图标点击修复；0.3.86 图片唤醒时机；0.3.87 交付卡片持久化；0.3.88 afterId 定位 + 回合折叠；0.3.89 时间线一次排好；0.3.90 交付折叠阈值 3；0.3.91 交付锚到助手消息；0.3.92 分支 + 归档恢复；0.3.93 归档按钮开关；0.3.94 归档独立页；0.3.95 归档行内边距；0.3.96 接入 backgroundMessages；0.3.97 问答卡换行/不回答；0.3.98 保留未回答卡片；0.3.99 未回答标签改已拒绝外观；0.4.0 审批卡去 tool chip；0.4.1 交付卡片合并保留；0.4.2 卡片随 turn.end 立即显示；0.4.3 加载气泡三态；0.4.4 设置页显示家里/外部；0.4.5 页头也显示；0.4.7 面板中文化（已回退）；0.4.8 面板回退只留模型与一条提示；0.4.9 页头呼吸灯与网络快切；0.5.0 网络面板测速/保存；0.5.1 测速与模式同排；0.5.2 间距与标签样式；0.5.3 锁定家里/外部；0.5.4 切会话不闪重连；0.5.5 断网探活；0.5.6 按插件连通性判断；0.5.7 接入 network 两层判断；0.5.8 修 lanBase 覆盖；0.5.9 返回/配置页保护；0.5.10 手风琴默认关闭；0.5.11 交付卡紧凑化；0.5.12 交付卡锚定修复；0.6.0 回合产物模型；0.6.1 过程折叠；0.6.2 草稿/空态；0.6.3 会话列表/缓存兜底；0.6.4 历史窗口 80；0.6.5 分页状态持久化；0.6.6 滚动恢复；0.6.7 长回复折叠；0.6.8 日期/骨架屏；0.6.9 视觉细节；0.6.10 设置/代码块；0.6.11 会话状态简化；0.6.12 设置展开态；0.6.13 通知区收起；0.6.14 失败重试；0.6.15 乐观发送；0.6.16 头像排版；0.6.17 回到底部避让；0.6.18 消息头/键盘滚动；0.6.19 重复气泡修复；0.4.6 模型胶囊与切换面板；0.4.7 面板中文化
> - 0.3.55 起未配置直接进设置页；0.3.56/57 长回复「↑ 回到开头」；0.3.58 图片缓存回收
> - 0.3.53 起 `/sessions` URL 固定带 `mode=turn|message`（P0-6 的模块半边）
> - 0.3.54 修了 0.3.52 的回归：已配置冷启动走 `Re()`，之前不调 `K()`
> - 测试：smoke 45/45、事件门 8/8、多图 14/14、markdown 10/10、失败返回 11/11、气泡收尾 8/8、键盘 7/7、markdown 10/10、换网 3/3、mode URL 6/6、图片缓存 9/9
> - 真机壳首页 BUILD = `20261115-001723`；壳由另一对话在改，此处不跟踪其 BUILD

## P0-1 通知携带 sessionId ✅

已改：

```text
ModuleRouterActivity.EXTRA_SESSION_ID
DockletBackgroundService -> ModuleRouterActivity
DockletBridge.notify.show sessionId
MainActivity.EXTRA_OPEN_SESSION
ModuleActivity.EXTRA_SESSION_ID
shell.info().initial.sessionId
```

同一模块收到不同 session 时不会关闭重开，而是：

```text
docklet:session event
window.__dockletSessionId
```

如果目标模块不同，则走 `NEXT_MODULE + NEXT_SESSION` 切换。

V3 需要：

```text
点某条会话的通知
→ 打开 dsh-console
→ 直接进入对应 session
```

需要改：

```text
notify.show 支持 sessionId
ModuleRouterActivity 支持 EXTRA_SESSION_ID
MainActivity / ModuleActivity 把 initialSession 传给模块
```

传递方式建议：

```text
Intent extra -> ModuleActivity -> WebView URL ?session=<id>
或 shell.info 返回 initial: { sessionId }
```

## P0-2 后台 SSE 解析 ✅

已实现：

```text
识别 data: / event: 行
忽略 id / retry
按 JSON envelope 解析 type / sessionId / id
只通知 message / message.assistant / turn / turn.end / message.completed
从 data / params / content 提取正文
通知带 sessionId
```

实测 mock SSE：

```text
event: message
data: {"protocolVersion":1,"type":"message.assistant","sessionId":"sess-demo","id":"m-3","text":"后台第 3 条消息"}

Notification tag = docklet-event-netlab-sess-demo-m-3
android.text = 后台第 3 条消息
```

## P0-3 前后台去重 ✅ 基础

已实现：

```text
通知 tag = docklet-event-<module>-<session>-<eventId>
notificationId = hash(module|session|eventId)
seen_events 持久化到 SharedPreferences
服务重连/重启后同一 eventId 不再通知
```

前台 `notify.show` 传相同的 `sessionId` + `eventId` 时，会命中同一 tag/id，覆盖而不是叠加。

仍需 V3 模块配合：前台和后台都使用同一条事件的 id。

## P0-4 通知正文（已作废，见文首订正）

通知按约定只显示「模块名 + 有新消息」，不取消息正文。
下面保留 relay 的响应结构，只用于排查数据。

### 真实 relay 响应结构（2026-11-15 核对，8788）

`GET /sessions?token=…` → `{ "current": "<id>", "sessions": [ … ] }`，每个会话：

```json
{
  "id": "session-…",
  "title": "会话标题",
  "snippet": "会话标题",
  "at": 1794682402378,
  "lastMessageAt": 1794682402378,
  "lastSeq": 237,
  "online": true,
  "running": true,
  "blank": false,
  "cwd": "D:\\docklet"
}
```

`GET /snapshot?session=<id>&turns=1&token=…` **没有 `turns` 字段**（mock server 的
`turns` 是假的），真实结构是：

```json
{
  "ok": true,
  "session": { … },
  "cursor": 18417,
  "truncated": true,
  "source": "…",
  "snapshotMode": "tail-chunk",
  "events": [
    { "id": "s18414", "seq": 18414, "type": "message.assistant",
      "at": 1794682205325,
      "payload": { "text": "回复正文…", "reasoning": "…", "usage": { … } } },
    { "id": "s18417", "seq": 18417, "type": "turn.end",
      "at": 1794682205401, "payload": { "turn": 128, "reason": { "kind": "completed" } } }
  ],
  "pending": …,
  "projections": …,
  "activity": …
}
```

## P0-5 前台会话抑制 ✅

已实现：`backgroundMessages.setActiveSession(sessionIdOrNull)`。

目标：

```text
用户正在 V3 里看会话 A，A 来消息不弹后台通知
B 来消息照常通知
```

建议接口（二选一，壳侧定）：

```text
background.focus(sessionId) / background.blur()
或壳的 ModuleRuntimeHost 直接提供当前前台 module + session
```

壳服务在通知前判断：

```text
event.sessionId == foregroundSession
且模块在前台
-> skip
```

模块侧已具备的条件：

```text
V3 切会话时写 localStorage('v3.session')，index.html 已 hook setItem/removeItem
vue 页面可见性变化走 visibilitychange，pagehide 时 flush()
=> 只要壳确认接口名，V3 加 focus/blur 调用是几行的事
```

**V3 现在不调 focus/blur**：接口没定，先不猜。壳定下名字（或改成读
`active_module + session`）后告诉 V3 这边，再补调用。

## P0-6 通知模式（已取消：壳一律按 turn 处理）

V3 的设置里已没有「每条消息」选项，`startWatchService` 固定发 `mode=turn`；
壳侧忽略 `mode`，只在一轮 `running` 由 true 变 false 时提醒一次。参数保留为
预留位，恢复该设置前两边都不需要改。

```text
/sessions?token=…&mode=turn|message
```

模块侧映射（`index.html` → `startWatchService`）：

```text
v3.notifyMode == "message" -> policy.mode == "change" -> mode=message
其它（v3.js 的 turn 分支不带 mode）              -> mode=turn
token 为空时也带 mode
mode 变化会重启监听（v3.js 的 state 签名含 mode）
```

壳服务要做：

```text
turn    ：同一会话 running 由 true 变 false 时通知一次（或收到 turn.end）
message ：lastMessageAt 每变化一次都通知
mode 参数缺失：按 turn 处理（0.3.53 之前的旧模块包不发 mode）
```

`/sessions` 的每个会话已带 `running` 字段（见 P0-4 的结构），turn 模式不需要
再猜；服务端会忽略 `mode` 参数（已实测 200）。

验证方法（壳侧）：

```text
装 0.3.53 -> 设置里切「回合结束」-> 后台监听
发一条需要多轮工具的消息：running 期间不通知，turn 结束后只通知一次
切「每条消息」：每条 assistant 消息都通知
记录 background.start 收到的 URL，应含 mode=turn / mode=message
```

## P1-1 网络链路实测 ✅ mock server

`tools/v3-mock-server.mjs` 提供 `/health`、`/snapshot`、`/events`、`/send`、`/upload`。

实测：

```text
GET /health -> 200 {"ok":true,"service":"docklet-v3-mock"}
GET /events -> SSE connected
event message.assistant 正常进入前台日志
```

## P1-2 文件上传链路 ✅ 小文件

`tools/v3-file-upload-test.mjs` 实测：

```text
files.write('upload/test.txt') -> ok
files.read('upload/test.txt',{encoding:'base64'}) -> 20 bytes
POST http://10.0.2.2:8789/upload -> {"ok":true,"bytes":20}
```

限制：单文件 5 MB，总配额 50 MB。  
大文件仍未做分块/base64 桥优化。

### 已补：WebView 文件选择器

`ModuleRuntimeHost` 已实现：

```text
WebChromeClient.onShowFileChooser(...)
```

所以 V3 的 `<input type="file">`（附件 +）现在可直接弹系统文件选择器；支持 accept / multiple。上传 API 已通。

## P1-3 V3 包构建与安装 ✅

```text
D:\docklet\dist\dsh-console-beta-0.6.19.pkg
http://192.168.2.6:8791/dsh-console-beta-0.6.19.pkg
```

```text
apps/dsh-console-beta 打包为 .pkg
dist/demos.json 指向 0.3.44
V3 数据目录 data/dsh-console-beta
```

壳侧安装链路已具备，URL 安装可用。  
真机上的模块版本尚未确认（电脑侧 adb 无设备）。

## P2 质量和收尾

```text
shell 重启后恢复模块 UI 路由
低内存重建 instrumentation
真机 OEM WebView
```

## 清除数据仍留 WebView localStorage

`MainActivity.performClearData()` 只删 `data/<id>` 和 CrashGuard，没有删 WebView origin：

```text
WebStorage.getInstance().deleteOrigin(module.origin())
```

结果：

```text
清除模块数据后 SQLite 空了
但 https://<id>.module.local 的 localStorage 还在
V3 下次启动继续用旧 IP/令牌自动连接
```

壳侧应在 `performClearData` 里和卸载一样调用 `deleteOrigin`。  
V3 0.3.9 已做兜底：SQLite 设置表为空时清掉旧 `v3.*` localStorage。

## 预置 V3 仍未移除（待壳确认）

交接说「URL 安装、APK 内不再预装 V3」，但当前 dist APK 不是这样：

```text
dist/docklet-shell-0.1.0-debug.apk   496,096 B
BUILD 20261115-012017
classes3.dex 含 preinstallBundledV3 / v3_preinstalled
assets/dsh-console-beta-0.6.19.pkg 仍在（APK 比模块包慢一版）
MainActivity.java:198 仍调用 preinstallBundledV3()
```

结果：

```text
全新安装首次启动 -> 自动装 0.3.42
已装 0.3.44 后启动 -> manager.get 成功，只写标记，不覆盖
URL 装 0.3.44 属升级，ModuleManager 允许
仓库里的旧 0.3.42 不会降级覆盖（VERSION_DOWNGRADE）
```

要满足「APK 内不预装」，壳侧需要删调用/方法并重打 APK；
本文件只记录，不改 `shell/`。

另外 APK 内 `assets/demos.json` 仍是 beta 0.3.42，和 `dist/demos.json` 的 0.3.44 不一致；
重打 APK 时一并同步。

## 建议顺序

```text
[x] 1. sessionId 路由
[x] 2. 后台 SSE 解析 + 去重
[x] 3. 网络链路实测
[x] 4. 文件上传链路（小文件）
[x] 5. V3 包构建
[x] 6a. 通知模式的模块半边（0.3.53 传 mode）
[x] 6b. 通知正文 —— 按约定不做（每模块一条「模块名 + 有新消息」）
[x] 7. 前台会话抑制 —— backgroundMessages.setActiveSession
[x] 8. 通知模式 —— 取消，壳一律 turn
[ ] 9. 确认 APK 是否还预置 V3     <- 壳
```

## 分工（2026-11-15）

```text
V3 这边（本会话）：apps/dsh-console-beta、dist/dsh-console-beta-*.pkg、demos.json、V3_BETA.md
壳那边（另一个对话）：shell/、APK、STATUS.md、performClearData、预置 V3
本文件：只写契约，不替壳改代码
```
