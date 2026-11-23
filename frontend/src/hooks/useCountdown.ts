import { useEffect, useState } from 'react'

/**
 * 남은 시간(초)을 돌려준다. 0.25초마다 다시 계산한다. 시간 제한이 없으면 null.
 * 시간이 0이 돼도 단계를 넘기는 건 서버가 하고, 넘어가면 SSE로 새 상태가 온다.
 *
 * @param localDeadline 끝나는 시각(이 브라우저의 Date.now() 기준)
 * @param receivedAt 그 상태를 받은 시각. 처음 그릴 때의 기준 시각으로 쓴다
 */
export function useCountdown(localDeadline: number | null, receivedAt: number): number | null {
  const [now, setNow] = useState(receivedAt)

  useEffect(() => {
    if (localDeadline === null) return
    const timer = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(timer)
  }, [localDeadline])

  if (localDeadline === null) return null
  const current = Math.max(now, receivedAt)
  return Math.max(0, Math.ceil((localDeadline - current) / 1000))
}
