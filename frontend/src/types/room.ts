// 서버 API가 주고받는 데이터 모양. 백엔드의 RoomState, RoomView와 같아야 한다.

export type RoomStatus = 'WAITING' | 'PROPOSING' | 'MERGING' | 'RANDOM_PICK'

export type PhaseDurations = {
  proposalSeconds: number
  mergeSeconds: number
  voteSeconds: number
  randomPickSeconds: number
}

/** 방 참여자 모두에게 똑같이 보이는 방 상태. SSE로도 이 모양 그대로 온다. */
export type RoomState = {
  code: string
  status: RoomStatus
  phaseDurations: PhaseDurations
  participantCount: number
  /** 지금 단계가 끝나는 시각(ISO 문자열). 시간 제한이 없는 단계면 null */
  phaseEndsAt: string | null
  /** 이 상태를 만든 순간의 서버 시각(ISO 문자열) */
  serverTime: string
  /** 제안된 메뉴 이름(가나다순). 누가 냈는지는 없다 */
  menus: string[]
  /** 제안 단계에서 메뉴를 냈거나 "다 좋음"을 누른 사람 수 */
  doneCount: number
}

/** 요청한 본인에 대한 정보. 다른 사람이 방장인지, 무엇을 냈는지는 알 수 없다. */
export type Me = {
  host: boolean
  proposal: string | null
  allGood: boolean
}

export type RoomView = {
  room: RoomState
  me: Me
}
