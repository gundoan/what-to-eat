import { useState } from 'react'
import { useNavigate } from 'react-router'
import { createRoom } from '../api/rooms'
import { ApiError } from '../api/client'
import type { PhaseDurations } from '../types/room'
import { DEFAULT_SECONDS } from '../utils/duration'

const DEFAULT_DURATIONS: PhaseDurations = {
  proposalSeconds: DEFAULT_SECONDS,
  mergeSeconds: DEFAULT_SECONDS,
  voteSeconds: DEFAULT_SECONDS,
  randomPickSeconds: DEFAULT_SECONDS,
}

/** 방 만들기 화면의 로직: 단계별 시간을 고르고, 방을 만들면 방 화면으로 이동한다. */
export function useCreateRoom() {
  const navigate = useNavigate()
  const [durations, setDurations] = useState<PhaseDurations>(DEFAULT_DURATIONS)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function setDuration(key: keyof PhaseDurations, seconds: number) {
    setDurations((current) => ({ ...current, [key]: seconds }))
  }

  async function submit() {
    setSubmitting(true)
    setError(null)
    try {
      const { code } = await createRoom(durations)
      navigate(`/rooms/${code}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '방을 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.')
      setSubmitting(false)
    }
  }

  return { durations, setDuration, submit, submitting, error }
}
