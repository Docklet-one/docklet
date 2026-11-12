/**
 * 全局键盘可视区：把键盘高度写到 --shell-kb，并维护 html.kb-open。
 * 必须挂载在根组件（AppGate 之前），这样锁屏、壳、所有全屏层都能用。
 */
export function installKeyboardInset() {
  const root = document.documentElement
  let nativeSeen = false

  const apply = (kb: number) => {
    const value = Math.max(0, Math.round(kb))
    root.style.setProperty('--shell-kb', `${value}px`)
    root.classList.toggle('kb-open', value > 0)
  }

  // 原生 insets 优先（物理像素 → CSS px）
  window.addEventListener('docklet:keyboard', (event) => {
    const detail = (event as CustomEvent<{ height?: number }>).detail
    if (!detail) return
    nativeSeen = true
    apply((Number(detail.height) || 0) / (window.devicePixelRatio || 1))
  })

  // 兜底：visualViewport 差值（桌面浏览器 / 没有原生事件的场景）
  const viewport = window.visualViewport
  const update = () => {
    if (nativeSeen) return
    const kb = viewport ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0
    apply(kb)
  }

  viewport?.addEventListener('resize', update)
  viewport?.addEventListener('scroll', update)
  window.addEventListener('resize', update)
  update()
}
