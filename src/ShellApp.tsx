import { useEffect, useRef, useState, type TouchEvent as ReactTouchEvent } from 'react'
import { Capacitor } from '@capacitor/core'
import { AboutPage } from './components/AboutPage'
import { AppDetailPage } from './components/AppDetailPage'
import { AppSheetHead } from './components/AppSheetHead'
import { HostedAppFrame } from './components/HostedAppFrame'
import { InstallPage } from './components/InstallPage'
import { AppRow } from './components/AppRow'
import { UpdateSheet } from './components/UpdateSheet'
import { DiagnosticsPage } from './components/DiagnosticsPage'
import { Icon } from './components/Icons'
import {
  appNotifications,
  appOpenNotificationSettings,
  appOnPermissionRequest,
  appPermissionStatus,
  appRequestNotifications,
  listHostedApps,
  pinAppShortcut,
  removeHostedApp,
  setAppNotification,
  type AppNotifyEntry,
  type HostedApp,
} from './native/appHost'
import { pushBackHandler, triggerBack } from './back'
import { App as NativeApp } from '@capacitor/app'
import { diagEnabled, logDiag, setDiagEnabled } from './diag'
import { useHostBridge } from './native/useHostBridge'
import { refreshHostBridge } from './native/hostBridge'
import { consumeOpenApp } from './native/phoneRelay'
import { isNewer } from './format'
import { loadCatalog, readCachedCatalog, type CatalogEntry } from './catalog'
import { matchesApp, searchHome, type SearchItem } from './search'
import {
  DEFAULT_CATEGORY,
  DEFAULT_NAME,
  getAppCategory,
  listCategories,
  readOrder,
  saveCategories,
  saveOrder,
  setAppCategory,
  sortByOrder,
  type Category,
} from './categories'

type SheetKind =
  | { kind: 'menu'; app: HostedApp }
  | { kind: 'category'; app: HostedApp }
  | { kind: 'sort'; app: HostedApp }
  | { kind: 'detail'; app: HostedApp }
  | { kind: 'remove'; app: HostedApp }
  | { kind: 'categories' }
  | null

const LONG_PRESS = 650
import { FIRST_SEEN_PREFIX, LAST_ACTIVE_PREFIX, markAppActive, readStamp, writeStamp } from './usage'
const DEFAULT_META_KEY = 'docklet.default.meta'

type DefaultMeta = { name: string; icon?: string }


function readDefaultMeta(): DefaultMeta {
  try {
    const raw = localStorage.getItem(DEFAULT_META_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as { name?: string; icon?: string }
      return {
        name: typeof parsed.name === 'string' && parsed.name.trim() ? parsed.name : DEFAULT_NAME,
        // 旧版本把 inbox 当默认图标存过，视为未设置
        icon: typeof parsed.icon === 'string' && parsed.icon !== 'inbox' ? parsed.icon : undefined,
      }
    }
  } catch {
    /* 忽略 */
  }
  return { name: DEFAULT_NAME }
}

function saveDefaultMeta(meta: DefaultMeta): void {
  try {
    localStorage.setItem(DEFAULT_META_KEY, JSON.stringify(meta))
  } catch {
    /* 忽略 */
  }
}
import {
  clearDeviceCode,
  hasDeviceCode,
  isDeviceCodeEnabled,
  isUninstallCheckEnabled,
  setDeviceCode,
  setDeviceCodeEnabled,
  setUninstallCheckEnabled,
  verifyDeviceCode,
} from './deviceCode'
import { UninstallConfirm } from './components/UninstallConfirm'
import { CategoryIcon, ICON_NAMES } from './icons'
import { isLargeText, setLargeText } from './uiPrefs'

