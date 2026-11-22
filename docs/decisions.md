# 기술 결정 기록

중요한 기술 결정을 "문제 → 고려한 선택지 → 선택한 방법과 이유 → 결과" 순서로 적는다.
누가 골랐는지도 함께 적는다. 직접 고르지 않은 결정도 나중에 공부할 수 있게 다른 선택지와 같이 남긴다.

---

## 1. Java·Spring Boot 버전

- 날짜: 2026-11-22
- 고른 사람: 직접 선택

**문제**: 최신 버전을 쓸지, 학습 자료가 많은 버전을 쓸지.

**고려한 선택지**

| 선택지 | 장점 | 단점 |
|---|---|---|
| Spring Boot 4.2.0 + Java 25 | 무료 지원이 2027-12-31까지로 가장 길다. Java 25는 최신 LTS | 국내 자료 상당수가 3.x 기준이다. 4.2.0은 2026-11-19에 나온 버전이다 |
| Spring Boot 3.5.16 + Java 21 | 국내 강의·블로그 대부분이 이 기준이다 | 무료 지원이 2026-06-30에 끝났다(3.5.16이 마지막 무료 패치). Spring Initializr에서 더 이상 만들 수 없다 |

**선택과 이유**: Spring Boot 4.2.0 + Java 25. 실제로 배포할 서비스라 보안 패치가 계속 나오는 버전이 필요하다. 공부할 핵심 개념(HTTP API, JPA, 트랜잭션, 테스트)은 3.x와 같다.

**결과: 3.x 자료를 볼 때 다른 점** (새로 발견할 때마다 추가한다)

