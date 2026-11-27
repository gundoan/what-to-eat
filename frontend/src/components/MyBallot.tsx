import type { Ballot, MenuItem } from '../types/room'
import { describeVotes } from '../utils/vote'

type Props = {
  ballot: Ballot
  menus: MenuItem[]
  /** 표 앞에 붙일 말. 기본은 "내가 낸 표" */
  title?: string
}

/** 내가 낸 투표지. 내 화면에만 보인다. */
export function MyBallot({ ballot, menus, title = '내가 낸 표' }: Props) {
  if (ballot.allGood) {
    return <p>다 좋음을 눌렀어요. 다른 사람들의 표로 정해져요.</p>
  }
  return (
    <p>
      {title}: {describeVotes(ballot.votes, menus)}
    </p>
  )
}
