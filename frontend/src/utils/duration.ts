import type { PhaseDurations, RoomStatus } from '../types/room'

// 백엔드 PhaseDurations 규칙: 30초~60분(30초 단위)이거나, 0이면 "제한 없음".
// 화면에서는 그 안에서 몇 가지를 목록으로 보여주고, 나머지는 "직접 설정"으로 분 단위(1~60분)로 받는다.

/** "제한 없음". 이 단계는 시간이 다 되는 일 없이, 모두 끝내거나 방장이 넘길 때만 넘어간다. */
export const NO_LIMIT = 0

/** 방 만들기 화면에 처음 골라져 있는 시간. 제안, 합치기, 투표(재투표도)는 5분, 랜덤 선택은 1분이다(백엔드 기본값과 같다). */
export const DEFAULT_PROPOSAL_SECONDS = 300
export const DEFAULT_MERGE_SECONDS = 300
export const DEFAULT_VOTE_SECONDS = 300
export const DEFAULT_RANDOM_PICK_SECONDS = 60

/** 방 만들기 화면의 시간 목록. 이 밖의 시간은 목록 맨 끝의 "직접 설정"으로 쓴다. */
export const DURATION_PRESETS: number[] = [30, 60, 120, 180, 300, 600, 1800, NO_LIMIT]

/** "직접 설정"으로 쓸 수 있는 시간(분). 더 길게 하려면 "제한 없음"을 고른다. */
export const CUSTOM_MIN_MINUTES = 1
export const CUSTOM_MAX_MINUTES = 60

/** "직접 설정" 칸에 쓴 글자를 초로 바꾼다. "15" → 900. 1~60 사이의 정수(분)가 아니면 null */
export function parseCustomMinutes(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const minutes = Number(trimmed)
  if (minutes < CUSTOM_MIN_MINUTES || minutes > CUSTOM_MAX_MINUTES) return null
  return minutes * 60
}

/** 고르던 시간을 "직접 설정" 칸에 미리 채울 글자로 바꾼다. 300 → "5". 분으로 쓸 수 없는 시간(30초, 제한 없음)이면 빈칸 */
export function toCustomMinutesText(seconds: number): string {
  return parseCustomMinutes(String(seconds / 60)) === null ? '' : String(seconds / 60)
}

/** 90 → "1분 30초", 60 → "1분", 30 → "30초", 0 → "제한 없음" */
export function formatDuration(seconds: number): string {
  if (seconds === NO_LIMIT) return '제한 없음'
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes === 0) return `${rest}초`
  if (rest === 0) return `${minutes}분`
  return `${minutes}분 ${rest}초`
}

/** 지금 단계를 방장이 "제한 없음"으로 정했는지. 재투표는 투표 시간을 같이 쓴다. */
export function phaseHasNoLimit(status: RoomStatus, durations: PhaseDurations): boolean {
  switch (status) {
    case 'PROPOSING':
      return durations.proposalSeconds === NO_LIMIT
    case 'MERGING':
      return durations.mergeSeconds === NO_LIMIT
    case 'VOTING':
    case 'REVOTING':
      return durations.voteSeconds === NO_LIMIT
    case 'RANDOM_PICK':
      return durations.randomPickSeconds === NO_LIMIT
    default:
      return false
  }
}
