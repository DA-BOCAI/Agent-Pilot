import { useMemo, useState } from 'react'
import { ActionPanel } from './components/ActionPanel'
import { ArtifactPreview } from './components/ArtifactPreview'
import { EmptyState } from './components/EmptyState'
import { getArtifactLabel, getNextActionText, getStepMarker, statusLabels, stepStatusLabels } from './domain/taskLabels'
import { useTaskWorkflow } from './hooks/useTaskWorkflow'
import './App.css'

function App() {
  // 页面层只负责把 hook 给出的状态和动作组装成飞书任务详情页，不直接处理 API 或 mock。
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
  // 记录用户当前选中的预览 tab；taskKey 用来判断任务是否已经刷新。
  const [previewSelection, setPreviewSelection] = useState({ index: 0, taskKey: '' })

  const activeStepCode = useMemo(() => {
    if (!task) return null
    if (confirmStepId) return confirmStepId
    // 没有确认步骤时，高亮当前运行中或等待确认的步骤。
    const running = task.planSteps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
    return running?.stepId ?? running?.code ?? null
  }, [confirmStepId, task])

  // delivery/link 类 artifact 不在页面里展示，预览区只保留可结构化渲染的文档和演示稿。
  const previewArtifacts = useMemo(() => task?.artifacts.filter((artifact) => artifact.type !== 'delivery') ?? [], [task])
  const taskPreviewKey = `${task?.taskId ?? ''}:${task?.updatedAt ?? ''}`
  // 任务刷新后自动回到第一个预览，避免旧 tab index 指向新 artifact 列表的错误位置。
  const activePreviewIndex = previewSelection.taskKey === taskPreviewKey ? previewSelection.index : 0
  const activeArtifact = previewArtifacts[activePreviewIndex] ?? previewArtifacts[0]

  return (
    <main className="app-shell">
      <header className="topbar reveal">
        {/* 顶部区域承担“任务摘要 + 进度条”职责，让用户一眼知道当前任务走到哪。 */}
        <div className="task-kicker">{isMockMode ? '演示模式' : 'API 模式'}</div>
        <div className="task-header">
          <div>
            <h1>{task?.inputText || '等待 IM 来源任务'}</h1>
            <p>
              {task
                ? `${statusLabels[task.status]} · ${getNextActionText(task.nextAction)}`
                : '任务由飞书聊天内容触发，页面用于展示进度、确认步骤和预览结果。'}
            </p>
          </div>
        </div>

        <section className="progress-strip" aria-label="任务步骤进度">
          {task?.planSteps.length ? (
            task.planSteps.map((step) => (
              <div
                className={`progress-step step-status-${step.status.toLowerCase()} ${
                  step.status === 'DONE' || step.status === 'APPROVED' ? 'is-done' : ''
                } ${step.stepId === activeStepCode || step.code === activeStepCode ? 'is-active' : ''}`}
                key={step.stepId}
              >
                <span>{getStepMarker(step.code)}</span>
                <strong>{step.name}</strong>
                <small>{stepStatusLabels[step.status]}</small>
              </div>
            ))
          ) : (
            <div className="empty-progress">任务创建后将在这里展示规划步骤。当前等待生成规划。</div>
          )}
        </section>
      </header>

      <section className="workspace-grid">
        <section className="main-panel reveal" aria-labelledby="workspace-title">
          <div className="panel-heading">
            <div>
              <span>{task ? statusLabels[task.status] : '未创建'}</span>
              <h2 id="workspace-title">任务详情</h2>
            </div>
            {task ? (
              <strong className={`status-pill status-${task.status.toLowerCase()}`}>{statusLabels[task.status]}</strong>
            ) : null}
          </div>

          <section className="content-grid">
            {/* 飞书上下文：展示自然语言来源，不在页面里提供输入框。 */}
            <article className="surface context-card">
              <h3>飞书上下文</h3>
              <p>{task?.inputText || '任务尚未创建。创建后将展示从飞书聊天 API 获取的原始需求摘要。'}</p>
              <dl>
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

            {/* 当前动作：只暴露当前任务状态下应该执行的一个或两个动作。 */}
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

            {/* 步骤时间线：比顶部进度条更详细，展示每步是否需要人工确认。 */}
            <article className="surface steps-card">
              <h3>任务步骤时间线</h3>
              {task?.planSteps.length ? (
                <div className="step-list">
                  {task.planSteps.map((step) => (
                    <div
                      className={`step-row step-status-${step.status.toLowerCase()} ${
                        step.stepId === activeStepCode || step.code === activeStepCode ? 'is-active' : ''
                      }`}
                      key={step.stepId}
                    >
                      <span>{getStepMarker(step.code)}</span>
                      <strong>{step.name}</strong>
                      <em>{stepStatusLabels[step.status]}</em>
                      <small>{step.requiresConfirm ? '需要确认' : '自动执行'}</small>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="暂无规划步骤" detail="调用生成规划接口后会展示 A_CAPTURE 到 F_DELIVER 的步骤树。" />
              )}
            </article>

            {/* 预览区：展示 Agent 生成的文档/PPT JSON，保持展示优先。 */}
            <article className="surface artifact-card">
              <h3>{task?.status === 'DELIVERED' ? '交付预览' : '预览'}</h3>
              {previewArtifacts.length ? (
                <div className="preview-layout">
                  <div className="preview-tabs" aria-label="预览类型">
                    {previewArtifacts.map((artifact, index) => (
                      <button
                        className={`artifact-tab artifact-${artifact.type} ${
                          artifact === activeArtifact ? 'is-selected' : ''
                        }`}
                        key={`${artifact.type}-${artifact.title}-${index}`}
                        onClick={() => setPreviewSelection({ index, taskKey: taskPreviewKey })}
                        type="button"
                      >
                        <span>{getArtifactLabel(artifact.type)}</span>
                        <strong>{artifact.title}</strong>
                      </button>
                    ))}
                  </div>
                  {activeArtifact ? <ArtifactPreview artifact={activeArtifact} /> : null}
                </div>
              ) : (
                <EmptyState title="暂无预览" detail="执行完成后将在这里预览文档 JSON 或 PPT JSON。" />
              )}
            </article>
          </section>
        </section>
      </section>
    </main>
  )
}

export default App
