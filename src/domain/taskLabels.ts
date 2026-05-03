import type { ArtifactType, StepStatus, TaskStatus } from '../types/task'

// 后端步骤可以扩展，已知步骤使用中文业务名称，未知步骤直接显示原始 code。
const stepLabels: Record<string, string> = {
  A_CAPTURE: '需求捕捉',
  B_PLAN: '任务规划',
  C_DOC: '文档生成',
  D_SLIDES: '演示稿生成',
  F_DELIVER: '交付归档',
}

// 状态文案集中放在这里，避免 App.tsx 或组件里散落硬编码。
export const statusLabels: Record<TaskStatus, string> = {
  CREATED: '任务已创建',
  PLANNED: '规划已生成',
  WAIT_CONFIRM: '等待用户确认',
  RUNNING: '任务执行中',
  DELIVERED: '交付完成',
  FAILED: '任务已终止',
}

export const stepStatusLabels: Record<StepStatus, string> = {
  PENDING: '未开始',
  RUNNING: '运行中',
  WAIT_CONFIRM: '等待确认',
  APPROVED: '已通过',
  SKIPPED: '已跳过',
  DONE: '已完成',
  FAILED: '已失败',
}

const artifactLabels: Record<ArtifactType, string> = {
  doc: '文档',
  slides: '演示稿',
  unknown: '预览',
}

export function getStepLabel(code: string) {
  return stepLabels[code] ?? code
}

export function getStepMarker(code: string) {
  // A_CAPTURE 这类步骤在进度条里只显示 A；未知 code 则取首字母。
  return code.includes('_') ? code.split('_')[0] : code.slice(0, 1).toUpperCase()
}

export function getArtifactLabel(type: ArtifactType) {
  return artifactLabels[type]
}

export function getNextActionText(nextAction: string) {
  // nextAction 是后端驱动 UI 动作的轻量协议：plan/execute/none/confirm:{stepId}。
  if (nextAction === 'plan') return '下一步：生成规划'
  if (nextAction === 'execute') return '下一步：执行任务'
  if (nextAction === 'none') return '无后续动作'
  if (nextAction.startsWith('confirm:')) {
    const stepId = nextAction.replace('confirm:', '')
    return `下一步：确认${getStepLabel(stepId)}步骤`
  }
  return `下一步：${nextAction}`
}

export function getConfirmStepId(nextAction: string) {
  if (!nextAction.startsWith('confirm:')) return null
  return nextAction.replace('confirm:', '')
}
