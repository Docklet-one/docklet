type BackHandler = () => void

/**
 * 安卓返回键 / 返回手势的处理器栈。
 * 最后打开的浮层最后注册，返回时最先关闭。
 */
const stack: BackHandler[] = []

export function pushBackHandler(handler: BackHandler): () => void {
  stack.push(handler)
  return () => {
    const index = stack.lastIndexOf(handler)
    if (index >= 0) stack.splice(index, 1)
  }
}

/** 有浮层就关掉并返回 true；没有可关的返回 false（交给系统退出 App） */
export function triggerBack(): boolean {
  const handler = stack[stack.length - 1]
  if (!handler) return false
  handler()
  return true
}
