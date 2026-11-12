const LOG_KEY = 'docklet.diag.v1'
const ENABLED_KEY = 'docklet.diag.enabled'
const LIMIT = 200

export interface DiagEntry {
  time: number
  event: string
  detail?: string
}

export function diagEnabled(): boolean {
  try {
    return localStorage.getItem(ENABLED_KEY) === '1'
  } catch {
    return false
  }
}

export function setDiagEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(ENABLED_KEY, enabled ? '1' : '0')
  } catch {
    /* 忽略 */
  }
}

export function readDiag(): DiagEntry[] {
  try {
    const raw = localStorage.getItem(LOG_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (item): item is DiagEntry =>
        Boolean(item) && typeof item.time === 'number' && typeof item.event === 'string',
    )
  } catch {
    return []
  }
}

export function clearDiag(): void {
  try {
    localStorage.removeItem(LOG_KEY)
  } catch {
    /* 忽略 */
  }
}

/** 只记事件和短原因；不记应用内容、token、完整 URL。 */
export function logDiag(event: string, detail?: string): void {
  if (!diagEnabled()) return
  try {
    const list = readDiag()
    list.push({ time: Date.now(), event, detail: detail?.slice(0, 160) })
    const trimmed = list.slice(-LIMIT)
    localStorage.setItem(LOG_KEY, JSON.stringify(trimmed))
  } catch {
    /* 忽略 */
  }
}
