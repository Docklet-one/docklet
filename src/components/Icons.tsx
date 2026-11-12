import type { SVGProps } from 'react'

export type IconName =
  | 'apps'
  | 'store'
  | 'library'
  | 'download'
  | 'check'
  | 'play'
  | 'close'
  | 'back'
  | 'trash'
  | 'refresh'
  | 'spark'
  | 'restart'
  | 'search'
  | 'settings'
  | 'palette'
  | 'heart'
  | 'pen'
  | 'key'
  | 'trophy'
  | 'info'
  | 'mail'
  | 'shield'
  | 'doc'

const PATHS: Record<IconName, JSX.Element> = {
  store: (
    <>
      <path d="M4.2 9.2h15.6l-1.15 9.9a2 2 0 0 1-2 1.8H7.35a2 2 0 0 1-2-1.8L4.2 9.2Z" />
      <path d="M8.6 9.2V7.4a3.4 3.4 0 0 1 6.8 0v1.8" />
    </>
  ),
  library: (
    <>
      <rect x="3.6" y="3.6" width="7" height="7" rx="2" />
      <rect x="13.4" y="3.6" width="7" height="7" rx="2" />
      <rect x="3.6" y="13.4" width="7" height="7" rx="2" />
      <rect x="13.4" y="13.4" width="7" height="7" rx="2" />
    </>
  ),
  download: (
    <>
      <path d="M12 3.5v11.2" />
      <path d="m7.8 10.6 4.2 4.2 4.2-4.2" />
      <path d="M4.5 19.5h15" />
    </>
  ),
  check: <path d="m4.5 12.6 4.8 4.8L19.6 7" />,
  play: <path d="M8.2 4.9c0-.8.9-1.3 1.6-.9l9 6.5c.6.4.6 1.4 0 1.8l-9 6.5c-.7.4-1.6-.1-1.6-.9V4.9Z" fill="currentColor" stroke="none" />,
  close: (
    <>
      <path d="m6.4 6.4 11.2 11.2" />
      <path d="M17.6 6.4 6.4 17.6" />
    </>
  ),
  back: <path d="M14.8 5.2 7.9 12l6.9 6.8" />,
  trash: (
    <>
      <path d="M4.5 7h15" />
      <path d="M9.6 7V5.4c0-.6.5-1.1 1.1-1.1h2.6c.6 0 1.1.5 1.1 1.1V7" />
      <path d="m6.4 7 .9 12.1c0 .7.6 1.2 1.3 1.2h6.8c.7 0 1.3-.5 1.3-1.2L17.6 7" />
    </>
  ),
  refresh: (
    <>
      <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" />
      <path d="M19.6 4.6v4.2h-4.2" />
    </>
  ),
  spark: <path d="M12 3.4l1.9 5.4 5.4 1.9-5.4 1.9L12 18l-1.9-5.4L4.7 10.7l5.4-1.9L12 3.4Z" />,
  apps: (
    <>
      <rect x="3.5" y="3.5" width="7" height="7" rx="2" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="2" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="2" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="2" />
    </>
  ),
  search: (
    <>
      <circle cx="10.8" cy="10.8" r="6.3" />
      <path d="m15.6 15.6 4.4 4.4" />
    </>
  ),
  heart: (
    <path d="M12 20.2s-7.4-4.5-7.4-9.5A4.2 4.2 0 0 1 12 7.5a4.2 4.2 0 0 1 7.4 3.2c0 5-7.4 9.5-7.4 9.5Z" />
  ),
  palette: (
    <>
      <path d="M12 2.6C6.6 2.6 2.2 6.8 2.2 12s4.4 9.4 9.8 9.4c.95 0 1.7-.77 1.7-1.75 0-.45-.17-.85-.45-1.15-.28-.3-.43-.67-.43-1.15a1.7 1.7 0 0 1 1.72-1.72h2c3.2 0 5.8-2.6 5.8-5.8C21.8 6.5 17.4 2.6 12 2.6Z" />
      <circle cx="8.2" cy="11.6" r="1.55" fill="currentColor" stroke="none" />
      <circle cx="11.7" cy="7.5" r="1.55" fill="currentColor" stroke="none" />
      <circle cx="16.2" cy="9.1" r="1.55" fill="currentColor" stroke="none" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3.1" />
      <path d="M19.6 15.2a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1.08-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1.08 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z" />
    </>
  ),
  restart: (
    <>
      <path d="M20 11.6a8 8 0 1 0-2.4 6" />
      <path d="M20 5.6v5h-5" />
    </>
  ),
  pen: (
    <>
      <path d="M4.5 19.5h4.2L19.2 9a2.3 2.3 0 0 0-3.2-3.2L5.6 16.2l-1.1 3.3Z" />
      <path d="m13.6 7.2 3.2 3.2" />
    </>
  ),
  key: (
    <>
      <circle cx="8" cy="12" r="3.4" />
      <path d="M11.4 12H20" />
      <path d="M17.2 12v3.1" />
      <path d="M20 12v2.4" />
    </>
  ),
  trophy: (
    <>
      <path d="M8 4h8v5a4 4 0 0 1-8 0V4Z" />
      <path d="M8 5H5v2a3 3 0 0 0 3 3" />
      <path d="M16 5h3v2a3 3 0 0 1-3 3" />
      <path d="M12 13v4" />
      <path d="M8.5 20h7" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.6" />
      <path d="M12 11v5.2" />
      <path d="M12 7.9v.2" />
    </>
  ),
  mail: (
    <>
      <rect x="3.4" y="5.6" width="17.2" height="12.8" rx="2.4" />
      <path d="m4.2 7.6 7.8 5.4 7.8-5.4" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.2 19 6v5.6c0 4.2-3 7.3-7 8.8-4-1.5-7-4.6-7-8.8V6l7-2.8Z" />
      <path d="m9 11.9 2.1 2.1 4-4" />
    </>
  ),
  doc: (
    <>
      <path d="M6 3.6h7l5 5v11.8a1.6 1.6 0 0 1-1.6 1.6H6a1.6 1.6 0 0 1-1.6-1.6V5.2A1.6 1.6 0 0 1 6 3.6Z" />
      <path d="M12.8 3.8v5.2h5.1" />
      <path d="M8.4 13h7" />
      <path d="M8.4 16.4h5" />
    </>
  ),
}

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName
  size?: number
}

export function Icon({ name, size = 20, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.9}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {PATHS[name]}
    </svg>
  )
}
