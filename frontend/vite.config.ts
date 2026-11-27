import type { IncomingMessage } from 'node:http'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const BACKEND = 'http://localhost:8080'

/** 개발용 여러 명 테스트 화면(dev-board.html)의 칸 주소 앞부분. 칸 N은 이 앱을 /p/N/... 주소로 띄운다. */
const PANEL_BASE = /^\/p\/\d+/

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 개발 중에는 /api 요청을 Spring Boot(8080)로 넘긴다. 브라우저에는 같은 주소로 보여서 CORS 설정이 필요 없다.
      '/api': BACKEND,
      // 여러 명 테스트 화면의 칸에서 오는 요청(/p/N/api/...). /p/N을 떼고 백엔드로 넘긴 뒤,
      // 백엔드가 준 쿠키의 경로(Path=/api/rooms/코드) 앞에 /p/N을 붙여서 돌려준다.
      // 브라우저는 쿠키를 경로마다 따로 보관하므로, 한 탭 안에서도 칸마다 다른 참여자가 된다. 백엔드는 그대로다.
      '^/p/\\d+/api/': {
        target: BACKEND,
        rewrite: (path) => path.replace(PANEL_BASE, ''),
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req) => {
            const base = panelBaseOf(req)
            const cookies = proxyRes.headers['set-cookie']
            if (base !== '' && cookies !== undefined) {
              proxyRes.headers['set-cookie'] = cookies.map((cookie) => cookie.replace(/;\s*Path=/i, `; Path=${base}`))
            }
          })
        },
      },
    },
  },
})

/**
 * 요청이 온 칸의 주소 앞부분("/p/3"). rewrite가 req.url에서 /p/N을 떼므로, 처음 들어온 주소(originalUrl)에서 찾는다.
 * originalUrl은 Vite가 쓰는 미들웨어 도구(connect)가 붙여 준다.
 */
function panelBaseOf(req: IncomingMessage): string {
  const url = (req as IncomingMessage & { originalUrl?: string }).originalUrl ?? req.url ?? ''
  return PANEL_BASE.exec(url)?.[0] ?? ''
}
