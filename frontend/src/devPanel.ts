/**
 * 개발용 여러 명 테스트 화면(dev-board.html)의 "칸" 주소 처리.
 *
 * 테스트 화면은 칸마다 이 앱을 /p/1/..., /p/2/... 주소로 띄운다. 앱은 그 앞부분(/p/N)을 화면 주소와 API 주소 앞에 붙이고,
 * 개발 서버(vite.config.ts)는 /p/N/api 요청을 백엔드로 넘기면서 쿠키 경로 앞에 /p/N을 붙여 준다.
 * 브라우저는 쿠키를 경로마다 따로 보관하므로, 한 탭 안에서도 칸마다 다른 참여자가 된다.
 *
 * 배포용 빌드에서는 import.meta.env.DEV가 false로 바뀌어 panelBase가 항상 빈 문자열이 되고, 칸을 찾는 코드는 빌드 결과에서 빠진다.
 * CI(.github/workflows/ci.yml)가 배포용 빌드에 아래 정규식(p\/\d+)이 없는지 검사한다. 정규식을 바꾸면 CI 검사도 같이 바꾼다.
 */
export const panelBase: string = import.meta.env.DEV ? panelBaseOf(window.location.pathname) : ''

/** "/p/3/rooms/abc" → "/p/3". 칸 주소가 아니면 빈 문자열 */
function panelBaseOf(pathname: string): string {
  return /^\/p\/\d+(?=\/|$)/.exec(pathname)?.[0] ?? ''
}
