# V3 对壳方回复的确认

> 读者：壳 / relay 对话
> 对应：`V3_BACKGROUND_MESSAGES_SHELL_RESPONSE.md`
> 日期：2026-11-15

## 结论

```text
1. 多会话：V3 需要 A（relay 全会话 SSE）；短期没有 A，接受 B 作为 V3 兼容模式
2. 事件：必须统一带 eventId 和 session；否则 activeSession 过滤做不了
3. 认证：优先 Authorization header，保留 query token 兜底
4. foreground：确认 notify
5. 通知内容：第一版接受“模块名 + 有新消息”
6. 壳按 v0.2 落地后，V3 再做 feature-detect 接入；在那之前 V3 不动 background.start 的用法
```

## 逐条回答（对应壳方 §12）

### 1. relay 能否提供全会话 SSE（A）？

**需要，但这是 relay 的改动，不在 V3 仓库。**

V3 侧的结构支持 A：V3 不依赖 `/events?session=` 的会话参数，只要事件里带 `session`，
`activeSession` 就能按事件过滤。需要 relay 提供（建议形态）：

```text
GET /events?all=1&token=...        （或 /events/all）
每条 SSE data 带：
{
  "type": "message.assistant",
  "eventId": "...",
  "session": "session-xxx",
  "sequence": 123,
  "emittedAt": 1760000000000,
  "payload": { ... }
}
```

要求：

- 每条事件必须有 `session`（现在没有）
- 必须有 `eventId`（现在有）
- 心跳单独事件类型，不算新消息
- 一条连接覆盖所有活跃会话；归档会话不推

如果 relay 短期做不了，见第 2 条。

### 2. A 暂时不能时，接受 B 吗？

**接受。** V3 现在就是用 B 的形态工作（`/sessions?mode=message`，按 `lastAssistantSeq`
变化判断）。壳里已有的 `runSessionsWatch()` 可以保留，但请按你们说的标记为
“V3 兼容模式”，不要作为通用接口。

B 的已知代价 V3 认了：5 秒延迟、轮询耗电、依赖 `/sessions` 返回结构。

### 3. 事件是否统一包含 session 和 eventId？

**必须包含。** 现状：

```text
eventId：有
session：没有（由订阅 URL 的 session 参数隐含）
```

A 方案下 `session` 必须进事件；否则 V3 无法区分“当前会话的新消息”和“其他会话的新消息”，
`activeSession` 过滤会失效，前台也会误提醒。

### 4. 认证用 headers 还是 query token？

**优先 Authorization header，保留 query token 兜底。**

```text
backgroundMessages.subscribe 用 headers: { Authorization: "Bearer ..." }
endpoint 不带 token（避免 token 进日志/被持久化到不安全位置）
如果壳某条路径只能 query，则退回 endpoint + ?token=
```

V3 页面内的 EventSource（前台实时流）仍然只能用 query token，这部分和后台服务分开看。

安全要求 V3 认可：token 不进日志、不跨模块、落盘单独保护。

### 5. 是否确认 foreground: "notify"？

**确认。** V3 是多会话，当前会话靠 `activeSession` 抑制；其他会话在前台也要提醒。

### 6. 通知第一版只用“模块名 + 有新消息”？

**确认接受。** 我们之前提过通知正文，v0.1 先不做；以后可以按壳方 §8 的
`notification: { title, body, sessionLabel }` 扩展，V3 再按需传。

## V3 侧实现计划（壳 v0.2 落地后）

```text
1. feature-detect：window.docklet.backgroundMessages 存在则用新接口，否则保持 background.start
2. subscribe 参数：
   endpoint（不带 token）、headers: Authorization、transport: "sse"、
   eventTypes: ["message.assistant", "turn.end"]、foreground: "notify"、
   sessionField: "session"、dedupeBy: "eventId"、persist: true
3. 进出会话：setActiveSession(id) / setActiveSession(null)
4. 页面销毁：V3 打开时先画缓存再拉快照（已有），不依赖后台页面存活
5. onStatus 写进设置页的“后台提醒状态”（connecting/online/offline/error + lastError）
```

V3 不要求壳保证送达、顺序、严格去重；重复事件 V3 用 `eventId` 去重。

## V3 对 A 的额外请求（relay 侧）

```text
1. 全会话事件流路径 + 认证方式定下来后写进文档
2. 事件统一带 session / eventId / type
3. message.assistant 与 turn.end 两种 type 都有（V3 的 eventTypes 要用）
4. 心跳事件单独 type（例如 heartbeat），壳过滤时不算新消息
5. 连接建立后先发最近一条游标（可选），壳不需要，V3 打开时会自己 resnapshot
```

## 一个可选扩展（不阻塞）

通知点击目前只能“打开模块”。如果以后能带会话信息：

```text
intent extras: { session: "session-xxx" }
```

V3 打开后可以直接切到那条会话。第一版不做也行，V3 会打开上次的会话并拉快照。

## 下一步

```text
壳：按 v0.2 设计多订阅前台服务 -> 先 SSE + headers/query token
    -> 前台策略 + activeSession -> 持久化/开机恢复/多模块
relay：决定 A 是否做、事件补 session
V3：等壳 v0.2 可用后，一个版本内接入 feature-detect + setActiveSession
```

## 附：B 模式的比较字段与事件语义（2026-11-15 补充）

### B 模式现在比的是 lastMessageAt，建议改 lastAssistantSeq

壳现状（`DockletBackgroundService.runSessionsWatch()`）：

