import type { RoomActions, RoomSnapshot } from '../../hooks/useRoom'
import type { Me } from '../../types/room'
import { NO_LIMIT } from '../../utils/duration'
import { Countdown } from '../Countdown'
import { PickForm, type PickTimeRule } from '../PickForm'

type Props = {
  room: RoomSnapshot
  me: Me
  actions: RoomActions
}

/** 뽑히지 않은 사람에게 보이는 안내 */
const WAITING_TEXT: Record<PickTimeRule, string> = {
  limited: '누군가 뽑혀서 메뉴를 정하고 있어요. 시간 안에 정하지 않으면 다른 사람이 뽑혀요.',
  stopped: '누군가 뽑혀서 메뉴를 정하고 있어요. 모두 한 번씩 뽑혀서, 이제는 자동으로 다시 뽑지 않아요.',
  noLimit: '누군가 뽑혀서 메뉴를 정하고 있어요.',
}

/**
 * 랜덤 선택: 아무도 메뉴를 내지 않았거나 아무도 투표하지 않았을 때. 서버가 참여자 중 한 명을 무작위로 뽑고, 그 사람이 정한다.
 * 누가 뽑혔는지는 뽑힌 본인만 안다(me.picking). 시간 안에 정하지 않으면 서버가 다른 사람을 다시 뽑는다.
 * 모두 한 번씩 뽑힌 뒤에는 자동으로 다시 뽑지 않고, 방장이 "다른 사람 뽑기"를 눌러야 처음부터 다시 뽑는다.
 * 방장이 랜덤 선택을 "제한 없음"으로 정했으면 처음부터 자동으로 다시 뽑지 않는다(다시 뽑기는 "다른 사람 뽑기"로만).
 */
export function RandomPickPhase({ room, me, actions }: Props) {
  // 메뉴가 하나도 없으면 제안 단계에서, 있으면 투표 단계에서 넘어온 것이다.
  const why = room.menus.length === 0 ? '아무도 메뉴를 제안하지 않아서' : '아무도 투표하지 않아서'
  const timeRule = pickTimeRule(room)

  return (
    <section>
      <Countdown room={room} />
      <p>
        {why}, 랜덤으로 뽑힌 한 사람이 메뉴를 정해요. 누가 뽑혔는지는 비밀이에요. ({room.pickTurn}번째 뽑기)
      </p>

      {me.picking ? (
        <PickForm menus={room.menus} timeRule={timeRule} onDecide={actions.decidePick} />
      ) : (
        <p>{WAITING_TEXT[timeRule]}</p>
      )}

      {me.host && (
        <>
          <p className="hint">
            {timeRule === 'stopped'
              ? '모두 한 번씩 뽑혔는데 아직 아무도 정하지 않았어요. 다른 사람 뽑기를 누르면 처음부터 다시 뽑아요.'
              : '뽑힌 사람이 자리에 없는 것 같으면 다른 사람을 뽑을 수 있어요.'}
          </p>
          {/* 방장은 메뉴를 정하지 않는다. 뽑힌 사람이 자리에 없을 때 다른 사람에게 넘기기만 한다. */}
          <button type="button" onClick={actions.nextPhase}>
            다른 사람 뽑기
          </button>
        </>
      )}
    </section>
  )
}

/**
 * 뽑힌 사람에게 걸린 시간 규칙.
 * 끝나는 시각이 없는 경우가 둘이라 단계별 시간으로 구분한다: 방장이 "제한 없음"으로 정한 방(noLimit)과,
 * 시간 제한이 있었지만 모두 한 번씩 뽑혀서 서버가 자동 다시 뽑기를 멈춘 방(stopped).
 */
function pickTimeRule(room: RoomSnapshot): PickTimeRule {
  if (room.phaseDurations.randomPickSeconds === NO_LIMIT) return 'noLimit'
  return room.phaseEndsAt === null ? 'stopped' : 'limited'
}
