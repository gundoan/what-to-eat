import { useState } from 'react'
import type { MenuItem } from '../types/room'

type Props = {
  /** 남아 있는 메뉴. 눌러서 바로 고를 수 있게 보여준다. 아무도 메뉴를 내지 않았으면 비어 있다 */
  menus: MenuItem[]
  /** 시간 안에 정해야 하는지. 모두 한 번씩 뽑혀 자동 다시 뽑기가 멈췄으면 false(시간 제한 없음) */
  timeLimited: boolean
  onDecide: (menuName: string) => void
}

/**
 * 랜덤으로 뽑힌 사람이 메뉴를 정하는 곳. 나온 메뉴를 눌러도 되고, 아무 이름이나 직접 써도 된다(1~30자).
 * 정하는 순간 모두에게 결정으로 공개되고 바꿀 수 없어서, 한 번 더 확인한다.
 */
export function PickForm({ menus, timeLimited, onDecide }: Props) {
  const [menuName, setMenuName] = useState('')

  function decide(name: string) {
    const trimmed = name.trim()
    if (trimmed === '') return
    if (window.confirm(`'${trimmed}'(으)로 정할까요? 정하면 바로 결정되고 바꿀 수 없어요.`)) onDecide(trimmed)
  }

  return (
    <section className="card">
      <p>
        <strong>내가 뽑혔어요!</strong> 먹을 메뉴를 정해 주세요.
      </p>
      <p className="hint">
        {timeLimited
          ? '시간 안에 정하지 않으면 다른 사람이 뽑혀요.'
          : '모두 한 번씩 뽑혀서 시간 제한이 없어요. 천천히 정해도 돼요.'}
      </p>

      {menus.length > 0 && (
        <>
          <p>나온 메뉴 중에서 고르기</p>
          <ul className="pick-list">
            {menus.map((menu) => (
              <li key={menu.id}>
                <span>{menu.name}</span>
                <button type="button" className="inline" onClick={() => decide(menu.name)}>
                  이걸로 정하기
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      <form
        onSubmit={(event) => {
          event.preventDefault()
          decide(menuName)
        }}
      >
        <input
          aria-label="정할 메뉴 이름"
          value={menuName}
          onChange={(event) => setMenuName(event.target.value)}
          maxLength={30}
          placeholder={menus.length > 0 ? '다른 메뉴 직접 쓰기 (예: 할매집 제육)' : '메뉴 이름 (예: 할매집 제육)'}
        />
        <button type="submit" disabled={menuName.trim() === ''}>
          이 메뉴로 정하기
        </button>
      </form>
    </section>
  )
}
