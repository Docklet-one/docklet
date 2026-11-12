import { Capacitor, registerPlugin } from '@capacitor/core'

export interface WatchPolicy {
  id: string
  endpoint: string
  token?: string
  intervalMs?: number
  field?: string
  /** edge（默认）：from→to 触发；change：字段值变化触发（每条消息提醒） */
  mode?: 'edge' | 'change'
  from?: boolean
  to?: boolean
  failField?: string
  title?: string
  doneText?: string
  failText?: string
  openApp?: string
  suppressApp?: string
  enabled?: boolean
}

export interface WatchStatus {
  running: boolean
  count: number
  exists: boolean
  activeApp: string
  granted: boolean
}

interface PhoneRelayPlugin {
  watchStart(options: WatchPolicy): Promise<{ ok: boolean; count: number }>
  watchStop(options: { id: string }): Promise<{ count: number }>
  watchStatus(options: { id: string }): Promise<WatchStatus>
  setActive(options: { appId: string }): Promise<void>
  testNotification(): Promise<void>
  consumeOpenChat(): Promise<{ open: boolean; app?: string }>
  readOpenChat(): Promise<{ open: boolean; app?: string }>
  requestPermission(): Promise<{ granted: boolean; pending?: boolean }>
}

const PhoneRelay = registerPlugin<PhoneRelayPlugin>('PhoneRelay')

/** 申请通知权限；返回是否已授予（未授予时系统可能弹框） */
export async function ensureNotificationPermission(): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) return true
  try {
    const result = await PhoneRelay.requestPermission()
    return Boolean(result && result.granted)
  } catch {
    return false
  }
}

/** 提交/更新一条后台监听策略 */
export async function startWatch(policy: WatchPolicy): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  await PhoneRelay.watchStart(policy)
}

export async function stopWatch(id: string): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  await PhoneRelay.watchStop({ id })
}

export async function watchStatus(id: string): Promise<WatchStatus | null> {
  if (!Capacitor.isNativePlatform()) return null
  try {
    return await PhoneRelay.watchStatus({ id })
  } catch {
    return null
  }
}

/** 当前前台的应用 id：策略里的 suppressApp 命中时就不弹提醒 */
export async function setActiveApp(appId: string): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  try {
    await PhoneRelay.setActive({ appId })
  } catch {
    /* 忽略 */
  }
}

export async function testNotification(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  await PhoneRelay.testNotification()
}

/** 通知点击后要打开哪个应用；没有待处理的通知就返回 null */
export async function consumeOpenApp(): Promise<string | null> {
  if (!Capacitor.isNativePlatform()) return null
  try {
    const result = await PhoneRelay.consumeOpenChat()
    return result && result.open ? String(result.app || 'chat') : null
  } catch {
    return null
  }
}

export async function peekOpenApp(): Promise<string | null> {
  if (!Capacitor.isNativePlatform()) return null
  try {
    const result = await PhoneRelay.readOpenChat()
    return result && result.open ? String(result.app || 'chat') : null
  } catch {
    return null
  }
}
