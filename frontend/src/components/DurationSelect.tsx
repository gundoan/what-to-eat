import { useState } from 'react'
import {
  CUSTOM_MAX_MINUTES,
  CUSTOM_MIN_MINUTES,
  DURATION_PRESETS,
  formatDuration,
  parseCustomMinutes,
  toCustomMinutesText,
} from '../utils/duration'

/** 목록에서 "직접 설정"을 뜻하는 값. 시간 값(초)과 겹치지 않는다 */
const CUSTOM = 'custom'

type Props = {
  label: string
  /** 고른 시간(초). "직접 설정"에 쓴 값이 1~60분이 아니면 null */
  value: number | null
  onChange: (seconds: number | null) => void
}

/**
 * 단계 시간 고르기. 목록에서 고르거나, 맨 끝의 "직접 설정"을 고르고 분을 직접 쓴다.
 * 직접 쓴 값이 규칙에 맞지 않으면 null을 알려서, 방 만들기를 누를 수 없게 한다.
 */
export function DurationSelect({ label, value, onChange }: Props) {
  const startsCustom = value !== null && !DURATION_PRESETS.includes(value)
  const [custom, setCustom] = useState(startsCustom)
  const [minutesText, setMinutesText] = useState(startsCustom && value !== null ? toCustomMinutesText(value) : '')
  // 아직 아무것도 안 썼을 때는 틀렸다고 하지 않는다(방 만들기만 막는다).
  const invalid = custom && minutesText.trim() !== '' && value === null

  function selectOption(option: string) {
    if (option === CUSTOM) {
      // 고르던 시간이 분 단위면 칸에 미리 채워서, 고치기만 하면 되게 한다.
      const text = value === null ? '' : toCustomMinutesText(value)
      setCustom(true)
      setMinutesText(text)
      onChange(parseCustomMinutes(text))
    } else {
      setCustom(false)
      onChange(Number(option))
    }
  }

  function typeMinutes(text: string) {
    setMinutesText(text)
    onChange(parseCustomMinutes(text))
  }

  return (
    <div>
      <label className="field">
        <span>{label}</span>
        <select value={custom ? CUSTOM : String(value)} onChange={(event) => selectOption(event.target.value)}>
          {DURATION_PRESETS.map((seconds) => (
            <option key={seconds} value={seconds}>
              {formatDuration(seconds)}
            </option>
          ))}
          <option value={CUSTOM}>직접 설정</option>
        </select>
      </label>
      {custom && (
        <label className="field">
          <span className="hint">
            {CUSTOM_MIN_MINUTES}~{CUSTOM_MAX_MINUTES}분 사이로 쓰기
          </span>
          <span className="minutes">
            <input
              aria-label={`${label} 직접 설정(분)`}
              inputMode="numeric"
              value={minutesText}
              onChange={(event) => typeMinutes(event.target.value)}
              aria-invalid={invalid}
            />
            분
          </span>
        </label>
      )}
      {invalid && (
        <p role="alert">
          {CUSTOM_MIN_MINUTES}~{CUSTOM_MAX_MINUTES} 사이의 숫자(분)로 써 주세요.
        </p>
      )}
    </div>
  )
}
