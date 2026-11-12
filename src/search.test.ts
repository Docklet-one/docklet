import { describe, expect, it } from 'vitest'
import { filterCatalog, matchesApp, searchHome } from './search'
import type { CatalogEntry } from './catalog'
import type { HostedApp } from './native/appHost'

const catalog: CatalogEntry[] = [
  {
    id: 'dsh-console',
    name: 'DSH控制台',
    icon: '💬',
    category: '工具',
    tagline: '手机上的 DSH 控制台',
    version: '1.0.116',
    url: 'https://apps.docklet.cn/dsh-console-1.0.116.zip',
    search: { keywords: ['dsh', 'kzt', 'console'] },
  },
  {
    id: 'notes',
    name: '笔记',
    icon: '📝',
    category: '工具',
    tagline: '随手记',
    version: '1.0.0',
    url: 'https://apps.docklet.cn/notes-1.0.0.zip',
    search: { keywords: ['biji', 'notes'] },
  },
]

const app = (id: string): HostedApp => ({
  id,
  name: id === 'dsh-console' ? 'DSH控制台' : '笔记',
  version: '1.0.0',
  icon: '▢',
  entry: 'app.js',
  origin: `https://${id}.app.local/`,
  capabilities: [],
  permissions: [],
})

describe('filterCatalog', () => {
  it('matches declared keywords exactly', () => {
    expect(filterCatalog(catalog, 'dsh').map((item) => item.id)).toEqual(['dsh-console'])
    expect(filterCatalog(catalog, 'notes').map((item) => item.id)).toEqual(['notes'])
    expect(filterCatalog(catalog, 'zzz').map((item) => item.id)).toEqual([])
  })
})

describe('searchHome', () => {
  it('returns installed apps first and uninstalled catalog entries', () => {
    const items = searchHome([app('dsh-console')], catalog, 'd')
    expect(items.map((item) => item.id)).toEqual(['dsh-console'])
    expect(items[0].installed).toBe(true)
    expect(items[0].tagline).toBe('v1.0.0')
  })

  it('returns empty for empty keyword', () => {
    const items = searchHome([app('dsh-console')], catalog, '')
    expect(items).toEqual([])
  })
})

describe('matchesApp', () => {
  it('matches name and id prefix without a catalog entry', () => {
    expect(matchesApp(app('dsh-console'), undefined, 'DSH')).toBe(true)
    expect(matchesApp(app('dsh-console'), undefined, 'dsh-')).toBe(true)
    expect(matchesApp(app('dsh-console'), undefined, 'console')).toBe(false)
  })
})