| 3.x 자료 | 이 프로젝트 (4.2) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| 테스트 의존성은 `spring-boot-starter-test` 하나 | 기능별 테스트 스타터 (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` 등) |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| Flyway는 `flyway-core` + `flyway-mysql` | `spring-boot-starter-flyway` + `flyway-mysql` |
| `@MockBean` | 없어졌다. Spring Framework의 `@MockitoBean`을 쓴다 |
| JUnit 5 | JUnit 6.1 (`@Test` 같은 쓰는 법은 같다) |
| Testcontainers 1 (`org.testcontainers:mysql`, `org.testcontainers.containers.MySQLContainer`) | Testcontainers 2 (`testcontainers-mysql`, `org.testcontainers.mysql.MySQLContainer`) |

---

## 2. 비밀 정보(DB 비밀번호) 관리

- 날짜: 2026-11-22
- 고른 사람: 직접 선택

**문제**: DB 비밀번호를 깃에 올리지 않으면서, 로컬에서 docker compose와 Spring Boot가 같은 값을 써야 한다.

**고려한 선택지**

1. 루트의 `.env` 파일 하나를 docker compose와 Spring Boot가 같이 읽는다.
2. `application-local.yml`(깃 제외)에 둔다. 국내 자료에서 가장 흔하지만, 같은 비밀번호를 compose용 파일에도 적어서 두 곳을 맞춰야 한다.
3. IDE 실행 설정에 환경 변수를 넣는다. 파일은 없지만, 설정이 IDE 안에 숨어 있어서 다시 세팅하기 번거롭다.

**선택과 이유**: 1번. 비밀번호를 한 곳에만 적으면 된다. 배포 서버에서도 같은 이름의 환경 변수를 넣으면 되므로 로컬과 배포의 구조가 같다.
- docker compose는 같은 폴더의 `.env`를 자동으로 읽는다.
- Spring Boot는 `spring.config.import: optional:file:../.env[.properties]`로 읽는다. 파일이 없으면(CI, 배포 서버) 건너뛴다.

**결과**: `git check-ignore`로 `.env`가 깃에서 빠지는 것을 확인했다. 깃에는 값이 빈 `.env.example`만 있다. 로컬 비밀번호는 무작위 24자로 만들었다.

---

## 3. 로컬 DB와 테스트 DB

- 날짜: 2026-11-22
- 고른 사람: 직접 선택

**문제**: 개발과 테스트에 쓸 MySQL을 어떻게 띄울지. "한 메뉴에 2표" 같은 규칙 위반을 DB 제약 조건과 동시 요청까지 실제와 같게 검증해야 한다.

**고려한 선택지**

1. Docker: 로컬은 Docker Compose로 MySQL을 띄우고, 테스트는 Testcontainers가 테스트마다 실제 MySQL 컨테이너를 띄운다.
2. MySQL을 Windows에 직접 설치하고, 테스트는 H2(메모리 DB)로 한다.

**선택과 이유**: 1번. H2는 MySQL과 잠금, 제약 조건, SQL 문법이 조금씩 달라서 정합성 테스트 결과가 실제와 어긋날 수 있다. MySQL 버전을 파일에 적어두면 내 PC, CI, 배포 서버가 모두 같은 MySQL을 쓴다. 배포도 Docker Compose라 미리 익숙해진다.

**대가**: 테스트를 돌릴 때 Docker가 켜져 있어야 하고, 컨테이너를 띄우는 시간만큼 테스트가 느리다.

---

## 4. 테이블 관리 방법

- 날짜: 2026-11-22
- 고른 사람: Claude가 제안, 승인함

**문제**: 테이블을 어떻게 만들고 바꿀지.

**고려한 선택지**

1. Flyway: 테이블 변경 이력을 SQL 파일(`V1__...sql`)로 남기고, 서버가 켜질 때 아직 적용 안 된 파일만 순서대로 실행한다.
2. JPA 자동 생성(`ddl-auto: create` 또는 `update`)
3. SQL을 손으로 실행

**선택과 이유**: 1번. JPA는 테이블을 만들지 않고 엔티티와 맞는지 검사만 한다(`ddl-auto: validate`).
- 자동 생성은 운영 DB를 의도치 않게 바꿀 수 있다.
- 손으로 실행하면 환경마다 테이블 구조가 달라지기 쉽다.
- SQL 파일이 PR에 그대로 보여서 리뷰하기 쉽다.

**대가**: 테이블을 바꿀 때마다 SQL 파일을 새로 써야 한다.

---

## 5. MySQL 버전

- 날짜: 2026-11-22
- 고른 사람: Claude가 고름

**고려한 선택지**: 9.7 LTS, 8.4 LTS, 26.x Innovation

**선택과 이유**: 9.7. 현재 LTS(장기 지원) 버전이다.
- 8.4는 자료가 조금 더 많지만 지원이 먼저 끝난다. 쓰는 법은 거의 같다.
- Innovation 버전은 다음 버전이 나올 때까지만 지원돼서 몇 달마다 바꿔야 한다.

---

## 6. 웹 방식: Spring MVC와 WebFlux

- 날짜: 2026-11-22
- 고른 사람: Claude가 고름

**문제**: SSE로 방 참여자 모두에게 실시간으로 알려야 한다.

**고려한 선택지**

1. Spring MVC + `SseEmitter`
2. Spring WebFlux (반응형, `Flux`로 SSE)

**선택과 이유**: 1번. JPA는 블로킹 방식이라 MVC와 자연스럽게 맞고, 자료도 많다. `SseEmitter`는 연결마다 요청 스레드를 붙잡지 않으므로, 서버 한 대에 방 단위의 작은 규모라면 충분하다.

**같이 정한 것**: `spring.jpa.open-in-view: false`. 켜두면 SSE처럼 오래 열린 요청이 끝날 때까지 DB 커넥션을 붙잡을 수 있다.

**나중에 다시 볼 것**: 서버를 여러 대로 늘리면 서버끼리 이벤트를 전달할 방법(예: Redis Pub/Sub)이 필요하다.

---

## 7. 폴더 구조와 프론트 서빙

- 날짜: 2026-11-22
- 고른 사람: Claude가 고름 (폴더 분리는 CLAUDE.md 요구사항)

- `frontend/`와 `backend/`를 처음부터 나눴다. 나중에 따로 배포하기 쉽게 하기 위해서다.
- 개발 중에는 Vite 개발 서버(5173)가 `/api` 요청을 Spring Boot(8080)로 넘긴다. 브라우저에는 같은 주소로 보여서 CORS 설정이 필요 없다.
- 배포 때 빌드한 프론트를 Spring Boot에 넣는 방법은 배포 단계에서 정한다. 후보는 세 가지다.
  - Gradle 빌드가 npm 빌드를 같이 실행한다.
  - Docker 멀티 스테이지 빌드에서 합친다.
  - Vite 빌드 결과를 backend의 static 폴더로 바로 출력한다.
- 프론트 린터는 템플릿 기본값인 Oxlint 대신 자료가 많은 ESLint를 골랐다.

---

## 8. Gradle 빌드 스크립트 문법

- 날짜: 2026-11-22
- 고른 사람: Claude가 고름

**고려한 선택지**: Groovy(`build.gradle`), Kotlin(`build.gradle.kts`)

**선택과 이유**: Groovy. 국내 예제가 더 많아서 자료를 따라 하기 쉽다. Kotlin은 자동 완성과 타입 검사가 더 좋고 Gradle의 새 기본값이라, 익숙해진 뒤 바꿀 수 있다.

---

## 9. Git·PR 작업 방식

- 날짜: 2026-11-22
- 고른 사람: Claude가 제안, 승인함

- 머지는 merge commit 방식만 허용한다(squash, rebase는 꺼둠). 작은 커밋 기록이 main에 그대로 남아 나중에 공부하기 좋다.
- main은 보호 규칙(ruleset)을 걸었다. PR 없이 바꿀 수 없고, 강제 push와 삭제도 막혀 있다.
- 머지된 브랜치는 자동으로 지운다.
- 커밋 작성자 이메일은 GitHub noreply 주소를 쓴다. 공개 레포의 커밋 기록에 개인 이메일이 남지 않는다.

---

## 10. 단계 시간 (서비스 규칙 변경)

- 날짜: 2026-11-22
- 고른 사람: 직접 결정. 세부 규칙은 Claude가 제안하고 승인함

**문제**: 처음 규칙은 모든 단계가 1분으로 고정이었다. 모임마다 필요한 시간이 다르다.

**선택**: 방장이 방을 만들 때 단계별 시간을 정한다. 정하지 않으면 모두 1분이다.

**세부 규칙과 이유**

- 정하는 시간은 제안, 합치기, 투표, 랜덤 선택 네 가지다. 재투표는 투표와 규칙이 같아서 투표 시간을 같이 쓴다. 설정 칸을 줄이기 위해서다.
- 범위는 30초~10분, 30초 단위다. 0초나 하루 같은 값을 막기 위해서다.
- 방을 만든 뒤에는 바꿀 수 없다. 진행 중에 시간이 바뀌면 참여자 화면의 남은 시간이 갑자기 달라져서 혼란스럽다.

**결과**: 기능 1(방 만들기)에서 구현한다.
