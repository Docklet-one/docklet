# 壳侧通知功能补齐（V3 功能优先）

> **2026-11-19：本文件已作废。** 通知内容以 `BACKGROUND_MESSAGES_API.md` §7 为准
> （每模块一条、标题模块名、正文「有新消息」、不显示会话信息），因此下面要求的
> 「通知正文」不做。旧 `background.start / stop / status` 已从平台契约 1.1.0 删除。

> 优化先冻结。V3 侧聊天、会话、审批、下载、上传、重置、切会话已完成。  
> 现在功能缺口集中在壳的通知服务。

## P0-1 通知正文

现状：

```text
/sessions 轮询只比较 lastMessageAt
通知正文固定「有新消息」
```

需要：

```text
发现某个会话 lastMessageAt 变化后
GET /snapshot?session=<id>&turns=1
取最后一条 assistant 正文
截断到 120–200 字作为通知正文
失败时回退「有新消息」
```

去重键继续用：

```text
type|session|eventId
```

## P0-2 前台会话抑制

目标：

```text
用户正在 V3 里看会话 A
A 来消息不弹后台通知
B 来消息照常通知
```

建议接口：

```text
background.focus(sessionId)    // 模块前台且停在某会话
background.blur()              // 模块退后台
```

壳服务保存当前前台 module + session：

```text
通知前判断 eventSession == foregroundSession
且模块在前台 -> skip
```

或者由壳的 ModuleRuntimeHost 直接提供当前 session，不必新增桥方法。

## P0-3 通知模式

V3 设置已有：

```text
回合结束 turn
每条消息 message
```

V3 启动后台监听时可带：

```text
/sessions?token=...&mode=turn|message
```

壳服务：

```text
turn：只在 lastMessageAt 变化且该会话 running 结束/回合结束 时通知
message：本轮实现可保持每次都通知
```

做不到精确 turn 状态时，至少不要忽略 mode 参数。

## 验收

```text
1. 后台收到 A 的消息，通知正文是最后一条内容，不是「有新消息」
2. 正在看 A，A 来消息不弹；B 来消息弹
3. 关闭通知开关，后台任务通知消失
4. 点通知进对应会话
5. mode 参数能从 V3 传到壳
```

## 边界

- 不改 V3 UI 结构，只改壳通知服务和桥。
- 保持现有 `/sessions` 全量监听和 5 秒轮询。
- 通知正文获取失败不能阻塞通知。
