# NEXT

> 给壳项目的下一轮实施清单。  
> 先读 `STATUS.md`，再按本文件优先级继续。  
> 2026-11-14 更新：P0/P1 与 UI v2 两阶段完成；V3 单槽基础（ModuleActivity + :m1）已落地并验证进程死亡。双槽/预加载仍未做。

## P0-1 CrashGuard 安全模式

```text
启动前持久化 launching=<id>
ready 后清除
ready 超时 / render gone / 进程死亡 → crashCount++
连续 2 次失败 → 不自动打开
模块列表标记“上次启动失败”
提供：重试 / 清数据 / 卸载
```

验收：

```text
故意让 demo 启动失败
重启壳不自动进 demo
能从列表清数据/卸载
```

## P0-2 网络能力

当前 `MIXED_CONTENT_NEVER_ALLOW`，模块 HTTPS origin 无法访问 HTTP 插件。

选择一种：

```text
A. 对声明 network 的模块允许 mixed content
B. 模块改用 http origin
C. 壳提供 network 代理
```

验收：

```text
示例模块声明 network
fetch http://<电脑>:8788/health 成功
最好能建立 SSE 或轮询
```

## P0-3 远程下载

已实现；本段保留原验收清单。远程 catalog 支持 url/sha256/size，包内 id/version 必须匹配，失败不破坏 current。

```text
catalog
按 URL 下载 .pkg
校验模块包
安装/更新
失败不破坏当前版本
```

## P1-1 ApiBridge 补齐

```text
files.read/write/list/delete
文件选择器
openExternal
clipboard
下载文件
```

## P1-2 BackgroundService

```text
前台服务/通知渠道
启动/停止
PendingIntent 打开模块
最小消息连接
```

## 暂不做

```text
V3 迁移
多 WebView 槽
预加载
复杂进程池
```

## 下一轮只做三件

```text
1. CrashGuard
2. network + mixed content
3. files / clipboard / openExternal
```

做完再评估远程下载和 BackgroundService。

## 2026-11-14 完成情况

```text
[x] P0-1 CrashGuard
    - files/crash_guard.json：launching、count、lastFailure、reason
    - ready 清除；ready timeout / render gone / 页面错误计数
    - 连续 2 次失败进入安全模式；卡片标记、重试/清数据/卸载面板
    - 实际用 tools/fixtures/crashy 触发 2 次失败并重启壳验证

[x] P0-2 network
    - 声明 network 的模块允许 mixed content，CSP 放行 http/https
    - App 允许 cleartext HTTP
    - netlab 演示 fetch + SSE
    - 10.0.2.2:8788/health 返回 200（现有 phone-relay）
    - 8789 测试服务器 /health + /events（SSE）均通过

[x] P1-1 files / clipboard / openExternal
    - data/<id>/files/ 相对路径、canonical 校验、5MB/50MB 配额
    - files.read/write/list/delete/exists
    - files.pick / share / download（OPEN_DOCUMENT / ACTION_SEND / MediaStore）
    - clipboard.read/write
    - openExternal（http/https/mailto/tel）
    - netlab 按钮 + CDP 实际调用均通过

[x] P0-3 远程下载 / catalog
    - 远程 catalog.json、相对包 URL、sha256 必填
    - 下载到 cache，边下边算 sha256；失败丢弃
    - 包内 id/version 必须匹配 catalog；原子安装
    - 更新 demo 1.0.1 成功；错误 sha 失败且 current 仍为 1.0.0
    - 调试默认 http://10.0.2.2:8789/catalog.json

[x] P1-2 BackgroundService
    - dataSync 前台服务 + 常驻通知
    - SSE 后台连接，JSON 消息 → 通知
    - 通知 PendingIntent 打开模块
    - background.start/stop/status
    - netlab 1.0.1 后台消息按钮；停止/卸载自动释放
    - 仍缺：消息持久化/离线队列、多连接、进程死亡自动恢复

[x] UI v2 阶段一
    - 设计稿 designs/hybrid（Warm Library）
    - 三 Tab 壳：模块 / 活动 / 设置
    - 首页 Spotlight + 列表 + 搜索
    - 活动页 / 设置页
    - （阶段一当时仍用旧 Sheet，已由阶段二替换）

[x] UI v2 阶段二
    - 全屏添加模块页：示例 / 远程 / 文件
    - 全屏模块详情页：状态 / 存储 / 危险区
    - 远程 Tab 仍打开独立远程面板（未完全内联）

[x] V3 双槽运行基础
    - ModuleActivity + :m1，ModuleActivity2 + :m2
    - ModuleSlotManager 轮流分配，只允许一个前台槽
    - WebView.setDataDirectorySuffix 解决多进程数据目录冲突
    - MainActivity 不再持有 WebView
    - 模块本地 StorageService / ModuleFiles
    - ready/cancelled/failure Activity result
    - kill :m2 后壳恢复，CrashGuard 计数
    - API 26–27 退回单槽
    - 详细交接：V3_HANDOFF.md

[ ] V3 预加载
[x] 壳进程死亡但槽进程存活：slot_session ready marker
[ ] 壳重启后恢复槽 Activity/UI 路由
[ ] 通知在模块 Activity 顶层时切换模块
[ ] 更新/卸载 stopAndWait
```

验收细节和截图见 `STATUS.md` 与 `docs/screenshots/`。
