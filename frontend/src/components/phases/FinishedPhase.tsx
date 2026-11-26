import type { RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { MyBallot } from '../MyBallot'
import { VoteScores } from '../VoteScores'

type Props = {
  room: RoomSnapshot
  me: Me
}

/** 메뉴가 결정되어 끝난 화면. */
export function FinishedPhase({ room, me }: Props) {
  return (
    <section>
      <h2>결정: {room.resultMenu?.name}</h2>
      {room.voteScores.length > 0 ? (
        <VoteScores scores={room.voteScores} />
      ) : (
        <p>메뉴가 하나라 투표 없이 결정됐어요.</p>
      )}
      {me.ballot && <MyBallot ballot={me.ballot} menus={room.menus} />}
    </section>
  )
}
