const CODE_KEY = 'docklet.deviceCode'
const ENABLED_KEY = 'docklet.deviceCodeOnStart'
export const UNINSTALL_CHECK_KEY = 'docklet.uninstallCheck'

async function sha256(text: string): Promise<string> {
  try {
    const data = new TextEncoder().encode(text)
    const digest = await crypto.subtle.digest('SHA-256', data)
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
  } catch {
    // 极端环境没有 SubtleCrypto 时退化为简单散列（仅本地防误触，不作为安全边界）
    let hash = 0
    for (let i = 0; i < text.length; i += 1) hash = (hash * 31 + text.charCodeAt(i)) | 0
    return `fallback:${hash}`
  }
}

export function hasDeviceCode(): boolean {
  try {
    return Boolean(localStorage.getItem(CODE_KEY))
  } catch {
    return false
  }
}

export function isDeviceCodeEnabled(): boolean {
  try {
    return localStorage.getItem(ENABLED_KEY) === '1'
  } catch {
    return false
  }
}

export function setDeviceCodeEnabled(value: boolean): void {
  try {
    localStorage.setItem(ENABLED_KEY, value ? '1' : '0')
  } catch {
    /* 忽略 */
  }
}

export async function setDeviceCode(code: string, keepStartup = false): Promise<void> {
  localStorage.setItem(CODE_KEY, await sha256(code))
  if (!keepStartup) setDeviceCodeEnabled(true)
}

export async function verifyDeviceCode(code: string): Promise<boolean> {
  try {
    const stored = localStorage.getItem(CODE_KEY)
    if (!stored) return false
    return stored === (await sha256(code))
  } catch {
    return false
  }
}

export function clearDeviceCode(): void {
  try {
    localStorage.removeItem(CODE_KEY)
    localStorage.removeItem(ENABLED_KEY)
  } catch {
    /* 忽略 */
  }
}

export function isUninstallCheckEnabled(): boolean {
  try {
    return localStorage.getItem(UNINSTALL_CHECK_KEY) === '1'
  } catch {
    return false
  }
}

export function setUninstallCheckEnabled(value: boolean): void {
  try {
    localStorage.setItem(UNINSTALL_CHECK_KEY, value ? '1' : '0')
  } catch {
    /* 忽略 */
  }
}

/** 生成一道一位数加减法题，结果非负 */
export function makeArithmetic(): { text: string; answer: number } {
  const a = 2 + Math.floor(Math.random() * 8)
  const b = 1 + Math.floor(Math.random() * 8)
  if (Math.random() < 0.5) return { text: `${a} + ${b}`, answer: a + b }
  const big = Math.max(a, b)
  const small = Math.min(a, b)
  return { text: `${big} - ${small}`, answer: big - small }
}
