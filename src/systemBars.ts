import { Capacitor } from '@capacitor/core'
import { StatusBar, Style } from '@capacitor/status-bar'

/**
 * 全局状态栏：内容画到状态栏后面，状态栏透明、深色文字。
 * 必须在根组件挂载前调用，锁屏等 AppGate 页面才能生效。
 */
export function installSystemBars() {
  if (!Capacitor.isNativePlatform()) return
  void StatusBar.setOverlaysWebView({ overlay: true }).catch(() => undefined)
  void StatusBar.setStyle({ style: Style.Light }).catch(() => undefined)
  void StatusBar.setBackgroundColor({ color: '#00000000' }).catch(() => undefined)
}
