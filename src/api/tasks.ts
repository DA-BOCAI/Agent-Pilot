import { createSSEConnection, requestJson } from './http'
import { mapTaskEvents, mapTaskView, mapWorkspace } from '../mappers/taskMapper'
import type {
  BackendTaskEvent,
  BackendTaskView,
  BackendWorkspace,
  CreateTaskRequest,
  RefinePreviewRequest,
  TaskEvent,
  TaskView,
  UpdatePreviewRequest,
  Workspace,
} from '../types/task'
import type { SSEConnection } from './http'

const DEFAULT_SOURCE = 'im_text'
const DEFAULT_USER_ID = 'user_zhouan'

// 创建任务时前端模拟“飞书消息已被后端拿到”的来源信息，真实接入后可替换为登录态/回调数据。
export async function createTask(inputText: string): Promise<TaskView> {
  const body: CreateTaskRequest = {
    requestId: `req_${crypto.randomUUID()}`,
    source: DEFAULT_SOURCE,
    userId: DEFAULT_USER_ID,
    inputText,
  }

  const task = await requestJson<BackendTaskView>('/tasks', {
    method: 'POST',
    body: JSON.stringify(body),
  })

  return mapTaskView(task)
}

// 生成规划：后端返回 TaskView，前端立即映射为 UI 稳定读取的展示模型。
export async function planTask(taskId: string): Promise<TaskView> {
  const task = await requestJson<BackendTaskView>(`/tasks/${taskId}/plan`, { method: 'POST' })
  return mapTaskView(task)
}

// 执行任务：后端会推进到完成或下一个确认闸门，前端不在这里猜状态。
export async function executeTask(taskId: string): Promise<TaskView> {
  const task = await requestJson<BackendTaskView>(`/tasks/${taskId}/execute`, { method: 'POST' })
  return mapTaskView(task)
}

// OpenAPI 要求字段名是 stepId，不再传旧版 stepCode。
export async function confirmTask(taskId: string, approved: boolean, stepId: string): Promise<TaskView> {
  const task = await requestJson<BackendTaskView>(`/tasks/${taskId}/confirm`, {
    method: 'POST',
    body: JSON.stringify({ approved, stepId }),
  })

  return mapTaskView(task)
}

// 刷新任务详情，用于页面恢复和手动刷新。
export async function getTask(taskId: string): Promise<TaskView> {
  const task = await requestJson<BackendTaskView>(`/tasks/${taskId}`)
  return mapTaskView(task)
}

// 事件接口先独立封装，后续如果页面要恢复时间线/调试视图不用再改底层 API。
export async function getEvents(taskId: string): Promise<TaskEvent[]> {
  const events = await requestJson<BackendTaskEvent[]>(`/tasks/${taskId}/events`)
  return mapTaskEvents(events)
}

export async function getWorkspace(taskId: string): Promise<Workspace> {
  const workspace = await requestJson<BackendWorkspace>(`/tasks/${taskId}/workspace`)
  return mapWorkspace(workspace)
}

export function connectWorkspaceStream(
  taskId: string,
  handlers: {
    onSnapshot?: (workspace: Workspace) => void
    onWorkspace?: (workspace: Workspace) => void
    onError?: (error: Event) => void
  }
): SSEConnection {
  return createSSEConnection<Workspace>(`/tasks/${taskId}/workspace/stream`, {
    onSnapshot: handlers.onSnapshot,
    onWorkspace: handlers.onWorkspace,
    onError: handlers.onError,
  })
}

export async function updatePreview(
  taskId: string,
  stepId: string,
  previewData: unknown
): Promise<Workspace> {
  const body: UpdatePreviewRequest = { previewData }
  const workspace = await requestJson<BackendWorkspace>(
    `/tasks/${taskId}/steps/${stepId}/preview`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    }
  )
  return mapWorkspace(workspace)
}

export async function refinePreview(
  taskId: string,
  stepId: string,
  instruction: string
): Promise<Workspace> {
  const body: RefinePreviewRequest = { instruction }
  const workspace = await requestJson<BackendWorkspace>(
    `/tasks/${taskId}/steps/${stepId}/preview/refine`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    }
  )
  return mapWorkspace(workspace)
}

export async function confirmWorkspace(
  taskId: string,
  stepId: string,
  approved: boolean = true,
  comment?: string
): Promise<Workspace> {
  const workspace = await requestJson<BackendWorkspace>(
    `/tasks/${taskId}/workspace/confirm`,
    {
      method: 'POST',
      body: JSON.stringify({ stepId, approved, comment: comment ?? '用户在工作台确认' }),
    }
  )
  return mapWorkspace(workspace)
}

export async function cancelWorkspace(
  taskId: string,
  stepId: string,
  comment?: string
): Promise<Workspace> {
  const workspace = await requestJson<BackendWorkspace>(
    `/tasks/${taskId}/workspace/cancel`,
    {
      method: 'POST',
      body: JSON.stringify({ stepId, comment: comment ?? '用户在工作台取消' }),
    }
  )
  return mapWorkspace(workspace)
}
