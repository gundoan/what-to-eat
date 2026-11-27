import { useCountdown } from '../hooks/useCountdown'
import type { RoomSnapshot } from '../hooks/useRoom'
import { phaseHasNoLimit } from '../utils/duration'

type Props = {
  room: RoomSnapshot
}

/**
 * 지금 단계의 남은 시간. 방장이 이 단계를 "제한 없음"으로 정했으면 "시간 제한 없음"을 보여준다.
 * 그 밖에 끝나는 시각이 없을 때(랜덤 선택에서 자동 다시 뽑기를 멈췄을 때)는 아무것도 보여주지 않는다.
 */
export function Countdown({ room }: Props) {
  const remaining = useCountdown(room.localDeadline, room.receivedAt)
  if (phaseHasNoLimit(room.status, room.phaseDurations)) {
    return <p className="countdown">시간 제한 없음</p>
  }
  if (remaining === null) return null

  const minutes = Math.floor(remaining / 60)
  const seconds = String(remaining % 60).padStart(2, '0')
  return (
    <p className="countdown" aria-live="polite">
      남은 시간 <strong>{`${minutes}:${seconds}`}</strong>
    </p>
  )
}
