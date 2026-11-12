/** 最近使用/首次出现的时间记录；安装也视为一次使用。 */
export const LAST_ACTIVE_PREFIX = 'docklet.lastOpened.'
export const FIRST_SEEN_PREFIX = 'docklet.firstSeen.'

export function readStamp(key: string): number {
  try {
    return Number(localStorage.getItem(key)) || 0
  } catch {
    return 0
  }
}

export function writeStamp(key: string, value: number): void {
  try {
    localStorage.setItem(key, String(value))
  } catch {
    /* 忽略 */
  }
}

export function markAppActive(id: string): void {
  writeStamp(LAST_ACTIVE_PREFIX + id, Date.now())
}
