import type { MenuItem } from '../types/room'

type Props = {
  menus: MenuItem[]
  /** 내 메뉴의 번호. 주면 목록에서 내 메뉴에 표시를 붙인다(내 화면에만 보인다) */
  myMenuId?: string | null
}

/** 메뉴 목록. 서버가 가나다순으로 주고, 누가 냈는지는 없다. */
export function MenuList({ menus, myMenuId = null }: Props) {
  if (menus.length === 0) {
    return <p>아직 제안된 메뉴가 없어요.</p>
  }
  return (
    <ul className="menu-list">
      {menus.map((menu) => (
        <li key={menu.id}>
          {menu.name}
          {menu.id === myMenuId && <span className="mine"> (내 메뉴)</span>}
        </li>
      ))}
    </ul>
  )
}
