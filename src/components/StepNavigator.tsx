import { useCallback, useState, useMemo } from 'react'
import type { PlanStep, Step } from '../types/task'

type StepNavigatorProps = {
  steps: PlanStep[]
  workspaceSteps?: Step[]
  activeStepCode: string | null
  onStepClick?: (stepId: string) => void
  isMobile?: boolean
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

export function StepNavigator({ steps, workspaceSteps, activeStepCode, onStepClick, isMobile = false }: StepNavigatorProps) {
  const [isExpanded, setIsExpanded] = useState(false)

  const handleClick = useCallback((stepId: string) => {
    onStepClick?.(stepId)
  }, [onStepClick])

  const displaySteps = useMemo(() => {
    if (workspaceSteps && workspaceSteps.length > 0) {
      return workspaceSteps.map((step, index) => ({
        stepId: step.stepId,
        code: `STEP_${index + 1}`,
        name: step.name,
        status: step.status,
        requiresConfirm: step.requiresConfirm,
        displayStatus: step.displayStatus,
        description: step.action,
        artifactType: undefined,
      }))
    }
    return steps
  }, [workspaceSteps, steps])

  const currentStepIndex = useMemo(() => {
    return displaySteps.findIndex((step) => step.stepId === activeStepCode || step.code === activeStepCode)
  }, [displaySteps, activeStepCode])

  const currentStep = displaySteps[currentStepIndex] || displaySteps[0]
  const currentStepCfg = currentStep ? (statusConfig[currentStep.status] || statusConfig.PENDING) : null

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

  // 移动端紧凑模式
  if (isMobile) {
    return (
      <section className="step-navigator-section step-navigator-mobile" aria-label="任务步骤">
        {/* 紧凑进度条 */}
        <div
          className="step-navigator-compact-bar"
          onClick={() => setIsExpanded(!isExpanded)}
          role="button"
          tabIndex={0}
        >
          <div className="step-compact-progress">
            <div
              className="step-compact-progress-fill"
              style={{
                width: `${((currentStepIndex + 1) / displaySteps.length) * 100}%`,
                background: currentStepCfg?.color || 'var(--primary)',
              }}
            />
          </div>
          <div className="step-compact-info">
            <span className="step-compact-count">
              {currentStepIndex + 1}/{displaySteps.length}
            </span>
            <span className="step-compact-name" style={{ color: currentStepCfg?.color || 'var(--text)' }}>
              {currentStep?.name || '准备中'}
            </span>
            <span className="step-compact-icon">{isExpanded ? '▲' : '▼'}</span>
          </div>
        </div>

        {/* 展开的步骤列表抽屉 */}
        {isExpanded && (
          <div className="step-navigator-drawer">
            <div className="step-navigator-drawer-header">
              <span>任务步骤</span>
              <button
                className="step-drawer-close"
                onClick={(e) => {
                  e.stopPropagation()
                  setIsExpanded(false)
                }}
              >
                ✕
              </button>
            </div>
            <div className="step-list-compact step-list-mobile">
              {displaySteps.map((step, index) => {
                const cfg = statusConfig[step.status] || statusConfig.PENDING
                const isActive = step.stepId === activeStepCode || step.code === activeStepCode
                const isClickable = onStepClick != null

                return (
                  <div
                    key={step.stepId}
                    className={`step-item-compact ${isActive ? 'is-active' : ''} ${isClickable ? 'is-clickable' : ''}`}
                    style={{
                      '--step-color': cfg.color,
                      '--step-bg': cfg.bg,
                      '--step-border': cfg.border,
                    } as React.CSSProperties}
                    onClick={() => {
                      if (isClickable) {
                        handleClick(step.stepId)
                        setIsExpanded(false)
                      }
                    }}
                    role={isClickable ? 'button' : undefined}
                    tabIndex={isClickable ? 0 : undefined}
                  >
                    <span className="step-number-compact">{index + 1}</span>
                    <span className="step-name-compact">{step.name}</span>
                    <span className="step-status-icon-compact">{cfg.icon}</span>
                    {step.requiresConfirm && (
                      <span className="step-confirm-hint-compact">需确认</span>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </section>
    )
  }

  // 桌面端完整模式
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
