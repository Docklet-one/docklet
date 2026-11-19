# Docklet 壳

## 编译

需要 **JDK 17** 与 **Android SDK**（compileSdk 34）。

```powershell
# 1. 让 Gradle 找到 Android SDK
#    在 shell/local.properties 里写：
sdk.dir=<android sdk 路径>

# 2. 指向 JDK 17
$env:JAVA_HOME = "<jdk17 路径>"

# 3. 构建
powershell -ExecutionPolicy Bypass -File tools/build.ps1
```

产物：

```text
dist/docklet-shell-0.1.0-debug.apk
```

## 安装

```powershell
adb install -r dist/docklet-shell-0.1.0-debug.apk
```

`apps/` 为空时编译得到空壳；模块通过在线目录或本地导入安装。
