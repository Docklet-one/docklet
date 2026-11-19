# V3 后台消息接口：壳方回复

> 读者：V3 / relay
> 对应：`V3_BACKGROUND_MESSAGES_REQUIREMENTS.md`
> 日期：2026-11-15

## 1. 结论摘要

```text
1. 当前 background.start 是前台服务，不是 WebView 计时器
2. 可以做常驻多模块服务，但要重做单订阅结构
3. 认证可以继续用 query token，也可以加 header；建议 header 优先
4. 多会话优先 A（relay 全会话 SSE），B 只作为兼容
```

壳的方向不变：

```text
壳负责接收、过滤、通知
V3 负责正文、历史、发送和业务
模块页面可以随时销毁
```

## 2. 当前 background.start 是什么

### 是前台服务

调用链：

```text
docklet.background.start
→ DockletBridge.backgroundStart()
→ startForegroundService()
→ DockletBackgroundService
→ startForeground(dataSync)
→ 独立线程 HttpURLConnection 读取 SSE
```

所以它不是 WebView 内的计时器，也不依赖模块页面存活。

### 但当前实现有四个限制

```text
1. 单模块
   已经有一个模块在后台时返回 BACKGROUND_BUSY

2. START_NOT_STICKY
   服务被杀后不会自动重建

3. 没有 BOOT_COMPLETED
   设备重启后不会恢复

4. 只持久化 module_id 和 seen_events
   endpoint、headers、订阅配置都没有持久化
```

这四点是“通知有时有、有时没有”的主要原因。

不是 WebView 计时器的问题，而是：

```text
服务被杀后没有恢复
重启后没有恢复
单模块结构无法覆盖多模块
```

## 3. 能否做成常驻前台服务，多模块复用

可以，但需要把当前单订阅服务改成订阅注册表。

需要支持：

```text
一个 dataSync 前台服务
多个模块订阅
每个订阅独立连接和重试
订阅配置落盘
START_STICKY / 显式拉起
BOOT_COMPLETED 恢复
断线退避
每模块独立通知
```

当前结构是：

```text
一个 moduleId
一个 sourceUrl
一个 worker
一个 runningModuleId
```

多模块版本不能继续用单例字段。

### Android 限制

需要明确写进规范：

```text
Doze / 厂商省电策略仍可能杀前台服务
Android 新版本对 dataSync 前台服务有时长限制
壳只保证“尽量在线”，不保证永久在线
断线后必须允许重连和补拉
```

## 4. 认证

### 现状

当前 bridge 只接收：

```text
url
moduleName
title
```

relay 现在把 token 放在 query：

```text
https://.../events?session=...&token=...
```

当前是可用的。

### header 支持

壳使用原生 `HttpURLConnection`，不是 WebView `EventSource`，所以 header 没有技术障碍。

建议：

```text
保留 ?token= 兼容
新增 headers 参数
优先 Authorization Header
不要把带 token 的 URL 写日志
持久化时按模块隔离
```

### 安全要求

- token 不进入普通日志
- token 不暴露给其他模块
- token 落盘需要单独保护
- 不把 token 回传给 WebView 以外的模块

## 5. 多会话：A 还是 B

### 当前已有 B 的雏形

`DockletBackgroundService` 里已有：

```text
runSessionsWatch()
```

当 URL 包含 `/sessions` 时：

```text
每 5 秒请求一次
比较 lastMessageAt
变化就发通知
```

所以 B 不是从零开始。

但它是 V3 专用逻辑：

```text
5 秒延迟
轮询耗电
依赖 /sessions 返回结构
壳里出现 V3 专用代码
```

### 推荐 A

```text
relay 提供全会话 SSE
一条连接覆盖所有会话
事件带 session
壳订阅一次
```

优点：

- 实时
- 一条连接
- 省电
- 多会话自然支持
- 壳不需要理解 `/sessions`

缺点：

- relay 要改
- 事件必须带 session

### B 的定位

```text
A 做不到时的兼容方案
```

如果 relay 短期不能加全会话流：

```text
壳保留 /sessions 轮询
但标记为 V3 兼容适配
不作为通用接口
```

## 6. 建议的接口 v0.2

```text
docklet.backgroundMessages.subscribe({
  endpoint,                 // 支持 ?token=
  transport: "sse",
  headers,                  // 可选，优先 Authorization
  eventTypes: ["message.assistant", "turn.end"],
  foreground: "notify",
  sessionField: "session",
  dedupeBy: "eventId",
  persist: true,
  onEvent(meta),
  onStatus(status)
})
```

### 参数说明

| 字段 | 必填 | 说明 |
|---|---|---|
| endpoint | 是 | 接收地址，建议 HTTPS |
| transport | 是 | 第一版 `sse` |
| headers | 否 | 认证头，按模块隔离保存 |
| eventTypes | 是 | JSON `type` 过滤，不是 SSE event 名 |
| foreground | 是 | `notify` 或 `silent` |
| sessionField | 否 | 会话字段名，默认 `session` |
| dedupeBy | 否 | 默认 `eventId` |
| persist | 否 | 默认 `true` |
| onEvent | 否 | 只给元数据 |
| onStatus | 否 | 连接状态 |

### onEvent 只给元数据

```json
{
  "session": "session-a",
  "eventId": "evt-123",
  "receivedAt": 1760000000000
}
```

不包含消息正文。

## 7. 前台策略

由应用声明，壳只执行。

```text
foreground: "silent"
→ 模块在前台不提醒

foreground: "notify"
→ 模块在前台也提醒
```

V3 是多会话渠道，建议：

```text
foreground: "notify"
```

### activeSession

```text
docklet.backgroundMessages.setActiveSession("session-a")
```

前台判断：

```text
模块在前台
foreground = notify
事件 session 等于 activeSession
→ 不通知，只交给 onEvent

事件 session 不等于 activeSession
→ 通知
```

后台时：

```text
activeSession 不生效
所有会话都提醒
```

## 8. 通知

第一版：

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

每个模块固定一条通知。

以后可扩展：

```text
notification: {
  title,
  body,
  sessionLabel
}
```

第一版默认不启用。

## 9. 去重与重连

```text
按 eventId 去重
重连后避免重复通知
短时间多条只更新同一条通知
setOnlyAlertOnce 避免刷屏
```

V3 自己用快照覆盖和 eventId 去重，壳不保证顺序和严格送达。

## 10. 持久化

```text
订阅配置落盘
进程重启恢复
设备重启恢复（BOOT_COMPLETED）
壳被系统杀死后尽量拉起
```

但仍需明确：

```text
系统省电策略可能阻止恢复
壳不保证永久在线
```

## 11. 非目标

```text
代发消息
自动回复
保存消息正文
未读数量
跨模块共享消息
跨应用读取消息
```

## 12. 需要 V3 最终确认

1. relay 能否提供全会话 SSE（A）？
2. 如果暂时不能，是否接受 B 作为 V3 兼容模式？
3. 事件是否统一包含 `session` 和 `eventId`？
4. 认证是否改用 headers，还是继续 query token？
5. 是否确认 `foreground: "notify"`？
6. 通知第一版是否只用“模块名 + 有新消息”？

## 13. 建议下一步

```text
V3 先确认 A/B 和认证方式
壳按 v0.2 设计多订阅前台服务
先支持 SSE + headers/query token
再接前台策略和 activeSession
最后补持久化、开机恢复、多模块
```

在 A/B 和认证确定之前，不建议直接改当前 `background.start` 的单订阅实现。
