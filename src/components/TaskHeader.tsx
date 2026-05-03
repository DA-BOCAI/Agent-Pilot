import type { TaskView, Workspace } from '../types/task'
import { statusLabels } from '../domain/taskLabels'

type TaskHeaderProps = {
  task: TaskView | null
  workspace?: Workspace | null
  isLoading: boolean
  onRefresh: () => void
  onReset: () => void
}

const statusColor: Record<string, string> = {
  CREATED: 'var(--primary)',
  PLANNED: 'var(--primary)',
  WAIT_CONFIRM: 'var(--warning)',
  RUNNING: 'var(--primary)',
  DELIVERED: 'var(--success)',
  FAILED: 'var(--danger)',
}

export function TaskHeader({ task, workspace, isLoading, onRefresh, onReset }: TaskHeaderProps) {
  const status = workspace?.status ?? task?.status ?? 'IDLE'
  const statusText = workspace?.displayStatus ?? (task ? statusLabels[task.status] : '等待任务')
  const title = workspace?.title ?? workspace?.inputSummary ?? task?.inputText ?? 'Agent Pilot 任务中心'

  return (
    <div className="task-header-row">
      <div className="task-header-main">
        <div className="task-header-badges">
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
        {(task || workspace) ? (
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
        ) : null}
      </div>
    </div>
  )
}
