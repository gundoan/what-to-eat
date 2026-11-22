/** 서버가 보낸 에러. code는 백엔드 ErrorCode 이름이다(예: ROOM_NOT_FOUND). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/**
 * 같은 주소(개발 중에는 Vite 프록시)로 요청을 보낸다.
 * 같은 주소라서 참여자 쿠키가 자동으로 붙는다.
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(
      response.status,
      problem?.code ?? 'UNKNOWN',
      problem?.detail ?? '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.',
    )
  }
  return (await response.json()) as T
}
