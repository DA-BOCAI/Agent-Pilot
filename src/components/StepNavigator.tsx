import { useCallback } from 'react'
import type { PlanStep } from '../types/task'
import { stepStatusLabels, getStepMarker } from '../domain/taskLabels'

type StepNavigatorProps = {
  steps: PlanStep[]
  activeStepCode: string | null
  onStepClick?: (stepId: string) => void
}

const statusConfig: Record<string, { icon: string; color: string; bg: string; border: string }> = {
  PENDING:    { icon: '○', color: 'var(--muted)',        bg: 'var(--paper)',        border: 'var(--line)' },
  RUNNING:    { icon: '◐', color: 'var(--primary)',      bg: 'var(--primary-soft)', border: 'var(--primary-border)' },
  WAIT_CONFIRM:{ icon: '!', color: 'var(--warning)',     bg: 'var(--warning-soft)', border: 'var(--warning-border)' },
  APPROVED:   { icon: '✓', color: 'var(--success)',      bg: 'var(--success-soft)', border: 'var(--success-border)' },
  DONE:       { icon: '✓', color: 'var(--success)',      bg: 'var(--success-soft)', border: 'var(--success-border)' },
  SKIPPED:    { icon: '—', color: 'var(--danger)',       bg: 'var(--danger-soft)',  border: 'var(--danger-border)' },
  FAILED:     { icon: '✕', color: 'var(--danger)',       bg: 'var(--danger-soft)',  border: 'var(--danger-border)' },
}

export function StepNavigator({ steps, activeStepCode, onStepClick }: StepNavigatorProps) {
  const handleClick = useCallback((stepId: string) => {
    onStepClick?.(stepId)
  }, [onStepClick])

  if (!steps.length) {
    return (
      <section className="step-navigator-section reveal" aria-label="任务步骤">
        <div className="step-navigator-header">
          <h2 className="section-title">任务流程</h2>
          <span className="section-badge">0 步骤</span>
        </div>
        <div className="step-navigator-empty">
          <div className="empty-illustration">📋</div>
          <p>任务创建后将在这里展示完整的规划步骤</p>
        </div>
      </section>
    )
  }

  const doneCount = steps.filter((s) => s.status === 'DONE' || s.status === 'APPROVED').length
  const progress = Math.round((doneCount / steps.length) * 100)

  return (
    <section className="step-navigator-section reveal" aria-label="任务步骤">
      <div className="step-navigator-header">
        <div className="section-title-wrap">
          <h2 className="section-title">任务流程</h2>
          <span className="section-badge">{steps.length} 步骤</span>
        </div>
        <div className="progress-text">
          <span className="progress-fraction">{doneCount}/{steps.length}</span>
          <span className="progress-percent">{progress}%</span>
        </div>
      </div>

      {/* 进度条 */}
      <div className="step-progress-bar" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
        <div className="step-progress-track">
          <div className="step-progress-fill" style={{ width: `${progress}%` }} />
        </div>
      </div>

      {/* 步骤列表 */}
      <div className="step-list">
        {steps.map((step, index) => {
          const cfg = statusConfig[step.status] || statusConfig.PENDING
          const isActive = step.stepId === activeStepCode || step.code === activeStepCode
          const isClickable = onStepClick != null

          return (
            <div
              key={step.stepId}
              className={`step-item ${isActive ? 'is-active' : ''} ${isClickable ? 'is-clickable' : ''}`}
              style={{
                '--step-color': cfg.color,
                '--step-bg': cfg.bg,
                '--step-border': cfg.border,
              } as React.CSSProperties}
              onClick={() => isClickable && handleClick(step.stepId)}
              role={isClickable ? 'button' : undefined}
              tabIndex={isClickable ? 0 : undefined}
            >
              <div className="step-marker">
                <span className="step-number">{index + 1}</span>
                <div className="step-line" aria-hidden="true">
                  {index < steps.length - 1 && <div className="step-line-track" />}
                </div>
              </div>

              <div className="step-content">
                <div className="step-title-row">
                  <strong className="step-name">{step.name}</strong>
                  <span className="step-code">{getStepMarker(step.code)}</span>
                </div>
                <div className="step-footer">
                  <span className="step-status-badge">
                    <span className="step-status-icon">{cfg.icon}</span>
                    {stepStatusLabels[step.status]}
                  </span>
                  {step.requiresConfirm && (
                    <span className="step-confirm-hint">需确认</span>
                  )}
                </div>
              </div>

              {isActive && (
                <div className="step-active-indicator" aria-hidden="true">
                  <div className="active-pulse" />
                </div>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
