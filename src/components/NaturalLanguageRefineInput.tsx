import { useState, useCallback, useRef, useEffect } from 'react'

type NaturalLanguageRefineInputProps = {
  previewType?: 'doc' | 'slides'
  previewStepId?: string
  onRefine: (stepId: string, instruction: string) => Promise<void>
  disabled?: boolean
  isMobile?: boolean
  onExpandChange?: (expanded: boolean) => void
}

function getStepId(previewType?: 'doc' | 'slides', previewStepId?: string): string | null {
  if (previewStepId) return previewStepId
  if (previewType === 'slides') return 'D_SLIDES'
  if (previewType === 'doc') return 'C_DOC'
  return null
}

function getTypeLabel(previewType?: 'doc' | 'slides'): string {
  if (previewType === 'slides') return 'PPT'
  if (previewType === 'doc') return '文档'
  return '内容'
}

export function NaturalLanguageRefineInput({
  previewType,
  previewStepId,
  onRefine,
  disabled = false,
  isMobile = false,
  onExpandChange,
}: NaturalLanguageRefineInputProps) {
  const [instruction, setInstruction] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [isExpanded, setIsExpanded] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  const stepId = getStepId(previewType, previewStepId)
  const typeLabel = getTypeLabel(previewType)
  const hasPreview = stepId !== null

  // 通知父组件展开状态变化
  useEffect(() => {
    onExpandChange?.(isExpanded)
  }, [isExpanded, onExpandChange])

  // 点击外部关闭面板
  useEffect(() => {
    if (!isMobile || !isExpanded) return

    const handleClickOutside = (event: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setIsExpanded(false)
      }
    }

    const handleEscKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsExpanded(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscKey)

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscKey)
    }
  }, [isMobile, isExpanded])

  // 展开时自动聚焦
  useEffect(() => {
    if (isExpanded && textareaRef.current) {
      setTimeout(() => textareaRef.current?.focus(), 100)
    }
  }, [isExpanded])

  const handleSubmit = useCallback(async () => {
    if (!stepId || !instruction.trim()) return

    setIsSubmitting(true)
    setError('')

    try {
      await onRefine(stepId, instruction.trim())
      setInstruction('')
      if (isMobile) {
        setIsExpanded(false)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '精修失败，请重试')
    } finally {
      setIsSubmitting(false)
    }
  }, [stepId, instruction, onRefine, isMobile])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }, [handleSubmit])

  if (!hasPreview) {
    return (
      <div className="natural-language-refine-input">
        <div className="natural-language-refine-empty">
          <span className="natural-language-refine-hint">暂无预览内容，无法进行精修</span>
        </div>
      </div>
    )
  }

  // 移动端浮动按钮模式
  if (isMobile) {
    return (
      <>
        {/* 浮动按钮 */}
        {!isExpanded && (
          <button
            className="refine-fab"
            onClick={() => setIsExpanded(true)}
            disabled={disabled}
            type="button"
            aria-label="打开调整输入"
          >
            <span className="refine-fab-icon">✏️</span>
          </button>
        )}

        {/* 展开的输入面板 */}
        {isExpanded && (
          <div className="refine-panel-overlay">
            <div ref={panelRef} className="refine-panel">
              <div className="refine-panel-header">
                <span className="refine-panel-title">调整{typeLabel}</span>
                <button
                  className="refine-panel-close"
                  onClick={() => setIsExpanded(false)}
                  type="button"
                >
                  ✕
                </button>
              </div>
              <div className="refine-panel-body">
                <textarea
                  ref={textareaRef}
                  className="refine-panel-textarea"
                  placeholder={`输入指令调整${typeLabel}，例如：修改标题、添加内容...`}
                  value={instruction}
                  onChange={(e) => setInstruction(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={disabled || isSubmitting}
                  rows={3}
                />
                {error && (
                  <div className="refine-panel-error">
                    {error}
                  </div>
                )}
              </div>
              <div className="refine-panel-footer">
                <button
                  className="btn btn-secondary refine-panel-cancel"
                  onClick={() => setIsExpanded(false)}
                  disabled={isSubmitting}
                  type="button"
                >
                  取消
                </button>
                <button
                  className="btn btn-primary refine-panel-submit"
                  onClick={handleSubmit}
                  disabled={disabled || isSubmitting || !instruction.trim()}
                  type="button"
                >
                  {isSubmitting ? '发送中...' : '发送'}
                </button>
              </div>
            </div>
          </div>
        )}
      </>
    )
  }

  // 桌面端常规模式
  return (
    <div className="natural-language-refine-input">
      <div className="natural-language-refine-input-wrapper">
        <textarea
          className="natural-language-refine-textarea"
          placeholder={`输入指令调整${typeLabel}...`}
          value={instruction}
          onChange={(e) => setInstruction(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled || isSubmitting}
          rows={1}
        />
        <button
          className="btn btn-primary natural-language-refine-submit"
          onClick={handleSubmit}
          disabled={disabled || isSubmitting || !instruction.trim()}
          type="button"
        >
          {isSubmitting ? '...' : '发送'}
        </button>
      </div>

      {error && (
        <div className="natural-language-refine-error">
          {error}
        </div>
      )}
    </div>
  )
}
