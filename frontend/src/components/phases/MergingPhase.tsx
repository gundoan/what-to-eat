import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { Countdown } from '../Countdown'
import { MenuList } from '../MenuList'
import { MergeGroupList } from '../MergeGroupList'
import { MergeQuestion } from '../MergeQuestion'
import { MyMergeStatus } from '../MyMergeStatus'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

export function MergingPhase({ room, me, actions }: Props) {
  const question = room.mergeGroups.find((group) => group.id === me.pendingGroupId)

  return (
    <section>
      <Countdown localDeadline={room.localDeadline} receivedAt={room.receivedAt} />
      <p>
        {room.participantCount}명 중 {room.doneCount}명 끝냄
      </p>

      {question && (
        <MergeQuestion
          group={question}
          myMenuId={me.menuId}
          onAnswer={(agree) => actions.answerMerge(question.id, agree)}
        />
      )}
      <MyMergeStatus me={me} onFinish={actions.finishMerge} />

      <h2>이름이 같은 메뉴</h2>
      <MergeGroupList groups={room.mergeGroups} />

      <h2>남은 메뉴</h2>
      <MenuList menus={room.menus} myMenuId={me.menuId} />

      {me.host && (
        <button type="button" onClick={actions.nextPhase}>
          다음 단계로
        </button>
      )}
    </section>
  )
}
