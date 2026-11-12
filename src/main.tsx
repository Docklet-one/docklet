import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { applyLargeText } from './uiPrefs'
import './about.css'

applyLargeText()

// 输入框避让统一由 keyboardInset.ts 的 --shell-kb / --kb-inset 负责，
// 这里不再监听 focusin 主动滚动，避免与面板让位叠加。

// 触屏设备长按会弹出复制/分享菜单，禁用它（输入框除外）；
// 桌面端保留右键菜单，方便检查元素和调试。
const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches
if (!finePointer) {
  document.addEventListener('contextmenu', (event) => {
    const target = event.target as HTMLElement | null
    if (target && target.closest('input, textarea, [contenteditable="true"]')) return
    event.preventDefault()
  })
}

import { ShellApp } from './ShellApp'
import { AppGate } from './components/AppGate'
import { installKeyboardInset } from './keyboardInset'
import { installSystemBars } from './systemBars'
import './shell.css'

installKeyboardInset()
installSystemBars()

// 启动层：至少 0.3s，首页就绪即淡出；3s 兜底强制隐藏。
const boot = document.getElementById('boot')
const bootStarted = performance.now()
let bootHidden = false
const hideBoot = () => {
  if (bootHidden) return
  bootHidden = true
  boot?.classList.add('is-hiding')
  window.setTimeout(() => boot?.remove(), 300)
}
window.__dockletReady = () => {
  const wait = Math.max(0, 300 - (performance.now() - bootStarted))
  window.setTimeout(hideBoot, wait)
}
window.setTimeout(hideBoot, 3000)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppGate>
      <ShellApp />
    </AppGate>
  </StrictMode>,
)
