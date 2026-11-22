import { useEffect, useState } from 'react'
import { joinRoom, subscribeRoom } from '../api/rooms'
import { ApiError } from '../api/client'
import type { Me, RoomState } from '../types/room'

export type RoomError = 'NOT_FOUND' | 'FULL' | 'UNKNOWN'

type RoomScreen =
  | { phase: 'loading' }
  | { phase: 'error'; error: RoomError }
  | { phase: 'ready'; room: RoomState; me: Me; connected: boolean }

/**
 * 방 화면의 로직.
 * 1) 링크로 들어오면 참여한다(이미 참여한 브라우저면 같은 참여자로 돌아온다).
 * 2) SSE로 연결해서 방 상태가 바뀔 때마다 화면을 갱신한다.
 */
export function useRoom(code: string): RoomScreen {
  const [screen, setScreen] = useState<RoomScreen>({ phase: 'loading' })

  useEffect(() => {
    let closed = false
    let unsubscribe: (() => void) | null = null

    joinRoom(code)
      .then((view) => {
        if (closed) return
        setScreen({ phase: 'ready', room: view.room, me: view.me, connected: true })
        unsubscribe = subscribeRoom(code, {
          onState: (room) => setScreen((current) => (current.phase === 'ready' ? { ...current, room } : current)),
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

  return screen
}

function toRoomError(e: unknown): RoomError {
  if (e instanceof ApiError && e.code === 'ROOM_NOT_FOUND') return 'NOT_FOUND'
  if (e instanceof ApiError && e.code === 'ROOM_FULL') return 'FULL'
  return 'UNKNOWN'
}
