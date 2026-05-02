import { getStepLabel } from '../domain/taskLabels'
import type {
  Artifact,
  ArtifactType,
  BackendArtifact,
  BackendPlanStep,
  BackendTaskEvent,
  BackendTaskView,
  BackendWorkspace,
  BackendStep,
  BackendConfirmation,
  BackendPreview,
  BackendAdjustments,
  BackendOutput,
  BackendTimelineEvent,
  PlanStep,
  Step,
  StepStatus,
  Confirmation,
  Preview,
  Adjustments,
  Output,
  TimelineEvent,
  TaskEvent,
  TaskStatus,
  TaskView,
  Workspace,
} from '../types/task'

const taskStatuses: TaskStatus[] = ['CREATED', 'PLANNED', 'WAIT_CONFIRM', 'RUNNING', 'DELIVERED', 'FAILED']
const stepStatuses: StepStatus[] = ['PENDING', 'RUNNING', 'WAIT_CONFIRM', 'APPROVED', 'SKIPPED', 'DONE', 'FAILED']

// 后端 DTO 和页面展示模型在 stepId/timestamp/artifact type 等字段上不完全一致，统一在这里收口。
export function mapTaskView(dto: BackendTaskView): TaskView {
  const now = new Date().toISOString()

  return {
    taskId: dto.taskId ?? '',
    requestId: dto.requestId ?? '',
    source: dto.source ?? 'im_text',
    userId: dto.userId ?? '',
    inputText: dto.inputText ?? '',
    status: normalizeTaskStatus(dto.status),
    nextAction: dto.nextAction ?? 'none',
    createdAt: dto.createdAt ?? now,
    updatedAt: dto.updatedAt ?? dto.createdAt ?? now,
    planSteps: (dto.planSteps ?? []).map(mapPlanStep),
    artifacts: (dto.artifacts ?? []).map(mapArtifact),
    events: (dto.events ?? []).map(mapTaskEvent),
  }
}

export function mapTaskEvents(events: BackendTaskEvent[]): TaskEvent[] {
  return events.map(mapTaskEvent)
}

function mapPlanStep(step: BackendPlanStep, index: number): PlanStep {
  // OpenAPI 里步骤主键是 stepId；旧 UI 依赖 code，所以前端同时保留两者。
  const code = step.stepId ?? step.code ?? `STEP_${index + 1}`
  const name = step.name ?? step.action ?? step.scene ?? getStepLabel(code)

  return {
    code,
    name,
    status: normalizeStepStatus(step.status),
    requiresConfirm: step.requiresConfirm ?? false,
    stepId: step.stepId ?? code,
    action: step.action,
    scene: step.scene,
    tool: step.tool,
  }
}

function mapArtifact(artifact: BackendArtifact): Artifact {
  const rawType = artifact.type ?? 'unknown'

  return {
    type: normalizeArtifactType(rawType),
    title: artifact.title ?? getArtifactFallbackTitle(rawType),
    url: artifact.url,
    data: artifact.data,
    json: artifact.json,
    content: artifact.content,
    rawType,
  }
}

function mapTaskEvent(event: BackendTaskEvent): TaskEvent {
  return {
    type: event.type ?? 'TASK_EVENT',
    message: event.message ?? event.type ?? '任务事件',
    createdAt: event.createdAt ?? event.timestamp ?? new Date().toISOString(),
    stepCode: event.stepCode ?? event.metadata?.stepId,
    metadata: event.metadata,
  }
}

function normalizeTaskStatus(status?: string): TaskStatus {
  return taskStatuses.includes(status as TaskStatus) ? (status as TaskStatus) : 'CREATED'
}

function normalizeStepStatus(status?: string): StepStatus {
  return stepStatuses.includes(status as StepStatus) ? (status as StepStatus) : 'PENDING'
}

function normalizeArtifactType(type: string): ArtifactType {
  // 真实接口和 mock 可能使用 document/presentation/ppt 等别名，先归一成 UI 可识别类型。
  const normalized = type.toLowerCase()
  if (normalized === 'doc' || normalized === 'document') return 'doc'
  if (normalized === 'slides' || normalized === 'slide' || normalized === 'ppt' || normalized === 'presentation') {
    return 'slides'
  }
  if (normalized === 'delivery' || normalized === 'link') return 'delivery'
  return 'unknown'
}

