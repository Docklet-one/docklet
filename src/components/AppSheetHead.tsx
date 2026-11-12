import type { HostedApp } from '../native/appHost'

/** 底部 sheet 的应用头：图标 + 名称 + 简介（无简介显示版本号） */
export function AppSheetHead({ app }: { app: HostedApp }) {
  return (
    <div className="sheet-app-head">
      <span className="app-row-icon">{app.icon || '▢'}</span>
      <span>
        <b>{app.name}</b>
        <i>{app.description || `v${app.version}`}</i>
      </span>
    </div>
  )
}
