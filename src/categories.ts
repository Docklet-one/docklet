/** 分类与排序：沿用旧版的存储键，迁移后无需改数据。 */

export interface Category {
  id: string
  name: string
  /** 分类图标（字符图标）；不设置则不显示 */
  icon?: string
}

const CATEGORY_KEY = 'docklet.categories.v2'
const APP_CATEGORY_PREFIX = 'docklet.app.category.'
const ORDER_PREFIX = 'docklet.library.order.'
export const DEFAULT_CATEGORY = 'default'
export const DEFAULT_NAME = '默认'

export function listCategories(): Category[] {
  try {
    const raw = localStorage.getItem(CATEGORY_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item) => item && typeof item.id === 'string' && typeof item.name === 'string')
      .map((item) => ({
        id: item.id,
        name: item.name,
        icon: typeof item.icon === 'string' && item.icon ? item.icon : undefined,
      }))
  } catch {
    return []
  }
}

export function saveCategories(list: Category[]): void {
  try {
    localStorage.setItem(CATEGORY_KEY, JSON.stringify(list))
  } catch {
    /* 忽略 */
  }
}

export function getAppCategory(appId: string): string {
  try {
    return localStorage.getItem(APP_CATEGORY_PREFIX + appId) || DEFAULT_CATEGORY
  } catch {
    return DEFAULT_CATEGORY
  }
}

export function setAppCategory(appId: string, categoryId: string): void {
  try {
    localStorage.setItem(APP_CATEGORY_PREFIX + appId, categoryId)
  } catch {
    /* 忽略 */
  }
}

export function readOrder(categoryId: string): string[] {
  try {
    const raw = localStorage.getItem(ORDER_PREFIX + categoryId)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'string') : []
  } catch {
    return []
  }
}

export function saveOrder(categoryId: string, ids: string[]): void {
  try {
    localStorage.setItem(ORDER_PREFIX + categoryId, JSON.stringify(ids))
  } catch {
    /* 忽略 */
  }
}

export function sortByOrder<T extends { id: string }>(items: T[], categoryId: string): T[] {
  const order = readOrder(categoryId)
  if (order.length === 0) return items
  const rank = new Map(order.map((id, index) => [id, index]))
  return [...items].sort((a, b) => {
    const ra = rank.has(a.id) ? (rank.get(a.id) as number) : Number.MAX_SAFE_INTEGER
    const rb = rank.has(b.id) ? (rank.get(b.id) as number) : Number.MAX_SAFE_INTEGER
    return ra - rb
  })
}
