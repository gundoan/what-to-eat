import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { Countdown } from '../Countdown'
import { MenuList } from '../MenuList'
import { ProposalForm } from '../ProposalForm'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

export function ProposingPhase({ room, me, actions }: Props) {
  return (
    <section>
      <Countdown localDeadline={room.localDeadline} receivedAt={room.receivedAt} />
      <p>
        {room.participantCount}명 중 {room.doneCount}명 끝냄
      </p>

      <ProposalForm me={me} onPropose={actions.propose} onAllGood={actions.allGood} />

      <h2>제안된 메뉴</h2>
      <MenuList menus={room.menus} />

      {me.host && (
        <button type="button" onClick={actions.nextPhase}>
          다음 단계로
        </button>
      )}
    </section>
  )
}
