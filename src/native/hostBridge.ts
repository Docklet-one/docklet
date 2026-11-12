import { Capacitor } from '@capacitor/core'
import {
  appClipboardWrite,
  appDownload,
  appFs,
  appNotify,
  appOpenExternal,
  appShareText,
  listHostedApps,
} from './appHost'
import { ensureNotificationPermission, startWatch, stopWatch, testNotification, watchStatus, type WatchPolicy } from './phoneRelay'

const APP_ORIGIN = /^https:\/\/([a-z][a-z0-9-]{1,31})\.app\.local$/

const allowed = new Map<string, Set<string>>()

export async function refreshHostBridge(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  let apps
  try {
    apps = await listHostedApps()
  } catch {
    // 读取失败时保留旧能力表，避免一次瞬时错误清空所有应用
    return
  }
  allowed.clear()
  for (const app of apps) allowed.set(app.id, new Set(app.capabilities ?? []))
}

interface RequestMessage {
  type: 'docklet:request'
  requestId: string
  method: string
  params?: Record<string, unknown>
}

function reply(target: Window, origin: string, requestId: string, ok: boolean, payload: unknown) {
  target.postMessage(
    { type: 'docklet:response', requestId, ok, ...(ok ? { data: payload } : { error: payload }) },
    origin,
  )
}

/** 应用通过 postMessage 调用壳能力；只接受声明过的能力。 */
export function installHostBridge(): () => void {
  const onMessage = (event: MessageEvent) => {
    const match = APP_ORIGIN.exec(event.origin)
    if (!match || !event.source) return
    const data = event.data as RequestMessage | undefined
    if (!data || data.type !== 'docklet:request' || !data.requestId) return

    const id = match[1]
    const caps = allowed.get(id) ?? new Set<string>()
    const target = event.source as Window

    const run = async () => {
      const method = data.method
      const params = (data.params ?? {}) as Record<string, string>
      switch (method) {
        case 'fs.write': {
          if (!caps.has('fs')) throw new Error('CAPABILITY_DENIED')
          const bytes = Uint8Array.from(atob(params.data ?? ''), (c) => c.charCodeAt(0))
          await appFs.write(id, params.path ?? '', bytes)
          return null
        }
        case 'fs.read': {
          if (!caps.has('fs')) throw new Error('CAPABILITY_DENIED')
          const bytes = await appFs.read(id, params.path ?? '')
          let binary = ''
          for (const byte of bytes) binary += String.fromCharCode(byte)
          return btoa(binary)
        }
        case 'fs.list': {
          if (!caps.has('fs')) throw new Error('CAPABILITY_DENIED')
          return appFs.list(id, params.path ?? '')
        }
        case 'fs.delete': {
          if (!caps.has('fs')) throw new Error('CAPABILITY_DENIED')
          await appFs.remove(id, params.path ?? '')
          return null
        }
        case 'notify': {
          if (!caps.has('notify')) throw new Error('CAPABILITY_DENIED')
          await appNotify(params.title ?? 'Docklet', params.body ?? '', id)
          return null
        }
        case 'openExternal': {
          if (!caps.has('openExternal')) throw new Error('CAPABILITY_DENIED')
          await appOpenExternal(params.url ?? '')
          return null
        }
        case 'download': {
          if (!caps.has('download')) throw new Error('CAPABILITY_DENIED')
          await appDownload(params.url ?? '', params.name ?? '')
          return null
        }
        case 'clipboard.write': {
          if (!caps.has('clipboard')) throw new Error('CAPABILITY_DENIED')
          await appClipboardWrite(params.text ?? '')
          return null
        }
        case 'share': {
          if (!caps.has('share')) throw new Error('CAPABILITY_DENIED')
          await appShareText(params.text ?? '')
          return null
        }
        case 'shell.exit': {
          window.dispatchEvent(new CustomEvent('docklet:exit-app', { detail: { id } }))
          return null
        }
        case 'watch.start': {
          if (!caps.has('background')) throw new Error('CAPABILITY_DENIED')
          const policy = (data.params?.policy ?? {}) as Record<string, unknown>
          const endpoint = String(policy.endpoint ?? '')
          if (!endpoint.startsWith('http://') && !endpoint.startsWith('https://')) throw new Error('INVALID_ENDPOINT')
          await startWatch({
            ...(policy as unknown as WatchPolicy),
            id: `${id}:${String(policy.id ?? '')}`,
            openApp: id,
            suppressApp: id,
          })
          return null
        }
        case 'watch.stop': {
          if (!caps.has('background')) throw new Error('CAPABILITY_DENIED')
          await stopWatch(`${id}:${String(params.id ?? '')}`)
          return null
        }
        case 'watch.status': {
          if (!caps.has('background')) throw new Error('CAPABILITY_DENIED')
          return watchStatus(`${id}:${String(params.id ?? '')}`)
        }
        case 'watch.test': {
          if (!caps.has('background')) throw new Error('CAPABILITY_DENIED')
          await testNotification()
          return null
        }
        case 'watch.ensurePermission': {
          if (!caps.has('background')) throw new Error('CAPABILITY_DENIED')
          return ensureNotificationPermission()
        }
        default:
          throw new Error('UNKNOWN_METHOD')
      }
    }

    run()
      .then((value) => reply(target, event.origin, data.requestId, true, value))
      .catch((error) => reply(target, event.origin, data.requestId, false, error instanceof Error ? error.message : String(error)))
  }

  window.addEventListener('message', onMessage)
  return () => window.removeEventListener('message', onMessage)
}
