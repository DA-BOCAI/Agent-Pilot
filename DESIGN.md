---
name: Agent-Pilot
description: 飞书内嵌的 IM 办公协同 Agent 任务仪表盘
colors:
  feishu-blue: "#3370ff"
  feishu-blue-strong: "#245bdb"
  feishu-blue-hover: "#4e83fd"
  feishu-blue-soft: "#eff4ff"
  feishu-blue-border: "#c9ddff"
  canvas-bg: "#f7f8fa"
  paper: "#ffffff"
  paper-soft: "#f9fafb"
  text-primary: "#1f2329"
  text-secondary: "#646a73"
  text-tertiary: "#8f959e"
  border-subtle: "#dee0e3"
  border-strong: "#bbbfc4"
  border-blue-soft: "#c9ddff"
  neutral-chip: "#f2f3f5"
  warning: "#de7802"
  warning-soft: "#fff7e8"
  warning-border: "#f3d49b"
  success: "#2ea121"
  success-soft: "#edf8ed"
  success-border: "#b7e6b0"
  danger: "#d83931"
  danger-bg: "#fff2f0"
  danger-border: "#f8d1cd"
  artifact-slides: "#8f5e00"
  artifact-slides-soft: "#fff7e8"
  artifact-slides-border: "#f3d49b"
  artifact-delivery: "#2ea121"
  artifact-delivery-soft: "#edf8ed"
  artifact-delivery-border: "#b7e6b0"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "clamp(30px, 4.2vw, 48px)"
    fontWeight: 750
    lineHeight: 1.08
    letterSpacing: "normal"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.22
    letterSpacing: "normal"
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "18px"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "normal"
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.65
    letterSpacing: "normal"
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "normal"
rounded:
  sm: "6px"
  md: "8px"
  pill: "999px"
spacing:
  page-gutter: "clamp(12px, 3vw, 40px)"
  xs: "4px"
  compact: "6px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "32px"
  section: "40px"
components:
  button-primary:
    backgroundColor: "{colors.feishu-blue}"
    textColor: "{colors.paper}"
    rounded: "{rounded.sm}"
    padding: "7px 12px"
    height: "36px"
  button-primary-hover:
    backgroundColor: "{colors.feishu-blue-hover}"
    textColor: "{colors.paper}"
    rounded: "{rounded.sm}"
  button-secondary:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.sm}"
    padding: "7px 12px"
    height: "36px"
  status-chip:
    backgroundColor: "{colors.feishu-blue-soft}"
    textColor: "{colors.feishu-blue}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
  surface-card:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: "16px"
  input-field:
    backgroundColor: "{colors.paper-soft}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.sm}"
    padding: "12px"
---

# Design System: Agent-Pilot

## 1. Overview

**Creative North Star: "飞书任务夹板"**

Agent-Pilot 的界面像飞书工作流旁边的一块任务夹板：轻、清楚、稳定，只把用户此刻需要判断的任务状态、确认动作和交付预览放到前台。它服务于 IM 触发后的办公协同链路，不抢飞书生态本身的主场。

系统应保持产品型界面的克制密度。蓝色用于行动和当前状态，白色与浅灰承担信息分区，细边框负责结构，轻阴影只提示层级。不要制造独立品牌站感，也不要把 AI 能力包装成夸张的视觉奇观。

**Key Characteristics:**
- 低噪音飞书蓝白灰，主色只用于可行动或当前状态。
- 信息架构稳定，优先展示任务概览、进度步骤、飞书上下文、当前动作、任务步骤和预览区。
- 业务语言优先，技术字段默认隐藏。
- 紧凑但不拥挤，适配飞书内嵌桌面和窄屏视口。
- 页面使用固定最大宽度和弹性页边距，内容密度随视口折叠，不改变模块顺序。
- 当前动作卡是唯一允许加重的焦点区域，用更强状态面和更重字重表达下一步。

## 2. Colors

这是一套 restrained 的飞书协作调色板：浅灰画布、白色工作面、一个明确蓝色主声部，以及少量状态色。

### Primary
- **飞书行动蓝**: 主要用于主按钮、当前步骤、可选中的预览 tab、状态强调和小型标签。它不是装饰色，必须对应可行动或当前上下文。
- **飞书强调蓝**: 只用于当前动作标题、主按钮边框和需要更强焦点的少量文字。
- **行动蓝 Hover**: 只用于主按钮 hover，不要扩大为新的品牌色。
- **浅蓝确认面**: 用于当前步骤、已完成步骤、选中 tab 和轻量信息标签，表达“已被系统识别或需要关注”。

