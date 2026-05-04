import React from 'react'

type OutlineItem = {
  id: string
  level: number
  title: string
}

type OutlinePanelProps = {
  items: OutlineItem[]
  activeId?: string
  onItemClick?: (id: string) => void
  collapsed?: boolean
  onToggleCollapse?: () => void
}

const OutlinePanel: React.FC<OutlinePanelProps> = ({
  items,
  activeId,
  onItemClick,
  collapsed = false,
  onToggleCollapse,
}) => {
  if (!items || items.length === 0) {
    return null
  }

  return (
    <div className="outline-panel">
      <div className="outline-panel-header">
        <span className="outline-panel-title">大纲</span>
        <button className="outline-panel-toggle" onClick={onToggleCollapse}>
          {collapsed ? '▸' : '▾'}
        </button>
      </div>
      {!collapsed && (
        <div className="outline-panel-list">
          {items.map((item) => (
            <div
              key={item.id}
              className={`outline-item${activeId === item.id ? ' is-active' : ''}`}
              style={{ paddingLeft: `${(item.level - 1) * 12}px` }}
              onClick={() => onItemClick?.(item.id)}
            >
              {item.title}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default React.memo(OutlinePanel)
