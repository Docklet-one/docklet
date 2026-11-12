import { useState } from 'react'
import type { ReactNode } from 'react'
import { hasDeviceCode, isDeviceCodeEnabled, verifyDeviceCode } from '../deviceCode'

/** 冷启动锁：设置了设备控制码且开启“启动时输入”时，先要求输入控制码 */
export function AppGate({ children }: { children: ReactNode }) {
  const [locked, setLocked] = useState(() => hasDeviceCode() && isDeviceCodeEnabled())
  const [value, setValue] = useState('')
  const [error, setError] = useState(false)
  const [busy, setBusy] = useState(false)

  if (!locked) return <>{children}</>

  const submit = async () => {
    if (!value || busy) return
    setBusy(true)
    const ok = await verifyDeviceCode(value)
    setBusy(false)
    if (ok) {
      setLocked(false)
      return
    }
    setError(true)
    setValue('')
  }

  return (
    <div className="lock-page">
      <div className="lock-box">
        <img className="lock-logo" src="/icons/bee/icon-rounded.png" alt="" />
        <b>输入设备控制码</b>
        <p>这台设备已开启启动验证</p>
        <input
          value={value}
          onChange={(event) => {
            setValue(event.target.value)
            setError(false)
          }}
          className="secure-input"
          type="text"
          autoComplete="off"
          autoFocus
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          onKeyDown={(event) => {
            if (event.key === 'Enter') void submit()
          }}
          placeholder="控制码"
        />
        {error && <span className="lock-error">控制码不正确</span>}
        <button type="button" className="shell-primary wide" disabled={!value || busy} onClick={() => void submit()}>
          {busy ? '验证中…' : '解锁'}
        </button>
        <small>忘记控制码：清除应用数据后重新设置（会删除已装应用与设置）</small>
      </div>
    </div>
  )
}
