import type { MenuItem } from '../types/room'

type Props = {
  menus: MenuItem[]
  myMenuId: string | null
  /** 지금 합치기를 요청할 수 있는 메뉴들(서버가 규칙으로 골라 준다) */
  requestableIds: string[]
  onRequest: (menuId: string) => void
}

/**
 * 합치기 단계의 남은 메뉴 목록. 요청할 수 있는 메뉴 옆에 "이 메뉴에 합치기" 버튼이 있고, 누르면 바로 요청을 보낸다.
 * 보낸 요청은 취소할 수 없지만, 상대가 동의해야 합쳐지므로 확인창 없이 보낸다(취소할 수 없다는 것은 위 안내에 적어 둔다).
 */
export function MergeMenuList({ menus, myMenuId, requestableIds, onRequest }: Props) {
  const requestable = new Set(requestableIds)

  return (
    <ul className="menu-list">
      {menus.map((menu) => (
        <li key={menu.id}>
          {menu.name}
          {menu.id === myMenuId && <span className="mine"> (내 메뉴)</span>}
          {requestable.has(menu.id) && (
            <button type="button" className="inline" onClick={() => onRequest(menu.id)}>
              이 메뉴에 합치기
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
