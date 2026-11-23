import type { RoomStatus } from '../../types/room'
import { MenuList } from '../MenuList'

type Props = {
  status: Extract<RoomStatus, 'MERGING' | 'RANDOM_PICK'>
  menus: string[]
}

/** 제안 다음 단계. 합치기(기능 3)와 랜덤 선택(기능 5)의 실제 화면은 그 기능에서 만든다. */
export function AfterProposalPhase({ status, menus }: Props) {
  if (status === 'RANDOM_PICK') {
    return <p>아무도 메뉴를 제안하지 않아서 랜덤 선택으로 넘어왔어요. (랜덤 선택은 기능 5에서 만들어요)</p>
  }
  return (
    <section>
      <p>메뉴 제안이 끝났어요. (합치기는 기능 3에서 만들어요)</p>
      <h2>제안된 메뉴</h2>
      <MenuList menus={menus} />
    </section>
  )
}
