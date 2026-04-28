import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = normalizeApiTarget(env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080')

  return {
    plugins: [react()],
    server: {
      proxy: {
        // 本地开发没配 VITE_API_BASE_URL 时，避免 /api/v1 请求打到 Vite 自己导致 404。
        '/api/v1': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
  }
})

function normalizeApiTarget(baseUrl: string) {
  return baseUrl.trim().replace(/\/api\/v1\/?$/, '').replace(/\/$/, '')
}
