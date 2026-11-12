import { useEffect, useRef, useState } from 'react'
import { pushBackHandler } from '../back'
import { loadCatalog, readCachedCatalog, RECOMMENDED, type CatalogEntry } from '../catalog'
import { AppDetailPage } from './AppDetailPage'
import { AppRow } from './AppRow'
import { UninstallConfirm } from './UninstallConfirm'
import {
  cancelInstall,
  inspectFromBytes,
  inspectFromUrl,
  installFromBytes,
  installFromToken,
  installFromUrl,
  listHostedApps,
  onInstallProgress,
  removeHostedApp,
  type HostedApp,
} from '../native/appHost'

const MESSAGES: Record<string, string> = {
  INVALID_URL: '链接不合法',
  NOT_NATIVE: '请在手机上的 Docklet 里安装',
  TOO_LARGE: '安装包太大',
  TOO_MANY_FILES: '文件数量超限',
  ZIP_UNSAFE: '压缩包路径不安全',
  ZIP_DUPLICATE: '压缩包里有重名文件',
    MANIFEST_MISSING: '缺少 app.json',
  MANIFEST_INVALID: '清单字段不合法',
  ENTRY_MISSING: '入口文件不存在',
  VERSION_NOT_HIGHER: '版本号没有高于已安装版本',
  BUILTIN_PROTECTED: '内置应用不能由安装包覆盖，请等壳更新',
  PUBLISHER_MISMATCH: '这个应用 id 已被其他来源占用',
  INSTALL_FAILED: '安装失败',
}

function explain(error: unknown): string {
  const text = error instanceof Error ? error.message : String(error)
  return MESSAGES[text] ?? text
}

import { formatSize, isNewer } from '../format'
import { filterCatalog } from '../search'
import { getCatalogDropped } from '../catalog'
import { logDiag } from '../diag'
import { markAppActive } from '../usage'

const RECOMMENDED_CATEGORY = '推荐'

