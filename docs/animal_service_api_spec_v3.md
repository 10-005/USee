# 유실·발견동물·보호소·입양 플랫폼 API 명세서 — v3

> **작성일:** 2026-11-02 (최종 갱신 2026-11-03)  
> **근거:** `animal_service_db_spec_v10.md`(DB 통합 명세 v10, 현행 45개 테이블, 스키마 2개) + Q406·Q413~Q429·S1~S8 확정 + R1~R16·Q430~Q445(2026-11-02~03 검토, 20.1) + 백엔드 스켈레톤 코드(`backend/common` 응답·예외·인증 규약). 이전 버전 `animal_service_api_spec_v2.md`는 보존합니다.  
> **문서 목적:** Android·BE·AI·보호소 화면이 공유하는 REST API 계약. 공통 규약(1장)은 코드와 일치하며, 각 엔드포인트의 DTO는 구현 중 조정될 수 있는 [초안]입니다.  
> **백엔드 스택 [확정]:** JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, JPA + QueryDSL, PostgreSQL 17, Redis 7.4, RabbitMQ(STOMP relay + 서비스 간 이벤트), AWS S3(presigned PUT), 네이버 지도 역지오코딩. OpenSearch는 도입하지 않습니다(S7). 서비스 4개: core-api(8080) / chat(8081) / notification(8082) / simulation(8083).

**표기 규칙**
- **[확정 정책]**: DB v10에서 확인되는 기능·권한·상태·제약, 또는 Q406~Q429·S1~S8 질의응답으로 확정한 사항. **[가안]**: 추천안을 채택했고 수치는 코드 설정값으로 둔 사항(플레이 테스트·운영 후 조정). **[초안]**: DTO 필드 이름·예시 값·세부 검증 등 구현 중 조정 가능한 제안. 확정 정책과 초안이 충돌하면 DB v10과 1장 공통 규약이 우선합니다. **v3에는 [미정] 항목이 없습니다**(20장).
- 표의 `/...` 경로는 **해당 장의 서비스 접두어**를 앞에 붙인 상대 경로입니다. core-api `/api/core/v1`(2~13·15·16장), chat `/api/chat/v1`(14장), notification `/api/notification/v1`(17장), simulation `/api/simulation/v1`(18장). **[비인증]** 표시 외에는 로그인 필수입니다.
- 이 문서는 **클라이언트·보호소 업무 API**를 다룹니다. **ADMIN API는 이번 프로젝트 구현 범위 외**(Q410·Q411)이며 별도 장을 두지 않습니다. 내부 AI 워커 및 공공데이터 ETL 전용 API는 다루지 않습니다.

## v3 변경 요약 (v2 → v3)

| 번호 | 결정 | API 반영 |
|---|---|---|
| 구조 | Spring 서비스 4개, 서비스별 경로 접두어·포트. DB는 `core`(core-api·chat·notification 공유) + `simulation` 스키마 2개(Q420 D) | 머리말·1.1·14·17·18장·22장 |
| Q406 A | 실종·발견·보호소에 `districtCode`(법정동 코드 앞 5자리), 서버가 네이버 역지오코딩으로 도출. `districts` 시드 테이블 + `GET /districts`(Q406-1). 실패 시 NULL·최근접 배정·등록 거부 없음(Q406-2) | 5.5·7.1·7.5·8.1·8.4·8.5·9.1·21.1 |
| Q412 | 초기 데이터는 Flyway 시드 SQL(스키마마다 마이그레이션 하나) | 5장·12.1·18장 |
| Q413 A | 앱이 카카오 SDK access token 전달, 서버가 카카오 사용자 조회로 검증, 2단계 가입 유지 | 2.1·2.5 |
| Q414 A | `registrationToken`은 Redis `auth:register:{token}` TTL 10분, 가입 성공 시 삭제 | 2.1·2.5 |
| Q415 A | 봉투 `{status, code, message, data, errors, traceId}`, 204 미사용, 중복 등록 409, 디바이스 토큰 등록만 upsert 200 | 1.2·16.2·17.6 |
| Q416 A | 시각은 ISO-8601 `+09:00` | 1.1 |
| Q417 | 목록 `{content, page, size, totalElements, totalPages, hasNext}`, page 0부터, size 최대 100, 엔드포인트별 정렬 화이트리스트 + `asc/desc`, 필드 필터 | 1.1·1.4·각 목록 |
| Q418 A | 에러 `code` = enum 상수명, 규칙 `{도메인}_상황`, 공통 `COMMON_*`·`AUTH_*` | 1.3·19장·전 장 오류 코드 재명명 |
| Q419 A | 2.4 로그아웃 요청에 `fcmToken` 선택 필드 | 2.4·17.7 |
| Q421 A | 실종·발견 신고 작성은 USER만, 담당 SHELTER는 배정된 발견 신고 종료만 | 1.5·7·8 |
| Q422 A | 입양 DRAFT는 SUBMITTED 이후만 보호소 목록에 노출 | 12.9 |
| Q423 A | 게시물·댓글·채팅 수정·삭제는 작성자 본인만 | 6~8·13·14 |
| Q424 A | 사진 수정 `imageSeqs`는 전체 최종 배열, 서버가 diff·순서 계산 | 4.1·6.4·7.5·8.5·11.5 |
| Q425 B | CLOSED 신고는 읽기 전용, 수정하려면 ACTIVE로 재개 | 7.5·8.5 |
| Q426 A | 알림 생성 이벤트 최소 집합, SYSTEM은 유형만 예약 | 12.14·17.8 |
| Q427 A | 저장소 객체 삭제는 커밋 후 best-effort, 실패는 로그 | 4.1·13.5 |
| Q428 B | 15.2 신고 내역 조회 삭제(범위 외) | 15장 |
| Q429 A | 모의 입양 튜닝값은 코드 설정 [가안] 유지 | 18.0 |
| S1~S8 | 스택 확정(머리말). S4 presigned PUT + S3, S5·S6 RabbitMQ, S7 ILIKE 검색, S8 simulation 스케줄러 10분 | 4·7.1·8.1·9.1·11.1·14·18.0 |
| 인증 세부 | RS256, core-api 발급·전 서비스 공개키 검증, 클레임 `userSeq/role/shelterSeq`, STOMP CONNECT·SUBSCRIBE 인가 | 1.1·14장 |
| Q430~Q433 (2026-11-03) | `mod_dttm` NULL·`@PreUpdate`·베이스 엔티티 2개(Q430), 시드 `district_code`·이미지는 오프라인 준비 후 SQL 삽입(Q431), AI 워커 양방향 RabbitMQ 페이로드(Q432), refresh는 `typ=refresh` RS256 JWT + 모든 쓰기 요청에서 `is_deleted` 재확인(Q433). 문구 통일: simulation은 `device_tokens` 미접근, 알림 이벤트 `NotificationRequested`/`NotificationCreated` 구분, 세션 스냅샷은 이미지 키, SUBSCRIBE 인가는 chat 모듈 인터셉터 | 1.1·2.3·14·17.6·17.8·18.0·20.1·21.1·21.5·21.6 |
| 2차 검토 R1~R16 | 포크 가안 검토 결과. AI_MATCH 쓰기 예외 유지(R1), 세션 스냅샷 4컬럼(R2), 보호소 업무 정책(R3~R6, 전용 수정 API 없음), 삭제 댓글·채팅방 생성(R7·R8), **채팅 푸시는 FCM 데이터 메시지 + 앱 판단(R9 C)**, 8.8 필터(R10), 도메인 재분류 `catalog`·`user`(R11·R12), `assignmentMethod` 응답 전용(R13), `districts` 07번(R14), 고아 객체 1일(R15), **채팅 전송은 STOMP SEND(R16 B)** | 20.1·14·19장 |

## 0. 기준 및 적용 범위

### 0.1 MOTIF 명세서에서 참고한 작성 방식

| 참고한 요소 | 적용 방식 | 주의 |
|---|---|---|
| 도메인별 번호·API 인덱스 | 도메인별 엔드포인트 표 + 상세 항목 번호 | MOTIF의 도메인 경로를 그대로 복제하지 않음 |
| 응답 envelope | v3는 MOTIF 형식 대신 **백엔드 코드의 `ApiResponse`** `{status, code, message, data, errors, traceId}`를 사용 [확정 정책] | 1.2 참조 |
| 각 API의 요청·성공 `data`·실패 분기 | 대표 DTO, 상세 검증, 주요 에러 제시 | 모든 메시지·예시 ID는 가상 값 |
| page/size 규약 | 일반 목록은 offset(`PageResponse`), 채팅 메시지만 `beforeMessageSeq` 커서 [확정 정책] | 1.1·14.4 참조 |
| HTTP 의미 및 오류 코드 분리 | 200/201/400/401/403/404/409 구분, **204 미사용** [확정 정책] | 1.2 참조 |

### 0.2 DB v10이 API에 강제하는 규칙

1. **USER 한 명당 비삭제 `ACTIVE` 실종 신고는 최대 1건**입니다. 두 신고 모두 `CLOSED → ACTIVE` 재개가 가능합니다. **CLOSED 상태에서는 본문을 수정할 수 없고**(Q425), 수정하려면 먼저 ACTIVE로 재개합니다.
2. 발견 신고는 등록 시 담당 보호소를 자동 배정합니다. 서버가 좌표를 네이버 역지오코딩으로 **시군구 코드(`districtCode`, 법정동 코드 앞 5자리)**로 바꿔 `shelter_district_assignments` 매핑을 먼저 찾고, 없으면 거리상 최근접 보호소를 고릅니다(Q406 A). 역지오코딩 실패 시 `districtCode`는 NULL로 두고 최근접으로 배정하며 **등록을 거부하지 않습니다**(Q406-2). 등록·수정에 `shelterSeq`를 사용자가 지정하지 않고, 수동 재배정 API는 ADMIN 범위 외로 **없습니다**.
3. 구조 기록은 `PROCESSING/DONE/FAILED/CANCELED` 4종이며, **DONE 구조 1건에 보호동물 여러 마리**를 연결할 수 있습니다. 보호동물 구조 정보는 `rescueSeq`를 통해 조회하며 복사 저장하지 않습니다.
4. 보호동물은 **이름 컬럼이 없고** 첫 등록이 `PROTECTING+UNAVAILABLE`입니다. 반환 완료 시간은 `animal_returns.reg_dttm`을 사용합니다.
5. 입양 신청은 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` 6종이며, 동일 회원·동물의 비삭제 활성(DRAFT/SUBMITTED/APPROVED) 신청은 **통틀어 1건**입니다. `CLOSED` 상태는 없습니다.
6. 공통 입양 문항은 **현재 21개**, 답변은 `questionSeq`를 문자열 키로 하는 단일 JSON 객체입니다. 템플릿·버전·질문 이력·제출 당시 문항 스냅샷은 없습니다.
7. 댓글·채팅 이미지에는 각각 **`imageSeq` 단일 선택 필드(최대 1장)**를 사용합니다(채팅은 STOMP SEND 본문의 `imageSeq`, R16 B). 댓글의 부모 삭제 후 기존 답글은 유지합니다. 게시물·반려동물·보호동물의 사진 수정은 **`imageSeqs` 전체 최종 배열**로 보내고 서버가 diff와 순서를 계산합니다(Q424).
8. 사용자 차단·신고 심사·신고 내역 조회(Q428)·보호소 이관·입양 후 관리·AI 동일 동물 확정 링크 API와 **ADMIN 전용 API 전체**는 **범위 밖**입니다. 모의 입양은 **범위 안**(18장)입니다.
9. 알림 저장 유형은 `COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM` **5종**, 채팅 푸시는 DB 알림 기록을 남기지 않습니다. 생성 이벤트는 17.8의 최소 집합(Q426)이며 `SYSTEM`은 유형만 예약하고 이번 범위에서 발송하지 않습니다. 모의 입양 알림은 별도 테이블 `simulation_notifications`와 별도 API(18장)로 분리합니다.
10. `TIMESTAMPTZ`를 유지하고 DB 세션 시간대는 `Asia/Seoul`입니다. API는 `OffsetDateTime`을 ISO-8601 `+09:00`으로 직렬화합니다(Q416).
11. 프로필 이미지는 USER·SHELTER 모두 공통 `images`의 `imageSeq`를 참조합니다. 이미지는 **업로더 본인만** 자기 리소스에 연결할 수 있습니다(Q407·Q408).
12. refresh token은 **Redis TTL**로 관리하며 DB 테이블이 없습니다(Q409). 초기 데이터(보호소·SHELTER 계정·행정구역 마스터·배정·동물 카탈로그·입양 문항·모의 입양 상점/시나리오)는 API로 관리하지 않고 **Flyway 시드 SQL**로 적재합니다(Q412).
13. **DB는 하나, 스키마는 둘**입니다(Q420 D). `core` 스키마를 core-api·chat·notification이 공유하고(FK 그대로, 테이블마다 쓰는 서비스는 하나), 모의 입양 11개 테이블은 `simulation` 스키마에 있으며 core를 **읽기 전용 JOIN**으로만 참조합니다(교차 FK 없음). 세션은 시작 시 동물의 `shelterSeq`·표시 정보를 스냅샷합니다.

---

## 1. 공통 규약 (COMMON)

### 1.1 프로토콜·인증·명명

| 항목 | 명세 | 확정 수준 |
|---|---|---|
| Base URL | `https://{host}/api/{service}/v1`. `service`는 `core`(2~13·15·16장) / `chat`(14장) / `notification`(17장) / `simulation`(18장). 내부 포트 8080/8081/8082/8083, Ingress는 `/api/**`만 라우팅 | [확정 정책] 코드 |
| 요청 본문 | UTF-8 JSON; `camelCase`; enum은 문자열; 응답은 null 필드 포함 | [확정 정책] 코드 직렬화 규칙 |
| 식별자 | DB `*_seq` → API `*Seq` 숫자형. 클라이언트가 신규 DB PK 부여 금지 | [확정 정책] |
| 인증 헤더 | `Authorization: Bearer {accessToken}`. **RS256** JWT, core-api만 개인키로 발급하고 네 서비스 모두 공개키로 검증. access 클레임 `userSeq`, `role`(`USER`/`SHELTER`), SHELTER만 `shelterSeq`, `typ=access`. refresh도 같은 키로 서명한 JWT(`userSeq`·`jti`·`typ=refresh`)이며 검증기는 `typ != access`인 토큰을 API 인증에서 거부(Q433). refresh는 Redis rotation(2.3). 수명 access 1시간·refresh 14일 | [확정 정책] 구조 / [가안] 수명 |
| 추적 헤더 | 요청 `X-Trace-Id`가 있으면 이어받고 없으면 서버가 생성. 응답 헤더·봉투 `traceId`·로그에 같은 값 | [확정 정책] 코드 |
| 권한 | `USER` 일반회원 / `SHELTER` 소속 보호소 담당자. 역할은 메서드 단위 `@PreAuthorize`, 소유·소속은 서비스 코드에서 검증. `ADMIN`은 DB `users.role` CHECK에 예약값으로만 남고 **이번 범위에서 계정·권한·API 없음**(Q410·Q411). 즉시 차단 없음: 탈퇴·정지 뒤에도 access 토큰은 자연 만료(최대 1시간)까지 유효. 대신 **모든 쓰기 요청(POST·PATCH·DELETE·STOMP SEND)**은 처리 전에 `users.is_deleted`·`account_status`를 재확인하고 탈퇴 계정이면 401 `AUTH_TOKEN_INVALID`, 정지면 403 `AUTH_ACCOUNT_UNAVAILABLE`. 조회(GET·SUBSCRIBE)는 재확인 없이 만료까지 허용(Q433). 별도 "민감 API" 목록은 두지 않음 | [확정 정책] Q433 |
| 시간 | `OffsetDateTime` → ISO-8601 `+09:00`, 예: `2026-10-29T17:50:00+09:00` | [확정 정책] Q416 A |
| 날짜 | `YYYY-MM-DD`, 예: `2026-10-29` | [확정 정책] DATE 의미 |
| 페이지네이션 | `page=0&size=20`(0-base, size 기본 20·최대 100). 응답은 1.4의 `Page<T>` | [확정 정책] Q417 |
| 정렬 | `sort=필드,asc\|desc`. 엔드포인트마다 **허용 필드 화이트리스트**를 명시하고 기본값은 `regDttm,desc`(동률은 PK desc). 화이트리스트 밖 필드·방향은 400 `COMMON_INVALID_INPUT`. 채팅 메시지(14.4)만 커서 | [확정 정책] Q417 |
| 필드 필터 | 목록마다 허용 필터 파라미터를 명시(상태·종·품종·행정구역·보호소 등). 명시되지 않은 파라미터는 무시 | [확정 정책] Q417 |
| 멱등성 | 중복 등록은 409(도메인 코드). 예외로 디바이스 토큰 등록(17.6)만 upsert 200. `Idempotency-Key` 헤더는 쓰지 않음 | [확정 정책] Q415 |
| 동시성 | 상태 전이/다중 행 변경은 트랜잭션, 관련 UNIQUE·FK·CHECK 및 BE 검증 | [확정 정책] |

**식별자 사용 규칙 [확정 정책]:** `userSeq`는 **행위자**(작성자·수신자·감사 컬럼), `shelterSeq`는 **보호소 자원의 범위**(소유·배정·업무)입니다. 댓글·채팅·북마크·신고·알림처럼 USER와 SHELTER가 함께 쓰는 자원은 `userSeq`만 저장하고 권한 검사는 자원의 `userSeq`와 토큰 `userSeq`를 비교합니다. 구조·보호동물·반환·입양 심사·보호소 정보처럼 보호소 업무 자원은 `shelterSeq`로 범위를 정하고 토큰 `shelterSeq`와 비교하되, 행위자는 여전히 `regUsrSeq`·`reviewedUsrSeq`에 `userSeq`로 남깁니다. **요청 본문·경로로 `shelterSeq`를 받지 않고** 토큰에서만 꺼내며, 응답과 공개 필터(8.1 등)에만 노출합니다. 보호소당 계정 1개(`shelter_members` 양쪽 UNIQUE)가 전제입니다.

### 1.2 공통 응답 envelope **[확정 정책 — 코드 `ApiResponse`]**

모든 응답은 `backend/common`의 `ApiResponse` 봉투로 감쌉니다. 컨트롤러는 DTO만 반환하고 `ApiResponseBodyAdvice`가 실제 HTTP 상태를 읽어 `status`에 채웁니다.

**성공 — 단건**

```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "missingReportSeq": 101, "status": "ACTIVE" },
  "errors": [],
  "traceId": "4f2c9e1a7b3d4c5e"
}
```

**성공 — 목록** (이하 엔드포인트의 `data`만 각 절에 기재)

```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  },
  "errors": [],
  "traceId": "4f2c9e1a7b3d4c5e"
}
```

**실패**

```json
{
  "status": 409,
  "code": "MISSING_ACTIVE_ALREADY_EXISTS",
  "message": "이미 진행 중인 실종 신고가 있습니다.",
  "data": null,
  "errors": [],
  "traceId": "4f2c9e1a7b3d4c5e"
}
```

**검증 실패** (`COMMON_INVALID_INPUT`)

```json
{
  "status": 400,
  "code": "COMMON_INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "data": null,
  "errors": [ { "field": "phone", "reason": "전화번호 형식이 아닙니다." } ],
  "traceId": "4f2c9e1a7b3d4c5e"
}
```

- 성공은 `code=SUCCESS`, 실패는 `code`에 19장의 에러 코드. HTTP: 조회/수정/삭제 200, 생성 201. **본문 없는 성공도 `200 + data:null`이며 204는 쓰지 않습니다**(Q415 A). 성공 메시지는 기본 "요청이 성공했습니다."이고 API별로 `@SuccessMessage`로 바꿀 수 있습니다.
- 실패는 `data:null`. `errors`는 필드 검증 실패에서만 채워지고 그 외에는 빈 배열입니다. 500은 내부 메시지를 노출하지 않습니다.
- 중복 등록은 409 도메인 코드가 기본이고, 디바이스 토큰 등록(17.6)만 upsert 200입니다(Q415).
- 성공 응답 예제에서 사용자·동물 식별자, 시각, 전화번호 및 이미지 URL은 **가상 데이터**입니다.

### 1.3 공통 HTTP 오류 **[확정 정책 — 코드 `CommonErrorCode`]**

