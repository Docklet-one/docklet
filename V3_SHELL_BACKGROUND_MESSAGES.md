# Docklet 通知接口（给 V3）

> 适用：旧 `window.docklet.background.*` 接口删除后的版本。  
> V3 只依赖本文档，不再回退旧接口。

## 1. 最终保留的接口

后台通知只保留一套：

```text
window.docklet.backgroundMessages.subscribe(options)
window.docklet.backgroundMessages.unsubscribe()
window.docklet.backgroundMessages.setActiveSession(sessionIdOrNull)
window.docklet.backgroundMessages.getState()
```

直接发送单条通知使用：

```text
window.docklet.notify.show(options)
```

已删除、不要使用：

```text
window.docklet.background.start()
window.docklet.background.stop()
window.docklet.background.status()
window.docklet.backgroundMessages.start()
window.docklet.backgroundMessages.status()
window.docklet.backgroundMessages.stop()
```

`backgroundMessages` 是后台订阅，`notify.show` 是单条直接通知，两者不是同一层接口。

## 2. 能力与系统权限

### 后台订阅

清单声明：

```json
"capabilities": ["background"]
```

系统要求：

```text
Android 13+ 需要通知权限
```

未授权返回：

```text
PERMISSION_REQUESTED
```

### 直接通知

清单声明：

```json
"capabilities": ["notify"]
```

系统同样需要通知权限。

用户关闭应用通知或系统通知后，通知会被抑制。

---

# 3. `window.docklet.backgroundMessages.subscribe(options)`

建立后台消息订阅。壳负责保持连接、提醒和去重；V3 只负责展示和业务。

```js
await window.docklet.backgroundMessages.subscribe({
  endpoint: "https://relay.example.com/events?all=1",
  transport: "sse",
  headers: {
    Authorization: "Bearer ..."
  },
  eventTypes: ["turn.end"],
  foreground: "notify",
  sessionField: "session",
  dedupeBy: "eventId",
  persist: true
});
```

## 参数

| 参数 | 必填 | 当前行为 |
|---|---|---|
| `endpoint` | 是 | `http` / `https`，建议 HTTPS；可带 query token；最长 4096 |
| `transport` | 是 | 当前为 `sse`；endpoint 含 `/sessions` 时自动走轮询 |
| `headers` | 否 | 连接时注入；推荐 `Authorization` |
| `eventTypes` | 是 | 按事件 `type` 过滤；默认 `["turn.end"]` |
| `foreground` | 是 | `notify` 或 `silent`，默认 `silent` |
| `sessionField` | 否 | 默认 `session` |
| `dedupeBy` | 否 | 当前固定按 `eventId` 去重 |
| `persist` | 否 | 当前订阅都会持久化；`false` 暂未实现 |

## 返回

```json
{
  "subscribed": true,
  "moduleId": "v3",
  "foreground": "notify",
  "eventTypes": ["turn.end"]
}
```

## 错误码

```text
PERMISSION_REQUESTED
INVALID_ENDPOINT
TOO_MANY_MESSAGE_SUBSCRIPTIONS
MESSAGE_SUBSCRIBE_FAILED
```

---

# 4. `window.docklet.backgroundMessages.unsubscribe()`

停止当前模块的后台订阅，并取消该模块的后台通知。

```js
await window.docklet.backgroundMessages.unsubscribe();
```

如果这是最后一个订阅，壳会停止后台服务。

---

# 5. `window.docklet.backgroundMessages.setActiveSession(sessionIdOrNull)`

设置 V3 当前正在查看的会话。

```js
await window.docklet.backgroundMessages.setActiveSession("session-a");
```

离开会话时：

```js
await window.docklet.backgroundMessages.setActiveSession(null);
```

它只影响 V3 在前台时的通知抑制：

```text
foreground: "notify"
  当前 activeSession 的事件 → 不通知
  其他 session 的事件       → 通知
  V3 在后台                 → 所有 session 都通知

foreground: "silent"
  V3 在前台  → 所有事件都不通知
  V3 在后台  → 所有事件都通知
```

`setActiveSession(null)` 后，前台不再有“当前会话”抑制。

---

# 6. `window.docklet.backgroundMessages.getState()`

