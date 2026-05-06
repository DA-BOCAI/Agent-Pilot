import { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { ArtifactPreview } from './ArtifactPreview'
import { EmptyState } from './EmptyState'
import OutlinePanel from './OutlinePanel'
import { getArtifactLabel } from '../domain/taskLabels'
import type { Artifact, PatchSlideTextRequest, Preview } from '../types/task'

type PreviewPanelProps = {
  artifacts: Artifact[]
  workspacePreview?: Preview
  isDelivered: boolean
  onDeterministicUpdate?: (previewData: unknown) => void
  onPatchSlideText?: (request: PatchSlideTextRequest) => void
  disabled?: boolean
}

export function PreviewPanel({ 
  artifacts, 
  workspacePreview, 
  isDelivered, 
  onDeterministicUpdate,
  onPatchSlideText,
  disabled = false 
}: PreviewPanelProps) {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [isLoading, setIsLoading] = useState(false)
  const [outlineItems, setOutlineItems] = useState<Array<{ id: string; level: number; title: string }>>([])
  const [activeOutlineId, setActiveOutlineId] = useState<string | undefined>()
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)
  const contentRef = useRef<HTMLDivElement>(null)
  const initialActiveSetRef = useRef(false)
  const isScrollingRef = useRef(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editedData, setEditedData] = useState<unknown>(null)
  const [editError, setEditError] = useState<string>('')

  const previewArtifacts = useMemo(() => {
    if (workspacePreview?.available && workspacePreview.data) {
      const previewArtifact: Artifact = {
        type: workspacePreview.type || 'doc',
        title: workspacePreview.title || '预览',
        data: workspacePreview.data,
      }
      return [previewArtifact, ...artifacts.filter(a => a.type !== workspacePreview.type)]
    }
    return artifacts
  }, [workspacePreview, artifacts])

  const activeArtifact = previewArtifacts[selectedIndex] ?? previewArtifacts[0]

  const handleSelect = useCallback((index: number) => {
    setIsLoading(true)
    setSelectedIndex(index)
    setOutlineItems([])
    setActiveOutlineId(undefined)
    initialActiveSetRef.current = false
    setTimeout(() => setIsLoading(false), 200)
  }, [])

  const handleOutlineChange = useCallback((items: Array<{ id: string; level: number; title: string }>) => {
    setOutlineItems(items)
    if (items.length > 0 && !initialActiveSetRef.current) {
      setActiveOutlineId(items[0].id)
      initialActiveSetRef.current = true
    }
  }, [])

  const handleOutlineItemClick = useCallback((id: string) => {
    setActiveOutlineId(id)
    const container = contentRef.current
    const el = container?.querySelector(`#${CSS.escape(id)}`) as HTMLElement | null
    if (el) {
      isScrollingRef.current = true
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      setTimeout(() => {
        isScrollingRef.current = false
      }, 800)
    }
  }, [])

  const handleStartEdit = useCallback(() => {
    if (!workspacePreview?.data) return
    try {
      const dataCopy = JSON.parse(JSON.stringify(workspacePreview.data))
      setEditedData(dataCopy)
      setIsEditing(true)
      setEditError('')
    } catch {
      setEditError('无法编辑：数据格式错误')
    }
  }, [workspacePreview])

  const handleDataChange = useCallback((newData: unknown) => {
    setEditedData(newData)
  }, [])

  const handleConfirmEdit = useCallback(() => {
    if (!onDeterministicUpdate || !editedData) return
    onDeterministicUpdate(editedData)
    setIsEditing(false)
    setEditedData(null)
    setEditError('')
  }, [editedData, onDeterministicUpdate])

  const handleCancelEdit = useCallback(() => {
    setIsEditing(false)
    setEditedData(null)
    setEditError('')
  }, [])

  useEffect(() => {
    const container = contentRef.current
    if (!container || outlineItems.length === 0) return

    const headingEls: HTMLElement[] = []
    const idToEl = new Map<string, HTMLElement>()
    for (const item of outlineItems) {
      const el = container.querySelector(`#${CSS.escape(item.id)}`) as HTMLElement | null
      if (el) {
        headingEls.push(el)
        idToEl.set(item.id, el)
      }
    }

    if (headingEls.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (isScrollingRef.current) return
        const containerRect = container.getBoundingClientRect()
        let bestId: string | undefined
        let bestTop = Infinity
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          const top = entry.boundingClientRect.top
          if (top <= containerRect.top + 80 && top < bestTop) {
            bestTop = top
            bestId = entry.target.id
          }
        }
        if (bestId) {
          setActiveOutlineId(bestId)
        }
      },
      { root: container, rootMargin: '0px 0px -60% 0px', threshold: 0 }
    )

    headingEls.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [outlineItems])

  const isDocWithOutline = activeArtifact?.type === 'doc' && outlineItems.length > 0
  const isSlidesType = activeArtifact?.type === 'slides'

  if (!previewArtifacts.length) {
    return (
      <section className="preview-panel-section" aria-label="预览区域">
        <div className="preview-panel-header">
          <div className="section-title-wrap">
            <h2 className="section-title">{isDelivered ? '交付预览' : '内容预览'}</h2>
            <span className="section-badge">0 文件</span>
          </div>
        </div>
        <div className="preview-panel-body">
          <EmptyState title="暂无预览" detail="执行完成后将在这里预览文档或演示稿。" />
        </div>
      </section>
    )
  }

  return (
    <section className="preview-panel-section" aria-label="预览区域">
      <div className="preview-panel-header">
        <div className="section-title-wrap">
          <h2 className="section-title">{isDelivered ? '交付预览' : '内容预览'}</h2>
          <span className="section-badge">{previewArtifacts.length} 文件</span>
        </div>
        {!isDelivered && workspacePreview?.available && onDeterministicUpdate && !isEditing && (
          <button
            className="btn btn-secondary preview-edit-btn"
            onClick={handleStartEdit}
            disabled={disabled}
            type="button"
          >
            编辑
          </button>
        )}
      </div>

      <div className="preview-tabs" role="tablist" aria-label="预览文件">
        {previewArtifacts.map((artifact, index) => (
          <button
            key={`${artifact.type}-${artifact.title}-${index}`}
            className={`preview-tab ${artifact.type} ${index === selectedIndex ? 'is-selected' : ''}`}
            onClick={() => handleSelect(index)}
            role="tab"
            aria-selected={index === selectedIndex}
            type="button"
          >
            <span className="tab-type">{getArtifactLabel(artifact.type)}</span>
            <span className="tab-title">{artifact.title}</span>
          </button>
        ))}
      </div>

      <div className="preview-panel-body">
        {isEditing ? (
          <div className="preview-editor">
            {editError && (
              <div className="preview-editor-error">{editError}</div>
            )}
            <div className="preview-editor-content">
              {activeArtifact && editedData && (
                <ArtifactPreview
                  artifact={{ ...activeArtifact, data: editedData }}
                  onOutlineChange={handleOutlineChange}
                  isEditing={true}
                  onDataChange={handleDataChange}
                  onPatchSlideText={onPatchSlideText}
                  disabled={disabled}
                />
              )}
            </div>
            <div className="preview-editor-actions">
              <button
                className="btn btn-primary preview-confirm-btn"
                onClick={handleConfirmEdit}
                disabled={disabled}
                type="button"
              >
                确认
              </button>
              <button
                className="btn btn-secondary preview-cancel-btn"
                onClick={handleCancelEdit}
                disabled={disabled}
                type="button"
              >
                取消
              </button>
            </div>
          </div>
        ) : isLoading ? (
          <div className="preview-loading">
            <div className="loading-spinner" />
            <span>正在加载预览…</span>
          </div>
        ) : activeArtifact ? (
          isDocWithOutline ? (
            <div className="doc-preview-with-outline">
              <div className="preview-content-wrapper" ref={contentRef}>
                <ArtifactPreview artifact={activeArtifact} onOutlineChange={handleOutlineChange} />
              </div>
              <OutlinePanel
                items={outlineItems}
                activeId={activeOutlineId}
                onItemClick={handleOutlineItemClick}
                collapsed={outlineCollapsed}
                onToggleCollapse={() => setOutlineCollapsed(c => !c)}
              />
            </div>
          ) : isSlidesType ? (
            <div className="slides-preview-wrapper">
              <ArtifactPreview artifact={activeArtifact} />
            </div>
          ) : (
            <div className="preview-content-wrapper" ref={contentRef}>
              <ArtifactPreview artifact={activeArtifact} onOutlineChange={handleOutlineChange} />
            </div>
          )
        ) : (
          <EmptyState title="无法预览" detail="当前文件暂无可预览内容。" />
        )}
      </div>
    </section>
  )
}
