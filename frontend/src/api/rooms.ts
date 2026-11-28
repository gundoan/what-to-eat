import { request } from './client'
import { panelBase } from '../devPanel'
import type { PhaseDurations, RoomStatus, RoomView, Vote } from '../types/room'

/** 여러 명 테스트 화면의 칸 안이면 /p/N/api(개발 중에만), 아니면 /api */
const API_BASE = `${panelBase}/api`

export function createRoom(durations: PhaseDurations): Promise<{ code: string }> {
  return request(`${API_BASE}/rooms`, { method: 'POST', body: JSON.stringify(durations) })
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

/**
 * 방장의 "시작"과 "다음 단계로"(랜덤 선택에서는 "다른 사람 뽑기").
 * from에는 화면에 보이던 단계를 보내서, 두 번 눌러도 한 단계만 넘어가게 한다.
 * 랜덤 선택은 다시 뽑아도 단계가 그대로라서, 화면에 보이던 뽑기 차례 번호(pickTurn)도 함께 보낸다.
 */
export function nextPhase(code: string, from: RoomStatus, pickTurn: number | null): Promise<RoomView> {
  // pickTurn이 null이면(랜덤 선택이 아니면) undefined로 바꿔서 본문에서 아예 뺀다.
  return request(`${roomPath(code)}/next-phase`, {
    method: 'POST',
    body: JSON.stringify({ from, pickTurn: pickTurn ?? undefined }),
  })
}

export function propose(code: string, menuName: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/proposal`, { method: 'PUT', body: JSON.stringify({ menuName }) })
}

/** "다 좋음". 이미 낸 메뉴가 있으면 취소된다. */
export function allGood(code: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/all-good`, { method: 'POST' })
}

/** 직접 합치기 요청: 내 메뉴를 targetMenuId 메뉴에 합치자고 요청한다. 받아들여지면 그 메뉴가 대표로 남는다. */
export function requestMerge(code: string, targetMenuId: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/merge-requests`, {
    method: 'POST',
    body: JSON.stringify({ targetMenuId }),
  })
}

/** 합치기 질문에 답한다(agree가 true면 합치기). 한 번 답하면 바꿀 수 없다. */
export function answerMerge(code: string, groupId: string, agree: boolean): Promise<RoomView> {
  return request(`${roomPath(code)}/me/merge-answers/${encodeURIComponent(groupId)}`, {
    method: 'PUT',
    body: JSON.stringify({ agree }),
  })
}

/** 표(1~2개)를 낸다. 한 번 내면 바꿀 수 없다. */
export function submitVotes(code: string, votes: Vote[]): Promise<RoomView> {
  return request(`${roomPath(code)}/me/ballot`, { method: 'PUT', body: JSON.stringify({ votes }) })
}

/** 투표의 "다 좋음"(투표하지 않음). 한 번 누르면 바꿀 수 없다. */
export function voteAllGood(code: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/ballot`, { method: 'PUT', body: JSON.stringify({ allGood: true }) })
}

/** "합치기 끝냄". 답하지 않은 질문은 "합치지 않기"로 처리된다. */
export function finishMerge(code: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/merge-done`, { method: 'POST' })
}

/** 랜덤 선택에서 뽑힌 사람이 메뉴를 정한다. 메뉴 목록에 없는 이름이어도 된다(1~30자). 정하면 바로 결정된다. */
export function decidePick(code: string, menuName: string): Promise<RoomView> {
  return request(`${roomPath(code)}/me/pick`, { method: 'POST', body: JSON.stringify({ menuName }) })
}

type RoomEventHandlers = {
  onView: (view: RoomView) => void
  onConnectionChange: (connected: boolean) => void
}

/**
 * 서버는 10초마다 하트비트(ping 이벤트)를 보낸다. 그 2.5배 동안 아무것도 받지 못하면 연결이 소리 없이 끊긴 것으로 본다.
 * 하트비트가 조금 늦게 와도 잘못 끊지 않으면서, 사람이 "멈췄나?" 하고 새로고침하기 전에 알아채는 시간이다.
 */
const SILENCE_LIMIT_MS = 25_000

/** 연결이 살아 있는지 살피는 간격. 끊긴 뒤 다시 연결을 시도하는 간격이기도 하다. */
const WATCH_INTERVAL_MS = 5_000

/**
 * SSE로 화면 정보(방 상태 + 내 정보)를 받는다. 돌려준 함수를 부르면 연결을 닫는다.
 * - 끊긴 것을 브라우저가 알면 EventSource가 알아서 다시 연결한다. 다시 연결되면 서버가 현재 화면 정보를 바로 보내준다.
 * - 중간의 프록시나 네트워크가 끊김을 전하지 않으면, 브라우저는 연결이 살아 있다고 믿고 기다리기만 한다.
 *   그래서 하트비트를 한동안 못 받으면 끊긴 것으로 보고 연결을 새로 만든다.
 * - 서버가 꺼져 있어서 오류 응답을 받으면 EventSource는 다시 시도하지 않는다(CLOSED). 그때도 연결을 새로 만든다.
 */
export function subscribeRoom(code: string, handlers: RoomEventHandlers): () => void {
  let lastHeardAt = Date.now()
  const heard = () => {
    lastHeardAt = Date.now()
  }

  function connect(): EventSource {
    const next = new EventSource(`${roomPath(code)}/events`)
    next.addEventListener('room', (event) => {
      heard()
      handlers.onView(JSON.parse((event as MessageEvent<string>).data) as RoomView)
    })
    next.addEventListener('ping', heard)
    next.onopen = () => {
      heard()
      handlers.onConnectionChange(true)
    }
    next.onerror = () => handlers.onConnectionChange(false)
    return next
  }

  let source = connect()
  const watcher = window.setInterval(() => {
    const silent = Date.now() - lastHeardAt > SILENCE_LIMIT_MS
    if (silent || source.readyState === EventSource.CLOSED) {
      source.close()
      handlers.onConnectionChange(false)
      lastHeardAt = Date.now()
      source = connect()
    }
  }, WATCH_INTERVAL_MS)

  return () => {
    window.clearInterval(watcher)
    source.close()
  }
}

function roomPath(code: string): string {
  return `${API_BASE}/rooms/${encodeURIComponent(code)}`
}
