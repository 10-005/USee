# Backend

Kotlin + Spring Boot 멀티모듈 모노레포. 버전은 `gradle/libs.versions.toml` 한 곳에서 관리한다.

| 모듈 | 포트 | API 접두어 | 설명 |
|---|---|---|---|
| `common` | - | - | 공통 응답·예외·인증·직렬화 (실행 불가 라이브러리) |
| `core-api` | 8080 | `/api/core/v1` | 회원·신고·보호·입양 등 핵심 API |
| `chat` | 8081 | `/api/chat/v1` | 채팅 (STOMP) |
| `notification` | 8082 | `/api/notification/v1` | 알림 |
| `simulation` | 8083 | `/api/simulation/v1` | 모의 입양 |

기술 스택: JDK 21, Spring Boot 4.1, Kotlin 2.3, JPA + QueryDSL(OpenFeign 포크, KSP), PostgreSQL, Redis, Spring Security, springdoc-openapi

## 로컬 실행

모든 명령은 `backend/`에서 실행한다.

```bash
# 1. 환경 변수
cp .env.example .env

# 2. JWT 테스트 키 쌍 생성 (.keys/는 git에 올라가지 않음)
mkdir -p .keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .keys/jwt-private.pem
openssl pkey -in .keys/jwt-private.pem -pubout -outform DER | base64 | tr -d '\n'
# 출력값을 .env의 JWT_PUBLIC_KEY= 뒤에 붙여 넣는다
openssl pkcs8 -topk8 -nocrypt -in .keys/jwt-private.pem -outform DER | base64 | tr -d '\n'
# 출력값을 .env의 JWT_PRIVATE_KEY= 뒤에 붙여 넣는다(core-api 발급용, PKCS#8. `pkey -outform DER`은 PKCS#1이라 안 된다)

# 3. PostgreSQL, Redis 실행 (최초 실행 시 core·simulation 스키마 생성)
docker compose up -d

# 4. 서비스 실행 (기본 프로필 local)
./gradlew :core-api:bootRun
```

- 빌드·테스트: `./gradlew build`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (서비스별 포트)
- 헬스 체크: `/actuator/health`, k8s probe는 `/actuator/health/liveness`, `/actuator/health/readiness`. Ingress는 `/api/**`만 라우팅하므로 외부에 노출되지 않는다.
- 스키마 초기화 SQL은 볼륨이 비어 있을 때만 실행된다. 처음부터 다시 만들려면 `docker compose down -v`.
- core-api는 기동 시 Flyway 마이그레이션을 먼저 적용한다. 예전에 `ddl-auto`로 만든 `images` 테이블이 남아 있으면 V1이 실패하므로 `docker compose down -v`로 초기화한다.

## Docker 이미지

빌드 컨텍스트는 `backend/`다.

```bash
docker build -f core-api/Dockerfile -t core-api .
```

## 패키지 구조 규칙

모듈별 베이스 패키지: `com.ssafy.animal.common`, `.core`, `.chat`, `.notification`, `.simulation`

실용적 헥사고날 구조를 도메인 단위로 적용한다. 예시는 `core-api`의 `sample` 도메인을 참고한다.

```text
com.ssafy.animal.core.{도메인}
├── domain                       # JPA 엔티티 = 도메인 모델, 도메인 이벤트, {도메인}ErrorCode
├── application
│   ├── port
│   │   ├── in                   # 유스케이스 인터페이스, Command
│   │   └── out                  # 저장소·외부 기술 포트 인터페이스
│   └── service                  # 유스케이스 구현 (@Transactional)
└── adapter
    ├── in/web                   # 컨트롤러, 요청/응답 DTO
    └── out
        ├── persistence          # 포트 구현 → JpaRepository/QueryDSL 위임
        └── messaging            # MQ 등 메시지 발행 어댑터
```