export function ShellApp() {
  useHostBridge()

  const [apps, setApps] = useState<HostedApp[]>([])
  const [categories, setCategories] = useState<Category[]>(listCategories)
  const [selected, setSelected] = useState(DEFAULT_CATEGORY)
  const [openApp, setOpenApp] = useState<HostedApp | null>(null)
  const [restoreAppId, setRestoreAppId] = useState<string | null>(() => localStorage.getItem('docklet.openApp'))
  const [sheet, setSheet] = useState<SheetKind>(null)
  const [settings, setSettings] = useState(false)
  const [settingsBasic, setSettingsBasic] = useState(false)
  const [largeText, setLargeTextState] = useState(isLargeText)
  const [hasCode, setHasCode] = useState(hasDeviceCode)
  const [codeOnStart, setCodeOnStart] = useState(isDeviceCodeEnabled)
  const [uninstallCheck, setUninstallCheck] = useState(isUninstallCheckEnabled)
  const [codeDialog, setCodeDialog] = useState<'set' | 'modify' | 'clear' | null>(null)
  const [oldCode, setOldCode] = useState('')
  const [newCodeValue, setNewCodeValue] = useState('')
  const [confirmCodeValue, setConfirmCodeValue] = useState('')
  const [codeError, setCodeError] = useState('')
  const [codeEditing, setCodeEditing] = useState(false)
  const [about, setAbout] = useState(false)
  const [installOpen, setInstallOpen] = useState(false)
  const [returnDetailId, setReturnDetailId] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [catalog, setCatalog] = useState<CatalogEntry[]>(() => readCachedCatalog())
  const [catalogFailed, setCatalogFailed] = useState(false)
  const [updateInfo, setUpdateInfo] = useState<{ versionName: string; notes?: string; apk: string } | null>(null)
  const [permissionAsk, setPermissionAsk] = useState<{ name: string; reason: string } | null>(null)
  const [online, setOnline] = useState(() => navigator.onLine)
  const [diagOn, setDiagOn] = useState(() => diagEnabled())
  const [diagOpen, setDiagOpen] = useState(false)
  const [toast, setToast] = useState('')
  const [notificationsGranted, setNotificationsGranted] = useState<boolean | null>(null)
  const [appNotifies, setAppNotifies] = useState<AppNotifyEntry[]>([])
  const [newName, setNewName] = useState('')
  const [addingCategory, setAddingCategory] = useState(false)
  const [iconEditing, setIconEditing] = useState<string | null>(null)
  const [sortActive, setSortActive] = useState<string | null>(null)
  const [pendingCategory, setPendingCategory] = useState<string | null>(null)
  const [catEditId, setCatEditId] = useState<string | null>(null)
  const [catEditName, setCatEditName] = useState('')
  const [catError, setCatError] = useState('')
  const [defaultEditing, setDefaultEditing] = useState(false)
  const [defaultEditName, setDefaultEditName] = useState('')
  const rowLocked = Boolean(catEditId) || addingCategory || defaultEditing
  const [newIcon, setNewIcon] = useState<string | undefined>(undefined)
  const [swipeProgress, setSwipeProgress] = useState(0)
  const [query, setQuery] = useState('')
  const [defaultMeta, setDefaultMeta] = useState<DefaultMeta>(readDefaultMeta)

  const paneRef = useRef<HTMLDivElement>(null)
  const gesture = useRef<{ x: number; y: number; active: boolean } | null>(null)
  const switching = useRef(false)
  const holdTimer = useRef<number | null>(null)
  const suppressClick = useRef(false)
  const menuOpenedAt = useRef(0)
  const categoriesOpenedAt = useRef(0)

  const showToast = (message: string) => {
    setToast(message)
    window.setTimeout(() => setToast(''), 2400)
  }

  const refresh = () => {
    listHostedApps()
      .then((list) => {
        setApps(list)
        setLoaded(true)
        setLoadError('')
        void refreshHostBridge()
      })
      .catch(() => {
        setLoaded(true)
        setLoadError('应用列表读取失败')
      })
  }

  useEffect(refresh, [])

  const pinShortcut = async (app: HostedApp) => {
    setSheet(null)
    try {
      const requested = await pinAppShortcut(app)
      if (!requested) showToast('当前桌面不支持快捷方式')
    } catch (error) {
      const message = String((error as Error)?.message || '')
      if (message.includes('NOT_NATIVE')) showToast('仅 Android 支持桌面快捷方式')
      else if (message.includes('UNSUPPORTED')) showToast('当前桌面不支持快捷方式')
      else showToast('添加失败，请重试')
    }
  }

  useEffect(() => {
    if (loaded) window.__dockletReady?.()
  }, [loaded])

  // 记住最后打开的页面：重建后恢复；关闭应用时清除
  useEffect(() => {
    if (!loaded || !restoreAppId) return
    if (!openApp) {
      const target = apps.find((app) => app.id === restoreAppId)
      if (target) setOpenApp(target)
    }
    // 只恢复一次，避免关闭后被立即重新打开
    setRestoreAppId(null)
  }, [apps, loaded, openApp, restoreAppId])

  useEffect(() => {
    if (!loaded) return
    if (openApp) localStorage.setItem('docklet.openApp', openApp.id)
    else localStorage.removeItem('docklet.openApp')
  }, [openApp, loaded])

  // 原生返回键通过这个全局函数询问 JS 是否有可关闭的浮层。
  useEffect(() => {
    const host = window as unknown as { __dockletBack?: () => boolean }
    host.__dockletBack = () => triggerBack()
    return () => {
      delete host.__dockletBack
    }
  }, [])

  // 沉浸式：内容画到状态栏后面，insets 由原生注入。
  // 状态栏本身在 main.tsx 的 installSystemBars() 里统一设置（锁屏也要生效）。
  // 键盘高度只由 keyboardInset.ts 写 --shell-kb / --kb-inset，这里不再重复监听。

  useEffect(() => {
    if (!settings || !Capacitor.isNativePlatform()) return
    appPermissionStatus()
      .then((status) => setNotificationsGranted(status.notifications))
      .catch(() => setNotificationsGranted(null))
    void appNotifications().then(setAppNotifies).catch(() => undefined)
  }, [settings])

  // 返回键：先关最上层，回到首页后再按才退出应用。
  useEffect(() => {
    if (!sheet) return
    return pushBackHandler(() => setSheet(null))
  }, [sheet])

  useEffect(() => {
    if (!settings) return
    return pushBackHandler(() => setSettings(false))
  }, [settings])

  useEffect(() => {
    if (!settings) setSettingsBasic(false)
  }, [settings])

  useEffect(() => {
    if (!settingsBasic) return
    return pushBackHandler(() => setSettingsBasic(false))
  }, [settingsBasic])

  useEffect(() => {
    if (!about) return
    return pushBackHandler(() => setAbout(false))
  }, [about])

  useEffect(() => {
    if (!codeDialog) return
    return pushBackHandler(() => {
      setCodeDialog(null)
      setOldCode('')
      setNewCodeValue('')
      setConfirmCodeValue('')
      setCodeError('')
      setCodeEditing(false)
    })
  }, [codeDialog])

  // 通知点击：原生派发事件；冷启动/锁屏则从原生 pending 里取
  const [pendingOpenId, setPendingOpenId] = useState<string | null>(null)
  const consumedPending = useRef(false)

  useEffect(() => {
    if (!loaded) return
    const today = new Date().toISOString().slice(0, 10)
    try {
      if (localStorage.getItem('docklet.updateCheck') === today) return
      localStorage.setItem('docklet.updateCheck', today)
    } catch {
      return
    }
    void (async () => {
      try {
        const [response, info] = await Promise.all([
          fetch('https://docklet.cn/version.json', { cache: 'no-store' }),
          NativeApp.getInfo(),
        ])
        if (!response.ok) return
        const remote = (await response.json()) as {
          versionName?: string
          versionCode?: number
          apk?: string
          notes?: string
        }
        if (!remote.versionName || !remote.apk) return
        if ((remote.versionCode ?? 0) <= Number(info.build ?? 0)) return
        if (localStorage.getItem('docklet.skipVersion') === remote.versionName) return
        logDiag('update-available', remote.versionName)
        setUpdateInfo({
          versionName: remote.versionName,
          notes: remote.notes,
          apk: remote.apk,
        })
      } catch {
        /* 静默失败 */
      }
    })()
  }, [loaded])

  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])

  useEffect(() => {
    let handle: { remove: () => Promise<void> } | null = null
    void appOnPermissionRequest((data) => {
      logDiag('permission-request', data.appId)
      setPermissionAsk({ name: data.name || data.appId, reason: data.reason || '需要发送通知' })
    }).then((subscription) => {
      handle = subscription
    })
    return () => {
      void handle?.remove()
    }
  }, [])

  useEffect(() => {
    void loadCatalog()
      .then((list) => {
        setCatalog(list)
        setCatalogFailed(false)
      })
      .catch(() => setCatalogFailed(true))
  }, [])

  useEffect(() => {
    if (!loaded || consumedPending.current) return
    consumedPending.current = true
    void consumeOpenApp().then((id) => {
      if (id) setPendingOpenId(id)
    })
  }, [loaded])

  useEffect(() => {
    if (!pendingOpenId || !loaded) return
    const target = apps.find((app) => app.id === pendingOpenId)
    if (target) setOpenApp(target)
    setPendingOpenId(null)
  }, [pendingOpenId, apps, loaded])

  useEffect(() => {
    const onOpen = (event: Event) => {
      const detail = (event as CustomEvent<{ id?: string }>).detail
      setPendingOpenId(detail?.id || '')
      // 事件已送达，清掉原生 pending，避免下次冷启动重复打开
      void consumeOpenApp()
    }
    window.addEventListener('docklet:open-app', onOpen)
    return () => window.removeEventListener('docklet:open-app', onOpen)
  }, [])

  useEffect(() => {
    if (!query) return
    const onDown = (event: PointerEvent) => {
      const target = event.target as HTMLElement | null
      if (target?.closest('.shell-search') || target?.closest('[data-search]')) return
      const active = document.activeElement as HTMLElement | null
      if (active?.closest('.shell-search')) {
        active.blur()
        return
      }
      setQuery('')
    }
    document.addEventListener('pointerdown', onDown, true)
    return () => document.removeEventListener('pointerdown', onDown, true)
  }, [query])

  const updateDefaultMeta = (patch: Partial<DefaultMeta>) => {
    setDefaultMeta((prev) => {
      const next = { ...prev, ...patch }
      saveDefaultMeta(next)
      return next
    })
  }

  // 首次见到某个应用时记下时间，作为“新装应用”排序依据。
  useEffect(() => {
    for (const app of apps) {
      if (!readStamp(FIRST_SEEN_PREFIX + app.id)) writeStamp(FIRST_SEEN_PREFIX + app.id, Date.now())
    }
  }, [apps])

  const railItems: Array<{ id: string; name: string; icon?: string }> = [
    { id: DEFAULT_CATEGORY, name: defaultMeta.name, icon: defaultMeta.icon },
  ]
  for (const category of categories) railItems.push({ id: category.id, name: category.name, icon: category.icon })

  const activeId = railItems.some((item) => item.id === selected)
    ? selected
    : (railItems[0]?.id ?? DEFAULT_CATEGORY)

  const isDefault = activeId === DEFAULT_CATEGORY

  // 默认分类是分段视图：新装应用 + 最近使用 + 还未分类
  const keyword = query.trim().toLowerCase()
  const catalogById = new Map(catalog.map((entry) => [entry.id, entry]))
  const matches = (app: HostedApp) => matchesApp(app, catalogById.get(app.id), keyword)
  const searchItems: SearchItem[] = searchHome(apps, catalog, keyword)
  const recentUsed = apps
    .filter((app) => matches(app) && readStamp(LAST_ACTIVE_PREFIX + app.id) > 0)
    .sort((a, b) => readStamp(LAST_ACTIVE_PREFIX + b.id) - readStamp(LAST_ACTIVE_PREFIX + a.id))
    .slice(0, 6)
  const unclassified = sortByOrder(
    apps.filter((app) => matches(app) && getAppCategory(app.id) === DEFAULT_CATEGORY),
    DEFAULT_CATEGORY,
  )
  const defaultSections = [
    { key: 'recent', title: '最近使用', apps: recentUsed },
    { key: 'unclassified', title: '还未分类', apps: unclassified },
  ].filter((section) => section.apps.length > 0)

  const paneApps = keyword
    ? apps.filter(matches)
    : isDefault
      ? [...new Map(defaultSections.flatMap((s) => s.apps).map((app) => [app.id, app])).values()]
      : sortByOrder(
          apps.filter((app) => matches(app) && getAppCategory(app.id) === activeId),
          activeId,
        )

  // 排序面板用未过滤的完整分类列表，避免搜索时只写回命中的子集
  const sortApps = isDefault
    ? sortByOrder(
        apps.filter((app) => getAppCategory(app.id) === DEFAULT_CATEGORY),
        DEFAULT_CATEGORY,
      )
    : sortByOrder(
        apps.filter((app) => getAppCategory(app.id) === activeId),
        activeId,
      )

  const switchCategory = (delta: number) => {
    const index = railItems.findIndex((item) => item.id === activeId)
    if (index < 0) return
    // 循环切换，最后一页继续滑回到第一页。
    const next = railItems[(index + delta + railItems.length) % railItems.length]
    if (next) setSelected(next.id)
  }

  const onTouchStart = (event: ReactTouchEvent) => {
    if (switching.current || keyword) return
    const target = event.target as HTMLElement | null
    // 搜索框和标题行不参与滑动
    if (target && target.closest('.shell-search, .pane-head, input, textarea')) return
    const touch = event.touches[0]
    gesture.current = { x: touch.clientX, y: touch.clientY, active: true }
    setSwipeProgress(0)
  }

  const onTouchMove = (event: ReactTouchEvent) => {
    const start = gesture.current
    if (!start || !start.active) return
    const touch = event.touches[0]
    const dx = touch.clientX - start.x
    const dy = touch.clientY - start.y
    if (Math.abs(dx) > Math.abs(dy) * 1.2) {
      start.active = false
      setSwipeProgress(0)
      return
    }
    const pane = paneRef.current
    if (!pane) return
    const atTop = pane.scrollTop <= 0
    const atBottom = pane.scrollTop + pane.clientHeight >= pane.scrollHeight - 1
    if ((dy > 0 && !atTop) || (dy < 0 && !atBottom)) {
      setSwipeProgress(0)
      return
    }
    setSwipeProgress(Math.min(1, Math.abs(dy) / 80))
  }

  const onTouchEnd = (event: ReactTouchEvent) => {
    const start = gesture.current
    gesture.current = null
    if (!start) return
    const touch = event.changedTouches[0]
    const dy = touch.clientY - start.y
    if (Math.abs(dy) < 56) {
      setSwipeProgress(0)
      return
    }
    // 先让进度条走满，再切换，避免“名字和进度条突然跳。
    setSwipeProgress(1)
    switching.current = true
    window.setTimeout(() => {
      switchCategory(dy < 0 ? 1 : -1)
      setSwipeProgress(0)
      switching.current = false
    }, 130)
  }

  const openAppItem = (app: HostedApp) => {
    setQuery('')
    if (suppressClick.current) return
    markAppActive(app.id)
    setOpenApp(app)
  }

  const startHold = (app: HostedApp) => () => {
    if (holdTimer.current) window.clearTimeout(holdTimer.current)
    holdTimer.current = window.setTimeout(() => {
      holdTimer.current = null
      suppressClick.current = true
      menuOpenedAt.current = Date.now()
      setSheet({ kind: 'menu', app })
      window.setTimeout(() => {
        suppressClick.current = false
      }, 500)
    }, LONG_PRESS)
  }

  const cancelHold = () => {
    if (holdTimer.current) window.clearTimeout(holdTimer.current)
    holdTimer.current = null
  }

  useEffect(() => () => cancelHold(), [])

  const assign = (app: HostedApp, categoryId: string) => {
    setAppCategory(app.id, categoryId)
    setSheet(null)
    const order = readOrder(categoryId).filter((id) => id !== app.id)
    saveOrder(categoryId, [app.id, ...order])
    // 不跳转：用户通常要连续把多个应用分到不同分类，停留当前分类。
  }

  const addCategory = () => {
    const name = newName.trim()
    if (!name) return false
    if (categories.length >= 10) {
      setCatError('分类数量已满，不可新建')
      return false
    }
    if (categories.some((item) => item.name.trim().toLowerCase() === name.toLowerCase())) {
      setCatError('已有同名分类')
      return false
    }
    const id = `c-${Date.now().toString(36)}`
    const next = [...categories, { id, name, icon: newIcon }]
    setCategories(next)
    saveCategories(next)
    setNewName('')
    setNewIcon(undefined)
    setSelected(id)
    setAddingCategory(false)
    setCatError('')
    return true
  }

  const setCategoryIcon = (category: Category, icon?: string) => {
    const next = categories.map((item) => (item.id === category.id ? { ...item, icon } : item))
    setCategories(next)
    saveCategories(next)
  }

  const renderIconPicker = (targetId: string, current: string | undefined) => (
    <div className="icon-pick">
      <button
        type="button"
        className={`none${current ? '' : ' on'}`}
        onClick={() => {
          if (targetId === 'new') setNewIcon(undefined)
          else if (targetId === 'default') updateDefaultMeta({ icon: undefined })
          else {
            const target = categories.find((item) => item.id === targetId)
            if (target) setCategoryIcon(target, undefined)
          }
          setIconEditing(null)
        }}
      >
        无      </button>
      {ICON_NAMES.map((icon) => (
        <button
          key={icon}
          type="button"
          className={icon === current ? 'on' : ''}
          aria-label={icon}
          onClick={() => {
            if (targetId === 'new') {
              setNewIcon(icon)
            } else if (targetId === 'default') {
              updateDefaultMeta({ icon })
            } else {
              const target = categories.find((item) => item.id === targetId)
              if (target) setCategoryIcon(target, icon)
            }
            setIconEditing(null)
          }}
        >
          <CategoryIcon name={icon} size={16} />
        </button>
      ))}
    </div>
  )

  const renameCategory = (category: Category, name: string) => {
    const trimmed = name.trim()
    if (!trimmed) return true
    if (
      categories.some(
        (item) =>
          item.id !== category.id && item.name.trim().toLowerCase() === trimmed.toLowerCase(),
      )
    ) {
      setCatError('已有同名分类')
      return false
    }
    const next = categories.map((item) => (item.id === category.id ? { ...item, name: trimmed } : item))
    setCategories(next)
    saveCategories(next)
    setCatError('')
    return true
  }

  const moveCategory = (category: Category, delta: number) => {
    const index = categories.findIndex((item) => item.id === category.id)
    const target = index + delta
    if (index < 0 || target < 0 || target >= categories.length) return
    const next = [...categories]
    ;[next[index], next[target]] = [next[target], next[index]]
    setCategories(next)
    saveCategories(next)
  }

  const deleteCategory = (category: Category) => {
    const next = categories.filter((item) => item.id !== category.id)
    setCategories(next)
    saveCategories(next)
    setSelected(DEFAULT_CATEGORY)
  }

  useEffect(() => {
    if (!sheet || sheet.kind !== 'categories') {
      setAddingCategory(false)
      setNewName('')
      setNewIcon(undefined)
      setIconEditing(null)
    }
  }, [sheet])

  const appCanUpdate = (app: HostedApp) => {
    const entry = catalogById.get(app.id)
    return Boolean(entry && isNewer(entry.version, app.version))
  }

  const renderAppRow = (app: HostedApp, keyPrefix = '') => {
    const entry = catalogById.get(app.id)
    return (
      <AppRow
        key={`${keyPrefix}${app.id}`}
        icon={app.icon || '▢'}
        name={app.name}
        tagline={entry?.tagline || app.tagline || `v${app.version}`}
        trailing={appCanUpdate(app) ? <span className="app-update-tag">可更新</span> : null}
        chevron
        onPointerDown={startHold(app)}
        onPointerUp={cancelHold}
        onPointerLeave={cancelHold}
        onPointerCancel={cancelHold}
        onClick={() => openAppItem(app)}
      />
    )
  }

  const renderAppTile = (app: HostedApp, keyPrefix = '') => (
    <button
      key={`${keyPrefix}${app.id}`}
      type="button"
      className="app-tile"
      onPointerDown={startHold(app)}
      onPointerUp={cancelHold}
      onPointerLeave={cancelHold}
      onPointerCancel={cancelHold}
      onClick={() => openAppItem(app)}
    >
      <span className="app-tile-icon" aria-hidden="true">{app.icon || '▢'}</span>
      <span className="app-tile-name">{app.name}</span>
      {appCanUpdate(app) && <span className="app-tile-update">可更新</span>}
    </button>
  )

  useEffect(() => {
    if (!sheet || sheet.kind !== 'sort') setSortActive(null)
  }, [sheet])

  const submitCode = async () => {
    setCodeError('')
    const next = newCodeValue.trim()
    if (codeDialog === 'set' || codeDialog === 'modify') {
      if (next.length < 4) {
        setCodeError('控制码至少 4 位')
        return
      }
      if (next !== confirmCodeValue.trim()) {
        setCodeError('两次输入的控制码不一致')
        return
      }
    }
    if (codeDialog === 'modify' || codeDialog === 'clear') {
      const ok = await verifyDeviceCode(oldCode)
      if (!ok) {
        setCodeError('当前控制码不正确')
        return
      }
    }
    if (codeDialog === 'clear') {
      clearDeviceCode()
      setHasCode(false)
      setCodeOnStart(false)
    } else if (codeDialog === 'modify') {
      await setDeviceCode(next, true)
      setHasCode(true)
    } else if (codeDialog === 'set') {
      await setDeviceCode(next)
      setHasCode(true)
      setCodeOnStart(true)
    }
    setCodeDialog(null)
    setOldCode('')
    setNewCodeValue('')
    setConfirmCodeValue('')
  }

  if (openApp) {
    return <HostedAppFrame app={openApp} onClose={() => setOpenApp(null)} onChanged={refresh} />
  }

  return (
    <div className="shell">
      <header className="shell-top">
        <div className="shell-brand">
          <img className="shell-logo" src="/icons/bee/icon-rounded.png" alt="" />
          <span>
            <b>Docklet</b>
            <i>— 应用小坞</i>
          </span>
        </div>
        <div className="shell-actions">
          <button type="button" className="shell-icon" aria-label="安装应用" onClick={() => setInstallOpen(true)}>
            <svg
              width="23"
              height="23"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M7 18a4.5 4.5 0 0 1-.7-8.95A5.5 5.5 0 0 1 17 8.5a3.8 3.8 0 0 1 .6 7.5" />
              <path d="M12 11v8" />
              <path d="m9 16 3 3 3-3" />
            </svg>
          </button>
          <button type="button" className="shell-icon" aria-label="设置" onClick={() => setSettings(true)}>
            <Icon name="settings" size={19} />
          </button>
        </div>
      </header>

      {!online && (
        <div className="net-banner" role="status" aria-live="polite">
          当前无网络，显示缓存内容
        </div>
      )}

      <main className="shell-main">
        <aside className="rail" aria-label="分类">
          {railItems.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`rail-item${item.id === activeId ? ' on' : ''}`}
              onClick={() => {
                setSelected(item.id)
                setQuery('')
              }}
              onContextMenu={(event) => event.preventDefault()}
            >
              {item.icon && (
                <span className="rail-ico">
                  <CategoryIcon name={item.icon} size={15} />
                </span>
              )}
              <span>{item.name}</span>
              {item.id === activeId && (
                <span className="rail-track">
                  <span className="rail-fill" style={{ width: `${Math.round(swipeProgress * 100)}%` }} />
                </span>
              )}
            </button>
          ))}
          <button
            type="button"
            className="rail-add"
            onClick={() => {
              categoriesOpenedAt.current = Date.now()
              setSheet({ kind: 'categories' })
            }}
          >
            管理分类
          </button>
        </aside>

        <div className="pane-wrap">
          {!loaded ? (
            <div className="pane">
              {Array.from({ length: 5 }, (_, index) => (
                <div key={index} className="row-skeleton" />
              ))}
            </div>
          ) : loadError ? (
            <div className="pane">
              <div className="shell-empty">
                <span className="empty-mark">!</span>
                <b>{loadError}</b>
                <p>应用目录可能暂时不可用，重试不会影响已装应用的数据。</p>
                <button type="button" className="shell-primary" onClick={() => refresh()}>
                  重试
                </button>
              </div>
            </div>
          ) : apps.length === 0 ? (
            <div className="pane">
              <div className="shell-empty">
                <b>坞里还没有应用</b>
                <p>可通过顶部安装按钮进入安装页。</p>
                <button type="button" className="shell-primary" onClick={() => setInstallOpen(true)}>
                  安装应用
                </button>
              </div>
            </div>
          ) : (
            <div
              className="pane"
              ref={paneRef}
              onTouchStart={onTouchStart}
              onTouchMove={onTouchMove}
              onTouchEnd={onTouchEnd}
              onTouchCancel={() => {
                gesture.current = null
                setSwipeProgress(0)
              }}
            >
              <div className="shell-search">
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

              <div className="pane-swipe">
                {keyword ? (
                  <div data-search>
                    {catalogFailed && (
                      <p className="search-hint">
                        未安装应用搜索失败
                        <br />
                        暂只显示已安装应用
                      </p>
                    )}
                  <div className="rows">
                    {searchItems.map((item) =>
                      item.installed && item.app ? (
                        renderAppRow(item.app)
                      ) : (
                        <AppRow
                          key={item.id}
                          icon={item.icon}
                          name={item.name}
                          tagline={item.tagline}
                          trailing={<span className="catalog-state">安装</span>}
                          onClick={() => {
                            setQuery('')
                            setReturnDetailId(item.id)
                            setInstallOpen(true)
                          }}
                        />
                      ),
                    )}
                    {searchItems.length === 0 && <p className="pane-empty">没有找到相关应用</p>}
                  </div>
                  </div>
                ) : paneApps.length === 0 ? (
                  <p className="pane-empty">这个分类还没有应用</p>
                ) : isDefault ? (
                  <div className="default-sections">
                    {defaultSections.map((section) => (
                      <section key={section.key} className="app-section">
                        <h4 className="app-section-title">{section.title}</h4>
                        {section.key === 'unclassified' ? (
                          <div className="rows">{section.apps.map((app) => renderAppRow(app))}</div>
                        ) : (
                          <div className="app-grid">
                            {section.apps.map((app) => renderAppTile(app, `${section.key}-`))}
                          </div>
                        )}
                      </section>
                    ))}
                  </div>
                ) : (
                  <div className="rows">{paneApps.map((app) => renderAppRow(app))}</div>
                )}
              </div>
            </div>
          )}
        </div>
      </main>

      {permissionAsk && (
        <div className="sheet-scrim" role="dialog" aria-modal="true" onClick={() => setPermissionAsk(null)}>
          <div className="sheet" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <div className="update-head">
              <b>允许通知</b>
              <span>{permissionAsk.name}</span>
            </div>
            <p className="update-notes">{permissionAsk.reason}</p>
            <div className="sheet-actions">
              <button type="button" className="sheet-ghost" onClick={() => setPermissionAsk(null)}>
                暂不
              </button>
              <button
                type="button"
                className="sheet-primary"
                onClick={() => {
                  void appRequestNotifications()
                  setPermissionAsk(null)
                }}
              >
                允许
              </button>
            </div>
          </div>
        </div>
      )}

      {updateInfo && (
        <UpdateSheet
          versionName={updateInfo.versionName}
          notes={updateInfo.notes}
          apk={updateInfo.apk}
          onClose={() => setUpdateInfo(null)}
          onSkip={() => {
            try {
              localStorage.setItem('docklet.skipVersion', updateInfo.versionName)
            } catch {
              /* 忽略 */
            }
            setUpdateInfo(null)
          }}
        />
      )}

      {installOpen && (
        <InstallPage
          initialDetailId={returnDetailId}
          onDetailConsumed={() => setReturnDetailId(null)}
          onClose={() => setInstallOpen(false)}
          onInstalled={() => {
            refresh()
            setSelected(DEFAULT_CATEGORY)
          }}
          onOpen={(id) => {
            const app = apps.find((item) => item.id === id)
            if (app) {
              markAppActive(app.id)
              // 记住来源详情，应用关闭后回到详情页。
              setReturnDetailId(id)
              setOpenApp(app)
            }
          }}
        />
      )}

      {about && <AboutPage onClose={() => setAbout(false)} />}

      {settings && !settingsBasic && (
        <div className="panel">
          <header className="panel-top">
            <button
              type="button"
              className="nav-back"
              aria-label="返回"
              onClick={() => setSettings(false)}
            >
              ‹
            </button>
            <b>设置</b>
            <span className="detail-top-gap" />
          </header>
          <div className="panel-body">
            <button type="button" className="panel-row" onClick={() => setSettingsBasic(true)}>
              <span>
                <b>基础设置</b>
                <i>字号等基础选项</i>
              </span>
              <em>›</em>
            </button>
            <button
              type="button"
              className="panel-row"
              onClick={() => {
                void appOpenNotificationSettings().catch(() => undefined)
              }}
            >
              <span>
                <b>通知权限</b>
                <i>
                  {notificationsGranted === null
                    ? '点按查看系统设置'
                    : notificationsGranted
                      ? '已授予'
                      : '未授予，应用无法提醒'}
                </i>
              </span>
              <em>›</em>
            </button>
            {appNotifies.length > 0 && <p className="panel-note">应用提醒</p>}
            {appNotifies.map((item) => (
              <button
                key={item.id}
                type="button"
                className="panel-row"
                role="switch"
                aria-checked={item.enabled}
                onClick={() => {
                  const next = !item.enabled
                  setAppNotifies((list) =>
                    list.map((app) => (app.id === item.id ? { ...app, enabled: next } : app)),
                  )
                  void setAppNotification(item.id, next).catch(() => {
                    setAppNotifies((list) =>
                      list.map((app) => (app.id === item.id ? { ...app, enabled: !next } : app)),
                    )
                  })
                }}
              >
                <span>
                  <b>
                    {item.icon ? item.icon + ' ' : ''}
                    {item.name}
                  </b>
                  <i>{item.enabled ? '允许后台提醒' : '已关闭提醒'}</i>
                </span>
                <em className={`switch${item.enabled ? ' on' : ''}`} aria-hidden="true">
                  <span />
                </em>
              </button>
            ))}
            <button
              type="button"
              className="panel-row"
              role="switch"
              aria-checked={diagOn}
              onClick={() => {
                const next = !diagOn
                setDiagOn(next)
                setDiagEnabled(next)
              }}
            >
              <span>
                <b>诊断日志</b>
                <i>仅保存在本机，默认关闭</i>
              </span>
              <em className={`switch${diagOn ? ' on' : ''}`} aria-hidden="true">
                <span />
              </em>
            </button>
            {diagOn && (
              <button type="button" className="panel-row" onClick={() => setDiagOpen(true)}>
                <span>
                  <b>诊断信息</b>
                  <i>版本、网络与最近日志</i>
                </span>
                <em>›</em>
              </button>
            )}
            <button type="button" className="panel-row" onClick={() => setAbout(true)}>
              <span>
                <b>关于与声明</b>
                <i>版本、联系与责任声明</i>
              </span>
              <em>›</em>
            </button>
            <p className="panel-note">
              已安装 {apps.length} 个应用
            </p>
          </div>
        </div>
      )}

      {diagOpen && <DiagnosticsPage onClose={() => setDiagOpen(false)} />}

      {settings && settingsBasic && (
        <div className="panel">
          <header className="panel-top">
            <button
              type="button"
              className="nav-back"
              aria-label="返回"
              onClick={() => setSettingsBasic(false)}
            >
              ‹
            </button>
            <b>基础设置</b>
            <span className="detail-top-gap" />
          </header>
          <div className="panel-body">
            <button
              type="button"
              className="panel-row"
              role="switch"
              aria-checked={largeText}
              onClick={() => {
                const next = !largeText
                setLargeText(next)
                setLargeTextState(next)
              }}
            >
              <span>
                <b>大字号</b>
                <i>让界面文字更大，便于阅读</i>
              </span>
              <em className={`switch${largeText ? ' on' : ''}`} aria-hidden="true">
                <span />
              </em>
            </button>

            <button
              type="button"
              className="panel-row"
              role="switch"
              aria-checked={uninstallCheck}
              onClick={() => {
                const next = !uninstallCheck
                setUninstallCheckEnabled(next)
                setUninstallCheck(next)
              }}
            >
              <span>
                <b>卸载二次确认</b>
                <i>卸载应用时需答对一道加减法</i>
              </span>
              <em className={`switch${uninstallCheck ? ' on' : ''}`} aria-hidden="true">
                <span />
              </em>
            </button>

            {hasCode ? (
              <>
                <button
                  type="button"
                  className="panel-row"
                  role="switch"
                  aria-checked={codeOnStart}
                  onClick={() => {
                    const next = !codeOnStart
                    setDeviceCodeEnabled(next)
                    setCodeOnStart(next)
                  }}
                >
                  <span>
                    <b>启动时输入控制码</b>
                    <i>冷启动需要输入设备控制码</i>
                  </span>
                  <em className={`switch${codeOnStart ? ' on' : ''}`} aria-hidden="true">
                    <span />
                  </em>
                </button>
                <button type="button" className="panel-row" onClick={() => { setCodeEditing(false); setCodeDialog('modify') }}>
                  <span>
                    <b>修改控制码</b>
                    <i>需要先输入当前控制码</i>
                  </span>
                  <em>›</em>
                </button>
                <button type="button" className="panel-row" onClick={() => { setCodeEditing(false); setCodeDialog('clear') }}>
                  <span>
                    <b>清除控制码</b>
                    <i>清除后冷启动不再验证</i>
                  </span>
                  <em>›</em>
                </button>
              </>
            ) : (
              <button type="button" className="panel-row" onClick={() => { setCodeEditing(false); setCodeDialog('set') }}>
                <span>
                  <b>设置设备控制码</b>
                  <i>至少 4 位，设置后冷启动需输入</i>
                </span>
                <em>›</em>
              </button>
            )}
          </div>
        </div>
      )}

      {codeDialog && (
        <div
          className={`confirm-scrim${codeEditing ? ' is-editing' : ''}`}
          onClick={() => {
            setCodeDialog(null)
            setOldCode('')
            setNewCodeValue('')
            setConfirmCodeValue('')
            setCodeError('')
            setCodeEditing(false)
          }}
        >
          <div className="confirm-box" onClick={(event) => event.stopPropagation()}>
            <b>
              {codeDialog === 'set' ? '设置设备控制码' : codeDialog === 'modify' ? '修改设备控制码' : '清除设备控制码'}
            </b>
            <p>
              {codeDialog === 'clear'
                ? '输入当前控制码后清除。'
                : codeDialog === 'modify'
                  ? '先输入当前控制码，再设置新的控制码。'
                  : '控制码至少 4 位，字符不限；忘记只能清除应用数据。'}
            </p>
            <div className="confirm-rule" />
            {codeDialog !== 'set' && (
              <input
                className="confirm-input secure-input"
                value={oldCode}
                onChange={(event) => {
                  setOldCode(event.target.value)
                  setCodeError('')
                }}
                onFocus={() => setCodeEditing(true)}
                type="text"
                placeholder="当前控制码"
                autoCapitalize="off"
                autoCorrect="off"
                spellCheck={false}
              />
            )}
            {codeDialog !== 'clear' && (
              <input
                className="confirm-input secure-input"
                value={newCodeValue}
                onChange={(event) => {
                  setNewCodeValue(event.target.value)
                  setCodeError('')
                }}
                onFocus={() => setCodeEditing(true)}
                type="text"
                placeholder="新控制码（至少 4 位）"
                autoCapitalize="off"
                autoCorrect="off"
                spellCheck={false}
              />
            )}
            {codeDialog !== 'clear' && (
              <input
                className="confirm-input secure-input"
                value={confirmCodeValue}
                onChange={(event) => {
                  setConfirmCodeValue(event.target.value)
                  setCodeError('')
                }}
                onFocus={() => setCodeEditing(true)}
                type="text"
                placeholder="再输入一次控制码"
                autoCapitalize="off"
                autoCorrect="off"
                spellCheck={false}
              />
            )}
            {codeError && <span className="confirm-error">{codeError}</span>}
            <div className="confirm-actions">
              <button
                type="button"
                onClick={() => {
                  setCodeDialog(null)
                  setOldCode('')
                  setNewCodeValue('')
                  setConfirmCodeValue('')
                  setCodeError('')
                }}
              >
                取消
              </button>
              <button type="button" className="is-danger" onClick={() => void submitCode()}>
                {codeDialog === 'clear' ? '清除' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}

      {sheet?.kind === 'menu' && (
        <div
          className="sheet-scrim" role="dialog" aria-modal="true"
          onClick={() => {
            if (Date.now() - menuOpenedAt.current < 350) return
            setSheet(null)
          }}
        >
          <div className="sheet amenu" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <AppSheetHead app={sheet.app} />
            <div className="sheet-rule is-head" />
            <div className="amenu-list">
              <button type="button" className="amenu-row" onClick={() => setSheet({ kind: 'detail', app: sheet.app })}>
                <span>详情</span>
                <em>›</em>
              </button>
              <button
                type="button"
                className="amenu-row"
                onClick={() => {
                  setPendingCategory(getAppCategory(sheet.app.id))
                  setSheet({ kind: 'category', app: sheet.app })
                }}
              >
                <span>分类</span>
                <em>›</em>
              </button>
              <button type="button" className="amenu-row" onClick={() => setSheet({ kind: 'sort', app: sheet.app })}>
                <span>排序</span>
                <em>›</em>
              </button>
              <button
                type="button"
                className="amenu-row"
                onClick={() => void pinShortcut(sheet.app)}
              >
                <span>添加到桌面</span>
                <em>›</em>
              </button>
              <button
                type="button"
                className="amenu-row"
                onClick={() => setSheet({ kind: 'remove', app: sheet.app })}
              >
                <span>卸载</span>
                <em>›</em>
              </button>
            </div>
            <div className="sheet-rule" />
            <div className="sheet-actions is-single">
              <button type="button" className="sheet-ghost" onClick={() => setSheet(null)}>
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {sheet?.kind === 'category' && (
        <div className="sheet-scrim" role="dialog" aria-modal="true" onClick={() => setSheet(null)}>
          <div className="sheet" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <AppSheetHead app={sheet.app} />
            <div className="sheet-rule is-head" />
            <p className="sheet-sub">改为分类</p>
            <div className="cat-grid">
              <button
                type="button"
                className={(pendingCategory ?? getAppCategory(sheet.app.id)) === DEFAULT_CATEGORY ? 'on' : ''}
                onClick={() => setPendingCategory(DEFAULT_CATEGORY)}
              >
                {defaultMeta.name}
              </button>
              {categories.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={(pendingCategory ?? getAppCategory(sheet.app.id)) === item.id ? 'on' : ''}
                  onClick={() => setPendingCategory(item.id)}
                >
                  {item.name}
                </button>
              ))}
            </div>
            <div className="sheet-rule" />
            <div className="sheet-actions">
              <button type="button" className="sheet-ghost" onClick={() => setSheet(null)}>
                取消
              </button>
              <button
                type="button"
                className="sheet-primary"
                disabled={!pendingCategory}
                onClick={() => {
                  if (!pendingCategory) return
                  assign(sheet.app, pendingCategory)
                }}
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {sheet?.kind === 'sort' && (
        <div className="sheet-scrim" role="dialog" aria-modal="true" onClick={() => setSheet(null)}>
          <div className="sheet" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <h3 className="sheet-title">应用排序</h3>
            <div className="sheet-rule is-head" />
            <div className="sort-list">
              {sortApps.map((app, index) => {
                const isOn = app.id === (sortActive ?? sheet.app.id)
                const reorder = (ids: string[]) => {
                  saveOrder(activeId, ids)
                  refresh()
                  setSortActive(app.id)
                }
                return (
                  <div
                    key={app.id}
                    className={`sort-row${isOn ? ' on' : ''}`}
                    onClick={() => setSortActive(app.id)}
                  >
                    <span className="app-row-icon">{app.icon || '▢'}</span>
                    <b>{app.name}</b>
                    <button
                      type="button"
                      className="sort-top"
                      disabled={index === 0}
                      aria-label="置顶"
                      onClick={() => {
                        const ids = sortApps.map((item) => item.id).filter((id) => id !== app.id)
                        reorder([app.id, ...ids])
                      }}
                    >
                      <span className="pin-glyph" aria-hidden="true">
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M3 4h18" />
                          <path d="M12 20V9" />
                          <path d="M6 15l6-6 6 6" />
                        </svg>
                      </span>
                    </button>
                    <button
                      type="button"
                      disabled={index === 0}
                      aria-label="上移"
                      onClick={() => {
                        const ids = sortApps.map((item) => item.id)
                        ;[ids[index - 1], ids[index]] = [ids[index], ids[index - 1]]
                        reorder(ids)
                      }}
                    >
                      ↑                    </button>
                    <button
                      type="button"
                      disabled={index === sortApps.length - 1}
                      aria-label="下移"
                      onClick={() => {
                        const ids = sortApps.map((item) => item.id)
                        ;[ids[index + 1], ids[index]] = [ids[index], ids[index + 1]]
                        reorder(ids)
                      }}
                    >
                      ↓                    </button>
                  </div>
                )
              })}
            </div>
            <div className="sheet-rule" />
            <button type="button" className="sheet-primary" onClick={() => setSheet(null)}>
              关闭
            </button>
          </div>
        </div>
      )}

      {sheet?.kind === 'detail' && (
        <AppDetailPage
          app={sheet.app}
          onClose={() => setSheet(null)}
          onOpen={() => {
            const target = sheet.app
            // 不清掉 sheet：从应用返回时回到详情页
            openAppItem(target)
          }}
          onRemove={() => setSheet({ kind: 'remove', app: sheet.app })}
        />
      )}

      {sheet?.kind === 'remove' && (
        <UninstallConfirm
          name={sheet.app.name}
          onCancel={() => setSheet(null)}
          onConfirm={() => {
            const target = sheet.app
            setSheet(null)
            return removeHostedApp(target.id)
              .then(refresh)
              .catch(() => {
                showToast('删除失败，请重试')
              })
          }}
        />
      )}

      {sheet?.kind === 'categories' && (
        <div className="sheet-scrim" role="dialog" aria-modal="true" onClick={() => setSheet(null)}>
          <div className="sheet" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-handle" />
            <h3 className="sheet-title">分类管理</h3>
            <p className="sheet-sub">点名称直接改；有应用时不能删除</p>
            <div className="sheet-rule is-head" />
            <div className="cat-manage">
              <div className="cat-row">
                <div
                  className={`cat-manage-row${defaultEditing ? ' is-editing' : ''}${
                    rowLocked && !defaultEditing ? ' is-locked' : ''
                  }`}
                >
                  <div className="cat-field">
                    <button
                      type="button"
                      className={`cat-ico${defaultMeta.icon ? '' : ' is-empty'}`}
                      onClick={() => setIconEditing(iconEditing === 'default' ? null : 'default')}
                      aria-label="选择图标"
                    >
                      {defaultMeta.icon ? <CategoryIcon name={defaultMeta.icon} size={16} /> : '＋'}
                    </button>
                    <input
                      value={defaultEditing ? defaultEditName : defaultMeta.name}
                      maxLength={4}
                      disabled={rowLocked && !defaultEditing}
                      onFocus={() => {
                        setDefaultEditing(true)
                        setDefaultEditName(defaultMeta.name)
                        setCatError('')
                      }}
                      onChange={(event) => setDefaultEditName(event.target.value)}
                    />
                  </div>
                  {defaultEditing ? (
                    <>
                      <button
                        type="button"
                        className="cat-cancel is-save"
                        onClick={() => {
                          const name = defaultEditName.trim() || DEFAULT_NAME
                          if (
                            categories.some(
                              (item) => item.name.trim().toLowerCase() === name.toLowerCase(),
                            )
                          ) {
                            setCatError('已有同名分类')
                            return
                          }
                          updateDefaultMeta({ name })
                          setDefaultEditing(false)
                          setCatError('')
                        }}
                      >
                        确认
                      </button>
                      <button
                        type="button"
                        className="cat-cancel"
                        onClick={() => {
                          setDefaultEditing(false)
                          setDefaultEditName('')
                        }}
                      >
                        取消
                      </button>
                    </>
                  ) : (
                    <span className="cat-hint">系统内置，只能改名</span>
                  )}
                </div>
                {iconEditing === 'default' && renderIconPicker('default', defaultMeta.icon)}
              </div>
              {categories.map((category, index) => {
                const count = apps.filter((app) => getAppCategory(app.id) === category.id).length
                return (
                  <div className="cat-row" key={category.id}>
                    <div
                      className={`cat-manage-row${catEditId === category.id ? ' is-editing' : ''}${
                        rowLocked && catEditId !== category.id ? ' is-locked' : ''
                      }`}
                    >
                      <div className="cat-field">
                        <button
                          type="button"
                          className={`cat-ico${category.icon ? '' : ' is-empty'}`}
                          onClick={() => setIconEditing(iconEditing === category.id ? null : category.id)}
                          aria-label="选择图标"
                        >
                          {category.icon ? <CategoryIcon name={category.icon} size={16} /> : '＋'}
                        </button>
                        <input
                          value={catEditId === category.id ? catEditName : category.name}
                          maxLength={4}
                          disabled={rowLocked && catEditId !== category.id}
                          onFocus={() => {
                            setCatEditId(category.id)
                            setCatEditName(category.name)
                            setCatError('')
                          }}
                          onChange={(event) => {
                            if (catEditId === category.id) setCatEditName(event.target.value)
                          }}
                        />
                      </div>
                    {catEditId === category.id ? (
                      <>
                        <button
                          type="button"
                          className="cat-cancel is-save"
                          onClick={() => {
                            const name = catEditName.trim()
                            if (!name || renameCategory(category, name)) setCatEditId(null)
                          }}
                        >
                          确认
                        </button>
                        <button
                          type="button"
                          className="cat-cancel"
                          onClick={() => setCatEditId(null)}
                        >
                          取消
                        </button>
                      </>
                    ) : (
                      <>
                    <button
                      type="button"
                      className="cat-sq"
                      disabled={index === 0}
                      onClick={() => moveCategory(category, -1)}
                      aria-label="上移"
                    >
                      ↑                    </button>
                    <button
                      type="button"
                      className="cat-sq"
                      disabled={index === categories.length - 1}
                      onClick={() => moveCategory(category, 1)}
                      aria-label="下移"
                    >
                      ↓                    </button>
                    <button
                      type="button"
                      className="cat-sq is-danger"
                      disabled={count > 0}
                      title={count > 0 ? `还有 ${count} 个应用` : ''}
                      onClick={() => deleteCategory(category)}
                      aria-label="删除"
                    >
                      ×
                    </button>
                      </>
                    )}
                    </div>
                    {iconEditing === category.id && renderIconPicker(category.id, category.icon)}
                  </div>
                )
              })}
              {categories.length === 0 && !addingCategory && <p className="pane-empty">还没有分类</p>}
            </div>
            {catError && <p className="cat-error">{catError}</p>}
            {addingCategory && (
              <div className="cat-row">
                <div className="cat-add">
                  <div className="cat-field">
                    <button
                      type="button"
                      className={`cat-ico${newIcon ? '' : ' is-empty'}`}
                      onClick={() => setIconEditing(iconEditing === 'new' ? null : 'new')}
                      aria-label="选择图标"
                    >
                      {newIcon ? <CategoryIcon name={newIcon} size={16} /> : '＋'}
                    </button>
                    <input
                      className="sheet-input"
                      value={newName}
                      onChange={(event) => setNewName(event.target.value)}
                      placeholder="新分类名称"
                      maxLength={4}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') addCategory()
                      }}
                    />
                  </div>
                  <button type="button" className="cat-cancel is-save" onClick={addCategory}>
                    确认
                  </button>
                  <button
                    type="button"
                    className="cat-cancel"
                    onClick={() => {
                      setAddingCategory(false)
                      setNewName('')
                      setNewIcon(undefined)
                    }}
                  >
                    取消
                  </button>
                </div>
                {iconEditing === 'new' && renderIconPicker('new', newIcon)}
              </div>
            )}
            {categories.length >= 10 ? (
              <p className="cat-error is-full">分类数量已满，不可新建</p>
            ) : (
              <button
                type="button"
                className="cat-add-toggle"
                disabled={rowLocked}
                onClick={() => {
                  if (Date.now() - categoriesOpenedAt.current < 350) return
                  setAddingCategory(true)
                  setCatError('')
                }}
              >
                ＋ 添加分类
              </button>
            )}
            <div className="sheet-rule" />
            <button type="button" className="sheet-primary" onClick={() => setSheet(null)}>
              关闭
            </button>
          </div>
        </div>
      )}
      {toast && <div className="shell-toast" role="status" aria-live="polite">{toast}</div>}
    </div>
  )
}

