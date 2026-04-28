// 预览类型按 OpenAPI 编写，同时保留少量旧 mock 字段，方便前端结构化渲染和 fallback。
export type DocumentBlock = {
  id?: string
  type?: 'text' | 'list' | 'table' | 'heading' | string
  text?: string
  items?: string[]
}

export type DocumentSection = {
  // blocks 是新接口的结构化内容；content/text/items 是早期 mock 的兼容字段。
  id?: string
  level?: number
  title?: string
  content?: string | string[]
  text?: string
  items?: string[]
  blocks?: DocumentBlock[]
}

export type DocumentPreviewData = {
  artifactType?: string
  title?: string
  summary?: string
  rawMarkdown?: string
  generatedAt?: string
  outline?: Array<{
    id?: string
    level?: number
    title?: string
  }>
  sections?: DocumentSection[]
  blocks?: DocumentSection[]
  warnings?: string[]
}

export type SlidePreviewData = {
  // rawMarkdown 供后续同步飞书或精修使用；slides 供当前页面卡片化预览。
  artifactType?: string
  title?: string
  rawMarkdown?: string
  generatedAt?: string
  pageCount?: number
  slides?: Array<{
    id?: string
    slideNo?: number
    title?: string
    subtitle?: string
    bodyMarkdown?: string
    bullets?: string[]
    notes?: string
  }>
  warnings?: string[]
}

export type DocumentPreviewRequest = {
  userInput: string
  docType: string
}

export type PresentationPreviewRequest = {
  userInput: string
  topic: string
}
