// 개발용 여러 명 테스트 화면(dev-board.html)의 시작점. 앱의 시작점(src/main.tsx)과 따로 있어서 배포용 빌드에 들어가지 않는다.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { DevBoard } from './DevBoard'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DevBoard />
  </StrictMode>,
)
