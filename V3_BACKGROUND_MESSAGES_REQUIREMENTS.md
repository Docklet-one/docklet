# V3 对后台消息接口的确认与要求

> 读者：壳 / relay 对话
> 来源：`docklet/BACKGROUND_MESSAGES_API.md`（v0.1）+ V3 当前实现
> 目的：把“通知有时有、有时没有”和“模块页面不敢真正退出”这两个问题一次定清
> 日期：2026-11-15

## 1. 背景与目标

现在的现象：

- 通知时有时无，大概率是后台连接挂在会被系统回收的地方（WebView 计时器或普通后台 Service）
- 模块一多，各自保活会让整个 App 变卡、被杀得更狠
- 我们希望**模块页面可以彻底退出**（WebView 销毁、不占内存），后台提醒由壳统一负责

目标（和 v0.1 文档一致）：

```text
壳：一个常驻前台服务，替所有模块维持连接、过滤、提醒
模块：只声明「连哪里、什么算新消息、前台怎么提醒」，页面随时可销毁
用户：单应用退出后不占 CPU/内存，通知仍能到；打开模块后自己同步历史
```

## 2. 对 v0.1 文档第 13 节的回答

| # | 问题 | V3 的回答 |
|---|---|---|
| 1 | SSE 还是 WebSocket | SSE。relay 目前只有 `GET /events?session=...&after=...&token=...`（EventSource），没有 WebSocket |
| 2 | 事件是否包含 `session` / `eventId` | 有 `eventId`（DSH 事件自带），**没有 `session`**；session 由订阅 URL 的 `session` 参数隐含 |
| 3 | 认证方式 | relay 用 `Authorization: Bearer <token>`，但 EventSource 不能设 header，实际 SSE 走 `?token=` 查询参数。壳必须支持 endpoint 内带凭据，或代持 header 转发 |
| 4 | 是否声明 `foreground: "notify"` | 是。V3 是多会话；当前会话靠 `activeSession` 抑制，其他会话要提醒 |
| 5 | 是否需要 `activeSession` 过滤 | 要，但前提是事件里带 session 或每个会话独立订阅；现在的 relay 一个订阅只能听一个会话 |
| 6 | 通知是否只要“模块名 + 有新消息” | v0.1 可以接受，但请保留以后加“会话名/摘要”的扩展位（见 §5） |
| 7 | 断线重连后谁拉历史 | V3 自己拉：回前台 `resnapshot`、加载更早一页；壳不需要补事件 |
| 8 | 接受不保证送达/顺序/严格去重 | 接受；V3 用快照覆盖 + `eventId` 去重，重复事件最多一次多余渲染，不会乱序 |

## 3. 三个必须由壳/relay 先定的阻塞点

### 3.1 多会话覆盖

relay 的 `/events` 必须带 `session`，一个 SSE 连接只能听一个会话。后台 API 只有一个
`subscribe(endpoint)`，所以后台最多只能听到“当前会话”的新消息，其他会话不会响。

需要二选一：

```text
A. relay 增加“全会话”事件流（例如 /events?all=1），壳订阅一次覆盖所有会话
B. 壳继续用现在的轮询：GET /sessions?mode=message，按 lastAssistantSeq 变化判断
```

V3 侧两个都能配合；请壳明确走哪条，V3 的 `subscribe` 参数会跟着定。

### 3.2 事件过滤必须用 JSON 字段，不是 SSE 事件名

relay 的 SSE 只发 `data: {...}`，没有 `event:` 名。按 v0.1 的 `event: "message"` 过滤，
会把 tool.start、heartbeat 等全部当成新消息。

建议接口增加：

```text
eventTypes: ["message.assistant", "turn.end"]   // JSON 里的 type 字段
```

或在文档里明确“event 是 JSON 字段名，不是 SSE event 名”。

### 3.3 认证

EventSource 不能加 header。请支持：

```text
endpoint 里允许 ?token=...（现状）
或壳在建立连接时把 headers 注入到底层请求（如果壳自己实现 SSE 客户端，而不是用 WebView EventSource）
```

## 4. 建议补进规范的内容（可靠性）

### 4.1 前台服务 + 单服务多路复用

```text
一个 dataSync 类型的前台服务，持有所有模块的订阅
固定“后台消息”通知渠道；服务自身通知与模块通知分开
每个模块一条通知（标题=模块名，正文=有新消息），不累积、不叠加
```

### 4.2 订阅持久化与开机恢复

```text
订阅配置（endpoint / headers / eventTypes / foreground / sessionField）落盘
进程重启、设备重启（BOOT_COMPLETED）后自动恢复
壳被系统杀死后重新拉起时恢复连接
```

这一条是“通知时有时无”的关键；现在的 v0.1 没写。

### 4.3 去重与合并

```text
重连后按 eventId 去重，避免一次重连弹多条相同通知
短时间多条新消息只更新同一条通知（setOnlyAlertOnce），不要刷屏
```

### 4.4 Android 限制要写清

```text
Doze / 厂商省电策略仍可能杀前台服务；壳负责“尽量在线”，不保证送达
建议壳引导用户把应用加入电池优化白名单（提供入口）
通知权限被拒时 onStatus 报 error，并给出可点击的修复入口
```

### 4.5 页面生命周期

```text
模块页面允许被销毁；壳不得依赖模块 WebView 维持连接
通知点击：打开模块 -> 模块自己拉历史 -> 壳清除该模块通知
模块在前台时，onEvent 只给元数据；前台提醒策略按 foreground + activeSession 执行
```

## 5. V3 需要的接口细节（建议 v0.2）

```text
subscribe({
  endpoint,                 // 支持 ?token=
  transport: "sse",
  eventTypes: ["message.assistant", "turn.end"],   // JSON type 过滤（新增）
  foreground: "notify",
  sessionField: "session",  // 事件没有时忽略
  dedupeBy: "eventId",      // 新增：去重键
  persist: true,            // 新增：订阅持久化 + 开机恢复
  onEvent(meta),            // 仅元数据：session? / eventId / receivedAt
  onStatus(status)          // connecting / online / offline / error
})

setActiveSession(sessionId | null)
getState() -> { state, lastEventAt, lastError, subscriptions }
```

`getState()` 建议返回最近一次事件时间和错误，方便 V3 在设置页显示“后台提醒状态”。

## 6. V3 侧会怎么接（供壳参考）

```text
feature-detect：有 backgroundMessages 就用，没有就退回旧 background.start
subscribe：endpoint + token、foreground: notify、eventTypes 按上表
进出会话：setActiveSession(id) / setActiveSession(null)
页面可销毁：V3 打开时先画缓存，再拉快照（已有）
打开模块后：壳清通知；V3 自己 resnapshot 补齐历史
```

## 7. 需要壳方回复的四个问题

1. 现在的 `background.start` 是前台服务，还是 WebView 里的计时器？（决定“被杀”的原因）
2. 能否做成一个常驻前台服务，支持订阅持久化、开机恢复、多模块复用？
3. 认证能否支持 query token（或壳代持 header）？
4. 多会话走 A（relay 全会话 SSE）还是 B（壳轮询 `/sessions?mode=message`）？

这四条定了，V3 再按最终接口改代码；在那之前 V3 保持现有 `background.start` 轮询，不动。
