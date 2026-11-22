import { DURATION_OPTIONS, formatDuration } from '../utils/duration'

type Props = {
  label: string
  value: number
  onChange: (seconds: number) => void
}

export function DurationSelect({ label, value, onChange }: Props) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(Number(event.target.value))}>
        {DURATION_OPTIONS.map((seconds) => (
          <option key={seconds} value={seconds}>
            {formatDuration(seconds)}
          </option>
        ))}
      </select>
    </label>
  )
}
