import { useEffect, useMemo, useState } from 'react'
import './App.css'

type TaskStatus = 'CREATED' | 'PLANNED' | 'WAIT_CONFIRM' | 'DELIVERED' | 'FAILED'
type StepCode = 'A_CAPTURE' | 'B_PLAN' | 'C_DOC' | 'D_SLIDES' | 'F_DELIVER'
type StepStatus = 'PENDING' | 'RUNNING' | 'WAIT_CONFIRM' | 'APPROVED' | 'SKIPPED' | 'DONE'
type ArtifactType = 'doc' | 'slides' | 'delivery'
type EventType =
  | 'TASK_CREATED'
  | 'TASK_PLANNED'
  | 'STEP_WAIT_CONFIRM'
  | 'STEP_RUNNING'
  | 'STEP_DONE'
  | 'STEP_APPROVED'
  | 'STEP_REJECTED'
  | 'TASK_DELIVERED'

type PlanStep = {
  code: StepCode
  name: string
  status: StepStatus
  requiresConfirm: boolean
}

type Artifact = {
  type: ArtifactType
  title: string
  url?: string
  data?: unknown
  json?: unknown
  content?: unknown
}

type TaskEvent = {
  type: EventType
  message: string
  createdAt: string
  stepCode?: StepCode
}

type TaskView = {
  taskId: string
  requestId: string
  source: string
  userId: string
  inputText: string
  status: TaskStatus
  nextAction: string
  createdAt: string
  updatedAt: string
  planSteps: PlanStep[]
  artifacts: Artifact[]
  events: TaskEvent[]
}

type DocumentSection = {
  title?: string
  content?: string | string[]
  text?: string
  items?: string[]
}

type DocumentPreviewData = {
  title?: string
  summary?: string
  sections?: DocumentSection[]
  blocks?: DocumentSection[]
}

type SlidePreviewData = {
  title?: string
  slides?: Array<{
    title?: string
    subtitle?: string
    bullets?: string[]
    notes?: string
  }>
}

const API_BASE = '/api/v1/tasks'
const TASK_ID_STORAGE_KEY = 'agent-pilot-task-id'
const MOCK_TASK_STORAGE_KEY = 'agent-pilot-mock-task'

const stepLabels: Record<StepCode, string> = {
  A_CAPTURE: '需求捕捉',
  B_PLAN: '任务规划',
  C_DOC: '文档生成',
  D_SLIDES: '演示稿生成',
  F_DELIVER: '交付归档',
}

const statusLabels: Record<TaskStatus, string> = {
  CREATED: '任务已创建',
  PLANNED: '规划已生成',
  WAIT_CONFIRM: '等待用户确认',
  DELIVERED: '交付完成',
  FAILED: '任务已终止',
}

const stepStatusLabels: Record<StepStatus, string> = {
  PENDING: '未开始',
  RUNNING: '运行中',
  WAIT_CONFIRM: '等待确认',
  APPROVED: '已通过',
  SKIPPED: '已跳过',
  DONE: '已完成',
}

const artifactLabels: Record<ArtifactType, string> = {
  doc: '文档',
  slides: '演示稿',
  delivery: '交付链接',
}

function createEvent(type: EventType, message?: string, stepCode?: StepCode): TaskEvent {
  return {
    type,
    message: message ?? type,
    createdAt: new Date().toISOString(),
    stepCode,
  }
}

function createPlanSteps(): PlanStep[] {
  return [
    { code: 'A_CAPTURE', name: stepLabels.A_CAPTURE, status: 'DONE', requiresConfirm: false },
    { code: 'B_PLAN', name: stepLabels.B_PLAN, status: 'DONE', requiresConfirm: false },
    { code: 'C_DOC', name: stepLabels.C_DOC, status: 'PENDING', requiresConfirm: true },
    { code: 'D_SLIDES', name: stepLabels.D_SLIDES, status: 'PENDING', requiresConfirm: false },
    { code: 'F_DELIVER', name: stepLabels.F_DELIVER, status: 'PENDING', requiresConfirm: false },
  ]
}

