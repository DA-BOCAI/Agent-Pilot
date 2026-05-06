import { useState, useCallback } from 'react'

type NaturalLanguageRefineInputProps = {
  previewType?: 'doc' | 'slides'
  previewStepId?: string
  onRefine: (stepId: string, instruction: string) => Promise<void>
  disabled?: boolean
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
}: NaturalLanguageRefineInputProps) {
  const [instruction, setInstruction] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')

  const stepId = getStepId(previewType, previewStepId)
  const typeLabel = getTypeLabel(previewType)
  const hasPreview = stepId !== null

  const handleSubmit = useCallback(async () => {
    if (!stepId || !instruction.trim()) return

    setIsSubmitting(true)
    setError('')
    
    try {
      await onRefine(stepId, instruction.trim())
      setInstruction('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '精修失败，请重试')
    } finally {
      setIsSubmitting(false)
    }
  }, [stepId, instruction, onRefine])

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

  return (
    <div className="natural-language-refine-input">
      <div className="natural-language-refine-header">
        <div className="section-title-wrap">
          <h2 className="section-title">自然语言精修</h2>
          <span className="section-badge">{typeLabel}</span>
        </div>
      </div>
      
      <div className="natural-language-refine-body">
        <div className="natural-language-refine-input-wrapper">
          <textarea
            className="natural-language-refine-textarea"
            placeholder={`输入调整指令，例如：更正式、优化讲稿、增加时间线页...`}
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled || isSubmitting}
            rows={2}
          />
          <button
            className="btn btn-primary natural-language-refine-submit"
            onClick={handleSubmit}
            disabled={disabled || isSubmitting || !instruction.trim()}
            type="button"
          >
            {isSubmitting ? '精修中...' : '精修'}
          </button>
        </div>
        
        {error && (
          <div className="natural-language-refine-error">
            {error}
          </div>
        )}
      </div>
    </div>
  )
}
