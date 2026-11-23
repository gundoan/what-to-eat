import { useCountdown } from '../hooks/useCountdown'

type Props = {
  localDeadline: number | null
  receivedAt: number
}

export function Countdown({ localDeadline, receivedAt }: Props) {
  const remaining = useCountdown(localDeadline, receivedAt)
  if (remaining === null) return null

  const minutes = Math.floor(remaining / 60)
  const seconds = String(remaining % 60).padStart(2, '0')
  return (
    <p className="countdown" aria-live="polite">
      남은 시간 <strong>{`${minutes}:${seconds}`}</strong>
    </p>
  )
}
