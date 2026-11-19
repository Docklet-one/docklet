# Docklet 模块平台 · 项目方针与技术路线

> 本文件是新项目的总纲。  
> 实施细节见 `module-platform.md`，交接见 `HANDOFF.md`，当前进度见 `STATUS.md`。

## 1. 项目定义

```text
Docklet 是一个封闭的模块平台。

壳负责：
  模块管理
  下载、安装、更新、卸载
  独立运行环境
  数据存储
  生命周期
  通知与跳转
  崩溃恢复

模块负责：
  自己的界面
  自己的业务逻辑
  自己的数据表
  自己的保存/恢复逻辑
```

壳不内置业务应用。  
所有模块由我们开发和发布。

## 2. 方针

### 2.1 平台优先

```text
先把壳做成稳定的平台
再谈具体模块
壳不依赖任何业务模块的数据结构
```

### 2.2 数据优先

```text
关键数据进原生 SQLite
页面存储只放可丢的临时状态
模块数据按 id 隔离
更新代码不丢数据
```

### 2.3 单前台，可恢复

```text
同一时间只有一个前台 WebView
离开模块保存状态
返回时恢复
不依赖后台 WebView 常驻
```

### 2.4 契约先行

```text
先定 module.json、生命周期、存储接口、桥接口
再写实现
模块必须实现 saveState / restoreState
```

### 2.5 明确边界

```text
不做微信级模板 DSL
不做逻辑/视图双线程
不做原生组件
不做开放第三方市场
```

## 3. 技术路线

## 3.1 总体结构

```text
Shell 主进程
  ModuleManager
  StorageService
  ApiBridge
  CrashGuard
  BackgroundService
  ModuleRegistry

模块进程
  :m1  当前前台模块
  :m2  预加载/备用（可选）
  :m3  临时大模块（可选）

每个模块进程
  ModuleActivity
  一个 WebView
  独立 origin
```

### 3.2 模块包

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
  "id": "demo",
  "name": "示例模块",
  "version": "1.0.0",
  "entry": "index.html",
  "minShell": "1.0.0",
  "capabilities": ["storage", "network", "notify"],
  "data": "demo"
}
```

规则：

```text
id 全局唯一
version 单调递增
entry 必须是包内相对路径
capabilities 决定桥权限
data 决定数据目录
```

包是归档文件，扩展名统一为 `.pkg`。

### 3.3 运行

```text
分配槽
→ 创建 ModuleActivity
→ 创建 WebView
→ 加载 https://<id>.module.local
→ 等待 ready
→ 超时/崩溃 → 回收槽
```

规则：

```text
一个槽同一时间只跑一个模块
模块退出 → 销毁 WebView，释放槽
槽不足 → 排队，不共享
槽复用前清理该槽的 WebView 数据
```

### 3.4 存储

```text
files/data/<id>/
  app.db
  files/
