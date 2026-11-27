import { useState } from 'react'
import type { Me } from '../types/room'

type Props = {
  me: Me
  onPropose: (menuName: string) => void
  onAllGood: () => void
}

/** 내 메뉴 제안·바꾸기와 "다 좋음". 단계가 끝나기 전에는 몇 번이든 바꿀 수 있다. */
export function ProposalForm({ me, onPropose, onAllGood }: Props) {
  const [menuName, setMenuName] = useState('')

  return (
    <section>
      {me.proposal && (
        <p>
          내 제안: <strong>{me.proposal}</strong>
        </p>
      )}
      {me.allGood && <p>다 좋음을 눌렀어요. 마음이 바뀌면 지금 제안해도 돼요.</p>}

      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (menuName.trim() === '') return
          onPropose(menuName)
          setMenuName('')
        }}
      >
        <input
          aria-label="메뉴 이름"
          value={menuName}
          onChange={(event) => setMenuName(event.target.value)}
          maxLength={30}
          placeholder="음식 이름 (예: 순댓국)"
        />
        <button type="submit">{me.proposal ? '바꾸기' : '제안하기'}</button>
      </form>

      {!me.allGood && (
        <button type="button" onClick={onAllGood}>
          {me.proposal ? '제안 취소하고 다 좋음' : '다 좋음'}
        </button>
      )}
    </section>
  )
}
