import { useState } from 'react'
import type { Vote, VotePoint } from '../types/room'

/** 한 사람이 낼 수 있는 표의 수 */
export const MAX_VOTES = 2

/**
 * 제출하기 전에 고르는 표. 메뉴마다 +1이나 -1 중 하나만 고를 수 있고, 모두 합쳐 2표까지다.
 * 서버도 같은 규칙으로 다시 검사한다(화면만 믿지 않는다).
 */
export function useBallotDraft() {
  const [points, setPoints] = useState<Record<string, VotePoint>>({})

  /** 같은 버튼을 다시 누르면 취소, 반대 버튼을 누르면 바꾸기, 새 메뉴는 남은 표가 있을 때만 고른다. */
  function toggle(menuId: string, point: VotePoint) {
    setPoints((current) => {
      if (current[menuId] === point) {
        const next = { ...current }
        delete next[menuId]
        return next
      }
      if (menuId in current || Object.keys(current).length < MAX_VOTES) {
        return { ...current, [menuId]: point }
      }
      return current
    })
  }

  const votes: Vote[] = Object.entries(points).map(([menuId, point]) => ({ menuId, point }))
  return { points, votes, remaining: MAX_VOTES - votes.length, toggle }
}
