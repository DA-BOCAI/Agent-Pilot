import { createEvent, createPlanSteps, updateTask } from '../domain/taskModel'
import { getConfirmStepId, getStepLabel } from '../domain/taskLabels'
import type { StepStatus, TaskView } from '../types/task'

// mock 用于后端不可用时保持演示闭环，状态流尽量模拟真实任务编排接口。
export function createMockTask(inputText: string): TaskView {
  const now = new Date().toISOString()
  return {
    taskId: `task_${Date.now()}`,
    requestId: `req_${crypto.randomUUID()}`,
    source: 'im_text',
    userId: 'user_zhouan',
    inputText,
    status: 'CREATED',
    nextAction: 'plan',
    createdAt: now,
    updatedAt: now,
    planSteps: [],
    artifacts: [],
    events: [createEvent('TASK_CREATED')],
  }
}

export function mockPlanTask(task: TaskView): TaskView {
  return updateTask(task, {
    status: 'PLANNED',
    nextAction: 'execute',
    planSteps: createPlanSteps(),
    events: [...task.events, createEvent('TASK_PLANNED')],
  })
}

export function mockExecuteTask(task: TaskView): TaskView {
  const currentSteps = task.planSteps.length > 0 ? task.planSteps : createPlanSteps()

  // 第一次执行停在文档确认闸门，体现“GUI 用于必要确认”的产品定位。
  if (task.status === 'PLANNED' && currentSteps.some((step) => step.code === 'C_DOC' && step.status === 'PENDING')) {
    return updateTask(task, {
      status: 'WAIT_CONFIRM',
      nextAction: 'confirm:C_DOC',
      planSteps: currentSteps.map((step) => (step.code === 'C_DOC' ? { ...step, status: 'WAIT_CONFIRM' } : step)),
      events: [
        ...task.events,
        createEvent('STEP_RUNNING', '文档生成步骤执行中', 'C_DOC'),
        createEvent('STEP_WAIT_CONFIRM', '文档生成步骤等待用户确认', 'C_DOC'),
      ],
    })
  }

  // 确认通过后再次执行，mock 直接生成文档和演示稿两个可预览产物。
  const deliveredSteps = currentSteps.map((step) =>
    step.status === 'PENDING' || step.status === 'RUNNING'
      ? { ...step, status: 'DONE' as StepStatus }
      : step.code === 'C_DOC' && step.status === 'APPROVED'
        ? { ...step, status: 'DONE' as StepStatus }
        : step,
  )

  return updateTask(task, {
    status: 'DELIVERED',
    nextAction: 'none',
    planSteps: deliveredSteps,
    artifacts: [
      {
        type: 'doc',
        title: 'Agent-Pilot 方案文档',
        url: 'https://example.com/docs/agent-pilot',
        data: {
          title: 'Agent-Pilot 方案文档',
          summary: '从 IM 对话触发任务，由 Agent 规划、执行、确认并生成文档和演示稿。',
          sections: [
            { title: '背景', content: '团队协作需求通常从 IM 讨论开始，需要跨文档和演示稿沉淀为交付物。' },
            { title: '目标', items: ['捕捉 IM 意图', '生成执行规划', '输出文档与演示稿', '回流交付结果'] },
            { title: '关键机制', content: ['TaskView 驱动任务详情', 'PlanStep 呈现执行链路', 'Artifact 承载预览数据'] },
          ],
        },
      },
      {
        type: 'slides',
        title: 'Agent-Pilot 管理层汇报演示稿',
        url: 'https://example.com/slides/agent-pilot',
        data: {
          title: 'Agent-Pilot 管理层汇报',
          slides: [
            { title: '协作痛点', subtitle: '从 IM 到汇报材料的链路过长', bullets: ['信息分散', '反复复制', '状态不可见'] },
            { title: 'Agent 工作流', bullets: ['创建任务', '生成规划', '执行步骤', '确认闸门', '交付归档'] },
            { title: '交付结果', bullets: ['方案文档', '演示稿'], notes: '强调端到端闭环。' },
          ],
        },
      },
    ],
    events: [
      ...task.events,
      createEvent('STEP_RUNNING', '演示稿生成与交付步骤执行中', 'D_SLIDES'),
      createEvent('STEP_DONE', '演示稿生成步骤已完成', 'D_SLIDES'),
      createEvent('STEP_DONE', '交付归档步骤已完成', 'F_DELIVER'),
      createEvent('TASK_DELIVERED'),
    ],
  })
}

export function mockConfirmTask(task: TaskView, approved: boolean): TaskView {
  const stepId = getConfirmStepId(task.nextAction)
  if (!stepId) return task

  // 拒绝确认会终止任务；通过确认则回到 execute，等待下一次执行继续推进。
  if (!approved) {
    return updateTask(task, {
      status: 'FAILED',
      nextAction: 'none',
      planSteps: task.planSteps.map((step) => (step.stepId === stepId ? { ...step, status: 'SKIPPED' } : step)),
      events: [...task.events, createEvent('STEP_REJECTED', `${getStepLabel(stepId)}步骤已拒绝`, stepId)],
    })
  }

  return updateTask(task, {
    status: 'PLANNED',
    nextAction: 'execute',
    planSteps: task.planSteps.map((step) => (step.stepId === stepId ? { ...step, status: 'APPROVED' } : step)),
    events: [...task.events, createEvent('STEP_APPROVED', `${getStepLabel(stepId)}步骤已通过`, stepId)],
  })
}
