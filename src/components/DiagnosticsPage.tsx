import { useEffect, useState } from 'react'
import { App as NativeApp } from '@capacitor/app'
import { pushBackHandler } from '../back'
import { clearDiag, readDiag, type DiagEntry } from '../diag'

export function DiagnosticsPage({ onClose }: { onClose: () => void }) {
  const [logs, setLogs] = useState<DiagEntry[]>([])
  const [info, setInfo] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => pushBackHandler(onClose), [onClose])

  const load = () => {
    setLogs(readDiag().slice().reverse())
    void NativeApp.getInfo()
      .then((value) => setInfo(`v${value.version} (${value.build})`))
      .catch(() => setInfo('版本未知'))
  }

  useEffect(load, [])

  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 1600)
    return () => window.clearTimeout(timer)
  }, [copied])

  const copy = async () => {
    const text = [
      `Docklet ${info}`,
      `build ${__BUILD_ID__}`,
      `online ${navigator.onLine ? 'yes' : 'no'}`,
      '',
      ...logs.map((entry) => `${new Date(entry.time).toLocaleString()}  ${entry.event}  ${entry.detail ?? ''}`),
    ].join('\n')
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      /* 忽略 */
    }
  }

  return (
    <div className="panel">
      <header className="panel-top">
        <button type="button" className="nav-back" aria-label="返回" onClick={onClose}>
          ‹
        </button>
        <b>诊断信息</b>
        <span className="detail-top-gap" />
      </header>
      <div className="panel-body">
        <p className="diag-meta">
          {info} · build {__BUILD_ID__}
          <br />
          网络：{navigator.onLine ? '在线' : '离线'} · 日志 {logs.length} 条（仅本机）
        </p>
        <div className="diag-actions">
          <button type="button" className="sheet-ghost" onClick={copy}>
            {copied ? '已复制' : '复制日志'}
          </button>
          <button
            type="button"
            className="sheet-ghost"
            onClick={() => {
              clearDiag()
              load()
            }}
          >
            清空
          </button>
        </div>
        {logs.length === 0 ? (
          <p className="pane-empty">暂无日志。到设置里打开诊断日志后再复现问题。</p>
        ) : (
          <div className="diag-list">
            {logs.map((entry, index) => (
              <div className="diag-row" key={`${entry.time}-${index}`}>
                <span>{new Date(entry.time).toLocaleTimeString()}</span>
                <b>{entry.event}</b>
                {entry.detail && <i>{entry.detail}</i>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
