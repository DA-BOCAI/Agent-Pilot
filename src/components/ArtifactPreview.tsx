import { getArtifactPayload } from '../domain/taskModel'
import { getArtifactLabel } from '../domain/taskLabels'
import { DocumentPreview } from './DocumentPreview'
import { SlidesPreview } from './SlidesPreview'
import type { Artifact, PatchSlideTextRequest } from '../types/task'
import type { DocumentPreviewData, SlidePreviewData } from '../types/preview'

type ArtifactPreviewProps = {
  artifact: Artifact
  onOutlineChange?: (outline: Array<{ id: string; level: number; title: string }>) => void
  isEditing?: boolean
  onDataChange?: (data: unknown) => void
  onPatchSlideText?: (request: PatchSlideTextRequest) => void
  disabled?: boolean
}

export function ArtifactPreview({ 
  artifact, 
  onOutlineChange,
  isEditing = false,
  onDataChange,
  onPatchSlideText,
  disabled = false
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
        onPatchText={onPatchSlideText}
        disabled={disabled}
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
