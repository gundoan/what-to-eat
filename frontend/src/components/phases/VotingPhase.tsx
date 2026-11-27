import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { BallotForm } from '../BallotForm'
import { Countdown } from '../Countdown'
import { MyBallot } from '../MyBallot'
import { VoteScores } from '../VoteScores'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

/**
 * 투표와 재투표 화면. 규칙은 같고, 표를 줄 수 있는 메뉴(voteCandidates)만 다르다.
 * 재투표에서는 동점이었던 메뉴만 후보이고, 왜 다시 투표하는지 알 수 있게 첫 투표 점수를 보여준다.
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

      {revote && (
        <>
          <h2>첫 투표 결과</h2>
          <VoteScores scores={room.voteScores} />
        </>
      )}

      {me.host && (
        <button type="button" onClick={actions.nextPhase}>
          다음 단계로
        </button>
      )}
    </section>
  )
}
