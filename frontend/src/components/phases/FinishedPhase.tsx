import type { RoomSnapshot } from '../../hooks/useRoom'
import type { Me, ResultReason } from '../../types/room'
import { MyBallot } from '../MyBallot'
import { VoteScores } from '../VoteScores'

type Props = {
  room: RoomSnapshot
  me: Me
}

const REASON_TEXT: Record<ResultReason, string> = {
  ONLY_MENU: '메뉴가 하나라 투표 없이 결정됐어요.',
  VOTE: '투표로 결정됐어요.',
  REVOTE: '첫 투표가 동점이라, 재투표로 결정됐어요.',
  REVOTE_RANDOM: '재투표에서도 정해지지 않아서, 동점이었던 메뉴 중에서 무작위로 정했어요.',
  PICKED: '랜덤으로 뽑힌 사람이 정했어요. 누가 정했는지는 비밀이에요.',
}

/** 메뉴가 결정되어 끝난 화면. 어떻게 결정됐는지와 메뉴별 점수를 보여준다. 누가 어떻게 찍었는지는 없다. */
export function FinishedPhase({ room, me }: Props) {
  const { result } = room
  // 끝난 방에는 항상 결과가 있다.
  if (result === null) return null
  const revoted = result.reason === 'REVOTE' || result.reason === 'REVOTE_RANDOM'

  return (
    <section>
      <h2>결정: {result.name}</h2>
      <p>{REASON_TEXT[result.reason]}</p>

      {room.voteScores.length > 0 && (
        <>
          <h3>{revoted ? '첫 투표 결과' : '투표 결과'}</h3>
          <VoteScores scores={room.voteScores} />
        </>
      )}
      {revoted && (
        <>
          <h3>재투표 결과</h3>
          {room.revoteScores.length > 0 ? (
            <VoteScores scores={room.revoteScores} />
          ) : (
            <p>재투표에서는 아무도 표를 내지 않았어요.</p>
          )}
        </>
      )}

      {/* 내 투표지는 마지막 판(재투표를 했으면 재투표)의 것이다. "다 좋음"이었으면 보여줄 표가 없다. */}
      {me.ballot && !me.ballot.allGood && (
        <MyBallot ballot={me.ballot} menus={room.menus} title={revoted ? '재투표에서 내가 낸 표' : '내가 낸 표'} />
      )}
    </section>
  )
}
