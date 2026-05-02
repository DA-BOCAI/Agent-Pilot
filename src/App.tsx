import { useMemo } from 'react'
import { TaskHeader } from './components/TaskHeader'
import { StepNavigator } from './components/StepNavigator'
import { PreviewPanel } from './components/PreviewPanel'
import { ActionPanel } from './components/ActionPanel'
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
    handleExecuteTask,
    handlePlanTask,
    handlePreviewPresentation,
    handleRefresh,
    handleReset,
    isLoading,
    isMockMode,
    task,
  } = useTaskWorkflow()

  const activeStepCode = useMemo(() => {
    if (!task) return null
    if (confirmStepId) return confirmStepId
    const running = task.planSteps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
    return running?.stepId ?? running?.code ?? null
  }, [confirmStepId, task])

  const previewArtifacts = useMemo(() => task?.artifacts.filter((artifact) => artifact.type !== 'delivery') ?? [], [task])

  return (
    <div className="app-shell">
      {/* 1. 任务标题区域 */}
      <TaskHeader
        task={task}
        isMockMode={isMockMode}
        isLoading={isLoading}
        onRefresh={handleRefresh}
        onReset={handleReset}
        onCreate={handleCreateTask}
      />

      {/* 2. 任务步骤区域 */}
      <StepNavigator
        steps={task?.planSteps ?? []}
        activeStepCode={activeStepCode}
      />

      {/* 3. 预览区域 */}
      <PreviewPanel
        artifacts={previewArtifacts}
        isDelivered={task?.status === 'DELIVERED'}
      />

      {/* 工作区：任务详情与操作 */}
      <section className="workspace-section reveal">
        <div className="workspace-inner">
          <div className="workspace-header">
            <div className="section-title-wrap">
              <h2 className="section-title">任务详情</h2>
              {task && (
                <span className={`status-pill status-${task.status.toLowerCase()}`}>
                  {statusLabels[task.status]}
                </span>
              )}
            </div>
          </div>

          <div className="workspace-body">
            {/* 左侧：上下文与步骤时间线 */}
            <div className="workspace-main">
              <article className="surface context-card">
                <h3>飞书上下文</h3>
                <p className="context-text">{task?.inputText || '任务尚未创建。创建后将展示从飞书聊天 API 获取的原始需求摘要。'}</p>
                <dl className="context-meta">
                  <div>
                    <dt>来源</dt>
                    <dd>飞书聊天</dd>
                  </div>
                  <div>
                    <dt>发起人</dt>
                    <dd>{task?.userId || '等待飞书回调'}</dd>
                  </div>
                  <div>
                    <dt>当前状态</dt>
                    <dd>{task ? statusLabels[task.status] : '等待创建任务'}</dd>
                  </div>
                  <div>
                    <dt>下一步</dt>
                    <dd>{task ? getNextActionText(task.nextAction) : '下一步：创建任务'}</dd>
                  </div>
                </dl>
              </article>

              <article className="surface steps-card">
                <h3>步骤时间线</h3>
                {task?.planSteps.length ? (
                  <div className="step-timeline">
                    {task.planSteps.map((step) => (
                      <div
                        className={`timeline-row step-status-${step.status.toLowerCase()} ${
                          step.stepId === activeStepCode || step.code === activeStepCode ? 'is-active' : ''
                        }`}
                        key={step.stepId}
                      >
                        <span className="timeline-code">{step.code}</span>
                        <strong className="timeline-name">{step.name}</strong>
                        <em className="timeline-status">{stepStatusLabels[step.status]}</em>
                        <small className="timeline-confirm">{step.requiresConfirm ? '需要确认' : '自动执行'}</small>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState title="暂无规划步骤" detail="调用生成规划接口后会展示完整步骤树。" />
                )}
              </article>
            </div>

            {/* 右侧：当前动作 */}
            <div className="workspace-side">
              <article className={`surface action-card action-status-${task?.status.toLowerCase() ?? 'idle'}`}>
                <h3>当前动作</h3>
                <p className="action-summary">
                  {task ? getNextActionText(task.nextAction) : '等待飞书消息卡片触发任务，或使用演示按钮创建模拟任务。'}
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
                />
              </article>
            </div>
          </div>
        </div>
      </section>

      {/* 页脚 */}
      <footer className="app-footer">
        <p>Agent Pilot · 飞书协作智能助手</p>
      </footer>
    </div>
  )
}

export default App
