import { useEffect, useState } from 'react'
import { createTask, executeTask, getTask, planTask, confirmTask as requestConfirmTask } from '../api/tasks'
import { previewDocument, previewPresentation } from '../api/previews'
import { createEvent, hasArtifactPayload, updateTask } from '../domain/taskModel'
import { getConfirmStepId } from '../domain/taskLabels'
import { createMockTask, mockConfirmTask, mockExecuteTask, mockPlanTask } from '../mocks/taskMock'
import { clearPersistedTask, getPersistedMockTask, getPersistedTaskId, persistTask } from '../storage/taskStorage'
import type { Artifact, TaskView } from '../types/task'

const DEMO_FEISHU_MESSAGE = '下周三给管理层同步 Agent-Pilot 协作闭环，重点讲从 IM 到演示稿。'

export function useTaskWorkflow() {
  const [task, setTask] = useState<TaskView | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [isMockMode, setIsMockMode] = useState(false)

  const confirmStepId = task ? getConfirmStepId(task.nextAction) : null

  useEffect(() => {
    const taskId = getPersistedTaskId()
    if (!taskId) return

    // 启动时优先恢复真实任务；接口不可用时回落到本地 mock，保证演示链路不断。
    void runAction(async () => {
      try {
        applyTask(await getTask(taskId), false)
      } catch {
        restoreMockTask()
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    setIsMockMode(mockMode)
    persistTask(nextTask)
  }

  function restoreMockTask() {
    const mockTask = getPersistedMockTask()
    if (!mockTask) return
    setTask(mockTask)
    setIsMockMode(true)
  }

  async function handleCreateTask() {
    await runAction(async () => {
      try {
        applyTask(await createTask(DEMO_FEISHU_MESSAGE), false)
      } catch {
        applyTask(createMockTask(DEMO_FEISHU_MESSAGE), true)
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
        const executedTask = await executeTask(task.taskId)
        // execute 是主流程，预览接口只是补充展示数据；预览失败不应拖垮任务状态更新。
        applyTask(await enrichTaskPreviews(executedTask), false)
      } catch {
        applyTask(mockExecuteTask(task), true)
      }
    })
  }

  async function handleConfirm(approved: boolean) {
    if (!task || !confirmStepId) return
    await runAction(async () => {
      try {
        applyTask(await requestConfirmTask(task.taskId, approved, confirmStepId), false)
      } catch {
        applyTask(mockConfirmTask(task, approved), true)
      }
    })
  }

  async function handleRefresh() {
    if (!task) return
    await runAction(async () => {
      try {
        applyTask(await getTask(task.taskId), false)
      } catch {
        restoreMockTask()
      }
    })
  }

  async function handlePreviewPresentation() {
    await runAction(async () => {
      const sourceTask = task ?? createMockTask(DEMO_FEISHU_MESSAGE)
      const preview = await previewPresentation({ userInput: sourceTask.inputText, topic: sourceTask.inputText })
      const previewArtifact: Artifact = {
        type: 'slides',
        title: preview.title ?? '演示稿预览',
        data: preview,
      }

      // 直接生成 PPT 时，把 slides 放在 artifact 首位，任务刷新后预览区会自动选中它。
      const nextTask = updateTask(sourceTask, {
        status: 'DELIVERED',
        nextAction: 'none',
        artifacts: [
          previewArtifact,
          ...sourceTask.artifacts.filter((artifact) => artifact.type !== 'slides' && artifact.type !== 'delivery'),
          ...sourceTask.artifacts.filter((artifact) => artifact.type === 'delivery'),
        ],
        events: [...sourceTask.events, createEvent('TASK_DELIVERED', 'PPT 预览已生成')],
      })

      applyTask(nextTask, false)
    })
  }

  function handleReset() {
    clearPersistedTask()
    setTask(null)
    setError('')
    setIsMockMode(false)
  }

  return {
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
  }
}

async function enrichTaskPreviews(task: TaskView): Promise<TaskView> {
  if (task.status !== 'DELIVERED') return task

  const previewArtifacts = task.artifacts.filter((artifact) => artifact.type !== 'delivery')
  if (previewArtifacts.length === 0) {
    // 有些后端实现只返回任务完成状态，不返回可渲染 artifact；这里主动生成文档/PPT预览。
    const createdArtifacts = await createPreviewArtifacts(task)
    return createdArtifacts.length ? { ...task, artifacts: [...task.artifacts, ...createdArtifacts] } : task
  }

  const enrichedArtifacts = await Promise.all(task.artifacts.map((artifact) => enrichArtifact(task, artifact)))
  return { ...task, artifacts: enrichedArtifacts }
}

async function createPreviewArtifacts(task: TaskView): Promise<Artifact[]> {
  // 两类预览互不阻塞：PPT 失败时文档仍可展示，反过来也一样。
  const [documentResult, presentationResult] = await Promise.allSettled([
    previewDocument({ userInput: task.inputText, docType: '方案文档' }),
    previewPresentation({ userInput: task.inputText, topic: task.inputText }),
  ])

  const artifacts: Artifact[] = []
  if (documentResult.status === 'fulfilled') {
    artifacts.push({ type: 'doc', title: documentResult.value.title ?? '文档预览', data: documentResult.value })
  }

  if (presentationResult.status === 'fulfilled') {
    artifacts.push({ type: 'slides', title: presentationResult.value.title ?? '演示稿预览', data: presentationResult.value })
  }

  return artifacts
}

async function enrichArtifact(task: TaskView, artifact: Artifact): Promise<Artifact> {
  if (hasArtifactPayload(artifact)) return artifact

  // 后端只给链接或标题时，按 artifact 类型补一次结构化 JSON，失败则保留原始 artifact。
  if (artifact.type === 'doc') {
    try {
      const preview = await previewDocument({ userInput: task.inputText, docType: artifact.title || '方案文档' })
      return { ...artifact, title: artifact.title || preview.title || '文档预览', data: preview }
    } catch {
      return artifact
    }
  }

  if (artifact.type === 'slides') {
    try {
      const preview = await previewPresentation({ userInput: task.inputText, topic: artifact.title || task.inputText })
      return { ...artifact, title: artifact.title || preview.title || '演示稿预览', data: preview }
    } catch {
      return artifact
    }
  }

  return artifact
}