### Neutral
- **工作区浅灰**: 页面背景，提供飞书内嵌应用的安静底色。
- **白色纸面**: 顶部概览、主面板和主要卡片的承载面。
- **浅灰纸面**: 预览区、代码块、步骤行和轻量空状态背景。
- **飞书正文黑**: 标题、正文和核心业务内容。
- **次级灰**: 描述、元信息、辅助说明和未激活步骤。
- **三级灰**: 低优先级状态、脚注和非关键辅助信息。
- **细分割线**: 默认边框和面板分隔。
- **强分割线**: hover 边框、虚线空状态和需要更清楚结构的地方。

### Tertiary
- **等待橙**: 只表示等待确认或需要用户介入。
- **完成绿**: 只表示交付完成或成功状态。
- **失败红**: 只表示失败、拒绝或错误消息。
- **演示稿琥珀**: 只用于预览 tab 和演示稿预览容器的类型识别，不能表示警告。
- **交付绿**: 用于交付链接预览，和完成状态同色但必须由文案区分含义。

### Named Rules

**The One Blue Rule.** 蓝色只表达主行动、当前状态和选中状态；不要用第二套蓝紫或青色体系制造品牌感。

**The Quiet Canvas Rule.** 页面背景必须保持浅灰，主要内容必须落在白色或浅灰纸面上；不要用深色科技背景、渐变背景或大面积彩色容器。

**The Semantic Tint Rule.** 橙、绿、红必须成套出现为文字、浅底和 1px 全边框；禁止只靠单一色块表达状态。

## 3. Typography

