# Docklet 模块平台方案

> 状态：阶段 0–3 + CrashGuard/network/远程 catalog 下载/files/clipboard/openExternal/BackgroundService 已实施（2026-11-14）；实际进度见 `STATUS.md`，本文件保留方案原文  
> 目标：壳 + 自研可下载 HTML 模块，按需加载，模块独立运行、独立存储、崩溃可恢复  
> 不追求：微信小程序级 DSL / 双线程 / 原生渲染 / 第三方开放平台

## 0. 当前实施范围

```text
现在只做壳
  ModuleManager
  RuntimeHost
  StorageService
  ApiBridge
  CrashGuard
  BackgroundService

验收用一个最小示例模块
  demo 模块
  验证安装、启动、保存、恢复、通知、卸载
```


## 1. 产品定位

```text
封闭模块平台
  所有模块由我们开发和发布
  不做任意第三方上传

按需下载
  启动只加载模块清单
  用户打开模块时才下载/解包

模块独立
  独立 WebView
  独立进程槽
  独立 origin
  独立数据目录

数据优先
  关键数据进原生 SQLite
  关键数据不依赖页面临时存储
```

明确边界：

```text
能保证：
  模块崩溃不拖死壳
  清 WebView 缓存不丢数据
  更新不丢数据
  卸载只删自己的模块和数据
  跳转前可保存当前模块状态

不能保证：
  微信级模板 DSL
  逻辑/视图双线程
  原生组件
  同 UID 下的恶意代码隔离
```

## 2. 总体架构

```text
Shell 主进程
  ModuleManager     清单、下载、解包、版本、回滚
  StorageService    每模块 SQLite，路径隔离
  ApiBridge         模块能力白名单
  CrashGuard        ready 超时、崩溃计数、安全模式
  BackgroundService 通知/watch/跳转
  ModuleRegistry    已安装模块、当前版本、槽位分配

模块进程
  :m1  当前前台模块
  :m2  预加载/备用（可选）
  :m3  临时大模块（可选）

每个模块进程
  ModuleActivity
  一个 WebView
  独立 origin：https://<id>.module.local
```

默认策略：

```text
同时只有一个前台 WebView
主模块不常驻 WebView
后台消息由 Shell BackgroundService 统一负责
离开模块：保存状态 → 销毁 WebView
返回模块：读 SQLite → 重建 WebView → restoreState
```

进程槽只是“当前前台”的容器，不是“每个模块固定一个进程”。

## 3. 模块包

目录：

```text
<id>/
  module.json
  index.html
  app.js
  app.css
  assets/
```

`module.json`：

```json
{
  "id": "dsh-console",
  "name": "DSH 控制台",
  "version": "1.0.0",
  "entry": "index.html",
  "minShell": "1.0.4",
  "capabilities": ["network", "storage", "clipboard", "background"],
  "data": "dsh-console"
}
```

规则：

```text
id 全局唯一，作为 origin 和数据目录名
version 单调递增
entry 必须是包内相对路径
capabilities 决定桥放行哪些能力
data 决定 SQLite/文件命名空间
```

第一版不做签名/审核，但保留：

```text
包结构校验
entry 存在校验
可选的 sha256 校验
版本比较
```

## 4. 安装、更新、卸载

安装：

```text
下载模块包到 cache/<id>-<version>.pkg
→ 校验包结构、module.json、entry
→ 解包到 modules/<id>/<version>.new
→ 原子改名为 <version>
→ 更新 current 指针
→ 保留上一版
```

更新：

```text
新版本启动成功 → 提交 current
启动失败/超时 → current 回退上一版
数据目录 data/<id> 不动
```

卸载：

```text
停止该模块进程
释放槽
删除 modules/<id>
二次确认后删除 data/<id>
```

失败处理：

```text
current 永远指向一个可启动版本
失败原因写 crash log
不自动删除失败版本，便于排查
```

## 5. 运行时与进程槽

### 5.1 槽位

建议预声明：

```text
:m1  前台模块
:m2  预加载/备用
:m3  可选
```

默认只使用 `:m1`；需要快速切换时启用 `:m2`。

规则：

```text
一个槽同一时间只跑一个模块
模块退出 → 销毁 WebView，释放槽
槽不足 → 队列，不共享
槽复用前清理该槽 WebView 数据
```

### 5.2 加载

