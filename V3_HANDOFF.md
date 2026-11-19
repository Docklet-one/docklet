# V3 迁移交接

> 状态：**双槽 V3 运行基础已落地（进程级预加载，默认关闭）**。  
> 结论：` :m1` / `:m2` 两个槽位和保守预加载都已跑通；可以迁移双槽 V3。内容级 WebView 预热仍是后续项。  
> 更新时间：2026-11-14 15:04

## 1. 已经完成的基础

### 进程边界

- `ModuleActivity` 运行在 `:m1`
- `ModuleActivity2` 运行在 `:m2`
- `ModuleSlotManager` 轮流分配槽位；同一时间只允许一个前台槽
- `MainActivity` 只做模块库、安装、远程、CrashGuard、调度
- **壳进程不再创建 WebView**
- 每个模块 Activity 自带一个 WebView

Manifest：

```xml
<activity android:name=".ModuleActivity" android:process=":m1" ... />
<activity android:name=".ModuleActivity2" android:process=":m2" ... />
```

### WebView 多进程数据目录

两个进程同时存在 WebView 时，必须给每个进程设置独立数据目录，否则 Chromium 会报：

```text
Using WebView from more than one process at once with the same data directory is not supported
```

`ModuleActivity.configureWebViewProcess()` 在创建 WebView 前调用：

```java
WebView.setDataDirectorySuffix("docklet-m1" / "docklet-m2");
```

该 API 从 Android 9（API 28）可用；API 26–27 上 `ModuleSlotManager` 只使用 `:m1`。

### 模块本地服务

`ModuleActivity` 自己初始化：

```text
ModuleManager（只读模块目录）
StorageService（data/<id>/app.db）
ModuleFiles（data/<id>/files）
ModuleRuntimeHost（WebView + Bridge）
```

因此 SQLite、模块文件、文件选择/分享现在都在模块进程内完成，不再依赖壳 Activity 里的 runtime。

### 结果契约

`MainActivity` 用 `startActivityForResult` 启动 `ModuleActivity`，结果：

```text
EXTRA_READY=true      正常 ready 后关闭
EXTRA_CANCELLED=true  ready 前用户主动关闭
EXTRA_REASON=...      启动失败 / render gone / 进程退出
```

壳侧：

```text
ready      -> crashGuard.onReady
cancelled  -> crashGuard.cancelLaunch
failure    -> crashGuard.recordFailure
```

另外 `ModuleActivity` ready 时发送同应用广播 `com.docklet.shell.MODULE_READY`，壳在 `onResume` 前就能清除 launching。

### 进程被杀 / 双槽

已验证：

```text
打开模块 -> :m1 进程存在
关闭后再开 -> :m2 进程存在，WebView 正常加载
kill -9 :m2
壳进程仍在，回到 MainActivity
CrashGuard count=1，reason=模块进程已退出
```

`MainActivity` 有 `processDeathCheck`，只有在模块 Activity 不在顶层时才记失败，避免“模块还开着被误判”。

### 后台状态跨进程

`DockletBackgroundService` 现在把当前模块 id 同步到：

```text
SharedPreferences: docklet_background / module_id
```

`DockletBridge.background.status`、首页/设置页读取带 Context 的 `runningModuleId(context)`，模块进程也能看到壳进程启动的后台服务。

### 通知点击切换模块

`ModuleRouterActivity` 是无界面路由器：

```text
通知 PendingIntent -> ModuleRouterActivity
active_module == target  -> 直接结束
active_module 非空       -> 广播 SWITCH_TO
                           -> 当前 ModuleActivity 保存/关闭
                           -> result 带 NEXT_MODULE
                           -> MainActivity 打开目标模块
active_module 为空       -> 启动 MainActivity + OPEN_MODULE
```

`active_module` 记录在 shell 的 `docklet_shell` SharedPreferences。

### sessionId / SSE / 去重

- 通知 PendingIntent 带 `moduleId` + `sessionId`
- `ModuleActivity` 新开时把 session 通过 `shell.info().initial.sessionId` 交给模块
- 同一模块收到新 session 时发送 `docklet:session` 事件，不重开 WebView
- 不同模块切换时 result 带 `NEXT_MODULE` + `NEXT_SESSION`
- BackgroundService 识别 `event:` / `data:`，解析 V3 envelope
- 只通知 `message.assistant` / `turn.end` 等事件
- 通知 tag/id 由 `module + session + eventId` 组成；seen events 持久化，重连不重复

### stopAndWait

`MainActivity.stopActiveModule(after)`：

```text
广播 STOP_REQUEST
-> ModuleActivity.closeRuntime（先保存）
-> Activity result
-> 释放槽位
-> 执行 after
4 秒超时取消 after
```

`performClearData` / `performUninstall` / 示例安装 / 文件包安装已接入。更新路径统一走同一个入口。

### 预加载（进程级）

设置里的「预加载槽位」默认关闭。开启后 shell 启动 `SlotWarmupService`（`:m2`）：

