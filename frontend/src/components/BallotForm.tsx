import { useBallotDraft } from '../hooks/useBallotDraft'
import type { MenuItem, Vote } from '../types/room'
import { describeVotes } from '../utils/vote'

type Props = {
  menus: MenuItem[]
  myMenuId: string | null
  onSubmit: (votes: Vote[]) => void
  onAllGood: () => void
}

/** 투표지. 메뉴마다 +1 또는 -1을 고르고(모두 합쳐 2표까지) "제출"하거나, "다 좋음"을 누른다. 둘 다 바꿀 수 없다. */
export function BallotForm({ menus, myMenuId, onSubmit, onAllGood }: Props) {
  const draft = useBallotDraft()

  function submit() {
    const message = `${describeVotes(draft.votes, menus)}\n제출하면 바꿀 수 없어요. 제출할까요?`
    if (window.confirm(message)) onSubmit(draft.votes)
  }

  function allGood() {
    if (window.confirm('투표하지 않고 다른 사람들의 표로 정할까요? 누르면 바꿀 수 없어요.')) onAllGood()
  }

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
      <button type="button" onClick={submit} disabled={draft.votes.length === 0}>
        제출
      </button>
      <button type="button" onClick={allGood}>
        다 좋음
      </button>
    </section>
  )
}
