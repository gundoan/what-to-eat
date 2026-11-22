// 백엔드 PhaseDurations 규칙과 같아야 한다: 30초~10분, 30초 단위, 기본 1분.
export const MIN_SECONDS = 30
export const MAX_SECONDS = 600
export const STEP_SECONDS = 30
export const DEFAULT_SECONDS = 60

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