```

接口：

```text
storage.open()
storage.query(sql, params)
storage.exec(sql, params)
storage.transaction(fn)
```

规则：

```text
只能访问自己的 app.db
路径必须在 data/<id> 内
SQL 参数化
迁移前备份
schemaVersion 由模块维护
```

### 3.5 能力桥

模块调用：

```text
window.docklet.request(method, params) -> Promise
```

宿主校验：

```text
模块 id
capabilities 白名单
参数类型
路径范围
```

默认能力：

```text
storage
files
clipboard
openExternal
notify
background
network（可选）
```

### 3.6 生命周期

模块必须实现：

```text
ready
onPause
onResume
saveState
restoreState
```

宿主在跳转前：

```text
pause
→ save
→ 等待提交或超时
→ 释放/挂起
→ 打开目标模块
```

返回：

```text
读 SQLite
→ 重建 WebView
→ restoreState
```

保存超时：

```text
300–500ms
超时使用最近一次 checkpoint
不无限阻塞跳转
```

### 3.7 安装与更新

```text
下载模块包到 cache
→ 校验 module.json / entry / 包结构
→ 解包到 modules/<id>/<version>.new
→ 原子改名为 <version>
→ 更新 current 指针
→ 保留上一版
```

失败：

```text
current 不动
旧版本继续可用
启动失败回滚上一版
```

卸载：

```text
停止模块
释放槽
删除 modules/<id>
确认后删除 data/<id>
```

### 3.8 崩溃恢复

```text
启动前写 launching=<id>
ready 后清除
超时 / 进程死亡 → crashCount[id]++
释放槽 → 回列表
连续失败 → 不自动打开 → 安全模式
```

WebView 侧：

```text
onRenderProcessGone 返回 true
只销毁当前模块，不杀宿主
```

### 3.9 通知

```text
BackgroundService 持有消息连接
收到消息 → 系统通知
PendingIntent 携带 moduleId / sessionId
点击 → 先保存当前模块 → 打开目标模块
```

## 4. 注意事项

### 4.1 内存

```text
一个 WebView 常驻约 50–150MB
同时活跃控制在 1–2 个
低端机优先单前台
不要预加载多个大模块
```

### 4.2 进程槽

```text
Android 进程名必须预声明
槽位不是保命符
后台进程仍可能被系统杀
槽复用必须清理 WebView 数据
```

### 4.3 生命周期

```text
返回键
切后台
键盘/安全区
文件选择
权限申请
横竖屏
进程被杀后的恢复
```

这些由 ModuleActivity 统一处理，模块不重复实现。

### 4.4 数据

```text
页面存储只放可丢状态
SQLite 迁移必须可回滚
卸载删除数据前二次确认
更新代码不动数据
```

### 4.5 更新

```text
下载失败不能破坏当前版本
解包必须原子替换
入口缺失必须拒绝
保留上一版用于回滚
```

### 4.6 通知

```text
Android 13+ 通知权限
后台服务限制
通知点击必须能拉起目标模块
目标模块不存在时提示安装
```

### 4.7 UI

```text
模块自己负责页面
壳提供统一安全区/返回/键盘行为
主题和基础样式通过共享 SDK 提供
避免每个模块各写一套导航
```

### 4.8 改版清理

```text
功能入口或交互改版后，同一次提交内删除旧实现
旧按钮、旧弹窗、旧回调、旧状态字段、旧布局方法都不能留
入口不可达不算删除，死代码必须清理
需要兼容旧行为时，写清原因和移除条件
```

## 5. 开发规范

```text
改功能时同步清理被替代的代码
不保留“以后可能用”的旧入口和旧分支
```

```text
每个模块独立目录
每个模块独立 module.json
每个模块独立数据表
模块之间不共享数据库
模块之间不直接调用
跨模块能力走壳的桥
```

命名：

```text
模块 id：小写字母、数字、连字符
数据目录：data/<id>
origin：https://<id>.module.local
```

## 6. 验收

```text
1. demo 模块能按需下载、安装、启动
2. 重启壳后模块仍在
3. 清 WebView 缓存后 SQLite 数据还在
4. 模块崩溃后壳能回列表
5. 连续崩溃不自动打开
6. 更新失败能回滚
7. 卸载只删自己的模块和数据
8. 跳转前当前模块能保存
9. 返回后能恢复原状态
10. 通知能打开目标模块
```

## 7. 阶段

```text
阶段 0：规范与 demo 骨架
阶段 1：ModuleManager
阶段 2：RuntimeHost
阶段 3：StorageService
阶段 4：生命周期
阶段 5：CrashGuard
阶段 6：BackgroundService
阶段 7：demo 验收
```

## 8. 非目标

```text
微信级 DSL
双线程渲染
原生组件库
第三方开放发布
每个模块一个 APK
```

## 9. 结论

```text
这是一个封闭的 HTML 模块平台。
壳提供运行时、存储、生命周期和恢复。
模块提供业务和界面。
先做壳，再用 demo 验收。
```
