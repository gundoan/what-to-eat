import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { BallotForm } from '../BallotForm'
import { Countdown } from '../Countdown'
import { MyBallot } from '../MyBallot'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

export function VotingPhase({ room, me, actions }: Props) {
  return (
    <section>
      <Countdown localDeadline={room.localDeadline} receivedAt={room.receivedAt} />
      <p>
        {room.participantCount}명 중 {room.doneCount}명 끝냄
      </p>

      {me.ballot ? (
        <>
          <MyBallot ballot={me.ballot} menus={room.menus} />
          <p className="hint">점수는 투표가 끝나면 공개돼요.</p>
        </>
      ) : (
        <BallotForm
          menus={room.menus}
          myMenuId={me.menuId}
          onSubmit={actions.submitVotes}
          onAllGood={actions.voteAllGood}
        />
      )}

      {me.host && (
        <button type="button" onClick={actions.nextPhase}>
          다음 단계로
        </button>
      )}
    </section>
  )
}
