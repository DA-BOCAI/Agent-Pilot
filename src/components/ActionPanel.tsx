import type { TaskView, Workspace, Confirmation } from '../types/task'

type ActionPanelProps = {
  disabled: boolean
  onConfirm: (approved: boolean) => void
  onPlan: () => void
  onRefresh: () => void
  onReset: () => void
  onWorkspaceConfirm: () => void
  onWorkspaceCancel: () => void
  task: TaskView | null
  workspace?: Workspace | null
}

function getConfirmationData(workspace?: Workspace | null, task?: TaskView | null): Confirmation | null {
  if (workspace?.confirmation?.available) {
    return workspace.confirmation
  }
  if (task?.status === 'WAIT_CONFIRM') {
    return {
      available: true,
      stepId: task.nextAction.replace('confirm:', ''),
    }
  }
  return null
}

function getActionState(workspace?: Workspace | null, task?: TaskView | null) {
  const status = workspace?.status ?? task?.status
  const nextAction = workspace?.nextAction ?? task?.nextAction
  return { status, nextAction }
}

export function ActionPanel({
  disabled,
  onConfirm,
  onPlan,
  onRefresh,
  onReset,
  onWorkspaceConfirm,
  onWorkspaceCancel,
  task,
  workspace,
}: ActionPanelProps) {
  const confirmation = getConfirmationData(workspace, task)
  const { status, nextAction } = getActionState(workspace, task)

  if (!task && !workspace) {
    return null
  }

  if (status === 'CREATED' && nextAction === 'plan') {
    return (
      <div className="action-stack">
        <button className="btn btn-primary" disabled={disabled} onClick={onPlan}>
          生成规划
        </button>
      </div>
    )
  }

  if ((status === 'PLANNED' || status === 'RUNNING') && nextAction === 'execute') {
    return (
      <div className="action-stack split">
        <button className="btn btn-primary" disabled={disabled} onClick={onWorkspaceConfirm}>
          确认
        </button>
        <button className="btn btn-secondary" disabled={disabled} onClick={onWorkspaceCancel}>
          取消
        </button>
      </div>
    )
  }

  if (status === 'WAIT_CONFIRM' && confirmation?.available) {
    return (
      <div className="action-stack split">
        {confirmation.title && (
          <div className="confirmation-title">{confirmation.title}</div>
        )}
        {confirmation.description && (
          <div className="confirmation-description">{confirmation.description}</div>
        )}
        <button className="btn btn-primary" disabled={disabled} onClick={() => onConfirm(true)}>
          通过
        </button>
        <button className="btn btn-secondary" disabled={disabled} onClick={() => onConfirm(false)}>
          拒绝
        </button>
      </div>
    )
  }

  if (status === 'DELIVERED') {
    return null
  }

  if (status === 'FAILED') {
    return (
      <div className="action-stack">
        <button className="btn btn-primary" disabled={disabled} onClick={onReset}>
          重新创建
        </button>
        <button className="btn btn-secondary" disabled={disabled} onClick={onRefresh}>
          刷新任务
        </button>
      </div>
    )
  }

  return (
    <div className="action-stack">
      <button className="btn btn-secondary" disabled={disabled} onClick={onRefresh}>
        刷新任务
      </button>
    </div>
  )
}