function getNextActionText(nextAction: string) {
  if (nextAction === 'plan') return '下一步：生成规划'
  if (nextAction === 'execute') return '下一步：执行任务'
  if (nextAction === 'none') return '无后续动作'
  if (nextAction.startsWith('confirm:')) {
    const code = nextAction.replace('confirm:', '') as StepCode
    return `下一步：确认${stepLabels[code] ?? code}步骤`
  }
  return `下一步：${nextAction}`
}

function getConfirmStepCode(nextAction: string): StepCode | null {
  if (!nextAction.startsWith('confirm:')) return null
  return nextAction.replace('confirm:', '') as StepCode
}

function createMockTask(inputText: string): TaskView {
  const now = new Date().toISOString()
  return {
    taskId: `task_${Date.now()}`,
    requestId: `req_${crypto.randomUUID()}`,
    source: 'im_text',
    userId: 'user_zhouan',
    inputText,
    status: 'CREATED',
    nextAction: 'plan',
    createdAt: now,
    updatedAt: now,
    planSteps: [],
    artifacts: [],
    events: [createEvent('TASK_CREATED')],
  }
}

function updateTask(task: TaskView, changes: Partial<TaskView>): TaskView {
  return {
    ...task,
    ...changes,
    updatedAt: new Date().toISOString(),
  }
}

function persistTask(task: TaskView) {
  localStorage.setItem(TASK_ID_STORAGE_KEY, task.taskId)
  localStorage.setItem(MOCK_TASK_STORAGE_KEY, JSON.stringify(task))
}

function clearPersistedTask() {
  localStorage.removeItem(TASK_ID_STORAGE_KEY)
  localStorage.removeItem(MOCK_TASK_STORAGE_KEY)
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new Error('Response is not JSON')
  }

  return response.json() as Promise<T>
}

async function createTask(inputText: string): Promise<TaskView> {
  return requestJson<TaskView>(API_BASE, {
    method: 'POST',
    body: JSON.stringify({
      requestId: `req_${crypto.randomUUID()}`,
      source: 'im_text',
      userId: 'user_zhouan',
      inputText,
    }),
  })
}

async function planTask(taskId: string): Promise<TaskView> {
  return requestJson<TaskView>(`${API_BASE}/${taskId}/plan`, { method: 'POST' })
}

async function executeTask(taskId: string): Promise<TaskView> {
  return requestJson<TaskView>(`${API_BASE}/${taskId}/execute`, { method: 'POST' })
}

async function confirmTask(taskId: string, approved: boolean, stepCode?: StepCode): Promise<TaskView> {
  return requestJson<TaskView>(`${API_BASE}/${taskId}/confirm`, {
    method: 'POST',
    body: JSON.stringify({ approved, stepCode }),
  })
}

async function getTask(taskId: string): Promise<TaskView> {
  return requestJson<TaskView>(`${API_BASE}/${taskId}`)
}

function mockPlanTask(task: TaskView): TaskView {
  return updateTask(task, {
    status: 'PLANNED',
    nextAction: 'execute',
    planSteps: createPlanSteps(),
    events: [...task.events, createEvent('TASK_PLANNED')],
  })
}

