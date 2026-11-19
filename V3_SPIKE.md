# V3 Spike 测试说明

> 第一个可运行 V3 模块，用来验证壳的迁移链路。  
> 不是完整 V3 UI。

## 包

```text
D:\docklet\dist\dsh-console-0.1.2.pkg
```

## 覆盖

```text
docklet.ready
SQLite：settings / messages
HTTP：/health
Snapshot：最近 3 轮
SSE：/events
会话：/sessions、/new、切换
发送：/target + /send
通知：notify.show
生命周期：save / pause / resume
从 SQLite 恢复设置和消息
```

## 测试步骤

```text
1. 壳里安装 dsh-console-0.1.2.pkg
2. 打开 DSH 控制台
3. 填电脑地址、令牌、可选会话 ID
4. 点连接
5. 看健康检查、最近消息、SSE 是否通
6. 发一条消息
7. 退出模块，再打开
8. 看地址/令牌/会话/消息是否恢复
9. 点 ＋ 发测试通知
10. 点通知，确认能回到模块
```

## 当前限制

```text
只验证链路，不是完整 V3
UI 只有连接、消息、输入和通知测试
没有 Markdown / 工具卡 / 审批 / 交付卡
消息去重只做基础 id 合并
后台通知仍依赖壳的 SSE 解析（壳侧待补）
通知还不会带 sessionId（壳侧待补）
```

## 通过标准

```text
能连上电脑
能收到 SSE
能发消息
重启后配置还在
点通知能回模块
```

通过后再把完整 V3 UI 搬进这个模块。
