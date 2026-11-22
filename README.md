# 뭐먹지?

단체(회사, 동아리, 친구 모임)에서 메뉴를 고를 때, 익명으로 메뉴를 제안하고 +1/-1로 투표해서 정하는 웹 서비스입니다.

> 지금 기능 1(방 만들기 + 링크로 참여)까지 만들었습니다. 나머지 기능은 하나씩 PR로 추가하고 있습니다.

## 주요 기능 (만드는 중)

1. ✅ **방 만들기 + 링크로 참여**: 회원가입이나 닉네임 없이 링크로 바로 익명 참여
   - 방장이 단계별 시간(30초~10분)을 정해서 방을 만들고, 참여 링크를 공유
   - 대기 화면에서 참여 인원이 실시간으로 늘어남 (누가 들어왔는지는 안 보임)
   - 새로고침해도 같은 참여자로 유지, 방 정원은 50명
2. **익명 메뉴 제안**: 한 사람이 메뉴 하나씩 제안, 원하지 않으면 "다 좋음"
3. **비슷한 메뉴 합치기**: AI가 비슷한 메뉴를 묶고, 제안자들이 동의하면 하나로 합침 (표가 갈라지는 문제 방지)
4. **투표**: 한 사람당 최대 2표, 각 표는 +1(먹고 싶다) 또는 -1(먹기 싫다)
5. **결과 + 재투표 + 랜덤 선택**: 동점이면 재투표, 그래도 동점이면 서버가 랜덤으로 결정

진행 단계와 화면은 SSE로 모든 참여자에게 실시간으로 갱신됩니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 백엔드 | Java 25, Spring Boot 4.2 (Spring MVC), Spring Data JPA, Gradle |
| DB | MySQL 9.7, Flyway (테이블 변경 이력 관리) |
| 테스트 | JUnit 6, Testcontainers (테스트할 때 실제 MySQL을 띄움) |
| 프론트 | React 19, TypeScript, Vite |
| 인프라 | Docker Compose, GitHub Actions (CI), AWS EC2 (배포 예정) |

기술을 고른 이유와 고려한 다른 선택지는 [docs/decisions.md](docs/decisions.md)에 정리했습니다.

## 폴더 구조

```
backend/       Spring Boot 서버
frontend/      React 화면
docs/          기술 결정 기록
compose.yaml   로컬 개발용 MySQL
.env.example   환경 변수 견본 (실제 값은 깃에 올리지 않는 .env에 둠)
```

## 로컬에서 실행하기

### 준비물

- JDK 25
- Node.js 26 이상
- Docker Desktop

### 1. 환경 변수 파일 만들기

`.env.example`을 복사해 같은 폴더에 `.env`를 만들고 비밀번호 두 개(`DB_PASSWORD`, `DB_ROOT_PASSWORD`)를 채웁니다. `.env`는 깃에 올라가지 않습니다.

### 2. MySQL 실행

```bash
docker compose up -d --wait
```

### 3. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

브라우저에서 http://localhost:8080/actuator/health 를 열어 `{"status":"UP"}`가 나오면 서버와 DB 연결이 정상입니다.

### 4. 프론트 실행

```bash
cd frontend
npm install
npm run dev
```

http://localhost:5173 에서 화면을 볼 수 있습니다. 개발 중에는 `/api` 요청이 자동으로 백엔드(8080)로 전달됩니다.

**써보기**: 방을 만든 뒤 "참여 링크 복사"로 링크를 복사해서 시크릿 창이나 다른 브라우저에 붙여넣으면, 처음 창의 참여 인원이 새로고침 없이 늘어납니다. 시크릿 창은 쿠키를 따로 쓰기 때문에 다른 참여자로 들어갑니다.

### 테스트

```bash
cd backend
./gradlew test
```

Docker Desktop이 켜져 있어야 합니다. 테스트가 테스트 전용 MySQL을 자동으로 띄우고 끝나면 지웁니다.

## API

| 요청 | 하는 일 |
|---|---|
| `POST /api/rooms` | 방 만들기. 본문에 단계별 시간(초)을 넣고, 안 넣으면 1분. 만든 사람이 방장이 되고 참여자 쿠키를 받음 |
| `POST /api/rooms/{code}/participants` | 링크로 참여. 이미 그 방 쿠키가 있으면 같은 참여자(200), 새로 들어오면 새 쿠키(201) |
| `GET /api/rooms/{code}` | 방 상태(단계, 시간, 참여 인원 수)와 본인이 방장인지 |
| `GET /api/rooms/{code}/events` | SSE. 연결하자마자 현재 방 상태, 이후 바뀔 때마다 새 방 상태 |

에러는 `{"status": 409, "detail": "방 정원이 가득 찼습니다.", "code": "ROOM_FULL"}`처럼 Spring 표준 형식(ProblemDetail)에 `code`를 붙여 보냅니다.
