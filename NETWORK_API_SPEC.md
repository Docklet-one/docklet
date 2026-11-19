# Docklet 网络状态接口 spec（v0.1）

> Shell 已实现：`docklet.network.getState()` / `watch()`，见 `DockletBridge`、`NetworkWatcher`。

> 读者：壳（Android 原生）/ 模块开发者
> 目的：给模块一个**快速、可靠、只读**的设备网络状态来源，解决 WebView `navigator.onLine`
> 在部分机型不触发、延迟大的问题。
> 范围：只描述“设备有没有网”，**不负责“插件/relay 通不通”**——后者归模块自己判断。
> 日期：2026-11-16
> 状态：壳已实现（SDK `docklet.network.getState/watch`）；契约以 `docs/module-spec.md` §8「网络状态」为准，本文是详细说明。

## 1. 设计原则

```text
只读、无副作用、不需要权限弹窗
语义是“设备事实”，不是“业务连通性”
旧壳没有接口时，模块能降级到 navigator.onLine
和 backgroundMessages 相互独立：一个管网络状态，一个管后台消息
```

## 2. 接口

```js
docklet.network.getState()          // -> Promise<NetworkState>
docklet.network.watch(callback)     // -> unsubscribe()
```

### 2.1 getState

```ts
type NetworkState = {
  online: boolean;        // 是否存在默认网络（Android: ConnectivityManager 有 active network）
  type: "wifi" | "cellular" | "ethernet" | "vpn" | "other" | "none";
  metered: boolean;       // 是否计费网络
  validated: boolean;     // 是否有互联网且验证通过（Android NET_CAPABILITY_VALIDATED）
  changedAt: number;      // 最近一次网络变化的毫秒时间戳
};
```

语义约束：

- `online === false` 时，`type` 必须是 `"none"`，`validated` 必须为 `false`
- `online === true` 但 `validated === false`：**不代表插件不可达**，可能连着没有外网的路由器，局域网 relay 仍然通
- `changedAt` 在每次 `online`/`type`/`validated` 变化时更新
- 首次调用必须返回当前快照，不能等回调

示例：

```js
await docklet.network.getState();
// { online: true, type: "wifi", metered: false, validated: true, changedAt: 1760000000000 }
```

### 2.2 watch

```js
const off = docklet.network.watch((state) => {
  // state 结构同 getState()
});

// 页面销毁或不再需要时
off();
```

回调时机：

- `online` / `type` / `validated` 任一项变化时触发
- 同一变化只触发一次（壳侧做去抖，避免 `onCapabilitiesChanged` 抖动刷屏）
- 页面存活期间送达；页面销毁后不补发
- 壳进入后台（Doze/省电）时可能延迟或暂停，模块不能依赖后台实时性
- 订阅后不要求立即回调一次；模块应先 `getState()` 再 `watch`

## 3. 权限与能力

```text
capabilities: 需要 "network"（现有白名单里已有）
运行时权限：无
隐私：不需要读取 SSID / 运营商信息；type 只到 wifi/cellular 粒度
```

## 4. 失败与降级

```text
接口不存在（旧壳）      -> 模块用 navigator.onLine + 自己的探测
getState 抛错           -> 模块按“未知”处理，不阻断主流程
watch 不支持            -> 返回 no-op unsubscribe，模块退回定时探测
```

模块侧推荐降级顺序：

```text
docklet.network.getState() -> navigator.onLine -> true（未知，继续探测）
```

## 5. 非目标

```text
插件/relay 可达性        -> 模块自己探（/health、TCP、SSE）
任意地址 TCP probe       -> 不做，避免 SSRF/滥用
延迟/丢包/信号强度        -> 未来可另开 getQuality()，第一版不做
SSID / 运营商 / IP       -> 不暴露
后台持续监听             -> 不做；后台依赖前台服务
```

## 6. 模块使用范式

```text
第一层（快）：network.getState()
  !online -> 显示「网络已断开」，停止探测
  online  -> 进入第二层

第二层（准）：模块探自己的服务端
  通   -> 已连接
  不通 -> 「连不到电脑，请检查 relay 或网络」，按策略重连

network.watch 只做触发器：
  网络恢复/切换 -> 立即探一次，不用等定时器
  网络丢失      -> 立刻更新第一层状态
```

`validated === false` 时仍应进入第二层，不能直接判死。

## 7. 测试清单

```text
[ ] 飞行模式开/关，getState 与 watch 在 1 秒内更新
[ ] Wi-Fi 断开切移动数据，type 与 metered 正确
[ ] Wi-Fi 连着但拔掉上游网线，online=true、validated=false
[ ] 局域网 relay 可达、无外网：模块仍能连上
[ ] 页面隐藏/恢复：watch 行为符合“页面存活期间”约定
[ ] 页面销毁后订阅自动清理，无内存泄漏
[ ] 旧壳（无接口）：模块降级逻辑正常
```

## 8. 版本

```text
接口版本：network v0.1
壳要求：Docklet 模块平台（在现有 capabilities 白名单内加 network 状态能力）
兼容：模块必须 feature-detect，navigator.onLine 作为兜底
```
