import { useMemo } from 'react'
import type { TimelineEvent } from '../types/task'

type TimelinePanelProps = {
  timeline: TimelineEvent[]
  maxItems?: number
}

const levelConfig: Record<string, { icon: string; className: string }> = {
  info: { icon: 'ℹ️', className: 'timeline-level-info' },
  success: { icon: '✅', className: 'timeline-level-success' },
  warning: { icon: '⚠️', className: 'timeline-level-warning' },
  error: { icon: '❌', className: 'timeline-level-error' },
}

export function TimelinePanel({ timeline, maxItems = 50 }: TimelinePanelProps) {
  const displayEvents = useMemo(() => {
    const sorted = [...timeline].sort(
      (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    )
    return sorted.slice(0, maxItems)
  }, [timeline, maxItems])

  if (!timeline.length) {
    return null
  }

  return (
    <section className="timeline-panel-section reveal" aria-label="事件日志">
      <div className="timeline-panel-header">
        <div className="section-title-wrap">
          <h2 className="section-title">事件日志</h2>
          <span className="section-badge">{timeline.length} 条</span>
        </div>
      </div>

      <div className="timeline-panel-body">
        <div className="timeline-events">
          {displayEvents.map((event, index) => (
            <TimelineEventItem
              key={`${event.timestamp}-${event.type}-${index}`}
              event={event}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

type TimelineEventItemProps = {
  event: TimelineEvent
}

function TimelineEventItem({ event }: TimelineEventItemProps) {
  const config = levelConfig[event.level] || levelConfig.info
  const time = new Date(event.timestamp).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  return (
    <div className={`timeline-event-item ${config.className}`}>
      <div className="timeline-event-icon">{config.icon}</div>
      <div className="timeline-event-content">
        <div className="timeline-event-header">
          <span className="timeline-event-title">{event.title}</span>
          <span className="timeline-event-time">{time}</span>
        </div>
        {event.message && (
          <p className="timeline-event-message">{event.message}</p>
        )}
        {event.stepId && (
          <div className="timeline-event-meta">
            <span className="timeline-event-step">步骤: {event.stepId}</span>
          </div>
        )}
      </div>
    </div>
  )
}