查询订阅和连接状态。没有 `onStatus` 回调，需要状态时轮询。

```js
const state = await window.docklet.backgroundMessages.getState();
```

返回：

```json
{
  "subscribed": true,
  "activeSession": "session-a",
  "state": "online",
  "lastError": "",
  "lastEventAt": 1760000000000
}
```

`state` 只能取：

```text
connecting
online
offline
```

---

# 7. 事件格式

SSE 的每条 `data:` 是一行 JSON：

```json
{
  "type": "turn.end",
  "eventId": "evt-123",
  "session": "session-a",
  "payload": {
    "turn": 6,
    "reason": {
      "kind": "completed"
    }
  }
}
```

字段：

| 字段 | 说明 |
|---|---|
| `type` | 事件类型，必须与 `eventTypes` 匹配 |
| `eventId` | 去重键；同一 `eventId` 不重复通知 |
| `session` | 会话标识，字段名可由 `sessionField` 改 |
| `payload` | V3 自己的业务数据，壳不解析 |

通知规则：

```text
turn.end，reason.kind 为空或 completed → 通知
aborted / interrupted / error → 不通知
```

`message.assistant.delta`、`heartbeat` 不应放入 `eventTypes`。

---

# 8. `window.docklet.notify.show(options)`

直接发送一条系统通知。它不是后台连接，不负责保持 SSE。

```js
await window.docklet.notify.show({
  title: "V3",
  body: "有一条新消息",
  sessionId: "session-a",
  eventId: "evt-123"
});
```

参数：

| 参数 | 说明 |
|---|---|
| `title` | 标题，最长 80 字；默认模块名 |
| `body` | 正文，最长 300 字 |
| `sessionId` | 点击通知后带回 V3 的会话标识 |
| `eventId` | 同一事件去重/更新 |
| `tag` | 通知 tag，默认 `module-<id>` |

成功：

```json
{ "shown": true, "tag": "module-v3" }
```

通知被用户关闭或被抑制：

```json
{ "shown": false, "suppressed": true }
```

可能错误：

```text
PERMISSION_REQUESTED
PERMISSION_REQUIRED
NOTIFY_FAILED
```

---

# 9. V3 推荐接入

旧接口已删除，不要做 `background.start` 回退。

```js
const messages = window.docklet.backgroundMessages;

await messages.subscribe({
  endpoint: "https://relay.example.com/events?all=1",
  transport: "sse",
  headers: {
    Authorization: "Bearer ..."
  },
  eventTypes: ["turn.end"],
  foreground: "notify",
  sessionField: "session"
});

// V3 正在查看某个会话
await messages.setActiveSession("session-a");

// 离开会话
await messages.setActiveSession(null);

// 轮询状态
const state = await messages.getState();

// 用户关闭后台消息
await messages.unsubscribe();
```

---

# 10. 旧接口到新接口对照

仅用于迁移，不提供旧接口。

| 旧接口（已删除） | 新接口 |
|---|---|
| `window.docklet.background.start({ url, title })` | `window.docklet.backgroundMessages.subscribe({ endpoint, eventTypes, foreground, ... })` |
| `window.docklet.background.stop()` | `window.docklet.backgroundMessages.unsubscribe()` |
| `window.docklet.background.status()` | `window.docklet.backgroundMessages.getState()` |
| 直接通知 | `window.docklet.notify.show(options)` |

映射：

```text
url      → endpoint
title    → 不再使用；通知标题由 V3 模块名决定
status   → getState()
```

---

# 11. 不要使用

```text
window.docklet.backgroundMessages.start()
window.docklet.backgroundMessages.status()
window.docklet.backgroundMessages.stop()
```

这三个方法不存在。

```text
window.docklet.background.start()
window.docklet.background.stop()
window.docklet.background.status()
```

旧后台连接接口已删除。

---

# 12. 最小判断

V3 只需要判断一次：

```js
if (!window.docklet || !window.docklet.backgroundMessages) {
  throw new Error("当前 Docklet 版本不支持后台消息");
}
```

然后只使用：

```text
window.docklet.backgroundMessages.subscribe
window.docklet.backgroundMessages.unsubscribe
window.docklet.backgroundMessages.setActiveSession
window.docklet.backgroundMessages.getState
window.docklet.notify.show
```
