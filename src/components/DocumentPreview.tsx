import { useEffect, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeSlug from 'rehype-slug'
import rehypeHighlight from 'rehype-highlight'
import Slugger from 'github-slugger'
import { unified } from 'unified'
import remarkParse from 'remark-parse'
import { visit } from 'unist-util-visit'
import type { Root } from 'mdast'
import { MarkdownEditor } from './MarkdownEditor'
import { StructuredEditor } from './StructuredEditor'
import type { DocumentBlock, DocumentPreviewData, DocumentSection } from '../types/preview'

type OutlineItem = {
  id: string
  level: number
  title: string
}

type DocumentPreviewProps = {
  data: DocumentPreviewData
  fallbackTitle: string
  onOutlineChange?: (outline: OutlineItem[]) => void
  isEditing?: boolean
  onDataChange?: (data: DocumentPreviewData) => void
  disabled?: boolean
}

function extractOutlineFromAst(md: string): OutlineItem[] {
  const slugger = new Slugger()
  const tree = unified().use(remarkParse).use(remarkGfm).parse(md) as Root
  const outline: OutlineItem[] = []
  visit(tree, 'heading', (node) => {
    const title = headingText(node)
    const id = slugger.slug(title)
    outline.push({ id, level: node.depth, title })
  })
  return outline
}

function headingText(node: { children: Array<{ type: string; value?: string; children?: Array<{ type: string; value?: string }> }> }): string {
  return node.children
    .map((child) => {
      if ('value' in child && child.value) return child.value
      if ('children' in child && child.children) return headingText(child as unknown as typeof node)
      return ''
    })
    .join('')
}

export function DocumentPreview({ 
  data, 
  fallbackTitle, 
  onOutlineChange,
  isEditing = false,
  onDataChange,
  disabled = false
}: DocumentPreviewProps) {
  const sections = data.sections ?? data.blocks ?? []

  const computedOutline = useMemo<OutlineItem[]>(() => {
    if (data.rawMarkdown) {
      return extractOutlineFromAst(data.rawMarkdown)
    }
    if (data.outline?.length) {
      const slugger = new Slugger()
      return data.outline.map((item) => ({
        id: item.id ?? slugger.slug(item.title ?? ''),
        level: item.level ?? 1,
        title: item.title ?? '',
      }))
    }
    return []
  }, [data.rawMarkdown, data.outline])

  useEffect(() => {
    if (onOutlineChange && computedOutline.length > 0) {
      onOutlineChange(computedOutline)
    }
  }, [computedOutline, onOutlineChange])

  if (isEditing) {
    if (data.rawMarkdown) {
      return (
        <MarkdownEditor
          value={data.rawMarkdown}
          onChange={(newMarkdown) => {
            onDataChange?.({ ...data, rawMarkdown: newMarkdown })
          }}
          disabled={disabled}
        />
      )
    }

    if (data.sections || data.blocks) {
      return (
        <StructuredEditor
          data={data}
          onChange={onDataChange}
          disabled={disabled}
        />
      )
    }
  }

  return (
    <div className="doc-preview artifact-doc">
      <header>
        <span>Document Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
        {data.summary ? <p>{data.summary}</p> : null}
      </header>
      {data.rawMarkdown ? (
        <div className="md-body">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[rehypeSlug, rehypeHighlight]}
          >
            {data.rawMarkdown}
          </ReactMarkdown>
        </div>
      ) : sections.length ? (
        <div className="doc-section-list">
          {sections.map((section, index) => (
            <section key={section.id ?? `${section.title ?? 'section'}-${index}`}>
              <h5 id={section.id ?? `section-${index}`}>{section.title ?? `段落 ${index + 1}`}</h5>
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
