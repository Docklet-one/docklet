import { describe, expect, it } from 'vitest'
import { getCatalogDropped, matchesCatalog, parseCatalog, type CatalogEntry } from './catalog'

const entry = (patch: Partial<CatalogEntry> = {}): CatalogEntry => ({
  id: 'dsh-console',
  name: 'DSH控制台',
  icon: '💬',
  category: '工具',
  tagline: '手机上的 DSH 控制台',
  version: '1.0.116',
  url: 'https://apps.docklet.cn/dsh-console-1.0.116.zip',
  size: 52629,
  publishedAt: '2026-11-08',
  releasedAt: '2026-11-08',
  search: { keywords: ['dsh', 'kzt', 'console'] },
  ...patch,
})

describe('parseCatalog', () => {
  it('accepts a valid catalog', () => {
    const list = parseCatalog({ apps: [entry(), entry({ id: 'notes', name: '笔记' })] })
    expect(list).toHaveLength(2)
    expect(getCatalogDropped()).toBe(0)
  })

  it('drops invalid ids, versions and urls', () => {
    parseCatalog({
      apps: [
        entry({ id: '1bad' }),
        entry({ version: 'x' }),
        entry({ url: 'http://example.com/a.zip' }),
        entry(),
      ],
    })
    expect(getCatalogDropped()).toBe(3)
  })

  it('drops duplicate ids', () => {
    parseCatalog({ apps: [entry(), entry()] })
    expect(getCatalogDropped()).toBe(1)
  })

  it('keeps http only for LAN addresses', () => {
    expect(parseCatalog({ apps: [entry({ url: 'http://192.168.2.7:8788/a.zip' })] })).toHaveLength(1)
    expect(parseCatalog({ apps: [entry({ url: 'http://8.8.8.8/a.zip' })] })).toHaveLength(0)
  })
})

describe('matchesCatalog', () => {
  it('matches name substring and exact keywords', () => {
    expect(matchesCatalog(entry(), '控制台')).toBe(true)
    expect(matchesCatalog(entry(), 'dsh')).toBe(true)
    expect(matchesCatalog(entry(), 'kzt')).toBe(true)
    expect(matchesCatalog(entry(), 'console')).toBe(true)
  })

  it('does not match partial or middle keywords', () => {
    expect(matchesCatalog(entry(), 'dshx')).toBe(false)
    expect(matchesCatalog(entry(), 'zhitai')).toBe(false)
  })

  it('matches everything for empty keyword', () => {
    expect(matchesCatalog(entry(), '   ')).toBe(true)
  })
})