| HTTP | code | 의미 |
|---|---|---|
| 400 | `COMMON_INVALID_INPUT` | 필수값/형식/범위/화이트리스트 밖 정렬 등 입력 오류. `errors[]`에 필드별 사유 |
| 400 | `COMMON_INVALID_JSON` | 요청 본문을 읽을 수 없음 |
| 401 | `AUTH_UNAUTHORIZED` | 인증 헤더 없음 |
| 401 | `AUTH_TOKEN_EXPIRED` | access 토큰 만료 → 앱은 2.3 refresh 시도 |
| 401 | `AUTH_TOKEN_INVALID` | 서명·형식·클레임 위조 → 앱은 재로그인 |
| 403 | `AUTH_FORBIDDEN` | 역할·소속·소유권 부족(도메인 코드가 없을 때) |
| 403 | `AUTH_ACCOUNT_UNAVAILABLE` | 계정 정지 등 상태(상태 세부 운영 정책은 [가안] ACTIVE만 사용) |
| 404 | `COMMON_NOT_FOUND` | 경로 없음 또는 리소스 없음(도메인 코드가 없을 때) |
| 405 | `COMMON_METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 500 | `COMMON_INTERNAL_ERROR` | 서버 내부 오류 |
| 503 | `COMMON_SERVICE_UNAVAILABLE` | 일시 장애 |

- **명명 규칙(Q418 A):** 응답 `code`는 서버 enum 상수명 그대로이며 `{도메인}_상황` 형식입니다. 공통 코드는 `COMMON_*`·`AUTH_*`, 도메인 코드는 각 도메인 패키지의 `{도메인}ErrorCode`에 둡니다(19장). 파일 크기·타입 오류는 공통이 아니라 `IMAGE_TOO_LARGE`(413)·`IMAGE_UNSUPPORTED_TYPE`(415)처럼 도메인 코드입니다.

**공통 필터:** 로그인 필수; `users.is_deleted=true` 작성자의 일반 게시물·AI 후보를 비노출합니다. 단 채팅 메시지, 기존 댓글·답글, 저장 알림, 보호소 업무상 과거 입양 기록, 모의 입양 리포트 등은 보존하고 작성자를 `(탈퇴한 사용자)`로 표기합니다. 동물 및 신고의 `is_deleted` 등 비노출은 각 API에서도 확인합니다. **[확정 정책] 403/404 원칙(Q434):** REST에서 타인·타 보호소의 자원에 접근하거나 비노출 자원을 조회하면 403이 아니라 해당 도메인의 `*_NOT_FOUND` 404로 존재를 감춥니다(실종·발견·구조·보호동물·입양 신청·댓글·알림·채팅방·모의 입양 공통). 403은 **요청자가 자기 것이 아닌 것을 자기 자원에 연결하려는 경우**(`IMAGE_NOT_OWNED`, Q407)와 **STOMP SUBSCRIBE·SEND**(`CHAT_ROOM_NOT_PARTICIPANT`, 방 존재가 이미 드러난 맥락)에만 씁니다. 역할 자체가 맞지 않는 요청(USER가 SHELTER 전용 API 호출 등)은 `@PreAuthorize`가 403 `AUTH_FORBIDDEN`을 냅니다.

### 1.4 목록 응답 DTO

- `Page<T>` **[확정 정책 — 코드 `PageResponse`]**: `{content: T[], page, size, totalElements, totalPages, hasNext}`. `page`는 0부터.
- `ImageRef`: `{imageSeq: number, imageUrl: string|null, displayOrder: number}`. `imageUrl`은 서버가 반환한 접근 URL이며 DB의 `image_key`를 앱이 임의 조합하지 않습니다. **[확정 정책] 읽기 방식(Q443):** 사진(`images/` 접두어)은 버킷 정책으로 **공개 읽기**를 허용한 **고정 URL**(`{public-base-url}/{image_key}`, 만료 없음, 앱 캐시 가능)입니다. 수료증(`certificates/`)만 비공개 객체이며 12.3 `certificateUrl`로 **presigned GET**(TTL 10분 [가안])을 내려줍니다.
- `AnimalClassification`: `{combinationSeq, speciesSeq, speciesName, breedSeq, breedName, colorSeq, colorName}`. **저장은 `combinationSeq`만** 사용합니다. `speciesSeq/breedSeq/colorSeq`는 표시를 위한 JOIN 파생 필드입니다.
- `ActorSummary`: `{userSeq, displayName, role, profileImage?: ImageRef|null}`. 회원 탈퇴 시 표시 이름 대체. SHELTER는 표시 이름으로 해당 보호소명 사용. 프로필 이미지는 USER·SHELTER 모두 공통 `images` 참조(Q408).
- `GeoPoint`: `{address, latitude, longitude, districtCode}`. 위도 -90~90/경도 -180~180 검증은 [초안] 구현 규약. `districtCode`는 서버가 역지오코딩으로 채우며 실패 시 `null`(Q406).
- `District`: `{districtCode, sidoName, sigunguName}`. 5.5 참조. 목록 필터 `districtCode`가 `districts`에 없는 값이면 400 `CATALOG_INVALID_DISTRICT_CODE`(7.1·8.1·9.1·11.1 공통).
- 목록마다 "정렬 화이트리스트"와 "필터" 한 줄을 명시합니다(Q417). 명시가 없는 목록은 `regDttm`만 허용합니다.

### 1.5 접근 권한 요약 (DB 정책과 직접 연동)

ADMIN 열은 v2에서 제거했습니다(구현 범위 외). 식별정보 수정·수동 재배정·기준정보 관리처럼 v1에서 ADMIN에 배정했던 운영 기능은 **API 없이 초기 데이터 적재·직접 DB 작업으로 처리**합니다(Q412).

| 기능 | USER | SHELTER |
|---|---|---|
| 반려동물 등록/수정/삭제 | **본인만** | 불가 |
| 실종·발견 신고 작성 | USER만(Q421) | **불가** |
| 실종·발견 신고 수정/삭제 | 작성자 본인, ACTIVE일 때만(Q423·Q425) | 불가 |
| 신고 재개/종료 | 작성자 본인 | **배정된 발견 신고의 종료만**(8.6, Q421) |
| 보호소 정보 변경 | 불가 | 소속 보호소 운영정보만(식별정보는 시드) |
| 구조·보호동물 등록·관리 | 불가 | 소속 보호소 업무 |
| 보호동물 반환/입양 심사·완료 | 불가 | 소속 보호소 |
| 입양 신청·저장·제출·취소 | **본인만** | 불가(SUBMITTED 이후 업무 열람만, Q422) |
| 모의 입양 세션·돌봄·구매·이벤트·산책 | **본인만** | 불가(자기 보호동물 세션의 **종료 리포트만** 열람, Q435) |
| 댓글 작성 | 허용 | 허용 |
| 채팅방 생성·메시지 전송 | 허용(USER↔USER, USER↔SHELTER). 전송은 STOMP SEND(R16 B) | 허용(USER 대상만, SHELTER가 먼저 생성 가능 R8). 전송은 STOMP SEND |
| 댓글 수정/삭제 | 작성자 본인(Q423) | 작성자 본인(Q423) |
| 채팅 메시지 수정/삭제 | 없음(기능 자체 없음) | 없음 |
| 신고 | 허용; 보호소 계정 대상 신고 불가 | 허용(USER·게시물·댓글 대상) |
| 북마크 | USER | 불가 |
| 일반 알림 | 수신자 본인 | 수신자 본인 |

---

## 2. 인증·회원 (AUTH)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 2.1 | POST | `/auth/kakao/verify` | [비인증] | 카카오 검증·기존 회원 로그인/최초 가입 컨텍스트 |
| 2.2 | POST | `/auth/local/login` | [비인증] | 사전 발급 SHELTER 로그인 |
| 2.3 | POST | `/auth/refresh` | [비인증] | access 갱신(Redis rotation) |
| 2.4 | POST | `/auth/logout` | 로그인 | refresh 폐기 [초안] |
| 2.5 | POST | `/auth/kakao/register` | [비인증: 가입 컨텍스트 필요] | 신규 USER 필수 개인정보 포함 최종 가입 |
| 2.6 | DELETE | `/users/me` | USER | 회원 탈퇴 |

### 2.1 POST /auth/kakao/verify

**[확정 정책]** USER는 KAKAO 계정만 사용합니다. 탈퇴 후 재가입하면 기존 회원 행을 복구하지 않고 새 `users/user_accounts` 행을 만듭니다. 카카오 로그인은 **Kakao Developers 앱(테스트 앱)** 기준이며 Kakao Developers에 팀원으로 등록된 계정만 로그인됩니다. 서버는 카카오에서 **계정 식별자만** 받고 프로필 사진·이메일·닉네임은 가져오지 않습니다(Q408-1). **[확정 정책] 흐름(Q413 A):** 앱이 카카오 SDK로 받은 access token을 전달하면 서버가 카카오 사용자 조회 API(`/v2/user/me`)로 검증해 카카오 id를 얻고, `user_accounts(provider=KAKAO, login_identifier=카카오 id)`가 활성이면 로그인 완료, 없으면 **가입 완료용 임시 컨텍스트만 반환**합니다. 인가코드 서버 교환 방식은 쓰지 않습니다. 이 경로는 `/api/core/v1/auth/**`로 `security.permit-paths`에 등록된 비인증 경로입니다.

**요청 [초안]**
```json
{ "kakaoAccessToken": "kakao-token" }
```

**기존 가입자 성공 200 `data` [초안]**
```json
{
  "requiresRegistration": false,
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "userSeq": 17, "role": "USER", "nickname": "반려가족" }
}
```

**신규 사용자 성공 200 `data` [초안]**
```json
{
  "requiresRegistration": true,
  "registrationToken": "short-lived-registration-context",
  "accessToken": null,
  "refreshToken": null,
  "user": null
}
```

- **핵심 정합성:** DB v10에서 USER의 `nickname/name/birth_date/phone` 네 필드는 모두 필수 CHECK입니다. 따라서 **카카오 검증 직후 개인정보가 없는 USER 레코드를 미리 INSERT하지 않습니다.** 2.5에서 필수 정보를 받은 뒤 최종 사용자/인증 행을 같은 트랜잭션에서 생성합니다.
- `registrationToken`은 미가입 카카오 계정의 확인 결과를 넘기기 위한 일시적 가입 컨텍스트입니다. **[확정 정책] 보관(Q414 A):** 서버가 불투명 랜덤 문자열을 만들어 Redis `auth:register:{token}`에 카카오 id를 저장하고 TTL 10분을 둡니다. 2.5 성공 시 즉시 삭제해 재사용을 막고, 만료되면 앱은 2.1을 다시 호출합니다. 이 값은 JWT가 아니며 `JwtAuthenticationFilter`를 타지 않고 2.5 본문에서만 받습니다. 서비스 일반 API 접근 권한이 없습니다.
- **주요 실패 [초안]:** 401 `AUTH_INVALID_KAKAO_TOKEN`, 403 `AUTH_ACCOUNT_UNAVAILABLE`, 409 `AUTH_KAKAO_ACCOUNT_CONFLICT`.

### 2.2 POST /auth/local/login

**요청 [초안]** `{ "loginId": "shelter01", "password": "..." }`.

**성공 200 `data` [초안]** `{ "accessToken":"...", "refreshToken":"...", "tokenType":"Bearer", "expiresIn":3600, "role":"SHELTER", "userSeq":55, "shelterSeq":7 }`. `shelterSeq`는 **항상 존재**합니다(SHELTER 계정은 `shelter_members` 1:1).

- **[확정 정책]** LOCAL은 SHELTER 전용, 계정 사전 발급(Flyway 시드, Q412). `password_hash`만 보관하며 비밀번호 재설정 API는 구현 제외. ADMIN 계정은 발급하지 않습니다(Q411). 발급 토큰의 클레임에 `shelterSeq`를 넣으며, `shelter_members` 행이 없는 SHELTER 계정은 로그인 거부합니다(토큰 검증기가 `role=SHELTER`에 `shelterSeq` 없는 토큰을 거부).
- **[초안]** 존재하지 않는 ID/틀린 비밀번호는 동일 401 `AUTH_INVALID_CREDENTIALS`.
- **[확정 정책] 실패 제한(Q445):** 같은 `loginId` 기준 연속 실패 횟수를 Redis `auth:login-fail:{loginId}`에 세고, **5회 실패 시 15분 동안 로그인을 거부**합니다 **[가안: 횟수·시간은 코드 설정]**. 차단 중에는 비밀번호가 맞아도 429 `AUTH_LOGIN_LOCKED`를 돌려주고, 차단은 TTL 만료로만 자동 해제됩니다(수동 해제 API·DB 컬럼 없음, 비밀번호 재설정이 없으므로 영구 잠금은 두지 않음). 성공 시 카운터를 삭제합니다. 존재하지 않는 ID도 같은 규칙으로 세어 계정 존재 여부를 노출하지 않습니다.

### 2.3 POST /auth/refresh · 2.4 POST /auth/logout

- **[확정 정책] refresh token은 Redis에 TTL로 저장하고 DB 테이블은 없습니다(Q409 B).** **[확정 정책] 토큰 형태(Q433):** refresh는 access와 **같은 RS256 개인키로 서명한 JWT**이며 클레임은 `userSeq`·`jti`·`typ=refresh`·`exp`(role·shelterSeq 없음). access에는 `typ=access`를 넣고, `JwtVerifier`는 `typ`이 `access`가 아닌 토큰을 거부해 refresh를 API 인증에 쓰지 못하게 합니다. refresh 검증(서명 + Redis 키 존재)은 core-api 2.3·2.4에서만 수행합니다. 운영 규칙 **[가안]**:
  - 키 `auth:refresh:{userSeq}:{jti}`, 값은 refresh 토큰 해시, TTL 14일(=refresh `exp`). access 토큰 수명 1시간.
  - 로그인마다 새 jti를 발급하므로 기기마다 키 1개 → **다중 기기 로그인 허용**.
  - `2.3` 호출 시 키 존재 확인 → 삭제 → 새 jti로 access·refresh 재발급(**rotation**). 이미 삭제된 jti로 재요청이 오면 탈취로 간주해 **해당 사용자의 refresh 키 전부 삭제**.
  - `2.4` 호출 시 해당 jti 키 삭제. access 토큰 블랙리스트는 두지 않고 1시간 자연 만료.
- `2.3` **요청 [초안]** `{ "refreshToken":"..." }` → 성공 200 `data:{accessToken,refreshToken,tokenType,expiresIn}`.
- `2.4` **요청 [초안]** `{ "refreshToken":"...", "fcmToken":"fcm-device-token" }` → 성공 200 `data:null`. **[확정 정책] 로그아웃 시 FCM(Q419 A):** `fcmToken`은 선택 필드이며 있으면 그 기기의 활성 디바이스 토큰만 비활성화합니다(17.7과 같은 처리). 없으면 토큰은 그대로 둡니다. 탈퇴 시에는 전량 비활성화합니다.
- **실패 [초안]** 401 `AUTH_INVALID_REFRESH_TOKEN`(2.3, 만료·불일치·재사용 감지 모두 동일), 공통 오류.

### 2.5 POST /auth/kakao/register

**요청 [초안]**
```json
{
  "registrationToken": "short-lived-registration-context",
  "nickname": "반려가족",
  "name": "김가람",
  "birthDate": "1997-05-12",
  "phone": "01012345678"
}
```

**성공 201 `data` [초안]**
```json
{
  "userSeq": 17,
  "role": "USER",
  "nickname": "반려가족",
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "tokenType": "Bearer"
}
```

- **[확정 정책]** `users`의 필수 개인정보 4종이 모두 충족될 때만 최종 USER+KAKAO `user_accounts` 생성. 활성 닉네임은 대소문자 무관, 전화번호는 활성 USER 간 중복 금지. **SMS 인증 없음.** 가입 요청에 프로필 사진 필드는 **없습니다**: 가입 전에는 access token이 없어 이미지 업로드(4장)가 불가능하므로 프로필 사진은 가입 후 3.2로 설정합니다(Q408). 카카오 프로필 사진도 가져오지 않아 `profileImage`는 `null`로 시작합니다(Q408-1).
- **[확정 정책]** Redis의 가입 컨텍스트를 조회해 카카오 id를 얻고(없거나 만료면 401), KAKAO 동일 계정 동시 가입은 `user_accounts` 활성 부분 UNIQUE로 막은 뒤 하나의 트랜잭션에서 최종 계정을 생성합니다. 성공 시 Redis 키를 삭제합니다. 실패 시 미완성 USER DB 행을 남기지 않습니다.
- **실패 [초안]** 401 `AUTH_INVALID_REGISTRATION_CONTEXT`; 409 `USER_DUPLICATE_NICKNAME`, `USER_DUPLICATE_PHONE`, `AUTH_KAKAO_ACCOUNT_CONFLICT`; 400 `USER_REQUIRED_PROFILE_MISSING`.

### 2.6 DELETE /users/me

**요청 [초안]** 본문 없음, 성공 200 `data:null`.

- **[확정 정책]** core-api 트랜잭션에서 `users`+`user_accounts` 소프트 삭제, 본인의 DRAFT/SUBMITTED/APPROVED 입양 신청을 CANCELED, 실제 ADOPTED 기록 보존, Redis의 refresh 키 전부 삭제. 커밋 후 `UserDeleted` 이벤트를 RabbitMQ로 발행하면 notification이 활성 device_tokens를 비활성화하고 simulation이 진행 중 세션을 ABANDONED 처리해 리포트를 생성·보존합니다(M6). 일반 게시물은 소유자가 탈퇴했으므로 가상 비노출, 댓글·채팅·저장 알림 및 보호소 과거 신청서 보존.
- **[초안]** 탈퇴 전 확인 절차·카카오 재검증은 앱 화면 흐름에서 정합니다. 서버는 재검증을 요구하지 않습니다.

---

## 3. 사용자 프로필 (USER)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 3.1 | GET | `/users/me` | 로그인 | 내 프로필·소속 보호소 |
| 3.2 | PATCH | `/users/me` | USER | 닉네임/이름/생년월일/전화/프로필 사진 수정 |
| 3.5 | GET | `/users/{userSeq}/profile` | 로그인 | 다른 회원의 공개 프로필 |

> v1의 3.3 `PATCH /users/me/profile-image`·3.4 `DELETE /users/me/profile-image`는 **삭제**했습니다(Q408). 프로필 사진은 3.2의 `profileImageSeq`로 설정·해제합니다. 번호 3.5는 v1과의 대조를 위해 유지합니다.

### 3.1 GET /users/me

**성공 200 `data` [초안]**
```json
{
  "userSeq": 17,
  "role": "USER",
  "nickname": "반려가족",
  "name": "김가람",
  "birthDate": "1997-05-12",
  "phone": "01012345678",
  "profileImage": null,
  "shelterSeq": null,
  "accountStatus": "ACTIVE"
}
```
- **[확정 정책]** SHELTER는 이름/생년월일/전화/닉네임 NULL이 가능하고 `shelterSeq`를 소속 관계로 조회. `profileImage`는 `users.profile_image_seq`를 통해 공통 `images`에서 읽은 `{ "imageSeq":1301, "imageUrl":"https://..." }` 형태이며 없으면 `null`(보호소 9.2의 `profileImage`와 같은 형태, Q408).

### 3.2 PATCH /users/me

**요청 [초안]** `{ "nickname":"새닉네임", "name":"김가람", "birthDate":"1997-05-12", "phone":"01012345678", "profileImageSeq": 1301 }` (부분 갱신, 보내는 필드만 변경).

- **응답** 수정된 프로필(3.1과 동일 DTO). **오류** 409 `USER_DUPLICATE_NICKNAME`/`USER_DUPLICATE_PHONE`. USER의 필수 4종을 null로 바꾸는 요청은 400 `COMMON_INVALID_INPUT`.
- **[확정 정책] 프로필 사진(Q407·Q408):** `profileImageSeq`는 4.2로 확정한 공통 `imageSeq`이며 **요청자가 업로드한 이미지(`images.reg_usr_seq` = 본인)만** 연결할 수 있습니다. 아니면 403 `IMAGE_NOT_OWNED`, 없으면 404 `IMAGE_NOT_FOUND`. `profileImageSeq:null`은 해제이고, 필드 미전달은 유지입니다. 교체·해제로 참조를 잃은 이전 이미지는 **마지막 참조 해제 정책(DB Q383)**에 따라 다른 참조가 없을 때만 `images` 행과 원본 객체를 삭제합니다(삭제 방식은 4.1의 Q427). 별도 프로필 업로드 경로는 없습니다.

### 3.5 GET /users/{userSeq}/profile

**성공 200 `data` [초안]** `{ "userSeq":18, "displayName":"발견도우미", "role":"USER", "profileImage":null }`. 비공개 실명/생년월일/전화는 반환하지 않습니다. 탈퇴 계정은 404 `USER_NOT_FOUND` [초안].

---

## 4. 이미지·파일 업로드 (MEDIA)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 4.1 | POST | `/images/upload-urls` | 로그인 | JPG/PNG S3 presigned PUT 주소 발급 |
| 4.2 | POST | `/images` | 로그인 | 업로드한 이미지의 공통 메타데이터 확정 |
| 4.3 | POST | `/files/upload-urls` | USER | 입양 수료증 이미지/PDF presigned PUT 주소 발급 |

### 4.1 POST /images/upload-urls · 4.2 POST /images

- **[확정 정책]** 게시물/반려동물 사진은 JPG/JPEG/PNG, 각 **최대 10MB**, 게시물별 **1~3장**, 댓글·채팅은 **최대 1장**, 프로필은 **0~1장**. 공통 `images`는 `image_seq/image_key/mime_type/original_file_name/byte_size/reg_usr_seq`를 관리합니다. 동일 이미지를 다수 도메인에서 재참조할 수 있습니다.
- **[확정 정책] 업로더 기록(Q407):** 4.2로 메타데이터를 확정할 때 서버가 `images.reg_usr_seq`에 인증 주체를 기록합니다. 공공데이터·시드 적재분은 NULL입니다. 이후 **모든 이미지 연결 요청**(게시물·반려동물 `imageSeqs`, 댓글·채팅 `imageSeq`, 프로필·보호소 `profileImageSeq/backgroundImageSeq`)은 `reg_usr_seq`가 요청자와 같은 이미지만 허용하고, 다르면 403 `IMAGE_NOT_OWNED`. 같은 사용자의 반려동물 사진→실종 신고 재사용은 그대로 가능합니다. ADMIN 전역 예외는 없습니다.
- **[확정 정책] 업로드 프로토콜(S4 A):** **AWS S3 + presigned PUT**. 앱은 4.1로 주소를 받아 S3에 직접 PUT하고 4.2로 메타데이터를 확정합니다. 서버의 S3 접근은 core-api의 `port/out` 어댑터로 격리합니다. CORS는 Android 네이티브에 해당 없습니다.

**4.1 요청 [초안]** `{ "fileName":"dog.jpg", "contentType":"image/jpeg", "byteSize":1234567 }`.

**4.1 성공 200 `data` [초안]** `{ "uploadUrl":"https://{bucket}.s3.ap-northeast-2.amazonaws.com/...", "imageKey":"images/2026/11/generated-key.jpg", "expiresInSeconds":600 }`.

**4.2 요청 [초안]** `{ "imageKey":"images/2026/11/generated-key.jpg" }`.

**4.2 성공 201 `data` [초안]** `{ "imageSeq":1001, "imageUrl":"https://{public-base-url}/images/2026/11/generated-key.jpg", "mimeType":"image/jpeg", "byteSize":1234567 }`.

- **[확정 정책] 읽기 URL(Q443):** `images/` 접두어 객체는 버킷 정책으로 익명 GET을 허용하고, 모든 `imageUrl`은 `{public-base-url}` + `image_key`의 **고정 URL**입니다(서명·만료 없음). 서버는 이 조합만 하고 앱은 URL을 그대로 씁니다. 키는 서버가 생성한 랜덤 값이라 추측이 어렵고, 사진은 모두 공개 게시물·프로필용이라 로그인 없이 열리는 것을 허용합니다. 업로드(PUT)는 계속 presigned만 허용하며 익명 쓰기는 열지 않습니다. `{public-base-url}`은 버킷 도메인 또는 CDN 도메인이며 코드 설정값입니다.

- **[확정 정책] 발급 컨텍스트:** 4.1이 `imageKey`를 서버가 생성하고 Redis `upload:image:{imageKey}`에 발급자 `userSeq`·`contentType`·`fileName`을 TTL 10분 **[가안]**으로 저장합니다. 4.2는 그 키의 발급자가 요청자와 같을 때만 받아들이고(아니면 404 `IMAGE_NOT_FOUND`), **S3 `HEAD` 호출로 실제 객체의 `Content-Length`·`Content-Type`을 읽어** 10MB·허용 MIME을 검증한 뒤 `images` 행을 INSERT하고 Redis 키를 삭제합니다. 클라이언트가 보낸 크기·MIME은 신뢰하지 않습니다. presigned 서명에는 `Content-Type`을 포함해 다른 타입 PUT을 거부합니다.
- **[확정 정책] 고아 객체:** 4.1 뒤 4.2가 오지 않은 객체는 DB 행이 없으므로 버킷 **수명주기 규칙**(`images/` 접두어의 미확정 객체를 24시간 뒤 삭제 **[가안]**, 확정 객체는 4.2에서 태그를 붙여 제외)으로 정리합니다.
- **[확정 정책] 사진 수정(Q424):** 각 도메인의 수정 API는 `imageSeqs`를 **원하는 최종 배열 순서대로** 받습니다. 서버가 현재 연결과 diff를 계산해 빠진 이미지는 연결 해제, 추가된 이미지는 소유·존재 검증 후 연결하고, `display_order`는 배열 index+1로 전부 다시 매깁니다. 따라서 순서 변경은 배열 순서만 바꿔 보내면 되고 `displayOrder`를 따로 보내지 않습니다. 1~3장·중복 금지. 구현은 연결 행 전체 삭제 후 재삽입이며 `images` 행은 건드리지 않습니다.
- **[확정 정책] 원본 정리(Q383·Q427):** 공통 이미지는 **모든 참조가 해제된 경우에만** `images` 행을 삭제하고, DB 커밋 **후** S3 객체 삭제를 best-effort로 시도합니다. 실패하면 로그만 남기고 재시도하지 않습니다(고아 객체 허용). 비즈니스 클라이언트에 임의의 `DELETE /images/{imageSeq}`는 제공하지 않습니다. 참조 확인 대상은 연결 테이블 4개, 댓글·채팅 `image_seq`, `users.profile_image_seq`, `shelters.profile_image_seq/background_image_seq`입니다. 모의 입양 상점 물품 사진은 `images`를 참조하지 않으므로(키 문자열, Q420) 대상이 아닙니다.
- **오류 [초안]** 400 `IMAGE_INVALID_COUNT`, 413 `IMAGE_TOO_LARGE`, 415 `IMAGE_UNSUPPORTED_TYPE`, 404 `IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`.

### 4.3 POST /files/upload-urls

**요청 [초안]** `{ "purpose":"ADOPTION_CERTIFICATE", "fileName":"certificate.pdf", "contentType":"application/pdf", "byteSize":823456 }`.

**성공 200 `data` [초안]** `{ "uploadUrl":"https://{bucket}.s3.ap-northeast-2.amazonaws.com/...", "storageKey":"certificates/2026/11/generated.pdf", "expiresInSeconds":600 }`.

- **[확정 정책]** 교육 수료증은 신청서당 선택 1건, JPG/PNG/PDF 최대 10MB. 저장 위치는 `adoption_applications.certificate_storage_key`이고 공통 `images.imageSeq` FK가 아닙니다. 발급 컨텍스트는 4.1과 같이 Redis `upload:file:{storageKey}`에 두고, 12.5에서 `certificateStorageKey`를 저장할 때 발급자 일치와 S3 `HEAD` 검증을 수행합니다.
- 수료증(`certificateStorageKey`)만 저장소 키 문자열 방식이고, 프로필 이미지를 포함한 모든 사진은 공통 `imageSeq` 참조입니다(Q408). 두 방식을 섞지 않습니다.
- **[확정 정책] 읽기(Q443):** `certificates/` 접두어는 **비공개**(익명 GET 불허)입니다. 12.3 상세 조회에서 권한을 통과한 요청자(신청자 본인·담당 보호소)에게만 서버가 **presigned GET URL**을 만들어 `certificateUrl`로 내려주며 TTL 10분 **[가안]**, 매 조회마다 새로 발급합니다. 목록(12.2·12.9)에는 URL을 넣지 않고 `certificateAttached` 불리언만 둡니다.
- **오류 [초안]** 413 `ADOPTION_CERTIFICATE_TOO_LARGE`, 415 `ADOPTION_CERTIFICATE_UNSUPPORTED_TYPE`, 404 `ADOPTION_CERTIFICATE_NOT_FOUND`(12.5에서 미업로드·타인 발급 키).

---

## 5. 동물 기준정보 (ANIMAL CATALOG)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 5.1 | GET | `/animal-species` | 로그인 | 종 전체 목록 |
| 5.2 | GET | `/animal-species/{speciesSeq}/breeds` | 로그인 | 종의 품종 목록 |
| 5.3 | GET | `/animal-colors` | 로그인 | 색상 기준 목록 |
| 5.4 | GET | `/animal-combinations` | 로그인 | 유효 품종·색상 조합 조회 |
| 5.5 | GET | `/districts` | 로그인 | 시·도/시·군·구 행정구역 마스터(Q406-1) |

### 5.1~5.5 조회 규약

- `5.1` 성공 `data:[{"speciesSeq":1,"speciesCode":"DOG","speciesName":"개"}, ...]`. **[확정 정책]** `speciesCode`는 `DOG/CAT/ETC`(M8). 모의 입양은 `DOG/CAT`만 시작 가능합니다.
- `5.2` 성공 `data:[{"breedSeq":21,"speciesSeq":1,"breedName":"말티즈","personality":"FRIENDLY"}, ...]`. **[확정 정책]** `personality`는 `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY` 또는 `null`(M7). 모의 입양 시작 시 이 값을 세션에 스냅샷합니다.
- `5.3` 성공 `data:[{"colorSeq":3,"colorName":"흰색"}, ...]`.
- `5.4` 요청 `?breedSeq=21&colorSeq=3`(각 필터 선택) → `data:[{"combinationSeq":88,"speciesSeq":1,"breedSeq":21,"colorSeq":3, ...}]`.
- `5.5` 요청 `?sidoName=대구광역시`(선택) → 성공 `data:[{"districtCode":"27110","sidoName":"대구광역시","sigunguName":"중구"}, ...]`. **[확정 정책]** `districtCode`는 법정동 코드 앞 5자리(시·군·구)이며 `districts` 시드 테이블에서 읽습니다. 7.1·8.1·9.1의 `districtCode` 필터 값과 신고·보호소 응답의 `districtCode`가 이 목록의 값입니다. 앱의 지역 선택 UI는 이 API로 시/도 → 시/군/구 목록을 구성합니다.
- **[확정 정책]** 네 동물 엔터티는 모두 `combinationSeq` 단일 FK를 저장합니다. 종은 품종에서 결정되고 `UNIQUE(breed_seq,color_seq)`입니다. 실제 마스터 값은 시드 데이터로 팀이 정합니다.
- **[확정 정책]** 기준정보는 비페이지 전량(수량 증가 시 서버 페이징 재검토). 기준정보 수정·시드 관리 API는 **없으며** Flyway 시드 SQL로 적재합니다(Q412).

---

## 6. 내 반려동물 (PETS)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 6.1 | GET | `/pets` | USER | 내 반려동물 목록 |
| 6.2 | GET | `/pets/{petSeq}` | 소유 USER | 내 반려동물 상세 |
| 6.3 | POST | `/pets` | USER | 반려동물 등록 |
| 6.4 | PATCH | `/pets/{petSeq}` | 소유 USER | 반려동물 수정 |
| 6.5 | DELETE | `/pets/{petSeq}` | 소유 USER | 반려동물 소프트 삭제 |

### 6.1 GET /pets · 6.2 GET /pets/{petSeq}

**요청 [초안]** `GET /pets?page=0&size=20&sort=regDttm,desc`. 정렬 화이트리스트 `regDttm`, `name`. 필터 없음(본인 소유 전량).

**목록 200 `data.content[]` [초안]** `{ "petSeq":5, "name":"초코", "classification":{"combinationSeq":88,"speciesName":"개","breedName":"말티즈","colorName":"흰색"}, "gender":"MALE", "representativeImageUrl":"https://...", "regDttm":"..." }`.

**상세 200 `data` [초안]** 목록 필드에 `age`, `weight`, `isNeutered`, `microchipNumber`, `description`, `images:[ImageRef]`, `modDttm`을 추가. 비삭제 소유자만 조회.

### 6.3 POST /pets

**요청 [초안]**
```json
{
  "name": "초코",
  "combinationSeq": 88,
  "gender": "MALE",
  "age": "3살",
  "weight": "4.5kg",
  "isNeutered": true,
  "microchipNumber": null,
  "description": "파란 목줄",
  "imageSeqs": [1001, 1002]
}
```
**성공 201 `data` [초안]** `{ "petSeq":5, "imageCount":2, "representativeImageSeq":1001 }`.

- **[확정 정책]** USER만 등록; 이름/유효 `combinationSeq`/성별 필수, 성별 `MALE/FEMALE/UNKNOWN`; 사진 1~3장. 첫 이미지가 대표. 중성화는 `true/false/null`, 몸무게와 나이는 선택 자유 텍스트.
- `userSeq`는 JWT 인증 주체에서 설정합니다. `imageSeqs`는 순서대로 `pet_images.display_order=1..N`로 연결합니다.
- **오류 [초안]** 404 `CATALOG_COMBINATION_NOT_FOUND`/`IMAGE_NOT_FOUND`, 400 `IMAGE_INVALID_COUNT`, 403 `IMAGE_NOT_OWNED`.

### 6.4 PATCH /pets/{petSeq} · 6.5 DELETE /pets/{petSeq}

- `6.4` **요청 [초안]** 6.3의 수정할 필드만 보냄; 이미지 변경 시 `imageSeqs` **전체 최종 배열**을 원하는 순서대로 전송하고 서버가 diff·순서를 계산합니다(Q424, 4.1). 성공 200 상세 DTO. `pets.mod_dttm`만 갱신(제거된 `mod_usr_seq`를 되살리지 않음). 소유자 본인만(Q423).
- `6.5` 성공 200 `data:null`. **기존 `missing_reports.pet_seq` 연결을 유지하고 실종 신고/AI 매칭에도 영향 없음.** 이미지 참조도 다른 도메인에서 참조 중이면 원본 삭제 금지.
- **오류 [초안]** 404 `PET_NOT_FOUND`(없음·삭제·타인 소유 모두, Q434); 6.4는 추가로 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`.

---

## 7. 실종 신고 (MISSING)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 7.1 | GET | `/missing-reports` | 로그인 | 실종 신고 검색/목록 |
| 7.2 | GET | `/missing-reports/{missingReportSeq}` | 로그인 | 상세/연락처 가시성 |
| 7.3 | GET | `/users/me/missing-reports` | USER | 내 실종 신고 목록/상태 |
| 7.4 | POST | `/missing-reports` | USER | 실종 신고 등록 |
| 7.5 | PATCH | `/missing-reports/{missingReportSeq}` | 작성자 | 신고 내용 수정 |
| 7.6 | PATCH | `/missing-reports/{missingReportSeq}/status` | 작성자 | ACTIVE↔CLOSED 변경 |
| 7.7 | DELETE | `/missing-reports/{missingReportSeq}` | 작성자 | 실종 신고 소프트 삭제 |

### 7.1 GET /missing-reports · 7.2 GET /missing-reports/{id}

**검색 파라미터 [확정 정책]** `?status=ACTIVE&speciesSeq=1&breedSeq=21&colorSeq=3&gender=MALE&districtCode=27110&keyword=목줄&page=0&size=20&sort=occurredDttm,desc`.
- 필터: `status`(기본 ACTIVE), `speciesSeq`, `breedSeq`, `colorSeq`, `gender`, `districtCode`(5.5 값, Q406 A), `keyword`. 서버는 `combinationSeq→animal_breeds→animal_species`로 종을 찾습니다.
- `keyword`는 `description`·`address`에 **`ILIKE '%키워드%'`**(S7, pg_trgm 인덱스 [가안]). 비노출 필터는 같은 쿼리에서 적용합니다.
- 정렬 화이트리스트: `regDttm`(기본), `occurredDttm`. 위치 반경 검색·거리순은 이번 범위에 없습니다(필요 시 `sortBy=DISTANCE` 열거형을 추가).

**목록 200 `data.content[]` [초안]**
```json
{
  "missingReportSeq": 101,
  "name": "초코",
  "classification": { "combinationSeq":88, "speciesName":"개", "breedName":"말티즈", "colorName":"흰색" },
  "gender": "MALE",
  "status": "ACTIVE",
  "address": "대구광역시 ...",
  "districtCode": "27110",
  "occurredDttm": "2026-10-28T18:00:00+09:00",
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00"
}
```

**상세 200 `data` [초안]** 목록 필드 + `user:{ActorSummary}`, `petSeq`, `age`, `isNeutered`, `microchipNumber`, `description`, `latitude`, `longitude`, `images:[ImageRef]`, `isPhonePublic`, `contactPhone`, `isBookmarked`, `commentCount`, `modDttm`.

- **[확정 정책]** 공개 연락처는 **로그인 사용자에게만** 보이며, 기본 비공개 시에는 **작성자만** 실제 번호를 열람합니다. 접근 불가 시 `contactPhone:null`로 응답 [초안]. `isPhonePublic` 자체는 반환 가능.
- **[확정 정책]** 탈퇴 작성자·삭제 신고는 일반 목록/상세에서 비노출. `circumstances` 필드는 존재하지 않습니다.
- **오류 [초안]** 7.2: 404 `MISSING_REPORT_NOT_FOUND`(없음·삭제·비노출). 7.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`(정렬 화이트리스트 밖).

### 7.4 POST /missing-reports

**요청 [초안]**
```json
{
  "petSeq": 5,
  "name": "초코",
  "combinationSeq": 88,
  "gender": "MALE",
  "age": "3살",
  "isNeutered": true,
  "microchipNumber": null,
  "description": "파란 목줄",
  "address": "대구광역시 ...",
  "latitude": 35.87,
  "longitude": 128.60,
  "occurredDttm": "2026-10-28T18:00:00+09:00",
  "contactPhone": "01012345678",
  "isPhonePublic": false,
  "imageSeqs": [1001, 1002]
}
```
**성공 201 `data` [초안]** `{ "missingReportSeq":101, "status":"ACTIVE", "districtCode":"27110", "aiMatchingRequested":true }`.

- `petSeq`는 선택, 지정 시 소유자 검증 후 당시 정보를 신고 행에 **스냅샷**으로 보존. 반려동물 미등록 신고도 가능하나 **신고의 `name`은 필수**입니다.
- 실종 주소/좌표/시각/이름/연락처/유효 조합/성별/사진 1~3장을 검증합니다. `contactPhone`은 회원 전화번호로 초기 제안하되 신고별로 수정 가능. **USER만 작성**(Q421).
- **[확정 정책] 행정구역 코드(Q406 A):** 서버가 좌표를 네이버 역지오코딩(`legalcode`)으로 조회해 앞 5자리를 `districtCode`에 저장합니다. 실패하면 `null`로 저장하고 등록은 그대로 진행합니다(Q406-2). 앱이 코드를 보내지 않습니다.
- **[확정 정책]** 작성 USER당 비삭제 ACTIVE 실종 신고 최대 1건(동일 반려동물 여부와 무관). 최초 등록/재개 시 유효한 발견 신고·보호동물과 AI 매칭 후보. 서버 트랜잭션과 부분 UNIQUE로 경합 차단.
- **오류 [초안]** 409 `MISSING_ACTIVE_ALREADY_EXISTS`; 404 `PET_NOT_FOUND`(타인 소유 포함, Q434), `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`.

### 7.5 PATCH /missing-reports/{id} · 7.6 PATCH /.../status · 7.7 DELETE /...

- `7.5` **요청 [초안]** 변경 가능한 7.4 필드 일부 + `imageSeqs`(전체 최종 배열, Q424). 상태는 별도 7.6 호출. `petSeq`는 등록 후 변경하지 않습니다(스냅샷 유지). 좌표·주소를 바꾸면 `districtCode`를 **재도출**합니다. 위치 수정은 **발견 신고와 달리 자동 보호소 배정 대상 자체가 아닙니다.** 이미지 배열에 diff가 있으면 AI 재매칭 요청. **[확정 정책] CLOSED는 읽기 전용(Q425 B):** `status=CLOSED`인 신고에 7.5를 호출하면 409 `MISSING_REPORT_CLOSED`이며, 고치려면 먼저 7.6으로 ACTIVE 재개합니다. 작성자 본인만(Q423).
- `7.6` **요청 [초안]** `{ "status":"CLOSED" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{missingReportSeq,status}`. ACTIVE 재개 시 다른 ACTIVE 신고가 있으면 409 `MISSING_ACTIVE_ALREADY_EXISTS`. 실종 신고의 종료 사유/종료 시각 API 필드는 **없습니다**. 작성자 본인만.
- `7.7` 성공 200 `data:null`. [확정 정책] 논리 삭제(ACTIVE·CLOSED 모두 가능); AI 대상 제외, 사진은 마지막 참조 해제 시만 정리. 작성자 본인만.
- 신고/보호동물 반환, 발견/구조 완료를 이유로 실종 신고가 자동 종료되지는 않습니다.
- **오류 [초안]** 7.5~7.7 공통 404 `MISSING_REPORT_NOT_FOUND`(없음·삭제·**타인 신고**, Q434). 7.5: 409 `MISSING_REPORT_CLOSED`, 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`. 7.6: 409 `MISSING_ACTIVE_ALREADY_EXISTS`(재개), 400 `COMMON_INVALID_INPUT`(현재와 같은 상태).

---

## 8. 발견 신고 (FOUND)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 8.1 | GET | `/found-reports` | 로그인 | 발견 신고 목록/검색 |
| 8.2 | GET | `/found-reports/{foundReportSeq}` | 로그인 | 발견 신고 상세 |
| 8.3 | GET | `/users/me/found-reports` | USER | 내 발견 신고 목록 |
| 8.4 | POST | `/found-reports` | USER | 발견 신고 등록·보호소 자동 배정 |
| 8.5 | PATCH | `/found-reports/{foundReportSeq}` | 작성자 | 발견 신고 내용 수정 |
| 8.6 | PATCH | `/found-reports/{foundReportSeq}/status` | 작성자/담당 SHELTER | ACTIVE↔CLOSED |
| 8.7 | DELETE | `/found-reports/{foundReportSeq}` | 작성자 | 발견 신고 소프트 삭제 |
| 8.8 | GET | `/shelters/me/found-reports` | SHELTER | 내 보호소 담당 발견 신고 목록 |

### 8.1 GET /found-reports · 8.2 GET /found-reports/{id}

**검색 [확정 정책]** `?status=ACTIVE&speciesSeq=1&breedSeq=21&colorSeq=3&gender=UNKNOWN&districtCode=27110&shelterSeq=7&keyword=목줄&page=0&size=20&sort=occurredDttm,desc`.
- 필터: `status`(기본 ACTIVE), `speciesSeq`, `breedSeq`, `colorSeq`, `gender`, `districtCode`(Q406 A), `shelterSeq`(담당 보호소, 공개 필터), `keyword`(`description`·`landmarkDescription`·`address` ILIKE, S7).
- 정렬 화이트리스트: `regDttm`(기본), `occurredDttm`.

**목록 200 `data.content[]` [초안]** `{ "foundReportSeq":210, "classification":{...}, "status":"ACTIVE", "shelterSeq":7, "address":"대구광역시 ...", "districtCode":"27110", "occurredDttm":"...", "representativeImageUrl":"..." }`.

**상세 200 `data` [초안]** 목록 필드 + `reporter:{ActorSummary}`, `shelter:{shelterSeq,shelterName}`, `age`, `isNeutered`, `microchipNumber`, `description`, `landmarkDescription`, `latitude`, `longitude`, `reporterPhone`, `isPhonePublic`, `closedReason`, `closedDttm`, `images`, `isBookmarked`, `commentCount`.

- **[확정 정책]** 비공개 `reporterPhone`은 작성자·**이 발견 신고의 담당 보호소**만 열람. 다른 사용자·다른 보호소에는 `null` 반환 [초안]. `reporterName` 별도 DB 컬럼 없음. 필요한 경우 `users.name`을 권한 범위 내 JOIN합니다.
- 삭제·탈퇴 작성자는 일반 노출에서 제외합니다. **발견 당시 목격 상황 전용 `circumstances` 없음**.
- **오류 [초안]** 8.2: 404 `FOUND_REPORT_NOT_FOUND`(없음·삭제·비노출). 8.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`.

### 8.4 POST /found-reports

**요청 [초안]**
```json
{
  "combinationSeq": 88,
  "gender": "UNKNOWN",
  "age": null,
  "isNeutered": null,
  "microchipNumber": null,
  "description": "목에 빨간 목줄",
  "reporterPhone": "01012345678",
  "isPhonePublic": false,
  "landmarkDescription": "공원 정문 앞",
  "address": "대구광역시 ...",
  "latitude": 35.87,
  "longitude": 128.60,
  "occurredDttm": "2026-10-29T10:00:00+09:00",
  "imageSeqs": [1201]
}
```
**성공 201 `data` [초안]** `{ "foundReportSeq":210, "status":"ACTIVE", "districtCode":"27110", "assignedShelterSeq":7, "assignmentMethod":"DISTRICT" }`.

- 주소/좌표/시각/신고자 연락처/조합/성별/사진 1~3장은 필수입니다. 기본 비공개. 신고자 이름은 별도 필드가 아니므로 요청하지 않습니다. **USER만 작성**(Q421).
- **[확정 정책] 담당 보호소 자동 배정(Q406 A·Q406-2 A), 서버 수행:** ① 좌표를 네이버 역지오코딩(`legalcode`)으로 조회해 앞 5자리를 `districtCode`로 저장 → ② `shelter_district_assignments`에 그 코드가 있으면 지정 보호소(`assignmentMethod:"DISTRICT"`) → ③ 코드가 없거나(역지오코딩 실패, `districtCode:null`) 배정표에 없으면 위경도 하버사인 최근접 비삭제 보호소(`assignmentMethod:"NEAREST"`, S3 A). 역지오코딩 실패로 **등록을 거부하지 않습니다**. `assignmentMethod`는 **응답 전용** 값이며 DB에 저장하지 않습니다(R13 A). 보호소가 하나도 없을 때만 409 `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE`(시드 전 환경). `shelterSeq` 임의 입력 불허. 수동 재배정 API는 없습니다(ADMIN 범위 외).
- **[확정 정책]** 이 시점에 발견 신고 상태는 ACTIVE; 기존 ACTIVE 실종 신고와 AI 비교 후보. 담당 보호소의 SHELTER 계정에 `SHELTER` 알림(17.8, Q426).
- **오류 [초안]** 409 `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE`; 404 `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`.

### 8.5 PATCH /found-reports/{id} · 8.6 PATCH /.../status · 8.7 DELETE /...

- `8.5` **요청 [초안]** 8.4의 수정 필드 일부 + `imageSeqs`(전체 최종 배열, Q424). `shelterSeq` 필드 불허. 좌표·주소를 바꾸면 `districtCode`는 **재도출**하지만 **소속 보호소는 자동 재배정되지 않습니다**. 이미지 diff가 있으면 AI 재매칭. **CLOSED는 읽기 전용(Q425 B):** 409 `FOUND_REPORT_CLOSED`, 고치려면 8.6으로 ACTIVE 재개. 작성자 본인만(Q423).
- `8.6` **요청 [초안]** `{ "status":"CLOSED", "closedReason":"보호자가 직접 찾아감" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{foundReportSeq,status,closedReason,closedDttm}`. CLOSED에서 `closedDttm`은 서버 생성 필수, `closedReason` 선택. ACTIVE 재개 시 두 값은 `null`로 초기화. **권한(Q421):** 작성자는 CLOSED·ACTIVE 모두 가능, **담당 SHELTER(토큰 `shelterSeq` = 신고의 `shelterSeq`)는 CLOSED만** 가능하고 재개는 불가.
- `8.7` 성공 200 `data:null`. 작성자 본인만. 기존 구조 기록이 연결되어 있더라도 **구조 기록/FK/진행 상태는 변경하지 않습니다**.
- **중요:** 구조 접수·완료, 직접 반환, 보호동물 상태 전환 시에도 발견 신고를 자동 CLOSED로 바꾸지 않습니다.
- **오류 [초안]** 8.5~8.7 공통 404 `FOUND_REPORT_NOT_FOUND`(없음·삭제·**타인 신고**; 8.6은 작성자도 담당 보호소도 아닌 경우, Q434). 8.5: 409 `FOUND_REPORT_CLOSED`, 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`. 8.6: 담당 SHELTER가 `ACTIVE` 재개를 요청하면 403 `AUTH_FORBIDDEN`(역할상 허용되지 않는 동작), 현재와 같은 상태면 400 `COMMON_INVALID_INPUT`.

### 8.8 GET /shelters/me/found-reports

**요청 [초안]** `?status=ACTIVE&hasRescue=false&page=0&size=20&sort=regDttm,desc`. 필터 `status`, `hasRescue`(비삭제 PROCESSING/DONE 구조 존재 여부)는 모두 **선택**이며, 생략하면 담당 발견 신고 전량을 반환합니다(R10 A). 정렬 화이트리스트 `regDttm`, `occurredDttm`. **응답** 담당 보호소(토큰 `shelterSeq`)의 발견 신고 목록, 각 항목에 8.1 목록 필드 + `reporterPhone`(담당 보호소이므로 열람 가능), `activeRescueSeq`(없으면 null).

---

## 9. 보호소 정보 (SHELTER)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 9.1 | GET | `/shelters` | 로그인 | 보호소 목록·검색 |
| 9.2 | GET | `/shelters/{shelterSeq}` | 로그인 | 보호소 상세·공개 프로필/배경 |
| 9.3 | GET | `/shelters/me` | SHELTER | 소속 보호소 상세 |
| 9.4 | PATCH | `/shelters/me` | SHELTER | 소속 보호소 운영정보·이미지 변경 |

### 9.1 GET /shelters · 9.2 GET /shelters/{id}

**요청 [확정 정책]** `?keyword=행복&districtCode=27110&page=0&size=20&sort=shelterName,asc`. 필터 `keyword`(`shelterName`·`address` ILIKE, S7), `districtCode`(보호소 소재지 코드, 오프라인 계산값을 시드에 삽입, Q406 A·Q431). 정렬 화이트리스트 `shelterName`, `regDttm`(기본 `shelterName,asc`). 목록 DTO는 상세에서 `description`·`operatingHours`·`closedDays`·`backgroundImage`를 뺀 것.

**상세 성공 200 `data` [초안]**
```json
{
  "shelterSeq": 7,
  "shelterName": "행복동물보호센터",
  "address": "대구광역시 ...",
  "districtCode": "27110",
  "latitude": 35.87,
  "longitude": 128.60,
  "phone": "0531234567",
  "description": "센터 소개",
  "operatingHours": "09:00~18:00",
  "closedDays": "일요일",
  "profileImage": { "imageSeq": 1701, "imageUrl":"https://..." },
  "backgroundImage": { "imageSeq": 1702, "imageUrl":"https://..." }
}
```
- **[확정 정책]** `profileImageSeq`와 `backgroundImageSeq`는 각각 images 직접 FK, 이미지 0~1장. 보호소당 담당 계정 1개. `externalShelterCode`는 응답에 노출하지 않습니다(내부 적재 키).

### 9.3 GET /shelters/me · 9.4 PATCH /shelters/me

- `9.3` 소속 보호소 상세와 관리 가능한 항목 반환.
- `9.4` **요청 [초안]** `{ "description":"...", "operatingHours":"...", "closedDays":"...", "profileImageSeq":1701, "backgroundImageSeq":1702 }` (부분 수정). **주소·이름·전화·좌표·행정구역 코드 등 식별정보는 SHELTER 수정 불가**이며 Flyway 시드·직접 DB 작업으로만 변경합니다(ADMIN API 없음, Q412). 대상 보호소는 토큰 `shelterSeq`. 이미지 두 필드는 보호소 계정이 업로드한 이미지만 연결 가능(403 `IMAGE_NOT_OWNED`, Q407).
- **오류 [초안]** 403 `IMAGE_NOT_OWNED`; 404 `SHELTER_NOT_FOUND`(토큰의 `shelterSeq`가 삭제된 보호소), `IMAGE_NOT_FOUND`. 대상이 토큰에서만 정해지므로 "타 보호소 수정" 상황 자체가 없어 `SHELTER_NOT_OWNED`는 두지 않습니다(Q442).

---

## 10. 구조 기록 (RESCUE)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 10.1 | GET | `/shelters/me/rescues` | SHELTER | 우리 보호소 구조 기록 목록 |
| 10.2 | GET | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 상세 |
| 10.3 | POST | `/rescues` | SHELTER | 발견 신고 연계/직접 구조 접수 |
| 10.4 | PATCH | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 진행 기록 수정 |
| 10.5 | PATCH | `/rescues/{rescueSeq}/status` | 담당 SHELTER | DONE/FAILED/CANCELED 처리 |
| 10.6 | DELETE | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 기록 논리 삭제(참조 있으면 거부) |

### 10.1 GET /shelters/me/rescues · 10.2 GET /rescues/{id}

**목록 파라미터 [초안]** `?status=PROCESSING&page=0&size=20&sort=regDttm,desc`. 필터 `status`, `foundReportSeq`. 정렬 화이트리스트 `regDttm`, `completedDttm`. 범위는 토큰 `shelterSeq`.

**상세 성공 200 `data` [초안]**
```json
{
  "rescueSeq": 301,
  "shelterSeq": 7,
  "foundReportSeq": 210,
  "status": "PROCESSING",
  "rescuedDate": null,
  "rescueReason": "길거리 배회",
  "rescueAddress": null,
  "rescueMemo": null,
  "resultReason": null,
  "regDttm": "2026-10-29T13:00:00+09:00",
  "completedDttm": null,
  "linkedAnimalCount": 0
}
```
- **주의:** 응답은 `regDttm`을 그대로 사용합니다. DB에 `received_dttm` 컬럼은 없습니다. 화면의 “접수 시각”은 이 값을 표시합니다.
- **오류 [초안]** 10.2·10.4~10.6 공통 404 `RESCUE_NOT_FOUND`(없음·삭제·**다른 보호소의 구조**, Q434).

### 10.3 POST /rescues

**요청 [초안]** `{ "foundReportSeq":210, "rescueReason":"길거리 배회", "rescueMemo":"담당자 출동" }` 또는 `{ "foundReportSeq":null, "rescueReason":"직접 구조" }`.

**성공 201 `data` [초안]** `{ "rescueSeq":301, "status":"PROCESSING", "regDttm":"..." }`.

- **[확정 정책]** 발견 신고를 선택하여 접수하면 즉시 PROCESSING 생성, 신고 없이 자체 접수도 가능. **SHELTER 전용**이며 `shelter_seq`는 토큰 `shelterSeq`로 서버가 설정합니다. 요청에 `shelterSeq` 필드는 **없습니다**(Q410). 발견 신고 1건에 비삭제 `PROCESSING/DONE` 합계 최대 1건. **[확정 정책] R3 A:** 담당 보호소가 아닌 발견 신고도 접수할 수 있습니다(구조는 실제 현장 대응이라 배정과 무관). **[확정 정책] 대상 신고 상태(Q442):** 소프트 삭제된 발견 신고는 404 `FOUND_REPORT_NOT_FOUND`, **CLOSED 신고는 접수 허용**(구조는 신고 상태와 독립, 8.5의 CLOSED 읽기 전용은 신고 본문에만 적용).
- **오류 [초안]** 409 `RESCUE_ACTIVE_ALREADY_EXISTS`, 404 `FOUND_REPORT_NOT_FOUND`.

### 10.4 PATCH /rescues/{id} · 10.5 PATCH /.../status

- `10.4` **요청 [초안]** `{ "rescueReason":"...", "rescueAddress":"...", "rescueMemo":"..." }`. **[확정 정책] R4 A:** 종료 상태(DONE/FAILED/CANCELED)에서는 같은 PATCH로 `rescueMemo`만 수정 가능하고, 다른 필드가 포함되면 400 `COMMON_INVALID_INPUT`. 나머지 필드는 PROCESSING에서만. **종료 후 수정 전용 엔드포인트는 두지 않습니다.**
- `10.5` **DONE 요청 [초안]** `{ "status":"DONE", "rescuedDate":"2026-10-29", "rescueAddress":"대구광역시 ...", "rescueReason":"배회 동물 구조", "rescueMemo":"..." }`.
- `10.5` **FAILED/CANCELED 요청 [초안]** `{ "status":"FAILED", "resultReason":"현장 도착 전 이동" }` 또는 CANCELED.
- **[확정 정책]** 최초 `PROCESSING → DONE/FAILED/CANCELED`. PROCESSING은 `completedDttm:null`; 종료 상태 3개는 서버가 시각 기록. DONE이면 `rescuedDate`와 공백 아닌 `rescueAddress` 필수. FAILED/CANCELED이면 공백 아닌 `resultReason` 필수.
- **오류 [초안]** 404 `RESCUE_NOT_FOUND`; 409 `RESCUE_INVALID_TRANSITION`; 400 `RESCUE_COMPLETION_FIELDS_REQUIRED`, `RESCUE_RESULT_REASON_REQUIRED`, `COMMON_INVALID_INPUT`(종료 상태에서 `rescueMemo` 외 필드).

### 10.6 DELETE /rescues/{id}

성공 200 `data:null`. **연결된 보호동물이 한 마리라도 있으면 삭제 거부**(409 `RESCUE_HAS_ANIMALS`), 없으면 404 `RESCUE_NOT_FOUND`. 발견 신고가 삭제되었다는 이유로 기존 구조 기록이 삭제되거나 중단되지 않습니다.

---

## 11. 보호동물·반환 (SHELTER ANIMAL)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 11.1 | GET | `/shelter-animals` | 로그인 | 보호동물 목록·검색 |
| 11.2 | GET | `/shelter-animals/{animalSeq}` | 로그인 | 보호동물 상세 |
| 11.3 | GET | `/shelters/me/animals` | SHELTER | 우리 보호소 등록 보호동물 목록 |
| 11.4 | POST | `/shelter-animals` | SHELTER | 직접/완료 구조 연계 보호동물 등록 |
| 11.5 | PATCH | `/shelter-animals/{animalSeq}` | 담당 SHELTER | 보호동물 기초 정보·사진 수정 |
| 11.6 | PATCH | `/shelter-animals/{animalSeq}/adoption-status` | 담당 SHELTER | AVAILABLE↔UNAVAILABLE 전환 |
| 11.7 | POST | `/shelter-animals/{animalSeq}/returns` | 담당 SHELTER | 실제 반환 완료 처리 |
| 11.8 | PATCH | `/shelter-animals/{animalSeq}/protection-status` | 담당 SHELTER | 반환/입양 외 종료(ENDED) 처리 등 |
| 11.9 | DELETE | `/shelter-animals/{animalSeq}` | 담당 SHELTER | 보호동물 논리 삭제(활성 신청 있으면 거부) |

### 11.1 GET /shelter-animals · 11.2 GET /shelter-animals/{id}

**검색 [확정 정책]** `?shelterSeq=7&districtCode=27110&protectionStatus=PROTECTING&adoptionStatus=AVAILABLE&speciesSeq=1&breedSeq=21&colorSeq=3&gender=UNKNOWN&keyword=순함&page=0&size=20&sort=noticeEndDate,asc`.
- 필터: `shelterSeq`, `districtCode`(보호소 소재지 기준 JOIN), `protectionStatus`(기본 PROTECTING), `adoptionStatus`, `speciesSeq`, `breedSeq`, `colorSeq`, `gender`, `keyword`(`description`·`healthCheck` ILIKE, S7).
- 정렬 화이트리스트: `regDttm`(기본), `noticeEndDate`.

**목록 200 `data.content[]` [초안]** `{ "animalSeq":700, "shelterSeq":7, "classification":{...}, "gender":"UNKNOWN", "protectionStatus":"PROTECTING", "adoptionStatus":"UNAVAILABLE", "representativeImageUrl":"...", "noticeEndDate":"2026-11-08" }`.

**상세 200 `data` [초안]**
```json
{
  "animalSeq": 700,
  "shelterSeq": 7,
  "rescueSeq": 301,
  "classification": { "combinationSeq": 88, "speciesSeq": 1, "breedSeq": 21, "colorSeq": 3 },
  "gender": "UNKNOWN",
  "age": "1살 추정",
  "isNeutered": null,
  "microchipNumber": null,
  "description": "사람을 잘 따름",
  "healthCheck": "기초 검진 완료",
  "vaccinationStatus": "예방접종 확인 중",
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-08",
  "protectionStatus": "PROTECTING",
  "adoptionStatus": "UNAVAILABLE",
  "endReason": null,
  "protectionEndedDttm": null,
  "rescueInfo": {
    "rescuedDate": "2026-10-29",
    "rescueReason": "길거리 구조",
    "rescueAddress": "대구광역시 ...",
    "rescueMemo": null
  },
  "images": [{ "imageSeq": 1701, "imageUrl":"https://...", "displayOrder":1 }],
  "isBookmarked": false,
  "simulation": { "speciesCode": "DOG", "personality": "FRIENDLY", "canStart": true, "activeSessionSeq": null }
}
```
- **[확정 정책]** 보호동물에 `name` 없음. `rescueInfo`는 **완료 구조 기록을 LEFT JOIN해 파생**하며 `rescueSeq=null`이면 `rescueInfo`의 4개 필드도 모두 `null` [초안: 객체 자체 null 대신]. DB에 `rescued_date/rescue_reason/rescue_address/rescue_memo`를 보호동물 컬럼으로 추가하지 않습니다.
- **[확정 정책]** PROTECTING이면서 비삭제이면 AVAILABLE/UNAVAILABLE 관계없이 AI 매칭 후보.
- **[초안] `simulation`** 블록은 상세 화면의 "모의 입양" 버튼용 파생 정보입니다. `speciesCode`는 조합→품종→종의 `species_code`, `personality`는 품종의 값(NULL 가능), `canStart`는 요청자가 USER이고 종이 DOG/CAT이며 동물이 보호 중이고 요청자에게 진행 중 세션이 없을 때 `true`, `activeSessionSeq`는 요청자의 진행 중 세션(있으면). 18.1 참조.
- **오류 [초안]** 11.2: 404 `ANIMAL_NOT_FOUND`(없음·삭제). 11.5~11.9: 404 `ANIMAL_NOT_FOUND`에 **다른 보호소 소속**도 포함(Q434). 11.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`.

### 11.4 POST /shelter-animals

**요청 [초안]**
```json
{
  "rescueSeq": 301,
  "combinationSeq": 88,
  "gender": "UNKNOWN",
  "age": "1살 추정",
  "isNeutered": null,
  "microchipNumber": null,
  "description": "사람을 잘 따름",
  "healthCheck": "기초 검진 완료",
  "vaccinationStatus": null,
  "imageSeqs": [1701, 1702]
}
```
**성공 201 `data` [초안]** `{ "animalSeq":700, "protectionStatus":"PROTECTING", "adoptionStatus":"UNAVAILABLE", "noticeStartDate":"2026-10-29", "noticeEndDate":"2026-11-08" }`.

- **SHELTER 전용.** `shelter_seq`는 토큰 `shelterSeq`로 서버가 설정하며 요청에 `shelterSeq` 필드는 **없습니다**(Q410). `rescueSeq` 선택. 값이 있으면 **DONE인 구조**만 참조 가능. 같은 구조에서 여러 마리를 등록할 수 있으며 `rescue_records.shelter_seq`와 새 보호동물의 보호소가 서로 달라도 연결을 허용합니다(DB v10 확정 규칙: 구조한 보호소와 보호하는 보호소가 다를 수 있음). 존재하지 않거나 삭제된 구조는 404 `RESCUE_NOT_FOUND`, 존재하지만 DONE이 아니면 409 `RESCUE_NOT_DONE`(Q442).
- `rescueSeq=null`이면 직접 등록이며 `rescueInfo`는 전부 null. USER는 작성 불가.
- **[확정 정책]** `PROTECTING+UNAVAILABLE` 초기 상태, 사진 1~3장, 공고 시작일=등록일, 종료일=시작+10일 (직접 등록 기준). 공공데이터 시드 적재 시에는 적재자가 공고 날짜를 직접 결정·입력합니다. 날짜 경과로 자동 상태 변경하지 않습니다(core에 스케줄러 없음, S8).
- `11.4`는 SHELTER의 수동 등록용이며 일반 요청에 공고 날짜를 받지 않습니다. 공공데이터 적재는 API가 아니라 Flyway 시드입니다(Q412).
- **오류 [초안]** 409 `RESCUE_NOT_DONE`; 404 `RESCUE_NOT_FOUND`, `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`.

### 11.5 PATCH /shelter-animals/{id}

- **요청 [초안]** `{ "combinationSeq":88, "gender":"UNKNOWN", "description":"...", "healthCheck":"...", "vaccinationStatus":"...", "noticeEndDate":"2026-11-15", "imageSeqs":[1702] }` (보내는 필드만 변경). `imageSeqs`는 전체 최종 배열(Q424). **[확정 정책] R5 A:** `noticeEndDate`는 담당 SHELTER가 이 PATCH의 일반 필드로 수정 가능(시작일 이후여야 함), `noticeStartDate`는 수정 불가. **공고 종료일 전용 엔드포인트는 두지 않습니다.** 기존 구조 정보는 보호동물 수정 요청에 포함하지 않으며 구조 수정이 필요하면 `10.4`에서 수정합니다. 범위는 토큰 `shelterSeq`.
- 사진 diff가 있고 PROTECTING이면 재매칭 작업 요청.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(타 보호소 포함), `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`; 400 `IMAGE_INVALID_COUNT`, `COMMON_INVALID_INPUT`(`noticeEndDate`가 시작일 이전).

### 11.6 PATCH /.../adoption-status

**요청 [초안]** `{ "adoptionStatus":"AVAILABLE" }` 또는 `UNAVAILABLE` → 성공 200 `data:{animalSeq,protectionStatus,adoptionStatus}`.

- **[확정 정책]** PROTECTING에서만 AVAILABLE/UNAVAILABLE 지정. RETURNED/ENDED는 UNAVAILABLE, ADOPTED는 ADOPTED여야 합니다. AVAILABLE→UNAVAILABLE이어도 기존 DRAFT/SUBMITTED/APPROVED 신청은 자동 거절하지 않습니다. 다만 **새 신청(12.4)과 기존 DRAFT의 제출(12.6)은 차단**합니다(Q437).
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`, 409 `ANIMAL_INVALID_STATUS_COMBINATION`.

### 11.7 POST /.../returns

**요청 [초안]**
```json
{ "recipientName":"김가람", "recipientPhone":"01012345678" }
```
**성공 201 `data` [초안]**
```json
{
  "returnSeq": 801,
  "animalSeq": 700,
  "recipientName": "김가람",
  "recipientPhone": "01012345678",
  "returnedUserSeq": 17,
  "regDttm": "2026-10-29T17:50:00+09:00",
  "protectionStatus": "RETURNED",
  "adoptionStatus": "UNAVAILABLE"
}
```
- **`regDttm`이 실제 반환 완료 처리 시각**입니다. DB 및 응답 어디에도 별도 `returnedDttm`을 두지 않습니다.
- **[확정 정책]** 실제로 인계가 완료된 사건만 생성, 보호동물당 1건 UNIQUE. 이름·전화 필수, 회원/비회원 모두 가능. 동일 전화번호로 활성 USER가 **정확히 1명** 일치하는 경우만 `returnedUserSeq` 연결하며 이것이 본인·소유권 검증을 의미하지 않습니다.
- 반환 INSERT, 보호 상태 RETURNED + 입양 상태 UNAVAILABLE + **`end_reason='RETURNED'` + `protection_ended_dttm`(= 반환 행의 `reg_dttm`)**, 해당 동물의 DRAFT/SUBMITTED/APPROVED 신청 자동 REJECTED를 **하나의 트랜잭션**으로 처리. 두 종료 컬럼을 함께 바꾸지 않으면 DB CHECK `ck_shelter_protection_end`에 걸립니다. 실종 신고·발견 신고 자동 종료 없음.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(타 보호소 포함); 409 `ANIMAL_ALREADY_RETURNED`, `ANIMAL_NOT_PROTECTING`; 400 `COMMON_INVALID_INPUT`(이름·번호 누락).

### 11.8 PATCH /.../protection-status · 11.9 DELETE /...

- `11.8` **요청 [초안]** `{ "protectionStatus":"ENDED", "endReason":"NATURAL_DEATH" }` (종료 사유는 RETURNED/ADOPTED/NATURAL_DEATH/EUTHANIZED/OTHER 중 규칙에 맞게 사용). 서버가 `protectionEndedDttm` 설정. **RETURNED는 11.7**, **ADOPTED는 12.11/12.12**를 통해 처리하여 관련 테이블 누락을 막습니다. 단순 상태 PATCH로 반환·입양 기록을 회피할 수 없도록 합니다.
- ENDED 처리 시 활성 신청 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED(사유 자연어, 처리시각 기록). **[확정 정책] R6 A:** ENDED는 담당 SHELTER 단독 처리이며 별도 승인 단계가 없고, `endReason`은 선택입니다(생략 시 `OTHER`).
- `11.8` **오류 [초안] (Q442):** `protectionStatus`가 `ENDED`가 아니거나 `endReason`이 `RETURNED/ADOPTED`이면 400 `COMMON_INVALID_INPUT`(각각 11.7·12.11/12.12로만 처리); 현재 PROTECTING이 아니면(이미 종료) 409 `ANIMAL_NOT_PROTECTING`; 404 `ANIMAL_NOT_FOUND`. 서버는 `end_reason`과 `protection_ended_dttm`을 함께 설정합니다.
- `11.9` 성공 200 `data:null`. DRAFT/SUBMITTED/APPROVED 신청서가 하나라도 있으면 **409 `ANIMAL_HAS_ACTIVE_APPLICATIONS`**, 없으면 404 `ANIMAL_NOT_FOUND`. 삭제된 보호동물은 일반 조회/AI 제외.

---
## 12. 입양 문항·신청·심사·완료 (ADOPTION)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 12.1 | GET | `/adoption/questions` | USER/SHELTER | 현재 공통 21개 질문 조회 |
| 12.2 | GET | `/users/me/adoption-applications` | USER | 내 입양 신청 내역 |
| 12.3 | GET | `/adoption-applications/{applicationSeq}` | 신청자/담당 SHELTER | 신청서 상세·답변·심사 결과 |
| 12.4 | POST | `/adoption-applications` | USER | DRAFT 생성 |
| 12.5 | PATCH | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | 미제출 답변 저장·수정 |
| 12.6 | POST | `/adoption-applications/{applicationSeq}/submit` | 신청자(USER) | 문항 검증 후 제출 |
| 12.7 | DELETE | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | DRAFT 물리 삭제 |
| 12.8 | POST | `/adoption-applications/{applicationSeq}/cancel` | 신청자(USER), SUBMITTED만 | 제출 신청 취소 |
| 12.9 | GET | `/shelters/me/adoption-applications` | SHELTER | 소속 보호소 신청 목록/심사 대기 |
| 12.10 | POST | `/adoption-applications/{applicationSeq}/review` | 담당 SHELTER | 서류 APPROVED/REJECTED |
| 12.11 | POST | `/adoption-applications/{applicationSeq}/complete` | 담당 SHELTER | 승인 신청 기준 실제 입양 완료 |
| 12.12 | POST | `/shelter-animals/{animalSeq}/external-adoption` | 담당 SHELTER | 서비스 외부 경로 실제 입양 완료 |
| 12.13 | POST | `/adoption-applications/{applicationSeq}/fail` | 담당 SHELTER | 승인 후 진행 실패 → REJECTED |
| 12.15 | GET | `/adoption-applications/{applicationSeq}/simulation-reports` | 담당 SHELTER | 신청자가 이 동물로 진행한 모의 입양 리포트(18.21 참조) |

### 12.1 GET /adoption/questions

**성공 200 `data` [초안]**
```json
{
  "questions": [
    {
      "questionSeq": 101,
      "displayOrder": 1,
      "questionText": "반려동물을 입양하려는 이유를 적어 주세요.",
      "questionType": "TEXT",
      "isRequired": true,
      "options": null,
      "condition": null
    },
    {
      "questionSeq": 102,
      "displayOrder": 2,
      "questionText": "동거 가족의 입양 동의 여부를 선택해 주세요.",
      "questionType": "SINGLE_CHOICE",
      "isRequired": true,
      "options": ["동의", "논의 중"],
      "condition": null
    }
  ]
}
```

- **예시의 실제 질문 문구/보기/ID는 공통 21문항 원본이 아니며 설명용 가상 값입니다.** DB v10에는 실제 질문 텍스트 21개가 수록되지 않았으므로 이 API의 실제 운영 데이터는 Flyway 시드로 적재합니다(Q412). 문항 관리 API는 없습니다.
- **[확정 정책]** 현재 문항 21개만 조회, `displayOrder` 순서대로 UI Q1~Q21 표시. `questionType`: `TEXT/SINGLE_CHOICE/MULTI_CHOICE`. `options`는 DB `options_jsonb`의 문자열 배열; `condition`은 `condition_jsonb`에서 `depends_on`(questionSeq), `operator`, `value`, `required_when_matched` 등을 반환합니다.
- **질문 버전/템플릿 ID는 반환하지 않습니다.** 이전 제출 답변도 현재 문항으로 보여주는 정책입니다.

### 12.2 GET /users/me/adoption-applications · 12.3 GET /adoption-applications/{id}

**목록 [초안]** `?status=SUBMITTED&page=0&size=20&sort=regDttm,desc` → `data.content[]`에 `applicationSeq`, `animalSeq`, `shelterSeq`, `status`, 보호동물 대표 이미지, `regDttm`, `submittedDttm`, `rejectionReason` 요약 포함. 필터 `status`, `animalSeq`. 정렬 화이트리스트 `regDttm`, `submittedDttm`.

**상세 성공 200 `data` [초안]**
```json
{
  "applicationSeq": 901,
  "userSeq": 17,
  "animalSeq": 700,
  "shelterSeq": 7,
  "status": "SUBMITTED",
  "answers": {
    "101": "가족으로 함께 살고 싶습니다.",
    "102": "동의"
  },
  "questions": [
    { "questionSeq":101, "questionText":"반려동물을 입양하려는 이유를 적어 주세요.", "displayOrder":1 }
  ],
  "certificateUrl": null,
  "applicant": {
    "name":"김가람",
    "birthDate":"1997-05-12",
    "phone":"01012345678"
  },
  "submittedDttm": "2026-10-29T17:50:00+09:00",
  "reviewedDttm": null,
  "reviewer": null,
  "rejectionReason": null
}
```

- **[확정 정책]** DRAFT에서는 신청자 인적사항을 현재 `users`에서 읽고, SUBMITTED 이후에는 `applicant_name/birth_date/phone` 스냅샷을 조회합니다. 작성된 답변 `answers`는 JSONB 객체이며 키는 **문자열 `questionSeq`**입니다.
- **[확정 정책] `certificateUrl`(Q443):** `certificate_storage_key`가 있으면 서버가 이 응답을 만들 때 **presigned GET URL**(TTL 10분 [가안])을 생성해 넣고, 없으면 `null`. 수료증 객체는 비공개라 URL이 만료되면 상세를 다시 조회해야 합니다. 사진(`images/`)과 달리 고정 URL이 아닙니다.
- 신청자 본인 외에는 소속 보호소 담당자의 업무 조회로만 상세 노출. 탈퇴 신청자의 과거 신청서와 수료증도 업무상 유지합니다(일반 공개 API에는 노출하지 않음). 담당 보호소는 12.15로 신청자의 해당 동물 모의 입양 리포트를 함께 열람할 수 있습니다(M14·M17, FK 없는 파생 조회).
- 문항을 수정해도 과거 `answers`는 **현재 문항 문구**와 함께 표시합니다. 별도 이력/버전/원본 문항 스냅샷 요청 API는 만들지 않습니다.
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`. 신청자 본인도 담당 보호소도 아니면 403이 아니라 이 404로 존재를 감춥니다(Q434; 종전 `ADOPTION_APPLICATION_ACCESS_DENIED` 삭제).

### 12.4 POST /adoption-applications (DRAFT 생성)

**요청 [초안]**
```json
{ "animalSeq": 700, "answers": {}, "certificateStorageKey": null }
```
**성공 201 `data` [초안]** `{ "applicationSeq":901, "status":"DRAFT", "animalSeq":700 }`.

- **[확정 정책]** USER만 생성. 동물이 PROTECTING + AVAILABLE인 경우만 신규 신청을 받습니다. 동일 사용자·동물 비삭제 DRAFT/SUBMITTED/APPROVED 합계 최대 1개. DRAFT는 빈 `answers` 허용.
- `shelterSeq`는 서버가 대상 보호동물에서 읽어 **신청 당시 스냅샷** 저장. 고객이 임의 전달하지 않습니다.
- **오류 [초안]** 409 `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`, `ADOPTION_ANIMAL_NOT_AVAILABLE`; 404 `ANIMAL_NOT_FOUND`.

### 12.5 PATCH /adoption-applications/{id} (DRAFT 저장)

**요청 [초안]**
```json
{
  "answers": { "101":"가족으로 함께 살고 싶습니다.", "102":"동의" },
  "certificateStorageKey": "certificates/generated.pdf"
}
```
**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"DRAFT", "savedQuestionCount":2 }`.

- **[확정 정책]** DRAFT만 수정. 단일 `answers_jsonb` 객체를 갱신; `questionSeq` 이외의 임의 키 불허 제안. 조건부 숨김 답변은 FE/BE에서 제거. 주관식은 문자열, 단일 선택은 보기 문자열, 복수 선택은 문자열 배열을 **[초안] DTO 규칙**으로 제안합니다.
- **[확정 정책]** 요청의 `answers`는 **객체 전체 교체**로 정의해 PATCH 부분 키 병합 혼동을 피합니다. `certificateStorageKey`는 3.2의 `profileImageSeq`와 같은 규칙으로 `null`은 삭제, 미전달은 유지입니다. 새 키는 4.3 발급자 일치와 S3 `HEAD` 검증을 통과해야 하며, 교체·삭제로 참조를 잃은 이전 수료증 객체는 커밋 후 best-effort 삭제(Q427).
- **오류 [초안]** 409 `ADOPTION_APPLICATION_NOT_DRAFT`, 400 `ADOPTION_INVALID_ANSWER`, 404 `ADOPTION_CERTIFICATE_NOT_FOUND`.

### 12.6 POST /adoption-applications/{id}/submit

**요청 [초안]** `{}` 또는 본문 없음. **성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"SUBMITTED", "submittedDttm":"2026-10-29T17:50:00+09:00" }`.

- **[확정 정책]** DRAFT→SUBMITTED, 별도 새 신청서 행 생성하지 않음. 현재 질문 **21개** 기준 조건/필수/보기 응답 검증. 숨겨진 답변 제거, 제출 후 답변 불변. 신청자 실명·생년월일·전화번호를 **제출 시점**에 스냅샷.
- 제출 시 대상 동물의 입양 가능 여부(PROTECTING + AVAILABLE)와 동시성 재검증. **동물이 UNAVAILABLE로 바뀐 뒤에는 기존 DRAFT도 제출할 수 없습니다**(409 `ADOPTION_ANIMAL_NOT_AVAILABLE`, Q437). DRAFT 행 자체는 유지되며 다시 AVAILABLE이 되면 제출 가능합니다. 이 시점 이후에도 동일 회원·동물 활성 신청서 중복 생성 불가.
- 제출 시 보호소 SHELTER 계정에 `ADOPTION` 알림(17.8, Q426).
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타인 신청서 포함); 409 `ADOPTION_APPLICATION_NOT_DRAFT`, `ADOPTION_ANIMAL_NOT_AVAILABLE`; 400 `ADOPTION_REQUIRED_ANSWER_MISSING`, `ADOPTION_INVALID_ANSWER`.

### 12.7 DELETE /adoption-applications/{id} · 12.8 POST /.../cancel

- `12.7` DRAFT만 **DB에서 실제 물리 삭제**하고 답변도 제거. 성공 200 `data:null`; DRAFT가 아니면 409 `ADOPTION_APPLICATION_NOT_DRAFT`. 수료증 객체는 커밋 후 best-effort 삭제, 실패는 로그(Q427).
- `12.8` **요청 [초안]** `{}` → 성공 200 `data:{applicationSeq,status:"CANCELED"}`. **SUBMITTED에서만 USER 직접 취소**; APPROVED 뒤 직접 취소 API 없음. `cancellationReason`이나 별도 취소 이력 컬럼은 추가하지 않음.
- 탈퇴 시 활성(DRAFT/SUBMITTED/APPROVED) 신청을 CANCELED로 자동 변경하는 것은 별도 사용자의 직접 취소 제한과 구분합니다.

### 12.9 GET /shelters/me/adoption-applications

**요청 [초안]** `?animalSeq=700&status=SUBMITTED&page=0&size=20&sort=submittedDttm,desc`. 필터 `animalSeq`, `status`(DRAFT 지정 불가). 정렬 화이트리스트 `submittedDttm`(기본), `regDttm`.

**성공 200 `data.content[]` [초안]** `{ "applicationSeq":901, "animalSeq":700, "applicantName":"김가람", "status":"SUBMITTED", "submittedDttm":"...", "certificateAttached":true }`.

- **[확정 정책]** 소속 보호소(토큰 `shelterSeq`)에 제출된 신청 및 보존된 과거 기록을 업무상 조회. 심사 대상은 SUBMITTED. **DRAFT는 목록·상세·건수 어디에도 노출하지 않습니다**(Q422 A). 12.3을 DRAFT 신청서에 SHELTER가 호출하면 404 `ADOPTION_APPLICATION_NOT_FOUND`.
- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(`status=DRAFT` 지정, 화이트리스트 밖 정렬).

### 12.10 POST /adoption-applications/{id}/review

**APPROVE 요청 [초안]** `{ "decision":"APPROVE" }`.

**REJECT 요청 [초안]** `{ "decision":"REJECT", "rejectionReason":"현재 보호 환경이 입양 조건에 부합하지 않습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"APPROVED", "reviewedDttm":"2026-10-29T17:50:00+09:00", "reviewedUsrSeq":55 }`.

- **[확정 정책]** `SUBMITTED → APPROVED/REJECTED`; REJECTED면 공백 아닌 **자연어 설명** 필수. `reviewedUsrSeq`에는 수동 처리자, `reviewedDttm`에는 심사 시각. 상태 이력/거절 단계/거절 코드 생성 금지.
- 여러 사용자 신청서의 APPROVED가 동시에 존재할 수 있습니다. **서류 승인만으로 보호동물을 ADOPTED로 만들지 않습니다.**
- 심사 결과는 신청자에게 `ADOPTION` 알림(17.8, Q426).
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 신청서 포함); 409 `ADOPTION_APPLICATION_NOT_SUBMITTED`; 400 `ADOPTION_REJECTION_REASON_REQUIRED`.

### 12.11 POST /adoption-applications/{id}/complete (실제 입양 완료)

**요청 [초안]** `{}`.

**성공 200 `data` [초안]**
```json
{
  "applicationSeq": 901,
  "applicationStatus": "ADOPTED",
  "animalSeq": 700,
  "protectionStatus": "ADOPTED",
  "adoptionStatus": "ADOPTED",
  "completedApplicationSeq": 901,
  "otherApplicationsAutoRejected": 3
}
```

- **[확정 정책]** 대상 신청서는 APPROVED 상태이고 실제 입양이 완료된 경우에만 호출. **원자적 변경:** 채택 신청서 ADOPTED → 보호동물 `protection_status=ADOPTED` + `adoption_status=ADOPTED` + `end_reason='ADOPTED'` + `protection_ended_dttm=now()` + `completed_application_seq=해당 신청서` → 다른 신청자의 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED. 종료 컬럼 두 개는 DB CHECK `ck_shelter_protection_end` 때문에 함께 설정합니다.
- 자동 거절 `rejectionReason`은 실제 사유를 설명하는 한국어 문장; `reviewedDttm`은 자동 거절 시각, `reviewedUsrSeq=null`. 이미 승인된 다른 신청의 예전 심사시각은 별도로 남지 않습니다.
- 채택 신청자에게 `ADOPTION`(ADOPTED) 알림, 자동 거절된 신청자들에게 `ADOPTION`(REJECTED) 알림(17.8, Q426).
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 포함); 409 `ADOPTION_APPLICATION_NOT_APPROVED`, `ADOPTION_ANIMAL_ALREADY_FINISHED`.

### 12.12 POST /shelter-animals/{animalSeq}/external-adoption

**요청 [초안]** `{}`. **성공 200 `data` [초안]** `{ "animalSeq":700, "protectionStatus":"ADOPTED", "adoptionStatus":"ADOPTED", "completedApplicationSeq":null }`.

- **[확정 정책]** 앱 내 신청서 없이 외부 경로를 통해 실제 입양 완료 가능. 서비스 신청 완료 FK는 **NULL 유지**. 반면 동물 상태는 ADOPTED/ADOPTED + `end_reason='ADOPTED'` + `protection_ended_dttm`으로 변경하며 기존 활성 신청은 자연어 이유와 함께 자동 REJECTED(12.11과 같은 트랜잭션 구성).
- 외부 입양자의 신상/입양 시각은 현행 테이블에 별도 칼럼이 없으므로 받지 않습니다(범위 외).
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(타 보호소 포함); 409 `ANIMAL_NOT_PROTECTING`(이미 반환·입양·종료된 동물).

### 12.13 POST /adoption-applications/{id}/fail

**요청 [초안]** `{ "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"REJECTED", "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

- **[확정 정책]** 승인 후 실제 입양 진행 실패 시 해당 APPROVED 신청서를 REJECTED로 변경하고 `reviewedDttm`을 실패 처리 시각으로 갱신. 별도 입양 프로세스/실패 이력 테이블 생성 금지. 호출 주체는 담당 보호소. 신청자에게 `ADOPTION`(REJECTED) 알림.
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 포함); 409 `ADOPTION_APPLICATION_NOT_APPROVED`; 400 `ADOPTION_REJECTION_REASON_REQUIRED`.

### 12.14 자동 상태 변경과 입양 알림 (클라이언트 이벤트)

| 발생 사건 | 신청서 변화 | 보호동물 변화 | 비고 |
|---|---|---|---|
| DRAFT 저장 | DRAFT 유지 | 없음 | 빈 답변 허용 |
| 제출 | DRAFT→SUBMITTED | 없음 | 21문항 조건 검증 |
| 서류 승인 | SUBMITTED→APPROVED | 없음 | 다수 승인 허용 |
| 서류 거절 | SUBMITTED→REJECTED | 없음 | 거절 사유 필수 |
| 직접 취소 | SUBMITTED→CANCELED | 없음 | 회원 직접 처리만 |
| 입양 완료 | 채택 APPROVED→ADOPTED; 다른 활성→REJECTED | ADOPTED/ADOPTED | 동일 트랜잭션 |
| 외부 입양 완료 | 다른 활성→REJECTED | ADOPTED/ADOPTED, 완료 FK NULL | 동일 트랜잭션 |
| 실제 반환 완료 | 활성→REJECTED | RETURNED/UNAVAILABLE | 반환 기록 생성 |
| 보호 종료(ENDED) | 활성→REJECTED | ENDED/UNAVAILABLE | 자연어 자동 거절 |
| AVAILABLE→UNAVAILABLE | **기존 활성 상태 유지**(DRAFT 행도 유지하되 제출 불가) | PROTECTING+UNAVAILABLE | 새 신청(12.4)과 DRAFT 제출(12.6) 차단(Q437) |
| 회원 탈퇴 | 본인 활성→CANCELED | 없음 | ADOPTED 이력은 보존 |

**[확정 정책] `ADOPTION` 알림 이벤트(Q426 A):**

| 전이 | 수신자 | 비고 |
|---|---|---|
| 제출(DRAFT→SUBMITTED) | 담당 보호소 SHELTER 계정 | `targetType=ADOPTION`, `targetSeq=applicationSeq` |
| 서류 승인·거절 | 신청자 | 거절 사유는 알림 `message`에 포함하지 않음(상세에서 조회) |
| 입양 완료(채택) | 채택 신청자 | |
| 자동 거절(타인 입양 완료·외부 입양·반환·ENDED) | 거절된 각 신청자 | 1건씩 |
| 승인 후 실패(12.13) | 신청자 | |
| 직접 취소·DRAFT 저장·회원 탈퇴 | 없음 | |

알림은 core-api가 트랜잭션 커밋 후 RabbitMQ 이벤트로 발행하고 notification 서비스가 `notifications` 저장 + FCM을 수행합니다(17.8).

---

## 13. 댓글·답글 (COMMENT)

> **공통 다형성 API [초안]**: 세 댓글 테이블을 하나의 REST 계약으로 표현하되 실제 저장은 `missing_report_comments` / `found_report_comments` / `shelter_animal_comments`로 각각 분리합니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 13.1 | GET | `/comments` | 로그인 | 대상 게시물의 루트 댓글 조회 |
| 13.2 | GET | `/comments/{commentType}/{commentSeq}/replies` | 로그인 | 해당 루트의 답글 조회 |
| 13.3 | POST | `/comments` | USER/SHELTER | 댓글·답글 생성 |
| 13.4 | PATCH | `/comments/{commentType}/{commentSeq}` | 작성자 | 댓글·답글 수정 |
| 13.5 | DELETE | `/comments/{commentType}/{commentSeq}` | 작성자 | 댓글 소프트 삭제 |

**타입 매핑 — 반드시 구분**

| 게시물 `targetType` | 실제 게시물 PK | `commentType` | 댓글 테이블 |
|---|---|---|---|
| `MISSING` | `missing_report_seq` | `MISSING_COMMENT` | `missing_report_comments` |
| `FOUND` | `found_report_seq` | `FOUND_COMMENT` | `found_report_comments` |
| `SHELTER_ANIMAL` | `animal_seq` | `SHELTER_ANIMAL_COMMENT` | `shelter_animal_comments` |

### 13.1 GET /comments · 13.2 GET /.../replies

**요청 [초안]** `GET /comments?targetType=MISSING&targetSeq=101&page=0&size=20&sort=regDttm,asc`; 답글은 `GET /comments/MISSING_COMMENT/44/replies?page=0&size=20`. 정렬 화이트리스트 `regDttm`(댓글은 기본 `asc`, 답글은 `asc` 고정).

**댓글 DTO [초안]**
```json
{
  "commentType": "MISSING_COMMENT",
  "commentSeq": 44,
  "parentCommentSeq": null,
  "targetType": "MISSING",
  "targetSeq": 101,
  "author": { "userSeq":17, "displayName":"반려가족", "role":"USER" },
  "body": "근처에서 본 것 같아요.",
  "image": null,
  "isDeleted": false,
  "replyCount": 2,
  "regDttm": "2026-10-29T17:50:00+09:00",
  "modDttm": null
}
```
- **[확정 정책]** 답글 깊이 최대 1, 부모와 동일 게시물 필수. 삭제된 루트는 **삭제 안내로 표시하고 기존 자식 답글은 유지**합니다. 탈퇴한 루트 작성자의 답글은 보존하되 **새 답글 작성은 차단**합니다.
- **[가안]** 삭제된 댓글은 `body:null`·`image:null`·`isDeleted:true`로 반환합니다. `replyCount`와 게시물의 `commentCount`는 비삭제 행만 셉니다.

### 13.3 POST /comments

**루트 댓글 요청 [초안]**
```json
{ "targetType":"MISSING", "targetSeq":101, "parentCommentSeq":null, "body":"근처에서 본 것 같아요.", "imageSeq":null }
```
**답글 요청 [초안]**
```json
{ "targetType":"MISSING", "targetSeq":101, "parentCommentSeq":44, "body":null, "imageSeq":1701 }
```
**성공 201 `data` [초안]** `{ "commentType":"MISSING_COMMENT", "commentSeq":45, "parentCommentSeq":44 }`.

- **[확정 정책]** 텍스트와 이미지 중 **최소 하나 필수**. `imageSeq`는 0~1개만 직접 연결. 본문만/이미지만/둘 다 가능. 사용자 댓글은 `user_seq`, SHELTER 담당자의 댓글도 실제 계정 `user_seq`에 저장하고 표시명은 소속 보호소명 조회.
- **[확정 정책]** 새 답글은 루트에만 허용. 탈퇴한 루트 댓글에는 신규 답글 불가. **[가안]** 삭제된 부모 댓글에도 새 답글은 불가(409 `COMMENT_PARENT_UNAVAILABLE`), 기존 답글 보존은 확정.
- `imageSeq`는 요청자가 업로드한 이미지만 연결 가능(403 `IMAGE_NOT_OWNED`, Q407).
- `COMMENT` 알림 수신자(17.8, Q426·Q436): **루트 댓글이면 게시물 작성자**, **답글이면 루트 댓글 작성자만**(게시물 작성자에게는 보내지 않음). 어느 경우든 본인에게는 보내지 않습니다.
- **오류 [초안]** 400 `COMMENT_EMPTY`, `COMMENT_DEPTH_EXCEEDED`; 403 `IMAGE_NOT_OWNED`; 404 `COMMENT_TARGET_NOT_FOUND`; 409 `COMMENT_PARENT_UNAVAILABLE`.

### 13.4 PATCH /comments/{type}/{id} · 13.5 DELETE /comments/{type}/{id}

- `13.4` **요청 [초안]** `{ "body":"수정된 설명", "imageSeq":null }` → 성공 200 수정 댓글. `null`은 해제, 미전달은 유지. `body`·`imageSeq` 둘 다 빈 상태가 되면 400 `COMMENT_EMPTY`. 작성자 본인만(Q423), 아니면 404 `COMMENT_NOT_FOUND`.
- `13.5` 성공 200 `data:null`. 작성자 본인만(Q423). **루트 삭제 시 자식 답글은 삭제하지 않음**; 삭제 표시를 유지. 원래 이미지 참조가 마지막이면 공통 이미지 원본을 커밋 후 best-effort 삭제(Q427).
- 댓글에 신고 기능을 적용할 때 신고 API는 **§15**를 사용합니다(`targetType=MISSING_COMMENT/FOUND_COMMENT/SHELTER_ANIMAL_COMMENT`).

---

## 14. 채팅 (CHAT) — 서비스 `chat`, 접두어 `/api/chat/v1`

> **[확정 정책] 서비스·저장소:** chat 서비스(8081)가 담당하며 `core` 스키마의 `chat_rooms`·`chat_participants`·`chat_messages`를 **쓰고**, `users`·`shelter_members`·`shelters`·`images`는 읽기 전용 JOIN으로만 참조합니다(Q420 D). 상대 표시명·프로필 URL·탈퇴 여부는 이 JOIN으로 구합니다.
>
> **[확정 정책] 실시간(S5 B):** WebSocket + STOMP, 엔드포인트 **`/api/chat/v1/ws`**(핸드셰이크는 `security.permit-paths`로 열어 둠). 브로커는 **RabbitMQ STOMP relay**(`enableStompBrokerRelay`)이며 chat 인스턴스를 여러 개 띄워도 같은 방을 공유합니다.
> - **CONNECT 인증:** CONNECT 프레임의 `Authorization: Bearer {accessToken}` 헤더를 `StompAuthChannelInterceptor`가 검증합니다. 없거나 틀리면 연결 거부(`AUTH_UNAUTHORIZED`/`AUTH_TOKEN_*`).
> - **SUBSCRIBE 인가:** 구독 목적지 `/topic/rooms/{chatRoomSeq}`. common의 `StompAuthChannelInterceptor`는 CONNECT 인증만 담당하고 DB에 접근하지 않으므로, **chat 모듈의 별도 `ChannelInterceptor`**가 SUBSCRIBE 프레임에서 요청자가 `chat_participants`에 있는지 확인하고 아니면 구독을 거부합니다(`CHAT_ROOM_NOT_PARTICIPANT`). `/user/queue/errors` 구독은 본인 큐이므로 검사 없이 허용합니다. 참여자가 아닌 방을 엿들을 수 없습니다.
> - **전송(R16 B):** 클라이언트가 STOMP **SEND** 프레임으로 `/app/rooms/{chatRoomSeq}/messages`에 보냅니다(14.5). 서버 `@MessageMapping`이 참여자·조합·탈퇴 상대·첨부 검증 → `chat_messages` 저장·커밋 → `/topic/rooms/{chatRoomSeq}`로 저장된 메시지 DTO를 브로드캐스트합니다(RabbitMQ relay 경유). 쓰기 경로는 이 하나뿐이며 **REST 메시지 전송은 없습니다.** 방 목록·생성·상세·과거 메시지 조회·읽음·나가기만 REST입니다.
> - **오류 응답(STOMP):** 검증 실패는 `@MessageExceptionHandler`가 송신자의 `/user/queue/errors`로 보냅니다. 본문은 HTTP와 **같은 `ApiResponse` 봉투**(`status`·`code`·`message`·`data:null`·`errors`·`traceId`)이고 코드도 19장과 같습니다(`CHAT_ROOM_NOT_PARTICIPANT`, `CHAT_INVALID_PAYLOAD`, `CHAT_COUNTERPART_DELETED`, `CHAT_SHARED_POST_NOT_FOUND`, `IMAGE_NOT_OWNED`, `IMAGE_NOT_FOUND`). 클라이언트는 연결 직후 `/user/queue/errors`를 구독합니다.
> - **푸시(R9 C):** 서버는 구독 여부를 판단하지 않습니다. 저장·커밋 후 chat이 `ChatMessageSent` 이벤트를 내고, notification이 참여자 중 **보낸 사람을 제외한 전원**에게 FCM **데이터 메시지**(`roomSeq`, `messageSeq`, `senderDisplayName`, `preview`)를 보냅니다. 표시 여부는 앱이 "현재 열려 있는 방" 기준으로 판단합니다(같은 방이면 무시, 아니면 로컬 알림). DB 알림 기록은 없습니다(17장).

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 14.1 | GET | `/chats/rooms` | USER/SHELTER | 내 대화방 목록·미읽음 계산 |
| 14.2 | POST | `/chats/rooms` | USER/SHELTER | 상대와 대화방 생성 또는 기존 방 재입장 |
| 14.3 | GET | `/chats/rooms/{chatRoomSeq}` | 참여자 | 방 상세·상대방 정보 |
| 14.4 | GET | `/chats/rooms/{chatRoomSeq}/messages` | 참여자 | 메시지 내역 조회 |
| 14.5 | STOMP SEND | `/app/rooms/{chatRoomSeq}/messages` | 참여자 | TEXT/IMAGE/SHARED_POST 발송 (R16 B, REST 아님) |
| 14.6 | PATCH | `/chats/rooms/{chatRoomSeq}/read` | 참여자 | 마지막 읽은 메시지 갱신 |
| 14.7 | POST | `/chats/rooms/{chatRoomSeq}/leave` | 참여자 | 대화방 나가기/기존 기록 숨김 |

### 14.1 GET /chats/rooms

**요청 [초안]** `?page=0&size=20`. 정렬은 `lastMessageDttm,desc` 고정(화이트리스트 없음). 필터 없음.

**목록 200 `data.content[]` [초안]**
```json
{
  "chatRoomSeq": 501,
  "roomType": "SHELTER",
  "counterpart": { "type":"SHELTER", "shelterSeq":7, "displayName":"행복동물보호센터" },
  "lastMessage": { "chatMessageSeq":4002, "messageType":"TEXT", "body":"확인했습니다." },
  "unreadCount": 2,
  "lastMessageDttm": "2026-10-29T17:50:00+09:00"
}
```
- **[확정 정책]** 미읽음은 DB 카운터 컬럼이 아니라 **조회 시 계산**합니다. 나가기 기준보다 오래된 메시지는 조회 결과에서 숨깁니다.

### 14.2 POST /chats/rooms

**USER 대상 요청 [초안]** `{ "targetType":"USER", "targetUserSeq":18 }`.

**보호소 대상 요청 [초안]** `{ "targetType":"SHELTER", "targetShelterSeq":7 }`.

**성공 200 또는 최초 생성 201 `data` [초안]** `{ "chatRoomSeq":501, "roomType":"SHELTER", "reused":true }`.

- **[확정 정책]** USER↔USER, USER↔SHELTER 1:1만 지원; SHELTER↔SHELTER 불가. 상대 조합당 방 하나. USER형 `user_a_seq/user_b_seq`는 오름차순 기준. 보호소당 계정 하나. 기존 방 재사용 시 예전 나가기 이전 메시지는 계속 숨김.
- **[가안]** SHELTER도 먼저 USER에게 방을 만들 수 있습니다(발견 신고·입양 신청 화면의 작성자에게 연락). `targetShelterSeq`와 `targetUserSeq`는 분기별로 하나만 허용.
- **오류 [초안]** 409 `CHAT_UNSUPPORTED_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED`; 404 `CHAT_TARGET_NOT_FOUND`.

### 14.3 GET /chats/rooms/{id} · 14.4 GET /chats/rooms/{id}/messages

- `14.3` 성공 200 `data:{chatRoomSeq,roomType,counterpart,lastReadMessageSeq,lastHiddenMessageSeq,unreadCount}`.
- `14.4` 요청 **[확정 정책]** `?beforeMessageSeq=4000&size=30` (**채팅 메시지는 offset 대신 seq 기준 역방향 커서**, 일반 목록 규약의 유일한 예외, Q417). 최초는 `beforeMessageSeq` 생략. `size` 최대 100. **응답 [초안]** `{ "content":[ChatMessage], "nextBeforeMessageSeq":3990, "hasNext":true }`.
- **[확정 정책]** 현재 참여자의 `last_hidden_message_seq` 이후만 반환. 공유 게시물이 삭제되면 메시지는 유지하고 게시물 상세는 `sharedPostUnavailable:true`로 표시 [초안]. 탈퇴 송신자는 `(탈퇴한 사용자)`로 표시.
- **[초안]** 해당 메시지 방 소속/정렬/페이징 경계는 서버에서 검증합니다.
- **오류 [초안]** 14.3·14.4·14.6·14.7 공통 404 `CHAT_ROOM_NOT_FOUND`(없는 방 또는 **요청자가 참여자가 아닌 방**, Q434). REST에서는 `CHAT_ROOM_NOT_PARTICIPANT`(403)를 쓰지 않습니다(STOMP 전용). 14.4: `beforeMessageSeq`가 이 방 메시지가 아니면 400 `CHAT_MESSAGE_NOT_IN_ROOM`.

### 14.5 STOMP SEND /app/rooms/{id}/messages

> **[확정 정책] R16 B:** 메시지 전송은 REST가 아니라 STOMP SEND입니다. v2의 `POST /chats/rooms/{id}/messages`는 **삭제(R16)**했고 번호 14.5는 이 SEND 규약에 재사용합니다.

**SEND 프레임** 목적지 `/app/rooms/{chatRoomSeq}/messages`, 본문 JSON **[초안]**:

**TEXT** `{ "clientMessageId":"c-20261102-0001", "type":"TEXT", "content":"안녕하세요." }`

**IMAGE** `{ "clientMessageId":"c-20261102-0002", "type":"IMAGE", "imageSeq":1701, "content":"사진입니다." }`

**SHARED_POST** `{ "clientMessageId":"c-20261102-0003", "type":"SHARED_POST", "sharedTargetType":"FOUND", "sharedTargetSeq":210, "content":null }`

**브로드캐스트 `/topic/rooms/{chatRoomSeq}` 본문 [초안]** (방 참여자 전원이 수신, 송신자 포함)
```json
{
  "chatMessageSeq": 4003,
  "clientMessageId": "c-20261102-0003",
  "chatRoomSeq": 501,
  "senderUserSeq": 17,
  "messageType": "SHARED_POST",
  "body": null,
  "imageSeq": null,
  "imageUrl": null,
  "sharedTargetType": "FOUND",
  "sharedTargetSeq": 210,
  "regDttm": "2026-10-29T17:50:00+09:00"
}
```

- **[확정 정책] 처리 순서:** `@MessageMapping`이 (1) 송신자가 `chat_participants`에 있는지, (2) 상대가 탈퇴하지 않았는지, (3) 본문 종류별 필수값을 검증 → `chat_messages` INSERT·커밋 → `/topic/rooms/{chatRoomSeq}`로 위 DTO 브로드캐스트(RabbitMQ relay) → `ChatMessageSent` 이벤트 발행(푸시는 notification, 14장 머리말). 인증 주체는 CONNECT 시 검증된 `AuthUser`이며 `senderUserSeq`는 그 `userSeq`(보호소 담당자도 users PK)입니다.
- **[확정 정책] 전송 확인·중복 제거:** 송신자는 브로드캐스트에서 자기가 보낸 `clientMessageId`를 받으면 "전송 완료"로 표시하고 `chatMessageSeq`를 붙입니다. `clientMessageId`는 클라이언트가 생성하는 임의 문자열(최대 64자)이며 서버는 저장하지 않고 **되돌려 주기만** 합니다. 재연결 후 재전송은 클라이언트 책임이며 서버 측 멱등 처리는 없습니다 **[가안]**.
- **[확정 정책] 본문 규칙:** TEXT는 비공백 `content` 필수. IMAGE는 `imageSeq` **정확히 하나 필수**(송신자가 업로드한 이미지만, 아니면 `IMAGE_NOT_OWNED`), `content`는 선택. SHARED_POST는 `sharedTargetType/sharedTargetSeq` 필수이며 코드 `MISSING/FOUND/SHELTER_ANIMAL`만. 다형성 대상 존재·접근 권한은 BE 검증. `imageUrl`은 서버가 `images`를 읽어 채운 접근 URL입니다.
- 상대가 탈퇴했다면 신규 메시지 거부. **차단 기능이 없으므로 차단 상태 검증은 작성하지 않습니다.** 메시지 개별 삭제·수정 기능 없음(Q423의 "수정·삭제"는 채팅에는 해당 없음).
- DB `notifications`에 채팅 메시지 알림을 저장하지 않으며 FCM 데이터 메시지만 발송합니다(R9 C). 발송 실패 시 재시도 없음.
- **오류(STOMP `/user/queue/errors`, 봉투는 HTTP와 동일):** `CHAT_INVALID_PAYLOAD`(400), `CHAT_ROOM_NOT_PARTICIPANT`(403), `IMAGE_NOT_OWNED`(403), `IMAGE_NOT_FOUND`(404), `CHAT_COUNTERPART_DELETED`(409), `CHAT_SHARED_POST_NOT_FOUND`(404). 오류 본문에 원래 `clientMessageId`를 `data`에 실어 어떤 전송이 실패했는지 알립니다 **[초안]**: `"data": { "clientMessageId": "c-20261102-0002" }`.

### 14.6 PATCH /chats/rooms/{id}/read · 14.7 POST /.../leave

- `14.6` 요청 [초안] `{ "lastReadMessageSeq":4003 }` → 성공 200 `data:{lastReadMessageSeq:4003, unreadCount:0}`. 메시지 ID는 **해당 방 소속**이어야 하며(아니면 400 `CHAT_MESSAGE_NOT_IN_ROOM`, BE가 복합 FK 위반 전에 검증) 읽음 포인터를 과거로 되돌리는 요청은 무시하고 현재 값을 반환합니다 [초안].
- `14.7` 요청 [초안] `{}` → 성공 200 `data:{lastHiddenMessageSeq:4003}`. 마지막 메시지 기준으로 과거 대화를 **본인에게만** 숨기며 방/메시지는 삭제하지 않습니다. 재입장 시 기존 방을 사용하고 이후 새 메시지만 표시.

---

## 15. 신고 (REPORT)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 15.1 | POST | `/reports` | 로그인(USER·SHELTER) | 회원/게시물/댓글 신고 접수 |

> v2의 15.2 `GET /users/me/reports`(내 신고 내역)는 **삭제**했습니다(Q428 B, 범위 외·미구현). 신고는 접수만 하고 서비스 안에서 열람하는 주체가 없습니다.

### 15.1 POST /reports

**요청 [초안]**
```json
{
  "targetType": "MISSING_COMMENT",
  "targetSeq": 44,
  "reasonDetail": "개인정보가 노출된 댓글입니다."
}
```
**성공 201 `data` [초안]** `{ "reportSeq":601, "status":"PENDING" }`.

- **[확정 정책]** 대상: `USER/MISSING/FOUND/SHELTER_ANIMAL/MISSING_COMMENT/FOUND_COMMENT/SHELTER_ANIMAL_COMMENT`의 **7종**. `USER`는 일반회원만 신고 가능하며 **SHELTER 계정/보호소 자체 신고 불가**. `ADOPTION` 신청서와 채팅 메시지도 직접 신고 대상에서 제외.
- 신고 사유는 **비공백 자유 텍스트 `reasonDetail` 필수**, 별도 `reasonCode` 없음.
- 같은 `(reporterUserSeq,targetType,targetSeq)`는 **전역 UNIQUE**로 단 한 번만 접수 가능; 현재 `PENDING`만 사용. 관리자 심사/처리/제재 API는 **구현 제외**이고 ADMIN 자체가 범위 외이므로 **접수된 신고를 열람하는 주체가 서비스 안에 없습니다**(DB 보관만). 사용자 자동 차단/차단 해제도 없음.
- **오류 [초안]** 409 `REPORT_ALREADY_EXISTS`; 400 `REPORT_TARGET_NOT_ALLOWED`, `REPORT_REASON_REQUIRED`; 404 `REPORT_TARGET_NOT_FOUND`.

---

## 16. 북마크 (BOOKMARK)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 16.1 | GET | `/users/me/bookmarks` | USER | 내가 저장한 게시물 목록 |
| 16.2 | POST | `/bookmarks` | USER | 게시물 북마크 등록 |
| 16.3 | DELETE | `/bookmarks/{targetType}/{targetSeq}` | USER | 게시물 북마크 해제 |

### 16.1 GET /users/me/bookmarks

**요청 [초안]** `?targetType=MISSING&page=0&size=20&sort=regDttm,desc` (`targetType` 생략 시 세 종류 통합). 필터 `targetType`. 정렬 화이트리스트 `regDttm`.

**목록 `data.content[]` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101, "regDttm":"...", "postSummary":{...} }`.

- **[확정 정책]** 원본 게시물 자체가 삭제되거나 작성자가 탈퇴하면 **목록에서 비노출**. 신고가 CLOSED거나 보호동물이 ADOPTED 상태여도 북마크는 유지됩니다.

### 16.2 POST /bookmarks · 16.3 DELETE /bookmarks

**요청 [초안]** `{ "targetType":"MISSING", "targetSeq":101 }`.

- `16.2` **성공 201 `data` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101 }`.
- `16.3` **성공 200 `data:null`**. 경로는 `DELETE /bookmarks/{targetType}/{targetSeq}`로 확정합니다(본문 없는 DELETE). 없는 북마크 해제는 404 `USER_BOOKMARK_NOT_FOUND`. 북마크는 `user` 도메인에 속합니다(R12).
- **[확정 정책]** 대상은 `MISSING/FOUND/SHELTER_ANIMAL` 3종. 회원+대상 UNIQUE, 해제는 **물리 DELETE**. 동일 북마크 추가 시 409 `USER_BOOKMARK_ALREADY_EXISTS`(Q415 A, 멱등 200 아님). 앱은 상세의 `isBookmarked`로 상태를 알고 있으므로 409를 "이미 저장됨"으로 처리합니다.

---

## 17. 알림·FCM (NOTIFICATION) — 서비스 `notification`, 접두어 `/api/notification/v1`

> **[확정 정책] 서비스·저장소:** notification 서비스(8082)가 `core` 스키마의 `notifications`·`device_tokens`를 **쓰고**, `users`는 읽기 전용 JOIN(Q420 D). 저장형 알림 생성 요청(`NotificationRequested`)은 **core-api만** 보내고 notification이 저장 후 FCM을 발송합니다. chat(`ChatMessageSent`)과 simulation(`SimulationPushRequested`)은 저장 없이 FCM 발송만 요청합니다. 단 AI 매칭 중복 방지(17.8)는 `missing_reports` 행 잠금과 한 트랜잭션이어야 하므로 **core-api가 `notifications` 행을 직접 INSERT**하고 notification은 FCM 발송만 맡습니다(이 한 건만 쓰기 소유권 예외).

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 17.1 | GET | `/notifications` | 로그인 | 내 저장 알림 목록 |
| 17.2 | PATCH | `/notifications/{notificationSeq}/read` | 수신자 | 개별 읽음 처리 |
| 17.3 | PATCH | `/notifications/read-all` | 로그인 | 전체 읽음 처리 [초안] |
| 17.4 | DELETE | `/notifications/{notificationSeq}` | 수신자 | 개별 알림 소프트 삭제 |
| 17.5 | DELETE | `/notifications` | 로그인 | 전체 알림 소프트 삭제 |
| 17.6 | POST | `/notifications/device-tokens` | 로그인 | FCM 토큰 등록/활성화 |
| 17.7 | DELETE | `/notifications/device-tokens/current` | 로그인 | 현재 활성 FCM 토큰 비활성화 [초안] |

### 17.1 GET /notifications

**요청 [초안]** `?isRead=false&notificationType=AI_MATCH&page=0&size=20&sort=regDttm,desc`. 필터 `isRead`, `notificationType`. 정렬 화이트리스트 `regDttm`.

**목록 200 `data.content[]` [초안]**
```json
{
  "notificationSeq": 805,
  "notificationType": "AI_MATCH",
  "message": "등록하신 실종 동물과 유사한 동물이 발견되었습니다.",
  "actor": null,
  "targetType": "FOUND",
  "targetSeq": 210,
  "missingReportSeq": 101,
  "isRead": false,
  "readDttm": null,
  "regDttm": "2026-10-29T17:50:00+09:00"
}
```

- **[확정 정책]** 5개 유형 `COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM`; `REPORT`·`SIMULATION` 없음. 모의 입양 알림은 이 목록에 **섞이지 않고** 18.22~18.23의 별도 API로 조회합니다(M13·M16). `targetType/targetSeq` 둘 다 NULL인 운영 알림 허용. 연결 대상이 있다면 서버에서 쌍과 접근 권한 검사.
- `AI_MATCH`에서 `targetType`은 **`FOUND` 또는 `SHELTER_ANIMAL`**, `targetSeq`는 매칭 상대 PK. `missingReportSeq`는 원본 실종 신고. **`matchedTargetType/matchedTargetSeq` 중복 필드 없음**.
- `ADOPTION` 클릭 대상 타입 `ADOPTION`은 입양 신청 `applicationSeq`를 가리킵니다. `SYSTEM`은 운영 공지용으로 **유형만 예약**하며 이번 범위에서 발송 주체·API가 없습니다(Q426). `SHELTER`는 담당 발견 신고 배정 알림이며 보호소가 받는 모든 알림의 범용 타입이 아닙니다(입양 제출은 `ADOPTION`).

### 17.2~17.5 알림 읽음·삭제

- `17.2` 성공 200 `data:{notificationSeq,isRead:true,readDttm:"..."}`; 타인 알림이거나 삭제된 알림이면 404 `NOTIFICATION_NOT_FOUND`(Q434).
- `17.3` 성공 200 `data:{updatedCount:5}` [초안]. 읽은 시간은 서버가 설정.
- `17.4` 및 `17.5` 성공 200 `data:null` [초안]. **[확정 정책]** `is_deleted=true`로 소프트 삭제(개별/전체), 별도 숨김 기능 없음. 17.4는 타인·삭제된 알림이면 404 `NOTIFICATION_NOT_FOUND`.
- 삭제된 AI_MATCH 알림도 **과거 알림 존재 확인에 포함**되어 같은 쌍의 알림을 재생성하지 않습니다.

### 17.6 POST /notifications/device-tokens · 17.7 DELETE /.../current

**등록 요청 [초안]** `{ "fcmToken":"fcm-device-token" }`.

**등록 성공 200 `data` [초안]** `{ "active":true }`.

- **[확정 정책]** 계정당 활성 FCM 토큰 최대 1개, 동일 토큰을 여러 계정에서 동시에 활성화 금지. 등록은 **upsert**이며 같은 토큰을 다시 보내도 200(Q415의 유일한 멱등 예외). 다른 토큰이면 기존 토큰을 비활성화하고 새 토큰을 활성화합니다. 다른 계정에서 활성인 토큰이면 그 계정의 토큰을 비활성화하고 요청 계정으로 옮깁니다(기기 양도). 모든 충돌을 서버가 이렇게 해소하므로 **409를 돌려주는 경우가 없고, 종전 `NOTIFICATION_DEVICE_TOKEN_CONFLICT`는 삭제**합니다(Q439). 탈퇴 시 계정의 활성 토큰 전부 비활성화.
- `17.7` **요청 [초안]** `DELETE /notifications/device-tokens/current` 본문 `{ "fcmToken":"fcm-device-token" }`. 성공 `data:null`; DB에는 활성 해제 시각 `mod_dttm` 기록. **역할 분담(Q419 A):** 로그아웃(2.4)에 `fcmToken`을 실으면 core-api가 같은 비활성화를 이벤트로 요청하므로 앱은 17.7을 따로 부르지 않아도 됩니다. 17.7은 로그아웃 없이 푸시만 끄는 설정 화면용입니다.
- 모의 입양 푸시(18장)도 **같은 활성 FCM 토큰**으로 발송합니다. 별도 토큰 등록은 없습니다(M16). simulation은 `device_tokens`를 읽지 않고 `SimulationPushRequested` 이벤트만 보내며, 토큰 조회와 FCM 발송은 notification이 합니다.

### 17.8 알림 생성·AI 매칭 발송 조건 (내부 비공개 동작)

**[확정 정책] 생성 이벤트 최소 집합(Q426 A).** 아래 외의 상태 전이는 알림을 만들지 않습니다.

| 유형 | 이벤트 | 수신자 | 발행 서비스 |
|---|---|---|---|
| `COMMENT` | 내 게시물(실종·발견·보호동물)에 루트 댓글 | 게시물 작성자(보호동물은 담당 보호소 SHELTER 계정). 본인 댓글 제외 | core-api |
| `COMMENT` | 내 루트 댓글에 답글 | 루트 댓글 작성자. 본인 답글 제외. 게시물 작성자에게는 중복 발송 안 함 | core-api |
| `AI_MATCH` | 실종×발견 / 실종×보호동물 매칭 | 실종 작성자 **한 명**, 쌍당 생애 1회 | core-api(직접 INSERT) |
| `ADOPTION` | 12.14 표의 전이 | 표 참조 | core-api |
| `SHELTER` | 발견 신고 자동 배정(8.4) | 담당 보호소 SHELTER 계정 | core-api |
| `SYSTEM` | 없음(유형만 예약) | — | — |
| 채팅 수신 | **FCM 데이터 메시지만**, `notifications` DB 행 저장하지 않음. 표시 여부는 앱이 현재 방 기준으로 판단(R9 C) | 보낸 사람 외 참여자 | chat → `ChatMessageSent` → notification |
| 모의 입양 | `notifications`에 저장하지 않음. `simulation_notifications` 별도 저장 + FCM(18.22) | 세션 소유 USER | simulation |

AI 매칭 규칙: 실종×발견은 ACTIVE·비삭제·비탈퇴 양쪽 검증, 실종×보호동물은 실종 ACTIVE + 보호동물 PROTECTING·비삭제(입양 상태 무관). 발생 계기는 실종/발견/보호동물 신규 등록, CLOSED→ACTIVE 재개, 사진 배열 diff. 같은 쌍은 `notification_type='AI_MATCH'` + `(missing_report_seq,target_type,target_seq)` 생애 1회.

**AI 중복 알림 필수 처리 순서 [확정 정책]**: ① 유효 동물/신고 상태 검사 → ② 해당 `missing_reports` 행에 `SELECT ... FOR UPDATE` 획득 → ③ `is_deleted=true` 알림까지 포함해 동일 쌍 `AI_MATCH` 중복 조회 → ④ 없으면 `message TEXT NOT NULL` 및 `targetType/targetSeq`로 한 행 INSERT(core-api 트랜잭션, 같은 `core` 스키마) → ⑤ 트랜잭션 커밋 → ⑥ `NotificationCreated` 이벤트(이미 저장됨, 발송만 요청)를 RabbitMQ로 발행하면 notification이 DB에 저장된 **똑같은 message 문자열**을 FCM body로 발송. 별도 전용 AI UNIQUE 인덱스는 **없습니다**. FCM 실패 시 DB 알림은 보존하고 재시도하지 않습니다. 그 외 유형은 발행 서비스가 `NotificationRequested` 이벤트(저장 + 발송 요청)에 수신자·유형·message·target을 실어 보내고 notification이 INSERT + FCM을 수행합니다. 두 이벤트는 이름으로 구분합니다(21.6).

**AI 워커 연동 [확정 정책, Q432]:** 매칭 요청과 결과 모두 RabbitMQ입니다. core-api는 실종·발견·보호동물의 등록·CLOSED→ACTIVE 재개·사진 배열 diff 때 `{ "sourceType": "MISSING|FOUND|SHELTER_ANIMAL", "sourceSeq": 101, "imageKeys": ["images/2026/11/a.jpg"], "speciesCode": "DOG", "occurredAt": "2026-11-02T17:50:00+09:00" }`를 발행합니다. 워커는 DB를 읽지 않고 페이로드와 S3 키만으로 동작하며, 유사하다고 판단한 쌍마다 `AiMatchFound { "missingReportSeq": 101, "targetType": "FOUND|SHELTER_ANIMAL", "targetSeq": 210, "score": 0.91, "modelVersion": "v1" }`를 발행합니다. `score`·`modelVersion`은 저장하지 않는 로그용 선택 필드이고 임계값 판단은 워커 몫입니다. core-api는 수신 시점에 위 ①(ACTIVE·PROTECTING·비삭제·비탈퇴) 재검사부터 ⑥까지 수행합니다. 워커의 임베딩 저장 위치는 POC 범위로 남깁니다.

---

## 18. 모의 입양 (SIMULATION) — 서비스 `simulation`, 접두어 `/api/simulation/v1`

> 이 장은 DB v10의 모의 입양 11개 테이블(`simulation_*`)의 API입니다. 결정 번호 M1~M17은 DB v10 추적표와 같습니다. 18.21(=12.15)만 core-api가 제공합니다.
>
> **[확정 정책] 서비스·저장소(Q420 D):** simulation 서비스(8083)는 **`simulation` 스키마**만 쓰고, `core` 스키마의 `users`(18.20 `ActorSummary`)·`shelter_animals`·`shelter_animal_images`·`images`·`animal_breeds`·`animal_species`는 **읽기 전용 JOIN**으로만 참조합니다(교차 FK 없음). `device_tokens`는 읽지 않고 푸시는 이벤트로 notification에 위임합니다. 반대로 core-api는 `simulation.simulation_reports`·`simulation_sessions`를 읽기 전용으로 JOIN해 12.15를 제공합니다(서비스별 DB 계정 권한을 줄 때 이 목록이 기준). Flyway 마이그레이션은 simulation이 자기 스키마 것을 따로 가집니다.
>
> **[확정 정책] 스냅샷:** 세션 시작 시 `species_type`·`personality`에 더해 동물의 **`shelter_seq`와 표시 정보(대표 사진 `image_key`, 품종명·색상명)**를 세션에 복사합니다(R2). 키는 저장만 하고 응답의 `representativeImageUrl`은 그때그때 URL로 변환합니다. 보호소 열람 권한(18.18·18.20, **종료 리포트만**, Q435)은 세션의 `shelter_seq`와 토큰 `shelterSeq` 비교로 simulation 혼자 판단하고, 동물 상태가 바뀌어도 진행 중 세션은 영향을 받지 않습니다. 상점 물품 사진은 `images` FK가 아니라 시드의 키 문자열로 저장하고 `imageUrl`로 노출합니다.
>
> **[확정 정책] 스케줄러(S8 A):** `@Scheduled` 10분 주기 작업 **하나**가 진행 중 세션을 순회합니다(21.5의 7). simulation 인스턴스는 1개 전제이며 2개 이상이면 ShedLock을 붙입니다. core·notification에는 스케줄 작업이 없습니다.

### 18.0 공통 규칙

**[확정 정책]**
- **대상·시작(M1·M7·M8):** 보호동물 상세(11.2)의 "모의 입양" 버튼으로 **실제 보호동물**에서 시작합니다. 종(`DOG/CAT`)과 성격(`ACTIVE/TIMID/AGGRESSIVE/FRIENDLY`)은 시작 시 동물→조합→품종에서 읽어 세션에 **스냅샷**하고, 이후 동물·품종이 바뀌어도 세션에는 영향이 없습니다. 종이 `ETC`이면 시작 불가. 품종의 성격이 NULL이면 랜덤 배정 **[가안]**.
- **시간(M2):** 실시간. 1게임일 = 1실제일, 총 **7일**. 스탯은 **서버가 조회·행동·스케줄러 시점에 마지막 계산 시각부터 경과 tick만큼 지연 계산**합니다. 클라이언트는 스탯을 직접 올리거나 시간을 보내지 않습니다. 모든 응답의 스탯은 계산 후 값입니다.
- **스탯(M3):** 7종 `hunger/hydration/cleanliness/friendship/stress/health/activity`, 각 0~100 정수. `stress`만 낮을수록 좋습니다. 초기값·tick당 변화·성격/종 배율은 **코드 설정값 [가안]**이며 DB에 두지 않습니다(아래 표).
- **돈(M5):** 시작 잔액 = 입력한 일주일 예상 비용(원, 정수). 펫샵에서 인벤토리로 가져올 때 결제되며 **음수 잔액을 허용**합니다. 밥그릇·물그릇·화장실 같은 기구도 구매 대상입니다.
- **세션 상태(M6):** `IN_PROGRESS / COMPLETED / ABANDONED / FAILED`. USER당 진행 중 세션 **1개**. 예정 종료 시각 도달 → COMPLETED, 포기 → ABANDONED, 건강 0 → FAILED("돌봄 부족" 표현, 죽음 표현 없음). 종료 시 리포트를 1회 생성해 **영구 보존**하고 새 세션을 시작할 수 있습니다. 탈퇴 시 진행 중 세션은 ABANDONED.
- **이벤트(M11·M15):** 집 돌발 이벤트, 산책 중 동물 행동, 아픔(병)을 **시나리오→선택지 2개→효과 JSON** 엔진 하나로 처리합니다. 시나리오는 `HOME/WALK` 장소와 `RANDOM/CONDITION`(스탯 조건) 발생 유형을 가집니다. "아픔"은 건강 등 스탯 조건으로 발생하는 HOME·CONDITION 이벤트이고, 동물병원은 그 이벤트의 선택지(진료비·진단 문구 `resultText`)입니다. **별도 병원·진단 API는 없습니다.** 미응답(PENDING) 이벤트는 세션당 동시에 1개.
- **산책(M12):** 개·고양이 모두 가능. 고양이는 종 배율로 스트레스 증가·활동 회복 감소 **[가안]**. 산책 중 동물 행동은 WALK 시나리오로 하트비트 응답에 실려 오고, 사용자가 먼저 하는 행동은 물 주기·간식 주기 두 가지입니다.
- **알림(M13·M16):** 일반 `notifications`와 **완전 분리**된 `simulation_notifications`에 simulation이 저장·커밋한 뒤, RabbitMQ 이벤트 `SimulationPushRequested`로 notification 서비스에 넘기고 notification이 활성 FCM 토큰으로 푸시합니다(`device_tokens`는 notification 소유라 simulation이 직접 읽지 않음). 유형은 `NEGLECT/EVENT/SESSION_END`와 스탯별 경고 7종 `STAT_HUNGER_WARNING/STAT_HYDRATION_WARNING/STAT_CLEANLINESS_WARNING/STAT_FRIENDSHIP_WARNING/STAT_STRESS_WARNING/STAT_HEALTH_WARNING/STAT_ACTIVITY_WARNING`(총 10종, Q438). 스탯 경고를 유형으로 나눈 이유는 "스탯당 하루 1회" 억제를 같은 세션·같은 유형의 최근 24시간 행 존재 여부로 판정하기 위해서입니다(별도 컬럼 없음). 산책 중 WALK 이벤트는 푸시 없이 하트비트 응답으로 전달합니다.
- **리포트(M14·M17·Q435):** 유저에게 쌓이고 보호소는 **종료 리포트만** 열람합니다. "보호소"는 **세션 동물의 `shelter_seq` 소속 SHELTER 회원**을 뜻하며, 자기 보호동물로 진행된 세션의 리포트(18.18·18.20)와 입양 신청 상세 경로(12.15)로 열람합니다. 세션 상세·행동 이력·지출 내역(18.4·18.7·18.11)은 **본인 전용**이며, 그 정보는 종료 시 리포트 생성 과정에서 집약됩니다. **리포트 `summary`는 비동기로 생성합니다(Q444):** 종료 트랜잭션은 집계 컬럼만 채우고 `summary=NULL`로 리포트를 저장·커밋 → 커밋 후 리스너(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, simulation 내부)가 세션 상세·행동 이력·지출 내역을 LLM 어댑터(`port/out`)에 보내 문구를 받아 `summary`만 UPDATE. 실패하면 1회 재시도 후 집계값 기반 규칙 문구를 대신 저장합니다 **[가안: 타임아웃 20초, 재시도 1회]**. DB 트랜잭션 안에서 LLM을 호출하지 않으며, 앱은 `summary`가 `null`이면 "요약 준비 중"으로 표시합니다. 수정·삭제 API는 없습니다.
- **마스터 데이터(Q412):** 펫샵 물품·이벤트 시나리오·선택지는 Flyway 시드로 적재하며 관리 API가 없습니다.

**권한 표기:** "본인" = `simulation_sessions.user_seq` 가 요청자. "보호소" = 세션 스냅샷 `shelter_seq` = 토큰 `shelterSeq`이며 **18.18·18.20·18.21에만** 등장합니다(Q435). 그 외 접근은 404 `SIMULATION_NOT_FOUND`로 존재를 감춥니다(1.3 공통 필터). 변경 계열(행동·구매·이벤트 응답·산책·포기)은 세션이 `IN_PROGRESS`일 때만 허용하고 아니면 409 `SIMULATION_NOT_IN_PROGRESS`.

**코드 설정값 [가안, Q429]** (DB 저장 없음, `application.yml`에 두고 플레이 테스트 후 조정)

| 항목 | 가안 |
|---|---|
| tick / 기간 | 1시간 / 7일 |
| 스탯 초기값 → tick당 변화 | hunger 70 → -4 · hydration 70 → -5 · cleanliness 80 → -2 · friendship 30 → -1 · activity 60 → -3 · stress 20 → +2 · health 90 → 0, 단 hunger·hydration·cleanliness 중 하나라도 20 미만이면 -3, stress 80 이상이면 추가 -2 |
| 성격 배율 | ACTIVE 활동·포만 감소 1.3, 산책 회복 1.3 · TIMID 스트레스 증가 1.5, 친밀도 회복 0.7 · AGGRESSIVE 산책 중 짖기·싸우기 가중치 2, 친밀도 감소 1.3 · FRIENDLY 친밀도 회복 1.5, 방치 시 스트레스 1.3 |
| 종 배율 | CAT 산책: 스트레스 증가 2.0, 활동 회복 0.5 |
| 이벤트 | HOME RANDOM 하루 1~2회, 응답 시한 6시간(만료 시 EXPIRED, 효과 없음), CONDITION 쿨다운 24시간, WALK 산책당 0~3회 |
| 스케줄러 | 10분 주기: 진행 중 세션 스탯 계산, CONDITION 판정, 알림, 만료·종료 처리, 산책 자동 종료 |
| 방치·위험 | 방치 8시간 무행동(이후 8시간마다), 스탯 위험 임계 20(스탯당 하루 1회) |
| 산책 | 하트비트 60초, 10분 무응답 시 자동 종료 |

**공통 DTO [초안]**
- `SimulationStats`: `{ "hunger":64, "hydration":58, "cleanliness":77, "friendship":31, "stress":24, "health":90, "activity":55, "calculatedDttm":"2026-11-01T14:00:00+09:00" }`.
- `SimulationEquipment`: `{ "foodBowl":true, "waterBowl":true, "litterBox":false, "toy":false }` — 보유 기구(메인 화면 오브젝트 활성 여부).
- `SimulationEvent`: 18.12의 발생 이벤트 DTO.
- `SimulationSession`(상세): 18.1 응답 참조.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 18.1 | POST | `/simulations` | USER | 세션 시작 `{animalSeq, expectedCost}` |
| 18.2 | GET | `/simulations/me/active` | USER | 진행 중 세션(스탯 계산 반영, PENDING 이벤트·기구·잔액 포함) |
| 18.3 | GET | `/simulations/me` | USER | 내 세션 목록(종료 포함) |
| 18.4 | GET | `/simulations/{sessionSeq}` | 본인 | 세션 상세 |
| 18.5 | POST | `/simulations/{sessionSeq}/abandon` | 본인 | 중도 포기 → ABANDONED + 리포트 |
| 18.6 | POST | `/simulations/{sessionSeq}/actions` | 본인 | 돌봄 행동 `{actionType, itemSeq?}` |
| 18.7 | GET | `/simulations/{sessionSeq}/action-logs` | 본인 | 행동 이력 |
| 18.8 | GET | `/simulations/shop/items` | 로그인 | 펫샵 물품 목록 `?speciesType=` |
| 18.9 | POST | `/simulations/{sessionSeq}/purchases` | 본인 | 구매 `{itemSeq, quantity}` |
| 18.10 | GET | `/simulations/{sessionSeq}/inventory` | 본인 | 보유 물품 + 잔액 |
| 18.11 | GET | `/simulations/{sessionSeq}/transactions` | 본인 | 지출 내역 |
| 18.12 | GET | `/simulations/{sessionSeq}/events` | 본인 | 발생 이벤트 `?status=` |
| 18.13 | POST | `/simulations/{sessionSeq}/events/{occurrenceSeq}/answer` | 본인 | 선택지 응답 `{choiceNo}` |
| 18.14 | POST | `/simulations/{sessionSeq}/walks` | 본인 | 산책 시작 |
| 18.15 | POST | `/simulations/{sessionSeq}/walks/{walkSeq}/heartbeat` | 본인 | 경과 반영·새 WALK 이벤트 수신 |
| 18.16 | POST | `/simulations/{sessionSeq}/walks/{walkSeq}/actions` | 본인 | 산책 중 행동 `{actionCode, itemSeq?}` |
| 18.17 | POST | `/simulations/{sessionSeq}/walks/{walkSeq}/end` | 본인 | 산책 종료 + 요약 |
| 18.18 | GET | `/simulations/{sessionSeq}/report` | 본인/보호소 | 종료 리포트(진행 중이면 중간 리포트) |
| 18.19 | GET | `/users/me/simulation-reports` | USER | 내 리포트 목록 |
| 18.20 | GET | `/shelters/me/simulation-reports` | SHELTER | 자기 보호동물 세션 리포트 `?animalSeq=` |
| 18.21 | GET | `/adoption-applications/{applicationSeq}/simulation-reports` | 담당 SHELTER | 신청자의 해당 동물 리포트(=12.15) |
| 18.22 | GET | `/simulations/notifications` | USER | 모의 입양 알림 목록 `?unreadOnly=` |
| 18.23 | PATCH | `/simulations/notifications/{notificationSeq}/read` | 수신자 | 읽음 |

> v1 이후 초안에 있던 `POST /simulations/{sessionSeq}/vet-visits`와 `/admin/simulations/*`(상점·시나리오·진단 관리)는 **삭제**했습니다(M15, ADMIN 범위 외).

### 18.1 POST /simulations

**요청 [초안]** `{ "animalSeq": 700, "expectedCost": 150000 }`.

**성공 201 `data` [초안]**
```json
{
  "sessionSeq": 3001,
  "userSeq": 17,
  "animal": { "animalSeq": 700, "shelterSeq": 7, "representativeImageUrl": "https://...", "speciesName": "개", "breedName": "말티즈" },
  "speciesType": "DOG",
  "personality": "FRIENDLY",
  "status": "IN_PROGRESS",
  "expectedCost": 150000,
  "balance": 150000,
  "startedDttm": "2026-11-01T14:00:00+09:00",
  "endsDttm": "2026-11-08T14:00:00+09:00",
  "endedDttm": null,
  "stats": { "hunger":70, "hydration":70, "cleanliness":80, "friendship":30, "stress":20, "health":90, "activity":60, "calculatedDttm":"2026-11-01T14:00:00+09:00" },
  "lastActionDttm": null,
  "equipment": { "foodBowl":false, "waterBowl":false, "litterBox":false, "toy":false },
  "pendingEvent": null,
  "activeWalkSeq": null
}
```

- **[확정 정책]** USER만. 선행 조건: 요청자에게 진행 중 세션 없음(부분 UNIQUE), 동물 비삭제, 종 `DOG/CAT`, **[가안]** 동물이 보호 중(`protectionStatus=PROTECTING`; 입양 완료·반환·종료 동물 불가). `expectedCost`는 0 이상 정수(원). 시작 트랜잭션: 활성 세션 검사(`FOR UPDATE`) → `core.shelter_animals`·`animal_breeds`·`animal_species`·`shelter_animal_images` 읽기 전용 JOIN으로 상태 검증 + 종·성격·`shelter_seq`·표시 정보 스냅샷 → 세션 INSERT(초기 스탯, `ends_dttm = started + 7일`, `balance = expectedCost`) → `action_logs(SYSTEM, SESSION_START)`. 교차 스키마라 동물 행은 잠그지 않으며, 읽기와 INSERT 사이에 동물 상태가 바뀌는 경쟁은 허용합니다(스냅샷 정책상 세션에 영향 없음).
- 인벤토리는 **비어 있는 상태**로 시작합니다. 밥·물·화장실을 쓰려면 18.9로 기구를 먼저 사야 합니다(M5·M9).
- **오류 [초안]** 409 `SIMULATION_ACTIVE_ALREADY_EXISTS`, `SIMULATION_ANIMAL_NOT_AVAILABLE`, `SIMULATION_SPECIES_NOT_SUPPORTED`(동물의 종이 ETC: 자원 상태 충돌이라 409, Q442); 400 `COMMON_INVALID_INPUT`(`expectedCost` 음수 등); 404 `SIMULATION_ANIMAL_NOT_FOUND`(simulation 모듈 자체 코드, core-api의 `ANIMAL_NOT_FOUND`를 빌리지 않음, Q442).

### 18.2 GET /simulations/me/active · 18.3 GET /simulations/me · 18.4 GET /simulations/{sessionSeq}

- `18.2` 성공 200 `data`: 18.1과 같은 세션 상세. **진행 중 세션이 없으면 `data:null`**(404 아님, [초안]). 조회 시점에 스탯을 지연 계산해 저장·반환합니다. 계산 결과 `health=0`이면 FAILED, `endsDttm` 경과면 COMPLETED로 그 자리에서 종료 처리하며, 이 호출에 한해 **방금 종료된 세션 상세를 `status` 변경 상태로 한 번 반환**합니다(앱이 리포트 화면으로 이동할 수 있도록, [초안]). 다음 호출부터는 `data:null`.
- `18.3` 요청 `?status=COMPLETED&page=0&size=20&sort=startedDttm,desc` → `data.content[]` `{ sessionSeq, animal:{animalSeq, representativeImageUrl, speciesName, breedName}, speciesType, personality, status, expectedCost, balance, startedDttm, endsDttm, endedDttm, hasReport }`. 필터 `status`. 정렬 화이트리스트 `startedDttm`(기본), `endedDttm`.
- `18.4` 본인만(Q435). 응답은 18.1 상세. 보호소는 세션 상세를 보지 않고 18.18·18.20의 종료 리포트만 봅니다.
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`(타인 세션 포함).

### 18.5 POST /simulations/{sessionSeq}/abandon

**요청 [초안]** `{}` → **성공 200 `data`** `{ "sessionSeq":3001, "status":"ABANDONED", "endedDttm":"...", "report": { ...18.18 리포트 DTO... } }`.

- **[확정 정책]** IN_PROGRESS → ABANDONED. 진행 중 산책은 ENDED, PENDING 이벤트는 EXPIRED 처리. 종료 트랜잭션(Q440): 스탯 최종 계산 → 상태·`ended_dttm` 갱신 → `simulation_reports` INSERT → `action_logs(SYSTEM, SESSION_END)` → **`simulation_notifications(SESSION_END)` INSERT까지 같은 트랜잭션** → 커밋 → `SimulationPushRequested` 발행(FCM은 notification). 알림 행 INSERT가 실패하면 종료 전체가 롤백됩니다.
- **오류 [초안]** 409 `SIMULATION_NOT_IN_PROGRESS`.

### 18.6 POST /simulations/{sessionSeq}/actions

**요청 [초안]** `{ "actionCode": "FEED", "itemSeq": 11 }`. 요청 필드는 DB `action_code` 값(`FEED` 등)을 담는 `actionCode`이며, `actionType`은 응답·18.7 필터에서 분류(`CARE/WALK/EVENT/SYSTEM`)를 뜻하는 다른 필드입니다(문구 통일).

**성공 200 `data` [초안]**
```json
{
  "actionLogSeq": 50001,
  "actionType": "CARE",
  "actionCode": "FEED",
  "consumedItem": { "itemSeq": 11, "itemName": "건식 사료 1kg", "remainingQuantity": 4 },
  "statDelta": { "hunger": 30, "friendship": 1 },
  "stats": { "hunger":94, "hydration":58, "cleanliness":77, "friendship":32, "stress":24, "health":90, "activity":55, "calculatedDttm":"2026-11-01T15:10:00+09:00" },
  "balance": 118000
}
```

- **[확정 정책] `actionCode`와 필요 기구·소모품(M9):**

| actionCode | 필요 기구(영구, 보유 여부) | 소모품(수량 -1) | `itemSeq` |
|---|---|---|---|
| `FEED` | `FOOD_BOWL` | `FOOD` | 소모할 FOOD 필수(여러 종류 보유 가능) |
| `WATER` | `WATER_BOWL` | 없음(물 무료) | 생략 |
| `CLEAN_LITTER` | `LITTER_BOX` | `LITTER` | 소모할 LITTER 필수 |
| `BATHE` | 없음 | `SHAMPOO` | 필수 |
| `PLAY` | `TOY`(장난감 1개 이상) | 없음 | 사용할 TOY 선택(생략 시 보유 장난감 중 첫 번째) |
| `SNACK` | 없음 | `SNACK` | 필수 |
| `MEDICINE` | 없음 | `MEDICINE` | 필수 |

- 서버 처리: 세션 잠금(`FOR UPDATE`) → 스탯 지연 계산 → 기구·소모품 검사 → 수량 차감 → 물품 `effects_jsonb`에 성격 배율을 적용해 스탯 반영(0~100 클램프) → `action_logs(CARE)` INSERT(`stat_delta_jsonb`, `stats_after_jsonb`) → `last_action_dttm` 갱신. 행동은 돈을 쓰지 않습니다(돈은 18.9에서만).
- 효과값(`effects_jsonb`)은 물품 마스터에 있으며 예: FOOD `{"hunger":30,"friendship":1}`, SNACK `{"hunger":10,"friendship":3,"stress":-5}`, SHAMPOO `{"cleanliness":40,"stress":5}`, MEDICINE `{"health":20}` **[가안, 초기 데이터]**.
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`; 409 `SIMULATION_EQUIPMENT_MISSING`(기구 미보유), `SIMULATION_ITEM_NOT_IN_INVENTORY`(소모품 0개·미보유), `SIMULATION_NOT_IN_PROGRESS`, `SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`(물품 종 범위와 세션 종 불일치, 409로 통일 Q442); 400 `COMMON_INVALID_INPUT`(`actionCode`와 물품 분류 불일치); 404 `SIMULATION_SHOP_ITEM_NOT_FOUND`.

### 18.7 GET /simulations/{sessionSeq}/action-logs

**요청 [초안]** `?actionType=CARE&page=0&size=50&sort=regDttm,desc` → `data.content[]` `{ actionLogSeq, actionType, actionCode, item:{itemSeq,itemName}|null, walkSeq, occurrenceSeq, statDelta, statsAfter, regDttm }`. 필터 `actionType`(`CARE/WALK/EVENT/SYSTEM`). 정렬 화이트리스트 `regDttm`. 본인만(Q435). **오류 [초안]** 404 `SIMULATION_NOT_FOUND`.

### 18.8 GET /simulations/shop/items

**요청 [초안]** `?speciesType=DOG` (생략 시 전체). `species_scope`가 요청 종 또는 `ALL`이고 `is_active=true`인 물품만, `display_order` 순.

**성공 200 `data` [초안]** (비페이지 전량)
```json
[
  { "itemSeq": 1, "itemName": "스테인리스 밥그릇", "category": "FOOD_BOWL", "isConsumable": false, "price": 12000, "description": "한 번 사면 계속 쓰는 기구입니다.", "imageUrl": "https://.../simulation/items/food-bowl.png", "speciesScope": "ALL", "effects": null },
  { "itemSeq": 11, "itemName": "건식 사료 1kg", "category": "FOOD", "isConsumable": true, "price": 8000, "description": "한 끼 분량씩 소모됩니다.", "imageUrl": null, "speciesScope": "DOG", "effects": { "hunger": 30, "friendship": 1 } }
]
```
- **[확정 정책]** `category` 9종 `FOOD_BOWL, WATER_BOWL, LITTER_BOX, FOOD, SNACK, TOY, LITTER, SHAMPOO, MEDICINE`. 기구(`FOOD_BOWL/WATER_BOWL/LITTER_BOX/TOY`)는 `isConsumable=false`. 물은 무료이므로 물 자체는 물품이 아니고 물그릇만 판매합니다. 물품 사진은 시드의 **저장소 키 문자열**(`simulation_shop_items.image_key`)에서 URL을 만들어 `imageUrl`로 내보내며 공통 `images`를 참조하지 않습니다(Q420).

### 18.9 POST /simulations/{sessionSeq}/purchases · 18.10 GET /.../inventory · 18.11 GET /.../transactions

**18.9 요청 [초안]** `{ "itemSeq": 11, "quantity": 5 }`.

**18.9 성공 201 `data` [초안]**
```json
{
  "transactionSeq": 70001,
  "item": { "itemSeq": 11, "itemName": "건식 사료 1kg", "category": "FOOD" },
  "quantity": 5,
  "amount": 40000,
  "balanceAfter": 110000,
  "inventoryQuantity": 5
}
```
- **[확정 정책]** 구매 시 `amount = price × quantity`를 잔액에서 차감하며 **잔액이 음수가 되어도 허용**합니다(M5). 기구는 `quantity=1`만 허용하고 이미 보유 중이면 409 `SIMULATION_EQUIPMENT_ALREADY_OWNED`. `species_scope`가 세션 종과 맞지 않으면 409 `SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`. 트랜잭션: 세션 잠금 → `simulation_transactions(PURCHASE)` INSERT → `balance` 갱신 → `simulation_inventory_items` UPSERT(`UNIQUE(session_seq,item_seq)`).
- **18.10 성공 200 `data`** `{ "balance":110000, "expectedCost":150000, "equipment":{SimulationEquipment}, "items":[ { "itemSeq":11, "itemName":"건식 사료 1kg", "category":"FOOD", "isConsumable":true, "quantity":5, "imageUrl":"https://..." } ] }`. 수량 0인 소모품 행도 포함(앱이 "다 썼음" 표시 가능, [초안]).
- **18.11 요청** `?transactionType=PURCHASE&page=0&size=50&sort=regDttm,desc` → `data.content[]` `{ transactionSeq, transactionType:"PURCHASE"|"EVENT", item:{itemSeq,itemName,category}|null, occurrenceSeq|null, quantity, amount, balanceAfter, description, regDttm }`. 필터 `transactionType`. 정렬 화이트리스트 `regDttm`. 본인만(Q435).
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`, `SIMULATION_SHOP_ITEM_NOT_FOUND`(비활성 물품 포함); 400 `COMMON_INVALID_INPUT`(quantity<1); 409 `SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`, `SIMULATION_EQUIPMENT_ALREADY_OWNED`, `SIMULATION_NOT_IN_PROGRESS`.

### 18.12 GET /simulations/{sessionSeq}/events · 18.13 POST /.../events/{occurrenceSeq}/answer

**18.12 요청 [초안]** `?status=PENDING&page=0&size=20&sort=occurredDttm,desc` (생략 시 전체). 필터 `status`. 정렬 화이트리스트 `occurredDttm`.

**`SimulationEvent` DTO [초안]**
```json
{
  "occurrenceSeq": 90001,
  "scenario": { "scenarioSeq": 5, "title": "축 처져 있어요", "description": "밥을 잘 안 먹고 구석에서 움직이지 않아요.", "triggerContext": "HOME", "triggerType": "CONDITION" },
  "choices": [
    { "choiceNo": 1, "choiceText": "동물병원에 데려간다", "cost": 30000 },
    { "choiceNo": 2, "choiceText": "하루 더 지켜본다", "cost": 0 }
  ],
  "walkSeq": null,
  "status": "PENDING",
  "occurredDttm": "2026-11-02T09:00:00+09:00",
  "expiresDttm": "2026-11-02T15:00:00+09:00",
  "chosenChoiceNo": null,
  "answeredDttm": null,
  "resultText": null
}
```
- 선택지의 `effects`는 응답 전에는 노출하지 않습니다(결과는 응답 후 `statDelta`로). `cost`는 노출합니다.

**18.13 요청 [초안]** `{ "choiceNo": 1 }`.

**18.13 성공 200 `data` [초안]**
```json
{
  "occurrenceSeq": 90001,
  "status": "ANSWERED",
  "chosenChoiceNo": 1,
  "resultText": "진단서: 가벼운 장염 소견. 처방식과 휴식을 권장합니다.",
  "statDelta": { "health": 30, "stress": -10 },
  "stats": { "hunger":40, "hydration":35, "cleanliness":60, "friendship":35, "stress":30, "health":68, "activity":40, "calculatedDttm":"2026-11-02T10:12:00+09:00" },
  "cost": 30000,
  "balanceAfter": 80000
}
```
- **[확정 정책] 발생 규칙(M11·M15):** HOME·RANDOM은 하루 1~2회 가중치 추첨 **[가안]**, HOME·CONDITION은 스탯 지연 계산 시점과 스케줄러 주기에 `trigger_condition_jsonb`(예 `{"stat":"health","op":"<","value":40}`)를 판정해 발생하고 같은 시나리오는 `cooldown_hours` 동안 재발생하지 않습니다. WALK 시나리오는 18.15에서만 발생합니다. 세션당 PENDING은 1개(부분 UNIQUE)라 새 이벤트는 기존 PENDING이 응답·만료된 뒤에만 생깁니다. HOME 이벤트 발생 시 `EVENT` 알림(저장 + FCM).
- **[확정 정책] 응답 처리:** `occurrence FOR UPDATE` → PENDING·미만료 확인 → 선택지 `effects_jsonb`에 성격 배율 적용해 스탯 반영 → `cost>0`이면 `simulation_transactions(EVENT)` INSERT + `balance` 차감(음수 허용) → `action_logs(EVENT, EVENT_ANSWER)` → ANSWERED·`chosen_choice_no`·`answered_dttm` 저장 → `resultText`(선택지 `result_text`, 진단서 문구 등) 반환. 응답은 `last_action_dttm`을 갱신합니다.
- 만료 처리(Q441): `expires_dttm` 경과를 **조회(18.2·18.12·18.13)와 스케줄러 양쪽**에서 감지하면 EXPIRED + `action_logs(EVENT, EVENT_EXPIRE)`, 효과·비용 없음(스탯 지연 계산과 같은 원칙으로 GET도 이 쓰기를 수행). 산책 종료(18.17·자동 종료) 시 PENDING WALK 이벤트도 같은 방식으로 EXPIRED. 이미 EXPIRED/ANSWERED면 409.
- 메인 화면의 **동물병원 오브젝트**는 PENDING인 CONDITION 이벤트가 있을 때 활성화되어 이 선택 화면으로 이동합니다(M15).
- **오류 [초안]** 404 `SIMULATION_EVENT_NOT_FOUND`; 409 `SIMULATION_EVENT_NOT_PENDING`(ANSWERED·EXPIRED·만료 경과), `SIMULATION_NOT_IN_PROGRESS`; 400 `SIMULATION_INVALID_CHOICE`.

### 18.14 POST /simulations/{sessionSeq}/walks · 18.15 POST /.../heartbeat · 18.16 POST /.../actions · 18.17 POST /.../end

**18.14 요청 [초안]** `{}` → **성공 201 `data`** `{ "walkSeq": 8001, "status": "IN_PROGRESS", "startedDttm": "...", "heartbeatIntervalSeconds": 60, "stats": {SimulationStats} }`.

- **[확정 정책]** 세션당 진행 중 산책 1건(부분 UNIQUE). 시작 시 `action_logs(WALK, WALK_START)`. 개·고양이 모두 가능(M12).

**18.15 요청 [초안]** `{}` (60초 간격 **[가안]**) → **성공 200 `data`**
```json
{
  "walkSeq": 8001,
  "elapsedMinutes": 12,
  "stats": { "...": "..." },
  "newEvent": { "...SimulationEvent(triggerContext=WALK, walkSeq=8001)..." },
  "pendingEvent": { "...": "..." }
}
```
- `last_heartbeat_dttm` 갱신 후 경과 tick 반영. 산책 길이와 성격·종 배율에 따라 WALK 시나리오를 0~3회 **[가안]** 추첨해 발생시키며, 발생한 경우 `newEvent`에 담아 반환합니다(푸시 없음). 이미 PENDING이 있으면 새로 만들지 않고 `pendingEvent`로 재전달. 산책 중 동물 행동(짖기·싸우기, 오줌, 빨리 달리기, 냄새 맡기, 앉기, 똥)은 모두 WALK 시나리오이며 응답은 18.13으로 보냅니다.
- 하트비트가 10분 **[가안]** 끊기면 스케줄러가 산책을 자동 종료(ENDED)합니다.

**18.16 요청 [초안]** `{ "actionCode": "GIVE_SNACK", "itemSeq": 21 }` 또는 `{ "actionCode": "GIVE_WATER" }` → **성공 200 `data`** 18.6과 같은 행동 결과(`actionType:"WALK"`, `walkSeq` 포함).

- `GIVE_WATER`는 기구·소모품 불필요(물 무료), `GIVE_SNACK`은 `SNACK` 소모품 -1. 기구 보유 조건은 없습니다(산책 중).

**18.17 요청 [초안]** `{}` → **성공 200 `data`**
```json
{
  "walkSeq": 8001,
  "status": "ENDED",
  "startedDttm": "...",
  "endedDttm": "...",
  "durationMinutes": 35,
  "statDelta": { "activity": 25, "stress": -12, "friendship": 4, "hunger": -6, "hydration": -8 },
  "stats": { "...": "..." },
  "eventCount": 2
}
```
- **[확정 정책]** 산책 효과(활동·스트레스 회복, 포만·수분 감소)는 **종료 시** 길이와 성격·종 배율로 계산해 한 번에 반영하고 `action_logs(WALK, WALK_END)`에 기록합니다. 종료 시 PENDING인 WALK 이벤트는 EXPIRED 처리(효과 없음).
- **오류 [초안]** 409 `SIMULATION_WALK_ALREADY_IN_PROGRESS`(18.14), `SIMULATION_WALK_NOT_IN_PROGRESS`(18.15~18.17), `SIMULATION_ITEM_NOT_IN_INVENTORY`, `SIMULATION_NOT_IN_PROGRESS`; 404 `SIMULATION_WALK_NOT_FOUND`.

### 18.18 GET /simulations/{sessionSeq}/report · 18.19~18.21 리포트 목록

**18.18 성공 200 `data` [초안]**
```json
{
  "sessionSeq": 3001,
  "status": "COMPLETED",
  "isFinal": true,
  "animal": { "animalSeq": 700, "shelterSeq": 7, "representativeImageUrl": "https://..." },
  "speciesType": "DOG",
  "personality": "FRIENDLY",
  "startedDttm": "2026-11-01T14:00:00+09:00",
  "endedDttm": "2026-11-08T14:00:00+09:00",
  "expectedCost": 150000,
  "totalSpent": 182000,
  "difference": -32000,
  "spentByCategory": { "FOOD_BOWL": 12000, "WATER_BOWL": 9000, "FOOD": 40000, "SNACK": 15000, "LITTER_BOX": 0, "LITTER": 0, "TOY": 18000, "SHAMPOO": 8000, "MEDICINE": 0, "EVENT": 80000 },
  "dailySpent": { "1": 39000, "2": 40000, "3": 0, "4": 30000, "5": 15000, "6": 50000, "7": 8000 },
  "finalStats": { "hunger":55, "hydration":60, "cleanliness":70, "friendship":62, "stress":28, "health":84, "activity":58 },
  "actionCount": 41,
  "eventCount": 9,
  "walkCount": 6,
  "summary": "예상보다 32,000원을 더 썼어요. 돌발 이벤트 비용이 가장 컸습니다."
}
```
- **[확정 정책]** 세션 종료(COMPLETED/ABANDONED/FAILED) 트랜잭션에서 `simulation_reports`를 **1회 생성**해 영구 보존합니다(`isFinal:true`). `summary`는 커밋 후 비동기로 채워지므로 종료 직후 조회(18.5 응답 포함)에서는 `null`일 수 있고, 앱은 "요약 준비 중"으로 표시하다가 재조회합니다(Q444). 중간 리포트(`isFinal:false`)의 `summary`는 항상 `null`입니다. 세션이 진행 중이면 같은 형식의 **중간 리포트를 조회 시 계산만** 하고 저장하지 않습니다(`isFinal:false`, `endedDttm:null`). 리포트 수정·삭제 API 없음. `spentByCategory`는 PURCHASE는 물품 분류, 이벤트 비용은 `EVENT` 키로 집계. `dailySpent` 키는 세션 1~7일차.
- FAILED면 `summary`에 "돌봄 부족으로 중단" 취지를 담고 죽음 표현을 쓰지 않습니다(M6).
- `18.19` 요청 `?page=0&size=20&sort=endedDttm,desc` → `data.content[]` `{ sessionSeq, animal:{...}, status, startedDttm, endedDttm, expectedCost, totalSpent, difference }`. 정렬 화이트리스트 `endedDttm`. USER 본인 전체.
- `18.20` 요청 `?animalSeq=700&page=0&size=20&sort=endedDttm,desc` → 같은 목록 DTO + `user:{ActorSummary}`(`core.users` 읽기 전용 JOIN). **자기 보호동물**(세션 스냅샷 `shelter_seq` = 토큰 `shelterSeq`)로 진행된 **종료된** 세션만. 필터 `animalSeq`. 진행 중 세션은 보호소에 노출하지 않습니다(Q435).
- `18.18`을 보호소가 호출하면 **종료된 세션의 저장 리포트만** 반환하고, 진행 중 세션의 중간 리포트는 본인만 볼 수 있습니다(보호소에는 404 `SIMULATION_NOT_FOUND`, Q435).
- `18.21` = 12.15. **core-api가 제공**하며 담당 SHELTER가 신청서의 `userSeq`·`animalSeq`로 `simulation.simulation_reports`를 읽기 전용 JOIN해 **파생 조회**한 리포트 목록(FK 없음, M14). 신청자가 탈퇴했어도 보존된 리포트는 반환.
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`, `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 신청서 포함, Q434).

### 18.22 GET /simulations/notifications · 18.23 PATCH /.../{notificationSeq}/read

**18.22 요청 [초안]** `?unreadOnly=true&page=0&size=20&sort=regDttm,desc`. 필터 `unreadOnly`, `sessionSeq`. 정렬 화이트리스트 `regDttm`.

**목록 200 `data.content[]` [초안]**
```json
{
  "notificationSeq": 120001,
  "sessionSeq": 3001,
  "notificationType": "STAT_HYDRATION_WARNING",
  "message": "아이의 수분이 20 아래로 떨어졌어요. 물을 챙겨 주세요.",
  "occurrenceSeq": null,
  "isRead": false,
  "readDttm": null,
  "regDttm": "2026-11-02T03:10:00+09:00"
}
```
- **[확정 정책]** `simulation_notifications`만 조회하며 17장 목록에는 섞이지 않습니다. 유형은 `NEGLECT/EVENT/SESSION_END` + `STAT_{HUNGER|HYDRATION|CLEANLINESS|FRIENDSHIP|STRESS|HEALTH|ACTIVITY}_WARNING` 10종(Q438). `EVENT`는 `occurrenceSeq`로 18.13 화면에 연결. 발송 규칙은 18.0 가안 표(방치 8시간, 위험 임계 20·같은 세션·같은 `STAT_*_WARNING` 유형은 24시간 내 1회, HOME 이벤트 발생, 세션 종료). 저장 후 커밋 → `SimulationPushRequested` 이벤트 → notification이 같은 `message`를 활성 FCM 토큰으로 발송, 실패 시 재시도 없음(17장과 동일).
- `18.23` 성공 200 `data:{notificationSeq,isRead:true,readDttm}`. 전체 읽음·삭제 API는 두지 않습니다(세션 종료 후에도 이력 보존, [초안]).
- **오류 [초안]** 404 `SIMULATION_NOTIFICATION_NOT_FOUND`.

---

## 19. 도메인 에러 코드 총괄 **[확정 정책 — 명명 규칙 Q418 A]**

> 응답 `code`는 서버 enum 상수명 그대로이며 **`{도메인}_상황`** 형식입니다. 공통 코드는 `common` 모듈의 `CommonErrorCode`(1.3), 도메인 코드는 각 서비스의 도메인 패키지 `{도메인}ErrorCode` enum에 둡니다(README 규칙). 접두어는 **패키지명 대문자**이며 DB 스키마와 무관합니다(core-api 한 서비스 안에 여러 도메인). HTTP 상태와 기본 메시지는 enum 값에서 가져오고, 메시지 문구는 구현 시 정합니다. 각 절의 "오류" 항목은 이 표의 코드와 일치합니다.

**HTTP 상태 배정 원칙(Q442, 2026-11-03 재검토):** 400 = 요청 입력 자체의 문제(누락·형식·허용값 밖·화이트리스트 밖), 403 = 자기 것이 아닌 것을 자기 자원에 연결(`IMAGE_NOT_OWNED`)·STOMP 참여 권한·역할 불일치(`AUTH_FORBIDDEN`), 404 = 없음·삭제·비노출·**타인/타 보호소 자원**(존재 감춤, Q434), 409 = 자원의 현재 상태와 충돌(중복, 상태 전이 불가, 종·범위 불일치, 이미 보유). 이 원칙에 따라 `*_NOT_OWNED`·`*_ACCESS_DENIED` 계열(이미지 제외)과 발생 경로가 없는 코드는 제거했고, `SIMULATION_SPECIES_NOT_SUPPORTED`·`SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`는 400→409로, `CATALOG_DISTRICT_NOT_FOUND`(400)는 이름을 `CATALOG_INVALID_DISTRICT_CODE`로 바꿨습니다.

**도메인 패키지 목록 (R11·R12 확정)** — 접두어 = 패키지명 대문자. 기준정보와 행정구역은 `catalog`, 북마크는 `user`에 속하고, `ANIMAL_`은 보호동물이 단독으로 씁니다.

| 서비스 | 패키지 | 담당 | 접두어 |
|---|---|---|---|
| core-api | `auth` | 카카오·LOCAL 로그인, 토큰, 가입 컨텍스트 (2장) | `AUTH_` |
| core-api | `user` | 회원 프로필·탈퇴 (3장), **북마크** (16장) | `USER_` |
| core-api | `image` | 공통 이미지 업로드·연결·참조 정리 (4장) | `IMAGE_` |
| core-api | `catalog` | 종·품종·색상·조합, **`districts`** (5장) | `CATALOG_` |
| core-api | `pet` | 내 반려동물 (6장) | `PET_` |
| core-api | `missing` | 실종 신고 (7장) | `MISSING_` |
| core-api | `found` | 발견 신고·자동 배정 (8장) | `FOUND_` |
| core-api | `shelter` | 보호소 정보·배정표 (9장) | `SHELTER_` |
| core-api | `rescue` | 구조 기록 (10장) | `RESCUE_` |
| core-api | `animal` | 보호동물·반환 (11장) | `ANIMAL_` |
| core-api | `adoption` | 입양 문항·신청·심사·완료·수료증 (12장) | `ADOPTION_` |
| core-api | `comment` | 댓글·답글 (13장) | `COMMENT_` |
| core-api | `report` | 신고 접수 (15장) | `REPORT_` |
| chat | `chat` | 대화방·메시지·STOMP (14장) | `CHAT_` |
| notification | `notification` | 일반 알림·디바이스 토큰·FCM 발송 (17장) | `NOTIFICATION_` |
| simulation | `simulation` | 모의 입양 전체 (18장) | `SIMULATION_` |

| 도메인(접두어) | 에러 code | HTTP | 주된 발생 조건 |
|---|---|---|---|
| `COMMON_`/`AUTH_` (공통) | `COMMON_INVALID_INPUT`, `COMMON_INVALID_JSON`, `AUTH_UNAUTHORIZED`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`, `AUTH_FORBIDDEN`, `COMMON_NOT_FOUND`, `COMMON_METHOD_NOT_ALLOWED`, `COMMON_INTERNAL_ERROR`, `COMMON_SERVICE_UNAVAILABLE` | 400/400/401/401/401/403/404/405/500/503 | 1.3 참조 |
| `AUTH_` | `AUTH_INVALID_KAKAO_TOKEN`, `AUTH_INVALID_CREDENTIALS`, `AUTH_INVALID_REFRESH_TOKEN`, `AUTH_INVALID_REGISTRATION_CONTEXT` | 401 | 인증 실패(refresh 재사용 감지 포함) |
| `AUTH_` | `AUTH_ACCOUNT_UNAVAILABLE`, `AUTH_KAKAO_ACCOUNT_CONFLICT`, `AUTH_LOGIN_LOCKED` | 403/409/429 | 계정 상태 / 같은 카카오 계정 동시 가입 / LOCAL 로그인 연속 실패로 일시 차단(Q445) |
| `USER_` | `USER_DUPLICATE_NICKNAME`, `USER_DUPLICATE_PHONE` | 409 | 활성 USER 유니크 충돌 |
| `USER_` | `USER_REQUIRED_PROFILE_MISSING`, `USER_NOT_FOUND` | 400/404 | 최종 USER 필수 개인정보 불충족 / 탈퇴·없는 회원 |
| `USER_` (북마크, R12) | `USER_BOOKMARK_ALREADY_EXISTS`, `USER_BOOKMARK_NOT_FOUND` | 409/404 | 중복 등록 / 없는 북마크 해제 |
| `IMAGE_` | `IMAGE_NOT_FOUND`, `IMAGE_INVALID_COUNT`, `IMAGE_TOO_LARGE`, `IMAGE_UNSUPPORTED_TYPE` | 404/400/413/415 | 이미지 업로드·연결 오류 |
| `IMAGE_` | `IMAGE_NOT_OWNED` | 403 | 타인이 업로드한 `imageSeq` 연결 시도(Q407) |
| `CATALOG_` (기준정보·행정구역) | `CATALOG_COMBINATION_NOT_FOUND`, `CATALOG_INVALID_DISTRICT_CODE` | 404/400 | 등록되지 않은 품종·색상 조합 / 목록 필터에 `districts`에 없는 `districtCode`(입력 오류이므로 400, 이름도 맞춤 Q442) |
| `PET_` | `PET_NOT_FOUND` | 404 | 없음·삭제·**타인 소유**(Q434; `PET_NOT_OWNED` 삭제) |
| `MISSING_` | `MISSING_REPORT_NOT_FOUND` | 404 | 없음·삭제·비노출·**타인 신고**(Q434) |
| `MISSING_` | `MISSING_ACTIVE_ALREADY_EXISTS`, `MISSING_REPORT_CLOSED` | 409 | 회원당 ACTIVE 1건 제약 위반 / CLOSED 본문 수정(Q425) |
| `FOUND_` | `FOUND_REPORT_NOT_FOUND`, `FOUND_REPORT_CLOSED`, `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE` | 404/409/409 | 없음·삭제·**타인 신고**(8.6은 작성자·담당 보호소 외, Q434) / CLOSED 본문 수정 / 비삭제 보호소가 하나도 없음 |
| `SHELTER_` | `SHELTER_NOT_FOUND` | 404 | 보호소 없음·삭제(`SHELTER_NOT_OWNED`는 토큰 기반이라 발생 경로 없어 삭제, Q442) |
| `RESCUE_` | `RESCUE_ACTIVE_ALREADY_EXISTS` | 409 | 동일 발견 PROCESSING/DONE 1건 제약 |
| `RESCUE_` | `RESCUE_NOT_FOUND`, `RESCUE_INVALID_TRANSITION`, `RESCUE_NOT_DONE` | 404/409/409 | 없음·삭제·**다른 보호소의 구조**(Q434) / 전이 불가 / 11.4에서 DONE 아닌 구조 참조(Q442) |
| `RESCUE_` | `RESCUE_COMPLETION_FIELDS_REQUIRED`, `RESCUE_RESULT_REASON_REQUIRED` | 400 | DONE 날짜·주소 / 실패·취소 사유 누락 |
| `RESCUE_` | `RESCUE_HAS_ANIMALS` | 409 | 참조 보호동물 있는 구조 삭제 시도 |
| `ANIMAL_` (보호동물) | `ANIMAL_NOT_FOUND`, `ANIMAL_INVALID_STATUS_COMBINATION` | 404/409 | 없음·삭제·**다른 보호소 소속**(쓰기 API, Q434) / 보호·입양 상태 조합 CHECK 위반 |
| `ANIMAL_` (보호동물) | `ANIMAL_ALREADY_RETURNED`, `ANIMAL_NOT_PROTECTING`, `ANIMAL_HAS_ACTIVE_APPLICATIONS` | 409 | 중복 반환 / 보호 중 아님(11.7·11.8·12.12) / 삭제 제한 |
| `ADOPTION_` | `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`, `ADOPTION_ANIMAL_NOT_AVAILABLE` | 409 | 활성 신청 중복 / 새 신청·DRAFT 제출 불가(Q437) |
| `ADOPTION_` | `ADOPTION_APPLICATION_NOT_FOUND` | 404 | 없음·타인 신청서·타 보호소 신청서·SHELTER가 DRAFT 조회(Q434; `ADOPTION_APPLICATION_ACCESS_DENIED` 삭제) |
| `ADOPTION_` | `ADOPTION_APPLICATION_NOT_DRAFT`, `ADOPTION_APPLICATION_NOT_SUBMITTED`, `ADOPTION_APPLICATION_NOT_APPROVED` | 409 | 상태 전이 원천 부적합 |
| `ADOPTION_` | `ADOPTION_REQUIRED_ANSWER_MISSING`, `ADOPTION_INVALID_ANSWER` | 400 | 조건부 필수·보기 응답 오류 |
| `ADOPTION_` | `ADOPTION_REJECTION_REASON_REQUIRED`, `ADOPTION_ANIMAL_ALREADY_FINISHED` | 400/409 | 사유 누락 / 종료 동물 완료 경합 |
| `ADOPTION_` | `ADOPTION_CERTIFICATE_TOO_LARGE`, `ADOPTION_CERTIFICATE_UNSUPPORTED_TYPE`, `ADOPTION_CERTIFICATE_NOT_FOUND` | 413/415/404 | 수료증 업로드·연결 오류 |
| `COMMENT_` | `COMMENT_EMPTY`, `COMMENT_DEPTH_EXCEEDED`, `COMMENT_PARENT_UNAVAILABLE` | 400/400/409 | 댓글 비어 있음 / 답글 제한 / 삭제·탈퇴 루트에 답글 |
| `COMMENT_` | `COMMENT_NOT_FOUND`, `COMMENT_TARGET_NOT_FOUND` | 404 | 댓글 없음·타인 댓글 수정 / 게시물 없음 |
| `CHAT_` | `CHAT_UNSUPPORTED_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED` | 409 | 채팅 조합 / 탈퇴 상대 |
| `CHAT_` | `CHAT_INVALID_PAYLOAD`, `CHAT_MESSAGE_NOT_IN_ROOM` | 400 | 메시지 종류·필수값 / `lastReadMessageSeq`·`beforeMessageSeq`가 그 방의 메시지가 아님(신규, Q442) |
| `CHAT_` | `CHAT_ROOM_NOT_PARTICIPANT` | 403 | **STOMP SUBSCRIBE·SEND 전용** 참여 권한. REST는 404 `CHAT_ROOM_NOT_FOUND` 사용(Q434) |
| `CHAT_` | `CHAT_ROOM_NOT_FOUND`, `CHAT_TARGET_NOT_FOUND`, `CHAT_SHARED_POST_NOT_FOUND` | 404 | REST에서 방 없음·비참여자(신규, Q434) / 방 생성 대상 없음 / 공유 게시물 없음 |
| `REPORT_` | `REPORT_TARGET_NOT_ALLOWED`, `REPORT_REASON_REQUIRED`, `REPORT_ALREADY_EXISTS`, `REPORT_TARGET_NOT_FOUND` | 400/400/409/404 | 허용 범위 / 사유 누락 / 중복 / 대상 없음 |
| `NOTIFICATION_` | `NOTIFICATION_NOT_FOUND` | 404 | 없음·삭제·타인 알림(17.2·17.4). `NOTIFICATION_DEVICE_TOKEN_CONFLICT`는 17.6 양도 규칙상 발생 경로가 없어 삭제(Q439) |
| `SIMULATION_` | `SIMULATION_NOT_FOUND`, `SIMULATION_ANIMAL_NOT_FOUND` | 404 | 세션 없음·본인 아님·보호소가 진행 중 세션 조회(존재 감춤, Q435) / 18.1 대상 보호동물 없음·삭제(simulation 자체 코드, Q442) |
| `SIMULATION_` | `SIMULATION_ACTIVE_ALREADY_EXISTS`, `SIMULATION_NOT_IN_PROGRESS` | 409 | USER당 진행 중 1개 / 종료된 세션 변경 시도 |
| `SIMULATION_` | `SIMULATION_SPECIES_NOT_SUPPORTED`, `SIMULATION_ANIMAL_NOT_AVAILABLE` | 409 | 동물의 종이 ETC(자원 상태 충돌, 400→409 Q442) / 보호 중 아닌 동물 |
| `SIMULATION_` | `SIMULATION_SHOP_ITEM_NOT_FOUND`, `SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`, `SIMULATION_EQUIPMENT_ALREADY_OWNED` | 404/409/409 | 펫샵 물품 없음·비활성 / 물품 종 범위와 세션 종 불일치(400→409 Q442) / 기구 중복 구매 |
| `SIMULATION_` | `SIMULATION_EQUIPMENT_MISSING`, `SIMULATION_ITEM_NOT_IN_INVENTORY` | 409 | 기구 미보유 / 소모품 없음 |
| `SIMULATION_` | `SIMULATION_EVENT_NOT_FOUND`, `SIMULATION_EVENT_NOT_PENDING`, `SIMULATION_INVALID_CHOICE` | 404/409/400 | 이벤트 응답 오류 |
| `SIMULATION_` | `SIMULATION_WALK_NOT_FOUND`, `SIMULATION_WALK_ALREADY_IN_PROGRESS`, `SIMULATION_WALK_NOT_IN_PROGRESS` | 404/409/409 | 산책 상태 오류 |
| `SIMULATION_` | `SIMULATION_NOTIFICATION_NOT_FOUND` | 404 | 모의 입양 알림 소유권 |

**v2 → v3 재명명 대조** (앱·서버 분기 코드 치환용)

| v2 | v3 |
|---|---|
| `INVALID_PARAMETER` | `COMMON_INVALID_INPUT` |
| `UNAUTHORIZED` / `FORBIDDEN` / `NOT_FOUND` / `INTERNAL_ERROR` | `AUTH_UNAUTHORIZED`(+`AUTH_TOKEN_EXPIRED`·`AUTH_TOKEN_INVALID`) / `AUTH_FORBIDDEN` / `COMMON_NOT_FOUND` / `COMMON_INTERNAL_ERROR` |
| `ACCOUNT_UNAVAILABLE`, `CONFLICT` | `AUTH_ACCOUNT_UNAVAILABLE`, (삭제: 도메인 409 코드 사용) |
| `FILE_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `INVALID_IMAGE_COUNT` | `IMAGE_TOO_LARGE`, `IMAGE_UNSUPPORTED_TYPE`, `IMAGE_INVALID_COUNT` |
| `INVALID_KAKAO_TOKEN`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `INVALID_REGISTRATION_CONTEXT`, `KAKAO_ACCOUNT_CONFLICT` | `AUTH_` 접두어 |
| `DUPLICATE_NICKNAME`, `DUPLICATE_PHONE`, `REQUIRED_USER_PROFILE_MISSING` | `USER_DUPLICATE_NICKNAME`, `USER_DUPLICATE_PHONE`, `USER_REQUIRED_PROFILE_MISSING` |
| `ACTIVE_MISSING_REPORT_ALREADY_EXISTS` | `MISSING_ACTIVE_ALREADY_EXISTS` |
| `SHELTER_ASSIGNMENT_UNAVAILABLE` | `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE` |
| `ACTIVE_RESCUE_ALREADY_EXISTS`, `INVALID_RESCUE_TRANSITION` | `RESCUE_ACTIVE_ALREADY_EXISTS`, `RESCUE_INVALID_TRANSITION` |
| `SHELTER_ANIMAL_NOT_FOUND`, `INVALID_ANIMAL_STATUS_COMBINATION` | `ANIMAL_NOT_FOUND`, `ANIMAL_INVALID_STATUS_COMBINATION` |
| `ANIMAL_NOT_AVAILABLE`, `ANIMAL_ALREADY_FINISHED` | `ADOPTION_ANIMAL_NOT_AVAILABLE`, `ADOPTION_ANIMAL_ALREADY_FINISHED` |
| `ACTIVE_APPLICATION_ALREADY_EXISTS`, `APPLICATION_*`, `REQUIRED_ADOPTION_ANSWER_MISSING`, `INVALID_ADOPTION_ANSWER`, `REJECTION_REASON_REQUIRED` | `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`, `ADOPTION_APPLICATION_*`, `ADOPTION_REQUIRED_ANSWER_MISSING`, `ADOPTION_INVALID_ANSWER`, `ADOPTION_REJECTION_REASON_REQUIRED` |
| `EMPTY_COMMENT`, `PARENT_COMMENT_UNAVAILABLE` | `COMMENT_EMPTY`, `COMMENT_PARENT_UNAVAILABLE` |
| `UNSUPPORTED_CHAT_PARTICIPANTS`, `INVALID_CHAT_PAYLOAD`, `SHARED_POST_NOT_FOUND` | `CHAT_UNSUPPORTED_PARTICIPANTS`, `CHAT_INVALID_PAYLOAD`, `CHAT_SHARED_POST_NOT_FOUND` |
| `DEVICE_TOKEN_CONFLICT` | (삭제: 17.6 upsert·양도 규칙으로 발생 경로 없음, Q439) |
| `SIMULATION_ACCESS_DENIED` | (삭제: `SIMULATION_NOT_FOUND`로 존재 감춤) |
| `PET_NOT_OWNED`, `SHELTER_NOT_OWNED`, `ADOPTION_APPLICATION_ACCESS_DENIED` (v3 1차) | (삭제: 각각 `PET_NOT_FOUND`·`SHELTER_NOT_FOUND`·`ADOPTION_APPLICATION_NOT_FOUND` 404로 존재 감춤, Q434) |
| 신규 (Q442) | `CHAT_ROOM_NOT_FOUND`(404), `CHAT_MESSAGE_NOT_IN_ROOM`(400), `SIMULATION_ANIMAL_NOT_FOUND`(404) |
| `ACTIVE_SIMULATION_ALREADY_EXISTS`, `SHOP_ITEM_*`, `EQUIPMENT_ALREADY_OWNED`, `REQUIRED_EQUIPMENT_MISSING`, `ITEM_NOT_IN_INVENTORY`, `EVENT_NOT_PENDING`, `INVALID_CHOICE`, `WALK_*` | `SIMULATION_ACTIVE_ALREADY_EXISTS`, `SIMULATION_SHOP_ITEM_*`, `SIMULATION_EQUIPMENT_ALREADY_OWNED`, `SIMULATION_EQUIPMENT_MISSING`, `SIMULATION_ITEM_NOT_IN_INVENTORY`, `SIMULATION_EVENT_NOT_PENDING`, `SIMULATION_INVALID_CHOICE`, `SIMULATION_WALK_*` |
| 신규 | `MISSING_REPORT_CLOSED`, `FOUND_REPORT_CLOSED`, `CATALOG_INVALID_DISTRICT_CODE`, `COMMENT_NOT_FOUND`, `USER_BOOKMARK_NOT_FOUND`, `ADOPTION_CERTIFICATE_*` |
| `ANIMAL_COMBINATION_NOT_FOUND` (v3 1차) | `CATALOG_COMBINATION_NOT_FOUND` (R11·R12: 기준정보는 `catalog` 도메인, `ANIMAL_`은 보호동물 단독) |
| `DISTRICT_NOT_FOUND` (v3 1차) | `CATALOG_INVALID_DISTRICT_CODE` (R12: `districts`는 `catalog` 도메인; 400 입력 오류라 이름도 Q442에서 변경) |
| `BOOKMARK_ALREADY_EXISTS` (v3 1차) | `USER_BOOKMARK_ALREADY_EXISTS` (R12: 북마크는 `user` 도메인) |
| `BOOKMARK_NOT_FOUND` (v3 1차) | `USER_BOOKMARK_NOT_FOUND` (R12) |

---

## 20. 결정 추적 (API 수준)

> 번호는 DB 문서 추적표와 공유합니다. DB v9에서 의도적으로 스킵한 차단/이관/AI 벡터 설계/보호소 삭제 엣지케이스는 재논의 대상으로 되살리지 않습니다.

### 20.1 종결된 안건

| 안건 | 결정 | 반영 |
|---|---|---|
| 인증 토큰 운영 | **Q409 B** refresh는 Redis TTL, DB 테이블 없음. 수치(1시간/14일)는 [가안] | 2.3·2.4 |
| 프로필 이미지 방식 | **Q408 A** `users.profile_image_seq` FK, 3.3·3.4 삭제 | 3.2 |
| 카카오 프로필 사진 | **Q408-1 A** 미수집, Kakao Developers 테스트 앱 | 2.1·2.5 |
| 이미지 업로더·소유권 | **Q407 A** `images.reg_usr_seq`, 403 `IMAGE_NOT_OWNED` | 4장 |
| ADMIN 전용 API 범위 | **Q410·Q411** 구현 범위 외. role CHECK는 예약값 유지 | 전 장 권한 열 |
| ADMIN의 구조·보호동물 보호소 지정 | **Q410** 10.3·11.4 SHELTER 전용, `shelterSeq` 필드 없음 | 10.3·11.4 |
| 모의 입양 범위 | **M1~M17** 범위 안, DB 11개 테이블 | 18장 |
| 행정구역 코드 | **Q406 A** 세 테이블 `district_code` + 서버 역지오코딩(네이버 legalcode 앞 5자리). **Q406-1 A** `districts` 시드 테이블 + `GET /districts`. **Q406-2 A** 실패 시 NULL·최근접·등록 거부 없음 | 0.2·5.5·7.1·7.4·7.5·8.1·8.4·8.5·9.1·21.1 |
| 초기 데이터 적재 경로 | **Q412** Flyway 시드 SQL, 스키마마다 마이그레이션 하나 | 5장·12.1·18장 |
| KAKAO 로그인 흐름 | **Q413 A** 앱 SDK access token → 서버 검증, 2단계 가입 | 2.1·2.5 |
| registrationToken 보관 | **Q414 A** Redis `auth:register:{token}` TTL 10분, 성공 시 삭제 | 2.1·2.5 |
| 응답 규약 | **Q415 A** 코드 봉투, 204 미사용, 중복 409, 디바이스 토큰만 upsert 200 | 1.2·16.2·17.6 |
| 시간 표기 | **Q416 A** ISO-8601 `+09:00` | 1.1 |
| 페이징·정렬·필터 | **Q417** `PageResponse`, page 0부터, size 최대 100, 엔드포인트별 정렬 화이트리스트 + `asc/desc`, 필드 필터 | 1.1·1.4·각 목록 |
| 에러 코드 명명 | **Q418 A** enum 상수명 = `code`, `{도메인}_상황`, 공통 `COMMON_*`·`AUTH_*` | 1.3·19장 |
| 로그아웃 시 FCM | **Q419 A** 2.4 요청에 `fcmToken` 선택 필드 | 2.4·17.7 |
| DB 스키마 구조 | **Q420 D** `core`(core-api·chat·notification 공유, FK 유지) + `simulation`(분리, 교차 FK 없음, 교차 읽기 허용, 스냅샷). Spring 서비스 4개 유지 | 0.2·14·17·18·22장 |
| SHELTER의 신고 작성 범위 | **Q421 A** 작성은 USER만, 담당 SHELTER는 배정 발견 신고 종료만 | 1.5·7.4·8.4·8.6 |
| 입양 DRAFT 보호소 노출 | **Q422 A** SUBMITTED 이후만 | 12.3·12.9 |
| 게시물·댓글·채팅 권한 경계 | **Q423 A** 수정·삭제 작성자 본인 | 6·7·8·13 |
| 사진 수정 DTO | **Q424 A** 전체 최종 배열, 서버가 diff·순서 계산 | 4.1·6.4·7.5·8.5·11.5 |
| CLOSED 신고 본문 수정 | **Q425 B** 읽기 전용, 수정하려면 재개 | 7.5·8.5 |
| 알림 생성 이벤트 | **Q426 A** 최소 집합, SYSTEM 유형만 예약 | 12.14·17.8 |
| 저장소 객체 정리 | **Q427 A** 커밋 후 best-effort 삭제, 실패 로그 | 4.1·12.5·12.7·13.5 |
| 신고 내역 조회 | **Q428 B** 15.2 삭제, 범위 외 | 15장 |
| 모의 입양 튜닝값 | **Q429 A** 코드 설정 [가안] | 18.0 |
| 스택 S1·S2 | JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, JPA + QueryDSL(OpenFeign 포크, KSP) | 머리말 |
| 위치 검색 S3 | **A** 위경도 + 하버사인, 사각 범위 사전 필터. 주소 변환은 네이버 지도 API | 8.4 |
| 업로드 S4 | **A** AWS S3 presigned PUT, 발급 컨텍스트 Redis, S3 HEAD 검증, 수명주기 규칙 | 4장 |
| 채팅 브로커 S5 · MQ S6 | **B** RabbitMQ STOMP relay. 서비스 간 이벤트도 RabbitMQ | 14장·17.8·2.6 |
| 검색 S7 | **OpenSearch 미도입**. `keyword`는 ILIKE, pg_trgm 인덱스 [가안] | 7.1·8.1·9.1·11.1 |
| 스케줄러 S8 | **A** `@Scheduled` 10분 주기 1개, simulation만. 다중 인스턴스 시 ShedLock | 18장·21.5 |
| 인증 세부 | RS256, core-api 발급·전 서비스 공개키 검증, 클레임 `userSeq/role/shelterSeq`, `@PreAuthorize` + 서비스 코드 소유권, STOMP CONNECT·SUBSCRIBE·SEND 인가, 즉시 차단 없음 | 1.1·14장 |
| R1 AI_MATCH 쓰기 주체 | **A** core-api가 실종 신고 행 잠금과 한 트랜잭션에서 `notifications`에 직접 INSERT, notification은 FCM만(쓰기 소유권 유일 예외, Q389·390 유지) | 17장·17.8 |
| R2 세션 스냅샷 컬럼 | **A** `shelter_seq` + 품종명·색상명·대표 사진 키. 값 복사(스냅샷)이며 FK 아님 | 18.0·18.1 |
| R3 발견 신고 구조 접수 범위 | **A** 담당이 아닌 보호소도 접수 가능(제약 없음). 보호소 업무 목록(8.8)은 담당 신고만 | 10.3 |
| R4 종료된 구조 기록 수정 | **A** DONE·FAILED·CANCELED 이후엔 기존 PATCH 10.4로 `rescueMemo`만. 전용 수정 API 없음 | 10.4 |
| R5 공고 종료일 수정 | **A** `noticeEndDate`는 기존 PATCH 11.5 필드로 수정 허용. 전용 API 없음 | 11.5 |
| R6 보호 종료 단독 처리 | **A** 반환·입양 없이 ENDED 허용, 사유 선택 | 11.8 |
| R7 삭제된 댓글 | **A** "(삭제된 댓글)"로 자리 유지, 삭제된 루트에 새 답글 불가, 기존 답글 유지 | 13장 |
| R8 채팅방 생성 주체 | **A** USER·SHELTER 모두 먼저 생성 가능 | 14.2 |
| R9 채팅 푸시 | **C** 서버는 구독 여부를 판단하지 않고 보낸 사람 외 전원에게 FCM 데이터 메시지, 표시 여부는 앱이 현재 방 기준으로 판단. Redis 구독 상태 없음 | 14장·17.8·21.6 |
| R10 8.8 보조 필드 | **A** `hasRescue` 선택 필터·`activeRescueSeq` 응답 유지, 기본은 전량 | 8.8 |
| R11·R12 도메인 분류 | 기준정보+행정구역 → `catalog`(`CATALOG_`), 북마크 → `user`(`USER_BOOKMARK_*`), 보호동물은 `animal`(`ANIMAL_`) 단독. core-api 13개 패키지 | 19장 |
| R13 배정 방식 필드 | **A** `assignmentMethod: DISTRICT|NEAREST` 응답 전용, DB 저장 없음 | 8.4 |
| R14 `districts` 번호 | **B** DB 문서에서 07번으로 넣고 이후 재번호 | DB v10 |
| R15 고아 S3 객체 | **A** 수명주기 1일 | 4.1 |
| R16 채팅 전송 방식 | **B** STOMP SEND `/app/rooms/{chatRoomSeq}/messages`, 서버 `@MessageMapping` 검증·저장·브로드캐스트, 오류는 `/user/queue/errors`에 HTTP와 같은 봉투. REST 메시지 전송 삭제, `clientMessageId`로 전송 확인 | 14장·21.4·22장 |
| Q430 감사 시각 매핑 | `mod_dttm` NULL 허용·INSERT 시 NULL·UPDATE 시만 채움(`@PreUpdate`). 베이스 엔티티 `BaseCreatedEntity`/`BaseTimeEntity` 분리 | DB v10 §1 (API 영향 없음) |
| Q431 시드 적재 | 보호소 `district_code`는 오프라인 계산값을 시드 SQL에 삽입, 시드 이미지는 S3에 먼저 올린 키를 SQL에 기록. 세부 절차는 적재 시점에 논의 | 9.1·DB v10 |
| Q432 AI 워커 연동 | 양방향 RabbitMQ. 요청 `{sourceType, sourceSeq, imageKeys[], speciesCode, occurredAt}`, 결과 `AiMatchFound {missingReportSeq, targetType, targetSeq, score, modelVersion}`. 워커 DB 미접근, 임계값은 워커, 자격 재검사·중복 처리는 core-api | 17.8·21.1·21.6 |
| Q433 refresh·탈퇴 계정 | refresh는 같은 RS256 키의 JWT(`userSeq`·`jti`·`typ=refresh`), access `typ=access`, 검증기가 `typ` 확인. 모든 쓰기 요청에서 `is_deleted` 재확인, 조회는 1시간 유예 | 1.1·2.3 |
| 문구 통일 (2026-11-03) | simulation `device_tokens` 미접근(17.6·18장), 알림 이벤트 `NotificationRequested`/`NotificationCreated` 구분(17.8·21.6), 세션 스냅샷은 이미지 키(18.0·21.5), SUBSCRIBE 인가는 chat 모듈 인터셉터(14장) | 해당 절 |
| Q434 403/404 정책 | **A** REST는 타인·타 보호소·비노출 자원 모두 도메인 `*_NOT_FOUND` 404. 403은 `IMAGE_NOT_OWNED`·STOMP `CHAT_ROOM_NOT_PARTICIPANT`·역할 불일치 `AUTH_FORBIDDEN`만. `PET_NOT_OWNED`·`SHELTER_NOT_OWNED`·`ADOPTION_APPLICATION_ACCESS_DENIED` 삭제 | 1.3·6~12·14·17·18·19장 |
| Q435 보호소의 모의 입양 열람 | **리포트만.** 18.4·18.7·18.11은 본인 전용. 세션 상세·행동 이력·지출 내역은 종료 시 LLM에 보내 리포트 `summary`로 집약 [가안: 실패 시 규칙 문구]. 보호소는 진행 중 세션 열람 불가 | 18.0·18.4·18.7·18.11·18.18·18.20·21.5 |
| Q436 답글 알림 수신자 | **루트 댓글 작성자만**(게시물 작성자에게 중복 발송 없음) | 13.3·17.8 |
| Q437 UNAVAILABLE 동물의 DRAFT 제출 | **차단.** 새 신청과 기존 DRAFT 제출 모두 409 `ADOPTION_ANIMAL_NOT_AVAILABLE`, DRAFT 행은 유지 | 11.6·12.6·12.14 |
| Q438 스탯 경고 알림 유형 | `STAT_WARNING` 대신 **스탯별 7종** `STAT_{HUNGER|HYDRATION|CLEANLINESS|FRIENDSHIP|STRESS|HEALTH|ACTIVITY}_WARNING`(총 10종). "스탯당 하루 1회"는 같은 세션·같은 유형 24시간 내 행 존재로 판정, 컬럼 추가 없음 | 18.0·18.22·21.5 |
| Q439 FCM 토큰 충돌 코드 | 17.6 upsert·양도로 충돌이 해소되므로 `NOTIFICATION_DEVICE_TOKEN_CONFLICT` 삭제 | 17.6·19장 |
| Q440 SESSION_END 트랜잭션 | 세션 종료·리포트·`simulation_notifications(SESSION_END)` INSERT까지 **한 트랜잭션**, `SimulationPushRequested` 발행만 커밋 후 | 18.5·21.5 |
| Q441 이벤트 만료 감지 | **조회(18.2·18.12·18.13)와 스케줄러 양쪽**에서 감지해 EXPIRED + `EVENT_EXPIRE` 로그. 산책 종료 시 PENDING WALK 이벤트도 EXPIRED | 18.13·18.17 |
| Q442 오류 코드 재검토 | 11.4 DONE 아닌 구조 409 `RESCUE_NOT_DONE`; 11.8 오류 코드 확정(400/409/404); 10.3은 삭제 신고 404·CLOSED 신고 접수 허용; simulation은 `SIMULATION_ANIMAL_NOT_FOUND` 자체 코드; 신규 `CHAT_ROOM_NOT_FOUND`·`CHAT_MESSAGE_NOT_IN_ROOM`; `SIMULATION_SPECIES_NOT_SUPPORTED`·`SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES` 400→409; `CATALOG_DISTRICT_NOT_FOUND`→`CATALOG_INVALID_DISTRICT_CODE`; 각 절 오류 항목에 404·이미지 코드 보강 | 19장·각 절 |
| 문구 통일 2차 (2026-11-03) | DB §5 구조 연결 선택, 11.7·12.11·12.12에 `end_reason`·`protection_ended_dttm` 설정 명시, `NotificationRequested` 발행 주체에서 chat 제거, 18.6 요청 `actionType`→`actionCode`, 교차 스키마 읽기 목록(simulation→`users` 포함, core-api→`simulation_sessions` 포함) | 해당 절 |
| Q443 이미지·수료증 읽기 URL | **A** 사진(`images/`)은 버킷 공개 읽기 + 고정 URL(`{public-base-url}/{image_key}`, 만료 없음). 수료증(`certificates/`)은 비공개 + 12.3 조회 시 presigned GET(TTL 10분 [가안]), 목록에는 `certificateAttached`만. 업로드는 계속 presigned PUT만 | 1.4·4.1·4.3·12.3 |
| Q444 리포트 `summary` 생성 | **비동기.** 종료 트랜잭션은 `summary=NULL`로 저장·커밋, 커밋 후 리스너가 LLM 어댑터 호출 → `summary` UPDATE. 실패 시 1회 재시도 후 규칙 문구 [가안: 타임아웃 20초]. 앱은 null이면 "요약 준비 중" | 18.0·18.18·21.5 |
| Q445 LOCAL 로그인 실패 제한 | Redis `auth:login-fail:{loginId}` 카운터, 5회 실패 시 15분 차단 [가안], 차단 중 429 `AUTH_LOGIN_LOCKED`, TTL 만료로만 해제, 성공 시 카운터 삭제 | 2.2·19장 |

### 20.2 미결 안건

**없음.** 남은 조정 항목은 [가안]으로 표시한 수치뿐입니다: 토큰 수명(1시간/14일), 업로드 컨텍스트 TTL(10분)·고아 객체 정리(24시간), 모의 입양 튜닝값(18.0 표), 채팅 구독 목적지 이름. 모두 코드 설정값이며 명세 버전을 올리지 않고 조정합니다. 시드 적재의 세부 절차(Q431)는 적재 시점에 별도 논의합니다. 새 안건은 Q446부터 번호를 붙입니다.

### 아직 스펙에 **없는 것** (의도된 제외)

- 회원 차단·차단 해제·신고 시 자동 차단, 관리자 신고 심사/신고 처리 완료 API, **신고 내역 조회(15.2, Q428)**, **ADMIN 전용 API 전체**(보호소 기준정보·행정구역 배정·입양 문항·SYSTEM 알림 관리 포함).
- 보호동물 보호소 간 이관, 보호소 삭제·정리, 사후관리.
- 모의 입양의 가상 동물(실제 보호동물 없이 시작), 동물병원·진단 전용 API, 상점·시나리오 관리 API, 리포트 수정·삭제.
- AI 이미지 임베딩 저장/벡터 차원·인덱스/모델 버전, AI 매칭 점수 보관, 실종↔발견/보호동물의 확정 동일 개체 FK.
- 입양 질문의 템플릿·버전/변경 이력, 별도 입양 프로세스/상태 이력/서류 심사 이력 API.
- 댓글 좋아요/채팅 메시지 개별 삭제, 채팅 푸시 DB 이력, 발견 신고 시 reporterName 별도 입력.

---

## 21. 핵심 서버 트랜잭션 흐름 (개발 체크리스트)

### 21.1 신고 등록 → AI 매칭 → 알림

1. 실종 신고 POST 요청의 `userSeq`는 로그인에서 결정(USER만). 유효 조합·사진 1~3장(소유 검증)·필수 속성 검증.
2. 좌표를 네이버 역지오코딩 어댑터(`port/out`)로 보내 법정동 코드 앞 5자리를 `district_code`로 얻음. 실패하면 NULL(Q406-2). 외부 호출은 트랜잭션 밖에서 먼저 수행.
3. 회원의 다른 비삭제 ACTIVE 신고 없음 확인 → DB UNIQUE `WHERE status='ACTIVE' AND is_deleted=false`도 적용 → 신고 및 연결 이미지 저장.
4. 커밋 후 매칭 요청 이벤트 `{sourceType:MISSING, sourceSeq, imageKeys[], speciesCode, occurredAt}`를 RabbitMQ로 발행(Q432). AI 워커가 페이로드와 S3 키만으로 비삭제·비탈퇴 유효 발견 신고/PROTECTING 보호동물과 비교. 매칭 구현 상세는 별도 POC.
5. 워커가 `AiMatchFound {missingReportSeq, targetType, targetSeq, score, modelVersion}`를 RabbitMQ로 발행하면 core-api가 자격을 재검사한 뒤 같은 쌍을 `missing_reports` 행 잠금 하에 확인하고 최초 1회만 `notifications`에 INSERT(17.8). `score`·`modelVersion`은 저장하지 않음.
6. 커밋 후 `NotificationCreated` 이벤트(발송만) → notification이 저장된 `message`와 동일한 문자열을 FCM에 전달. 실패해도 DB 보존/재시도 없음.

### 21.2 발견 신고 → 구조 접수 → 완료 → 보호동물

1. 발견 신고 POST(USER만): 역지오코딩으로 `district_code` 도출(실패 시 NULL) → `shelter_district_assignments` 매핑 → 없으면 하버사인 최근접 → 서버 자동 배정, 발견 신고 ACTIVE. 커밋 후 담당 보호소에 `SHELTER` 알림 이벤트.
2. 보호소가 구조 대상 선택 → rescue PROCESSING 즉시 생성; 같은 발견 신고의 비삭제 PROCESSING/DONE 합계 최대 1개.
3. DONE 처리: 구조 날짜·장소 필수, 완료시각 기록. 발견 신고 상태는 **그대로**.
4. 보호동물 POST: 구조가 DONE인 경우 `rescueSeq`로 참조 또는 직접 등록 `rescueSeq:null`. DONE 하나에 여러 보호동물 허용.
5. 보호동물 상세에서 구조 속성 4개는 **LEFT JOIN**으로 조회, 복제/별도 수정 금지.

### 21.3 입양 DRAFT → 제출 → 심사 → 실제 완료

1. 동물 PROTECTING+AVAILABLE 확인, 같은 USER/동물 활성 신청 하나만 허용하며 DRAFT 생성.
2. 현재 21문항의 `questionSeq` 키로 answers JSONB 저장·수정, DRAFT는 빈 값 허용.
3. 제출 때 필수/조건/숨김 답변 검증 + 신청자 인적사항 스냅샷; SUBMITTED로 전환.
4. 소속 보호소가 수동 승인(APPROVED) 또는 자연어 거절(REJECTED). APPROVED가 여러 개 있어도 아직 입양 완료 아님.
5. 실제 채택한 승인 신청에 완료 호출 → 신청 ADOPTED + 동물 ADOPTED/ADOPTED + completedApplicationSeq 일치 + 다른 활성 신청 자동 REJECTED **한 트랜잭션**.
6. 외부 입양이면 completedApplicationSeq NULL; 반환이면 단일 반환 INSERT + RETURNED/UNAVAILABLE + 활성 신청 자동 REJECTED.

### 21.4 채팅 전송·나가기와 재입장

1. `roomType=USER`면 두 USER 정렬/단일방 제약, `roomType=SHELTER`면 USER+보호소 제약 확인(14.2).
2. **전송(R16 B):** 클라이언트 SEND `/app/rooms/{chatRoomSeq}/messages` → `@MessageMapping`이 참여자·탈퇴 상대·본문 검증(실패 시 `/user/queue/errors`로 봉투 응답) → `chat_messages` INSERT·커밋 → `/topic/rooms/{chatRoomSeq}`로 `messageSeq`·`clientMessageId`를 실은 DTO 브로드캐스트(RabbitMQ relay) → `ChatMessageSent` 이벤트 → notification이 보낸 사람 외 참여자에게 FCM 데이터 메시지(R9 C, 표시는 앱 판단).
3. 나갈 때 참여자의 `lastHiddenMessageSeq`를 해당 방 최신 메시지 기준으로 갱신(기존 메시지 삭제 없음).
4. 같은 상대와 재입장해도 방은 재사용하고, **그 참가자에게만** 과거 메시지 조회 제외.
4. `lastReadMessageSeq`와 `lastHiddenMessageSeq`는 반드시 그 방의 메시지를 참조. 미읽음은 쿼리 계산.

### 21.5 모의 입양 세션 시작 → 돌봄 → 종료 리포트

1. 세션 시작(18.1): 요청자의 `IN_PROGRESS` 세션을 `SELECT ... FOR UPDATE`로 확인(부분 UNIQUE 병행) → `core` 스키마를 읽기 전용 JOIN해 동물 비삭제·보호 중·종 `DOG/CAT` 검증 → 품종에서 성격 스냅샷(NULL이면 랜덤) + 동물의 `shelter_seq`·대표 사진 `image_key`·품종명·색상명 스냅샷(R2) → 세션 INSERT(초기 스탯, `ends_dttm`, `balance=expected_cost`) → `action_logs(SYSTEM, SESSION_START)`. 교차 스키마라 동물 행은 잠그지 않음.
2. 모든 조회·행동·스케줄러 진입 시 **스탯 지연 계산**: `stats_calculated_dttm`부터 경과 tick × (기본 변화 × 성격 배율)을 적용해 0~100으로 클램프하고 `stats_calculated_dttm` 갱신. 계산 결과 `health=0`이면 FAILED, `ends_dttm` 경과면 COMPLETED로 6단계 종료 처리.
3. 돌봄 행동(18.6): 세션 잠금 → 지연 계산 → 기구 보유·소모품 수량 검사 → 수량 -1 → 물품 효과 반영 → `action_logs(CARE)` → `last_action_dttm` 갱신. 돈은 쓰지 않음.
4. 구매(18.9): 세션 잠금 → `simulation_transactions(PURCHASE)` INSERT → `balance -= amount`(음수 허용) → `simulation_inventory_items` UPSERT. 기구는 수량 1·재구매 409.
5. 이벤트: HOME·RANDOM은 스케줄러가 하루 1~2회 추첨, HOME·CONDITION은 지연 계산 시점·스케줄러에서 조건 판정(쿨다운 적용), WALK는 하트비트(18.15)에서 추첨. PENDING 1개 부분 UNIQUE. 응답(18.13): `occurrence FOR UPDATE` → PENDING·미만료 확인 → 효과 반영 → `cost>0`이면 `transactions(EVENT)` + 잔액 차감 → `action_logs(EVENT_ANSWER)` → ANSWERED. 만료는 EXPIRED + `action_logs(EVENT_EXPIRE)`, 효과 없음.
6. 종료(COMPLETED/ABANDONED/FAILED 공통): 스탯 최종 계산 → 진행 중 산책 ENDED·PENDING 이벤트 EXPIRED → 상태·`ended_dttm` 갱신 → `simulation_reports` INSERT(지출 집계·최종 스탯·카운트·summary) → `action_logs(SYSTEM, SESSION_END)` → `simulation_notifications(SESSION_END)` INSERT(같은 트랜잭션, Q440) → 커밋 → `SimulationPushRequested` 이벤트(notification이 FCM) + 커밋 후 비동기 리스너가 LLM 어댑터로 `summary` 생성·UPDATE(실패 시 1회 재시도 후 규칙 문구, Q444). 리포트는 `summary=NULL`로 먼저 저장되며 세션당 1건 UNIQUE, 영구 보존.
7. 알림 스케줄러(S8, 10분 [가안]): simulation의 `@Scheduled` 작업 하나가 진행 중 세션마다 2단계 계산 → 방치(`last_action_dttm` 8시간 경과) `NEGLECT`, 스탯 20 미만 `STAT_{스탯}_WARNING`(같은 세션·같은 유형 24시간 내 1회, Q438), 조건 이벤트 발생 `EVENT`를 `simulation_notifications`에 저장 후 커밋 → `SimulationPushRequested` 이벤트 → notification이 FCM 발송. simulation은 `device_tokens`를 읽지 않고 일반 `notifications`에도 쓰지 않음. 하트비트 10분 끊긴 산책은 자동 종료. core·notification에는 스케줄 작업 없음.

### 21.6 서비스 간 이벤트(RabbitMQ) 목록 [가안]

| 이벤트 | 발행 | 소비 | 용도 |
|---|---|---|---|
| `NotificationRequested` | core-api | notification | `notifications` INSERT + FCM (COMMENT·ADOPTION·SHELTER). 페이로드: 수신자·유형·message·target. chat은 이 이벤트를 내지 않음(채팅은 `ChatMessageSent`만, R9) |
| `NotificationCreated` | core-api | notification | **FCM 발송만**. core-api가 직접 INSERT한 `AI_MATCH` 행(R1·17.8 ⑥). 페이로드: `notificationSeq`·수신자·message |
| `SimulationPushRequested` | simulation | notification | `simulation_notifications` 저장 완료 건의 FCM 발송만(저장은 simulation, `device_tokens` 조회는 notification) |
| `UserDeleted` | core-api | notification·simulation | 디바이스 토큰 `is_active=false`, 진행 중 세션 ABANDONED + 리포트 |
| `DeviceTokenDeactivateRequested` | core-api(2.4 `fcmToken`) | notification | 로그아웃 기기 토큰 비활성화 |
| 매칭 요청(`MissingReportRegistered`·`FoundReportRegistered`·`ShelterAnimalRegistered`·`ImagesChanged`) | core-api | AI 워커 | 페이로드 `{sourceType, sourceSeq, imageKeys[], speciesCode, occurredAt}`(Q432). 워커는 DB 미접근 |
| `AiMatchFound` | AI 워커 | core-api | 페이로드 `{missingReportSeq, targetType, targetSeq, score, modelVersion}`(Q432). core-api가 자격 재검사 후 17.8 순서로 처리 |
| `ChatMessageSent` | chat | notification | 보낸 사람을 제외한 참여자 전원에게 FCM **데이터 메시지**(`roomSeq, messageSeq, senderDisplayName, preview`). 표시 여부는 앱이 판단, DB 저장 없음(R9 C) |

이벤트 이름·페이로드는 `common`의 `EventPublisher` 포트로 발행하며 MQ 어댑터를 빈으로 등록하면 `LoggingEventAdapter`를 대체합니다.

---

## 22. DB v10 테이블 ↔ API 대응표

| DB 테이블 | 스키마 / 쓰는 서비스 | 주요 API 절 | 주의 |
|---|---|---|---|
| `users`, `auth_providers`, `user_accounts` | core / core-api | 2·3 | USER 필수 개인정보/LOCAL 사전 발급(SHELTER만)/동시 소프트 삭제. `profile_image_seq` FK(Q408). refresh는 Redis(Q409). chat·notification·simulation은 읽기 전용 |
| `districts` | core / core-api(시드만) | 5.5·7·8·9 | 시군구 법정동 코드 5자리 마스터(Q406-1). 필터 값 검증·지역 선택 UI |
| `shelters`, `shelter_members`, `shelter_district_assignments` | core / core-api | 8·9 | 보호소당 계정 1개, `district_code` 단일 PK. 기준정보·배정은 Flyway 시드(Q412). 보호소 `district_code`는 오프라인 계산값을 시드에 삽입(Q406·Q431) |
| `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations` | core / core-api(시드만) | 5·18 | 조합 FK 한 개로 저장. `species_code DOG/CAT/ETC`(M8), `breeds.personality`(M7). simulation 읽기 전용 |
| `pets`, `pet_images` | core / core-api | 6 | 사진 1~3장, 실종 신고 스냅샷 별도 |
| `images` | core / core-api | 3·4·6~11·13·14 | `reg_usr_seq` 업로더(Q407), 마지막 참조 해제 후 원본 best-effort 삭제(Q427). chat은 `chat_messages.image_seq` 연결 행만 쓰고 `images` 행은 core-api만 생성 |
| `missing_reports`, `missing_report_images` | core / core-api | 7·17 | 회원당 ACTIVE 최대 1건, `district_code`(Q406), CLOSED 읽기 전용(Q425), AI 알림 기준 |
| `found_reports`, `found_report_images` | core / core-api | 8·10·17 | `district_code` 도출 → 매핑/최근접 자동 배정, 수동 재배정 API 없음, CLOSED 읽기 전용 |
| `rescue_records` | core / core-api | 10·11 | DONE 1:N 보호동물, 구조 정보 JOIN. SHELTER 전용 생성 |
| `shelter_animals`, `shelter_animal_images` | core / core-api | 11·12·18 | 최초 PROTECTING+UNAVAILABLE, 이름 없음. 모의 입양 시작 대상(simulation 읽기 전용) |
| `animal_returns` | core / core-api | 11 | 반환 시점=regDttm, 소유권 검증 아님 |
| `adoption_form_questions` | core / core-api(시드만) | 12 | 현재 21문항, 문항 이력/버전 없음, 관리 API 없음 |
| `adoption_applications` | core / core-api | 12 | JSONB questionSeq 키, 6상태, 별도 프로세스 없음. DRAFT는 보호소 비노출(Q422). 12.15 리포트 파생 조회 |
| `chat_rooms`, `chat_participants`, `chat_messages` | core / **chat** | 14 | 마지막 읽음/나가기 포인터, 직접 imageSeq. STOMP SUBSCRIBE·SEND 인가 근거, 메시지 쓰기는 SEND 핸들러만(R16 B) |
| `missing_report_comments`, `found_report_comments`, `shelter_animal_comments` | core / core-api | 13 | 각각 imageSeq 직접 FK, 삭제 루트의 답글 유지 |
| `reports` | core / core-api | 15 | PENDING만, 신고 심사 없음, 열람 주체·조회 API 없음(Q428) |
| `bookmarks` | core / core-api | 16 | 해제 물리 삭제, 중복 409 |
| `notifications`, `device_tokens` | core / **notification**(AI_MATCH 행만 core-api가 INSERT) | 17·18 | 5종, AI 최초 1회/FCM 재시도 없음. 모의 입양 푸시도 notification이 같은 토큰으로 발송(`SimulationPushRequested`). 디바이스 토큰 등록 upsert |
| `simulation_sessions` | **simulation** / simulation | 18.1~18.5 | USER당 IN_PROGRESS 1건 부분 UNIQUE, 종·성격·`shelter_seq`·동물 표시 정보 스냅샷, 스탯 7개 0~100, 잔액 음수 허용. `user_seq`·`animal_seq`는 논리 참조 |
| `simulation_shop_items` | simulation / simulation(시드만) | 18.8 | 9분류, 기구 `is_consumable=false`, 사진은 키 문자열(images FK 없음), 관리 API 없음 |
| `simulation_inventory_items` | simulation / simulation | 18.6·18.9·18.10·18.16 | UNIQUE(session,item), 기구 수량 1, 소모품 0 유지 |
| `simulation_transactions` | simulation / simulation | 18.9·18.11·18.13 | PURCHASE/EVENT, `balance_after`, 리포트 집계 근거 |
| `simulation_action_logs` | simulation / simulation | 18.6·18.7·18.13~18.17 | CARE/WALK/EVENT/SYSTEM, `stats_after_jsonb`, 방치 판정 |
| `simulation_walks` | simulation / simulation | 18.14~18.17 | 세션당 IN_PROGRESS 1건, 하트비트 자동 종료(스케줄러) |
| `simulation_event_scenarios`, `simulation_event_choices` | simulation / simulation(시드만) | 18.12·18.13 | HOME/WALK × RANDOM/CONDITION, 선택지 2개·`result_text` |
| `simulation_event_occurrences` | simulation / simulation | 18.12·18.13·18.15 | 세션당 PENDING 1건 부분 UNIQUE, 6시간 만료 |
| `simulation_reports` | simulation / simulation (core-api 읽기 전용) | 18.5·18.18~18.21·12.15 | 세션 1:1, 종료 시 1회 생성, 영구 보존, 보호소 열람만 |
| `simulation_notifications` | simulation / simulation | 18.22·18.23 | 4유형, 일반 알림과 분리, 푸시는 이벤트로 notification에 위임 |

---

**v3 종료.** 이 문서는 DB v10과 백엔드 공통 코드 규약을 기준으로 한 API 계약이며, [가안] 수치와 [초안] DTO 세부는 구현 중 조정할 수 있습니다. Q430~Q445는 2026-11-03에 확정해 v3에 반영했습니다. 새 안건은 Q446부터 번호를 붙여 질의응답으로 확정한 뒤 반영합니다. v2는 `animal_service_api_spec_v2.md`로 보존합니다.
