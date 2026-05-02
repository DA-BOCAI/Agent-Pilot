// 以下类型分两层：TaskView 是前端稳定展示模型，Backend* 是 OpenAPI 返回 DTO。
export type TaskStatus = 'CREATED' | 'PLANNED' | 'WAIT_CONFIRM' | 'RUNNING' | 'DELIVERED' | 'FAILED'

export type StepStatus = 'PENDING' | 'RUNNING' | 'WAIT_CONFIRM' | 'APPROVED' | 'SKIPPED' | 'DONE' | 'FAILED'

export type ArtifactType = 'doc' | 'slides' | 'delivery' | 'unknown'

export type EventType =
  | 'TASK_CREATED'
  | 'TASK_PLANNED'
  | 'STEP_WAIT_CONFIRM'
  | 'STEP_RUNNING'
  | 'STEP_DONE'
  | 'STEP_APPROVED'
  | 'STEP_REJECTED'
  | 'TASK_DELIVERED'
  | string

export type PlanStep = {
  // code 用于 UI 标识和展示，stepId 用于和后端确认接口交互。
  code: string
  name: string
  status: StepStatus
  requiresConfirm: boolean
  stepId: string
  action?: string
  scene?: string
  tool?: string
}

export type Artifact = {
  // type 是归一化后的 UI 类型，rawType 保留后端原始类型便于排查兼容问题。
  type: ArtifactType
  title: string
  url?: string
  data?: unknown
  json?: unknown
  content?: unknown
  rawType?: string
}

export type TaskEvent = {
  type: EventType
  message: string
  createdAt: string
  stepCode?: string
  metadata?: Record<string, string>
}

export type TaskView = {
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

export type BackendPlanStep = {
  // 后端当前主要返回 stepId/scene/action/tool，保留 code/name 是为了兼容旧 mock 或未来调整。
  stepId?: string
  code?: string
  name?: string
  scene?: string
  action?: string
  tool?: string
  requiresConfirm?: boolean
  status?: string
}

export type BackendArtifact = {
  type?: string
  title?: string
  url?: string
  data?: unknown
  json?: unknown
  content?: unknown
}

export type BackendTaskEvent = {
  // OpenAPI 使用 timestamp；旧前端模型使用 createdAt，mapper 会统一到 createdAt。
  type?: string
  message?: string
  createdAt?: string
  timestamp?: string
  stepCode?: string
  metadata?: Record<string, string>
}

export type BackendTaskView = {
  taskId?: string
  requestId?: string
  source?: string
  userId?: string
  inputText?: string
  status?: string
  nextAction?: string
  createdAt?: string
  updatedAt?: string
  planSteps?: BackendPlanStep[]
  artifacts?: BackendArtifact[]
  events?: BackendTaskEvent[]
}

export type CreateTaskRequest = {
  inputText: string
  requestId: string
  source: string
  userId: string
}

export type ConfirmTaskRequest = {
  approved: boolean
  stepId: string
  comment?: string
}

export type Step = {
  id: string
  title: string
  description?: string
  status: StepStatus
  displayStatus?: string
  requiresConfirm: boolean
  artifactType?: string
  order: number
}

export type Confirmation = {
  available: boolean
  stage?: 'confirm1' | 'confirm2'
  stepId?: string
  title?: string
  description?: string
  artifactType?: string
  theme?: string
  previewReady?: boolean
  preview?: Preview
}

export type Preview = {
  available: boolean
  type?: 'doc' | 'slides'
  title?: string
  theme?: string
  stepId?: string
  data?: unknown
}

export type AdjustmentControl = {
  key: string
  label: string
  type: 'select' | 'text' | 'structured' | 'instruction'
  options?: Array<{ label: string; value: string }>
}

export type DeterministicUpdateAction = {
  method: 'PUT'
  endpoint: string
}

export type NaturalLanguageRefineAction = {
  method: 'POST'
  endpoint: string
}

export type AdjustmentActions = {
  deterministicUpdate?: DeterministicUpdateAction
  naturalLanguageRefine?: NaturalLanguageRefineAction
}

export type Adjustments = {
  available: boolean
  stepId?: string
  type?: 'doc' | 'slides'
  controls?: AdjustmentControl[]
  actions?: AdjustmentActions
}

export type Output = {
  type: string
  title: string
  url: string
  token?: string
}

export type TimelineEvent = {
  timestamp: string
  type: string
  title: string
  message: string
  level: 'info' | 'success' | 'warning' | 'error'
  stepId?: string | null
  metadata?: Record<string, unknown>
}

export type Workspace = {
  taskId: string
  title?: string
  status: TaskStatus
  displayStatus?: string
  nextAction?: string
  source?: string
  userId?: string
  createdAt?: string
  updatedAt?: string
  inputSummary?: string
  contextText?: string
  steps: Step[]
  confirmation: Confirmation
  preview: Preview
  adjustments: Adjustments
  outputs: Output[]
  timeline: TimelineEvent[]
  debugTask?: unknown
}

export type BackendStep = {
  id?: string
  stepId?: string
  title?: string
  description?: string
  status?: string
  displayStatus?: string
  requiresConfirm?: boolean
  artifactType?: string
  order?: number
}

export type BackendConfirmation = {
  available?: boolean
  stage?: string
  stepId?: string
  title?: string
  description?: string
  artifactType?: string
  theme?: string
  previewReady?: boolean
  preview?: BackendPreview
}

export type BackendPreview = {
  available?: boolean
  type?: string
  title?: string
  theme?: string
  stepId?: string
  data?: unknown
}

export type BackendAdjustmentControl = {
  key?: string
  label?: string
  type?: string
  options?: Array<{ label?: string; value?: string }>
}

export type BackendAdjustmentActions = {
  deterministicUpdate?: {
    method?: string
    endpoint?: string
  }
  naturalLanguageRefine?: {
    method?: string
    endpoint?: string
  }
}

export type BackendAdjustments = {
  available?: boolean
  stepId?: string
  type?: string
  controls?: BackendAdjustmentControl[]
  actions?: BackendAdjustmentActions
}

export type BackendOutput = {
  type?: string
  title?: string
  url?: string
  token?: string
}

export type BackendTimelineEvent = {
  timestamp?: string
  type?: string
  title?: string
  message?: string
  level?: string
  stepId?: string | null
  metadata?: Record<string, unknown>
}

export type BackendWorkspace = {
  taskId?: string
  title?: string
  status?: string
  displayStatus?: string
  nextAction?: string
  source?: string
  userId?: string
  createdAt?: string
  updatedAt?: string
  inputSummary?: string
  contextText?: string
  steps?: BackendStep[]
  confirmation?: BackendConfirmation
  preview?: BackendPreview
  adjustments?: BackendAdjustments
  outputs?: BackendOutput[]
  timeline?: BackendTimelineEvent[]
  debugTask?: unknown
}

export type UpdatePreviewRequest = {
  previewData: unknown
}

export type RefinePreviewRequest = {
  instruction: string
}
