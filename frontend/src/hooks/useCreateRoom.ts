import { useState } from 'react'
import { useNavigate } from 'react-router'
import { createRoom } from '../api/rooms'
import { ApiError } from '../api/client'
import type { PhaseDurations } from '../types/room'
import {
  DEFAULT_MERGE_SECONDS,
  DEFAULT_PROPOSAL_SECONDS,
  DEFAULT_RANDOM_PICK_SECONDS,
  DEFAULT_VOTE_SECONDS,
} from '../utils/duration'

/** 고르는 중인 단계별 시간. "직접 설정"에 쓴 값이 규칙에 맞지 않는 단계는 null이다 */
type DurationDraft = { [K in keyof PhaseDurations]: number | null }

const DEFAULT_DURATIONS: DurationDraft = {
  proposalSeconds: DEFAULT_PROPOSAL_SECONDS,
  mergeSeconds: DEFAULT_MERGE_SECONDS,
  voteSeconds: DEFAULT_VOTE_SECONDS,
  randomPickSeconds: DEFAULT_RANDOM_PICK_SECONDS,
}

/** 방 만들기 화면의 로직: 단계별 시간을 고르고, 방을 만들면 방 화면으로 이동한다. */
export function useCreateRoom() {
  const navigate = useNavigate()
  const [durations, setDurations] = useState<DurationDraft>(DEFAULT_DURATIONS)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const ready = complete(durations)

  function setDuration(key: keyof PhaseDurations, seconds: number | null) {
    setDurations((current) => ({ ...current, [key]: seconds }))
  }

  async function submit() {
    if (ready === null) return
    setSubmitting(true)
    setError(null)
    try {
      const { code } = await createRoom(ready)
      navigate(`/rooms/${code}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '방을 만들지 못했어요. 잠시 뒤 다시 시도해 주세요.')
      setSubmitting(false)
    }
  }

  return { durations, setDuration, submit, submitting, error, canSubmit: ready !== null }
}

/** 네 단계의 시간이 모두 정해졌으면 보낼 값을, 하나라도 틀린 칸이 있으면 null을 돌려준다. */
function complete(draft: DurationDraft): PhaseDurations | null {
  const { proposalSeconds, mergeSeconds, voteSeconds, randomPickSeconds } = draft
  if (proposalSeconds === null || mergeSeconds === null || voteSeconds === null || randomPickSeconds === null) {
    return null
  }
  return { proposalSeconds, mergeSeconds, voteSeconds, randomPickSeconds }
}
