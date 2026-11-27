import type { PhaseDurations, RoomStatus } from '../types/room'

// 백엔드 PhaseDurations 규칙과 같아야 한다: 30초~10분(30초 단위)이거나, 0이면 "제한 없음".
export const MIN_SECONDS = 30
export const MAX_SECONDS = 600
export const STEP_SECONDS = 30

/** "제한 없음". 이 단계는 시간이 다 되는 일 없이, 모두 끝내거나 방장이 넘길 때만 넘어간다. */
export const NO_LIMIT = 0

/** 방 만들기 화면에 처음 골라져 있는 시간. 제안, 합치기, 투표(재투표도)는 5분, 랜덤 선택은 1분이다(백엔드 기본값과 같다). */
export const DEFAULT_PROPOSAL_SECONDS = 300
export const DEFAULT_MERGE_SECONDS = 300
export const DEFAULT_VOTE_SECONDS = 300
export const DEFAULT_RANDOM_PICK_SECONDS = 60

/** 방 만들기 화면에서 고를 수 있는 시간: 30초부터 10분까지, 맨 끝에 "제한 없음" */
export const DURATION_OPTIONS: number[] = [
  ...Array.from({ length: (MAX_SECONDS - MIN_SECONDS) / STEP_SECONDS + 1 }, (_, i) => MIN_SECONDS + i * STEP_SECONDS),
  NO_LIMIT,
]

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
