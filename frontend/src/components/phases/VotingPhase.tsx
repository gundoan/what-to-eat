import type { MenuItem } from '../../types/room'
import { MenuList } from '../MenuList'

type Props = {
  menus: MenuItem[]
  myMenuId: string | null
}

/** 투표 단계. 실제 투표 화면은 기능 4에서 만든다. */
export function VotingPhase({ menus, myMenuId }: Props) {
  return (
    <section>
      <p>투표 단계예요. (투표는 기능 4에서 만들어요)</p>
      <h2>투표할 메뉴</h2>
      <MenuList menus={menus} myMenuId={myMenuId} />
    </section>
  )
}
