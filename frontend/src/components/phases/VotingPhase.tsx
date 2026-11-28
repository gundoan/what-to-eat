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

/**
 * 투표와 재투표 화면. 규칙은 같고, 표를 줄 수 있는 메뉴(voteCandidates)만 다르다.
 * 재투표에서는 동점이었던 메뉴만 후보이고, 왜 다시 투표하는지는 한 줄로만 알려준다.
 * 첫 투표 점수표는 화면만 복잡하게 해서 보여주지 않는다. 두 점수표는 끝난 뒤 결과 화면에 나온다.
 */
export function VotingPhase({ room, me, actions }: Props) {
  const revote = room.status === 'REVOTING'

  return (
    <section>
      <Countdown room={room} />
      <p>
        {room.participantCount}명 중 {room.doneCount}명 끝냄
      </p>

      {revote && (
        <>
          <p>
            <strong>{room.voteCandidates.map((menu) => menu.name).join(', ')}</strong> 메뉴가 동점이라, 이 메뉴들만
            놓고 한 번 더 투표해요. 첫 투표에 냈어도 다시 내 주세요.
          </p>
          <p className="hint">재투표에서도 동점이면 동점인 메뉴 중에서 무작위로 정해요.</p>
        </>
      )}

      {me.ballot ? (
        <>
          <MyBallot ballot={me.ballot} menus={room.voteCandidates} />
          <p className="hint">점수는 {revote ? '재투표가' : '투표가'} 끝나면 공개돼요.</p>
        </>
      ) : (
        <BallotForm
          menus={room.voteCandidates}
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
