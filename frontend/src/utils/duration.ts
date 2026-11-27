// 백엔드 PhaseDurations 규칙과 같아야 한다: 30초~10분, 30초 단위.
export const MIN_SECONDS = 30
export const MAX_SECONDS = 600
export const STEP_SECONDS = 30

/** 방 만들기 화면에 처음 골라져 있는 시간. 제안, 합치기, 투표(재투표도)는 5분, 랜덤 선택은 1분이다(백엔드 기본값과 같다). */
export const DEFAULT_PROPOSAL_SECONDS = 300
export const DEFAULT_MERGE_SECONDS = 300
export const DEFAULT_VOTE_SECONDS = 300
export const DEFAULT_RANDOM_PICK_SECONDS = 60

export const DURATION_OPTIONS: number[] = Array.from(
  { length: (MAX_SECONDS - MIN_SECONDS) / STEP_SECONDS + 1 },
  (_, i) => MIN_SECONDS + i * STEP_SECONDS,
)

/** 90 → "1분 30초", 60 → "1분", 30 → "30초" */
export function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes === 0) return `${rest}초`
  if (rest === 0) return `${minutes}분`
  return `${minutes}분 ${rest}초`
}
