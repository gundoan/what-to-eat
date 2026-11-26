import type { MergeGroup } from '../types/room'
import { requestParts } from '../utils/mergeGroup'

type Props = {
  group: MergeGroup
  myMenuId: string | null
  onAnswer: (agree: boolean) => void
}

/** 내 답을 기다리는 합치기 질문(자동 그룹 또는 내가 받은 직접 요청). 한 번 답하면 바꿀 수 없다. */
export function MergeQuestion({ group, myMenuId, onAnswer }: Props) {
  const request = requestParts(group)
  return (
    <section className="card">
      {request ? (
        <>
          <h2>합치기 요청을 받았어요</h2>
          <p>
            &apos;{request.from.name}&apos; 메뉴를 내 메뉴 &apos;{request.to.name}&apos;에 합칠까요?
          </p>
          <p className="hint">합치면 내 메뉴가 대표로 남아요. 한 번 답하면 바꿀 수 없어요.</p>
        </>
      ) : (
        <>
          <h2>이 메뉴들과 합칠까요?</h2>
          <p>{group.menus.map((menu) => (menu.id === myMenuId ? `${menu.name}(내 메뉴)` : menu.name)).join(' · ')}</p>
          <p className="hint">
            합치기로 한 메뉴 중 하나가 무작위로 대표가 되고, 나머지는 대표 메뉴에 합쳐져요. 한 번 답하면 바꿀 수 없어요.
          </p>
        </>
      )}
      <div className="buttons">
        <button type="button" onClick={() => onAnswer(true)}>
          합치기
        </button>
        <button type="button" onClick={() => onAnswer(false)}>
          합치지 않기
        </button>
      </div>
    </section>
  )
}
