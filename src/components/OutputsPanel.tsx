import type { Output } from '../types/task'

type OutputsPanelProps = {
  outputs: Output[]
}

const outputTypeLabels: Record<string, string> = {
  doc: '文档',
  document: '文档',
  slides: '演示稿',
  presentation: '演示稿',
  ppt: '演示稿',
  link: '链接',
  delivery: '交付物',
  file: '文件',
  unknown: '产物',
}

const outputTypeIcons: Record<string, string> = {
  doc: '📄',
  document: '📄',
  slides: '📊',
  presentation: '📊',
  ppt: '📊',
  link: '🔗',
  delivery: '📦',
  file: '📁',
  unknown: '📎',
}

export function OutputsPanel({ outputs }: OutputsPanelProps) {
  if (!outputs.length) {
    return null
  }

  return (
    <section className="outputs-panel-section reveal" aria-label="交付产物">
      <div className="outputs-panel-header">
        <div className="section-title-wrap">
          <h2 className="section-title">交付产物</h2>
          <span className="section-badge">{outputs.length} 个</span>
        </div>
      </div>

      <div className="outputs-panel-body">
        <div className="outputs-list">
          {outputs.map((output, index) => (
            <OutputItem key={`${output.type}-${output.title}-${index}`} output={output} />
          ))}
        </div>
      </div>
    </section>
  )
}

type OutputItemProps = {
  output: Output
}

function OutputItem({ output }: OutputItemProps) {
  const typeLabel = outputTypeLabels[output.type.toLowerCase()] || output.type
  const typeIcon = outputTypeIcons[output.type.toLowerCase()] || outputTypeIcons.unknown

  return (
    <div className="output-item">
      <div className="output-icon">{typeIcon}</div>
      <div className="output-content">
        <div className="output-title-row">
          <span className="output-type">{typeLabel}</span>
          <strong className="output-title">{output.title}</strong>
        </div>
        {output.url && (
          <a
            className="output-link"
            href={output.url}
            target="_blank"
            rel="noopener noreferrer"
          >
            {output.url}
            <span className="output-link-icon">↗</span>
          </a>
        )}
        {output.token && (
          <div className="output-token">
            <span className="output-token-label">Token:</span>
            <code className="output-token-value">{output.token}</code>
          </div>
        )}
      </div>
    </div>
  )
}
