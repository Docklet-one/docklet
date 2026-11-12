import { useMemo, useState } from 'react'
import { isUninstallCheckEnabled, makeArithmetic } from '../deviceCode'

/** 卸载确认：开启“二次确认”后需要答对一道加减法 */
export function UninstallConfirm({
  name,
  onCancel,
  onConfirm,
}: {
  name: string
  onCancel: () => void
  onConfirm: () => void | Promise<void>
}) {
  const check = isUninstallCheckEnabled()
  const question = useMemo(() => (check ? makeArithmetic() : null), [check])
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const ok = !question || (value.trim() !== '' && Number(value) === question.answer)

  return (
    <div
      className={`confirm-scrim${editing ? ' is-editing' : ''}`}
      role="dialog"
      aria-modal="true"
      aria-label={`卸载应用：${name}`}
      onClick={() => {
        if (!busy) onCancel()
      }}
    >
      <div className="confirm-box" onClick={(event) => event.stopPropagation()}>
        <b>卸载应用</b>
        <p>卸载后可能仍有缓存残留。</p>
        <p>可以回到应用，执行“清除全部数据 / 恢复初始化”后再卸载。</p>
        <p>（是否有该入口，由各应用自身设计决定）</p>
        <p>清除和卸载都会导致配置、数据丢失，请自行记录、备份必要信息。</p>
        <div className="confirm-rule" />
        {question && (
          <div className="confirm-question">
            <span>
              {question.text} = ?
            </span>
            <input
              value={value}
              onChange={(event) => setValue(event.target.value.replace(/[^\d]/g, '').slice(0, 2))}
              inputMode="numeric"
              placeholder="输入答案"
              autoFocus
              onFocus={() => setEditing(true)}
            />
          </div>
        )}
        <div className="confirm-actions">
          <button type="button" onClick={onCancel} disabled={busy}>
            取消
          </button>
          <button
            type="button"
            className="is-danger"
            disabled={!ok || busy}
            onClick={async () => {
              setBusy(true)
              try {
                await onConfirm()
              } finally {
                setBusy(false)
              }
            }}
          >
            仍要卸载
          </button>
        </div>
      </div>
    </div>
  )
}
