import type { SlidePreviewData } from '../types/preview'

type SlidesPreviewProps = {
  data: SlidePreviewData
  fallbackTitle: string
}

export function SlidesPreview({ data, fallbackTitle }: SlidesPreviewProps) {
  // slides 为空时展示原始 JSON，避免后端新增字段时页面直接空白。
  const slides = data.slides ?? []

  return (
    <div className="slides-preview artifact-slides">
      <header>
        <span>Slides Preview</span>
        <h4>{data.title ?? fallbackTitle}</h4>
      </header>
      {slides.length ? (
        <div className="slide-grid">
          {slides.map((slide, index) => (
            <article className="slide-card" key={slide.id ?? `${slide.title ?? 'slide'}-${index}`}>
              <span>{String(slide.slideNo ?? index + 1).padStart(2, '0')}</span>
              <h5>{slide.title ?? `Slide ${index + 1}`}</h5>
              {slide.subtitle ? <p>{slide.subtitle}</p> : null}
              {slide.bullets?.length ? (
                <ul>
                  {slide.bullets.map((bullet, bulletIndex) => (
                    <li key={`${bullet}-${bulletIndex}`}>{bullet}</li>
                  ))}
                </ul>
              ) : null}
              {slide.notes ? <small>{slide.notes}</small> : null}
            </article>
          ))}
        </div>
      ) : (
        <pre>{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  )
}
