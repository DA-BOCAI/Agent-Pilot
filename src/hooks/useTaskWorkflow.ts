import { useCallback, useEffect, useRef, useState } from 'react'
import {
  connectWorkspaceStream,
  getWorkspace,
  planTask,
  confirmTask as requestConfirmTask,
  refinePreview,
  updatePreview,
  confirmWorkspace,
  cancelWorkspace,
} from '../api/tasks'
import { workspaceToTaskView } from '../mappers/taskMapper'
import { getConfirmStepId } from '../domain/taskLabels'
import { getTaskIdFromUrl } from '../utils/urlParams'
import type { TaskView, Workspace } from '../types/task'
import type { SSEConnection } from '../api/http'

const SSE_RECONNECT_DELAY = 3000
const SSE_MAX_RECONNECT_ATTEMPTS = 5

export function useTaskWorkflow() {
  const [task, setTask] = useState<TaskView | null>(null)
  const [workspace, setWorkspace] = useState<Workspace | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
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
      setTask(workspaceToTaskView(ws))
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
        setTask(workspaceToTaskView(ws))
        setSseConnected(true)
        reconnectAttemptsRef.current = 0
      },
      onWorkspace: (ws: Workspace) => {
        if (isUnmountedRef.current) return
        setWorkspace(ws)
        setTask(workspaceToTaskView(ws))
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
        const existingWorkspace = await getWorkspace(taskId)
        setWorkspace(existingWorkspace)
        setTask(workspaceToTaskView(existingWorkspace))
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

  function applyWorkspace(ws: Workspace) {
    setWorkspace(ws)
  }

  async function handlePlanTask() {
    const currentTaskId = workspace?.taskId ?? task?.taskId
    if (!currentTaskId) return
    await runAction(async () => {
      await planTask(currentTaskId)
      const refreshedWorkspace = await getWorkspace(currentTaskId)
      applyWorkspace(refreshedWorkspace)
      setTask(workspaceToTaskView(refreshedWorkspace))
    })
  }

  async function handleConfirm(approved: boolean) {
    const currentTaskId = workspace?.taskId ?? task?.taskId
    if (!currentTaskId || !confirmStepId) return
    await runAction(async () => {
      await requestConfirmTask(currentTaskId, approved, confirmStepId)
      const refreshedWorkspace = await getWorkspace(currentTaskId)
      applyWorkspace(refreshedWorkspace)
      setTask(workspaceToTaskView(refreshedWorkspace))
    })
  }

  async function handleRefresh() {
    const currentTaskId = workspace?.taskId ?? task?.taskId
    if (!currentTaskId) return
    await runAction(async () => {
      try {
        const refreshedWorkspace = await getWorkspace(currentTaskId)
        applyWorkspace(refreshedWorkspace)
        setTask(workspaceToTaskView(refreshedWorkspace))
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

  function handleReset() {
    closeSSEConnection()
    setTask(null)
    setWorkspace(null)
    setError('')
    reconnectAttemptsRef.current = 0
  }

  async function handleWorkspaceConfirm() {
    const stepId = workspace?.confirmation?.stepId
    if (!workspace || !stepId) {
      setError('无法确认：当前没有等待确认的步骤')
      return
    }

    await runAction(async () => {
      try {
        const updatedWorkspace = await confirmWorkspace(workspace.taskId, stepId, true)
        applyWorkspace(updatedWorkspace)
      } catch (confirmError) {
        setError(confirmError instanceof Error ? confirmError.message : '工作台确认失败')
      }
    })
  }

  async function handleWorkspaceCancel() {
    const stepId = workspace?.confirmation?.stepId
    if (!workspace || !stepId) {
      setError('无法取消：当前没有等待确认的步骤')
      return
    }

    await runAction(async () => {
      try {
        const updatedWorkspace = await cancelWorkspace(workspace.taskId, stepId)
        applyWorkspace(updatedWorkspace)
      } catch (cancelError) {
        setError(cancelError instanceof Error ? cancelError.message : '工作台取消失败')
      }
    })
  }

  return {
    confirmStepId,
    error,
    handleConfirm,
    handleDeterministicUpdate,
    handleNaturalLanguageRefine,
    handlePlanTask,
    handleRefresh,
    handleReset,
    handleWorkspaceConfirm,
    handleWorkspaceCancel,
    isLoading,
    sseConnected,
    task,
    workspace,
  }
}
