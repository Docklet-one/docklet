import { useEffect } from 'react'
import { pushBackHandler } from '../back'
import type { CatalogEntry } from '../catalog'
import type { HostedApp } from '../native/appHost'

const PERMISSION_LABELS: Record<string, string> = {
  notifications: '通知',
}

const CAPABILITY_LABELS: Record<string, string> = {
  fs: '文件读写',
  openExternal: '打开外部链接',
  clipboard: '剪贴板',
  share: '系统分享',
  background: '后台运行',
  download: '下载文件',
  notify: '发送通知',
  storage: '本地存储',
}

import { formatSize, isNewer } from '../format'

function capabilityLabel(name: string): string {
  return CAPABILITY_LABELS[name] ?? name
}

export function AppDetailPage({
  app,
  entry,
  onClose,
  onOpen,
  onRemove,
  onInstall,
  onCancel,
  installing,
  installFailed,
  installProgress,
  catalogLoading,
}: {
  app?: HostedApp
  entry?: CatalogEntry
  onClose: () => void
  onOpen?: () => void
  onRemove?: () => void
  onInstall?: () => void
  onCancel?: () => void
  installing?: boolean
  installFailed?: boolean
  installProgress?: number | null
  catalogLoading?: boolean
}) {
  useEffect(() => pushBackHandler(onClose), [onClose])

  const installed = Boolean(app)
  const name = app?.name ?? entry?.name ?? ''
  const icon = app?.icon || entry?.icon || '▢'
  const version = app?.version ?? entry?.version ?? ''
  const intro = entry?.description || app?.description || entry?.tagline || app?.tagline || '暂无简介'
  const author = entry?.author || app?.author || '未署名'
  const authorContact = entry?.authorContact || app?.authorContact || ''
  const capabilities = app?.capabilities ?? entry?.capabilities ?? []
  const permissions = app?.permissions ?? entry?.permissions ?? []
  const installable = Boolean(entry?.url && onInstall)
  const canUpdate = Boolean(installed && entry && isNewer(entry.version, version) && installable)

  return (
    <div className="detail-page">
      <header className="detail-top">
        <button type="button" className="nav-back" aria-label="返回" onClick={onClose}>
          ‹
        </button>
        <b>应用详情</b>
        <span className="detail-top-gap" />
      </header>

      <div className="detail-body">
        <div className="detail-hero">
          <span className="detail-icon">{icon}</span>
          <b>{name}</b>
        </div>

        <div className="detail-stats">
          <div>
            <span>当前版本</span>
            <b>v{version}</b>
          </div>
          <div>
            <span>文件大小</span>
            <b>{formatSize(app?.size ?? entry?.size)}</b>
          </div>
          <div>
            <span>{app?.releasedAt || entry?.releasedAt ? '更新时间' : '发布时间'}</span>
            <b>{app?.releasedAt || entry?.releasedAt || app?.publishedAt || entry?.publishedAt || '—'}</b>
          </div>
        </div>

        <section className="detail-section">
          <h4>简介</h4>
          <p className="detail-intro">{intro}</p>
          {!installed && entry && (
            <div className="detail-line">
              <span>分类</span>
              <b>{entry.category}</b>
            </div>
          )}
        </section>

        <section className="detail-section">
          <h4>作者</h4>
          <div className="detail-author">
            <b>{author}</b>
            <span>{authorContact || '—'}</span>
          </div>
        </section>

        <section className="detail-section">
          <h4>权限</h4>
          {permissions.length === 0 ? (
            <p className="detail-muted">该应用不请求系统权限</p>
          ) : (
            permissions.map((permission) => (
              <div className="detail-line" key={permission.name}>
                <span>{PERMISSION_LABELS[permission.name] ?? permission.name}</span>
                <b>{permission.reason || '—'}</b>
              </div>
            ))
          )}
        </section>

        <section className="detail-section">
          <h4>能力</h4>
          {capabilities.length === 0 ? (
            <p className="detail-muted">无特殊能力</p>
          ) : (
            <>
              <div className="detail-tags">
                {capabilities.map((capability) => (
                  <span key={capability} className={installed ? 'ok' : undefined}>
                    {capabilityLabel(capability)}
                  </span>
                ))}
              </div>
              <p className="detail-hint">
                {installed ? '已按安装清单校验生效' : '作者声明，安装后校验'}
              </p>
            </>
          )}
        </section>
      </div>

      <footer className="detail-footer">
        {installing ? (
          <>
            <div className="detail-progress">
              获取中…{installProgress == null ? '' : ` ${installProgress}%`}
            </div>
            <button type="button" className="detail-cancel" onClick={onCancel}>
              取消
            </button>
          </>
        ) : installed ? (
          canUpdate ? (
            <button type="button" className="detail-open" onClick={onInstall}>
              更新应用
            </button>
          ) : (
            <button type="button" className="detail-open" onClick={onOpen}>
              打开
            </button>
          )
        ) : installable ? (
          <button type="button" className="detail-open" onClick={onInstall}>
            {installFailed ? '获取失败 重新安装' : '安装'}
          </button>
        ) : catalogLoading ? (
          <button type="button" className="detail-open" disabled>
            加载中…
          </button>
        ) : (
          <button type="button" className="detail-open" disabled>
            暂不可用
          </button>
        )}
        {!installing && installed && onRemove && (
          <button type="button" className="detail-trash" onClick={onRemove} aria-label="卸载">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M3 6h18" />
              <path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2" />
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
              <path d="M10 11v6M14 11v6" />
            </svg>
          </button>
        )}
      </footer>
    </div>
  )
}
