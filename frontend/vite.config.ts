import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// nginx/default.conf 의 경로 라우팅을 로컬 dev 서버에서 그대로 흉내낸다.
// (도커 없이 각 backend 서비스를 gradlew bootRun 등으로 개별 기동했을 때 기준.
//  docker compose 스택 전체를 띄웠다면 nginx:80 을 직접 쓰는 편이 낫다.)
// 서비스 하나가 추가/이동되면 nginx/default.conf 와 함께 고칠 것 — 안 그러면
// "로컬 dev 서버에선 되는데 docker/prod 에선 404" 가 재현된다.
const backendProxy = {
  '/api/ai': { target: 'http://localhost:8084', changeOrigin: true },
  '/api/orders': { target: 'http://localhost:8082', changeOrigin: true },
  '/api/members': { target: 'http://localhost:8083', changeOrigin: true },
  '/api/auth': { target: 'http://localhost:8083', changeOrigin: true },
  '/api/books': { target: 'http://localhost:8081', changeOrigin: true },
  '/api/reviews': { target: 'http://localhost:8081', changeOrigin: true },
  '/api/wishlist': { target: 'http://localhost:8081', changeOrigin: true },
  '/api/recent-books': { target: 'http://localhost:8081', changeOrigin: true },
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: backendProxy
  }
})