```text
分配槽
→ 创建 ModuleActivity
→ 创建 WebView
→ 加载 https://<id>.module.local
→ 等待模块 ready
→ 超时/崩溃 → 回收槽
```

### 5.3 origin

```text
https://<id>.module.local
```

由本地服务拦截，映射到：

```text
modules/<id>/<current>/
```

模块只能访问自己的静态资源。

## 6. 模块生命周期契约

每个模块必须实现：

```text
ready
onPause
onResume
saveState
restoreState
```

宿主发给模块：

```json
{ "type": "lifecycle", "action": "pause|resume|save", "requestId": "..." }
```

模块回复：

```json
{ "type": "lifecycle.result", "requestId": "...", "ok": true }
```

规则：

```text
暂停：模块立即暂停计时/动画，停止写操作
保存：模块把可恢复状态写入自己的 SQLite
恢复：模块从 SQLite 读取并还原页面
```

宿主不能自动冻结 JS 堆；状态必须由模块显式序列化。

### 6.1 返回

系统返回手势（或返回键）**始终由 Activity 接管**。模块按页面注册同步处理器：

```js
const off = docklet.onBack(() => {
  if (panelOpen) {
    closePanel();
    return 'handled';      // 这一次返回被我用了，留在模块里
  }
  if (atRoot) {
    return 'exit';         // 到头了，让宿主关闭模块、回到坞内
  }
  gotoParent();            // 模块内部的"上一步"
  return 'handled';
});
```

无论哪种写法，返回值都必须是这两个字符串之一；布尔返回值不再被识别。

规则：

```text
宿主先关闭自己盖的层（弹窗 → 覆盖页）
再询问模块注册的处理器
  返回 'handled' → 本次返回已消费，留在模块
  返回 'exit'    → 宿主退出模块（或 canGoBack() 时回退网页历史）
  没有处理器     → canGoBack() ? goBack() : 关闭模块
多个处理器按注册顺序反向执行
处理器必须同步返回，不要在里面做异步等待
```

兜底（防止模块把用户关住）：

```text
默认 400ms：处理器没有应答 → 宿主强制关闭模块，回到坞内
页面可以声明更长的等待（上限 2000ms）：

  window.__dockletBackGrace = 1200;   // 毫秒，超过 2000 按 2000 处理

页面未 ready、白屏或崩溃 → 立即关闭，不等待
```

注意：

```text
声明 grace 只影响"宿主等多久"，不影响"能不能退"——
超过 grace 仍然会退出，模块不能借此永久拦截返回
想播放退出动画时：先 return 'handled'，自己完成动画后再调用
docklet.close()（或让下一次返回返回 'exit'）
```

`onBack()` 返回注销函数。页面卸载时调用它可以避免处理器残留。模块不应直接监听 Activity 或系统返回手势。

## 7. 存储

### 7.1 目录

```text
files/data/<id>/
  app.db
  files/
```

### 7.2 接口

```text
storage.open()
storage.query(sql, params)
storage.exec(sql, params)
storage.transaction(fn)
```

宿主校验：

```text
调用方模块 id
数据库路径必须是 data/<id>/app.db
SQL 参数化
不允许访问其他模块目录
```

### 7.3 迁移

```text
每个库有 schemaVersion
升级在事务中执行
迁移前备份 app.db
失败回滚备份和模块版本
```

### 7.4 保存时机

```text
onPause 必存
定时 checkpoint（1–3 秒）
关键操作后（走一步/提交表单）
跳转前宿主强制 save
```

保存超时：

```text
300–500ms
超时使用最近一次 checkpoint
不阻塞跳转无限等待
```

## 8. 能力桥

模块统一调用：

```text
window.docklet.request(method, params) -> Promise
```

宿主校验：

```text
模块 id
capabilities 白名单
参数类型
路径必须落在 data/<id>
```

建议能力：

```text
storage
files
clipboard
openExternal
notify
background
network（可选代理）
```

不暴露：

```text
任意文件路径
其他模块数据库
shell 命令
任意原生反射
```

## 9. 崩溃恢复

启动前：

```text
SharedPreferences 写 launching=<id>
```

模块 ready 后清除。

异常：

```text
ready 超时
onRenderProcessGone
进程死亡
```

处理：

```text
crashCount[id]++
释放槽
回模块列表
```

连续失败：

