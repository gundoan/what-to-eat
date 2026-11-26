import type { Me, MergeGroup } from '../types/room'
import { requestParts } from '../utils/mergeGroup'

type Props = {
  me: Me
  /** 내가 보내고 답을 기다리는 직접 요청 */
  sentRequest: MergeGroup | undefined
  onFinish: () => void
}

/** 합치기 단계에서 내 메뉴가 어떻게 됐는지와 "합치기 끝냄" 버튼. */
export function MyMergeStatus({ me, sentRequest, onFinish }: Props) {
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
  if (sentRequest) {
    // 보낸 요청의 답은 상대가 할 일이고 취소도 안 되므로, 답이 나올 때까지 끝낼 수 없다.
    return (
      <section>
        <p>
          내 메뉴를 &apos;{requestParts(sentRequest)?.to.name}&apos;에 합치자고 요청했어요. 답을 기다리는 중이에요.
        </p>
        <p className="hint">답이 나오면 합치기를 끝낼 수 있어요.</p>
        <button type="button" disabled>
          합치기 끝냄
        </button>
      </section>
    )
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