function getArtifactFallbackTitle(type: string) {
  const normalized = normalizeArtifactType(type)
  if (normalized === 'doc') return '文档预览'
  if (normalized === 'slides') return '演示稿预览'
  return '预览'
}

export function mapWorkspace(dto: BackendWorkspace): Workspace {
  const now = new Date().toISOString()

  return {
    taskId: dto.taskId ?? '',
    title: dto.title,
    status: normalizeTaskStatus(dto.status),
    displayStatus: dto.displayStatus,
    nextAction: dto.nextAction,
    source: dto.source,
    userId: dto.userId,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    inputSummary: dto.inputSummary,
    contextText: dto.contextText,
    steps: (dto.steps ?? []).map(mapStep),
    confirmation: mapConfirmation(dto.confirmation),
    preview: mapPreview(dto.preview),
    adjustments: mapAdjustments(dto.adjustments),
    outputs: (dto.outputs ?? []).map(mapOutput),
    timeline: (dto.timeline ?? []).map(mapTimeline),
    debugTask: dto.debugTask,
  }
}

function mapStep(step: BackendStep, index: number): Step {
  return {
    id: step.id ?? step.stepId ?? `STEP_${index + 1}`,
    title: step.title ?? `步骤 ${index + 1}`,
    description: step.description,
    status: normalizeStepStatus(step.status),
    displayStatus: step.displayStatus,
    requiresConfirm: step.requiresConfirm ?? false,
    artifactType: step.artifactType,
    order: step.order ?? index + 1,
  }
}

function mapConfirmation(dto?: BackendConfirmation): Confirmation {
  if (!dto) {
    return { available: false }
  }

  return {
    available: dto.available ?? false,
    stage: dto.stage as 'confirm1' | 'confirm2' | undefined,
    stepId: dto.stepId,
    title: dto.title,
    description: dto.description,
    artifactType: dto.artifactType,
    theme: dto.theme,
    previewReady: dto.previewReady,
    preview: dto.preview ? mapPreview(dto.preview) : undefined,
  }
}

function mapPreview(dto?: BackendPreview): Preview {
  if (!dto) {
    return { available: false }
  }

  return {
    available: dto.available ?? false,
    type: dto.type as 'doc' | 'slides' | undefined,
    title: dto.title,
    theme: dto.theme,
    stepId: dto.stepId,
    data: dto.data,
  }
}

function mapAdjustments(dto?: BackendAdjustments): Adjustments {
  if (!dto) {
    return { available: false }
  }

  return {
    available: dto.available ?? false,
    stepId: dto.stepId,
    type: dto.type as 'doc' | 'slides' | undefined,
    controls: dto.controls?.map((control) => ({
      key: control.key ?? '',
      label: control.label ?? '',
      type: control.type as 'select' | 'text' | 'structured' | 'instruction',
      options: control.options?.map((opt) => ({
        label: opt.label ?? '',
        value: opt.value ?? '',
      })),
    })),
    actions: dto.actions
      ? {
          deterministicUpdate: dto.actions.deterministicUpdate
            ? {
                method: dto.actions.deterministicUpdate.method as 'PUT',
                endpoint: dto.actions.deterministicUpdate.endpoint ?? '',
              }
            : undefined,
          naturalLanguageRefine: dto.actions.naturalLanguageRefine
            ? {
                method: dto.actions.naturalLanguageRefine.method as 'POST',
                endpoint: dto.actions.naturalLanguageRefine.endpoint ?? '',
              }
            : undefined,
        }
      : undefined,
  }
}

function mapOutput(dto: BackendOutput): Output {
  return {
    type: dto.type ?? 'unknown',
    title: dto.title ?? '产物',
    url: dto.url ?? '',
    token: dto.token,
  }
}

function mapTimeline(dto: BackendTimelineEvent): TimelineEvent {
  return {
    timestamp: dto.timestamp ?? new Date().toISOString(),
    type: dto.type ?? 'EVENT',
    title: dto.title ?? dto.type ?? '事件',
    message: dto.message ?? '',
    level: (dto.level as 'info' | 'success' | 'warning' | 'error') ?? 'info',
    stepId: dto.stepId,
    metadata: dto.metadata,
  }
}
