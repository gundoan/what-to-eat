// 서버 API가 주고받는 데이터 모양. 백엔드의 RoomState, RoomView와 같아야 한다.

export type RoomStatus = 'WAITING' | 'PROPOSING' | 'MERGING' | 'VOTING' | 'REVOTING' | 'RANDOM_PICK' | 'FINISHED'

export type PhaseDurations = {
  proposalSeconds: number
  mergeSeconds: number
  voteSeconds: number
  randomPickSeconds: number
}

export type MenuItem = {
  /** 공개 번호(무작위). 올라온 순서와 상관없다 */
  id: string
  name: string
}

/** PENDING: 답을 기다리는 중, MERGED: 합쳐짐, NOT_MERGED: 합치지 않음 */
export type MergeGroupStatus = 'PENDING' | 'MERGED' | 'NOT_MERGED'

/** AUTO: 이름이 같아서 자동으로 묶은 그룹, DIRECT: 참여자가 직접 보낸 합치기 요청 */
export type MergeGroupKind = 'AUTO' | 'DIRECT'

/** 합치기 그룹: "이 메뉴들을 합칠까요?"라는 질문 하나. 누가 어떻게 답했는지는 없고, 답한 사람 수만 있다. */
export type MergeGroup = {
  id: string
  kind: MergeGroupKind
  status: MergeGroupStatus
  /** 그룹에 든 메뉴(합쳐져 사라진 메뉴 포함), 가나다순 */
  menus: MenuItem[]
  /** 직접 요청에서 요청받은 메뉴의 번호. 나머지 하나가 요청한 메뉴다. 자동 그룹이면 null */
  targetMenuId: string | null
  /** 합쳐졌을 때 남은 대표 메뉴 이름. 아니면 null */
  representative: string | null
  answeredCount: number
}

/** 표 하나의 점수. +1(먹고 싶다) 또는 -1(먹기 싫다) */
export type VotePoint = 1 | -1

export type Vote = {
  menuId: string
  point: VotePoint
}

/** 한 메뉴가 받은 점수. 누가 어떻게 찍었는지는 없다. */
export type MenuScore = {
  menu: MenuItem
  plus: number
  minus: number
  total: number
}

/** 내가 낸 투표지. "다 좋음"이면 votes가 비어 있다. */
export type Ballot = {
  allGood: boolean
  votes: Vote[]
}

/** 방 참여자 모두에게 똑같이 보이는 방 상태. */
export type RoomState = {
  code: string
  status: RoomStatus
  phaseDurations: PhaseDurations
  participantCount: number
  /** 지금 단계가 끝나는 시각(ISO 문자열). 시간 제한이 없는 단계면 null */
  phaseEndsAt: string | null
  /** 이 상태를 만든 순간의 서버 시각(ISO 문자열) */
  serverTime: string
  /** 남아 있는 메뉴(다른 메뉴에 합쳐진 것은 빠진다), 가나다순. 누가 냈는지는 없다 */
  menus: MenuItem[]
  /** 지금 단계(제안, 합치기, 투표)를 끝낸 사람 수 */
  doneCount: number
  /** 합치기 그룹들(합치기 단계부터 생긴다) */
  mergeGroups: MergeGroup[]
  /** 결정된 메뉴. 끝나기 전에는 null */
  resultMenu: MenuItem | null
  /** 메뉴별 점수(총점이 높은 순). 투표가 끝난 뒤에만 온다 */
  voteScores: MenuScore[]
}

/** 받는 사람 본인에 대한 정보. 다른 사람이 방장인지, 무엇을 냈는지는 알 수 없다. */
export type Me = {
  host: boolean
  /** 내가 제안한 메뉴 이름(합쳐졌어도 원래 이름) */
  proposal: string | null
  allGood: boolean
  /** 남아 있는 내 메뉴의 공개 번호. 메뉴가 없거나 다른 메뉴에 합쳐졌으면 null */
  menuId: string | null
  /** 내 메뉴가 최종적으로 합쳐진 메뉴 이름 */
  mergedInto: string | null
  /** 내 답을 기다리는 합치기 그룹(자동 그룹이나 내가 받은 직접 요청)의 번호 */
  pendingGroupId: string | null
  /** 내가 보내고 상대의 답을 기다리는 직접 요청의 번호 */
  sentGroupId: string | null
  /** 지금 내가 직접 합치기를 요청할 수 있는 메뉴들의 번호 */
  requestableMenuIds: string[]
  /** 내가 낸 투표지. 아직 내지 않았으면 null */
  ballot: Ballot | null
  /** 지금 단계(제안, 합치기, 투표)에서 할 일을 끝냈는지 */
  done: boolean
}

/** 한 참여자가 받는 화면 정보. HTTP 응답과 SSE 이벤트 모두 이 모양이다. */
export type RoomView = {
  room: RoomState
  me: Me
}
