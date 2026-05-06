import { useState, useEffect, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { SlidesEditor } from './SlidesEditor'
import type { SlidePreviewData } from '../types/preview'
import type { PatchSlideTextRequest } from '../types/task'

type SlidesPreviewProps = {
  data: SlidePreviewData
  fallbackTitle: string
  isEditing?: boolean
  onPatchText?: (request: PatchSlideTextRequest) => void
  disabled?: boolean
  isMobile?: boolean
}

export function SlidesPreview({
  data,
  fallbackTitle,
  isEditing = false,
  onPatchText,
  disabled = false,
  isMobile = false
}: SlidesPreviewProps) {
  const slides = data.slides ?? []
  const totalPages = slides.length

  const [currentPage, setCurrentPage] = useState(1)
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)

  const safePage = Math.min(Math.max(1, currentPage), totalPages || 1)

  const goToPage = useCallback((page: number) => {
    setCurrentPage(page)
  }, [])

  const goPrev = useCallback(() => {
    setCurrentPage((prev) => Math.max(1, prev - 1))
  }, [])

  const goNext = useCallback(() => {
    setCurrentPage((prev) => Math.min(totalPages, prev + 1))
  }, [totalPages])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') {
        goPrev()
      } else if (e.key === 'ArrowRight') {
        goNext()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [goPrev, goNext])

  if (isEditing && onPatchText) {
    return (
      <SlidesEditor
        data={data}
        onPatchText={onPatchText}
        disabled={disabled}
        isMobile={isMobile}
      />
    )
  }

  if (!slides.length) {
    return (
      <div className="slides-preview artifact-slides">
        <header>
          <span>Slides Preview</span>
          <h4>{data.title ?? fallbackTitle}</h4>
        </header>
        <pre>{JSON.stringify(data, null, 2)}</pre>
      </div>
    )
  }

  const currentSlide = slides[safePage - 1]

  return (
    <div className="slides-preview artifact-slides">
      {/* 移动端隐藏大纲侧边栏 */}
      {!isMobile && (
        <aside className={`slide-outline${outlineCollapsed ? ' is-collapsed' : ''}`}>
          <div className="slide-outline-header">
            {!outlineCollapsed && <span>目录</span>}
            <button
              className="slide-outline-toggle"
              onClick={() => setOutlineCollapsed((v) => !v)}
              aria-label={outlineCollapsed ? '展开目录' : '收起目录'}
            >
              {outlineCollapsed ? '▸' : '◂'}
            </button>
          </div>
          {!outlineCollapsed && (
            <ul className="slide-outline-list">
              {slides.map((slide, index) => (
                <li
                  key={slide.id ?? `${slide.title ?? 'slide'}-${index}`}
                  className={`slide-outline-item${safePage === index + 1 ? ' is-active' : ''}`}
                  onClick={() => goToPage(index + 1)}
                >
                  <span className="slide-outline-item-no">{String(slide.slideNo ?? index + 1).padStart(2, '0')}</span>
                  <span>{slide.title ?? `Slide ${index + 1}`}</span>
                </li>
              ))}
            </ul>
          )}
        </aside>
      )}

      <div className="slide-main">
        <header>
          <span>Slides Preview</span>
          <h4>{data.title ?? fallbackTitle}</h4>
        </header>

        <div className="slide-stage-wrapper">
          <div className="slide-stage">
            <article className="slide-page">
              <h2 className="slide-stage-title">{currentSlide.title ?? `Slide ${safePage}`}</h2>
              {currentSlide.subtitle && <h3 className="slide-stage-subtitle">{currentSlide.subtitle}</h3>}
              {currentSlide.bodyMarkdown && (
                <div className="slide-stage-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {currentSlide.bodyMarkdown}
                  </ReactMarkdown>
                </div>
              )}
              {currentSlide.bullets?.length ? (
                <ul className="slide-stage-body">
                  {currentSlide.bullets.map((bullet, i) => (
                    <li key={`${bullet}-${i}`}>{bullet}</li>
                  ))}
                </ul>
              ) : null}
              {currentSlide.notes && <small className="slide-stage-notes">{currentSlide.notes}</small>}
            </article>
          </div>
        </div>

        <div className="slide-nav">
          <button
            className="slide-nav-btn"
            disabled={safePage <= 1}
            onClick={goPrev}
            aria-label="上一页"
          >
            ‹
          </button>
          <span className="slide-nav-indicator">
            {safePage} / {totalPages}
          </span>
          <button
            className="slide-nav-btn"
            disabled={safePage >= totalPages}
            onClick={goNext}
            aria-label="下一页"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  )
}
