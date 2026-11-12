import { Capacitor, registerPlugin } from '@capacitor/core'

export interface HostedPermission {
  name: string
  reason: string
}

export interface HostedApp {
  id: string
  name: string
  version: string
  icon: string
  shortcutColor?: string
  entry: string
  origin: string
  description?: string
  tagline?: string
  author?: string
  authorContact?: string
  updatedAt?: string
  publishedAt?: string
  releasedAt?: string
  /** 应用目录占用（字节） */
  size?: number
  /** 清单声明 immersive：铺满全屏，不显示壳的返回条 */
  immersive?: boolean
  capabilities: string[]
  permissions: HostedPermission[]
}

interface FsEntry {
  name: string
  dir: boolean
  size: number
}

export interface AppNotifyEntry {
  id: string
  name: string
  icon: string
  enabled: boolean
}

interface AppHostPlugin {
  list(): Promise<{ apps: HostedApp[] }>
  pinShortcut(options: {
    id: string
    name: string
    icon?: string
    color?: string
  }): Promise<{ requested: boolean }>
  security(): Promise<{ webMessageListener: boolean }>
  inspect(options: { url: string }): Promise<HostedApp & { size: number; token?: string }>
  inspectBase64(options: { data: string }): Promise<HostedApp & { size: number; token?: string }>
  installToken(options: { token: string }): Promise<Omit<HostedApp, 'icon' | 'capabilities' | 'permissions'>>
  cancelInstall(): Promise<void>
  addListener(
    eventName: 'progress' | 'permissionRequest',
    listener: (data: never) => void,
  ): Promise<{ remove: () => Promise<void> }>
  requestNotifications(): Promise<{ granted: boolean }>
  install(options: { url: string }): Promise<Omit<HostedApp, 'icon' | 'capabilities' | 'permissions'>>
  installBase64(options: { data: string }): Promise<Omit<HostedApp, 'icon' | 'capabilities' | 'permissions'>>
  remove(options: { id: string }): Promise<void>
  fsWrite(options: { id: string; path: string; data: string }): Promise<void>
  fsRead(options: { id: string; path: string }): Promise<{ data: string }>
  fsList(options: { id: string; path?: string }): Promise<{ items: FsEntry[] }>
  fsDelete(options: { id: string; path: string }): Promise<void>
  notify(options: { title: string; body: string; appId?: string }): Promise<void>
  appNotifications(): Promise<{ apps: AppNotifyEntry[] }>
  setAppNotification(options: { id: string; enabled: boolean }): Promise<void>
  permissionStatus(): Promise<{ notifications: boolean }>
  openNotificationSettings(): Promise<void>
  download(options: { url: string; name?: string }): Promise<void>
  openExternal(options: { url: string }): Promise<void>
  clipboardWrite(options: { text: string }): Promise<void>
  shareText(options: { text: string }): Promise<void>
}

const native = registerPlugin<AppHostPlugin>('AppHost')

export async function appSecurity(): Promise<{ webMessageListener: boolean }> {
  if (!Capacitor.isNativePlatform()) return { webMessageListener: true }
  try {
    return await native.security()
  } catch {
    return { webMessageListener: true }
  }
}

export async function listHostedApps(): Promise<HostedApp[]> {
  if (!Capacitor.isNativePlatform()) return []
  const result = await native.list()
  return (result.apps ?? []).map((app) => ({
    ...app,
    capabilities: app.capabilities ?? [],
    permissions: app.permissions ?? [],
  }))
}

export async function pinAppShortcut(app: HostedApp): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  const result = await native.pinShortcut({
    id: app.id,
    name: app.name,
    icon: app.icon,
    color: app.shortcutColor,
  })
  return Boolean(result && result.requested)
}

export async function installFromUrl(url: string): Promise<Omit<HostedApp, 'icon' | 'capabilities' | 'permissions'>> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  return native.install({ url })
}

export async function installFromBytes(bytes: Uint8Array): Promise<Omit<HostedApp, 'icon' | 'capabilities' | 'permissions'>> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  return native.installBase64({ data: toBase64(bytes) })
}

export async function inspectFromUrl(
  url: string,
): Promise<HostedApp & { size: number; token?: string }> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  const result = await native.inspect({ url })
  return {
    ...result,
    capabilities: result.capabilities ?? [],
    permissions: result.permissions ?? [],
  }
}

export async function inspectFromBytes(
  data: Uint8Array,
): Promise<HostedApp & { size: number; token?: string }> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  const result = await native.inspectBase64({ data: toBase64(data) })
  return {
    ...result,
    capabilities: result.capabilities ?? [],
    permissions: result.permissions ?? [],
  }
}

export async function installFromToken(token: string): Promise<void> {
  if (!Capacitor.isNativePlatform()) throw new Error('NOT_NATIVE')
  await native.installToken({ token })
}

export async function cancelInstall(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  try {
    await native.cancelInstall()
  } catch {
    /* 忽略 */
  }
}

export function onInstallProgress(
  listener: (data: { stage: string; received: number; total: number }) => void,
): () => void {
  if (!Capacitor.isNativePlatform()) return () => {}
  const handle = native.addListener('progress', listener)
  return () => {
    void handle.then((item) => item.remove())
  }
}

export async function removeHostedApp(id: string): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  await native.remove({ id })
}

export function appEntryUrl(app: HostedApp): string {
  const base = app.origin.endsWith('/') ? app.origin : app.origin + '/'
  // .js 入口交给壳生成的 iframe 包装页
  if (app.entry.endsWith('.js')) return base + '__docklet.html'
  return base + app.entry
}

function toBase64(data: Uint8Array): string {
  let binary = ''
  for (const byte of data) binary += String.fromCharCode(byte)
  return btoa(binary)
}

function fromBase64(text: string): Uint8Array {
  const binary = atob(text)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes
}

export const appFs = {
  async write(id: string, path: string, data: Uint8Array): Promise<void> {
    await native.fsWrite({ id, path, data: toBase64(data) })
  },
  async read(id: string, path: string): Promise<Uint8Array> {
    const result = await native.fsRead({ id, path })
    return fromBase64(result.data)
  },
  async list(id: string, path = ''): Promise<FsEntry[]> {
    const result = await native.fsList({ id, path })
    return result.items ?? []
  },
  async remove(id: string, path: string): Promise<void> {
    await native.fsDelete({ id, path })
  },
}

export const appNotify = (title: string, body: string, appId?: string) =>
  native.notify({ title, body, appId })
/** 有 background 能力的应用 + 是否允许提醒（壳设置页用） */
export async function appNotifications(): Promise<AppNotifyEntry[]> {
  if (!Capacitor.isNativePlatform()) return []
  try {
    const result = await native.appNotifications()
    return result.apps ?? []
  } catch {
    return []
  }
}
export const setAppNotification = (id: string, enabled: boolean) =>
  native.setAppNotification({ id, enabled })
export const appPermissionStatus = () => native.permissionStatus()
export const appRequestNotifications = () => native.requestNotifications()
export const appOnPermissionRequest = (
  listener: (data: { appId: string; name: string; reason: string }) => void,
) =>
  native.addListener('permissionRequest', listener as (data: never) => void)
export const appOpenNotificationSettings = () => native.openNotificationSettings()
export const appDownload = (url: string, name?: string) => native.download({ url, name })
export const appOpenExternal = (url: string) => native.openExternal({ url })
export const appClipboardWrite = (text: string) => native.clipboardWrite({ text })
export const appShareText = (text: string) => native.shareText({ text })