export function InstallPage({
  onClose,
  onInstalled,
  onOpen,
  initialDetailId,
  onDetailConsumed,
}: {
  onClose: () => void
  onInstalled: () => void
  onOpen?: (id: string) => void
  initialDetailId?: string | null
  onDetailConsumed?: () => void
}) {
  const [mode, setMode] = useState<'list' | 'local'>('list')
  const [catalog, setCatalog] = useState<CatalogEntry[]>(() => {
    const cached = readCachedCatalog()
    return cached.length > 0 ? cached : RECOMMENDED
  })
  const [category, setCategory] = useState(RECOMMENDED_CATEGORY)
  const [query, setQuery] = useState('')
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [pct, setPct] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [installFailed, setInstallFailed] = useState(false)
  const [warn, setWarn] = useState('')
  const [overwrite, setOverwrite] = useState<{
    id: string
    name: string
    from: string
    to: string
    downgrade: boolean
    action: () => Promise<void>
  } | null>(null)
  const [installing, setInstalling] = useState(false)
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [slow, setSlow] = useState(false)
  const [catalogReady, setCatalogReady] = useState(false)
  const [catalogDropped, setCatalogDropped] = useState(0)
  const [toast, setToast] = useState<'install' | 'uninstall' | null>(null)
  const [pending, setPending] = useState<
    (HostedApp & { size: number; token?: string; bytes?: Uint8Array }) | null
  >(null)
  const [hosted, setHosted] = useState<HostedApp[]>([])
  const [hostedLoaded, setHostedLoaded] = useState(false)
  const [detail, setDetail] = useState<{ entry: CatalogEntry; app?: HostedApp } | null>(null)
  const [removing, setRemoving] = useState<HostedApp | null>(null)
  const [swipe, setSwipe] = useState(0)
  const fileRef = useRef<HTMLInputElement>(null)
  const requestRef = useRef<AbortController | null>(null)
  const cancelledRef = useRef(false)
  const listRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ y: number } | null>(null)
  const switching = useRef(false)

  useEffect(() => pushBackHandler(onClose), [onClose])

  useEffect(
    () =>
      onInstallProgress(({ received, total }) => {
        setPct(total > 0 ? Math.min(100, Math.round((received / total) * 100)) : null)
      }),
    [],
  )

  // 安装页内部也按“层层返回”：确认弹窗 → 链接/本地表单 → 应用列表
  useEffect(() => {
    if (mode === 'list' && !pending) return
    return pushBackHandler(() => {
      if (pending) {
        setPending(null)
        setMode('list')
      } else if (mode === 'local') setMode('list')
    })
  }, [mode, pending])

  // 同 id 覆盖确认：返回键先关弹窗，不退出安装页
  useEffect(() => {
    if (!overwrite) return
    return pushBackHandler(() => setOverwrite(null))
  }, [overwrite])

  const hourKey = () => {
    const now = new Date()
    return `${now.getFullYear()}${now.getMonth() + 1}${now.getDate()}-${now.getHours()}`
  }

  const fetchList = async (options: { skeleton: boolean; force: boolean; manual: boolean }) => {
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    let timedOut = false
    if (options.skeleton) setListState('loading')
    setSlow(false)
    const slowTimer = window.setTimeout(() => setSlow(true), 2000)
    const timeoutTimer = window.setTimeout(() => {
      timedOut = true
      controller.abort()
    }, 3500)
    try {
      const list = await loadCatalog({ force: options.force, signal: controller.signal })
      window.clearTimeout(slowTimer)
      window.clearTimeout(timeoutTimer)
      setCatalog(list)
      const dropped = getCatalogDropped()
      setCatalogDropped(dropped)
      logDiag('catalog-ok', `${list.length} apps / ${dropped} dropped`)
      setCatalogReady(true)
      setListState('ready')
      setSlow(false)
      if (options.manual) localStorage.setItem('docklet.catalog.hour', hourKey())
    } catch {
      window.clearTimeout(slowTimer)
      window.clearTimeout(timeoutTimer)
      if (controller.signal.aborted && !timedOut) return
      setSlow(false)
      logDiag('catalog-fail')
      setListState('error')
    }
  }

  useEffect(() => {
    const cached = readCachedCatalog()
    if (cached.length > 0) {
      setCatalog(cached)
      setCatalogReady(true)
      setListState('ready')
    }
    void fetchList({ skeleton: cached.length === 0, force: false, manual: false })
    return () => requestRef.current?.abort()
  }, [])

  const manualRefresh = () => {
    if (localStorage.getItem('docklet.catalog.hour') === hourKey()) {
      setListState('loading')
      window.setTimeout(() => setListState('ready'), 800)
      return
    }
    void fetchList({ skeleton: true, force: true, manual: true })
  }

  useEffect(() => {
    listHostedApps()
      .then((list) => {
        setHosted(list)
        setHostedLoaded(true)
      })
      .catch(() => {
        setHosted([])
        setHostedLoaded(true)
      })
  }, [])

  // 从应用返回时，恢复到之前打开的详情页
  useEffect(() => {
    if (!initialDetailId || !hostedLoaded) return
    const entry = catalog.find((item) => item.id === initialDetailId)
    if (entry) {
      setDetail({ entry, app: hosted.find((app) => app.id === initialDetailId) })
    }
    onDetailConsumed?.()
  }, [initialDetailId, hostedLoaded, hosted, catalog, onDetailConsumed])

  const categories = [
    RECOMMENDED_CATEGORY,
    ...Array.from(new Set(catalog.map((entry) => entry.category))),
  ]
  const keyword = query.trim().toLowerCase()
  const list = keyword.trim()
    ? filterCatalog(catalog, keyword)
    : catalog.filter((entry) =>
        category === RECOMMENDED_CATEGORY ? entry.recommended === true : entry.category === category,
      )

  useEffect(() => {
    if (!query.trim()) return
    const onDown = (event: PointerEvent) => {
      const target = event.target as HTMLElement | null
      if (target?.closest('.install-search') || target?.closest('[data-search]')) return
      const active = document.activeElement as HTMLElement | null
      if (active?.closest('.install-search')) {
        active.blur()
        return
      }
      setQuery('')
    }
    document.addEventListener('pointerdown', onDown, true)
    return () => document.removeEventListener('pointerdown', onDown, true)
  }, [query])

  const switchCategory = (delta: number) => {
    const index = categories.indexOf(category)
    const next = categories[(index + delta + categories.length) % categories.length]
    setCategory(next)
    setQuery('')
  }

  const onSwipeStart = (event: React.TouchEvent) => {
    if (switching.current || keyword) return
    drag.current = { y: event.touches[0].clientY }
    setSwipe(0)
  }

  const onSwipeMove = (event: React.TouchEvent) => {
    if (!drag.current) return
    const dy = event.touches[0].clientY - drag.current.y
    const container = listRef.current
    const atTop = !container || container.scrollTop <= 0
    const atBottom = container
      ? container.scrollTop + container.clientHeight >= container.scrollHeight - 1
      : true
    if ((dy < 0 && !atBottom) || (dy > 0 && !atTop)) {
      setSwipe(0)
      return
    }
    setSwipe(Math.min(1, Math.abs(dy) / 80))
  }

  const onSwipeEnd = (event: React.TouchEvent) => {
    if (!drag.current) return
    const dy = event.changedTouches[0].clientY - drag.current.y
    drag.current = null
    if (Math.abs(dy) < 56) {
      setSwipe(0)
      return
    }
    setSwipe(1)
    switching.current = true
    window.setTimeout(() => {
      switchCategory(dy < 0 ? 1 : -1)
      setSwipe(0)
      switching.current = false
    }, 130)
  }

  const prepare = async () => {
    if (!url.trim() || busy) return
    setBusy(true)
    setPct(null)
    setMessage('')
    try {
      setPending(await inspectFromUrl(url.trim()))
    } catch (error) {
      setMessage(explain(error))
    } finally {
      setBusy(false)
    }
  }

  const performPendingInstall = async () => {
    if (!pending) return
    const target = url.trim()
    setBusy(true)
    setPct(null)
    setMessage('')
    try {
      if (pending.bytes) await installFromBytes(pending.bytes)
      else if (pending.token) await installFromToken(pending.token)
      else await installFromUrl(target)
      await showInstalled(pending)
    } catch (error) {
      setMessage(explain(error))
      setPending(null)
    } finally {
      setBusy(false)
    }
  }

  const confirm = async () => {
    if (!pending) return
    const current = hosted.find((app) => app.id === pending.id)
    if (current) {
      setOverwrite({
        id: pending.id,
        name: pending.name,
        from: current.version,
        to: pending.version,
        downgrade: !isNewer(pending.version, current.version) && pending.version !== current.version,
        action: performPendingInstall,
      })
      return
    }
    await performPendingInstall()
  }

  const pickFile = async (file: File) => {
    setBusy(true)
    setPct(null)
    setMessage('')
    try {
      const bytes = new Uint8Array(await file.arrayBuffer())
      const info = await inspectFromBytes(bytes)
      setPending({ ...info, bytes })
    } catch (error) {
      setMessage(explain(error))
    } finally {
      setBusy(false)
    }
  }

  const showInstalled = async (info: HostedApp & { size?: number; url?: string }) => {
    const list = await listHostedApps()
    setHosted(list)
    setHostedLoaded(true)
    setPending(null)
    setMode('list')
    setDetail({
      entry: {
        id: info.id,
        name: info.name,
        icon: info.icon || '▢',
        category: '其他',
        tagline: info.description ?? '',
        version: info.version,
        capabilities: info.capabilities ?? [],
        permissions: info.permissions ?? [],
        url: info.url,
        size: info.size,
      },
      app: list.find((item) => item.id === info.id),
    })
    markAppActive(info.id)
    onInstalled()
    setToast('install')
    window.setTimeout(() => setToast(null), 2200)
  }

  const runCatalogInstall = async (
    entry: CatalogEntry,
    info: HostedApp & { token?: string },
  ) => {
    cancelledRef.current = false
    setInstalling(true)
    setPct(null)
    setInstallFailed(false)
    setWarn('')
    setMessage('')
    try {
      await installFromToken(info.token ?? '')
      logDiag('install-ok', `${info.id} ${info.version}`)
      await showInstalled({ ...info, url: entry.url })
    } catch (error) {
      if (cancelledRef.current) {
        setInstalling(false)
        setPct(null)
        return
      }
      const reason = explain(error)
      const short = /网络|HTTP|超时|TIMEOUT|Abort/i.test(reason)
        ? '网络异常'
        : /空间|ENOSPC|NO_SPACE|DISK/i.test(reason)
          ? '空间不足'
          : /签名|SIGNATURE|VERIFY/i.test(reason)
            ? '签名异常'
            : /版本/.test(reason)
              ? '版本较低'
              : /地址/.test(reason)
                ? '地址无效'
                : /zip|安装包|ENTRY|DOWNLOAD|UNZIP/i.test(reason)
                  ? '安装包异常'
                  : '获取失败'
      logDiag('install-fail', `${info.id} ${reason}`)
      setInstallFailed(true)
      setWarn(short === '获取失败' ? '安装失败 原因未知' : `获取失败：${short}`)
      window.setTimeout(() => setWarn(''), 3000)
    } finally {
      setInstalling(false)
      setPct(null)
    }
  }

  const installCatalog = async (entry: CatalogEntry) => {
    const target = entry.url ?? ''
    if (!target) return
    setMessage('')
    try {
      const info = await inspectFromUrl(target)
      if (info.id !== entry.id) throw new Error('ID_MISMATCH')
      const current = hosted.find((app) => app.id === entry.id)
      if (current && !isNewer(entry.version, current.version)) {
        setOverwrite({
          id: info.id,
          name: info.name,
          from: current.version,
          to: entry.version,
          downgrade: !isNewer(entry.version, current.version) && entry.version !== current.version,
          action: () => runCatalogInstall(entry, info),
        })
        return
      }
      await runCatalogInstall(entry, info)
    } catch (error) {
      const reason = explain(error)
      setInstallFailed(true)
      setWarn(reason === 'ID_MISMATCH' ? '应用 ID 与目录不一致' : '获取失败')
      window.setTimeout(() => setWarn(''), 3000)
    }
  }

  const renderConfirm = () => {
    if (!pending) return null
    return (
        <div className="install-confirm">
          <h3>安装「{pending.name}」？</h3>
          <p className="install-sub">
            v{pending.version} · {formatSize(pending.size)}
          </p>
          {pending.permissions.length > 0 ? (
            <ul className="perm-list">
              {pending.permissions.map((permission) => (
                <li key={permission.name}>
                  <b>{permission.name}</b>
                  <span>{permission.reason}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="install-note">该应用不请求系统权限。</p>
          )}
          <button type="button" className="shell-primary wide" disabled={busy} onClick={confirm}>
            {busy ? `安装中…${pct === null ? '' : ` ${pct}%`}` : '安装'}
          </button>
          <button
            type="button"
            className="install-ghost"
            onClick={() => {
              if (busy) {
                void cancelInstall()
                return
              }
              setPending(null)
              setMode('list')
            }}
          >
            {busy ? '取消安装' : '取消'}
          </button>
        </div>
      )
  }

  const renderLocal = () => {
    if (pending) return renderConfirm()
    return (
      <div className="install-form">
        {message && <p className="install-error install-error-top">{message}</p>}
        <label className="install-label">安装包链接</label>
        <input
          className="sheet-input"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          placeholder="https://…/app.zip"
          inputMode="url"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
        />
        <button type="button" className="shell-primary wide" disabled={busy} onClick={prepare}>
          {busy ? '检查中…' : '检查并安装'}
        </button>

        <div className="install-divider">
          <span>或者</span>
        </div>

        <input
          ref={fileRef}
          type="file"
          accept=".zip,application/zip"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (file) void pickFile(file)
          }}
        />
        <button type="button" className="install-file" disabled={busy} onClick={() => fileRef.current?.click()}>
          {busy ? '安装中…' : '选择本地 zip 文件'}
        </button>

        <p className="install-note">zip 需包含 app.json 与入口文件；安装后代码只读，数据写入 data/&lt;id&gt;/。</p>
      </div>
    )
  }

  return (
    <div className="install">
      <header className="install-top">
        <button type="button" className="nav-back" aria-label="返回" onClick={onClose}>
          ‹
        </button>
        <b>安装应用</b>
        <button type="button" className="install-import" onClick={() => setMode(mode === 'local' ? 'list' : 'local')}>
          {mode === 'local' ? '列表' : '导入'}
        </button>
      </header>

      {mode === 'list' ? (
        <div className="install-main">
          <aside className="install-rail" aria-label="安装分类">
            {categories.map((item) => (
              <button
                key={item}
                type="button"
                className={`install-cat${item === category ? ' on' : ''}`}
                onClick={() => {
                  setCategory(item)
                  setQuery('')
                }}
              >
                {item}
                {item === category && (
                  <span className="install-cat-track">
                    <span style={{ width: `${Math.round(swipe * 100)}%` }} />
                  </span>
                )}
              </button>
            ))}
            <button type="button" className="install-rail-add" onClick={manualRefresh}>
              刷新
            </button>
          </aside>

          <div
            className="install-pane"
            onTouchStart={onSwipeStart}
            onTouchMove={onSwipeMove}
            onTouchEnd={onSwipeEnd}
            onTouchCancel={() => {
              drag.current = null
              setSwipe(0)
            }}
          >
            <div className="install-search">
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索应用"
                aria-label="搜索应用"
                type="search"
                autoCapitalize="off"
                autoCorrect="off"
                spellCheck={false}
              />
            </div>

            {slow && listState === 'loading' && (
              <p className="install-slow" role="status">加载较慢，请稍后</p>
            )}
            {catalogDropped > 0 && listState === 'ready' && (
              <p className="install-slow" role="status">
                有 {catalogDropped} 个应用数据异常，已忽略
              </p>
            )}
            {listState === 'loading' ? (
              <div className="catalog-list">
                {Array.from({ length: 4 }, (_, index) => (
                  <div key={index} className="catalog-skeleton" />
                ))}
              </div>
            ) : listState === 'error' ? (
              <div className="install-list-error">
                <b>列表获取失败，请联系作者。</b>
                <button type="button" className="shell-primary" onClick={() => void fetchList({ skeleton: true, force: true, manual: false })}>
                  重试
                </button>
              </div>
            ) : (
          <div className="catalog-list" ref={listRef} data-search>
              {list.map((entry) => {
                const current = hosted.find((app) => app.id === entry.id)
                const canUpdate = Boolean(current && entry.url && isNewer(entry.version, current.version))
                return (
                  <AppRow
                    key={entry.id}
                    className="catalog-card"
                    icon={entry.icon}
                    name={entry.name}
                    tagline={entry.tagline}
                    trailing={
                      current ? (
                        <span className={`catalog-state${canUpdate ? ' is-update' : ''}`}>
                          {canUpdate ? '可更新' : '已安装'}
                        </span>
                      ) : null
                    }
                    onClick={() => {
                      setQuery('')
                      setDetail({ entry, app: current })
                    }}
                  />
                )
              })}
              {list.length === 0 && <p className="pane-empty">没有匹配的应用</p>}
            </div>
            )}
          </div>
        </div>
      ) : (
        <div className="install-body">{renderLocal()}</div>
      )}

      {detail && (
        <AppDetailPage
          entry={detail.entry}
          app={detail.app}
          onClose={() => setDetail(null)}
          onOpen={() => {
            const app = detail.app
            setToast(null)
            setDetail(null)
            if (app) onOpen?.(app.id)
          }}
          onRemove={
            detail.app
              ? () => {
                  setRemoving(detail.app ?? null)
                }
              : undefined
          }
          installing={installing}
          installFailed={installFailed}
          onCancel={() => {
            cancelledRef.current = true
            void cancelInstall()
            setInstalling(false)
            setPct(null)
            setInstallFailed(false)
            setWarn('')
          }}
          installProgress={pct}
          catalogLoading={!catalogReady}
          onInstall={detail.entry.url ? () => void installCatalog(detail.entry) : undefined}
        />
      )}

      {overwrite && (
        <div className="install-modal">
          <div className="sheet-scrim" role="dialog" aria-modal="true">
            <div className="sheet">
              <div className="sheet-handle" />
              <div className="update-head">
                <b>{overwrite.downgrade ? '发现应用版本降级' : '已安装相同的文件路径'}</b>
              </div>
              {overwrite.downgrade ? (
                <p className="update-notes">
                  当前版本：{overwrite.from}
                  <br />
                  目标版本：{overwrite.to}
                  <br />
                  <br />
                  降级版本可能造成应用异常等未知风险。
                  <br />
                  可以先安装，查看有无异常。
                  <br />
                  <br />
                  如有异常，可以执行以下步骤：
                  <br />
                  1. 安装回最新版本
                  <br />
                  2. 打开应用，执行“清除全部数据 / 恢复初始化”
                  <br />
                  （是否有该入口，由各应用自身设计决定）
                  <br />
                  3. 卸载应用
                  <br />
                  4. 安装降级版本
                  <br />
                  5. 安装后如仍有异常，可再次执行“清除全部数据 / 恢复初始化”
                  <br />
                  <br />
                  清除、卸载和降级都可能导致配置和数据丢失，
                  <br />
                  请自行记录、备份必要信息。
                </p>
              ) : (
                <p className="update-notes">
                  apps/{overwrite.id}/
                  <br />
                  「{overwrite.name}」已存在，继续安装将会覆盖原应用。
                </p>
              )}
              <div className="sheet-actions">
                <button type="button" className="sheet-ghost" onClick={() => setOverwrite(null)}>
                  取消
                </button>
                <button
                  type="button"
                  className="sheet-primary"
                  onClick={async () => {
                    const target = overwrite
                    setOverwrite(null)
                    try {
                      if (target.downgrade) await removeHostedApp(target.id)
                      await target.action()
                    } catch (error) {
                      setMessage(explain(error))
                    }
                  }}
                >
                  {overwrite.downgrade ? '继续' : '继续安装'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {warn && (
        <div className="install-done install-warn" role="status" aria-live="polite">
          <span className="install-done-icon">
            <img src="/icons/bee/icon-rounded.png" alt="" />
          </span>
          <span className="install-done-text">{warn}</span>
        </div>
      )}

      {toast && (
        <div
          className={`install-done${toast === 'uninstall' ? ' is-uninstall' : ''}`}
          role="status"
          aria-live="polite"
        >
          <span className="install-done-icon">
            <img src="/icons/bee/icon-rounded.png" alt="" />
          </span>
          <span className="install-done-text">
            {toast === 'uninstall' ? '应用卸载成功了！' : '应用安装成功了！'}
          </span>
        </div>
      )}

      {pending && mode === 'list' && (
        <div className="install-modal">{renderConfirm()}</div>
      )}

      {removing && (
        <div className="install-modal">
        <UninstallConfirm
          name={removing.name}
          onCancel={() => setRemoving(null)}
          onConfirm={async () => {
            try {
              const removed = removing
              await removeHostedApp(removed.id)
              const list = await listHostedApps()
              setHosted(list)
              onInstalled()
              setRemoving(null)
              setToast('uninstall')
              window.setTimeout(() => setToast(null), 2200)
              setDetail({
                entry: catalog.find((item) => item.id === removed.id) ?? {
                  id: removed.id,
                  name: removed.name,
                  icon: removed.icon || '▢',
                  category: '其他',
                  tagline: removed.description ?? '',
                  version: removed.version,
                  capabilities: removed.capabilities ?? [],
                  permissions: removed.permissions ?? [],
                },
              })
            } catch (error) {
              setMessage(`删除失败：${explain(error)}`)
              setRemoving(null)
            }
          }}
        />
        </div>
      )}
    </div>
  )
}
