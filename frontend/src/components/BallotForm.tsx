import { useBallotDraft } from '../hooks/useBallotDraft'
import type { MenuItem, Vote } from '../types/room'

type Props = {
  menus: MenuItem[]
  myMenuId: string | null
  onSubmit: (votes: Vote[]) => void
  onAllGood: () => void
}

/**
 * 투표지. 메뉴마다 +1 또는 -1을 고르고(모두 합쳐 2표까지) "제출"하거나, "다 좋음"을 누른다.
 * 둘 다 누르면 바로 처리되고 바꿀 수 없다. 확인창은 띄우지 않고, 바꿀 수 없다는 것을 버튼 위에 미리 적어 둔다.
 */
export function BallotForm({ menus, myMenuId, onSubmit, onAllGood }: Props) {
  const draft = useBallotDraft()

  return (
    <section>
      <p>
        남은 표 <strong>{draft.remaining}개</strong>
      </p>
      <p className="hint">한 메뉴에 1표씩, +1은 먹고 싶다, -1은 먹기 싫다예요. 내 메뉴에도 줄 수 있어요.</p>
      <ul className="ballot">
        {menus.map((menu) => {
          const chosen = draft.points[menu.id]
          const noVotesLeft = draft.remaining === 0 && chosen === undefined
          return (
            <li key={menu.id}>
              <span>
                {menu.name}
                {menu.id === myMenuId && <span className="mine"> (내 메뉴)</span>}
              </span>
              <span>
                <button
                  type="button"
                  className="inline"
                  aria-pressed={chosen === 1}
                  disabled={noVotesLeft}
                  onClick={() => draft.toggle(menu.id, 1)}
                >
                  +1
                </button>
                <button
                  type="button"
                  className="inline"
                  aria-pressed={chosen === -1}
                  disabled={noVotesLeft}
                  onClick={() => draft.toggle(menu.id, -1)}
                >
                  -1
                </button>
              </span>
            </li>
          )
        })}
      </ul>
      <p className="hint">제출하거나 &apos;다 좋음&apos;을 누르면 바꿀 수 없어요.</p>
      <button type="button" onClick={() => onSubmit(draft.votes)} disabled={draft.votes.length === 0}>
        제출
      </button>
      <button type="button" onClick={onAllGood}>
        다 좋음
      </button>
    </section>
  )
}
