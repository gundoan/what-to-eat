import { request } from './client'
import type { PhaseDurations, RoomStatus, RoomView } from '../types/room'

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
  const join = request<RoomView>(`${roomPath(code)}/participants`, { method: 'POST' }).finally(() =>
    pendingJoins.delete(code),
  )
  pendingJoins.set(code, join)
  return join
}

export function getRoom(code: string): Promise<RoomView> {
  return request(roomPath(code))
}

/** 방장의 "시작"과 "다음 단계로". from에는 화면에 보이던 단계를 보내서, 두 번 눌러도 한 단계만 넘어가게 한다. */
export function nextPhase(code: string, from: RoomStatus): Promise<RoomView> {
  return request(`${roomPath(code)}/next-phase`, { method: 'POST', body: JSON.stringify({ from }) })
}

export function propose(code: string, menuName: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/proposal`, { method: 'PUT', body: JSON.stringify({ menuName }) })
}

/** "다 좋음". 이미 낸 메뉴가 있으면 취소된다. */
export function allGood(code: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/all-good`, { method: 'POST' })
}

/** 합치기 질문에 답한다(agree가 true면 합치기). 한 번 답하면 바꿀 수 없다. */
export function answerMerge(code: string, groupId: string, agree: boolean): Promise<RoomView> {
  return request(`${roomPath(code)}/me/merge-answers/${encodeURIComponent(groupId)}`, {
    method: 'PUT',
    body: JSON.stringify({ agree }),
  })
}

/** "합치기 끝냄". 답하지 않은 질문은 "합치지 않기"로 처리된다. */
export function finishMerge(code: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/merge-done`, { method: 'POST' })
}

type RoomEventHandlers = {
  onView: (view: RoomView) => void
  onConnectionChange: (connected: boolean) => void
}

/**
 * SSE로 화면 정보(방 상태 + 내 정보)를 받는다. 연결이 끊기면 브라우저(EventSource)가 알아서 다시 연결하고,
 * 다시 연결되면 서버가 현재 화면 정보를 바로 보내준다. 돌려준 함수를 부르면 연결을 닫는다.
 */
export function subscribeRoom(code: string, handlers: RoomEventHandlers): () => void {
  const source = new EventSource(`${roomPath(code)}/events`)
  source.addEventListener('room', (event) => {
    handlers.onView(JSON.parse((event as MessageEvent<string>).data) as RoomView)
  })
  source.onopen = () => handlers.onConnectionChange(true)
  source.onerror = () => handlers.onConnectionChange(false)
  return () => source.close()
}

function roomPath(code: string): string {
  return `/api/rooms/${encodeURIComponent(code)}`
}
