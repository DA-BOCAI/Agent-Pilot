import { SectionEditor } from './SectionEditor'
import type { DocumentPreviewData, DocumentSection } from '../types/preview'

type StructuredEditorProps = {
  data: DocumentPreviewData
  onChange?: (data: DocumentPreviewData) => void
  disabled?: boolean
}

export function StructuredEditor({ data, onChange, disabled }: StructuredEditorProps) {
  const sections = data.sections ?? data.blocks ?? []

  const handleFieldChange = (field: keyof DocumentPreviewData, value: string) => {
    onChange?.({ ...data, [field]: value })
  }

  const handleSectionChange = (index: number, newSection: DocumentSection) => {
    const newSections = [...sections]
    newSections[index] = newSection
    onChange?.({ ...data, sections: newSections })
  }

  return (
    <div className="structured-editor">
      <div className="editor-field">
        <label>文档标题</label>
        <input
          type="text"
          value={data.title ?? ''}
          onChange={(e) => handleFieldChange('title', e.target.value)}
          disabled={disabled}
          placeholder="输入文档标题..."
        />
      </div>

      <div className="editor-field">
        <label>文档摘要</label>
        <textarea
          value={data.summary ?? ''}
          onChange={(e) => handleFieldChange('summary', e.target.value)}
          disabled={disabled}
          placeholder="输入文档摘要..."
          rows={3}
        />
      </div>

      {sections.length > 0 && (
        <div className="editor-sections">
          <label>段落内容</label>
          {sections.map((section, index) => (
            <SectionEditor
              key={section.id ?? index}
              section={section}
              index={index}
              onChange={(newSection) => handleSectionChange(index, newSection)}
              disabled={disabled}
            />
          ))}
        </div>
      )}
    </div>
  )
}
