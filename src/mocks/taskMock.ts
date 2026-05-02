import { createEvent, createPlanSteps, updateTask } from '../domain/taskModel'
import { getConfirmStepId, getStepLabel } from '../domain/taskLabels'
import type {
  StepStatus,
  TaskView,
  Workspace,
  Step,
  Confirmation,
  Preview,
  Adjustments,
  Output,
  TimelineEvent,
} from '../types/task'

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

function createMockSteps(): Step[] {
  return [
    {
      id: 'A_UNDERSTAND',
      title: '理解需求',
      description: '分析用户输入，提取关键意图',
      status: 'DONE',
      displayStatus: '已完成',
      requiresConfirm: false,
      order: 1,
    },
    {
      id: 'B_PLAN',
      title: '生成规划',
      description: '制定执行计划，确定步骤顺序',
      status: 'DONE',
      displayStatus: '已完成',
      requiresConfirm: false,
      order: 2,
    },
    {
      id: 'C_DOC',
      title: '创建飞书文档',
      description: '生成任务管理模板文档',
      status: 'WAIT_CONFIRM',
      displayStatus: '等待确认',
      requiresConfirm: true,
      artifactType: 'doc',
      order: 3,
    },
    {
      id: 'D_SLIDES',
      title: '创建演示稿',
      description: '生成管理层汇报演示稿',
      status: 'PENDING',
      displayStatus: '待完成',
      requiresConfirm: true,
      artifactType: 'slides',
      order: 4,
    },
    {
      id: 'E_REVIEW',
      title: '审核确认',
      description: '用户确认最终产物',
      status: 'PENDING',
      displayStatus: '待完成',
      requiresConfirm: false,
      order: 5,
    },
    {
      id: 'F_DELIVER',
      title: '交付归档',
      description: '将产物归档到飞书',
      status: 'PENDING',
      displayStatus: '待完成',
      requiresConfirm: false,
      order: 6,
    },
  ]
}

function createMockConfirmation(stage: 'confirm1' | 'confirm2' = 'confirm2'): Confirmation {
  if (stage === 'confirm1') {
    return {
      available: true,
      stage: 'confirm1',
      stepId: 'A_UNDERSTAND',
      title: '请确认 Agent 是否正确理解您的需求',
      description: 'Agent 已分析您的输入，请确认理解是否准确后再继续执行。',
      previewReady: false,
    }
  }

  return {
    available: true,
    stage: 'confirm2',
    stepId: 'C_DOC',
    title: '预览已生成，请确认是否正式创建飞书产物',
    description: '你可以查看预览并精修；确认后 Agent 将正式创建飞书产物。',
    artifactType: 'doc',
    theme: 'business',
    previewReady: true,
    preview: {
      available: true,
      type: 'doc',
      title: 'Agent-Pilot 方案文档',
      theme: 'business',
      stepId: 'C_DOC',
    },
  }
}

function createMockPreview(type: 'doc' | 'slides' = 'doc'): Preview {
  if (type === 'slides') {
    return {
      available: true,
      type: 'slides',
      title: '双十一项目作战方案',
      theme: 'business',
      stepId: 'D_SLIDES',
      data: {
        theme: 'business',
        title: '双十一项目作战方案',
        slides: [
          {
            title: '项目目标',
            blocks: [
              { type: 'metric', title: 'GMV目标', value: '3亿' },
              { type: 'metric', title: 'DAU目标', value: '500万' },
            ],
          },
          {
            title: '执行策略',
            blocks: [
              { type: 'text', content: '多渠道引流，提升转化率' },
              { type: 'text', content: '优化用户体验，降低流失' },
            ],
          },
          {
            title: '风险预案',
            blocks: [
              { type: 'text', content: '流量激增时启用备用服务器' },
              { type: 'text', content: '建立 24 小时值班机制' },
            ],
          },
        ],
      },
    }
  }

  return {
    available: true,
    type: 'doc',
    title: 'Agent-Pilot 方案文档',
    theme: 'business',
    stepId: 'C_DOC',
    data: {
      title: 'Agent-Pilot 方案文档',
      summary: '从 IM 对话触发任务，由 Agent 规划、执行、确认并生成文档和演示稿。',
      sections: [
        { title: '背景', content: '团队协作需求通常从 IM 讨论开始，需要跨文档和演示稿沉淀为交付物。' },
        { title: '目标', items: ['捕捉 IM 意图', '生成执行规划', '输出文档与演示稿', '回流交付结果'] },
        { title: '关键机制', content: ['TaskView 驱动任务详情', 'PlanStep 呈现执行链路', 'Artifact 承载预览数据'] },
      ],
    },
  }
}

