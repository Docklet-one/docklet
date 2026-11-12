/** 推荐清单：优先从官网拉取 catalog.json，失败时退回内置最小清单。 */
export interface CatalogEntry {
  id: string
  name: string
  icon: string
  category: string
  tagline: string
  description?: string
  author?: string
  authorContact?: string
  version: string
  recommended?: boolean
  capabilities?: string[]
  permissions?: Array<{ name: string; reason: string }>
  publishedAt?: string
  releasedAt?: string
  url?: string
  size?: number
  search?: { keywords?: string[] }
}

/** 离线兜底：拉取失败时还能看到入口；没有 url 的条目不显示安装。 */
export const RECOMMENDED: CatalogEntry[] = [
  {
    id: 'dsh-console',
    name: 'DSH控制台',
    icon: '💬',
    category: '工具',
    recommended: true,
    tagline: '手机上的 DSH 控制台，收发消息、处理审批与提问',
    version: '1.0.121',
    capabilities: ['fs', 'openExternal', 'clipboard', 'share', 'background', 'download'],
    permissions: [{ name: 'notifications', reason: '接收新消息提醒' }],
  },
]

const CATALOG_URL = 'https://apps.docklet.cn/catalog.json'
const CACHE_KEY = 'docklet.catalog.v1'
const CACHE_DAY_KEY = 'docklet.catalog.day'

const ID_PATTERN = /^[a-z][a-z0-9-]{1,31}$/
const VERSION_PATTERN = /^\d+(\.\d+){0,2}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const MAX_SIZE = 200 * 1024 * 1024

let lastCatalogDropped = 0

export const getCatalogDropped = () => lastCatalogDropped

function isValidEntry(item: Record<string, unknown>): boolean {
  if (typeof item.id !== 'string' || !ID_PATTERN.test(item.id)) return false
  if (typeof item.name !== 'string' || !item.name.trim() || item.name.length > 24) return false
  if (typeof item.version !== 'string' || !VERSION_PATTERN.test(item.version)) return false
  if (item.url != null) {
    if (typeof item.url !== 'string') return false
    try {
      const parsed = new URL(item.url)
      if (parsed.protocol === 'http:') {
        const allowed =
          parsed.hostname === 'localhost' ||
          parsed.hostname === '127.0.0.1' ||
          /^10\./.test(parsed.hostname) ||
          /^192\.168\./.test(parsed.hostname) ||
          /^172\.(1[6-9]|2\d|3[0-1])\./.test(parsed.hostname)
        if (!allowed) return false
      } else if (parsed.protocol !== 'https:') {
        return false
      }
    } catch {
      return false
    }
  }
  if (item.size != null && (typeof item.size !== 'number' || item.size <= 0 || item.size > MAX_SIZE)) return false
  if (item.publishedAt != null && (typeof item.publishedAt !== 'string' || !DATE_PATTERN.test(item.publishedAt))) return false
  if (item.releasedAt != null && (typeof item.releasedAt !== 'string' || !DATE_PATTERN.test(item.releasedAt))) return false
  return true
}

function normalize(raw: unknown): CatalogEntry[] {
  const list = Array.isArray(raw)
    ? raw
    : raw && typeof raw === 'object' && Array.isArray((raw as { apps?: unknown }).apps)
      ? ((raw as { apps: unknown[] }).apps)
      : []
  const seen = new Set<string>()
  const result: CatalogEntry[] = []
  let dropped = 0
  for (const item of list) {
    if (!item || typeof item !== 'object') {
      dropped += 1
      continue
    }
    const record = item as Record<string, unknown>
    if (!isValidEntry(record) || seen.has(String(record.id))) {
      dropped += 1
      continue
    }
    seen.add(String(record.id))
    result.push({
      id: String(record.id),
      name: String(record.name),
      icon: typeof record.icon === 'string' ? record.icon : '▢',
      category: typeof record.category === 'string' && record.category ? record.category : '其他',
      tagline: typeof record.tagline === 'string' ? record.tagline : '',
      description: typeof record.description === 'string' ? record.description : undefined,
      author: typeof record.author === 'string' ? record.author : undefined,
      authorContact: typeof record.authorContact === 'string' ? record.authorContact : undefined,
      version: String(record.version),
      recommended: record.recommended !== false,
      capabilities: Array.isArray(record.capabilities) ? record.capabilities.map(String) : [],
      permissions: Array.isArray(record.permissions)
        ? record.permissions
            .filter((perm): perm is Record<string, unknown> => Boolean(perm) && typeof perm === 'object')
            .map((perm) => ({ name: String(perm.name ?? ''), reason: String(perm.reason ?? '') }))
        : [],
      publishedAt: typeof record.publishedAt === 'string' ? record.publishedAt : undefined,
      releasedAt: typeof record.releasedAt === 'string' ? record.releasedAt : undefined,
      url: typeof record.url === 'string' ? record.url : undefined,
      size: typeof record.size === 'number' ? record.size : undefined,
      search: (() => {
        const raw = record.search && typeof record.search === 'object'
          ? (record.search as Record<string, unknown>).keywords
          : null
        if (!Array.isArray(raw)) return undefined
        const keywords = raw.map(String).map((item) => item.trim()).filter(Boolean)
        return keywords.length ? { keywords } : undefined
      })(),
    })
  }
  lastCatalogDropped = dropped
  return result
}

export function parseCatalog(raw: unknown): CatalogEntry[] {
  return normalize(raw)
}

export function readCachedCatalog(): CatalogEntry[] {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? normalize(JSON.parse(raw)) : []
  } catch {
    return []
  }
}

export function isCatalogFreshToday(): boolean {
  try {
    return localStorage.getItem(CACHE_DAY_KEY) === new Date().toISOString().slice(0, 10)
  } catch {
    return false
  }
}

export async function loadCatalog(options?: { force?: boolean; signal?: AbortSignal }): Promise<CatalogEntry[]> {
  const force = options?.force === true
  if (!force) {
    const cached = readCachedCatalog()
    if (cached.length > 0 && isCatalogFreshToday()) return cached
  }
  try {
    const response = await fetch(CATALOG_URL, { cache: 'no-store', signal: options?.signal })
    if (!response.ok) throw new Error(`HTTP_${response.status}`)
    const list = normalize(await response.json())
    if (list.length === 0) throw new Error('EMPTY_CATALOG')
    localStorage.setItem(CACHE_KEY, JSON.stringify(list))
    localStorage.setItem(CACHE_DAY_KEY, new Date().toISOString().slice(0, 10))
    return list
  } catch (error) {
    if (options?.signal?.aborted) throw error
    const cached = readCachedCatalog()
    if (!force && cached.length > 0) return cached
    throw error
  }
}


/** 名称做子串匹配；id、拼音、首字母、英文只做前缀匹配，避免单字母误命中。 */
export function matchesCatalog(entry: CatalogEntry, keyword: string): boolean {
  const key = keyword.trim().toLowerCase()
  if (!key) return true
  if (entry.name.toLowerCase().includes(key)) return true
  if (entry.id.toLowerCase().startsWith(key)) return true
  const keywords = entry.search?.keywords ?? []
  return keywords.some((field) => field.trim().toLowerCase() === key)
}
