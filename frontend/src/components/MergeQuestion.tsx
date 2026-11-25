import type { MergeGroup } from '../types/room'

type Props = {
  group: MergeGroup
  myMenuId: string | null
  onAnswer: (agree: boolean) => void
}

/** 내 답을 기다리는 합치기 질문. 한 번 답하면 바꿀 수 없다. */
export function MergeQuestion({ group, myMenuId, onAnswer }: Props) {
  const names = group.menus.map((menu) => (menu.id === myMenuId ? `${menu.name}(내 메뉴)` : menu.name))
  return (
    <section className="card">
      <h2>이 메뉴들과 합칠까요?</h2>
      <p>{names.join(' · ')}</p>
      <p className="hint">
        합치기로 한 메뉴 중 하나가 무작위로 대표가 되고, 나머지는 대표 메뉴에 합쳐져요. 한 번 답하면 바꿀 수 없어요.
      </p>
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