- JPA 엔티티를 도메인 모델로 그대로 쓴다. 도메인/엔티티 분리와 매퍼는 두지 않는다.
- 서비스는 `port/out` 인터페이스에만 의존한다. 저장소는 `adapter/out/persistence`에서 `JpaRepository`에 위임하고, 복잡한 조회는 QueryDSL(`Q{엔티티}`)로 작성한다.
- MQ, S3, FCM, ai-matching, 다른 서비스 호출 같은 외부 기술은 반드시 `port/out` + `adapter/out`으로 격리한다.
- 이벤트 발행은 `common`의 `EventPublisher.publish(이벤트)`만 쓴다. 이벤트 클래스는 `common.event`의 `Events.kt`(API 명세 21.6). 트랜잭션 안에서 부르면 커밋 후에만 나가고 롤백이면 버려진다. `EVENTS_TRANSPORT=rabbit`이면 RabbitMQ 교환기 `animal.events`(라우팅 키 = 클래스 이름), 기본값 `log`는 로그만 남긴다. 소비 쪽은 자기 큐를 선언해 묶고, 리스너는 `@ConditionalOnProperty(events.transport=rabbit)` 아래에 둔다.
- 도메인 에러 코드는 `domain` 패키지에 `ErrorCode`를 구현하는 enum으로 만든다(`{도메인}_상황`, 예: `SampleErrorCode.SAMPLE_NOT_FOUND`). common에는 `ErrorCode` 인터페이스와 공통 코드(`CommonErrorCode`)만 둔다.
- 엔티티는 `BaseTimeEntity`(`regDttm` + `modDttm`, `modDttm`은 INSERT 때 NULL·UPDATE 때만 채움)를, `mod_dttm` 컬럼이 없는 테이블은 `BaseCreatedEntity`(`regDttm`만)를 상속한다(DB 명세 Q430). ID 필드는 `{엔티티}Seq`.
- 테이블은 Flyway SQL로 만든다. core는 `core-api/src/main/resources/db/migration`, simulation은 `simulation/src/main/resources/db/migration`. chat·notification은 core 스키마를 쓰고 `ddl-auto=validate`(테이블을 만들지 않음).
- 새 마이그레이션은 `V{yyyyMMdd_HHmm}__{설명}.sql`로 추가한다(병렬 브랜치 충돌 방지, `out-of-order` 켜짐). 적용된 파일은 수정 금지.
- 개발·시연용 더미 데이터는 `core-api/src/main/resources/db/dev`에 둔다. local·dev 프로필에서만 적용된다.
- Kotlin에서 `in`은 키워드라 패키지 선언에 백틱이 필요하다: ``package ...port.`in` ``

## common 모듈

서비스는 `implementation(project(":common"))`만 추가하면 AutoConfiguration으로 아래가 적용된다.

### 공통 응답

```json
{ "status": 200, "code": "SUCCESS", "message": "요청이 성공했습니다.", "data": {}, "errors": [], "traceId": "..." }
```

- 컨트롤러는 DTO만 반환한다. `ApiResponseBodyAdvice`가 실제 HTTP 상태를 읽어 봉투를 씌운다. 상태 코드는 `@ResponseStatus(HttpStatus.CREATED)` 등으로 지정한다.
- 성공 메시지를 바꿀 때만 `@SuccessMessage("...")`를 붙인다.
- 반환값이 없는 API(`Unit`)는 `200` + `data: null`. 204는 쓰지 않는다.
- 목록은 `PageResponse.of(page)`로 변환한다: `{ content, page, size, totalElements, totalPages, hasNext }`
- 정렬은 `SortPolicy`로 처리한다(명세 Q417). 엔티티 필드명을 그대로 정렬에 쓰지 않는다.
  1. 기본: 명세에 화이트리스트가 따로 없으면 `regDttm`만 허용이므로 도우미 한 줄로 끝낸다.
     `private val sortPolicy = SortPolicy.regDttmOnly(entity.regDttm, entity.entitySeq)`
  2. 명세에 화이트리스트가 있으면 직접 선언한다(`SamplePersistenceAdapter` 참고). API 필드 이름 → Q경로, 기본 정렬, 동률 처리(PK desc).
  3. QueryDSL에서 `.orderBy(*sortPolicy.toOrderSpecifiers(pageable.sort))`. 허용 안 된 필드는 400 `COMMON_INVALID_INPUT`(`errors[].field = "sort"`), sort가 없으면 기본 정렬

### 예외

- 서비스에서는 `throw BusinessException(SampleErrorCode.SAMPLE_NOT_FOUND)`처럼 `ErrorCode` 구현체만 던진다. 응답 `code`는 enum 상수 이름, HTTP 상태와 기본 메시지는 enum 값에서 가져온다.
- 공통 코드(`CommonErrorCode`): `COMMON_INVALID_INPUT`, `COMMON_INVALID_JSON`, `AUTH_UNAUTHORIZED`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`, `AUTH_FORBIDDEN`, `COMMON_NOT_FOUND`, `COMMON_METHOD_NOT_ALLOWED`, `COMMON_INTERNAL_ERROR`, `COMMON_SERVICE_UNAVAILABLE`
- 실패 응답은 `data: null`, `errors: [{ field, reason }]`. 500은 내부 메시지를 노출하지 않는다.

### 인증

- `Authorization: Bearer {accessToken}` (RS256). 공개키로 검증만 한다.
- 클레임: `userSeq`, `role`(`USER`/`SHELTER`), SHELTER는 `shelterSeq`
- 컨트롤러에서 `@AuthenticationPrincipal user: AuthUser`로 받는다.
- 인증 없이 열 경로는 서비스 설정의 `security.permit-paths`에 추가한다. (`/actuator/health/**`, Swagger는 기본 허용)
- STOMP는 `StompAuthChannelInterceptor`가 CONNECT 프레임의 `Authorization` 헤더를 검증한다. (`chat`의 `WebSocketConfig`)

### 직렬화

camelCase, enum은 문자열, null 필드 포함, 날짜는 `OffsetDateTime`을 ISO-8601 오프셋 포함(`2026-11-02T10:00:00+09:00`, Asia/Seoul)으로 내보낸다.

### TraceId

`X-Trace-Id` 요청 헤더가 있으면 이어받고 없으면 생성한다. 응답 헤더, 응답 바디 `traceId`, 로그(MDC)에 같은 값이 들어간다.