function createMockAdjustments(type: 'doc' | 'slides' = 'slides'): Adjustments {
  return {
    available: true,
    stepId: type === 'slides' ? 'D_SLIDES' : 'C_DOC',
    type,
    controls: [
      {
        key: 'theme',
        label: '主题',
        type: 'select',
        options: [
          { label: '商务蓝', value: 'business' },
          { label: '科技紫', value: 'tech' },
          { label: '活动红', value: 'campaign' },
          { label: '极简灰', value: 'minimal' },
        ],
      },
      {
        key: 'title',
        label: '标题',
        type: 'text',
      },
      {
        key: type === 'slides' ? 'slides' : 'sections',
        label: type === 'slides' ? '幻灯片结构' : '文档结构',
        type: 'structured',
      },
      {
        key: 'naturalLanguageRefine',
        label: '自然语言精修',
        type: 'instruction',
      },
    ],
    actions: {
      deterministicUpdate: {
        method: 'PUT',
        endpoint: '/api/v1/tasks/{taskId}/steps/{stepId}/preview',
      },
      naturalLanguageRefine: {
        method: 'POST',
        endpoint: '/api/v1/tasks/{taskId}/steps/{stepId}/preview/refine',
      },
    },
  }
}

function createMockOutputs(): Output[] {
  return [
    {
      type: 'doc',
      title: 'Agent-Pilot 方案文档',
      url: 'https://feishu.cn/docx/agent-pilot-plan',
    },
    {
      type: 'slides',
      title: 'Agent-Pilot 管理层汇报演示稿',
      url: 'https://feishu.cn/slides/agent-pilot-report',
    },
  ]
}

function createMockTimeline(): TimelineEvent[] {
  const now = Date.now()
  return [
    {
      timestamp: new Date(now - 300000).toISOString(),
      type: 'TASK_CREATED',
      title: '任务创建',
      message: '用户从飞书聊天发起任务',
      level: 'info',
    },
    {
      timestamp: new Date(now - 280000).toISOString(),
      type: 'STEP_RUNNING',
      title: '理解需求',
      message: 'Agent 正在分析用户输入',
      level: 'info',
      stepId: 'A_UNDERSTAND',
    },
    {
      timestamp: new Date(now - 260000).toISOString(),
      type: 'STEP_DONE',
      title: '理解需求',
      message: '需求分析完成，已提取关键意图',
      level: 'success',
      stepId: 'A_UNDERSTAND',
    },
    {
      timestamp: new Date(now - 240000).toISOString(),
      type: 'STEP_RUNNING',
      title: '生成规划',
      message: 'Agent 正在制定执行计划',
      level: 'info',
      stepId: 'B_PLAN',
    },
    {
      timestamp: new Date(now - 220000).toISOString(),
      type: 'STEP_DONE',
      title: '生成规划',
      message: '执行规划已生成，共 6 个步骤',
      level: 'success',
      stepId: 'B_PLAN',
    },
    {
      timestamp: new Date(now - 200000).toISOString(),
      type: 'STEP_RUNNING',
      title: '创建飞书文档',
      message: '正在生成文档预览',
      level: 'info',
      stepId: 'C_DOC',
    },
    {
      timestamp: new Date(now - 180000).toISOString(),
      type: 'STEP_WAIT_CONFIRM',
      title: '创建飞书文档',
      message: '文档预览已生成，等待用户确认',
      level: 'warning',
      stepId: 'C_DOC',
    },
  ]
}

export function createMockWorkspace(inputText: string): Workspace {
  const now = new Date().toISOString()
  const taskId = `task_${Date.now()}`

  return {
    taskId,
    title: 'Agent-Pilot 任务',
    status: 'WAIT_CONFIRM',
    displayStatus: '等待确认',
    nextAction: 'confirm:C_DOC',
    source: 'im_text',
    userId: 'user_zhouan',
    createdAt: now,
    updatedAt: now,
    inputSummary: inputText.slice(0, 100) + (inputText.length > 100 ? '...' : ''),
    contextText: '用户在飞书群聊中讨论了 Agent-Pilot 方案，需要生成文档和演示稿进行汇报。',
    steps: createMockSteps(),
    confirmation: createMockConfirmation('confirm2'),
    preview: createMockPreview('doc'),
    adjustments: createMockAdjustments('doc'),
    outputs: [],
    timeline: createMockTimeline(),
  }
}

