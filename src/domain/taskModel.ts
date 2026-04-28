import { getStepLabel } from './taskLabels'
import type { Artifact, EventType, PlanStep, StepStatus, TaskEvent, TaskView } from '../types/task'

export function createEvent(type: EventType, message?: string, stepCode?: string): TaskEvent {
  return {
    type,
    message: message ?? type,
    createdAt: new Date().toISOString(),
    stepCode,
  }
}

export function createPlanSteps(): PlanStep[] {
  return [
    createPlanStep('A_CAPTURE', 'DONE', false),
    createPlanStep('B_PLAN', 'DONE', false),
    createPlanStep('C_DOC', 'PENDING', true),
    createPlanStep('D_SLIDES', 'PENDING', false),
    createPlanStep('F_DELIVER', 'PENDING', false),
  ]
}

export function updateTask(task: TaskView, changes: Partial<TaskView>): TaskView {
  return {
    ...task,
    ...changes,
    updatedAt: new Date().toISOString(),
  }
}

// 预览 payload 兼容历史 mock、未来真实 API 和手工补齐的不同字段名。
export function getArtifactPayload(artifact: Artifact) {
  return artifact.data ?? artifact.json ?? artifact.content ?? null
}

export function hasArtifactPayload(artifact: Artifact) {
  const payload = getArtifactPayload(artifact)
  return Boolean(payload && typeof payload === 'object')
}

function createPlanStep(code: string, status: StepStatus, requiresConfirm: boolean): PlanStep {
  return {
    code,
    name: getStepLabel(code),
    status,
    requiresConfirm,
    stepId: code,
  }
}
