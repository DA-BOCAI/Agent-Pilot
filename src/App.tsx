import { useMemo, useState, useEffect } from 'react'
import { TaskHeader } from './components/TaskHeader'
import { StepNavigator } from './components/StepNavigator'
import { PreviewPanel } from './components/PreviewPanel'
import { ActionPanel } from './components/ActionPanel'
// import { AdjustmentsPanel } from './components/AdjustmentsPanel'
import { OutputsPanel } from './components/OutputsPanel'
import { NaturalLanguageRefineInput } from './components/NaturalLanguageRefineInput'
import { ToastContainer } from './components/Toast'
import { ToastProvider } from './contexts/ToastContext'
import { useToast } from './contexts/useToast'
import { getNextActionText } from './domain/taskLabels'
import { useTaskWorkflow } from './hooks/useTaskWorkflow'
import './App.css'

// 检测是否为移动端的 hook
function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(() => window.innerWidth <= 767)

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 767)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return isMobile
}

function AppContent() {
  const isMobile = useIsMobile()
  const [isRefineInputExpanded, setIsRefineInputExpanded] = useState(false)
  const { showToast } = useToast()
  const {
    confirmStepId,
    error,
    handleConfirm,
    handleDeterministicUpdate,
    handleNaturalLanguageRefine,
    handlePlanTask,
    handleRefresh,
    handleWorkspaceConfirm,
    handleWorkspaceCancel,
    handlePatchSlideText,
    isInitialLoading,
    isLoading,
    sseConnected,
    task,
    workspace,
  } = useTaskWorkflow({
    onSuccess: (message) => showToast(message),
  })

  const activeStepCode = useMemo(() => {
    if (workspace?.steps?.length) {
      const runningStep = workspace.steps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
      return runningStep?.stepId ?? null
    }
    if (!task) return null
    if (confirmStepId) return confirmStepId
    const running = task.planSteps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
    return running?.stepId ?? running?.code ?? null
  }, [confirmStepId, task, workspace])

  const previewArtifacts = useMemo(() => task?.artifacts ?? [], [task])

  const displaySteps = workspace?.steps && workspace.steps.length > 0
    ? workspace.steps.map((step, index) => ({
        stepId: step.stepId,
        code: `STEP_${index + 1}`,
        name: step.name,
        status: step.status,
        requiresConfirm: step.requiresConfirm,
        displayStatus: step.displayStatus,
        description: step.action,
        artifactType: undefined,
      }))
    : (task?.planSteps ?? [])

  const doneCount = displaySteps.filter((s) => s.status === 'DONE' || s.status === 'APPROVED' || s.status === 'WAIT_CONFIRM').length
  const progress = displaySteps.length ? Math.round((doneCount / displaySteps.length) * 100) : 0

  if (isInitialLoading) {
    return (
      <div className="app-shell">
        <header className="top-bar">
          <div className="top-bar-inner">
            <div className="skeleton-row">
              <div className="skeleton-block skeleton-badge" />
              <div className="skeleton-block skeleton-title" />
            </div>
            <div className="skeleton-row">
              <div className="skeleton-block skeleton-progress" />
            </div>
          </div>
        </header>
        <div className="main-layout">
          <aside className="sidebar">
            <div className="sidebar-inner">
              <div className="skeleton-section">
                <div className="skeleton-block skeleton-heading" />
                {Array.from({ length: 4 }, (_, i) => (
                  <div key={i} className="skeleton-block skeleton-step" />
                ))}
              </div>
              <div className="skeleton-section">
                <div className="skeleton-block skeleton-heading" />
                <div className="skeleton-block skeleton-output" />
              </div>
            </div>
          </aside>
          <main className="main-preview">
            <div className="skeleton-section">
              <div className="skeleton-block skeleton-heading" />
              <div className="skeleton-block skeleton-preview" />
            </div>
          </main>
        </div>
        <footer className="bottom-bar">
          <div className="bottom-bar-inner">
            <div className="skeleton-row">
              <div className="skeleton-block skeleton-action" />
              <div className="skeleton-block skeleton-btn" />
            </div>
          </div>
        </footer>
      </div>
    )
  }

  return (
    <div className="app-shell">
      {/* ===== 顶部紧凑信息栏 ===== */}
      <header className="top-bar">
        <div className="top-bar-inner">
          <TaskHeader
            task={task}
            workspace={workspace}
            isLoading={isLoading}
            onRefresh={handleRefresh}
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
                isMobile={isMobile}
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
        <main className={`main-preview ${isMobile && isRefineInputExpanded ? 'with-expanded-input' : ''}`}>
          <PreviewPanel
            artifacts={previewArtifacts}
            workspacePreview={workspace?.preview}
            isDelivered={task?.status === 'DELIVERED' || workspace?.status === 'DELIVERED'}
            onDeterministicUpdate={handleDeterministicUpdate}
            onPatchSlideText={handlePatchSlideText}
            disabled={isLoading}
            isMobile={isMobile}
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
                  : '等待飞书消息卡片触发任务。'}
            </span>
            {error && <span className="bottom-bar-error">{error}</span>}
          </div>
          <div className="bottom-bar-controls">
            <ActionPanel
              disabled={isLoading}
              onConfirm={handleConfirm}
              onPlan={handlePlanTask}
              onRefresh={handleRefresh}
              onWorkspaceConfirm={handleWorkspaceConfirm}
              onWorkspaceCancel={handleWorkspaceCancel}
              task={task}
              workspace={workspace}
            />
          </div>
        </div>

        {/* 调整面板（条件展开）
        <AdjustmentsPanel
          adjustments={workspace?.adjustments ?? { available: false, stepId: '' }}
          onDeterministicUpdate={handleDeterministicUpdate}
          onNaturalLanguageRefine={handleNaturalLanguageRefine}
          disabled={isLoading}
        /> */}

        {/* 自然语言精修输入框 */}
        {workspace?.taskId && (
          <NaturalLanguageRefineInput
            previewType={workspace.preview?.type}
            previewStepId={workspace.preview?.stepId}
            onRefine={handleNaturalLanguageRefine}
            disabled={isLoading}
            isMobile={isMobile}
            onExpandChange={setIsRefineInputExpanded}
          />
        )}
      </footer>
      <ToastContainer />
    </div>
  )
}

function App() {
  return (
    <ToastProvider>
      <AppContent />
    </ToastProvider>
  )
}

export default App
