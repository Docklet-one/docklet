/** 界面偏好：大字号（本地保存） */
const KEY = 'docklet.largeText'

export function isLargeText(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function setLargeText(value: boolean): void {
  try {
    localStorage.setItem(KEY, value ? '1' : '0')
  } catch {
    /* 忽略 */
  }
  applyLargeText(value)
}

export function applyLargeText(value = isLargeText()): void {
  document.documentElement.classList.toggle('large-text', value)
}
