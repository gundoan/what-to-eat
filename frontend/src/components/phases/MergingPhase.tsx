import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { Countdown } from '../Countdown'
import { MergeGroupList } from '../MergeGroupList'
import { MergeMenuList } from '../MergeMenuList'
import { MergeQuestion } from '../MergeQuestion'
import { MyMergeStatus } from '../MyMergeStatus'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

export function MergingPhase({ room, me, actions }: Props) {
  const question = room.mergeGroups.find((group) => group.id === me.pendingGroupId)
  const sentRequest = room.mergeGroups.find((group) => group.id === me.sentGroupId)
  const hint = requestHint(me)

  return (
    <section>
      <Countdown room={room} />
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
      <MyMergeStatus me={me} sentRequest={sentRequest} onFinish={actions.finishMerge} />

      <h2>합치기 현황</h2>
      <MergeGroupList groups={room.mergeGroups} />

      <h2>남은 메뉴</h2>
      {hint && <p className="hint">{hint}</p>}
      <MergeMenuList
        menus={room.menus}
        myMenuId={me.menuId}
        requestableIds={me.requestableMenuIds}
        onRequest={actions.requestMerge}
      />

      {me.host && (
        <button type="button" onClick={actions.nextPhase}>
          다음 단계로
        </button>
      )}
    </section>
  )
}

/** 직접 합치기에 대한 안내. 메뉴가 없거나 합치기를 끝냈으면 보여주지 않는다. */
function requestHint(me: Me): string | null {
  if (me.menuId === null || me.done) return null
  if (me.pendingGroupId !== null || me.sentGroupId !== null) {
    return '내 메뉴가 결과가 안 나온 합치기에 들어 있어요. 결과가 나오면 다른 메뉴에 합치기를 요청할 수 있어요.'
  }
  if (me.requestableMenuIds.length === 0) {
    return '지금은 합치기를 요청할 수 있는 메뉴가 없어요.'
  }
  return '자동으로 안 묶인 같은 메뉴가 있으면 "이 메뉴에 합치기"로 요청해 보세요. 받아들여지면 그 메뉴가 대표로 남아요. 보낸 요청은 취소할 수 없어요.'
}
