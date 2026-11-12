import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Icon, type IconName } from './Icons'
import { appOpenExternal } from '../native/appHost'

interface AboutPageProps {
  onClose: () => void
}

interface SectionLink {
  label: string
  value: string
  href: string
}

interface Section {
  key: string
  title: string
  icon: IconName
  body: string
  links?: SectionLink[]
}

/** 外部链接：原生交给系统浏览器，网页预览退回 window.open */
function openLink(href: string) {
  try {
    void appOpenExternal(href).catch(() => window.open(href, '_blank'))
  } catch {
    window.open(href, '_blank')
  }
}

const SECTIONS: Section[] = [
  {
    key: 'about',
    title: '软件介绍',
    icon: 'info',
    body: `Docklet 是一个轻量的 Android 应用坞：简单的小应用不必每个都封装成 APK，收进坞里就能用。

开发文档及更多信息，详见官网或开源地址。`,
  },
  {
    key: 'contact',
    title: '联系我们',
    icon: 'mail',
    body: `有问题、建议或合作意向，欢迎通过以下方式联系：`,
    links: [
      { label: '电子邮件', value: 'docklet@163.com', href: 'mailto:docklet@163.com' },
      { label: 'QQ 群', value: '558391173', href: 'https://qun.qq.com/' },
      { label: '官网', value: 'https://docklet.cn', href: 'https://docklet.cn' },
      {
        label: '开源地址',
        value: 'https://github.com/Docklet-one/docklet',
        href: 'https://github.com/Docklet-one/docklet',
      },
    ],
  },
  {
    key: 'takedown',
    title: '侵权处理',
    icon: 'shield',
    body: `应用内的资源来自网络收集、AI 生成和网友提供。

如果你认为本应用的图标、文案、素材等侵犯了你的合法权益，请通过「联系我们」告知，并说明涉及的具体内容；我们核实后会尽快处理。`,
  },
  {
    key: 'liability',
    title: '责任声明',
    icon: 'doc',
    body: `代码能开源的尽量开源；一些简单的小应用没必要单独开源。

如果不放心，建议先审查代码，再决定要不要安装。

作者不对使用中出现的软硬件问题、BUG 等一切负面后果负责。`,
  },
]

/** 设置页「关于与声明」：整页 + 手风琴折叠 */
export function AboutPage({ onClose }: AboutPageProps) {
  const [open, setOpen] = useState<string | null>(null)

  return createPortal(
    <div className="about-page" role="dialog" aria-modal="true" aria-label="关于与声明">
      <header className="about-head">
        <button type="button" className="nav-back" aria-label="返回" onClick={onClose}>
          ‹
        </button>
        <h1>关于与声明</h1>
        <span className="detail-top-gap" />
      </header>

      <div className="about-body">
        {SECTIONS.map((section) => {
          const expanded = open === section.key
          return (
            <section key={section.key} className={`about-item${expanded ? ' is-open' : ''}`}>
              <button
                type="button"
                className="about-item-head"
                aria-expanded={expanded}
                onClick={() => setOpen(expanded ? null : section.key)}
              >
                <span className="about-item-title">
                  <Icon name={section.icon} size={17} />
                  <span>{section.title}</span>
                </span>
                <i className="about-chevron" aria-hidden="true">
                  ▾
                </i>
              </button>
              <div className="about-item-body">
                <div>
                  <p>{section.body}</p>
                  {section.links && (
                    <div className="about-links">
                      {section.links.map((link) => (
                        <button
                          key={link.label + link.value}
                          type="button"
                          className="about-link"
                          onClick={() => openLink(link.href)}
                        >
                          <span>
                            <b>{link.label}</b>
                            <i>{link.value}</i>
                          </span>
                          <em aria-hidden="true">↗</em>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </section>
          )
        })}

        <p className="about-foot">© 2026 应用小坞 Docklet</p>
      </div>
    </div>,
    document.body,
  )
}
