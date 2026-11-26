import type { MenuScore } from '../../types/room'
import { VoteScores } from '../VoteScores'

type Props = {
  scores: MenuScore[]
}

/** 1등이 동점일 때. 실제 재투표 화면은 기능 5에서 만든다. */
export function RevotingPhase({ scores }: Props) {
  const best = Math.max(...scores.map((score) => score.total))
  const tied = scores.filter((score) => score.total === best).map((score) => score.menu.name)
  return (
    <section>
      <p>
        {tied.join(', ')} 메뉴가 동점이라 재투표를 해요. (재투표는 기능 5에서 만들어요)
      </p>
      <VoteScores scores={scores} />
    </section>
  )
}
