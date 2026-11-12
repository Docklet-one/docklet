# Docklet 壳

Capacitor + React 的 Android 应用坞。只负责安装、运行、版本、返回栈、键盘/安全区和目录应用静态服务。

这个仓库**只包含壳**，不包含任何目录应用源码、应用 zip、官网或发布目录。

## 目录

```text
src/                 壳界面、应用列表、安装页、详情页
android/             原生 AppHost、安装、能力通道
public/              壳图标
index.html           壳入口
package.json         壳构建
build-apk.ps1        只打壳 APK
```

## 构建

```powershell
npm install
npx tsc --noEmit
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

输出：

```text
docklet-debug.apk
```

`build-apk.ps1` 只做：

1. `npm run build:app`
2. `npx cap sync android`
3. Gradle `assembleDebug`
4. 复制 APK 到根目录

它不会构建应用、不会打包应用 zip、不会生成 catalog、不会上传。

## 说明

- 目录应用通过 `app.json` 安装，由壳在运行时提供 `https://<id>.app.local`
- 应用源码和发布物不放这个仓库
- `android/docklet-release.jks` 和 `keystore.properties` 只放本地，不提交
- 壳 APK 发布前递增 `android/app/build.gradle` 的 `versionCode`
