import { useState, useCallback } from 'react'
import type { Adjustments, AdjustmentControl } from '../types/task'

type AdjustmentsPanelProps = {
  adjustments: Adjustments
  onDeterministicUpdate?: (stepId: string, data: Record<string, string>) => void
  onNaturalLanguageRefine?: (stepId: string, instruction: string) => void
  disabled?: boolean
}

export function AdjustmentsPanel({
  adjustments,
  onDeterministicUpdate,
  onNaturalLanguageRefine,
  disabled = false,
}: AdjustmentsPanelProps) {
  const [controlValues, setControlValues] = useState<Record<string, string>>({})
  const [instruction, setInstruction] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleControlChange = useCallback((key: string, value: string) => {
    setControlValues((prev) => ({ ...prev, [key]: value }))
  }, [])

  const handleDeterministicSubmit = useCallback(async () => {
    if (!adjustments.stepId || !onDeterministicUpdate) return
    setIsSubmitting(true)
    try {
      await onDeterministicUpdate(adjustments.stepId, controlValues)
    } finally {
      setIsSubmitting(false)
    }
  }, [adjustments.stepId, controlValues, onDeterministicUpdate])

  const handleNaturalLanguageSubmit = useCallback(async () => {
    if (!adjustments.stepId || !instruction.trim() || !onNaturalLanguageRefine) return
    setIsSubmitting(true)
    try {
      await onNaturalLanguageRefine(adjustments.stepId, instruction.trim())
      setInstruction('')
    } finally {
      setIsSubmitting(false)
    }
  }, [adjustments.stepId, instruction, onNaturalLanguageRefine])

  if (!adjustments.available) {
    return null
  }

  const hasDeterministicAction = adjustments.actions?.deterministicUpdate && adjustments.controls?.length
  const hasNaturalLanguageAction = adjustments.actions?.naturalLanguageRefine

  return (
    <section className="adjustments-panel-section reveal" aria-label="调整面板">
      <div className="adjustments-panel-header">
        <div className="section-title-wrap">
          <h2 className="section-title">调整面板</h2>
          {adjustments.type && (
            <span className="section-badge">{adjustments.type === 'doc' ? '文档' : '演示稿'}</span>
          )}
        </div>
      </div>

      <div className="adjustments-panel-body">
        {hasDeterministicAction && (
          <div className="adjustments-deterministic">
            <h3 className="adjustments-subtitle">确定性调整</h3>
            <div className="adjustments-controls">
              {adjustments.controls!.map((control) => (
                <AdjustmentControlInput
                  key={control.key}
                  control={control}
                  value={controlValues[control.key] ?? ''}
                  onChange={(value) => handleControlChange(control.key, value)}
                  disabled={disabled || isSubmitting}
                />
              ))}
            </div>
            <button
              className="btn btn-secondary adjustments-submit-btn"
              disabled={disabled || isSubmitting}
              onClick={handleDeterministicSubmit}
            >
              {isSubmitting ? '应用中...' : '应用调整'}
            </button>
          </div>
        )}

        {hasNaturalLanguageAction && (
          <div className="adjustments-natural-language">
            <h3 className="adjustments-subtitle">自然语言精修</h3>
            <textarea
              className="adjustments-instruction-input"
              placeholder="输入调整指令，例如：增加一页总结、修改标题字体..."
              value={instruction}
              onChange={(e) => setInstruction(e.target.value)}
              disabled={disabled || isSubmitting}
              rows={3}
            />
            <button
              className="btn btn-primary adjustments-submit-btn"
              disabled={disabled || isSubmitting || !instruction.trim()}
              onClick={handleNaturalLanguageSubmit}
            >
              {isSubmitting ? '精修中...' : '精修'}
            </button>
          </div>
        )}
      </div>
    </section>
  )
}

type AdjustmentControlInputProps = {
  control: AdjustmentControl
  value: string
  onChange: (value: string) => void
  disabled: boolean
}

function AdjustmentControlInput({ control, value, onChange, disabled }: AdjustmentControlInputProps) {
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      onChange(e.target.value)
    },
    [onChange]
  )

  return (
    <div className="adjustment-control">
      <label className="adjustment-control-label">{control.label}</label>
      {control.type === 'select' && control.options ? (
        <select
          className="adjustment-control-select"
          value={value}
          onChange={handleChange}
          disabled={disabled}
        >
          <option value="">请选择</option>
          {control.options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      ) : control.type === 'text' ? (
        <input
          type="text"
          className="adjustment-control-input"
          value={value}
          onChange={handleChange}
          disabled={disabled}
          placeholder={`输入${control.label}`}
        />
      ) : control.type === 'structured' ? (
        <textarea
          className="adjustment-control-textarea"
          value={value}
          onChange={handleChange}
          disabled={disabled}
          rows={2}
          placeholder="输入结构化数据（JSON 格式）"
        />
      ) : control.type === 'instruction' ? (
        <textarea
          className="adjustment-control-textarea"
          value={value}
          onChange={handleChange}
          disabled={disabled}
          rows={2}
          placeholder="输入调整指令"
        />
      ) : null}
    </div>
  )
}
