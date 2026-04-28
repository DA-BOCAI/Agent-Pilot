import { getArtifactPayload } from '../domain/taskModel'
import { getArtifactLabel } from '../domain/taskLabels'
import { DocumentPreview } from './DocumentPreview'
import { SlidesPreview } from './SlidesPreview'
import type { Artifact } from '../types/task'
import type { DocumentPreviewData, SlidePreviewData } from '../types/preview'

type ArtifactPreviewProps = {
  artifact: Artifact
}

export function ArtifactPreview({ artifact }: ArtifactPreviewProps) {
  const payload = getArtifactPayload(artifact)

  // 预览区只关心归一化后的 artifact 类型；未知或缺少 JSON 时走统一空态。
  if (artifact.type === 'doc' && payload && typeof payload === 'object') {
    return <DocumentPreview data={payload as DocumentPreviewData} fallbackTitle={artifact.title} />
  }

  if (artifact.type === 'slides' && payload && typeof payload === 'object') {
    return <SlidesPreview data={payload as SlidePreviewData} fallbackTitle={artifact.title} />
  }

  return (
    <div className={`preview-empty artifact-${artifact.type}`}>
      <span>{getArtifactLabel(artifact.type)}</span>
      <h4>{artifact.title}</h4>
      <p>后端尚未返回可预览的 JSON 数据。</p>
    </div>
  )
}
