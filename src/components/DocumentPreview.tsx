import type { DocumentBlock, DocumentPreviewData, DocumentSection } from '../types/preview'

type DocumentPreviewProps = {
  data: DocumentPreviewData
  fallbackTitle: string
}

export function DocumentPreview({ data, fallbackTitle }: DocumentPreviewProps) {
  // 兼容后端 sections.blocks 与早期 mock blocks，两种结构都走同一套文档预览。
  const sections = data.sections ?? data.blocks ?? []

  return (
    <div className="doc-preview artifact-doc">
      <header>
        <span>Document Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
        {data.summary ? <p>{data.summary}</p> : null}
      </header>
      {sections.length ? (
        <div className="doc-section-list">
          {sections.map((section, index) => (
            <section key={section.id ?? `${section.title ?? 'section'}-${index}`}>
              <h5>{section.title ?? `段落 ${index + 1}`}</h5>
              <PreviewContent section={section} />
            </section>
          ))}
        </div>
      ) : (
        <pre>{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  )
}

function PreviewContent({ section }: { section: DocumentSection }) {
  // 新接口返回块结构；旧 mock 可能直接给 content/text/items。
  if (section.blocks?.length) {
    return (
      <>
        {section.blocks.map((block, index) => (
          <DocumentBlockPreview block={block} key={block.id ?? `${block.type ?? 'block'}-${index}`} />
        ))}
      </>
    )
  }

  const content = section.content ?? section.text

  if (Array.isArray(section.items)) {
    return (
      <ul>
        {section.items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    )
  }

  if (Array.isArray(content)) {
    return (
      <ul>
        {content.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    )
  }

  return <p>{content ?? '暂无内容'}</p>
}

function DocumentBlockPreview({ block }: { block: DocumentBlock }) {
  if (block.items?.length) {
    return (
      <ul>
        {block.items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    )
  }

  return <p>{block.text ?? '暂无内容'}</p>
}
