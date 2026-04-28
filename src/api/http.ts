const API_ROOT = createApiRoot()

// 所有业务接口都走这一层，避免组件或 hook 里散落 base URL、headers 和 JSON 校验逻辑。
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

function createApiRoot() {
  const configuredBase = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/$/, '')
  if (!configuredBase) return '/api/v1'
  // cpolar 地址每天会变，源码只接受 origin；是否已经带 /api/v1 都在这里兼容。
  return configuredBase.endsWith('/api/v1') ? configuredBase : `${configuredBase}/api/v1`
}
