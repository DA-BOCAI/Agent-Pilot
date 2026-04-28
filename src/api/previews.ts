import { requestJson } from './http'
import type {
  DocumentPreviewData,
  DocumentPreviewRequest,
  PresentationPreviewRequest,
  SlidePreviewData,
} from '../types/preview'

// 文档预览接口返回 rawMarkdown、outline、sections，页面优先渲染 sections。
export function previewDocument(request: DocumentPreviewRequest): Promise<DocumentPreviewData> {
  return requestJson<DocumentPreviewData>('/previews/document', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

// PPT 预览接口返回 slides 数组，页面按 slide card 模拟翻页预览。
export function previewPresentation(request: PresentationPreviewRequest): Promise<SlidePreviewData> {
  return requestJson<SlidePreviewData>('/previews/presentation', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
