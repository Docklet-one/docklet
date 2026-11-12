/// <reference types="vite/client" />

declare const __BUILD_ID__: string

interface Window {
  __dockletReady?: () => void
}
