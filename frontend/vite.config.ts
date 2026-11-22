import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 개발 중에는 /api 요청을 Spring Boot(8080)로 넘긴다. 브라우저에는 같은 주소로 보여서 CORS 설정이 필요 없다.
      '/api': 'http://localhost:8080',
    },
  },
})