```text
只设置 WebView 数据目录 + 预热 :m2 进程
不创建第二个 WebView
可用内存 < 256MB 跳过
onTrimMemory 低内存停止预热
```

这是保守的进程预热，不是内容预加载；失败不影响正常打开。  
真正的后台 WebView 预热/回收仍属于后续内存策略。

### 壳死亡 ready 标记

`ModuleActivity` ready 后写入：

```text
files/slot_session  -> moduleId / slot / timestamp
```

壳重启时如果 `launching` 与 marker 匹配，且 `:m1`/`:m2` 进程仍存活，则直接清除 launching，不误记失败；进程已死才交给 CrashGuard 计数。正常关闭会清掉 marker。

已验证：

```text
模块 ready -> slot_session=netlab/:m1
kill 壳进程 -> :m1 仍存活
重启壳 -> slot_session 清空，CrashGuard 不增加
```

## 2. 完整 V3 的可选项 / 质量项

```text
[x] SlotManager
[x] :m2 备用槽
[x] 壳进程死亡但模块进程仍活着：ready marker 兜底
[x] 模块运行时收到通知 -> 切换模块
[x] 停止槽并等待 result（stopAndWait 入口）
[x] 进程级预加载（默认关闭，不创建第二 WebView）
[~] 后台 WebView 预热 / 回收：RUNNING_CRITICAL 已回收槽进程，内容级策略未做
[ ] instrumentation / 真机进程死亡测试
```

## 3. 关键剩余风险

### 壳进程死亡

已用 `slot_session` ready marker 兜底：壳重启时先看槽进程是否仍存活，避免把活着的模块误记为启动失败。  
剩余的是“壳重启后如何恢复槽 Activity/UI 路由”，不是 CrashGuard 计数。

### 通知切换

已由 `ModuleRouterActivity` + `SWITCH_TO` + `EXTRA_NEXT_MODULE` 打通；剩余是低内存/进程已死时的兜底体验。

### 更新/卸载停槽

`stopActiveModule(after)` 已提供；远程更新应在真正替换包之前调用它，不要绕过入口直接 `manager.install`。

### 测试

- JVM：53 个通过
- 模拟器：`:m1` / `:m2` 双槽启动、正常关闭、`kill -9 :m2`、壳进程被杀后的 ready marker 恢复、通知切换（quiz → netlab）、STOP_REQUEST 关闭、预加载 m2 后 WebView 正常、RUNNING_CRITICAL 回收 m2、后台跨进程状态已手工验证
- V3 mock：`shell.info().initial.sessionId=sess-demo`；SSE notification tag `docklet-event-netlab-sess-demo-m-3`；小文件 `files.read` base64 + `/upload` 成功
- Activity 结果增加单次保护；壳侧进程死亡检测在槽进程仍存活时最多重试 3 次，避免低内存重建被误判
- 没有 instrumentation / 低内存重建 / 真机 OEM WebView 覆盖

## 4. 建议的 V3 继续顺序

```text
[x] 1. ModuleSlotManager（activeId / release）
[x] 2. launch/result/processDeath 走槽位状态
[x] 3. :m2 双槽运行
[x] 4. 壳死亡 ready marker
[x] 5. 通知切换模块
[x] 6. stopAndWait 入口
[x] 7. 进程级预加载（默认关闭）
[~] 8. 后台 WebView 预热 / 回收策略（进程回收已做）
[ ] 9. instrumentation：
       - 低内存重建
       - 槽复用
       - 数据不丢
[ ] 10. 真机验证
```

## 5. 关键文件

```text
shell/app/src/main/java/com/docklet/shell/ModuleActivity.java
shell/app/src/main/java/com/docklet/shell/ModuleActivity2.java
shell/app/src/main/java/com/docklet/shell/ModuleRouterActivity.java
shell/app/src/main/java/com/docklet/shell/MainActivity.java
shell/app/src/main/java/com/docklet/shell/runtime/ModuleSlotManager.java
shell/app/src/main/java/com/docklet/shell/runtime/WebViewProcess.java
shell/app/src/main/java/com/docklet/shell/runtime/SlotWarmupService.java
shell/app/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBridge.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBackgroundService.java
shell/app/src/main/AndroidManifest.xml
```

## 6. 验收命令

```powershell
# 模块进程
adb shell ps -A | Select-String "docklet"

# 当前前台
adb shell dumpsys activity activities | Select-String "topResumedActivity"

# 杀掉当前模块进程（需要 root/emulator）
$m1 = (adb shell pidof com.docklet.shell.debug:m1).Trim()
adb shell kill -9 $m1

# CrashGuard 状态
adb shell "run-as com.docklet.shell.debug cat files/crash_guard.json"
```

预期：壳进程仍在，`topResumedActivity` 回到 `MainActivity`，CrashGuard 记录一次失败，模块列表显示“上次失败/安全模式”。