**Display Font:** 系统中文无衬线栈，优先 `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `PingFang SC`, `Microsoft YaHei UI`, `Microsoft YaHei`。
**Body Font:** 同一系统中文无衬线栈。
**Label/Mono Font:** 标签沿用系统中文无衬线；步骤代码可使用 `Cascadia Mono`, `Consolas`, `ui-monospace`, `monospace`。

**Character:** 字体系统不追求品牌个性，而追求飞书内嵌环境的一致性和中文可读性。层级通过字号、字重和空间建立，不靠字间距或装饰字体。

### Hierarchy
- **Display** (750, `clamp(30px, 4.2vw, 48px)`, 1.08): 顶部任务标题，只用于当前任务或创建任务入口。
- **Headline** (700, 24px, 1.22): 主面板标题和大区块标题。
- **Title** (700, 18px, 1.3): 卡片标题。
- **Body** (400, 14px, 1.65): 任务说明、飞书上下文、预览正文和空状态说明。正文行长应控制在 65-75ch 内。
- **Label** (600, 12px, normal): 状态标签、步骤小字、tab 类型和弱提示。

### Named Rules

**The Business Language Rule.** 标题和正文必须说业务状态，不说内部实现；除非是开发调试视图，避免把 `taskId`、`requestId`、`createdAt`、`updatedAt` 放到界面层级中。

## 4. Elevation

系统采用“边框为主、阴影为辅”的混合层级。默认结构由 1px 细边框定义，阴影只用于顶层容器和焦点反馈，强度必须轻，不能出现浮夸的玻璃感或暗色大投影。

### Shadow Vocabulary
- **Panel Ambient** (`box-shadow: 0 8px 24px rgba(31, 35, 41, 0.06)`): 顶部概览和主面板，用来让白色纸面从浅灰背景中轻微抬起。
- **Active Inset** (`box-shadow: inset 0 0 0 1px rgba(51, 112, 255, 0.12)`): 当前步骤的轻量内描边，配合浅蓝底使用。
- **Focus Ring** (`box-shadow: 0 0 0 3px rgba(51, 112, 255, 0.14)`): 输入焦点状态，必须明显但不能压过内容。

### Named Rules

**The Border First Rule.** 先用细边框和背景层次分区，只有顶层面板和 focus 可以使用阴影。

## 5. Components

### Buttons

- **Shape:** 小圆角按钮，使用 6px radius，最小高度 36px。
- **Primary:** 飞书行动蓝背景、白色文字、40px 最小高度，使用强调蓝边框和 650 字重，用于创建、生成规划、执行、通过和重新创建等主动作。
- **Hover / Focus:** hover 切换到行动蓝 hover；focus 使用浅蓝 focus ring，不改变布局尺寸。
- **Secondary:** 白底、细边框、正文黑，用于刷新、新建、返回 IM、拒绝等次级动作。hover 只改变边框和文字为行动蓝。

### Chips

- **Style:** 圆角胶囊，浅蓝底、行动蓝文字、1px 浅蓝边框，用于模式提示和轻量状态。
- **State:** 状态 chip 可使用蓝、橙、绿、红四类，但只能表达任务状态，不要变成装饰标签。

### Cards / Containers

- **Corner Style:** 主面板和业务卡片使用 8px radius；步骤行、预览子块和按钮使用 6px radius。
- **Background:** 主容器白底，预览和空状态使用浅灰纸面。
- **Shadow Strategy:** 顶层面板使用 Panel Ambient，普通卡片默认无阴影，hover 只改变边框。
- **Border:** 所有卡片保留 1px 细边框，当前状态使用浅蓝边框。
- **Internal Padding:** 顶部概览 22-24px，普通卡片 16px，紧凑子项 12-14px。
- **Page Rhythm:** 页面最大宽度为 1180px，页边距使用 `clamp(12px, 3vw, 40px)`；相邻模块用 16px gap，组内关系用 6-12px gap。

### Current Action

当前动作卡是产品界面的主焦点。默认使用浅蓝底和强调蓝标题；等待确认切换为浅橙，交付完成切换为浅绿，失败切换为浅红。它可以比普通卡片更重，但不能引入额外装饰、图标堆叠或第二个主按钮。

### Inputs / Fields

- **Style:** 浅灰纸面背景、细边框、6px radius、12px padding。
- **Focus:** 边框切换到行动蓝，并出现 3px 浅蓝 focus ring。
- **Error / Disabled:** 错误使用红色边框与浅红背景；disabled 降低透明度并禁用指针。

### Navigation

当前界面没有完整导航系统，只有顶部辅助操作和预览 tab。辅助操作使用按钮组，允许换行；预览 tab 在桌面端左侧纵向排列，窄屏下改为单列或多列堆叠。

### Progress Strip

进度条是该产品的签名组件。它使用 5 列等宽步骤卡，字母编号放在胶囊中，当前或完成步骤使用浅蓝底和蓝色编号。移动端必须折叠为单列，不能改变步骤顺序。

### Preview Tabs

预览 tab 用轻量分类色帮助用户区分产物类型：文档使用行动蓝，演示稿使用琥珀，交付链接使用完成绿。桌面端 tab 固定在左侧 220-280px 列中，内容区自适应；中窄屏下 tab 转为自适应横向网格；移动端单列堆叠。选中态使用同色浅底和 1px 全边框，标题文字保持正文黑，预览内容容器不整块染色。

## 6. Do's and Don'ts

### Do:

- **Do** 使用 `#3370ff` 作为唯一主行动色，并把它限制在按钮、当前状态、选中项和小型标签中。
- **Do** 保持 `#f7f8fa` 背景、白色主面板、细分割线和轻阴影，贴近飞书内嵌应用。
- **Do** 用橙、绿、红的浅底和全边框表达等待、完成、失败，不要只换文字颜色。
- **Do** 优先展示任务概览、进度步骤、飞书上下文、当前动作、任务步骤和预览区。
- **Do** 在窄屏下保持模块顺序，只把网格折叠为单列。
- **Do** 用 spacing token 控制间距，避免在同一层级混用任意 10px、13px、18px 这类无语义值。
- **Do** 保持一个明确主动作；刷新、新建、返回这类工具动作不要和当前动作并列抢注意力。
- **Do** 把视觉强化集中在当前动作卡和主按钮，其他区域保持安静。
- **Do** 尊重 `prefers-reduced-motion`，动画只用于轻微入场、hover 和 focus 反馈。

### Don't:

- **Don't** 做成通用 agent dashboard、运维控制台或调试台。
- **Don't** 默认加入右侧工作台、事件流、审计日志、trace 面板、技术元数据、任务 ID、请求 ID、创建时间和更新时间。
- **Don't** 把自然语言入口放在页面输入框里；飞书聊天 API 是默认输入来源。
- **Don't** 使用重装饰、强品牌渐变、大面积深色科技感、过度卡片化布局或营销页面式 hero。
- **Don't** 使用彩色侧边条、渐变文字、玻璃拟态或重复的图标卡片网格。
- **Don't** 把产物分类色扩散到正文区、主按钮或页面背景。
- **Don't** 在同一屏重复表达相同信息，例如顶部摘要、上下文卡片和动作卡片同时重复完整状态链路。
- **Don't** 为了“更大胆”添加渐变文字、玻璃卡片、深色科技背景或额外装饰图形。
