# Docklet 壳

Android 壳：负责模块的安装、运行、隔离、存储和恢复。模块是独立的 HTML 包，
从在线目录安装，壳本身不预置任何模块。

## 目录

```text
shell/      Android 工程（Gradle）：app 是壳本身，core 是模块平台
apps/       模块源码占位目录（当前为空，由模块侧维护）
tools/      构建与备份脚本
module-platform.md   模块与壳的契约（返回、生命周期、存储、能力桥）
```

## 编译

需要 JDK 17 与 Android SDK（compileSdk 34）。

```powershell
$env:JAVA_HOME   = "<jdk17 路径>"
$env:ANDROID_HOME = "<android sdk 路径>"

# 先写 shell/local.properties
sdk.dir=<android sdk 路径>

powershell -ExecutionPolicy Bypass -File tools/build.ps1
```

产物：

```text
dist/docklet-shell-0.1.0-debug.apk
```

`apps/` 为空时编译得到“空壳”，装到手机后从在线目录安装模块。

## 安装

```powershell
adb install -r dist/docklet-shell-0.1.0-debug.apk
```

## 与模块的关系

- 模块源码在 `apps/<id>/`，由模块侧负责，壳不修改
- 壳扫描 `apps/*/module.json` 把模块打进 APK（仓库里该目录为空）
- 运行时模块优先来自 `https://apps.docklet.cn/catalog.json`
