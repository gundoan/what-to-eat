import type { Me } from '../types/room'

type Props = {
  me: Me
  onFinish: () => void
}

/** 합치기 단계에서 내 메뉴가 어떻게 됐는지와 "합치기 끝냄" 버튼. */
export function MyMergeStatus({ me, onFinish }: Props) {
  if (me.proposal === null) {
    return <p>메뉴를 내지 않아서 합칠 게 없어요. 투표를 기다려 주세요.</p>
  }
  if (me.mergedInto !== null) {
    return (
      <p>
        내 메뉴({me.proposal})가 &apos;{me.mergedInto}&apos;(으)로 합쳐졌어요. 투표만 하면 돼요.
      </p>
    )
  }
  if (me.done) {
    return <p>합치기를 끝냈어요. 다른 사람을 기다리는 중이에요.</p>
  }
  return (
    <section>
      <p className="hint">더 합칠 메뉴가 없으면 눌러 주세요. 답하지 않은 질문은 &apos;합치지 않기&apos;로 처리돼요.</p>
      <button type="button" onClick={onFinish}>
        합치기 끝냄
      </button>
    </section>
  )
}
