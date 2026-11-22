import { Route, Routes } from 'react-router'
import { HomePage } from './pages/HomePage'
import { RoomPage } from './pages/RoomPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/rooms/:code" element={<RoomPage />} />
    </Routes>
  )
}

export default App
