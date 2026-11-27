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
        {board.code === null ? (
          <span>방을 만들면 방 코드가 여기에 나와요.</span>
        ) : (
          <span>
            방 코드 <strong>{board.code}</strong>
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
          {board.outsideUrl !== null && (
            <>
              {' '}
              다른 브라우저에서 같은 방 열기: <code>{board.outsideUrl}</code>
            </>
          )}
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
