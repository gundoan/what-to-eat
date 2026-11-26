import type { MenuItem, Vote } from '../types/room'

/** "김밥 +1, 라면 -1"처럼 표를 글로 보여준다. */
export function describeVotes(votes: Vote[], menus: MenuItem[]): string {
  return votes
    .map((vote) => {
      const name = menus.find((menu) => menu.id === vote.menuId)?.name ?? '(사라진 메뉴)'
      return `${name} ${vote.point > 0 ? '+1' : '-1'}`
    })
    .join(', ')
}
