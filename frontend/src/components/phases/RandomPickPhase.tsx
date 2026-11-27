import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { Countdown } from '../Countdown'
import { PickForm } from '../PickForm'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

/**
 * 랜덤 선택: 아무도 메뉴를 내지 않았거나 아무도 투표하지 않았을 때. 서버가 참여자 중 한 명을 무작위로 뽑고, 그 사람이 정한다.
 * 누가 뽑혔는지는 뽑힌 본인만 안다(me.picking). 시간 안에 정하지 않으면 서버가 다른 사람을 다시 뽑는다.
 */
export function RandomPickPhase({ room, me, actions }: Props) {
  // 메뉴가 하나도 없으면 제안 단계에서, 있으면 투표 단계에서 넘어온 것이다.
  const why = room.menus.length === 0 ? '아무도 메뉴를 제안하지 않아서' : '아무도 투표하지 않아서'

  function pickSomeoneElse() {
    // 방장은 메뉴를 정하지 않는다. 뽑힌 사람이 자리에 없을 때 다른 사람에게 넘기기만 한다.
    if (window.confirm('지금 뽑힌 사람 대신 다른 사람을 뽑을까요?')) actions.nextPhase()
  }

  return (
    <section>
      <Countdown localDeadline={room.localDeadline} receivedAt={room.receivedAt} />
      <p>
        {why}, 랜덤으로 뽑힌 한 사람이 메뉴를 정해요. 누가 뽑혔는지는 비밀이에요. ({room.pickTurn}번째 뽑기)
      </p>

      {me.picking ? (
        <PickForm menus={room.menus} onDecide={actions.decidePick} />
      ) : (
        <p>누군가 뽑혀서 메뉴를 정하고 있어요. 시간 안에 정하지 않으면 다른 사람이 뽑혀요.</p>
      )}

      {me.host && (
        <>
          <p className="hint">뽑힌 사람이 자리에 없는 것 같으면 다른 사람을 뽑을 수 있어요.</p>
          <button type="button" onClick={pickSomeoneElse}>
            다른 사람 뽑기
          </button>
        </>
      )}
    </section>
  )
}
