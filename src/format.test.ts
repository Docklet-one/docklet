import { describe, expect, it } from 'vitest'
import { formatSize, isNewer } from './format'

describe('isNewer', () => {
  it('compares numeric segments', () => {
    expect(isNewer('1.0.1', '1.0.0')).toBe(true)
    expect(isNewer('1.1', '1.0.9')).toBe(true)
    expect(isNewer('1.0', '1.0.0')).toBe(false)
    expect(isNewer('0.9.9', '1.0.0')).toBe(false)
  })
})

describe('formatSize', () => {
  it('formats bytes, KB and MB', () => {
    expect(formatSize(undefined)).toBe('—')
    expect(formatSize(512)).toBe('512 B')
    expect(formatSize(2048)).toBe('2 KB')
    expect(formatSize(5 * 1024 * 1024)).toBe('5.0 MB')
  })
})
