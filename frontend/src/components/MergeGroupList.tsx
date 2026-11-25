import type { MergeGroup } from '../types/room'

type Props = {
  groups: MergeGroup[]
}

/** 이름이 같아서 자동으로 묶인 그룹들과 진행 상황. 누가 어떻게 답했는지는 없다. */
export function MergeGroupList({ groups }: Props) {
  if (groups.length === 0) {
    return <p>이름이 같은 메뉴가 없어요.</p>
  }
  return (
    <ul className="merge-groups">
      {groups.map((group) => (
        <li key={group.id}>
          {group.menus.map((menu) => menu.name).join(' · ')}
          <span className="hint"> — {statusText(group)}</span>
        </li>
      ))}
    </ul>
  )
}

function statusText(group: MergeGroup): string {
  switch (group.status) {
    case 'PENDING':
      return `답한 사람 ${group.answeredCount}/${group.menus.length}`
    case 'MERGED':
      return `'${group.representative}'(으)로 합쳐졌어요`
    case 'NOT_MERGED':
      return '합치지 않았어요'
  }
}
