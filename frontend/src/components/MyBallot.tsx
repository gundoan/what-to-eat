import type { Ballot, MenuItem } from '../types/room'
import { describeVotes } from '../utils/vote'

type Props = {
  ballot: Ballot
  menus: MenuItem[]
}

/** 내가 낸 투표지. 내 화면에만 보인다. */
export function MyBallot({ ballot, menus }: Props) {
  if (ballot.allGood) {
    return <p>다 좋음을 눌렀어요. 다른 사람들의 표로 정해져요.</p>
  }
  return <p>내가 낸 표: {describeVotes(ballot.votes, menus)}</p>
}
