import { useMemo } from 'react'
import type { TaskView, Workspace } from '../types/task'
import { statusLabels, getNextActionText } from '../domain/taskLabels'

type TaskHeaderProps = {
  task: TaskView | null
  workspace?: Workspace | null
  isMockMode: boolean
  isLoading: boolean
  onRefresh: () => void
  onReset: () => void
  onCreate: () => void
}

const statusColor: Record<string, string> = {
  CREATED: 'var(--primary)',
  PLANNED: 'var(--primary)',
  WAIT_CONFIRM: 'var(--warning)',
  RUNNING: 'var(--primary)',
  DELIVERED: 'var(--success)',
  FAILED: 'var(--danger)',
}

export function TaskHeader({ task, workspace, isMockMode, isLoading, onRefresh, onReset, onCreate }: TaskHeaderProps) {
  const status = workspace?.status ?? task?.status ?? 'IDLE'
  const statusText = workspace?.displayStatus ?? (task ? statusLabels[task.status] : '等待任务')
  const nextText = workspace?.nextAction ? getNextActionText(workspace.nextAction) : (task ? getNextActionText(task.nextAction) : '点击按钮创建模拟任务开始体验')
  const title = workspace?.title ?? workspace?.inputSummary ?? task?.inputText ?? 'Agent Pilot 任务中心'

  const metaItems = useMemo(() => {
    const source = workspace ?? task
    if (!source) return []
    return [
      { label: '任务ID', value: source.taskId.slice(0, 8) },
      { label: '发起人', value: source.userId || '—' },
      { label: '来源', value: source.source || '飞书聊天' },
      { label: '更新时间', value: new Date(source.updatedAt ?? task?.updatedAt ?? new Date()).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    ]
  }, [workspace, task])

  return (
    <header className="task-header-section reveal">
      <div className="task-header-inner">
        {/* 顶部标签行 */}
        <div className="task-header-kicker">
          <span className="kicker-badge kicker-mode">{isMockMode ? '演示模式' : 'API 模式'}</span>
          {(task || workspace) && (
            <span
              className="kicker-badge kicker-status"
              style={{ '--status-color': statusColor[status] || 'var(--muted)' } as React.CSSProperties}
            >
              <span className="status-dot" />
              {statusText}
            </span>
          )}
        </div>

        {/* 主标题区 */}
        <div className="task-header-body">
          <h1 className="task-title">
            {title}
          </h1>
          <p className="task-subtitle">{nextText}</p>
        </div>

        {/* 元信息行 */}
        {metaItems.length > 0 && (
          <div className="task-header-meta">
            {metaItems.map((item) => (
              <div className="meta-item" key={item.label}>
                <span className="meta-label">{item.label}</span>
                <span className="meta-value">{item.value}</span>
              </div>
            ))}
          </div>
        )}

        {/* 操作按钮组 */}
        <div className="task-header-actions">
          {(!task && !workspace) ? (
            <>
              <button className="btn btn-primary btn-lg" disabled={isLoading} onClick={onCreate}>
                <span className="btn-icon">🚀</span>
                创建模拟任务
              </button>
            </>
          ) : (
            <>
              <button className="btn btn-secondary" disabled={isLoading} onClick={onRefresh}>
                <span className="btn-icon">↻</span>
                刷新
              </button>
              <button className="btn btn-ghost" disabled={isLoading} onClick={onReset}>
                <span className="btn-icon">✕</span>
                重置
              </button>
            </>
          )}
        </div>
      </div>

      {/* 装饰背景 */}
      <div className="task-header-glow" aria-hidden="true" />
    </header>
  )
}
