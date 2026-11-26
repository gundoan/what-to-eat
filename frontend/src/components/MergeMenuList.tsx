import type { MenuItem } from '../types/room'

type Props = {
  menus: MenuItem[]
  myMenuId: string | null
  /** 내가 낸 메뉴 이름. 확인 창에 쓴다 */
  myMenuName: string | null
  /** 지금 합치기를 요청할 수 있는 메뉴들(서버가 규칙으로 골라 준다) */
  requestableIds: string[]
  onRequest: (menuId: string) => void
}

/** 합치기 단계의 남은 메뉴 목록. 요청할 수 있는 메뉴 옆에 "이 메뉴에 합치기" 버튼이 있다. */
export function MergeMenuList({ menus, myMenuId, myMenuName, requestableIds, onRequest }: Props) {
  const requestable = new Set(requestableIds)

  function confirmAndRequest(menu: MenuItem) {
    const message =
      `요청이 받아들여지면 내 메뉴(${myMenuName})는 '${menu.name}'에 합쳐지고, '${menu.name}'이(가) 대표로 남아요.\n` +
      '보낸 요청은 취소할 수 없어요. 보낼까요?'
    if (window.confirm(message)) onRequest(menu.id)
  }

  return (
    <ul className="menu-list">
      {menus.map((menu) => (
        <li key={menu.id}>
          {menu.name}
          {menu.id === myMenuId && <span className="mine"> (내 메뉴)</span>}
          {requestable.has(menu.id) && (
            <button type="button" className="inline" onClick={() => confirmAndRequest(menu)}>
              이 메뉴에 합치기
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