function mockExecuteTask(task: TaskView): TaskView {
  const currentSteps = task.planSteps.length > 0 ? task.planSteps : createPlanSteps()

  if (task.status === 'PLANNED' && currentSteps.some((step) => step.code === 'C_DOC' && step.status === 'PENDING')) {
    return updateTask(task, {
      status: 'WAIT_CONFIRM',
      nextAction: 'confirm:C_DOC',
      planSteps: currentSteps.map((step) => (step.code === 'C_DOC' ? { ...step, status: 'WAIT_CONFIRM' } : step)),
      events: [
        ...task.events,
        createEvent('STEP_RUNNING', '文档生成步骤执行中', 'C_DOC'),
        createEvent('STEP_WAIT_CONFIRM', '文档生成步骤等待用户确认', 'C_DOC'),
      ],
    })
  }

  const deliveredSteps = currentSteps.map((step) =>
    step.status === 'PENDING' || step.status === 'RUNNING'
      ? { ...step, status: 'DONE' as StepStatus }
      : step.code === 'C_DOC' && step.status === 'APPROVED'
        ? { ...step, status: 'DONE' as StepStatus }
        : step,
  )

  return updateTask(task, {
    status: 'DELIVERED',
    nextAction: 'none',
    planSteps: deliveredSteps,
    artifacts: [
      {
        type: 'doc',
        title: 'Agent-Pilot 方案文档',
        url: 'https://example.com/docs/agent-pilot',
        data: {
          title: 'Agent-Pilot 方案文档',
          summary: '从 IM 对话触发任务，由 Agent 规划、执行、确认并生成文档和演示稿。',
          sections: [
            { title: '背景', content: '团队协作需求通常从 IM 讨论开始，需要跨文档和演示稿沉淀为交付物。' },
            { title: '目标', items: ['捕捉 IM 意图', '生成执行规划', '输出文档与演示稿', '回流交付结果'] },
            { title: '关键机制', content: ['TaskView 驱动任务详情', 'PlanStep 呈现执行链路', 'Artifact 承载预览数据'] },
          ],
        },
      },
      {
        type: 'slides',
        title: 'Agent-Pilot 管理层汇报演示稿',
        url: 'https://example.com/slides/agent-pilot',
        data: {
          title: 'Agent-Pilot 管理层汇报',
          slides: [
            { title: '协作痛点', subtitle: '从 IM 到汇报材料的链路过长', bullets: ['信息分散', '反复复制', '状态不可见'] },
            { title: 'Agent 工作流', bullets: ['创建任务', '生成规划', '执行步骤', '确认闸门', '交付归档'] },
            { title: '交付结果', bullets: ['方案文档', '演示稿', 'IM 回流链接'], notes: '强调端到端闭环。' },
          ],
        },
      },
      { type: 'delivery', title: 'IM 群交付卡片', url: 'https://example.com/delivery/agent-pilot' },
    ],
    events: [
      ...task.events,
      createEvent('STEP_RUNNING', '演示稿生成与交付步骤执行中', 'D_SLIDES'),
      createEvent('STEP_DONE', '演示稿生成步骤已完成', 'D_SLIDES'),
      createEvent('STEP_DONE', '交付归档步骤已完成', 'F_DELIVER'),
      createEvent('TASK_DELIVERED'),
    ],
  })
}

function mockConfirmTask(task: TaskView, approved: boolean): TaskView {
  const stepCode = getConfirmStepCode(task.nextAction)
  if (!stepCode) return task

  if (!approved) {
    return updateTask(task, {
      status: 'FAILED',
      nextAction: 'none',
      planSteps: task.planSteps.map((step) => (step.code === stepCode ? { ...step, status: 'SKIPPED' } : step)),
      events: [...task.events, createEvent('STEP_REJECTED', `${stepLabels[stepCode]}步骤已拒绝`, stepCode)],
    })
  }

  return updateTask(task, {
    status: 'PLANNED',
    nextAction: 'execute',
    planSteps: task.planSteps.map((step) => (step.code === stepCode ? { ...step, status: 'APPROVED' } : step)),
    events: [...task.events, createEvent('STEP_APPROVED', `${stepLabels[stepCode]}步骤已通过`, stepCode)],
  })
}

function getArtifactPayload(artifact: Artifact) {
  return artifact.data ?? artifact.json ?? artifact.content ?? null
}

