import { request } from './client'
import type { PhaseDurations, RoomState, RoomView } from '../types/room'

export function createRoom(durations: PhaseDurations): Promise<{ code: string }> {
  return request('/api/rooms', { method: 'POST', body: JSON.stringify(durations) })
}

// 같은 방에 참여 요청이 겹치면 하나로 합친다.
// 개발 모드(StrictMode)는 화면을 두 번 그려서, 합치지 않으면 쿠키를 받기 전에 두 번 참여해 참여자가 둘이 된다.
const pendingJoins = new Map<string, Promise<RoomView>>()

export function joinRoom(code: string): Promise<RoomView> {
  const pending = pendingJoins.get(code)
  if (pending) {
    return pending
  }
  const join = request<RoomView>(`/api/rooms/${encodeURIComponent(code)}/participants`, { method: 'POST' })
    .finally(() => pendingJoins.delete(code))
  pendingJoins.set(code, join)
  return join
}

type RoomEventHandlers = {
  onState: (state: RoomState) => void
  onConnectionChange: (connected: boolean) => void
}

/**
 * SSE로 방 상태를 받는다. 연결이 끊기면 브라우저(EventSource)가 알아서 다시 연결하고,
 * 다시 연결되면 서버가 현재 상태를 바로 보내준다. 돌려준 함수를 부르면 연결을 닫는다.
 */
export function subscribeRoom(code: string, handlers: RoomEventHandlers): () => void {
  const source = new EventSource(`/api/rooms/${encodeURIComponent(code)}/events`)
  source.addEventListener('room', (event) => {
    handlers.onState(JSON.parse((event as MessageEvent<string>).data) as RoomState)
  })
  source.onopen = () => handlers.onConnectionChange(true)
  source.onerror = () => handlers.onConnectionChange(false)
  return () => source.close()
}
