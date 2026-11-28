import { useState } from 'react'
import { MAX_PANELS, QUICK_ROOMS, useDevBoard } from './useDevBoard'

const COUNT_OPTIONS = Array.from({ length: MAX_PANELS }, (_, i) => i + 1)

/**
 * 개발용 여러 명 테스트 화면. 한 탭 안의 칸마다 앱을 띄우고, 칸마다 다른 참여자가 된다.
 * dev-board.html에서만 쓰고, 배포용 빌드에는 들어가지 않는다.
 */
export function DevBoard() {
  const board = useDevBoard()

  return (
    <div className="board">
      <header>
        <h1>여러 명 테스트 화면 (개발용)</h1>
        <label>
          칸 수{' '}
          <select value={board.count} onChange={(event) => board.setCount(Number(event.target.value))}>
            {COUNT_OPTIONS.map((count) => (
              <option key={count} value={count}>
                {count}
              </option>
            ))}
          </select>
        </label>
        <span className="quick">
          빠른 방 만들기
          {QUICK_ROOMS.map((quick) => (
            <button key={quick.seconds} type="button" onClick={() => void board.quickCreate(quick.seconds)}>
              {quick.label}
            </button>
          ))}
        </span>
        <button type="button" onClick={board.restart}>
          방 만들기 화면으로
        </button>
        {board.outsideUrl === null ? (
          <span>방을 만들면 방 주소가 여기에 나와요.</span>
        ) : (
          <span className="room-url">
            방 주소 <strong>{board.outsideUrl}</strong>
            {/* 방이 바뀌면 "복사했어요"를 지우도록, 주소를 key로 준다. */}
            <CopyButton key={board.outsideUrl} text={board.outsideUrl} />
          </span>
        )}
        <button type="button" disabled={board.code === null} onClick={board.joinAll}>
          모든 칸 들어가기
        </button>
        {board.createError !== null && (
          <p role="alert" className="hint">
            {board.createError}
          </p>
        )}
        <p className="hint">
          칸마다 다른 참여자예요.
          {board.outsideUrl !== null &&
            ' 칸 밖에서 같은 방을 열려면 방 주소를 엣지나 크롬 시크릿 창에 붙여 넣으세요. 같은 크롬의 새 탭은 연결 수 제한 때문에 멈출 수 있어요.'}
        </p>
      </header>

      <div className="panels" style={{ gridTemplateColumns: `repeat(${board.count}, minmax(0, 1fr))` }}>
        {board.panels.map((panel) => (
          <section key={panel.number} className="panel">
            <div className="panel-bar">
              <strong>칸 {panel.number}</strong>
              {panel.src === null ? (
                <button type="button" disabled={board.code === null} onClick={() => board.join(panel.number)}>
                  들어가기
                </button>
              ) : (
                <>
                  <button type="button" onClick={() => board.reload(panel.number)}>
                    새로고침
                  </button>
                  <button type="button" onClick={() => board.close(panel.number)}>
                    닫기
                  </button>
                </>
              )}
            </div>
            {panel.src === null ? (
              <p className="empty">
                {board.code === null
                  ? '칸 1에서 방을 만들면 들어갈 수 있어요.'
                  : '방에 들어가 있지 않아요. 들어가기를 누르면 참여해요. 전에 들어갔던 칸이면 같은 참여자로 돌아와요.'}
              </p>
            ) : (
              <iframe
                key={panel.version}
                ref={board.frameRef(panel.number)}
                src={panel.src}
                title={`칸 ${panel.number}`}
              />
            )}
          </section>
        ))}
      </div>
    </div>
  )
}

/** 방 주소 복사. 복사가 막히면(브라우저 권한 등) 주소를 직접 고를 수 있게 창으로 보여준다. */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      window.prompt('아래 주소를 복사하세요.', text)
    }
  }

  return (
    <button type="button" onClick={() => void copy()}>
      {copied ? '복사했어요' : '주소 복사'}
    </button>
  )
}
