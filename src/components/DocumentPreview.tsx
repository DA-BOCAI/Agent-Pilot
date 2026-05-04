import { useEffect } from 'react'
import React from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { DocumentBlock, DocumentPreviewData, DocumentSection } from '../types/preview'

type DocumentPreviewProps = {
  data: DocumentPreviewData
  fallbackTitle: string
  onOutlineChange?: (outline: Array<{ id: string; level: number; title: string }>) => void
}

function slugify(text: string): string {
  return text
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff-]/g, '')
}

const headingIdCounts: Record<string, number> = {}

function uniqueSlugify(text: string): string {
  const base = slugify(text) || 'heading'
  headingIdCounts[base] = (headingIdCounts[base] || 0) + 1
  const count = headingIdCounts[base]
  return count === 1 ? base : `${base}-${count}`
}

function childrenToText(children: React.ReactNode): string {
  if (typeof children === 'string') return children
  if (typeof children === 'number') return String(children)
  if (Array.isArray(children)) return children.map(childrenToText).join('')
  if (React.isValidElement(children)) {
    const childProps = children.props as Record<string, unknown>
    if (childProps.children) {
      return childrenToText(childProps.children as React.ReactNode)
    }
  }
  return ''
}

function extractOutlineFromMarkdown(md: string): Array<{ id: string; level: number; title: string }> {
  for (const key of Object.keys(headingIdCounts)) {
    delete headingIdCounts[key]
  }
  const outline: Array<{ id: string; level: number; title: string }> = []
  const regex = /^ {0,3}(#{1,6})\s+(.+)$/gm
  let match
  while ((match = regex.exec(md)) !== null) {
    const level = match[1].length
    const title = match[2].trim()
    outline.push({ id: uniqueSlugify(title), level, title })
  }
  return outline
}

function createHeading(tag: string) {
  return ({ children, ...props }: React.HTMLAttributes<HTMLHeadingElement> & { children?: React.ReactNode }) => {
    const id = uniqueSlugify(childrenToText(children))
    return React.createElement(tag, { ...props, id }, children)
  }
}

const headingComponents = {
  h1: createHeading('h1'),
  h2: createHeading('h2'),
  h3: createHeading('h3'),
  h4: createHeading('h4'),
  h5: createHeading('h5'),
  h6: createHeading('h6'),
}

export function DocumentPreview({ data, fallbackTitle, onOutlineChange }: DocumentPreviewProps) {
  const sections = data.sections ?? data.blocks ?? []

  useEffect(() => {
    if (!onOutlineChange) return
    for (const key of Object.keys(headingIdCounts)) {
      delete headingIdCounts[key]
    }
    if (data.outline?.length) {
      onOutlineChange(
        data.outline.map((item) => ({
          id: item.id ?? uniqueSlugify(item.title ?? ''),
          level: item.level ?? 1,
          title: item.title ?? '',
        }))
      )
    } else if (data.rawMarkdown) {
      onOutlineChange(extractOutlineFromMarkdown(data.rawMarkdown))
    }
  }, [data.outline, data.rawMarkdown, onOutlineChange])

  return (
    <div className="doc-preview artifact-doc">
      <header>
        <span>Document Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
        {data.summary ? <p>{data.summary}</p> : null}
      </header>
      {data.rawMarkdown ? (
        <div className="md-body">
          <ReactMarkdown remarkPlugins={[remarkGfm]} components={headingComponents}>
            {data.rawMarkdown}
          </ReactMarkdown>
        </div>
      ) : sections.length ? (
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
