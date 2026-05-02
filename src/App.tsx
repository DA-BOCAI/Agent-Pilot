import { useMemo } from 'react'
import { TaskHeader } from './components/TaskHeader'
import { StepNavigator } from './components/StepNavigator'
import { PreviewPanel } from './components/PreviewPanel'
import { ActionPanel } from './components/ActionPanel'
import { AdjustmentsPanel } from './components/AdjustmentsPanel'
import { OutputsPanel } from './components/OutputsPanel'
import { TimelinePanel } from './components/TimelinePanel'
import { EmptyState } from './components/EmptyState'
import { getNextActionText, statusLabels, stepStatusLabels } from './domain/taskLabels'
import { useTaskWorkflow } from './hooks/useTaskWorkflow'
import './App.css'

function App() {
  const {
    confirmStepId,
    error,
    handleConfirm,
    handleCreateTask,
    handleDeterministicUpdate,
    handleExecuteTask,
    handleNaturalLanguageRefine,
    handlePlanTask,
    handlePreviewPresentation,
    handleRefresh,
    handleReset,
    isLoading,
    isMockMode,
    sseConnected,
    task,
    workspace,
  } = useTaskWorkflow()

  const activeStepCode = useMemo(() => {
    if (workspace?.steps?.length) {
      const runningStep = workspace.steps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
      return runningStep?.id ?? null
    }
    if (!task) return null
    if (confirmStepId) return confirmStepId
    const running = task.planSteps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
    return running?.stepId ?? running?.code ?? null
  }, [confirmStepId, task, workspace])

  const previewArtifacts = useMemo(() => task?.artifacts.filter((artifact) => artifact.type !== 'delivery') ?? [], [task])

  return (
    <div className="app-shell">
      {/* 1. 任务标题区域 */}
      <TaskHeader
        task={task}
        workspace={workspace}
        isMockMode={isMockMode}
        isLoading={isLoading}
        onRefresh={handleRefresh}
        onReset={handleReset}
        onCreate={handleCreateTask}
      />

      <StepNavigator
        steps={task?.planSteps ?? []}
        workspaceSteps={workspace?.steps}
        activeStepCode={activeStepCode}
      />

      <PreviewPanel
        artifacts={previewArtifacts}
        workspacePreview={workspace?.preview}
        isDelivered={task?.status === 'DELIVERED' || workspace?.status === 'DELIVERED'}
      />

      <section className="workspace-section reveal">
        <div className="workspace-inner">
          <div className="workspace-header">
            <div className="section-title-wrap">
              <h2 className="section-title">任务详情</h2>
              {(task || workspace) && (
                <span className={`status-pill status-${(workspace?.status ?? task?.status ?? 'idle').toLowerCase()}`}>
                  {workspace?.displayStatus ?? (task ? statusLabels[task.status] : '')}
                </span>
              )}
              {sseConnected && (
                <span className="sse-status-badge connected">SSE 已连接</span>
              )}
            </div>
          </div>

          <div className="workspace-body">
            <div className="workspace-main">
              <article className="surface context-card">
                <h3>飞书上下文</h3>
                <p className="context-text">{(workspace?.inputSummary ?? task?.inputText) || '任务尚未创建。创建后将展示从飞书聊天 API 获取的原始需求摘要。'}</p>
                <dl className="context-meta">
                  <div>
                    <dt>来源</dt>
                    <dd>{(workspace?.source ?? task?.source) || '飞书聊天'}</dd>
                  </div>
                  <div>
                    <dt>发起人</dt>
                    <dd>{(workspace?.userId ?? task?.userId) || '等待飞书回调'}</dd>
                  </div>
                  <div>
                    <dt>当前状态</dt>
                    <dd>{workspace?.displayStatus ?? (task ? statusLabels[task.status] : '等待创建任务')}</dd>
                  </div>
                  <div>
                    <dt>下一步</dt>
                    <dd>{workspace?.nextAction ? getNextActionText(workspace.nextAction) : (task ? getNextActionText(task.nextAction) : '下一步：创建任务')}</dd>
                  </div>
                </dl>
              </article>

              <article className="surface steps-card">
                <h3>步骤时间线</h3>
                {(() => {
                  const displaySteps = workspace?.steps ?? task?.planSteps
                  if (!displaySteps?.length) {
                    return <EmptyState title="暂无规划步骤" detail="调用生成规划接口后会展示完整步骤树。" />
                  }
                  return (
                    <div className="step-timeline">
                      {displaySteps.map((step) => {
                        const stepId = 'id' in step ? step.id : step.stepId
                        const stepCode = 'code' in step ? step.code : `STEP_${stepId}`
                        const stepName = 'title' in step ? step.title : step.name
                        const stepStatus = step.status
                        const stepDisplayStatus = 'displayStatus' in step ? step.displayStatus : null
                        const requiresConfirm = step.requiresConfirm
                        return (
                          <div
                            className={`timeline-row step-status-${stepStatus.toLowerCase()} ${
                              stepId === activeStepCode || stepCode === activeStepCode ? 'is-active' : ''
                            }`}
                            key={stepId}
                          >
                            <span className="timeline-code">{stepCode}</span>
                            <strong className="timeline-name">{stepName}</strong>
                            <em className="timeline-status">{stepDisplayStatus ?? stepStatusLabels[stepStatus]}</em>
                            <small className="timeline-confirm">{requiresConfirm ? '需要确认' : '自动执行'}</small>
                          </div>
                        )
                      })}
                    </div>
                  )
                })()}
              </article>
            </div>

            <div className="workspace-side">
              <article className={`surface action-card action-status-${(workspace?.status ?? task?.status ?? 'idle').toLowerCase()}`}>
                <h3>当前动作</h3>
                <p className="action-summary">
                  {workspace?.nextAction
                    ? getNextActionText(workspace.nextAction)
                    : task
                      ? getNextActionText(task.nextAction)
                      : '等待飞书消息卡片触发任务，或使用演示按钮创建模拟任务。'}
                </p>
                {error ? <div className="error-banner">{error}</div> : null}
                <ActionPanel
                  disabled={isLoading}
                  onConfirm={handleConfirm}
                  onCreate={handleCreateTask}
                  onExecute={handleExecuteTask}
                  onPlan={handlePlanTask}
                  onPreviewPresentation={handlePreviewPresentation}
                  onRefresh={handleRefresh}
                  onReset={handleReset}
                  task={task}
                  workspace={workspace}
                />
              </article>
            </div>
          </div>
        </div>
      </section>

      <section className="preview-adjustments-section reveal">
        <div className="preview-adjustments-inner">
          <div className="preview-area">
            <AdjustmentsPanel
              adjustments={workspace?.adjustments ?? { available: false, stepId: '' }}
              onDeterministicUpdate={handleDeterministicUpdate}
              onNaturalLanguageRefine={handleNaturalLanguageRefine}
              disabled={isLoading}
            />
          </div>
        </div>
      </section>

      <OutputsPanel outputs={workspace?.outputs ?? []} />

      <TimelinePanel timeline={workspace?.timeline ?? []} />

      {/* 页脚 */}
      <footer className="app-footer">
        <p>Agent Pilot · 飞书协作智能助手</p>
      </footer>
    </div>
  )
}

export default App
