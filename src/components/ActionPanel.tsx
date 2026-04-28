import type { TaskView } from '../types/task'

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
}: ActionPanelProps) {
  // 未创建任务时只给演示入口；真实飞书入口未来可从后端回调触发。
  if (!task) {
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

  // 页面动作完全由 TaskView.status + nextAction 决定，不让用户看到无关操作。
  if (task.status === 'CREATED' && task.nextAction === 'plan') {
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

  if ((task.status === 'PLANNED' || task.status === 'RUNNING') && task.nextAction === 'execute') {
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

  // WAIT_CONFIRM 是唯一需要用户做二选一确认的状态。
  if (task.status === 'WAIT_CONFIRM' && task.nextAction.startsWith('confirm:')) {
    return (
      <div className="action-stack split">
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

  if (task.status === 'DELIVERED') {
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

  if (task.status === 'FAILED') {
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
