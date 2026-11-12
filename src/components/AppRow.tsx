import type { PointerEventHandler, ReactNode } from 'react'

/** 首页与安装页共用的一行应用卡片。 */
export function AppRow({
  icon,
  name,
  tagline,
  trailing,
  chevron,
  className = 'app-row',
  onClick,
  onPointerDown,
  onPointerUp,
  onPointerLeave,
  onPointerCancel,
}: {
  icon: string
  name: string
  tagline: string
  trailing?: ReactNode
  chevron?: boolean
  className?: string
  onClick: () => void
  onPointerDown?: PointerEventHandler<HTMLButtonElement>
  onPointerUp?: PointerEventHandler<HTMLButtonElement>
  onPointerLeave?: PointerEventHandler<HTMLButtonElement>
  onPointerCancel?: PointerEventHandler<HTMLButtonElement>
}) {
  return (
    <button
      type="button"
      className={className}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onPointerLeave={onPointerLeave}
      onPointerCancel={onPointerCancel}
      onClick={onClick}
    >
      <span className="app-row-icon" aria-hidden="true">{icon}</span>
      <span className="app-row-meta">
        <b>{name}</b>
        <i>{tagline}</i>
      </span>
      {trailing}
      {chevron && <span className="app-row-chev">›</span>}
    </button>
  )
}
