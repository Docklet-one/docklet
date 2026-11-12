import { beforeEach, describe, expect, it } from 'vitest'
import { clearDiag, diagEnabled, logDiag, readDiag, setDiagEnabled } from './diag'

const store = new Map<string, string>()

beforeEach(() => {
  store.clear()
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
    },
  })
})

describe('diag', () => {
  it('is off by default and logs nothing', () => {
    expect(diagEnabled()).toBe(false)
    logDiag('x')
    expect(readDiag()).toEqual([])
  })

  it('writes and clears logs when enabled', () => {
    setDiagEnabled(true)
    logDiag('install-ok', 'dsh-console 1.0.116')
    const logs = readDiag()
    expect(logs).toHaveLength(1)
    expect(logs[0].event).toBe('install-ok')
    clearDiag()
    expect(readDiag()).toEqual([])
  })
})
