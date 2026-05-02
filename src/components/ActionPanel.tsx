import type { TaskView, Workspace, Confirmation } from '../types/task'

type ActionPanelProps = {
  disabled: boolean
  onConfirm: (approved: boolean) => void
  onCreate: () => void
  onExecute: () => void
  onPlan: () => void
  onPreviewPresentation: () => void
  onRefresh: () => void
  onReset: () => void
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
  onCreate,
  onExecute,
  onPlan,
  onPreviewPresentation,
  onRefresh,
  onReset,
  task,
  workspace,
}: ActionPanelProps) {
  const confirmation = getConfirmationData(workspace, task)
  const { status, nextAction } = getActionState(workspace, task)

  if (!task && !workspace) {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onCreate}>
          创建模拟任务
        </button>
        <button disabled={disabled} onClick={onPreviewPresentation}>
          生成PPT预览
        </button>
      </div>
    )
  }

  if (status === 'CREATED' && nextAction === 'plan') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onPlan}>
          生成规划
        </button>
        <button disabled={disabled} onClick={onPreviewPresentation}>
          生成PPT预览
        </button>
      </div>
    )
  }

  if ((status === 'PLANNED' || status === 'RUNNING') && nextAction === 'execute') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onExecute}>
          执行任务
        </button>
        <button disabled={disabled} onClick={onPreviewPresentation}>
          生成PPT预览
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
        <button className="primary" disabled={disabled} onClick={() => onConfirm(true)}>
          通过
        </button>
        <button disabled={disabled} onClick={() => onConfirm(false)}>
          拒绝
        </button>
        <button disabled={disabled} onClick={onPreviewPresentation}>
          生成PPT预览
        </button>
      </div>
    )
  }

  if (status === 'DELIVERED') {
    return (
      <div className="action-stack">
        <p className="action-note">交付物已在下方展示，可回到飞书继续协作。</p>
        <button className="primary" disabled={disabled} onClick={onPreviewPresentation}>
          重新生成PPT预览
        </button>
        <button disabled={disabled}>返回 IM</button>
      </div>
    )
  }

  if (status === 'FAILED') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onReset}>
          重新创建
        </button>
        <button disabled={disabled} onClick={onRefresh}>
          刷新任务
        </button>
        <button disabled={disabled} onClick={onPreviewPresentation}>
          生成PPT预览
        </button>
      </div>
    )
  }

  return (
    <div className="action-stack">
      <button disabled={disabled} onClick={onRefresh}>
        刷新任务
      </button>
      <button disabled={disabled} onClick={onPreviewPresentation}>
        生成PPT预览
      </button>
    </div>
  )
}
