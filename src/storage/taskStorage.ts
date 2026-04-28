import type { TaskView } from '../types/task'

const TASK_ID_STORAGE_KEY = 'agent-pilot-task-id'
const MOCK_TASK_STORAGE_KEY = 'agent-pilot-mock-task'

// localStorage 只保存恢复演示链路需要的最小状态，不承担长期数据源职责。
export function getPersistedTaskId() {
  return localStorage.getItem(TASK_ID_STORAGE_KEY)
}

export function getPersistedMockTask() {
  const mockTask = localStorage.getItem(MOCK_TASK_STORAGE_KEY)
  if (!mockTask) return null

  try {
    // 本地缓存可能来自旧版本结构，解析失败时直接放弃恢复，避免页面启动崩溃。
    return JSON.parse(mockTask) as TaskView
  } catch {
    return null
  }
}

export function persistTask(task: TaskView) {
  localStorage.setItem(TASK_ID_STORAGE_KEY, task.taskId)
  localStorage.setItem(MOCK_TASK_STORAGE_KEY, JSON.stringify(task))
}

export function clearPersistedTask() {
  localStorage.removeItem(TASK_ID_STORAGE_KEY)
  localStorage.removeItem(MOCK_TASK_STORAGE_KEY)
}
