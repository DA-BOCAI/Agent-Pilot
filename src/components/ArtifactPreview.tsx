import { getArtifactPayload } from '../domain/taskModel'
import { getArtifactLabel } from '../domain/taskLabels'
import { DocumentPreview } from './DocumentPreview'
import { SlidesPreview } from './SlidesPreview'
import type { Artifact } from '../types/task'
import type { DocumentPreviewData, SlidePreviewData } from '../types/preview'

type ArtifactPreviewProps = {
  artifact: Artifact
  onOutlineChange?: (outline: Array<{ id: string; level: number; title: string }>) => void
  isEditing?: boolean
  onDataChange?: (data: unknown) => void
  disabled?: boolean
  isMobile?: boolean
}

export function ArtifactPreview({
  artifact,
  onOutlineChange,
  isEditing = false,
  onDataChange,
  disabled = false,
  isMobile = false
}: ArtifactPreviewProps) {
  const payload = getArtifactPayload(artifact)

  if (artifact.type === 'doc' && payload && typeof payload === 'object') {
    return (
      <DocumentPreview 
        data={payload as DocumentPreviewData} 
        fallbackTitle={artifact.title} 
        onOutlineChange={onOutlineChange}
        isEditing={isEditing}
        onDataChange={onDataChange}
        disabled={disabled}
      />
    )
  }

  if (artifact.type === 'slides' && payload && typeof payload === 'object') {
    return (
      <SlidesPreview
        data={payload as SlidePreviewData}
        fallbackTitle={artifact.title}
        isEditing={isEditing}
        disabled={disabled}
        isMobile={isMobile}
      />
    )
  }

  return (
    <div className={`preview-empty artifact-${artifact.type}`}>
      <span>{getArtifactLabel(artifact.type)}</span>
      <h4>{artifact.title}</h4>
      <p>后端尚未返回可预览的 JSON 数据。</p>
    </div>
  )
}