export function mockGetWorkspace(taskId: string): Workspace {
  const now = new Date().toISOString()

  return {
    taskId,
    title: 'Agent-Pilot 任务',
    status: 'DELIVERED',
    displayStatus: '已交付',
    nextAction: 'none',
    source: 'im_text',
    userId: 'user_zhouan',
    createdAt: now,
    updatedAt: now,
    inputSummary: '帮我生成一份 Agent-Pilot 方案文档和演示稿',
    contextText: '用户在飞书群聊中讨论了 Agent-Pilot 方案，需要生成文档和演示稿进行汇报。',
    steps: [
      { id: 'A_UNDERSTAND', title: '理解需求', status: 'DONE', displayStatus: '已完成', requiresConfirm: false, order: 1 },
      { id: 'B_PLAN', title: '生成规划', status: 'DONE', displayStatus: '已完成', requiresConfirm: false, order: 2 },
      { id: 'C_DOC', title: '创建飞书文档', status: 'DONE', displayStatus: '已完成', requiresConfirm: true, artifactType: 'doc', order: 3 },
      { id: 'D_SLIDES', title: '创建演示稿', status: 'DONE', displayStatus: '已完成', requiresConfirm: true, artifactType: 'slides', order: 4 },
      { id: 'E_REVIEW', title: '审核确认', status: 'DONE', displayStatus: '已完成', requiresConfirm: false, order: 5 },
      { id: 'F_DELIVER', title: '交付归档', status: 'DONE', displayStatus: '已完成', requiresConfirm: false, order: 6 },
    ],
    confirmation: { available: false },
    preview: createMockPreview('slides'),
    adjustments: createMockAdjustments('slides'),
    outputs: createMockOutputs(),
    timeline: [
      ...createMockTimeline(),
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_APPROVED',
        title: '创建飞书文档',
        message: '用户确认通过，开始正式创建',
        level: 'success',
        stepId: 'C_DOC',
      },
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_DONE',
        title: '创建飞书文档',
        message: '文档已创建完成',
        level: 'success',
        stepId: 'C_DOC',
      },
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_DONE',
        title: '创建演示稿',
        message: '演示稿已创建完成',
        level: 'success',
        stepId: 'D_SLIDES',
      },
      {
        timestamp: new Date().toISOString(),
        type: 'TASK_DELIVERED',
        title: '任务完成',
        message: '所有产物已交付归档',
        level: 'success',
      },
    ],
  }
}

export function mockWorkspaceConfirm(workspace: Workspace, approved: boolean): Workspace {
  if (!approved) {
    return {
      ...workspace,
      status: 'FAILED',
      displayStatus: '已终止',
      nextAction: 'none',
      confirmation: { available: false },
      timeline: [
        ...workspace.timeline,
        {
          timestamp: new Date().toISOString(),
          type: 'STEP_REJECTED',
          title: '用户拒绝',
          message: '用户拒绝确认，任务已终止',
          level: 'error',
          stepId: workspace.confirmation.stepId,
        },
      ],
    }
  }

  const newSteps = workspace.steps.map((step) =>
    step.id === workspace.confirmation.stepId ? { ...step, status: 'APPROVED' as const, displayStatus: '已通过' } : step
  )

  return {
    ...workspace,
    status: 'PLANNED',
    displayStatus: '执行中',
    nextAction: 'execute',
    steps: newSteps,
    confirmation: { available: false },
    timeline: [
      ...workspace.timeline,
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_APPROVED',
        title: '确认通过',
        message: '用户确认通过，继续执行',
        level: 'success',
        stepId: workspace.confirmation.stepId,
      },
    ],
  }
}

export function mockWorkspaceExecute(workspace: Workspace): Workspace {
  const newSteps = workspace.steps.map((step) =>
    step.status === 'PENDING' || step.status === 'APPROVED' ? { ...step, status: 'DONE' as const, displayStatus: '已完成' } : step
  )

  return {
    ...workspace,
    status: 'DELIVERED',
    displayStatus: '已交付',
    nextAction: 'none',
    steps: newSteps,
    preview: createMockPreview('slides'),
    adjustments: createMockAdjustments('slides'),
    outputs: createMockOutputs(),
    timeline: [
      ...workspace.timeline,
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_RUNNING',
        title: '创建演示稿',
        message: '正在生成演示稿预览',
        level: 'info',
        stepId: 'D_SLIDES',
      },
      {
        timestamp: new Date().toISOString(),
        type: 'STEP_DONE',
        title: '创建演示稿',
        message: '演示稿已创建完成',
        level: 'success',
        stepId: 'D_SLIDES',
      },
      {
        timestamp: new Date().toISOString(),
        type: 'TASK_DELIVERED',
        title: '任务完成',
        message: '所有产物已交付归档',
        level: 'success',
      },
    ],
  }
}