```text
每 5 秒 GET /sessions
每个会话取 lastMessageAt（relay 里 = item.at = session.updatedAt）
首次 prime，之后 at > 上次记录就发一条“有新消息”
```

问题：

```text
updatedAt 会被工具调用、流式 delta 推动，不只是“助手回复完成”
毫秒时间戳：同一秒两条消息可能相等（> 比较会漏）
壳无法区分“助手消息”与“其他活动”，只能一律通知
现在壳 dispatch 时伪造 message.assistant，正文写死“有新消息”，语义不对
```

V3 建议：

```text
/sessions 每个会话项增加 lastAssistantSeq（relay 的 /health 已对 target 算过）
B 模式比较 lastAssistantSeq，严格大于才通知
并且只在 running == false（本轮结束）时通知，避免工具回合中途连响
```

如果短期不加字段，退而求其次：用 lastSeq（事件游标）+ running == false，
turn 结束时游标前进，一轮只提醒一次。

另外：URL 里的 `mode=message|turn` 目前 B 模式完全没读（relay /sessions 也没处理），
是遗留参数，请实现或删掉。

### message.assistant 是完整消息，不是增量

relay 映射（`phone-relay/index.js` 的 `messageFromEvent`）：

```text
assistant/message       -> message.assistant      携带 data.message，完整文本
assistant/message.delta -> message.assistant.delta 增量
```

结论：

```text
message.assistant 可以当“新消息”通知
message.assistant.delta 绝对不能触发通知
但工具回合可能产生多条 assistant 消息，按 message.assistant 通知会一轮多响
```

V3 建议的通知触发：

```text
默认：eventTypes = ["turn.end"]，本轮结束提醒一次
      aborted / error 的 turn.end 不提醒（或单独配置）
要更早提醒：eventTypes = ["message.assistant", "turn.end"]
      壳对同一会话做合并（如 30 秒内只响一次），V3 按 eventId 去重
```

V3 默认选 `["turn.end"]`：一轮一次，语义最稳；代价是长回合提醒来得晚。

### 对 relay 的两个具体请求

```text
1. /sessions 每项加 lastAssistantSeq（与 /health 字段一致）
2. 明确 message.assistant 是完整消息；通知侧默认用 turn.end，不要用 delta
```

如果 relay 短期不能加 lastAssistantSeq，请明确 B 模式改用
`lastSeq + running == false`，并把 `mode` 参数的处理写进文档。

## 附二：字段确认后的具体算法

> 前提：`/sessions` 每项带 `lastAssistantSeq`、`lastSeq`、`running`；
> `turn.end` 带状态（completed / error / aborted）。

### A 模式（全会话 SSE）

```text
subscribe 参数：
  eventTypes: ["turn.end"]
  sessionField: "session"
  dedupeBy: "eventId"
  foreground: "notify"

壳收到 turn.end 时：
  读 payload 状态：status（新）或 reason.kind（现有 relay 字段）
  completed -> 通知
  aborted / error -> 不通知（用户主动停止或本轮失败，不需要“有新消息”提醒）
  有 session -> 前台时与 activeSession 比较，相同则只回调不通知
  没有 session -> 不做会话过滤（按文档约定）
```

### B 模式（轮询 /sessions）

```text
每 5 秒 GET /sessions，维护 prevSeq[session]

对每个会话：
  seq = lastAssistantSeq（为空则退回 lastSeq）
  if seq > prevSeq[session] and running == false:
      通知（同一轮只提醒一次；一次轮询多个会话命中时合并成一条通知）
  prevSeq[session] = seq

第一次轮询只 prime，不通知
running == true 的会话先记下 seq，等 running 变 false 后再判断
```

为什么要 `running == false`：工具回合可能在 assistant 消息之后继续调用工具，
提前通知会让人点进去看到“还没答完”。

如果 relay 同时能给出每会话的“本轮状态”（completed / error / aborted），
B 模式可以进一步过滤 error / aborted，避免失败轮也提醒；没有这个字段就先接受。

### turn.end 字段兼容

现有 relay 的 turn.end payload 是：

```json
{ "turn": 6, "reason": { "kind": "error" } }
```

如果新增 `status` 字段，请**保留 `reason.kind`**，壳的过滤写成：

```text
const status = payload.status || payload.reason?.kind || "";
notify = status === "completed";
```

这样旧 relay、新 relay 都能工作，V3 也不受影响（V3 现在读的就是 `reason.kind`）。

### V3 侧最终参数

```text
foreground: "notify"
eventTypes: ["turn.end"]          // 默认；要更早提醒再加 message.assistant
sessionField: "session"
dedupeBy: "eventId"
persist: true
```

V3 不依赖通知正文；通知只是“有新消息”的铃，打开模块后自己 resnapshot。

### 实测：turn.end 的 reason.kind 取值

本地 relay 全量 17 个会话、506 条 turn.end 的统计：

```text
completed   445
aborted      34
error        26
interrupted   1
payload 形状：100% 都有 turn + reason.kind，没有缺失 reason 的样本
```

结论：

```text
当前 DSH/relay：正常完成的 turn.end 一定带 reason.kind = "completed"
但取值不止 completed/error/aborted，还有 interrupted
relay 自己的代码也是防御式写法：Boolean(reason && reason.kind === 'error')
说明 reason 理论上可能缺失
```

通知过滤建议：

```text
const kind = payload.status || payload.reason?.kind || "";
notify = kind === "" || kind === "completed";   // 缺失时按正常完成处理
aborted / interrupted / error -> 不通知
```

如果以后 DSH 新增状态值，壳不认识就按“非 completed”处理，避免把失败轮当新消息。
