import type { Output } from '../types/task'

type OutputsPanelProps = {
  outputs: Output[]
}

const outputTypeIcons: Record<string, string> = {
  doc: '📄',
  document: '📄',
  slides: '📊',
  presentation: '📊',
  ppt: '📊',
  link: '🔗',
  file: '📁',
  unknown: '📎',
}

export function OutputsPanel({ outputs }: OutputsPanelProps) {
  if (!outputs.length) {
    return null
  }

  return (
    <section className="outputs-panel-section" aria-label="交付产物">
      <div className="outputs-panel-header">
        <div className="section-title-wrap">
          <h2 className="section-title">交付产物</h2>
          <span className="section-badge">{outputs.length} 个</span>
        </div>
      </div>

      <div className="outputs-list-compact">
        {outputs.map((output, index) => (
          <OutputItem key={`${output.type}-${output.title}-${index}`} output={output} />
        ))}
      </div>
    </section>
  )
}

type OutputItemProps = {
  output: Output
}

function OutputItem({ output }: OutputItemProps) {
  const typeIcon = outputTypeIcons[output.type.toLowerCase()] || outputTypeIcons.unknown

  return (
    <div className="output-item-compact">
      <span className="output-icon-compact">{typeIcon}</span>
      <span className="output-title-compact">{output.title}</span>
      {output.url && (
        <a
          className="output-link-compact"
          href={output.url}
          target="_blank"
          rel="noopener noreferrer"
          title={output.url}
        >
          打开 ↗
        </a>
      )}
    </div>
  )
}
