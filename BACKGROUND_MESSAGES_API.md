# Docklet 后台消息接口 v0.1

> **2026-11-19：v0.1 草案，已被 `V3_SHELL_BACKGROUND_MESSAGES.md` 与
> `docs/module-spec.md` §8.1 取代。** 现行接口只有
> `backgroundMessages.{subscribe, unsubscribe, setActiveSession, getState}`；
> 本文里的 `onEvent` / `onStatus` / `event` 字段没有实现。
> §7 的通知约定仍然有效。

> 给模块方（v3）的接入说明。壳只负责接收和提醒，不保存消息正文，不代发消息。

## 1. 目标

让模块在关闭或不在前台时，仍然能：

- 保持消息接收连接
- 在有新消息时让用户看到、听到
- 用户打开模块后，自己同步和展示消息

## 2. 分工

### 壳负责

- 建立并保持后台连接
- 按模块隔离连接和认证
- 过滤“新消息”事件
- 根据应用声明决定前台是否提醒
- 显示固定通知
- 播放提示音 / 振动（跟随系统设置）
- 连接断开后重连

### 壳不负责

- 发送消息
- 自动回复
- 保存消息正文
- 统计未读数量
- 消息排序或保证送达
- 跨模块消息查询
- 执行模块的业务逻辑

### 模块负责

- 提供接收端点和认证信息
- 声明什么事件算新消息
- 声明前台提醒策略
- 自己同步、展示、处理消息
- 自己发送消息

## 3. 接口

```text
docklet.backgroundMessages.subscribe(options)
docklet.backgroundMessages.unsubscribe()
docklet.backgroundMessages.setActiveSession(sessionId)
docklet.backgroundMessages.getState()
```

### subscribe 参数

```json
{
  "endpoint": "https://example.com/messages",
  "transport": "sse",
  "headers": {
    "Authorization": "Bearer ..."
  },
  "event": "message",
  "foreground": "notify",
  "sessionField": "session",
  "onEvent": function (meta) {},
  "onStatus": function (status) {}
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| endpoint | 是 | 接收地址，建议 HTTPS |
| transport | 是 | 第一版支持 `sse`，`websocket` 待评估 |
| headers | 否 | 运行时认证头，按模块隔离保存 |
| event | 是 | 什么事件算“新消息” |
| foreground | 是 | `notify` 或 `silent` |
| sessionField | 否 | 事件中表示会话的字段，默认 `session` |
| onEvent | 否 | 模块在前台时收到事件的回调 |
| onStatus | 否 | 连接状态变化回调 |

### onEvent 收到的内容

只包含事件元数据，不包含消息正文：

```json
{
  "session": "session-a",
  "eventId": "evt-123",
  "receivedAt": 1760000000000
}
```

壳不保证 `session` 和 `eventId` 一定存在。  
模块需要正文时，自己从服务端同步。

## 4. 事件约定

### SSE

只有指定 `event` 的事件算新消息：

```text
event: message
data: {"session":"session-a","eventId":"evt-123"}
```

心跳、状态、注释等事件不提醒。

### WebSocket（待评估）

按约定的消息类型过滤，例如：

```json
{"type":"message","session":"session-a","eventId":"evt-123"}
```

第一版可以只支持 SSE。

## 5. 前台提醒策略

由模块声明，壳只执行。

```text
foreground: "silent"
→ 模块在前台时不提醒
→ 模块在后台时提醒

foreground: "notify"
→ 模块在前台时也提醒
→ 模块在后台时提醒
```

默认值建议为 `silent`。  
v3 是多会话渠道，建议使用 `notify`，这样用户在当前会话里也能听到其他会话的新消息。

## 6. activeSession

模块可以告诉壳当前正在看哪个会话：

```text
docklet.backgroundMessages.setActiveSession("session-a")
```

离开会话或不再需要过滤：

```text
docklet.backgroundMessages.setActiveSession(null)
```

前台判断：

```text
模块在前台
foreground = notify
事件 session 等于 activeSession
→ 不通知，只通过 onEvent 交给页面

事件 session 不等于 activeSession
→ 通知

事件没有 session 元数据
→ 不做会话过滤
```

后台时：

```text
activeSession 不生效
所有会话的新消息都提醒
```

## 7. 通知表现

每个模块固定一条通知。

```text
标题：模块名
正文：有新消息
```

例如：

```text
DSH 控制台 Beta
有新消息
```

不显示：

- 消息正文
- 发送人
- 会话名
- 未读数量
- 消息列表

点击通知：

- 打开或回到对应模块
- 模块打开后，壳清除该模块的通知

## 8. 声音和振动

- 使用固定“后台消息”通知渠道
- 默认允许声音和振动
- 跟随系统静音、勿扰和通知权限
- 不绕过系统限制

## 9. 连接生命周期

```text
subscribe
→ 壳启动后台连接
→ 连接断开自动重连
→ 模块打开时继续接收
→ unsubscribe 或用户关闭后台消息时停止
```

壳不保证：

- 消息一定送达
- 事件一定不重复
- 连接永远在线
- 消息严格按顺序

模块打开后仍应自己同步服务端历史。

## 10. 状态回调

```text
onStatus({
  state: "connecting" | "online" | "offline" | "error",
  reason: "..."
})
```

只用于显示连接状态，不包含消息内容。

## 11. 隔离与安全

- 接口自动绑定当前模块
- 不接受模块传入其他模块 ID
- 每个模块只能注册自己的端点
- 每个模块只能看到自己的通知状态
- 不提供跨模块消息查询
- 壳不保存消息正文

## 12. 非目标

第一版明确不做：

- 代发消息
- 自动回复
- 消息正文存储
- 未读数量
- 多模块共享消息
- 跨应用读取消息

## 13. 需要 v3 确认

1. 使用 SSE 还是 WebSocket？
2. 事件里是否包含 `session` 和 `eventId`？
3. 认证使用 Bearer Header 还是其他方式？
4. 是否声明 `foreground: "notify"`？
5. 是否需要 `activeSession` 过滤当前会话？
6. 通知是否只需要“模块名 + 有新消息”？
7. 断线重连后，v3 自己拉历史，还是需要壳补事件？
8. 是否接受壳不保证送达、顺序和严格去重？

## 14. 最小接入示例

```text
docklet.backgroundMessages.subscribe({
  endpoint: "https://...",
  transport: "sse",
  headers: { Authorization: "Bearer ..." },
  event: "message",
  foreground: "notify",
  sessionField: "session",
  onEvent(meta) {
    // 只收到 session / eventId / receivedAt
    // 正文由模块自己同步
  },
  onStatus(status) {
    // connecting / online / offline / error
  }
})
```

用户进入会话时：

```text
docklet.backgroundMessages.setActiveSession("session-a")
```

离开会话时：

```text
docklet.backgroundMessages.setActiveSession(null)
```

不再需要后台消息时：

```text
docklet.backgroundMessages.unsubscribe()
```