function App() {
  const [task, setTask] = useState<TaskView | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [isMockMode, setIsMockMode] = useState(false)
  const [activePreviewIndex, setActivePreviewIndex] = useState(0)

  const confirmStepCode = task ? getConfirmStepCode(task.nextAction) : null

  const activeStepCode = useMemo(() => {
    if (!task) return null
    if (confirmStepCode) return confirmStepCode
    const running = task.planSteps.find((step) => step.status === 'RUNNING' || step.status === 'WAIT_CONFIRM')
    return running?.code ?? null
  }, [confirmStepCode, task])

  const activeArtifact = task?.artifacts[activePreviewIndex] ?? task?.artifacts[0]

  useEffect(() => {
    const taskId = localStorage.getItem(TASK_ID_STORAGE_KEY)
    if (!taskId) return

    void runAction(async () => {
      try {
        const restoredTask = await getTask(taskId)
        setTask(restoredTask)
        setActivePreviewIndex(0)
        setIsMockMode(false)
      } catch {
        const mockTask = localStorage.getItem(MOCK_TASK_STORAGE_KEY)
        if (!mockTask) return
        const parsedTask = JSON.parse(mockTask) as TaskView
        setTask(parsedTask)
        setActivePreviewIndex(0)
        setIsMockMode(true)
      }
    })
  }, [])

  async function runAction(action: () => Promise<void>) {
    setIsLoading(true)
    setError('')
    try {
      await action()
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : '操作失败')
    } finally {
      setIsLoading(false)
    }
  }

  function applyTask(nextTask: TaskView, mockMode = isMockMode) {
    setTask(nextTask)
    setActivePreviewIndex(0)
    setIsMockMode(mockMode)
    persistTask(nextTask)
  }

  async function handleCreateTask() {
    await runAction(async () => {
      const feishuMessage = '下周三给管理层同步 Agent-Pilot 协作闭环，重点讲从 IM 到演示稿。'
      try {
        const createdTask = await createTask(feishuMessage)
        applyTask(createdTask, false)
      } catch {
        applyTask(createMockTask(feishuMessage), true)
      }
    })
  }

  async function handlePlanTask() {
    if (!task) return
    await runAction(async () => {
      try {
        applyTask(await planTask(task.taskId), false)
      } catch {
        applyTask(mockPlanTask(task), true)
      }
    })
  }

  async function handleExecuteTask() {
    if (!task) return
    await runAction(async () => {
      try {
        applyTask(await executeTask(task.taskId), false)
      } catch {
        applyTask(mockExecuteTask(task), true)
      }
    })
  }

  async function handleConfirm(approved: boolean) {
    if (!task) return
    await runAction(async () => {
      try {
        applyTask(await confirmTask(task.taskId, approved, confirmStepCode ?? undefined), false)
      } catch {
        applyTask(mockConfirmTask(task, approved), true)
      }
    })
  }

  async function handleRefresh() {
    if (!task) return
    await runAction(async () => {
      try {
        const refreshedTask = await getTask(task.taskId)
        setTask(refreshedTask)
        setActivePreviewIndex(0)
        persistTask(refreshedTask)
        setIsMockMode(false)
      } catch {
        const mockTask = localStorage.getItem(MOCK_TASK_STORAGE_KEY)
        if (!mockTask) return
        const parsedTask = JSON.parse(mockTask) as TaskView
        setTask(parsedTask)
        setActivePreviewIndex(0)
        setIsMockMode(true)
      }
    })
  }

  function handleReset() {
    clearPersistedTask()
    setTask(null)
    setActivePreviewIndex(0)
    setError('')
    setIsMockMode(false)
  }

  return (
    <main className="app-shell">
      <header className="topbar reveal">
        <div className="task-kicker">TaskController 驱动 · {isMockMode ? 'Mock 演示模式' : 'API 模式'}</div>
        <div className="task-header">
          <div>
            <h1>{task?.inputText || '创建一个 IM 来源任务'}</h1>
            <p>
              {task
                ? `${task.source} · ${task.userId} · ${statusLabels[task.status]} · ${getNextActionText(task.nextAction)}`
                : '任务由飞书聊天内容触发，页面用于展示进度、确认步骤和预览结果。'}
            </p>
          </div>
        </div>

        <section className="progress-strip" aria-label="任务步骤进度">
          {task?.planSteps.length ? (
            task.planSteps.map((step) => (
              <div
                className={`progress-step ${step.status === 'DONE' || step.status === 'APPROVED' ? 'is-done' : ''} ${
                  step.code === activeStepCode ? 'is-active' : ''
                }`}
                key={step.code}
              >
                <span>{step.code.split('_')[0]}</span>
                <strong>{step.name}</strong>
                <small>{stepStatusLabels[step.status]}</small>
              </div>
            ))
          ) : (
            <div className="empty-progress">任务创建后将在这里展示规划步骤。当前等待生成规划。</div>
          )}
        </section>

        <div className="top-actions" aria-label="辅助操作">
          <button onClick={handleRefresh} disabled={!task || isLoading}>
            刷新任务
          </button>
          <button onClick={handleReset} disabled={isLoading}>
            新建任务
          </button>
          <button disabled={!task}>返回 IM</button>
        </div>
      </header>

      <section className="workspace-grid">
        <section className="main-panel reveal" aria-labelledby="workspace-title">
          <div className="panel-heading">
            <div>
              <span>{task ? statusLabels[task.status] : '未创建'}</span>
              <h2 id="workspace-title">任务详情</h2>
            </div>
            {task ? <strong className={`status-pill status-${task.status.toLowerCase()}`}>{task.status}</strong> : null}
          </div>

          <section className="content-grid">
            <article className="surface context-card">
              <h3>飞书上下文</h3>
              <p>{task?.inputText || '任务尚未创建。创建后将展示从飞书聊天 API 获取的原始需求摘要。'}</p>
              <dl>
                <div>
                  <dt>来源</dt>
                  <dd>{task?.source ?? 'im_text'}</dd>
                </div>
                <div>
                  <dt>发起人</dt>
                  <dd>{task?.userId ?? '等待飞书回调'}</dd>
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

            <article className="surface action-card">
              <h3>当前动作</h3>
              <p>{task ? getNextActionText(task.nextAction) : '等待飞书消息卡片触发任务，或使用演示按钮创建模拟任务。'}</p>
              {error ? <div className="error-banner">{error}</div> : null}
              <ActionPanel
                disabled={isLoading}
                onConfirm={handleConfirm}
                onCreate={handleCreateTask}
                onExecute={handleExecuteTask}
                onPlan={handlePlanTask}
                onReset={handleReset}
                task={task}
              />
            </article>

            <article className="surface steps-card">
              <h3>任务步骤时间线</h3>
              {task?.planSteps.length ? (
                <div className="step-list">
                  {task.planSteps.map((step) => (
                    <div className={`step-row ${step.code === activeStepCode ? 'is-active' : ''}`} key={step.code}>
                      <span>{step.code}</span>
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

            <article className="surface artifact-card">
              <h3>{task?.status === 'DELIVERED' ? '交付预览' : '预览'}</h3>
              {task?.artifacts.length ? (
                <div className="preview-layout">
                  <div className="preview-tabs" aria-label="预览类型">
                    {task.artifacts.map((artifact, index) => (
                      <button
                        className={artifact === activeArtifact ? 'is-selected' : ''}
                        key={`${artifact.type}-${artifact.title}-${index}`}
                        onClick={() => setActivePreviewIndex(index)}
                        type="button"
                      >
                        <span>{artifactLabels[artifact.type]}</span>
                        <strong>{artifact.title}</strong>
                      </button>
                    ))}
                  </div>
                  {activeArtifact ? <ArtifactPreview artifact={activeArtifact} /> : null}
                </div>
              ) : (
                <EmptyState title="暂无预览" detail="执行完成后将在这里预览文档 JSON、PPT JSON 或交付链接。" />
              )}
            </article>
          </section>
        </section>
      </section>
    </main>
  )
}

function ArtifactPreview({ artifact }: { artifact: Artifact }) {
  const payload = getArtifactPayload(artifact)

  if (artifact.type === 'doc' && payload && typeof payload === 'object') {
    return <DocumentPreview data={payload as DocumentPreviewData} fallbackTitle={artifact.title} />
  }

  if (artifact.type === 'slides' && payload && typeof payload === 'object') {
    return <SlidesPreview data={payload as SlidePreviewData} fallbackTitle={artifact.title} />
  }

  return (
    <div className="preview-empty">
      <span>{artifactLabels[artifact.type]}</span>
      <h4>{artifact.title}</h4>
      {artifact.url ? (
        <a href={artifact.url} target="_blank">
          打开链接
        </a>
      ) : (
        <p>后端尚未返回可预览的 JSON 数据。</p>
      )}
    </div>
  )
}

function DocumentPreview({ data, fallbackTitle }: { data: DocumentPreviewData; fallbackTitle: string }) {
  const sections = data.sections ?? data.blocks ?? []

  return (
    <div className="doc-preview">
      <header>
        <span>Document Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
        {data.summary ? <p>{data.summary}</p> : null}
      </header>
      {sections.length ? (
        <div className="doc-section-list">
          {sections.map((section, index) => (
            <section key={`${section.title ?? 'section'}-${index}`}>
              <h5>{section.title ?? `段落 ${index + 1}`}</h5>
              <PreviewContent section={section} />
            </section>
          ))}
        </div>
      ) : (
        <pre>{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  )
}

function PreviewContent({ section }: { section: DocumentSection }) {
  const content = section.content ?? section.text

  if (Array.isArray(section.items)) {
    return (
      <ul>
        {section.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    )
  }

  if (Array.isArray(content)) {
    return (
      <ul>
        {content.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    )
  }

  return <p>{content ?? '暂无内容'}</p>
}

function SlidesPreview({ data, fallbackTitle }: { data: SlidePreviewData; fallbackTitle: string }) {
  const slides = data.slides ?? []

  return (
    <div className="slides-preview">
      <header>
        <span>Slides Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
      </header>
      {slides.length ? (
        <div className="slide-grid">
          {slides.map((slide, index) => (
            <article className="slide-card" key={`${slide.title ?? 'slide'}-${index}`}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <h5>{slide.title ?? `Slide ${index + 1}`}</h5>
              {slide.subtitle ? <p>{slide.subtitle}</p> : null}
              {slide.bullets?.length ? (
                <ul>
                  {slide.bullets.map((bullet) => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
              ) : null}
              {slide.notes ? <small>{slide.notes}</small> : null}
            </article>
          ))}
        </div>
      ) : (
        <pre>{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  )
}

function EmptyState({ detail, title }: { detail: string; title: string }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{detail}</p>
    </div>
  )
}

function ActionPanel({
  disabled,
  onConfirm,
  onCreate,
  onExecute,
  onPlan,
  onReset,
  task,
}: {
  disabled: boolean
  onConfirm: (approved: boolean) => void
  onCreate: () => void
  onExecute: () => void
  onPlan: () => void
  onReset: () => void
  task: TaskView | null
}) {
  if (!task) {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onCreate}>
          创建模拟任务
        </button>
      </div>
    )
  }

  if (task.status === 'CREATED' && task.nextAction === 'plan') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onPlan}>
          生成规划
        </button>
      </div>
    )
  }

  if (task.status === 'PLANNED' && task.nextAction === 'execute') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled} onClick={onExecute}>
          执行任务
        </button>
      </div>
    )
  }

  if (task.status === 'WAIT_CONFIRM' && task.nextAction.startsWith('confirm:')) {
    return (
      <div className="action-stack split">
        <button className="primary" disabled={disabled} onClick={() => onConfirm(true)}>
          通过
        </button>
        <button disabled={disabled} onClick={() => onConfirm(false)}>
          拒绝
        </button>
      </div>
    )
  }

  if (task.status === 'DELIVERED') {
    return (
      <div className="action-stack">
        <button className="primary" disabled={disabled}>
          查看预览
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
      </div>
    )
  }

  return null
}

export default App
