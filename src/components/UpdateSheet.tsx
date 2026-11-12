import { pushBackHandler } from '../back'
import { useEffect } from 'react'

export function UpdateSheet({
  versionName,
  notes,
  apk,
  onClose,
  onSkip,
}: {
  versionName: string
  notes?: string
  apk: string
  onClose: () => void
  onSkip: () => void
}) {
  useEffect(() => pushBackHandler(onClose), [onClose])

  return (
    <div className="sheet-scrim" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="sheet" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="update-head">
          <b>有新版本</b>
          <span>v{versionName}</span>
        </div>
        {notes && <p className="update-notes">{notes}</p>}
        <div className="sheet-actions is-single">
          <button
            type="button"
            className="sheet-primary"
            onClick={() => {
              window.open(apk, '_blank', 'noopener')
              onClose()
            }}
          >
            下载更新
          </button>
        </div>
        <div className="sheet-actions">
          <button type="button" className="sheet-ghost" onClick={onClose}>
            稍后
          </button>
          <button type="button" className="sheet-ghost" onClick={onSkip}>
            跳过此版本
          </button>
        </div>
      </div>
    </div>
  )
}
