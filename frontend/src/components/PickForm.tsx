import { useState } from 'react'
import type { MenuItem } from '../types/room'

/**
 * 뽑힌 사람에게 걸린 시간 규칙.
 * - limited: 시간 안에 정하지 않으면 다른 사람이 뽑힌다.
 * - stopped: 모두 한 번씩 뽑혀서 자동 다시 뽑기가 멈췄다(시간 제한 없음).
 * - noLimit: 방장이 랜덤 선택을 "제한 없음"으로 정했다.
 */
export type PickTimeRule = 'limited' | 'stopped' | 'noLimit'

/**
 * 뽑힌 사람에게 보이는 안내. 제한 없음이면 남은 시간 자리에 "시간 제한 없음"이 이미 보이므로, 이 줄은 재미있게 쓴다.
 * 카드 제목("내가 뽑혔어요!")처럼 뽑힌 사람이 속으로 하는 말투다.
 */
const TIME_HINT: Record<PickTimeRule, string> = {
  limited: '시간 안에 정하지 않으면 다른 사람이 뽑혀요.',
  stopped: '모두 한 번씩 뽑혀서 이제 시간 제한이 없어요. 진짜 내가 좋아하는 걸로 가자!',
  noLimit: '진짜 내가 좋아하는 걸로 가자!',
}

type Props = {
  /** 남아 있는 메뉴. 고르면 입력칸에 들어가게 보여준다. 아무도 메뉴를 내지 않았으면 비어 있다 */
  menus: MenuItem[]
  timeRule: PickTimeRule
  onDecide: (menuName: string) => void
}

/**
 * 랜덤으로 뽑힌 사람이 메뉴를 정하는 곳. 나온 메뉴를 고르면 입력칸에 이름이 들어가고, 아무 이름이나 직접 써도 된다(1~30자).
 * "이 메뉴로 정하기"를 눌러야 정해진다. 정하는 순간 모두에게 결정으로 공개되고 바꿀 수 없는데,
 * 메뉴마다 버튼이 줄지어 있어서 한 번에 정해지면 옆 줄을 잘못 눌렀을 때 되돌릴 수 없다. 그래서 고르기와 정하기를 나눴다.
 */
export function PickForm({ menus, timeRule, onDecide }: Props) {
  const [menuName, setMenuName] = useState('')
  const chosen = menuName.trim()

  return (
    <section className="card">
      <p>
        <strong>내가 뽑혔어요!</strong> 먹을 메뉴를 정해 주세요.
      </p>
      <p className="hint">{TIME_HINT[timeRule]}</p>

      {menus.length > 0 && (
        <>
          <p>나온 메뉴에서 고르기</p>
          <p className="hint">고르면 아래 칸에 들어가요. &apos;이 메뉴로 정하기&apos;를 눌러야 정해져요.</p>
          <ul className="pick-list">
            {menus.map((menu) => (
              <li key={menu.id}>
                <span>{menu.name}</span>
                <button
                  type="button"
                  className="inline"
                  aria-pressed={chosen === menu.name}
                  onClick={() => setMenuName(menu.name)}
                >
                  고르기
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (chosen !== '') onDecide(chosen)
        }}
      >
        <input
          aria-label="정할 메뉴 이름"
          value={menuName}
          onChange={(event) => setMenuName(event.target.value)}
          maxLength={30}
          placeholder={menus.length > 0 ? '고르거나 직접 쓰기 (예: 순댓국)' : '메뉴 이름 (예: 순댓국)'}
        />
        <button type="submit" disabled={chosen === ''}>
          이 메뉴로 정하기
        </button>
      </form>
    </section>
  )
}
