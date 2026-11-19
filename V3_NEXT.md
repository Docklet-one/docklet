# V3 下一步完善清单

> 基于 `V3_HANDOFF.md`。双槽基础已通，迁移 V3 前先补下面几项。

## P0-1 通知点击切换模块 ✅ 已完成

实现：

```text
PendingIntent -> ModuleRouterActivity（无界面）
当前模块顶部：
  Router 读 active_module
  -> 广播 SWITCH_TO
  -> 当前 ModuleActivity closeRuntime（先保存，最多 500ms）
  -> Activity result 带 NEXT_MODULE
  -> MainActivity 释放槽位并打开目标模块
无活动模块：
  Router 启动 MainActivity 并带 OPEN_MODULE
点击当前模块自己的通知：
  Router 直接结束，不重开
```

验收：

```text
打开 quiz
启动 netlab 后台 -> 通知
点通知
quiz 保存并关闭
netlab 在 :m1 打开
active_module=netlab，CrashGuard 不增加
```

已验证。

## P0-2 更新/卸载 stopAndWait ✅ 已完成

实现：

```text
MainActivity.stopActiveModule(after)
  -> 广播 STOP_REQUEST
  -> ModuleActivity.closeRuntime（保存）
  -> Activity result
  -> 释放槽位
  -> 执行 after（清除数据 / 卸载 / 更新）
4 秒未返回则取消操作
```

`performClearData` / `performUninstall` / 示例安装 / 文件包安装已接入 stopAndWait；直接发送 STOP_REQUEST 已验证：

```text
模块运行中 -> STOP_REQUEST
模块保存并关闭
active_module 清空
CrashGuard 不增加
```

更新路径保留同一个 `stopActiveModule(Runnable)` 入口。

## P1-1 预加载 ✅ 进程级

实现范围：

```text
设置页可开关，默认关闭
只预热 :m2 进程与 WebView 数据目录
不创建第二个 WebView
可用内存 < 256MB 时不预热
onTrimMemory 低内存时停止预热
预热失败只回退，不影响正常打开
```

明确不是“内容预加载”：不会把模块 UI 提前加载到 WebView。  
这样避免为了预加载牺牲前台稳定性。

验证：

```text
开启预加载 -> :m2 进程出现
打开 m1 模块 -> 正常
再打开 m2 模块 -> WebView 正常，CrashGuard 不增加
```

## P1-2 双槽内存/回收策略（部分）

已做：

```text
预加载内存阈值 256MB
onTrimMemory(RUNNING_LOW) 停止预热
不创建第二个后台 WebView，避免两套 WebView 常驻
```

已做：

```text
RUNNING_CRITICAL -> killBackgroundProcesses 回收 :m2
```

未做：真正的后台 WebView 内容回收/恢复策略。当前仍是单前台 WebView。

## P1-3 instrumentation / 进程测试

已手工验证：

```text
[x] kill :m2 -> 壳恢复，CrashGuard 计数
[x] kill 壳进程（槽仍活）-> slot_session 恢复，不误计
[x] 槽复用（m1/m2 轮流）
[x] 通知切换 + STOP_REQUEST
[x] RUNNING_CRITICAL 回收 m2
[~] 低内存 Activity 重建：已加进程存活重试 + 结果单次保护，真实重建未跑
[ ] 数据不丢自动化
[ ] instrumentation
```

## P1-4 真机验证

```text
OEM WebView
进程死亡行为
通知权限
文件选择
```

## P2-1 壳重启后的槽恢复

`slot_session` 已能避免误记失败。  
还需要明确：

```text
壳重启后是否自动回到模块
还是回首页让用户重新打开
```

建议先回首页，不自动恢复模块，避免崩溃循环。

## P2-2 文档

更新 `STATUS.md` / `V3_HANDOFF.md`，把：

```text
双槽完成
未完成的通知队列
stopAndWait
预加载
测试范围
```

写清楚。

## 建议顺序

```text
[x] 1. 通知切换队列
[x] 2. stopAndWait
[~] 3. 进程测试（kill m2 / kill 壳已做，低内存重建待做）
[ ] 4. 真机
[x] 5. 预加载（进程级，默认关闭）
```
