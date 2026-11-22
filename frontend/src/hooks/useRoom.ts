import { useEffect, useState } from 'react'
import { allGood, getRoom, joinRoom, nextPhase, propose, subscribeRoom } from '../api/rooms'
import { ApiError } from '../api/client'
import type { Me, RoomState, RoomView } from '../types/room'

export type RoomError = 'NOT_FOUND' | 'FULL' | 'UNKNOWN'

/** 서버가 보낸 방 상태에, 이 브라우저의 시계 기준으로 바꾼 "단계가 끝나는 시각"을 더한 것. */
export type RoomSnapshot = RoomState & {
  /** 단계가 끝나는 시각(이 브라우저의 Date.now() 기준, 밀리초). 시간 제한이 없으면 null */
  localDeadline: number | null
  /** 이 상태를 받은 시각(이 브라우저의 Date.now() 기준) */
  receivedAt: number
}

type RoomScreen =
  | { phase: 'loading' }
  | { phase: 'error'; error: RoomError }
  | { phase: 'ready'; room: RoomSnapshot; me: Me; connected: boolean }

export type RoomActions = {
  /** 방장의 "시작"(대기 중) 또는 "다음 단계로" */
  nextPhase: () => void
  propose: (menuName: string) => void
  allGood: () => void
}

/**
 * 방 화면의 로직.
 * 1) 링크로 들어오면 참여한다(이미 참여한 브라우저면 같은 참여자로 돌아온다).
 * 2) SSE로 연결해서 방 상태가 바뀔 때마다 화면을 갱신한다.
 * 3) 시작, 제안, "다 좋음" 같은 행동을 서버에 보낸다.
 */
export function useRoom(code: string) {
  const [screen, setScreen] = useState<RoomScreen>({ phase: 'loading' })
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let closed = false
    let unsubscribe: (() => void) | null = null

    joinRoom(code)
      .then((view) => {
        if (closed) return
        setScreen(withView(view))
        unsubscribe = subscribeRoom(code, {
          onState: (state) =>
            setScreen((current) => (current.phase === 'ready' ? { ...current, room: newer(current.room, state) } : current)),
          onConnectionChange: (connected) => {
            setScreen((current) => (current.phase === 'ready' ? { ...current, connected } : current))
            // 다시 연결되면 내 정보(me)도 다시 받는다. 다른 탭에서 바꿨을 수 있다.
            if (connected) {
              getRoom(code)
                .then((latest) => {
                  if (!closed) setScreen(withView(latest))
                })
                .catch(() => {})
            }
          },
        })
      })
      .catch((e: unknown) => {
        if (!closed) setScreen({ phase: 'error', error: toRoomError(e) })
      })

    return () => {
      closed = true
      unsubscribe?.()
    }
  }, [code])

  async function run(action: () => Promise<RoomView>) {
    setActionError(null)
    try {
      setScreen(withView(await action()))
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.')
    }
  }

  const actions: RoomActions = {
    nextPhase: () => {
      if (screen.phase === 'ready') void run(() => nextPhase(code, screen.room.status))
    },
    propose: (menuName) => void run(() => propose(code, menuName)),
    allGood: () => void run(() => allGood(code)),
  }

  return { screen, actions, actionError }
}

/**
 * 서버 시각과 이 브라우저 시각의 차이를 이용해, 끝나는 시각을 이 브라우저 시계 기준으로 바꾼다.
 * 그래서 휴대폰 시계가 틀려도 남은 시간은 맞게 보인다. 렌더링 중이 아니라 상태를 받는 순간에만 부른다.
 */
function toSnapshot(state: RoomState): RoomSnapshot {
  const now = Date.now()
  const offset = Date.parse(state.serverTime) - now
  const localDeadline = state.phaseEndsAt === null ? null : Date.parse(state.phaseEndsAt) - offset
  return { ...state, localDeadline, receivedAt: now }
}

/**
 * HTTP 응답과 SSE 이벤트는 도착 순서가 바뀔 수 있다. 서버 시각이 더 늦은(더 최신인) 상태만 반영해서
 * 오래된 상태가 화면을 덮어쓰지 않게 한다.
 */
function newer(current: RoomSnapshot, next: RoomState): RoomSnapshot {
  return Date.parse(next.serverTime) >= Date.parse(current.serverTime) ? toSnapshot(next) : current
}

function toRoomError(e: unknown): RoomError {
  if (e instanceof ApiError && e.code === 'ROOM_NOT_FOUND') return 'NOT_FOUND'
  if (e instanceof ApiError && e.code === 'ROOM_FULL') return 'FULL'
  return 'UNKNOWN'
}

/** 서버가 돌려준 방 화면 정보(RoomView)를 반영하는 상태 변경 함수를 만든다. */
function withView(view: RoomView): (current: RoomScreen) => RoomScreen {
  return (current) => {
    if (current.phase !== 'ready') {
      return { phase: 'ready', room: toSnapshot(view.room), me: view.me, connected: true }
    }
    return { ...current, room: newer(current.room, view.room), me: view.me }
  }
}
