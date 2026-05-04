import { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { ArtifactPreview } from './ArtifactPreview'
import { EmptyState } from './EmptyState'
import OutlinePanel from './OutlinePanel'
import { getArtifactLabel } from '../domain/taskLabels'
import type { Artifact, Preview } from '../types/task'

type PreviewPanelProps = {
  artifacts: Artifact[]
  workspacePreview?: Preview
  isDelivered: boolean
}

export function PreviewPanel({ artifacts, workspacePreview, isDelivered }: PreviewPanelProps) {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [isLoading, setIsLoading] = useState(false)
  const [outlineItems, setOutlineItems] = useState<Array<{ id: string; level: number; title: string }>>([])
  const [activeOutlineId, setActiveOutlineId] = useState<string | undefined>()
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)
  const contentRef = useRef<HTMLDivElement>(null)

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
    setTimeout(() => setIsLoading(false), 200)
  }, [])

  const handleOutlineChange = useCallback((items: Array<{ id: string; level: number; title: string }>) => {
    setOutlineItems(items)
    if (items.length > 0 && !activeOutlineId) {
      setActiveOutlineId(items[0].id)
    }
  }, [activeOutlineId])

  const handleOutlineItemClick = useCallback((id: string) => {
    setActiveOutlineId(id)
    const container = contentRef.current
    const el = container?.querySelector(`#${CSS.escape(id)}`) as HTMLElement | null
    if (container && el) {
      const containerRect = container.getBoundingClientRect()
      const elRect = el.getBoundingClientRect()
      container.scrollTo({
        top: container.scrollTop + elRect.top - containerRect.top - 16,
        behavior: 'smooth',
      })
    }
  }, [])

  useEffect(() => {
    const container = contentRef.current
    if (!container || outlineItems.length === 0) return

    const handleScroll = () => {
      const headings = outlineItems
        .map(item => ({ id: item.id, el: container.querySelector(`#${CSS.escape(item.id)}`) as HTMLElement | null }))
        .filter(h => h.el !== null)

      for (let i = headings.length - 1; i >= 0; i--) {
        const rect = headings[i].el!.getBoundingClientRect()
        const containerRect = container.getBoundingClientRect()
        if (rect.top <= containerRect.top + 80) {
          setActiveOutlineId(headings[i].id)
          return
        }
      }
      if (headings.length > 0) {
        setActiveOutlineId(headings[0].id)
      }
    }

    container.addEventListener('scroll', handleScroll)
    return () => container.removeEventListener('scroll', handleScroll)
  }, [outlineItems])

  const isDocWithOutline = activeArtifact?.type === 'doc' && outlineItems.length > 0

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
        {isLoading ? (
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