```text
不自动打开
标记“上次启动失败”
提供卸载/清除数据/重试
```

## 10. 通知与跳转

后台消息由 Shell BackgroundService 负责，不依赖其他模块。

流程：

```text
收到模块消息
→ 发系统通知
→ 通知携带 moduleId=dsh-console, sessionId
→ 用户点击
→ 宿主先让当前模块 pause + save
→ 等待 SQLite 提交或超时
→ 释放/挂起当前模块
→ 打开目标模块
```

返回：

```text
从目标模块返回
→ 恢复之前模块
→ 读 SQLite
→ restoreState
```

要求：

```text
通知权限
通知渠道
PendingIntent 路由
模块不存在时提示安装
```

## 11. 示例模块要求

壳阶段只验收 demo 示例模块。

demo 需要覆盖：

```text
一个页面
一个计数器
一个输入框
一个保存按钮
一个模拟消息按钮
```

验收点：

```text
安装后可启动
修改计数/输入后可保存
切到其他页面再回来，状态恢复
进程被回收后，状态仍能恢复
收到模拟消息后能发通知
点通知能回到 demo
```

壳不定义业务表结构。  
demo 的数据表由 demo 自己维护，壳只提供 SQLite 连接和路径隔离。

## 12. 生命周期与系统行为

ModuleActivity 统一处理：

```text
返回键
切后台/回前台
键盘与安全区
文件选择
权限申请
横竖屏
进程被杀后的恢复
```

模块只负责自己的页面逻辑，不重复实现这些。

## 13. 开发与构建

每个模块独立目录：

```text
apps/<id>/
  src/
  module.json
  build/
```

统一 SDK：

```text
docklet.request
docklet.storage
docklet.ui
docklet.lifecycle
docklet.notify
```

构建产物：

```text
<id>-<version>.pkg
```

上传到自己的源，壳按需下载。

## 14. 实施阶段

```text
阶段 0：规范
  module.json、生命周期、存储、通知、崩溃策略

阶段 1：ModuleManager
  下载、解包、current、回滚、卸载

阶段 2：RuntimeHost
  一个模块能启动、退出、销毁

阶段 3：StorageService
  SQLite、路径隔离、迁移、备份

阶段 4：生命周期
  pause/save/restore、跳转前保存

阶段 5：崩溃恢复
  ready 超时、onRenderProcessGone、安全模式

阶段 6：通知与跳转
  后台服务、PendingIntent、返回恢复

阶段 7：示例模块与验收
  SQLite 数据层、3/5/30、去 cursor

阶段 8：测试与加固
  低内存、崩溃、更新回滚、清缓存
```

粗略工程量：

```text
11–18 人月
2–3 人：6–9 个月到可用
```

## 15. 验收标准

```text
1. 模块能按需下载、安装、启动
2. 重启壳后模块仍在
3. 清 WebView 缓存后 SQLite 数据还在
4. 模块崩溃后壳能回列表
5. 连续崩溃不自动打开
6. 更新失败能回滚
7. 卸载只删自己的模块和数据
8. 跳转前当前模块能保存
9. 返回后能恢复原页面/原局
10. 示例模块能正常启动、保存、恢复、收发消息
11. 长会话内存稳定
```

## 16. 主要风险

```text
WebView 内存与低端机 OOM
进程槽数量与 Android 后台限制
SQLite 迁移失败/数据损坏
模块不实现生命周期契约
返回恢复耗时（WebView 冷启动）
OEM WebView 差异
通知权限与后台服务限制
```

对应措施：

```text
单前台 WebView
状态可序列化
保存超时兜底
崩溃计数与安全模式
更新保留上一版
故障注入测试
```

## 17. 待确认

```text
1. 示例模块只做最小验收，还是按完整业务模型预演
2. 默认启用 1 个还是 2 个 WebView 槽
3. 后台消息由 Shell 服务统一持有，还是各模块自己持有
4. 模块更新是否允许自动，还是必须用户确认
5. 是否保留上一版用于回滚
```

## 18. 结论

```text
这是当前约束下的最佳折中：
  下载/安装/卸载
  模块独立进程/数据
  原生 SQLite
  跳转前保存、返回恢复

它不是微信小程序运行时
它能满足当前约束：
  壳被模块拖死
  WebView 存储被清
  卸载/更新丢数据
  跳转丢当前状态
```
