// 서버 API가 주고받는 데이터 모양. 백엔드의 RoomState, RoomView와 같아야 한다.

export type RoomStatus = 'WAITING'

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
}

/** 요청한 본인에 대한 정보. 다른 사람이 방장인지는 알 수 없다. */
export type Me = {
  host: boolean
}

export type RoomView = {
  room: RoomState
  me: Me
}
