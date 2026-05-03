const API_ROOT = 'https://agent-api.nexaid.top/api/v1'

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const url = createApiUrl(path)
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${url}`)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new Error('Response is not JSON')
  }

  return response.json() as Promise<T>
}

function createApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_ROOT}${normalizedPath}`
}

export type SSEEventHandler<T = unknown> = (data: T) => void

export type SSEErrorHandler = (error: Event) => void

export interface SSEConnection {
  close: () => void
  eventSource: EventSource
}

export function createSSEConnection<T = unknown>(
  path: string,
  handlers: {
    onSnapshot?: SSEEventHandler<T>
    onWorkspace?: SSEEventHandler<T>
    onError?: SSEErrorHandler
  }
): SSEConnection {
  const url = createApiUrl(path)
  const eventSource = new EventSource(url)

  if (handlers.onSnapshot) {
    eventSource.addEventListener('snapshot', (event) => {
      try {
        const data = JSON.parse(event.data) as T
        handlers.onSnapshot!(data)
      } catch (error) {
        console.error('Failed to parse snapshot event:', error)
      }
    })
  }

  if (handlers.onWorkspace) {
    eventSource.addEventListener('workspace', (event) => {
      try {
        const data = JSON.parse(event.data) as T
        handlers.onWorkspace!(data)
      } catch (error) {
        console.error('Failed to parse workspace event:', error)
      }
    })
  }

  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error)
    if (handlers.onError) {
      handlers.onError(error)
    }
  }

  return {
    close: () => {
      eventSource.close()
    },
    eventSource,
  }
}
