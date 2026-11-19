# 壳侧改动交接（V3 全屏 / 通知 / 预置）

> 背景：这台机器上 `D:\docklet` 不是 git 仓库，双方同时改 `shell/` 会直接覆盖。
> 结论：**壳由一边统一合并，另一边停止改 shell**。  
> 本文件记录最近这轮壳改动，供壳侧负责人合并/复核。

## 0. 当前 APK

- `D:\docklet\dist\docklet-shell-0.1.0-debug.apk`
- 构建时间：2026-11-14 17:56
- V3 模块：`dsh-console-beta` 0.3.1
- 以下改动都已编进这个 APK，但**没有 git diff**，需要按文件核对。

## 1. 模块 chrome 模式（全屏）

涉及：

```text
shell/core/src/main/java/com/docklet/platform/ModuleManifest.java
shell/core/src/main/java/com/docklet/platform/InstalledModule.java
shell/core/src/main/java/com/docklet/platform/ModuleManager.java
shell/app/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
```

行为：

```text
module.json 新增可选字段 chrome
缺省：standard
允许：standard | none
none：壳不画顶部工具栏，WebView 直接占满
```

关键点：

- `ModuleManifest` 解析 `chrome`，默认 `standard`，非法值报 `MANIFEST_INVALID`。
- `InstalledModule` 保存并暴露 `chrome()`，默认 `standard`。
- `ModuleManager.installed()` 把 `manifest.chrome()` 传给 `InstalledModule`。
- `ModuleRuntimeHost` 构造时：

```java
if (!"none".equals(module.chrome())) {
    toolbar = buildToolbar();
    root.addView(toolbar, ...);
}
```

- 普通模块仍保留壳工具栏，行为不变。

## 2. 真正 edge-to-edge

涉及：

```text
shell/app/src/main/java/com/docklet/shell/ModuleActivity.java
shell/app/src/main/java/com/docklet/shell/runtime/ModuleRuntimeHost.java
```

`ModuleActivity.setupWindow()`：

```text
statusBarColor = TRANSPARENT
navigationBarColor = TRANSPARENT
setDecorFitsSystemWindows(false)   // API 30+
setNavigationBarContrastEnforced(false) // API 29+
API < 30：LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION
```

`ModuleRuntimeHost`：

```java
root.setOnApplyWindowInsetsListener((view, insets) -> {
    applySystemInsets(insets);
    return insets;
});
```

`applySystemInsets`：

- API 30+ 取 `systemBars() | displayCutout()` 的 top/bottom；
- API 30+ 取 `ime()` 的 bottom；
- API < 30 用 `getSystemWindowInsetTop/Bottom()`；
- 标准模块：工具栏加 top padding，`webHost` 加 bottom padding；
- `chrome:none`：不 padding，改为向页面注入 safe-area。

## 3. safe-area / IME 注入

`ModuleRuntimeHost.injectSafeArea()`：

```text
physical px / density = CSS px
设置 html 变量：
  --safe-top
  --safe-bottom
  --v3-top
  --v3-bottom
  --v3-ime
再 dispatch：
  MessageEvent('message', { data: {
    type: 'docklet:insets',
    detail: { top, bottom, ime }
  }})
```

调用时机：

- `onBridgeReady()` 之后；
- insets 变化时（键盘弹起/收起）。

V3 已支持这些变量：

```text
--v3-top / --v3-bottom / --v3-ime
window.addEventListener('message', ... type === 'docklet:insets')
```

注意：**必须除以 density**。之前直接把物理像素写成 CSS px，顶部和底部会大一大块。

## 4. 后台通知：单会话 + 全会话

涉及：

```text
shell/app/src/main/java/com/docklet/shell/runtime/DockletBackgroundService.java
shell/app/src/main/java/com/docklet/shell/runtime/DockletBridge.java
```

### 4.1 `/sessions` 全量轮询

`runStream()` 入口：

```java
if (sourceUrl.contains("/sessions")) {
    runSessionsWatch();
    return;
}
```

`runSessionsWatch()`：

```text
每 5 秒 GET sourceUrl（/sessions?token=...）
第一次只建立 lastMessageAt 基线，不补历史通知
之后某个会话 lastMessageAt 变大 → dispatch 一条 message.assistant
notification 带 sessionId
点击通过 ModuleRouterActivity.EXTRA_SESSION_ID 进入对应会话
```

通知正文目前是 `有新消息`，不是完整消息内容。

### 4.2 `background.stop` 假成功修复

`DockletBridge.backgroundStop()` 删除了这个提前返回：

```java
if (running == null) {
    return success(running=false);
}
```

现在：

```text
running == null 也继续 stopService
只有 running 明确属于其他模块时才拒绝
```

这是之前“通知开关关了但服务还在”的根因之一。

## 5. 首次启动预置 V3（**已决定移除**）

> 现在壳已支持 URL 安装，V3 改为从链接安装。  
> 合并时请删除 `MainActivity.preinstallBundledV3()` 的调用和方法，  
> 不要让全新安装自动塞入 DSH Beta。  
> 预置用的 demo asset 可以保留，URL 安装不依赖它。

涉及：

```text
shell/app/src/main/java/com/docklet/shell/MainActivity.java
```

`onCreate` 里 `showHome()` 后调用：

```java
preinstallBundledV3();
```

行为：

```text
首次启动：从 assets 安装 dsh-console-beta
成功后写 v3_preinstalled=true
已安装：只写标记，不重复装
用户主动卸载：标记还在，下次启动不会硬装回来
```

依赖：

```text
apps/dsh-console-beta/module.json
apps/dsh-console-beta/index.html
apps/dsh-console-beta/v3.js
apps/dsh-console-beta/avatars/*
```

由现有 `copyDemosToDist` / demo asset 构建流程打进 APK。

## 6. 测试改动

```text
shell/core/src/test/java/com/docklet/platform/DemoPackagesTest.java
```

加入了：

```text
dsh-console-0.1.2.pkg
dsh-console-beta-0.2.x / 0.3.x
```

只影响测试，不影响运行时。

## 7. 合并建议

```text
1. 先合并第 1、2、3 节（全屏 + insets）
2. 再合并第 4 节（通知停止修复 + 全会话）
3. 最后合并第 5 节（预置）
4. 合并后重新打 APK，用 V3 Beta 验证：
   - 状态栏下无壳留白
   - 键盘弹起输入框上移
   - 任意会话来消息可通知
   - 点通知进对应会话
   - 关闭通知后后台任务消失
   - 全新安装自动出现 DSH 控制台 Beta
```

## 8. 边界

- 不要同时在两边改 `shell/` 同一文件；没有 git 冲突提示。
- 本文件只记录我这边改过的壳行为，具体代码以当前工作区为准。
- V3 包侧（`apps/dsh-console-beta`、`dist/*.pkg`、`V3_*.md`）仍由我继续维护。

## 9. 可核对快照（2026-11-14 18:10）

除了本文件，还留了一份逐文件快照和 SHA256：

```text
D:\docklet\shell-handoff\
  README.md
  SHA256SUMS.txt
  shell/...  （9 个当前工作区文件副本）
```

使用方式：

```text
先比对 SHA256SUMS.txt
同 hash 不用动
不同 hash 逐文件 diff，不要整目录覆盖
```

注意：快照是**当前合并后的工作区状态**，不是只含单方改动的干净补丁；URL 安装、download、session/SSE 等另一侧改动也在里面。
