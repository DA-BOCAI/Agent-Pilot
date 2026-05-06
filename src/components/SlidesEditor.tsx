import { useState, useEffect, useCallback } from 'react'
import type { SlidePreviewData } from '../types/preview'
import type { PatchSlideTextRequest } from '../types/task'

type SlidesEditorProps = {
  data: SlidePreviewData
  onPatchText: (request: PatchSlideTextRequest) => void
  disabled?: boolean
  isMobile?: boolean
}

export function SlidesEditor({ data, onPatchText, disabled, isMobile = false }: SlidesEditorProps) {
  const slides = data.slides ?? []
  const totalPages = slides.length

  const [currentPage, setCurrentPage] = useState(1)
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)

  const safePage = Math.min(Math.max(1, currentPage), totalPages || 1)
  const currentSlide = slides[safePage - 1]

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

  if (!slides.length) {
    return (
      <div className="slides-editor">
        <div className="slide-main">
          <div className="editor-field">
            <label>PPT 标题</label>
            <input
              type="text"
              value={data.title ?? ''}
              onChange={(e) => onPatchText({ target: 'deckTitle', value: e.target.value })}
              disabled={disabled}
              placeholder="输入 PPT 标题..."
            />
          </div>
        </div>
      </div>
    )
  }

  const handleDeckTitleChange = (value: string) => {
    onPatchText({ target: 'deckTitle', value })
  }

  const handleTitleChange = (value: string) => {
    onPatchText({
      slideId: currentSlide?.id,
      slideNo: safePage,
      target: 'title',
      value
    })
  }

  const handleBodyMarkdownChange = (value: string) => {
    onPatchText({
      slideId: currentSlide?.id,
      slideNo: safePage,
      target: 'bodyMarkdown',
      value
    })
  }

  const handleBulletChange = (index: number, value: string) => {
    onPatchText({
      slideId: currentSlide?.id,
      slideNo: safePage,
      target: 'bullet',
      bulletIndex: index,
      value
    })
  }

  const handleSpeakerNotesChange = (value: string) => {
    onPatchText({
      slideId: currentSlide?.id,
      slideNo: safePage,
      target: 'speakerNotes',
      value
    })
  }

  return (
    <div className="slides-editor">
      {/* 移动端隐藏大纲侧边栏 */}
      {!isMobile && (
        <aside className={`slide-outline${outlineCollapsed ? ' is-collapsed' : ''}`}>
          <div className="slide-outline-header">
            {!outlineCollapsed && <span>目录</span>}
            <button
              className="slide-outline-toggle"
              onClick={() => setOutlineCollapsed((v) => !v)}
              aria-label={outlineCollapsed ? '展开目录' : '收起目录'}
              type="button"
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
        <div className="editor-field">
          <label>PPT 标题</label>
          <input
            type="text"
            value={data.title ?? ''}
            onChange={(e) => handleDeckTitleChange(e.target.value)}
            disabled={disabled}
            placeholder="输入 PPT 标题..."
          />
        </div>

        <div className="editor-field">
          <label>幻灯片标题</label>
          <input
            type="text"
            value={currentSlide?.title ?? ''}
            onChange={(e) => handleTitleChange(e.target.value)}
            disabled={disabled}
            placeholder="输入幻灯片标题..."
          />
        </div>

        {currentSlide?.subtitle && (
          <div className="editor-field">
            <label>副标题</label>
            <input
              type="text"
              value={currentSlide.subtitle}
              disabled={disabled}
              placeholder="副标题通常作为 bodyMarkdown 的一部分..."
            />
          </div>
        )}

        {currentSlide?.bullets && currentSlide.bullets.length > 0 ? (
          <div className="editor-field">
            <label>列表项</label>
            <div className="slide-bullets-editor">
              {currentSlide.bullets.map((bullet, index) => (
                <input
                  key={index}
                  type="text"
                  value={bullet}
                  onChange={(e) => handleBulletChange(index, e.target.value)}
                  disabled={disabled}
                  placeholder={`列表项 ${index + 1}`}
                  className="slide-bullet-input"
                />
              ))}
            </div>
          </div>
        ) : currentSlide?.bodyMarkdown ? (
          <div className="editor-field">
            <label>内容</label>
            <textarea
              value={currentSlide.bodyMarkdown}
              onChange={(e) => handleBodyMarkdownChange(e.target.value)}
              disabled={disabled}
              placeholder="输入内容..."
              rows={10}
              className="slide-content-textarea"
            />
          </div>
        ) : null}

        <div className="editor-field">
          <label>备注</label>
          <textarea
            value={currentSlide?.notes ?? ''}
            onChange={(e) => handleSpeakerNotesChange(e.target.value)}
            disabled={disabled}
            placeholder="输入备注..."
            rows={3}
            className="slide-notes-textarea"
          />
        </div>

        <div className="slide-nav">
          <button
            className="slide-nav-btn"
            disabled={safePage <= 1}
            onClick={goPrev}
            aria-label="上一页"
            type="button"
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
            type="button"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  )
}
