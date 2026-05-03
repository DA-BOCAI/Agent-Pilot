import type { TaskView, Workspace } from '../types/task'
import { statusLabels } from '../domain/taskLabels'

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
  const title = workspace?.title ?? workspace?.inputSummary ?? task?.inputText ?? 'Agent Pilot 任务中心'

  return (
    <div className="task-header-row">
      <div className="task-header-main">
        <div className="task-header-badges">
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
        <h1 className="task-title">{title}</h1>
      </div>

      <div className="task-header-actions">
        {(!task && !workspace) ? (
          <button className="btn btn-primary" disabled={isLoading} onClick={onCreate}>
            <span className="btn-icon">🚀</span>
            创建模拟任务
          </button>
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
  )
}
