import type { Me, PhaseDurations } from '../../types/room'
import { formatDuration } from '../../utils/duration'

type Props = {
  me: Me
  phaseDurations: PhaseDurations
  onStart: () => void
}

export function WaitingPhase({ me, phaseDurations, onStart }: Props) {
  return (
    <section>
      {me.host ? (
        <button type="button" onClick={onStart}>
          시작
        </button>
      ) : (
        <p>방장이 시작하면 메뉴 제안이 시작돼요.</p>
      )}

      <h2>단계별 시간</h2>
      <ul>
        <li>제안: {formatDuration(phaseDurations.proposalSeconds)}</li>
        <li>합치기: {formatDuration(phaseDurations.mergeSeconds)}</li>
        <li>투표: {formatDuration(phaseDurations.voteSeconds)}</li>
        <li>랜덤 선택: {formatDuration(phaseDurations.randomPickSeconds)}</li>
      </ul>
    </section>
  )
}
