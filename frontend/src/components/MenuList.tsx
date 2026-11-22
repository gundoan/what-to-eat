type Props = {
  menus: string[]
}

/** 제안된 메뉴 목록. 서버가 가나다순으로 주고, 누가 냈는지는 없다. */
export function MenuList({ menus }: Props) {
  if (menus.length === 0) {
    return <p>아직 제안된 메뉴가 없어요.</p>
  }
  return (
    <ul className="menu-list">
      {menus.map((menu, index) => (
        // 같은 이름이 여러 개일 수 있어서 순서 번호를 같이 쓴다.
        <li key={`${index}-${menu}`}>{menu}</li>
      ))}
    </ul>
  )
}
