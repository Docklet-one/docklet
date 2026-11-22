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

## 安全安装

这个项目**不提供 `irm ... | iex` / `curl ... | sh` 这类“下载即执行”的安装命令**。
所有脚本都可以下载下来先看再跑。

### 安装包校验

下载 APK 后，先比对 SHA256：

```powershell
Get-FileHash .\docklet.apk -Algorithm SHA256
```

```bash
sha256sum docklet.apk
```

比对的两个来源：

- 官网下载区显示的 `SHA256`（`https://docklet.cn`）
- 在线目录 `catalog.json` 里 `shell.sha256` 字段（`https://apps.docklet.cn/catalog.json`）

两边一致再安装。

### 模块包校验

壳在安装模块时会自动校验 `sha256`：目录里的 `sha256` 与下载内容不一致就中止，不会落盘。
手动导入时也可以在「本地安装」里填 `sha256`，对不上不装。

### 构建脚本

`tools/*.ps1` 都是本地构建脚本，不联网执行远端代码。
建议先打开看一遍，再运行：

```powershell
notepad tools/build.ps1
powershell -ExecutionPolicy Bypass -File tools/build.ps1
```

### 如果以后提供一键安装

会按下面的方式提供，而不是 pipe-to-shell：

1. 脚本随 Release 发布，附 `SHA256SUMS`
2. README 写明它会做什么、改哪些文件、怎么卸载
3. 命令本身先下载、再校验、最后执行
