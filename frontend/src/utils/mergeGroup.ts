import type { MenuItem, MergeGroup } from '../types/room'

/** 직접 요청에서 요청한 메뉴(from)와 요청받은 메뉴(to). 자동 그룹이면 null */
export function requestParts(group: MergeGroup): { from: MenuItem; to: MenuItem } | null {
  if (group.kind !== 'DIRECT') return null
  const to = group.menus.find((menu) => menu.id === group.targetMenuId)
  const from = group.menus.find((menu) => menu.id !== group.targetMenuId)
  return from && to ? { from, to } : null
}
