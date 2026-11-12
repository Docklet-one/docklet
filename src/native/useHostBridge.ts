import { useEffect } from 'react'
import { Capacitor } from '@capacitor/core'
import { installHostBridge, refreshHostBridge } from './hostBridge'

/** 壳启动时挂上能力通道；仅原生平台需要。 */
export function useHostBridge(): void {
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return
    let dispose = () => {}
    void refreshHostBridge().then(() => {
      dispose = installHostBridge()
    })
    return () => dispose()
  }, [])
}
