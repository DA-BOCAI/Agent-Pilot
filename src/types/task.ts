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
