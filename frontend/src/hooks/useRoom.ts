import { useEffect, useState } from 'react'
import {
  allGood,
  answerMerge,
  finishMerge,
  joinRoom,
  nextPhase,
  propose,
  requestMerge,
  submitVotes,
  subscribeRoom,
  voteAllGood,
} from '../api/rooms'
import { ApiError } from '../api/client'
import type { Me, RoomState, RoomView, Vote } from '../types/room'

export type RoomError = 'NOT_FOUND' | 'FULL' | 'ENDED' | 'UNKNOWN'

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
  /** 내 메뉴를 다른 메뉴에 합치자고 요청한다 */
  requestMerge: (targetMenuId: string) => void
  /** 합치기 질문에 답한다. agree가 true면 합치기 */
  answerMerge: (groupId: string, agree: boolean) => void
  /** "합치기 끝냄" */
  finishMerge: () => void
  /** 표(1~2개)를 낸다 */
  submitVotes: (votes: Vote[]) => void
  /** 투표의 "다 좋음" */
  voteAllGood: () => void
}

/**
 * 방 화면의 로직.
 * 1) 링크로 들어오면 참여한다(이미 참여한 브라우저면 같은 참여자로 돌아온다).
 * 2) SSE로 연결해서 방 상태나 내 정보가 바뀔 때마다 화면을 갱신한다.
 * 3) 시작, 제안, "다 좋음", 합치기 답 같은 행동을 서버에 보낸다.
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
          // SSE에는 방 상태와 내 정보가 함께 온다. 다른 사람의 행동으로 내 정보가 바뀌어도(내 메뉴가 합쳐짐 등) 바로 보인다.
          // 다시 연결될 때도 서버가 지금 화면 정보를 먼저 보내주므로, 따로 다시 불러올 필요가 없다.
          onView: (latest) => setScreen(withView(latest)),
          onConnectionChange: (connected) =>
            setScreen((current) => (current.phase === 'ready' ? { ...current, connected } : current)),
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
    requestMerge: (targetMenuId) => void run(() => requestMerge(code, targetMenuId)),
    answerMerge: (groupId, agree) => void run(() => answerMerge(code, groupId, agree)),
    finishMerge: () => void run(() => finishMerge(code)),
    submitVotes: (votes) => void run(() => submitVotes(code, votes)),
    voteAllGood: () => void run(() => voteAllGood(code)),
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

function toRoomError(e: unknown): RoomError {
  if (e instanceof ApiError && e.code === 'ROOM_NOT_FOUND') return 'NOT_FOUND'
  if (e instanceof ApiError && e.code === 'ROOM_FULL') return 'FULL'
  if (e instanceof ApiError && e.code === 'VOTING_ENDED') return 'ENDED'
  return 'UNKNOWN'
}

/**
 * 서버가 보낸 화면 정보(RoomView)를 반영하는 상태 변경 함수를 만든다.
 * HTTP 응답과 SSE 이벤트는 도착 순서가 바뀔 수 있어서, 서버 시각이 더 늦은(더 최신인) 정보만 반영한다.
 * 방 상태와 내 정보는 같은 순간에 만들어진 것이라 함께 반영한다.
 */
function withView(view: RoomView): (current: RoomScreen) => RoomScreen {
  return (current) => {
    if (current.phase !== 'ready') {
      return { phase: 'ready', room: toSnapshot(view.room), me: view.me, connected: true }
    }
    if (Date.parse(view.room.serverTime) < Date.parse(current.room.serverTime)) {
      return current
    }
    return { ...current, room: toSnapshot(view.room), me: view.me }
  }
}
