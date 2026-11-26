import type { MergeGroup } from '../types/room'
import { requestParts } from '../utils/mergeGroup'

type Props = {
  groups: MergeGroup[]
}

/** 자동으로 묶인 그룹과 직접 합치기 요청의 진행 상황. 누가 어떻게 답했는지는 없다. */
export function MergeGroupList({ groups }: Props) {
  if (groups.length === 0) {
    return <p>아직 합치기가 없어요.</p>
  }
  return (
    <ul className="merge-groups">
      {groups.map((group) => (
        <li key={group.id}>
          {title(group)}
          <span className="hint"> — {statusText(group)}</span>
        </li>
      ))}
    </ul>
  )
}

function title(group: MergeGroup): string {
  const request = requestParts(group)
  if (request) {
    return `${request.from.name} → ${request.to.name} (직접 요청)`
  }
  return `${group.menus.map((menu) => menu.name).join(' · ')} (이름이 같음)`
}

function statusText(group: MergeGroup): string {
  switch (group.status) {
    case 'PENDING':
      return group.kind === 'DIRECT' ? '답 기다리는 중' : `답한 사람 ${group.answeredCount}/${group.menus.length}`
    case 'MERGED':
      return `'${group.representative}'(으)로 합쳐졌어요`
    case 'NOT_MERGED':
      return '합치지 않았어요'
  }
}
