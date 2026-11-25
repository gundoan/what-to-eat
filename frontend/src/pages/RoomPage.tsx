import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { MergingPhase } from '../components/phases/MergingPhase'
import { ProposingPhase } from '../components/phases/ProposingPhase'
import { RandomPickPhase } from '../components/phases/RandomPickPhase'
import { VotingPhase } from '../components/phases/VotingPhase'
import { WaitingPhase } from '../components/phases/WaitingPhase'
import { useRoom, type RoomError } from '../hooks/useRoom'
import type { RoomStatus } from '../types/room'

const STATUS_LABELS: Record<RoomStatus, string> = {
  WAITING: '대기 중',
  PROPOSING: '메뉴 제안 중',
  MERGING: '합치기 중',
  VOTING: '투표 중',
  RANDOM_PICK: '랜덤 선택',
}

const ERROR_MESSAGES: Record<RoomError, string> = {
  NOT_FOUND: '방을 찾을 수 없어요. 링크를 다시 확인해 주세요.',
  FULL: '방 정원(50명)이 가득 찼어요.',
  UNKNOWN: '방에 들어가지 못했어요. 잠시 뒤 다시 시도해 주세요.',
}

export function RoomPage() {
  const { code = '' } = useParams()
  const { screen, actions, actionError } = useRoom(code)

  if (screen.phase === 'loading') {
    return <main>방에 들어가는 중...</main>
  }

  if (screen.phase === 'error') {
    return (
      <main>
        <p role="alert">{ERROR_MESSAGES[screen.error]}</p>
        <Link to="/">새 방 만들기</Link>
      </main>
    )
  }

  const { room, me, connected } = screen
  return (
    <main>
      <h1>뭐먹지?</h1>
      <p className="status">{STATUS_LABELS[room.status]}</p>
      <p>
        참여 인원 <strong>{room.participantCount}명</strong>
      </p>
      {me.host && <p className="badge">내가 방장이에요. 다른 사람에게는 보이지 않아요.</p>}
      {!connected && <p role="status">연결이 끊겨서 다시 연결하는 중이에요...</p>}
      {actionError && <p role="alert">{actionError}</p>}

      {room.status === 'WAITING' && (
        <WaitingPhase me={me} phaseDurations={room.phaseDurations} onStart={actions.nextPhase} />
      )}
      {room.status === 'PROPOSING' && <ProposingPhase room={room} me={me} actions={actions} />}
      {room.status === 'MERGING' && <MergingPhase room={room} me={me} actions={actions} />}
      {room.status === 'VOTING' && <VotingPhase menus={room.menus} myMenuId={me.menuId} />}
      {room.status === 'RANDOM_PICK' && <RandomPickPhase />}

      <CopyLinkButton />
    </main>
  )
}

function CopyLinkButton() {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
    } catch {
      window.prompt('아래 링크를 복사해서 보내주세요.', window.location.href)
    }
  }

  return (
    <button type="button" onClick={() => void copy()}>
      {copied ? '링크를 복사했어요' : '참여 링크 복사'}
    </button>
  )
}
