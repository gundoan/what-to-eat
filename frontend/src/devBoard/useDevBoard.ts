import { useEffect, useRef, useState } from 'react'

/**
 * 칸은 4개까지만 둔다. HTTP/1.1에서 브라우저는 한 주소(localhost:5173)에 연결을 6개까지만 열고,
 * 칸마다 SSE가 연결 하나를 계속 쓴다. 칸이 6개면 연결이 모두 SSE에 묶여서 제안이나 투표 요청이 나가지 못한다.
 */
export const MAX_PANELS = 4

export type Panel = {
  /** 칸 번호(1부터) */
  number: number
  /** 칸에 띄운 주소. null이면 닫혀 있다 */
  src: string | null
  /** 주소를 새로 띄울 때마다 바뀐다. iframe의 key로 써서, 같은 주소라도 처음부터 다시 연다 */
  version: number
}

type BoardState = {
  /** 보이는 칸 수(1~MAX_PANELS) */
  count: number
  /** 칸들이 함께 쓰는 방 코드. 칸 1에서 방을 만들면 채워진다 */
  code: string | null
  panels: Panel[]
}

/**
 * 여러 명 테스트 화면의 로직.
 * 1) 칸 1에서 방을 만들면 그 방 코드를 알아낸다(칸 1의 주소를 0.5초마다 본다).
 * 2) 다른 칸을 같은 방에 들여보내거나, 칸을 닫고(나가기) 다시 열고, 새로고침한다.
 * 3) 칸 수, 방 코드, 열린 칸을 주소(?n=4&room=...&open=1,2)에 적어서, 테스트 화면을 새로고침해도 그대로 돌아온다.
 *
 * 칸 N은 앱을 /p/N/... 주소로 띄운다. 그래서 칸마다 쿠키가 따로 저장되어 다른 참여자가 된다(devPanel.ts 참고).
 */
export function useDevBoard() {
  const [state, setState] = useState<BoardState>(initialState)
  const frames = useRef(new Map<number, HTMLIFrameElement>())

  useEffect(() => {
    const timer = window.setInterval(() => {
      const path = frames.current.get(1)?.contentWindow?.location.pathname ?? ''
      const found = /^\/p\/1\/rooms\/([^/]+)/.exec(path)?.[1]
      if (found === undefined) return
      const code = decodeURIComponent(found)
      setState((current) => {
        if (current.code === code) return current
        // 칸 1이 새 방을 만들었다. 다른 칸은 전에 있던 방에 있으니 닫는다.
        return { ...current, code, panels: current.panels.map((panel) => (panel.number === 1 ? panel : close(panel))) }
      })
    }, 500)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const params = new URLSearchParams({ n: String(state.count) })
    if (state.code !== null) {
      params.set('room', state.code)
      params.set('open', openNumbers(state.panels).join(','))
    }
    window.history.replaceState(null, '', `?${params}`)
  }, [state])

  function setCount(count: number) {
    // 줄인 칸은 닫는다. 그 칸의 참여자는 창을 닫고 나간 것과 같다.
    setState((current) => ({
      ...current,
      count,
      panels: current.panels.map((panel) => (panel.number > count ? close(panel) : panel)),
    }))
  }

  function joinAll() {
    setState((current) => {
      const { code, count } = current
      if (code === null) return current
      return {
        ...current,
        panels: current.panels.map((panel) =>
          panel.number <= count && panel.src === null ? open(panel, roomUrl(panel.number, code)) : panel,
        ),
      }
    })
  }

  function join(number: number) {
    setState((current) => {
      const { code } = current
      if (code === null) return current
      return { ...current, panels: replace(current.panels, number, (panel) => open(panel, roomUrl(number, code))) }
    })
  }

  function closePanel(number: number) {
    setState((current) => ({ ...current, panels: replace(current.panels, number, close) }))
  }

  /** 칸 안에서 다른 주소로 옮겨 갔을 수 있으니(방 만들기 → 방 화면), 지금 주소 그대로 새로고침한다. */
  function reload(number: number) {
    frames.current.get(number)?.contentWindow?.location.reload()
  }

  /** 칸 1만 방 만들기 화면으로 돌아가고 나머지 칸은 닫는다. 새 방에서는 쿠키도 새로 받아서 모두 새 참여자가 된다. */
  function restart() {
    setState((current) => ({
      ...current,
      code: null,
      panels: current.panels.map((panel) => (panel.number === 1 ? open(panel, homeUrl(1)) : close(panel))),
    }))
  }

  /** 칸의 iframe을 기억해 두는 ref 콜백. 칸 1의 주소를 보거나 칸을 새로고침할 때 쓴다. */
  function frameRef(number: number) {
    return (frame: HTMLIFrameElement | null) => {
      if (frame === null) {
        frames.current.delete(number)
      } else {
        frames.current.set(number, frame)
      }
    }
  }

  return {
    count: state.count,
    code: state.code,
    panels: state.panels.filter((panel) => panel.number <= state.count),
    /** 같은 방을 다른 브라우저(칸 밖)에서 열 주소. 인터넷 끊기처럼 칸 하나만으로 못 하는 테스트에 쓴다 */
    outsideUrl: state.code === null ? null : `${window.location.origin}/rooms/${encodeURIComponent(state.code)}`,
    setCount,
    joinAll,
    join,
    close: closePanel,
    reload,
    restart,
    frameRef,
  }
}

function initialState(): BoardState {
  const params = new URLSearchParams(window.location.search)
  const count = Math.min(Math.max(Math.trunc(Number(params.get('n') ?? MAX_PANELS)) || MAX_PANELS, 1), MAX_PANELS)
  const code = params.get('room')
  const opened = new Set((params.get('open') ?? '').split(',').map(Number))
  const panels = Array.from({ length: MAX_PANELS }, (_, i): Panel => {
    const number = i + 1
    return { number, src: initialSrc(number, count, code, opened), version: 0 }
  })
  return { count, code, panels }
}

function initialSrc(number: number, count: number, code: string | null, opened: Set<number>): string | null {
  if (number > count) return null
  if (code === null) return number === 1 ? homeUrl(1) : null
  return opened.has(number) ? roomUrl(number, code) : null
}

function homeUrl(number: number): string {
  return `/p/${number}/`
}

function roomUrl(number: number, code: string): string {
  return `/p/${number}/rooms/${encodeURIComponent(code)}`
}

function open(panel: Panel, src: string): Panel {
  return { ...panel, src, version: panel.version + 1 }
}

function close(panel: Panel): Panel {
  return { ...panel, src: null }
}

function replace(panels: Panel[], number: number, change: (panel: Panel) => Panel): Panel[] {
  return panels.map((panel) => (panel.number === number ? change(panel) : panel))
}

function openNumbers(panels: Panel[]): number[] {
  return panels.filter((panel) => panel.src !== null).map((panel) => panel.number)
}
