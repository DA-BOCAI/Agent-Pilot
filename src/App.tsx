import { useMemo } from 'react'
import { TaskHeader } from './components/TaskHeader'
import { StepNavigator } from './components/StepNavigator'
import { PreviewPanel } from './components/PreviewPanel'
import { ActionPanel } from './components/ActionPanel'
import { AdjustmentsPanel } from './components/AdjustmentsPanel'
import { OutputsPanel } from './components/OutputsPanel'
import { getNextActionText } from './domain/taskLabels'
import { useTaskWorkflow } from './hooks/useTaskWorkflow'
import './App.css'

function App() {
  const {
    confirmStepId,
    error,
    handleConfirm,
    handleCreateTask,
    handleDeterministicUpdate,
    handleNaturalLanguageRefine,
    handleExecuteTask,
    handleNaturalLanguageRefine: _handleNaturalLanguageRefine,
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

  const previewArtifacts = useMemo(() => task?.artifacts ?? [], [task])

  const displaySteps = workspace?.steps && workspace.steps.length > 0
    ? workspace.steps.map((step, index) => ({
        stepId: step.id,
        code: `STEP_${index + 1}`,
        name: step.title,
        status: step.status,
        requiresConfirm: step.requiresConfirm,
        displayStatus: step.displayStatus,
        description: step.description,
        artifactType: step.artifactType,
      }))
    : (task?.planSteps ?? [])

  const doneCount = displaySteps.filter((s) => s.status === 'DONE' || s.status === 'APPROVED').length
  const progress = displaySteps.length ? Math.round((doneCount / displaySteps.length) * 100) : 0

  return (
    <div className="app-shell">
      {/* ===== 顶部紧凑信息栏 ===== */}
      <header className="top-bar">
        <div className="top-bar-inner">
          <TaskHeader
            task={task}
            workspace={workspace}
            isMockMode={isMockMode}
            isLoading={isLoading}
            onRefresh={handleRefresh}
            onReset={handleReset}
            onCreate={handleCreateTask}
          />
          {/* 全局进度条 */}
          {displaySteps.length > 0 && (
            <div className="top-progress" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
              <div className="top-progress-track">
                <div className="top-progress-fill" style={{ width: `${progress}%` }} />
              </div>
              <span className="top-progress-text">{doneCount}/{displaySteps.length} 步骤 · {progress}%</span>
            </div>
          )}
        </div>
      </header>

      {/* ===== 主体：侧边栏 + 预览区 ===== */}
      <div className="main-layout">
        {/* 左侧边栏 */}
        <aside className="sidebar">
          <div className="sidebar-inner">
            {/* 步骤导航 */}
            <div className="sidebar-section">
              <StepNavigator
                steps={task?.planSteps ?? []}
                workspaceSteps={workspace?.steps}
                activeStepCode={activeStepCode}
              />
            </div>

            {/* 交付产物 */}
            <div className="sidebar-section">
              <OutputsPanel outputs={workspace?.outputs ?? []} />
            </div>

            {/* SSE 状态 */}
            {sseConnected && (
              <div className="sidebar-footer">
                <span className="sse-dot" />
                SSE 已连接
              </div>
            )}
          </div>
        </aside>

        {/* 主预览区 */}
        <main className="main-preview">
          <PreviewPanel
            artifacts={previewArtifacts}
            workspacePreview={workspace?.preview}
            isDelivered={task?.status === 'DELIVERED' || workspace?.status === 'DELIVERED'}
          />
        </main>
      </div>

      {/* ===== 底部操作栏 ===== */}
      <footer className="bottom-bar">
        <div className="bottom-bar-inner">
          <div className="bottom-bar-info">
            <span className="bottom-bar-label">当前动作</span>
            <span className="bottom-bar-action">
              {workspace?.nextAction
                ? getNextActionText(workspace.nextAction)
                : task
                  ? getNextActionText(task.nextAction)
                  : '等待飞书消息卡片触发任务，或使用演示按钮创建模拟任务。'}
            </span>
            {error && <span className="bottom-bar-error">{error}</span>}
          </div>
          <div className="bottom-bar-controls">
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
          </div>
        </div>

        {/* 调整面板（条件展开） */}
        <AdjustmentsPanel
          adjustments={workspace?.adjustments ?? { available: false, stepId: '' }}
          onDeterministicUpdate={handleDeterministicUpdate}
          onNaturalLanguageRefine={handleNaturalLanguageRefine}
          disabled={isLoading}
        />
      </footer>
    </div>
  )
}

export default App
