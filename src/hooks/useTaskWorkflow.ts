import { useCallback, useEffect, useRef, useState } from 'react'
import {
  connectWorkspaceStream,
  createTask,
  executeTask,
  getTask,
  getWorkspace,
  planTask,
  confirmTask as requestConfirmTask,
  refinePreview,
  updatePreview,
} from '../api/tasks'
import { previewDocument, previewPresentation } from '../api/previews'
import { createEvent, hasArtifactPayload, updateTask } from '../domain/taskModel'
import { getConfirmStepId } from '../domain/taskLabels'
import { createMockTask, mockConfirmTask, mockExecuteTask, mockPlanTask } from '../mocks/taskMock'
import { getTaskIdFromUrl } from '../utils/urlParams'
import type { Artifact, TaskView, Workspace } from '../types/task'
import type { SSEConnection } from '../api/http'

const DEMO_FEISHU_MESSAGE = '下周三给管理层同步 Agent-Pilot 协作闭环，重点讲从 IM 到演示稿。'
const SSE_RECONNECT_DELAY = 3000
const SSE_MAX_RECONNECT_ATTEMPTS = 5

export function useTaskWorkflow() {
  const [task, setTask] = useState<TaskView | null>(null)
  const [workspace, setWorkspace] = useState<Workspace | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [isMockMode, setIsMockMode] = useState(false)
  const [sseConnected, setSseConnected] = useState(false)

  const sseConnectionRef = useRef<SSEConnection | null>(null)
  const reconnectAttemptsRef = useRef(0)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isUnmountedRef = useRef(false)

  const confirmStepId = task ? getConfirmStepId(task.nextAction) : null

  const closeSSEConnection = useCallback(() => {
    if (sseConnectionRef.current) {
      sseConnectionRef.current.close()
      sseConnectionRef.current = null
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
      reconnectTimeoutRef.current = null
    }
    setSseConnected(false)
  }, [])

  const fallbackToGetWorkspace = useCallback(async (taskId: string) => {
    try {
      const ws = await getWorkspace(taskId)
      setWorkspace(ws)
      setSseConnected(false)
    } catch (fallbackError) {
      console.error('Fallback getWorkspace failed:', fallbackError)
    }
  }, [])

  const connectSSE = useCallback((taskId: string) => {
    if (isUnmountedRef.current) return

    closeSSEConnection()

    const connection = connectWorkspaceStream(taskId, {
      onSnapshot: (ws: Workspace) => {
        if (isUnmountedRef.current) return
        setWorkspace(ws)
        setSseConnected(true)
        reconnectAttemptsRef.current = 0
      },
      onWorkspace: (ws: Workspace) => {
        if (isUnmountedRef.current) return
        setWorkspace(ws)
        setSseConnected(true)
        reconnectAttemptsRef.current = 0
      },
      onError: async () => {
        if (isUnmountedRef.current) return
        console.error('SSE connection error')
        setSseConnected(false)

        if (reconnectAttemptsRef.current < SSE_MAX_RECONNECT_ATTEMPTS) {
          reconnectAttemptsRef.current += 1
          console.log(`SSE reconnecting... attempt ${reconnectAttemptsRef.current}/${SSE_MAX_RECONNECT_ATTEMPTS}`)

          reconnectTimeoutRef.current = setTimeout(() => {
            if (!isUnmountedRef.current && taskId) {
              connectSSE(taskId)
            }
          }, SSE_RECONNECT_DELAY)
        } else {
          console.log('SSE max reconnect attempts reached, falling back to GET /workspace')
          await fallbackToGetWorkspace(taskId)
        }
      },
    })

    sseConnectionRef.current = connection
  }, [closeSSEConnection, fallbackToGetWorkspace])

  useEffect(() => {
    const taskId = getTaskIdFromUrl()
    if (!taskId) return

    void runAction(async () => {
      try {
        const existingTask = await getTask(taskId)
        applyTask(existingTask, false)
        connectSSE(taskId)
      } catch {
        // 接口失败时不恢复任何数据，不显示内容
      }
    })

    return () => {
      isUnmountedRef.current = true
      closeSSEConnection()
    }
  }, [connectSSE, closeSSEConnection])

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
  }

  function applyWorkspace(ws: Workspace) {
    setWorkspace(ws)
  }

  async function handleCreateTask() {
    await runAction(async () => {
      try {
        const newTask = await createTask(DEMO_FEISHU_MESSAGE)
        applyTask(newTask, false)
        connectSSE(newTask.taskId)
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
        const [refreshedTask, refreshedWorkspace] = await Promise.all([
          getTask(task.taskId),
          getWorkspace(task.taskId),
        ])
        applyTask(refreshedTask, false)
        applyWorkspace(refreshedWorkspace)
      } catch {
        // 接口失败时不恢复任何数据，不显示内容
      }
    })
  }

  async function handleDeterministicUpdate(previewData: unknown) {
    if (!workspace?.preview?.stepId) {
      setError('无法更新：当前没有活动的预览')
      return
    }

    await runAction(async () => {
      try {
        const updatedWorkspace = await updatePreview(
          workspace.taskId,
          workspace.preview!.stepId!,
          previewData
        )
        applyWorkspace(updatedWorkspace)
      } catch (updateError) {
        setError(updateError instanceof Error ? updateError.message : '确定性更新失败')
      }
    })
  }

  async function handleNaturalLanguageRefine(instruction: string) {
    if (!workspace?.preview?.stepId) {
      setError('无法精修：当前没有活动的预览')
      return
    }

    await runAction(async () => {
      try {
        const updatedWorkspace = await refinePreview(
          workspace.taskId,
          workspace.preview!.stepId!,
          instruction
        )
        applyWorkspace(updatedWorkspace)
      } catch (refineError) {
        setError(refineError instanceof Error ? refineError.message : '自然语言精修失败')
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

      const nextTask = updateTask(sourceTask, {
        status: 'DELIVERED',
        nextAction: 'none',
        artifacts: [
          previewArtifact,
          ...sourceTask.artifacts.filter((artifact) => artifact.type !== 'slides'),
        ],
        events: [...sourceTask.events, createEvent('TASK_DELIVERED', 'PPT 预览已生成')],
      })

      applyTask(nextTask, false)
    })
  }

  function handleReset() {
    closeSSEConnection()
    setTask(null)
    setWorkspace(null)
    setError('')
    setIsMockMode(false)
    reconnectAttemptsRef.current = 0
  }

  return {
    confirmStepId,
    error,
    handleConfirm,
    handleCreateTask,
    handleDeterministicUpdate,
    handleExecuteTask,
    handleNaturalLanguageRefine,
    handlePlanTask,
    handlePreviewPresentation,
    handleRefresh,
    handleReset,
    isLoading,
    isMockMode,
    sseConnected,
    task,
    workspace,
  }
}

async function enrichTaskPreviews(task: TaskView): Promise<TaskView> {
  if (task.status !== 'DELIVERED') return task

  const previewArtifacts = task.artifacts.filter((artifact) => artifact.type !== 'unknown')
  if (previewArtifacts.length === 0) {
    const createdArtifacts = await createPreviewArtifacts(task)
    return createdArtifacts.length ? { ...task, artifacts: [...task.artifacts, ...createdArtifacts] } : task
  }

  const enrichedArtifacts = await Promise.all(task.artifacts.map((artifact) => enrichArtifact(task, artifact)))
  return { ...task, artifacts: enrichedArtifacts }
}

async function createPreviewArtifacts(task: TaskView): Promise<Artifact[]> {
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
