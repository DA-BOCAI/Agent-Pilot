import { useCallback } from 'react'
import type { PlanStep, Step } from '../types/task'

type StepNavigatorProps = {
  steps: PlanStep[]
  workspaceSteps?: Step[]
  activeStepCode: string | null
  onStepClick?: (stepId: string) => void
}

const statusConfig: Record<string, { icon: string; color: string; bg: string; border: string }> = {
  PENDING:     { icon: '○', color: 'var(--muted)',        bg: 'var(--paper)',        border: 'var(--line)' },
  RUNNING:     { icon: '◐', color: 'var(--primary)',      bg: 'var(--primary-soft)', border: 'var(--primary-border)' },
  WAIT_CONFIRM:{ icon: '!', color: 'var(--warning)',      bg: 'var(--warning-soft)', border: 'var(--warning-border)' },
  APPROVED:    { icon: '✓', color: 'var(--success)',      bg: 'var(--success-soft)', border: 'var(--success-border)' },
  DONE:        { icon: '✓', color: 'var(--success)',      bg: 'var(--success-soft)', border: 'var(--success-border)' },
  SKIPPED:     { icon: '—', color: 'var(--danger)',       bg: 'var(--danger-soft)',  border: 'var(--danger-border)' },
  FAILED:      { icon: '✕', color: 'var(--danger)',       bg: 'var(--danger-soft)',  border: 'var(--danger-border)' },
}

export function StepNavigator({ steps, workspaceSteps, activeStepCode, onStepClick }: StepNavigatorProps) {
  const handleClick = useCallback((stepId: string) => {
    onStepClick?.(stepId)
  }, [onStepClick])

  const displaySteps = workspaceSteps && workspaceSteps.length > 0
    ? workspaceSteps.map((step, index) => ({
        stepId: step.id,
        code: `STEP_${index + 1}`,
        name: step.title,
        status: step.status,
        requiresConfirm: step.requiresConfirm,
        displayStatus: step.displayStatus,
        description: step.description,
        artifactType: step.artifactType,
      }))
    : steps

  if (!displaySteps.length) {
    return (
      <section className="step-navigator-section" aria-label="任务步骤">
        <div className="step-navigator-header">
          <div className="section-title-wrap">
            <h2 className="section-title">任务流程</h2>
            <span className="section-badge">0 步骤</span>
          </div>
        </div>
        <div className="step-navigator-empty">
          <div className="empty-illustration">📋</div>
          <p>任务创建后将在这里展示完整的规划步骤</p>
        </div>
      </section>
    )
  }

  return (
    <section className="step-navigator-section" aria-label="任务步骤">
      <div className="step-navigator-header">
        <div className="section-title-wrap">
          <h2 className="section-title">任务流程</h2>
          <span className="section-badge">{displaySteps.length} 步骤</span>
        </div>
      </div>

      <div className="step-list-compact">
        {displaySteps.map((step, index) => {
          const cfg = statusConfig[step.status] || statusConfig.PENDING
          const isActive = step.stepId === activeStepCode || step.code === activeStepCode
          const isClickable = onStepClick != null
          const stepName = step.name

          return (
            <div
              key={step.stepId}
              className={`step-item-compact ${isActive ? 'is-active' : ''} ${isClickable ? 'is-clickable' : ''}`}
              style={{
                '--step-color': cfg.color,
                '--step-bg': cfg.bg,
                '--step-border': cfg.border,
              } as React.CSSProperties}
              onClick={() => isClickable && handleClick(step.stepId)}
              role={isClickable ? 'button' : undefined}
              tabIndex={isClickable ? 0 : undefined}
            >
              <span className="step-number-compact">{index + 1}</span>
              <span className="step-name-compact">{stepName}</span>
              <span className="step-status-icon-compact">{cfg.icon}</span>
              {step.requiresConfirm && (
                <span className="step-confirm-hint-compact">需确认</span>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
