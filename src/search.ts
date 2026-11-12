import { matchesCatalog, type CatalogEntry } from './catalog'
import type { HostedApp } from './native/appHost'

export interface SearchItem {
  id: string
  name: string
  icon: string
  tagline: string
  installed: boolean
  app?: HostedApp
  entry?: CatalogEntry
}

export function filterCatalog(catalog: CatalogEntry[], keyword: string): CatalogEntry[] {
  if (!keyword.trim()) return catalog
  return catalog.filter((entry) => matchesCatalog(entry, keyword))
}

export function matchesApp(app: HostedApp, entry: CatalogEntry | undefined, keyword: string): boolean {
  const key = keyword.trim().toLowerCase()
  if (!key) return true
  if (entry && matchesCatalog(entry, keyword)) return true
  return app.name.toLowerCase().includes(key) || app.id.toLowerCase().startsWith(key)
}

/** 首页全局搜索：已安装应用 + catalog 里未安装的应用。 */
export function searchHome(apps: HostedApp[], catalog: CatalogEntry[], keyword: string): SearchItem[] {
  if (!keyword.trim()) return []
  const entries = new Map(catalog.map((entry) => [entry.id, entry]))
  const installed: SearchItem[] = apps
    .filter((app) => matchesApp(app, entries.get(app.id), keyword))
    .map((app) => ({
      id: app.id,
      name: app.name,
      icon: app.icon || '▢',
      tagline: app.description || app.tagline || `v${app.version}`,
      installed: true,
      app,
      entry: entries.get(app.id),
    }))
  const rest: SearchItem[] = catalog
    .filter((entry) => !apps.some((app) => app.id === entry.id) && matchesCatalog(entry, keyword))
    .map((entry) => ({
      id: entry.id,
      name: entry.name,
      icon: entry.icon,
      tagline: entry.tagline,
      installed: false,
      entry,
    }))
  return [...installed, ...rest]
}
