type MarkdownEditorProps = {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}

export function MarkdownEditor({ value, onChange, disabled }: MarkdownEditorProps) {
  return (
    <div className="markdown-editor">
      <textarea
        className="markdown-editor-textarea"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        placeholder="编辑 Markdown 内容..."
        spellCheck={false}
      />
    </div>
  )
}
