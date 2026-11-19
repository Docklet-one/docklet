# V3 接入 Docklet 后台消息（v0.2 实装版）

> 读者：V3
> 对应壳：`BUILD 20261118-154029` 之后的版本
> 目标：V3 页面可以彻底销毁，后台由壳保持连接并提醒；正文和历史仍由 V3 自己拉取。

## 1. 先做 feature-detect

```js
const backgroundMessages =
  window.docklet && window.docklet.backgroundMessages;

if (!backgroundMessages) {
  // 退回现有 background.start
}
```

不要假设所有 Docklet 版本都有这个接口。

## 2. 接口

```text
window.docklet.backgroundMessages.subscribe(options)
window.docklet.backgroundMessages.unsubscribe()
window.docklet.backgroundMessages.setActiveSession(sessionIdOrNull)
window.docklet.backgroundMessages.getState()
```

### subscribe 参数

```js
await docklet.backgroundMessages.subscribe({
  endpoint: "https://relay.example/events?all=1",
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

| 字段 | 必填 | 当前行为 |
|---|---|---|
| endpoint | 是 | http/https；建议 HTTPS；可带 query token |
| transport | 是 | `sse`；如果 endpoint 包含 `/sessions`，自动走 B 模式轮询 |
| headers | 否 | 连接时注入；推荐用 Authorization |
| eventTypes | 是 | 按 JSON 的 `type` 过滤；不传默认 `["turn.end"]` |
| foreground | 是 | `notify` 或 `silent`；默认 `silent` |
| sessionField | 否 | 默认 `session` |
| dedupeBy | 否 | 当前固定按 `eventId` 去重，字段先保留 |
| persist | 否 | 当前订阅都会持久化；`false` 暂未实现 |

返回：

```json
{
  "subscribed": true,
  "moduleId": "v3",
  "foreground": "notify",
  "eventTypes": ["turn.end"]
}
```

失败会抛错，错误码包括：

```text
PERMISSION_REQUESTED
INVALID_ENDPOINT
TOO_MANY_MESSAGE_SUBSCRIPTIONS
MESSAGE_SUBSCRIBE_FAILED
```

### unsubscribe

```js
await docklet.backgroundMessages.unsubscribe();
```

停止该模块订阅，取消该模块通知。  
如果这是最后一个订阅，前台服务停止。

### setActiveSession

```js
await docklet.backgroundMessages.setActiveSession("session-a");
// 离开会话
await docklet.backgroundMessages.setActiveSession(null);
```

只影响“模块在前台时”的通知抑制。  
后台时所有会话都会提醒。

### getState

```js
const state = await docklet.backgroundMessages.getState();
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

`state` 取值：

```text
connecting
online
offline
```

当前没有 `onEvent` / `onStatus` 回调。  
需要状态展示时，由 V3 轮询 `getState()`；不需要状态时可以不调用。

## 3. A 模式：全会话 SSE

这是推荐模式。

```js
await docklet.backgroundMessages.subscribe({
  endpoint: "https://relay.example/events?all=1",
  transport: "sse",
  headers: { Authorization: "Bearer ..." },
  eventTypes: ["turn.end"],
  foreground: "notify",
  sessionField: "session"
});
```

壳期望每条事件：

```json
{
  "type": "turn.end",
  "eventId": "evt-123",
  "session": "session-a",
  "payload": {
    "turn": 6,
    "reason": { "kind": "completed" }
  }
}
```

通知规则：

```text
turn.end，kind 为空或 completed
→ 通知

aborted / interrupted / error
→ 不通知
```

其他 type：

```text
message.assistant.delta
→ 不应放进 eventTypes

heartbeat
→ 不应放进 eventTypes
```

如果以后要更早提醒，可以加 `message.assistant`，但建议壳侧限频，V3 仍按 `eventId` 去重。

## 4. B 模式：轮询 /sessions

A 暂时不可用时使用。

V3 只需把 endpoint 指向：

```text
https://relay.example/sessions?mode=message
```

壳检测到 `/sessions` 后自动进入 B 模式：

```text
每 5 秒轮询一次
首次只建立游标，不提醒
seq = lastAssistantSeq，缺失时用 lastSeq
running == false 且 seq 前进
→ 发一条“模块名 + 有新消息”
```

第一次轮询不通知。  
一次轮询多个会话命中，也只合并成一条通知。

B 模式不区分会话通知内容，用户点开 V3 后由 V3 自己恢复。

## 5. 认证

推荐：

```js
headers: {
  Authorization: "Bearer <token>"
}
```

兼容：

```text
endpoint 带 ?token=...
```

token 不会写到日志，不会暴露给其他模块。

## 6. 前台策略

`foreground: "notify"`：

```text
模块在前台
事件 session != activeSession
→ 通知

事件 session == activeSession
→ 不通知

模块在后台
→ 所有会话通知
```

`foreground: "silent"`：

```text
模块在前台
→ 不通知

模块在后台
→ 通知
```

V3 是多会话，建议：

```text
foreground: "notify"
进出会话时调用 setActiveSession
```

## 7. 通知表现

```text
标题：模块名
正文：有新消息
```

不显示：

```text
消息正文
发送人
会话名
数量
```

每个模块固定一条通知；新的提醒更新同一条通知。  
点击通知打开 V3；V3 自己拉快照。

## 8. 生命周期

```text
subscribe
→ 启动一个共享前台服务
→ 订阅持久化

进程重启
→ START_STICKY 恢复

设备重启
→ BOOT_COMPLETED 恢复

unsubscribe
→ 删除订阅
→ 如果是最后一个，停止服务
```

约束：

```text
最多 8 个模块订阅
一个模块最多一条订阅
壳不保证永久在线
断线后 V3 自己 resnapshot
```

Android 厂商省电策略仍可能杀前台服务。  
壳负责尽量重连，不保证送达。

## 9. 与旧接口的关系

旧接口保持不变：

```js
docklet.background.start(...)
docklet.background.stop()
docklet.background.status()
```

V3 做 feature-detect 后：

```text
有 backgroundMessages
→ 用新接口，不再调用 background.start

没有
→ 保持旧 background.start
```

V3 切到新接口时，壳会停掉同模块的旧单订阅服务，避免双份通知。

## 10. 当前没有做的能力

```text
代发消息
自动回复
消息正文存储
历史队列
未读数量
跨模块消息
onEvent 实时回调
onStatus 实时回调
```

需要正文、历史、发送，仍然由 V3 自己完成。

## 11. V3 接入清单

```text
[ ] feature-detect window.docklet.backgroundMessages
[ ] subscribe endpoint / headers / eventTypes / foreground
[ ] 用 turn.end，不用 delta
[ ] 进入会话 setActiveSession(id)
[ ] 离开会话 setActiveSession(null)
[ ] 卸载/退出登录时 unsubscribe
[ ] getState 显示连接状态（可选）
[ ] 旧版本退回 background.start
```

## 12. 联调检查

```text
1. V3 在前台，当前会话新消息 → 不响
2. V3 在前台，其他会话新消息 → 响
3. V3 在后台，任意会话新消息 → 响
4. 杀掉 V3 WebView → 后台仍能提醒
5. 重启 App → 订阅恢复
6. 重启设备 → 订阅恢复
7. 断网恢复 → 自动重连
8. 两个模块同时订阅 → 各自一条通知
9. 连续多轮 turn.end → 同一模块只更新一条通知
10. aborted / error / interrupted 的 turn.end → 不响
```
