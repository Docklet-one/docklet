import { useCallback, useEffect, useRef, useState } from 'react'
import { App as NativeApp } from '@capacitor/app'
import { pushBackHandler } from '../back'
import { appEntryUrl, appSecurity, removeHostedApp, type HostedApp } from '../native/appHost'
import { UninstallConfirm } from './UninstallConfirm'
import { setActiveApp } from '../native/phoneRelay'
import { logDiag } from '../diag'

/** 打开一个目录应用的整页容器。布局与键盘交给系统，网页只收安全区。 */
export function HostedAppFrame({
  app,
  onClose,
  onChanged,
}: {
  app: HostedApp
  onClose: () => void
  onChanged?: () => void
}) {
  const frameRef = useRef<HTMLIFrameElement>(null)
  const lastInsets = useRef('')
  const [menu, setMenu] = useState(false)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [kbCss, setKbCss] = useState(0)
  const [blocked, setBlocked] = useState(false)
  const [removeError, setRemoveError] = useState('')
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading')
  const [reason, setReason] = useState('')
  const [retryKey, setRetryKey] = useState(0)
  const failedRef = useRef(false)
  const loadedRef = useRef(false)
  const backTimerRef = useRef<number | null>(null)
  const backHandledRef = useRef(false)
  // 清单声明 immersive 的应用铺满全屏（不渲染壳的返回条）
  const immersive = app.immersive === true
  const targetOrigin = app.origin.startsWith('http') ? app.origin.replace(/\/$/, '') : '*'

  const readInset = (name: string) => {
    const raw = getComputedStyle(document.documentElement).getPropertyValue(name)
    return Number.parseFloat(raw) || 0
  }

  const send = (top: number, bottom: number, ime: number) => {
    const key = `${top}|${bottom}|${ime}`
    if (key === lastInsets.current) return
    lastInsets.current = key
    frameRef.current?.contentWindow?.postMessage(
      { type: 'docklet:insets', detail: { top, bottom, ime } },
      targetOrigin,
    )
  }

  const sendInsets = () => {
    send(readInset('--safe-top'), readInset('--safe-bottom'), 0)
  }

  const handleLoad = () => {
    loadedRef.current = true
    if (!failedRef.current) setPhase('ready')
    // 首帧的 postMessage 可能打在 about:blank 上丢失，加载后强制重发
    lastInsets.current = ''
    sendInsets()
  }

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      const data = event.data as { type?: string; id?: string; reason?: string } | null
      if (!data || (data.type !== 'docklet:load-error' && data.type !== 'docklet:back-handled')) return
      if (data.type === 'docklet:back-handled') {
        backHandledRef.current = true
        if (backTimerRef.current) window.clearTimeout(backTimerRef.current)
        return
      }
      if (data.id && data.id !== app.id) return
      failedRef.current = true
      logDiag('iframe-error', `${app.id} ${data.reason ?? ''}`)
      setReason(data.reason === 'NOT_FOUND' ? '找不到应用入口' : '加载失败')
      setPhase('error')
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [app.id])

  useEffect(() => {
    failedRef.current = false
    loadedRef.current = false
    setPhase('loading')
    setReason('')
    const timer = window.setTimeout(() => {
      if (loadedRef.current || failedRef.current) return
      logDiag('iframe-timeout', app.id)
      setReason('加载超时')
      setPhase('error')
    }, 10000)
    return () => window.clearTimeout(timer)
  }, [app.id, retryKey])

  const requestClose = useCallback(() => {
    backHandledRef.current = false
    frameRef.current?.contentWindow?.postMessage({ type: 'docklet:back' }, targetOrigin)
    if (backTimerRef.current) window.clearTimeout(backTimerRef.current)
    backTimerRef.current = window.setTimeout(() => {
      if (!backHandledRef.current) onClose()
    }, 250)
  }, [onClose, targetOrigin])

  useEffect(() => pushBackHandler(requestClose), [requestClose])

  useEffect(() => {
    return () => {
      if (backTimerRef.current) window.clearTimeout(backTimerRef.current)
    }
  }, [])

  useEffect(() => {
    if (!menu) return
    return pushBackHandler(() => setMenu(false))
  }, [menu])

  useEffect(() => {
    if (!confirmRemove) return
    return pushBackHandler(() => setConfirmRemove(false))
  }, [confirmRemove])

  // 应用内调用 api.shell.exit()：桥接派发 docklet:exit-app，这里负责关掉容器
  useEffect(() => {
    const onExit = (event: Event) => {
      const detail = (event as CustomEvent<{ id?: string }>).detail
      if (!detail || detail.id === app.id) onClose()
    }
    window.addEventListener('docklet:exit-app', onExit)
    return () => window.removeEventListener('docklet:exit-app', onExit)
  }, [app.id, onClose])

  useEffect(() => {
    void setActiveApp(app.id)
    const subscription = NativeApp.addListener('appStateChange', (state) => {
      void setActiveApp(state.isActive ? app.id : '')
    })
    return () => {
      void setActiveApp('')
      void subscription.then((handle) => handle.remove())
    }
  }, [app.id])

  useEffect(() => {
    sendInsets()
    const timer = window.setTimeout(sendInsets, 300)
    return () => window.clearTimeout(timer)
  }, [app.id])

  // 键盘：原生发 docklet:keyboard（物理像素），除以 devicePixelRatio 得 CSS px
  useEffect(() => {
    const onKeyboard = (event: Event) => {
      const detail = (event as CustomEvent<{ height?: number }>).detail
      if (!detail) return
      const ime = Math.round((Number(detail.height) || 0) / (window.devicePixelRatio || 1))
      setKbCss(ime)
      const top = readInset('--safe-top')
      const bottom = ime > 0 ? 0 : readInset('--safe-bottom')
      send(top, bottom, 0)
    }
    window.addEventListener('docklet:keyboard', onKeyboard)
    return () => window.removeEventListener('docklet:keyboard', onKeyboard)
  }, [app.id])

  useEffect(() => {
    if (!removeError) return
    const timer = window.setTimeout(() => setRemoveError(''), 2400)
    return () => window.clearTimeout(timer)
  }, [removeError])

  useEffect(() => {
    void appSecurity().then((state) => setBlocked(!state.webMessageListener))
  }, [app.id])

  return (
    <div className={`appview${immersive ? ' is-immersive' : ''}`}>
      {immersive ? null : (
        <div className="appview-bar">
          <button type="button" className="appview-back" onClick={onClose}>
            ‹ 返回
          </button>
          <span className="appview-title">{app.name}</span>
          <button type="button" className="appview-more" aria-label="应用菜单" onClick={() => setMenu(true)}>
            ⋯
          </button>
        </div>
      )}

      {blocked ? (
        <div style={{ padding: '32px 24px', fontSize: 15, lineHeight: 1.8, color: 'var(--ink)' }}>
          <b>系统 WebView 过旧</b>
          <p style={{ color: 'var(--ink-soft)' }}>
            这台设备缺少安全隔离所需的 WebView 能力，应用已停止运行。请更新「Android System WebView」或 Chrome 后重试。
          </p>
        </div>
      ) : (
        <iframe
        key={retryKey}
        ref={frameRef}
        title={app.name}
        src={appEntryUrl(app)}
        sandbox="allow-scripts allow-same-origin allow-forms"
        allow="camera; microphone; geolocation; accelerometer; gyroscope; magnetometer; clipboard-write; fullscreen"
        style={{ height: kbCss > 0 ? `calc(100% - ${kbCss}px)` : '100%' }}
        onLoad={handleLoad}
      />
      )}

      {phase === 'error' && (
        <div className="appview-state">
          <span className="appview-state-icon">{app.icon || '▢'}</span>
          <b>应用加载失败</b>
          <p>{app.name} · {reason || '未知原因'}</p>
          <div className="appview-state-actions">
            <button
              type="button"
              className="shell-primary"
              onClick={() => setRetryKey((value) => value + 1)}
            >
              重试
            </button>
            <button type="button" className="install-ghost" onClick={onClose}>
              返回
            </button>
          </div>
        </div>
      )}

      {menu && (
        <div className="sheet-scrim" onClick={() => setMenu(false)}>
          <div className="sheet appview-menu" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <div className="appview-info">
              <span className="appview-icon">{app.icon || '▢'}</span>
              <span>
                <b>{app.name}</b>
                <i>
                  v{app.version} · {app.id}
                </i>
              </span>
            </div>
            <p className="appview-origin">{app.origin}</p>
            <button
              type="button"
              className="sheet-row is-danger"
              onClick={() => {
                setMenu(false)
                setConfirmRemove(true)
              }}
            >
              删除应用
            </button>
            <button type="button" className="sheet-ghost" onClick={() => setMenu(false)}>
              取消
            </button>
          </div>
        </div>
      )}

      {confirmRemove && (
        <UninstallConfirm
          name={app.name}
          onCancel={() => setConfirmRemove(false)}
          onConfirm={async () => {
            try {
              await removeHostedApp(app.id)
              onChanged?.()
              onClose()
            } catch {
              setConfirmRemove(false)
              setRemoveError('删除失败，请重试')
            }
          }}
        />
      )}
      {removeError && <div className="shell-toast" role="status" aria-live="polite">{removeError}</div>}
    </div>
  )
}
