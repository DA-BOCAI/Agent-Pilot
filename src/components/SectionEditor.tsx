import type { DocumentSection } from '../types/preview'

type SectionEditorProps = {
  section: DocumentSection
  index: number
  onChange: (section: DocumentSection) => void
  disabled?: boolean
}

export function SectionEditor({ section, index, onChange, disabled }: SectionEditorProps) {
  const content = section.content ?? section.text ?? ''
  const items = section.items ?? []
  const hasItems = items.length > 0

  return (
    <div className="section-editor">
      <div className="editor-field">
        <label>段落 {index + 1} 标题</label>
        <input
          type="text"
          value={section.title ?? ''}
          onChange={(e) => onChange({ ...section, title: e.target.value })}
          disabled={disabled}
          placeholder="输入段落标题..."
        />
      </div>

      {hasItems ? (
        <div className="editor-field">
          <label>列表项（每行一项）</label>
          <textarea
            value={items.join('\n')}
            onChange={(e) => {
              const newItems = e.target.value.split('\n').filter(Boolean)
              onChange({ ...section, items: newItems })
            }}
            disabled={disabled}
            placeholder="输入列表项，每行一项..."
            rows={5}
          />
        </div>
      ) : (
        <div className="editor-field">
          <label>段落内容</label>
          <textarea
            value={content}
            onChange={(e) => onChange({ ...section, content: e.target.value })}
            disabled={disabled}
            placeholder="输入段落内容..."
            rows={5}
          />
        </div>
      )}
    </div>
  )
}
