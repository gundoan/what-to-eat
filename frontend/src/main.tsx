import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { panelBase } from './devPanel'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* 여러 명 테스트 화면의 칸 안이면 화면 주소가 /p/N으로 시작한다(개발 중에만). */}
    <BrowserRouter basename={panelBase || undefined}>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
