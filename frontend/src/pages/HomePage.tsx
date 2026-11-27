import { DurationSelect } from '../components/DurationSelect'
import { useCreateRoom } from '../hooks/useCreateRoom'

export function HomePage() {
  const { durations, setDuration, submit, submitting, error, canSubmit } = useCreateRoom()

  return (
    <main>
      <h1>뭐먹지?</h1>
      <p>익명으로 메뉴를 제안하고 투표해서 정해요.</p>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <h2>단계별 시간</h2>
        <DurationSelect
          label="제안"
          value={durations.proposalSeconds}
          onChange={(seconds) => setDuration('proposalSeconds', seconds)}
        />
        <DurationSelect
          label="합치기"
          value={durations.mergeSeconds}
          onChange={(seconds) => setDuration('mergeSeconds', seconds)}
        />
        <DurationSelect
          label="투표 (재투표도 같은 시간)"
          value={durations.voteSeconds}
          onChange={(seconds) => setDuration('voteSeconds', seconds)}
        />
        <DurationSelect
          label="랜덤 선택"
          value={durations.randomPickSeconds}
          onChange={(seconds) => setDuration('randomPickSeconds', seconds)}
        />

        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting || !canSubmit}>
          {submitting ? '만드는 중...' : '방 만들기'}
        </button>
      </form>
    </main>
  )
}
