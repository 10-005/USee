# 유실·발견동물·보호소·입양 플랫폼 API 명세서 — v2 (초안)

> **작성일:** 2026-11-01  
> **근거:** `animal_service_db_spec_v9.md`(DB 통합 명세 v9, 현행 44개 테이블, Q406~Q412·M1~M17 최신 결정). 이전 버전 `animal_service_api_spec_v1.md`는 보존합니다.  
> **형식 참고:** `MOTIF_API_명세서_v0_11.md`(파일 내 문서 제목은 v0.13)  
> **문서 목적:** Android·BE·AI·보호소 화면이 공유할 1차 REST API 계약. **구현 완료 또는 팀 회의 확정 문서가 아닙니다.**  
> **백엔드 스택:** Kotlin + Spring Boot, Redis 사용 [확정]. RabbitMQ(메시지 큐)·OpenSearch(특징 검색)는 구상 중 [미정]. 세부 스택(S1~S8)은 20장 참조.

**표기 규칙**
- **[확정 정책]**: DB v9에서 확인되는 기능·권한·상태·제약, 또는 Q406 이후 질의응답으로 확정한 사항. **[가안]**: 추천안을 임시 채택했고 수치는 코드 설정값으로 둔 사항. **[초안]**: API URL, HTTP 방식, DTO 이름, 오류 코드, 조회 정렬·페이징, 실시간 통신 방식 등 제안. **[미정]**: 별도 결정 필요(질문 번호 병기). 확정 정책과 초안이 충돌하면 DB v9이 우선합니다.
- 표의 `/...` 경로는 모두 `/api/v1`을 앞에 붙인 상대 경로입니다. **[비인증]** 표시 외에는 로그인 필수입니다.
- 이 문서는 **클라이언트·보호소 업무 API**를 다룹니다. **ADMIN API는 이번 프로젝트 구현 범위 외**(Q410·Q411)이며 별도 장을 두지 않습니다. 내부 AI 워커 및 공공데이터 ETL 전용 API는 임의로 확정하지 않습니다.
- **DB v9만으로 확정할 수 없는 내용을 실제 확정처럼 기재하지 않습니다.** 서버 스펙의 세부 구현은 이 문서의 [초안] 또는 [미정]으로 유지합니다.

## v2 변경 요약 (v1 → v2)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q0-1 | 새 파일 v9/v2 생성, v8/v1 보존 | 본 문서 |
| Q0-2 | Kotlin + Spring Boot, Redis 확정. RabbitMQ·OpenSearch [미정], S1~S8 보류 | 머리말·20장 |
| Q406 | **보류** 행정구역 코드 저장·도출(서버 역지오코딩 / 필터 삭제 / 앱 전송). 재개 신호 "지도 관련 정리하자" | 7.1·8.1·8.4·9.1의 `districtCode` [미정] 유지 |
| Q407 | `images.reg_usr_seq` 추가. 이미지 연결은 업로더 본인만 | 4장, 403 `IMAGE_NOT_OWNED` |
| Q408 | `users.profile_image_seq` FK로 통일. 3.3·3.4 삭제, 3.2에 `profileImageSeq` | 2.5·3장·4.3 |
| Q408-1 | 카카오 프로필 사진·이메일·닉네임 미수집. Kakao Developers 테스트 앱 | 2.1·2.5 |
| Q409 | refresh token Redis TTL 저장(B). 수치는 [가안] | 2.3·2.4 |
| Q410 | ADMIN 구현 범위 외. 10.3·11.4 SHELTER 전용, `shelterSeq` 필드 없음 | 전 장 권한 열, 18장 삭제 |
| Q411 | `users.role` CHECK는 유지, ADMIN은 예약값 | 1.1 권한 |
| Q412 | 초기 데이터 적재 경로 [미정], API로 관리하지 않음은 확정 | 5장·12.1·18장 |
| Q413~Q419 | 인증·공통 규약 2차 질문, 답 없음 → 현행 초안 유지 [미정] | 20장 |
| M1~M17 | 모의 입양 확정(DB 11개 테이블 추가) | **18장 신설** 모의 입양(SIMULATION), 19·21·22장 |
| ADMIN 파급 | 18장 ADMIN 삭제, 2.2 SHELTER 전용, 15장 접수만, 모의 입양 관리 API 없음 | 각 장 |

## 0. 기준 및 적용 범위

### 0.1 MOTIF 명세서에서 참고한 작성 방식

| 참고한 요소 | 적용 방식 | 주의 |
|---|---|---|
| 도메인별 번호·API 인덱스 | 도메인별 엔드포인트 표 + 상세 항목 번호 | MOTIF의 도메인 경로를 그대로 복제하지 않음 |
| `result/code/message/data/timestamp` envelope | **[초안]** 같은 응답 껍데기 사용 | 동물 플랫폼 팀 공통 응답 규약으로 확정된 것은 아님 |
| 각 API의 요청·성공 `data`·실패 분기 | 대표 DTO, 상세 검증, 주요 에러 제시 | 모든 메시지·예시 ID는 가상 값 |
| page/size 및 별도 커서 규약 | 우선 일반 목록에 **[초안] offset** 제안 | 동적 채팅·알림 등 cursor 채택 여부 [미정] |
| HTTP 의미 및 오류 코드 분리 | 200/201/400/401/403/404/409 등 구분 | 204 vs envelope 통일 [미정] |

### 0.2 v9에서 특히 달라진 규칙 (API 강제)

1. **USER 한 명당 비삭제 `ACTIVE` 실종 신고는 최대 1건**입니다. 두 신고 모두 `CLOSED → ACTIVE` 재개가 가능합니다.
2. 발견 신고는 등록 시 담당 보호소를 자동 배정합니다(시군구 매핑 → 없으면 거리상 최근접). 등록·수정에 `shelterSeq`를 사용자가 지정하지 않습니다. 수동 재배정 API는 ADMIN 범위 외로 **없습니다**(행정구역 코드 도출 방식은 Q406 보류).
3. 구조 기록은 `PROCESSING/DONE/FAILED/CANCELED` 4종이며, **DONE 구조 1건에 보호동물 여러 마리**를 연결할 수 있습니다. 보호동물 구조 정보는 `rescueSeq`를 통해 조회하며 복사 저장하지 않습니다.
4. 보호동물은 **이름 컬럼이 없고** 첫 등록이 `PROTECTING+UNAVAILABLE`입니다. 반환 완료 시간은 `animal_returns.reg_dttm`을 사용합니다.
5. 입양 신청은 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` 6종이며, 동일 회원·동물의 비삭제 활성(DRAFT/SUBMITTED/APPROVED) 신청은 **통틀어 1건**입니다. `CLOSED` 상태는 없습니다.
6. 공통 입양 문항은 **현재 21개**, 답변은 `questionSeq`를 문자열 키로 하는 단일 JSON 객체입니다. 템플릿·버전·질문 이력·제출 당시 문항 스냅샷은 없습니다.
7. 댓글·채팅 이미지에는 각각 **`imageSeq` 단일 선택 필드(최대 1장)**를 사용합니다. 댓글의 부모 삭제 후 기존 답글은 유지합니다.
8. 사용자 차단·신고 심사·보호소 이관·입양 후 관리·AI 동일 동물 확정 링크 API와 **ADMIN 전용 API 전체**는 **범위 밖**입니다. 모의 입양은 v2에서 **범위 안**(18장)입니다.
9. 알림 저장 유형은 `COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM` **5종**, 채팅 푸시는 DB 알림 기록을 남기지 않습니다. 모의 입양 알림은 별도 테이블 `simulation_notifications`와 별도 API(18장)로 분리합니다.
10. `TIMESTAMPTZ`를 유지하고 DB 세션 시간대는 `Asia/Seoul`입니다. API는 시간대 정보가 있는 ISO-8601 시각으로 직렬화합니다.
11. 프로필 이미지는 USER·SHELTER 모두 공통 `images`의 `imageSeq`를 참조합니다. 이미지는 **업로더 본인만** 자기 리소스에 연결할 수 있습니다(Q407·Q408).
12. refresh token은 **Redis TTL**로 관리하며 DB 테이블이 없습니다(Q409). 초기 데이터(보호소·SHELTER 계정·행정구역 배정·동물 카탈로그·입양 문항·모의 입양 상점/시나리오)는 API로 관리하지 않습니다(Q412, 적재 경로 [미정]).

---

## 1. 공통 규약 (COMMON)

### 1.1 프로토콜·인증·명명

| 항목 | 명세 | 확정 수준 |
|---|---|---|
| Base URL | `https://{host}/api/v1` | [초안] 호스트·게이트웨이 미정 |
| 요청 본문 | UTF-8 JSON; `camelCase` | [초안] MOTIF 명세 형식 참고 |
| 식별자 | DB `*_seq` → API `*Seq` 숫자형. 클라이언트가 신규 DB PK 부여 금지 | [초안] 이름 / [확정 정책] 서버 PK 존재 |
| 인증 헤더 | `Authorization: Bearer {accessToken}` | [확정 정책] JWT access + refresh(Redis) / [가안] 수명 access 1시간·refresh 14일 |
| 권한 | `USER` 일반회원 / `SHELTER` 소속 보호소 담당자. `ADMIN`은 DB `users.role` CHECK에 예약값으로만 남고 **이번 범위에서 계정·권한·API 없음**(Q410·Q411) | [확정 정책] |
| 시간 | 예: `2026-10-29T17:50:00+09:00`(KST) 또는 동등한 UTC `Z`, offset 필수 | [초안] 출력 offset, [확정 정책] TIMESTAMPTZ·DB 서울 세션 |
| 날짜 | `YYYY-MM-DD`, 예: `2026-10-29` | [확정 정책] DATE 의미 |
| 페이지네이션 | `page=0&size=20` (0-base, 최대 100 제안) | [초안] |
| 정렬 | 미지정 시 최신순(`regDttm DESC`, PK DESC) 제안 | [초안] 도메인별 확정 필요 |
| 멱등성 | POST 중복 요청은 DB UNIQUE + 상태 검증으로 보호; 요청키(`Idempotency-Key`) 여부 미정 | [초안]/[미정] |
| 동시성 | 상태 전이/다중 행 변경은 트랜잭션, 관련 UNIQUE·FK·CHECK 및 BE 검증 | [확정 정책] |

**중요:** `SHELTER` 사용자의 `userSeq`는 실제 로그인 계정 ID이고, `shelterSeq`는 소속 보호소 ID입니다. 서버가 `shelter_members`로 소속을 확인합니다. 요청자가 타 보호소의 `shelterSeq`를 마음대로 지정해도 권한이 커지지 않습니다. 본인 ID는 가급적 URL의 `/me` 및 인증 컨텍스트에서 도출합니다.

### 1.2 공통 응답 envelope **[초안 — MOTIF 참고]**

**성공 — 단건**

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "missingReportSeq": 101, "status": "ACTIVE" },
  "timestamp": "2026-10-29T17:50:00+09:00"
}
```

**성공 — 목록** (이하 엔드포인트의 `data`만 각 절에 기재)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-29T17:50:00+09:00"
}
```

**실패**

```json
{
  "result": "FAIL",
  "code": "ACTIVE_MISSING_REPORT_ALREADY_EXISTS",
  "message": "이미 진행 중인 실종 신고가 있습니다.",
  "data": null,
  "timestamp": "2026-10-29T17:50:00+09:00"
}
```

- [초안] 성공 시 `result/code=SUCCESS`, 실패 시 `result=FAIL`과 구체적인 에러 코드. 주요 HTTP: 조회/수정/삭제 200, 생성 201, 비동기 접수 202 후보. 본문 없는 성공도 `200 + data:null`을 기본으로 제안합니다. **204 No Content 채택 여부와 중복 등록의 409/멱등 200 선택은 [미정] Q415**입니다.
- [초안] `INVALID_PARAMETER`의 `data`에 `[{"field":"...","message":"..."}]` 형태의 필드별 오류를 넣을 수 있습니다.
- 성공 응답 예제에서 사용자·동물 식별자, 시각, 전화번호 및 이미지 URL은 **가상 데이터**입니다.

### 1.3 공통 HTTP 오류 **[초안]**

| HTTP | code | 의미 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | 필수값/형식/개수/상태 변경 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증 미제공/만료/위조 |
| 403 | `FORBIDDEN` | 역할·소속·소유권 부족 |
| 403 | `ACCOUNT_UNAVAILABLE` | 계정 정지 등 상태(구체 상태 운영 정책 [미정]) |
| 404 | `NOT_FOUND` | 리소스 없음·삭제됨·비노출(상세 도메인 코드 우선) |
| 409 | `CONFLICT` | 상태 전이·중복·참조 중 업무 충돌 |
| 413 | `FILE_TOO_LARGE` | 정책상 파일 크기 초과(업로드 방식에 따른 HTTP 위치 미정) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 허용되지 않은 이미지/문서 타입 |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류 |

- 공통 코드의 최종 이름(`INVALID_PARAMETER` vs `INVALID_REQUEST` 등)과 명명 규칙은 **[미정] Q418**입니다. 19장 참조.

**공통 필터:** 로그인 필수; `users.is_deleted=true` 작성자의 일반 게시물·AI 후보를 비노출합니다. 단 채팅 메시지, 기존 댓글·답글, 저장 알림, 보호소 업무상 과거 입양 기록, 모의 입양 리포트 등은 보존하고 작성자를 `(탈퇴한 사용자)`로 표기합니다. 동물 및 신고의 `is_deleted` 등 비노출은 각 API에서도 확인합니다. 관리자 업무 권한이 없으므로 404/403 구분은 공개하지 않을 정보의 존재를 감추는 쪽을 [초안]으로 제안합니다.

### 1.4 목록 응답 DTO **[초안]**

- `Page<T>`: `{list: T[], page, size, totalElements, totalPages, hasNext, hasPrevious}`.
- `ImageRef`: `{imageSeq: number, imageUrl: string|null, displayOrder: number}`. `imageUrl`은 서버가 반환한 접근 URL이며 DB의 `image_key`를 임의 조합하지 않습니다.
- `AnimalClassification`: `{combinationSeq, speciesSeq, speciesName, breedSeq, breedName, colorSeq, colorName}`. **저장은 `combinationSeq`만** 사용합니다. `speciesSeq/breedSeq/colorSeq`는 표시를 위한 JOIN 파생 필드입니다.
- `ActorSummary`: `{userSeq, displayName, role, profileImage?: ImageRef|null}`. 회원 탈퇴 시 표시 이름 대체. SHELTER는 표시 이름으로 해당 보호소명 사용. 프로필 이미지는 USER·SHELTER 모두 공통 `images` 참조(Q408).
- `GeoPoint`: `{address, latitude, longitude}`. 위도 -90~90/경도 -180~180 검증은 [초안] 구현 규약.
- `[초안]` 목록의 검색 조건, `sort` 화이트리스트, cursor 기반 전환, 공유 URL 수명은 20장의 미결 안건(Q417) 참조.

### 1.5 접근 권한 요약 (DB 정책과 직접 연동)

ADMIN 열은 v2에서 제거했습니다(구현 범위 외). 식별정보 수정·수동 재배정·기준정보 관리처럼 v1에서 ADMIN에 배정했던 운영 기능은 **API 없이 초기 데이터 적재·직접 DB 작업으로 처리**합니다(Q412).

| 기능 | USER | SHELTER |
|---|---|---|
| 반려동물 등록/수정/삭제 | **본인만** | 불가 |
| 실종·발견 신고 작성 | USER | [미정: 해당 역할 작성 지원 여부] |
| 자신의 실종·발견 신고 수정/재개/종료 | 본인 | 발견 신고는 담당 업무 권한 범위 별도 검토 |
| 보호소 정보 변경 | 불가 | 소속 보호소 운영정보만(식별정보는 초기 데이터) |
| 구조·보호동물 등록·관리 | 불가 | 소속 보호소 업무 |
| 보호동물 반환/입양 심사·완료 | 불가 | 소속 보호소 |
| 입양 신청·저장·제출·취소 | **본인만** | 불가(업무 열람만) |
| 모의 입양 세션·돌봄·구매·이벤트·산책 | **본인만** | 불가(자기 보호동물 세션의 리포트·이력 열람만) |
| 댓글·채팅 | 허용 | 허용(대화 조합 제한) |
| 신고 | 신고자 역할의 최종 제한 [미정]; 보호소 계정 대상 신고 불가 | USER 대상 신고 가능 여부 확인 필요 |
| 북마크/일반 알림 | 일반회원 중심 [초안] | 알림 수신/업무 알림 |

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

**[확정 정책]** USER는 KAKAO 계정만 사용합니다. 탈퇴 후 재가입하면 기존 회원 행을 복구하지 않고 새 `users/user_accounts` 행을 만듭니다. 카카오 로그인은 **Kakao Developers 앱(테스트 앱)** 기준이며 Kakao Developers에 팀원으로 등록된 계정만 로그인됩니다. 서버는 카카오에서 **계정 식별자만** 받고 프로필 사진·이메일·닉네임은 가져오지 않습니다(Q408-1). **[초안]** 카카오 access token을 받아 서버에서 카카오 사용자 조회 API로 검증하고, 기존 회원이면 로그인 완료, 미가입자면 **가입 완료용 임시 컨텍스트만 반환**합니다. 카카오 인가코드 서버 교환 방식도 후보입니다(**[미정] Q413**).

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

- **핵심 정합성:** DB v9에서 USER의 `nickname/name/birth_date/phone` 네 필드는 모두 필수 CHECK입니다. 따라서 **카카오 검증 직후 개인정보가 없는 USER 레코드를 미리 INSERT하지 않습니다.** 2.5에서 필수 정보를 받은 뒤 최종 사용자/인증 행을 같은 트랜잭션에서 생성합니다.
- `registrationToken`은 미가입 카카오 계정의 확인 결과를 넘기기 위한 **[초안] 일시적 가입 컨텍스트**입니다. 보관 방식은 **[미정] Q414**(A: Redis `auth:register:{token}`에 카카오 id 저장, TTL 10분, 2.5 성공 시 삭제 / B: 서명 토큰 10분 만료, 재사용 방지 없음). 일반 access token 대신 사용하며 서비스 일반 API 접근 권한이 없습니다.
- **주요 실패 [초안]:** 401 `INVALID_KAKAO_TOKEN`, 403 `ACCOUNT_UNAVAILABLE`, 409 `KAKAO_ACCOUNT_CONFLICT`.

### 2.2 POST /auth/local/login

**요청 [초안]** `{ "loginId": "shelter01", "password": "..." }`.

**성공 200 `data` [초안]** `{ "accessToken":"...", "refreshToken":"...", "tokenType":"Bearer", "expiresIn":3600, "role":"SHELTER", "userSeq":55, "shelterSeq":7 }`. `shelterSeq`는 **항상 존재**합니다(SHELTER 계정은 `shelter_members` 1:1).

- **[확정 정책]** LOCAL은 SHELTER 전용, 계정 사전 발급(초기 데이터, Q412). `password_hash`만 보관하며 비밀번호 재설정 API는 구현 제외. ADMIN 계정은 발급하지 않습니다(Q411).
- **[초안]** 존재하지 않는 ID/틀린 비밀번호는 동일 401 `INVALID_CREDENTIALS`.

### 2.3 POST /auth/refresh · 2.4 POST /auth/logout

- **[확정 정책] refresh token은 Redis에 TTL로 저장하고 DB 테이블은 없습니다(Q409 B).** 운영 규칙 **[가안]**:
  - 키 `auth:refresh:{userSeq}:{jti}`, 값은 refresh 토큰 해시, TTL 14일. access 토큰 수명 1시간.
  - 로그인마다 새 jti를 발급하므로 기기마다 키 1개 → **다중 기기 로그인 허용**.
  - `2.3` 호출 시 키 존재 확인 → 삭제 → 새 jti로 access·refresh 재발급(**rotation**). 이미 삭제된 jti로 재요청이 오면 탈취로 간주해 **해당 사용자의 refresh 키 전부 삭제**.
  - `2.4` 호출 시 해당 jti 키 삭제. access 토큰 블랙리스트는 두지 않고 1시간 자연 만료.
- `2.3` **요청 [초안]** `{ "refreshToken":"..." }` → 성공 200 `data:{accessToken,refreshToken,tokenType,expiresIn}`.
- `2.4` **요청 [초안]** `{ "refreshToken":"..." }` → 성공 200 `data:null`. 로그아웃 시 FCM 토큰 처리는 **[미정] Q419**(A: 요청에 `fcmToken` 선택 필드, 있으면 그 기기 토큰만 비활성 / B: 앱이 17.7을 별도 호출). 탈퇴 시 전량 비활성화만 확정.
- **실패 [초안]** 401 `INVALID_REFRESH_TOKEN`(2.3, 만료·불일치·재사용 감지 모두 동일), 공통 오류.

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
- **[초안]** 2.1의 가입 컨텍스트 유효성 및 KAKAO 동일 계정 동시 가입 방지 후 하나의 트랜잭션에서 최종 계정 생성. 실패 시 미완성 USER DB 행을 남기지 않습니다.
- **실패 [초안]** 401 `INVALID_REGISTRATION_CONTEXT`; 409 `DUPLICATE_NICKNAME`, `DUPLICATE_PHONE`, `KAKAO_ACCOUNT_CONFLICT`; 400 `REQUIRED_USER_PROFILE_MISSING`.

### 2.6 DELETE /users/me

**요청 [초안]** 본문 없음, 성공 200 `data:null`.

- **[확정 정책]** `users`+`user_accounts` 소프트 삭제, 활성 device_tokens 비활성, 본인의 DRAFT/SUBMITTED/APPROVED 입양 신청을 CANCELED, 실제 ADOPTED 기록 보존. 진행 중 모의 입양 세션은 ABANDONED 처리하고 리포트를 생성·보존합니다(M6). Redis의 refresh 키는 전부 삭제. 일반 게시물은 소유자가 탈퇴했으므로 가상 비노출, 댓글·채팅·저장 알림 및 보호소 과거 신청서 보존.
- **[초안]** 탈퇴 전 확인 절차·비밀번호/카카오 재검증·비동기 푸시 세션 처리 미정.

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

- **응답** 수정된 프로필(3.1과 동일 DTO). **오류** 409 `DUPLICATE_NICKNAME`/`DUPLICATE_PHONE`. USER의 필수 4종을 null로 바꾸는 요청은 400.
- **[확정 정책] 프로필 사진(Q407·Q408):** `profileImageSeq`는 4.2로 확정한 공통 `imageSeq`이며 **요청자가 업로드한 이미지(`images.reg_usr_seq` = 본인)만** 연결할 수 있습니다. 아니면 403 `IMAGE_NOT_OWNED`, 없으면 404 `IMAGE_NOT_FOUND`. `profileImageSeq:null`은 해제이고, 필드 미전달은 유지입니다. 교체·해제로 참조를 잃은 이전 이미지는 **마지막 참조 해제 정책(DB Q383)**에 따라 다른 참조가 없을 때만 `images` 행과 원본 객체를 삭제합니다. 별도 프로필 업로드 경로는 없습니다.

### 3.5 GET /users/{userSeq}/profile

**성공 200 `data` [초안]** `{ "userSeq":18, "displayName":"발견도우미", "role":"USER", "profileImage":null }`. 비공개 실명/생년월일/전화는 반환하지 않습니다. 탈퇴 계정은 404 `USER_NOT_FOUND` [초안].

---

## 4. 이미지·파일 업로드 (MEDIA)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 4.1 | POST | `/images/upload-urls` | 로그인 | JPG/PNG 업로드 주소 발급 [초안] |
| 4.2 | POST | `/images` | 로그인 | 업로드한 이미지의 공통 메타데이터 확정 [초안] |
| 4.3 | POST | `/files/upload-urls` | USER | 입양 수료증 이미지/PDF 업로드 주소 발급 [초안] |

### 4.1 POST /images/upload-urls · 4.2 POST /images

- **[확정 정책]** 게시물/반려동물 사진은 JPG/JPEG/PNG, 각 **최대 10MB**, 게시물별 **1~3장**, 댓글·채팅은 **최대 1장**, 프로필은 **0~1장**. 공통 `images`는 `image_seq/image_key/mime_type/original_file_name/byte_size/reg_usr_seq`를 관리합니다. 동일 이미지를 다수 도메인에서 재참조할 수 있습니다.
- **[확정 정책] 업로더 기록(Q407):** 4.2로 메타데이터를 확정할 때 서버가 `images.reg_usr_seq`에 인증 주체를 기록합니다. 공공데이터·초기 데이터 적재분은 NULL입니다. 이후 **모든 이미지 연결 요청**(게시물·반려동물 `imageSeqs`, 댓글·채팅 `imageSeq`, 프로필·보호소 `profileImageSeq/backgroundImageSeq`)은 `reg_usr_seq`가 요청자와 같은 이미지만 허용하고, 다르면 403 `IMAGE_NOT_OWNED`. 같은 사용자의 반려동물 사진→실종 신고 재사용은 그대로 가능합니다. ADMIN 전역 예외는 없습니다.
- **[미정] 업로드 프로토콜(S4):** 아래는 S3 presigned PUT을 예시로 사용합니다. 객체 저장소(AWS S3 / MinIO)와 업로드 방식(presigned PUT 직접 업로드 / 서버 경유 multipart)은 스택 결정에 따릅니다. 경로·DTO는 바뀔 수 있습니다.

**4.1 요청 [초안]** `{ "fileName":"dog.jpg", "contentType":"image/jpeg", "byteSize":1234567 }`.

**4.1 성공 200 `data` [초안]** `{ "uploadUrl":"https://storage.example.invalid/...", "imageKey":"images/generated-key.jpg", "expiresInSeconds":600 }`.

**4.2 요청 [초안]** `{ "imageKey":"images/generated-key.jpg", "mimeType":"image/jpeg", "originalFileName":"dog.jpg", "byteSize":1234567 }`.

**4.2 성공 201 `data` [초안]** `{ "imageSeq":1001, "imageUrl":"https://cdn.example.invalid/images/generated-key.jpg" }`.

- 파일 키는 **클라이언트가 임의 지정하는 것이 아니라 서버가 발급한 업로드 컨텍스트에 한정**하는 것을 제안합니다. 4.1을 호출한 사용자만 같은 `imageKey`로 4.2를 호출할 수 있어야 하며, 저장소에 업로드된 객체의 실제 크기/MIME 서버 검증이 필요합니다.
- 이미지 수정 API는 각 도메인의 `imageSeqs` 전체 순서 배열을 교체하는 [초안] 방식으로 설계합니다. 공통 이미지는 **모든 참조가 해제된 경우에만 삭제**하며, 비즈니스 클라이언트에 임의의 `DELETE /images/{imageSeq}`는 제공하지 않습니다. 직접 FK 참조(`users.profile_image_seq`, `shelters.profile_image_seq/background_image_seq`, `simulation_shop_items.image_seq`)도 참조 확인 대상입니다.
- **오류 [초안]** 400 `INVALID_IMAGE_COUNT`, 413 `FILE_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, 404 `IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`.

### 4.3 POST /files/upload-urls

**요청 [초안]** `{ "purpose":"ADOPTION_CERTIFICATE", "fileName":"certificate.pdf", "contentType":"application/pdf", "byteSize":823456 }`.

**성공 200 `data` [초안]** `{ "uploadUrl":"https://storage.example.invalid/...", "storageKey":"certificates/generated.pdf" }`.

- **[확정 정책]** 교육 수료증은 신청서당 선택 1건, JPG/PNG/PDF 최대 10MB. 저장 위치는 `adoption_applications.certificate_storage_key`이고 공통 `images.imageSeq` FK가 아닙니다.
- 수료증(`certificateStorageKey`)만 저장소 키 문자열 방식이고, 프로필 이미지를 포함한 모든 사진은 공통 `imageSeq` 참조입니다(Q408). 두 방식을 섞지 않습니다.

---

## 5. 동물 기준정보 (ANIMAL CATALOG)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 5.1 | GET | `/animal-species` | 로그인 | 종 전체 목록 |
| 5.2 | GET | `/animal-species/{speciesSeq}/breeds` | 로그인 | 종의 품종 목록 |
| 5.3 | GET | `/animal-colors` | 로그인 | 색상 기준 목록 |
| 5.4 | GET | `/animal-combinations` | 로그인 | 유효 품종·색상 조합 조회 |

### 5.1~5.4 조회 규약

- `5.1` 성공 `data:[{"speciesSeq":1,"speciesCode":"DOG","speciesName":"개"}, ...]`. **[확정 정책]** `speciesCode`는 `DOG/CAT/ETC`(M8). 모의 입양은 `DOG/CAT`만 시작 가능합니다.
- `5.2` 성공 `data:[{"breedSeq":21,"speciesSeq":1,"breedName":"말티즈","personality":"FRIENDLY"}, ...]`. **[확정 정책]** `personality`는 `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY` 또는 `null`(M7). 모의 입양 시작 시 이 값을 세션에 스냅샷합니다.
- `5.3` 성공 `data:[{"colorSeq":3,"colorName":"흰색"}, ...]`.
- `5.4` 요청 `?breedSeq=21&colorSeq=3`(각 필터 선택) → `data:[{"combinationSeq":88,"speciesSeq":1,"breedSeq":21,"colorSeq":3, ...}]`.
- **[확정 정책]** 네 동물 엔터티는 모두 `combinationSeq` 단일 FK를 저장합니다. 종은 품종에서 결정되고 `UNIQUE(breed_seq,color_seq)`이며 실제 마스터 시드/기타 옵션 범위는 [미정]입니다.
- **[초안]** 기준정보는 비페이지 전량(수량 증가 시 서버 페이징 재검토). 기준정보 수정·시드 관리 API는 **없으며** 초기 데이터로 적재합니다(Q412, 경로 [미정]).

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

**요청 [초안]** `GET /pets?page=0&size=20`.

**목록 200 `data.list[]` [초안]** `{ "petSeq":5, "name":"초코", "classification":{"combinationSeq":88,"speciesName":"개","breedName":"말티즈","colorName":"흰색"}, "gender":"MALE", "representativeImageUrl":"https://...", "regDttm":"..." }`.

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
- `userSeq`는 JWT 인증 주체에서 설정합니다. `imageSeqs`는 순서대로 `pet_images.display_order=1..N`로 연결하는 **[초안] DTO**입니다.
- **오류 [초안]** 404 `ANIMAL_COMBINATION_NOT_FOUND`/`IMAGE_NOT_FOUND`, 400 `INVALID_IMAGE_COUNT`.

### 6.4 PATCH /pets/{petSeq} · 6.5 DELETE /pets/{petSeq}

- `6.4` **요청 [초안]** 6.3의 수정할 필드만 보냄; 이미지 변경 시 `imageSeqs` **전체 최종 배열** 전송. 성공 200 상세 DTO. `pets.mod_dttm`만 갱신(제거된 `mod_usr_seq`를 되살리지 않음).
- `6.5` 성공 200 `data:null`. **기존 `missing_reports.pet_seq` 연결을 유지하고 실종 신고/AI 매칭에도 영향 없음.** 이미지 참조도 다른 도메인에서 참조 중이면 원본 삭제 금지.
- **오류 [초안]** 403 `PET_NOT_OWNED`, 404 `PET_NOT_FOUND`.

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

**검색 파라미터 [초안]** `?status=ACTIVE&speciesSeq=1&breedSeq=21&districtCode=...&page=0&size=20`; 위치 검색 반경/정렬 기준은 [미정]. `districtCode` 필터는 **[미정] Q406(보류)**: DB에 행정구역 코드 컬럼이 없어 저장·도출 방식(서버 역지오코딩 / 필터 삭제 / 앱 전송)이 정해질 때까지 구현하지 않습니다. 사용자가 "지도 관련 정리하자"라고 하면 이 항목부터 재개합니다. 서버는 `combinationSeq→animal_breeds→animal_species`로 검색합니다.

**목록 200 `data.list[]` [초안]**
```json
{
  "missingReportSeq": 101,
  "name": "초코",
  "classification": { "combinationSeq":88, "speciesName":"개", "breedName":"말티즈", "colorName":"흰색" },
  "gender": "MALE",
  "status": "ACTIVE",
  "address": "대구광역시 ...",
  "occurredDttm": "2026-10-28T18:00:00+09:00",
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00"
}
```

**상세 200 `data` [초안]** 목록 필드 + `user:{ActorSummary}`, `petSeq`, `age`, `isNeutered`, `microchipNumber`, `description`, `latitude`, `longitude`, `images:[ImageRef]`, `isPhonePublic`, `contactPhone`, `isBookmarked`, `commentCount`, `modDttm`.

- **[확정 정책]** 공개 연락처는 **로그인 사용자에게만** 보이며, 기본 비공개 시에는 **작성자만** 실제 번호를 열람합니다. 접근 불가 시 `contactPhone:null`로 응답 [초안]. `isPhonePublic` 자체는 반환 가능.
- **[확정 정책]** 탈퇴 작성자·삭제 신고는 일반 목록/상세에서 비노출. `circumstances` 필드는 존재하지 않습니다.

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
**성공 201 `data` [초안]** `{ "missingReportSeq":101, "status":"ACTIVE", "aiMatchingRequested":true }`.

- `petSeq`는 선택, 지정 시 소유자 검증 후 당시 정보를 신고 행에 **스냅샷**으로 보존. 반려동물 미등록 신고도 가능하나 **신고의 `name`은 필수**입니다.
- 실종 주소/좌표/시각/이름/연락처/유효 조합/성별/사진 1~3장을 검증합니다. `contactPhone`은 회원 전화번호로 초기 제안하되 신고별로 수정 가능.
- **[확정 정책]** 작성 USER당 비삭제 ACTIVE 실종 신고 최대 1건(동일 반려동물 여부와 무관). 최초 등록/재개 시 유효한 발견 신고·보호동물과 AI 매칭 후보. 서버 트랜잭션과 부분 UNIQUE로 경합 차단.
- **오류 [초안]** 409 `ACTIVE_MISSING_REPORT_ALREADY_EXISTS`, 403 `PET_NOT_OWNED`, 400 `INVALID_IMAGE_COUNT`.

### 7.5 PATCH /missing-reports/{id} · 7.6 PATCH /.../status · 7.7 DELETE /...

- `7.5` **요청 [초안]** 변경 가능한 7.4 필드 일부 + `imageSeqs` (전량 교체 시). 상태는 별도 7.6 호출. 최초 등록 후 `petSeq` 변경 허용 여부 [미정]. 위치 수정은 **발견 신고와 달리 자동 보호소 배정 대상 자체가 아닙니다.** 이미지 추가/교체/삭제 시 ACTIVE이면 AI 재매칭 요청.
- `7.6` **요청 [초안]** `{ "status":"CLOSED" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{missingReportSeq,status}`. ACTIVE 재개 시 다른 ACTIVE 신고가 있으면 409. 실종 신고의 종료 사유/종료 시각 API 필드는 **없습니다**.
- `7.7` 성공 200 `data:null`. [확정 정책] 논리 삭제; AI 대상 제외, 사진은 마지막 참조 해제 시만 정리.
- 신고/보호동물 반환, 발견/구조 완료를 이유로 실종 신고가 자동 종료되지는 않습니다.

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

**검색 [초안]** `?status=ACTIVE&speciesSeq=1&districtCode=...&shelterSeq=7&page=0&size=20`. `districtCode`는 7.1과 같이 **[미정] Q406(보류)**.

**목록 200 `data.list[]` [초안]** `{ "foundReportSeq":210, "classification":{...}, "status":"ACTIVE", "shelterSeq":7, "address":"대구광역시 ...", "occurredDttm":"...", "representativeImageUrl":"..." }`.

**상세 200 `data` [초안]** 목록 필드 + `reporter:{ActorSummary}`, `shelter:{shelterSeq,shelterName}`, `age`, `isNeutered`, `microchipNumber`, `description`, `landmarkDescription`, `latitude`, `longitude`, `reporterPhone`, `isPhonePublic`, `closedReason`, `closedDttm`, `images`, `isBookmarked`, `commentCount`.

- **[확정 정책]** 비공개 `reporterPhone`은 작성자·**이 발견 신고의 담당 보호소**만 열람. 다른 사용자·다른 보호소에는 `null` 반환 [초안]. `reporterName` 별도 DB 컬럼 없음. 필요한 경우 `users.name`을 권한 범위 내 JOIN합니다.
- 삭제·탈퇴 작성자는 일반 노출에서 제외합니다. **발견 당시 목격 상황 전용 `circumstances` 없음**.

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
**성공 201 `data` [초안]** `{ "foundReportSeq":210, "status":"ACTIVE", "assignedShelterSeq":7 }`.

- 주소/좌표/시각/신고자 연락처/조합/성별/사진 1~3장은 필수입니다. 기본 비공개. 신고자 이름은 별도 필드가 아니므로 요청하지 않습니다.
- **담당 보호소 자동 배정은 서버 수행**: `district_code` 단일 매핑 우선, 미매핑 시 위경도 최근접. 발견 좌표→행정구역 코드 도출 방식은 **[미정] Q406(보류)**이며, 결정 전까지는 최근접 단계만 동작한다고 가정합니다. 자동 배정 실패 처리도 [미정]. `shelterSeq` 임의 입력 불허. 수동 재배정 API는 없습니다(ADMIN 범위 외).
- **[확정 정책]** 이 시점에 발견 신고 상태는 ACTIVE; 기존 ACTIVE 실종 신고와 AI 비교 후보.
- **오류 [초안]** 409 `SHELTER_ASSIGNMENT_UNAVAILABLE`, 404 `ANIMAL_COMBINATION_NOT_FOUND`.

### 8.5 PATCH /found-reports/{id} · 8.6 PATCH /.../status · 8.7 DELETE /...

- `8.5` **요청 [초안]** 8.4의 수정 필드 일부. `shelterSeq` 필드 불허; **주소·좌표가 바뀌어도 소속 보호소는 자동 재배정되지 않습니다**. ACTIVE 사진 변경 시 AI 재매칭.
- `8.6` **요청 [초안]** `{ "status":"CLOSED", "closedReason":"보호자가 직접 찾아감" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{foundReportSeq,status,closedReason,closedDttm}`. CLOSED에서 `closedDttm`은 서버 생성 필수, `closedReason` 선택. ACTIVE 재개 시 두 값은 `null`로 초기화.
- `8.7` 성공 200 `data:null`. 기존 구조 기록이 연결되어 있더라도 **구조 기록/FK/진행 상태는 변경하지 않습니다**.
- **중요:** 구조 접수·완료, 직접 반환, 보호동물 상태 전환 시에도 발견 신고를 자동 CLOSED로 바꾸지 않습니다.

### 8.8 GET /shelters/me/found-reports

**요청 [초안]** `?status=ACTIVE&page=0&size=20`; **응답** 담당 보호소의 발견 신고 목록, 각 항목에 `foundReportSeq`, 위치, 발생일, 연락처 가시성, 구조 연계 여부(복수 실패 이력 포함 여부는 [미정])를 표시.

---

## 9. 보호소 정보 (SHELTER)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 9.1 | GET | `/shelters` | 로그인 | 보호소 목록·검색 |
| 9.2 | GET | `/shelters/{shelterSeq}` | 로그인 | 보호소 상세·공개 프로필/배경 |
| 9.3 | GET | `/shelters/me` | SHELTER | 소속 보호소 상세 |
| 9.4 | PATCH | `/shelters/me` | SHELTER | 소속 보호소 운영정보·이미지 변경 |

### 9.1 GET /shelters · 9.2 GET /shelters/{id}

**요청 [초안]** `?keyword=...&districtCode=...&page=0&size=20`. `districtCode`는 **[미정] Q406(보류)**.

**상세 성공 200 `data` [초안]**
```json
{
  "shelterSeq": 7,
  "shelterName": "행복동물보호센터",
  "address": "대구광역시 ...",
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
- **[확정 정책]** `profileImageSeq`와 `backgroundImageSeq`는 각각 images 직접 FK, 이미지 0~1장. 보호소당 담당 계정 1개. `externalShelterCode` 노출 범위는 [미정].

### 9.3 GET /shelters/me · 9.4 PATCH /shelters/me

- `9.3` 소속 보호소 상세와 관리 가능한 항목 반환.
- `9.4` **요청 [초안]** `{ "description":"...", "operatingHours":"...", "closedDays":"...", "profileImageSeq":1701, "backgroundImageSeq":1702 }` (부분 수정). **주소·이름·전화·좌표 등 식별정보는 SHELTER 수정 불가**이며 초기 데이터 적재·직접 DB 작업으로만 변경합니다(ADMIN API 없음, Q412). 이미지 두 필드는 보호소 계정이 업로드한 이미지만 연결 가능(403 `IMAGE_NOT_OWNED`, Q407).
- **오류 [초안]** 403 `SHELTER_NOT_OWNED`, `IMAGE_NOT_OWNED`; 404 `SHELTER_NOT_FOUND`.

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

**목록 파라미터 [초안]** `?status=PROCESSING&page=0&size=20`.

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

### 10.3 POST /rescues

**요청 [초안]** `{ "foundReportSeq":210, "rescueReason":"길거리 배회", "rescueMemo":"담당자 출동" }` 또는 `{ "foundReportSeq":null, "rescueReason":"직접 구조" }`.

**성공 201 `data` [초안]** `{ "rescueSeq":301, "status":"PROCESSING", "regDttm":"..." }`.

- **[확정 정책]** 발견 신고를 선택하여 접수하면 즉시 PROCESSING 생성, 신고 없이 자체 접수도 가능. **SHELTER 전용**이며 `shelter_seq`는 토큰의 소속 보호소(`shelter_members`)에서 서버가 설정합니다. 요청에 `shelterSeq` 필드는 **없습니다**(Q410). 발견 신고 1건에 비삭제 `PROCESSING/DONE` 합계 최대 1건. 담당 보호소가 아닌 발견 신고에 대한 접수 허용 여부는 [미정].
- **오류 [초안]** 409 `ACTIVE_RESCUE_ALREADY_EXISTS`, 404 `FOUND_REPORT_NOT_FOUND`.

### 10.4 PATCH /rescues/{id} · 10.5 PATCH /.../status

- `10.4` **요청 [초안]** `{ "rescueReason":"...", "rescueAddress":"...", "rescueMemo":"..." }`; 완료 후 수정 허용 범위는 [미정].
- `10.5` **DONE 요청 [초안]** `{ "status":"DONE", "rescuedDate":"2026-10-29", "rescueAddress":"대구광역시 ...", "rescueReason":"배회 동물 구조", "rescueMemo":"..." }`.
- `10.5` **FAILED/CANCELED 요청 [초안]** `{ "status":"FAILED", "resultReason":"현장 도착 전 이동" }` 또는 CANCELED.
- **[확정 정책]** 최초 `PROCESSING → DONE/FAILED/CANCELED`. PROCESSING은 `completedDttm:null`; 종료 상태 3개는 서버가 시각 기록. DONE이면 `rescuedDate`와 공백 아닌 `rescueAddress` 필수. FAILED/CANCELED이면 공백 아닌 `resultReason` 필수.
- **오류 [초안]** 409 `INVALID_RESCUE_TRANSITION`, 400 `RESCUE_COMPLETION_FIELDS_REQUIRED`, `RESCUE_RESULT_REASON_REQUIRED`.

### 10.6 DELETE /rescues/{id}

성공 200 `data:null`. **연결된 보호동물이 한 마리라도 있으면 삭제 거부**(409 `RESCUE_HAS_ANIMALS`). 발견 신고가 삭제되었다는 이유로 기존 구조 기록이 삭제되거나 중단되지 않습니다.

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

**검색 [초안]** `?shelterSeq=7&protectionStatus=PROTECTING&adoptionStatus=AVAILABLE&speciesSeq=1&breedSeq=21&page=0&size=20`.

**목록 200 `data.list[]` [초안]** `{ "animalSeq":700, "shelterSeq":7, "classification":{...}, "gender":"UNKNOWN", "protectionStatus":"PROTECTING", "adoptionStatus":"UNAVAILABLE", "representativeImageUrl":"...", "noticeEndDate":"2026-11-08" }`.

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

- **SHELTER 전용.** `shelter_seq`는 토큰의 소속 보호소에서 서버가 설정하며 요청에 `shelterSeq` 필드는 **없습니다**(Q410). `rescueSeq` 선택. 값이 있으면 **DONE인 구조**만 참조 가능. 같은 구조에서 여러 마리를 등록할 수 있으며 `rescue_records.shelter_seq`와 새 보호동물의 보호소가 서로 달라도 DB FK에서 강제 금지하지 않습니다. 단 SHELTER의 타 보호소 구조 기록에 접근할 권한은 별개로 검토해야 합니다.
- `rescueSeq=null`이면 직접 등록이며 `rescueInfo`는 전부 null. USER는 작성 불가.
- **[확정 정책]** `PROTECTING+UNAVAILABLE` 초기 상태, 사진 1~3장, 공고 시작일=등록일, 종료일=시작+10일 (직접 등록 기준). 공공데이터 초기 적재 시에는 적재자가 공고 날짜를 직접 결정·입력합니다. 날짜 경과로 자동 상태 변경하지 않습니다.
- `11.4`를 일반 SHELTER의 수동 등록용으로 제한하고, ETL 등록 시 별도 내부 경로로 날짜 명시하도록 [초안] 분리합니다. 일반 요청에 공고 날짜를 자유롭게 받지 않는 제안이며 정책은 [미정].
- **오류 [초안]** 409 `RESCUE_NOT_DONE`, 404 `RESCUE_NOT_FOUND`, `ANIMAL_COMBINATION_NOT_FOUND`.

### 11.5 PATCH /shelter-animals/{id}

- **요청 [초안]** `{ "combinationSeq":88, "gender":"UNKNOWN", "description":"...", "healthCheck":"...", "vaccinationStatus":"...", "imageSeqs":[1702] }` (보내는 필드만 변경). 공고 기간 수정 권한/형식 [미정]; 기존 구조 정보는 보호동물 수정 요청에 포함하지 않으며 구조 수정이 필요하면 `10.4`에서 수정합니다.
- 사진 변경 시 PROTECTING이면 재매칭 작업 요청.

### 11.6 PATCH /.../adoption-status

**요청 [초안]** `{ "adoptionStatus":"AVAILABLE" }` 또는 `UNAVAILABLE` → 성공 200 `data:{animalSeq,protectionStatus,adoptionStatus}`.

- **[확정 정책]** PROTECTING에서만 AVAILABLE/UNAVAILABLE 지정. RETURNED/ENDED는 UNAVAILABLE, ADOPTED는 ADOPTED여야 합니다. AVAILABLE→UNAVAILABLE이어도 기존 DRAFT/SUBMITTED/APPROVED 신청은 자동 거절하지 않으며 새 신청만 차단.
- **오류 [초안]** 409 `INVALID_ANIMAL_STATUS_COMBINATION`.

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
- 반환 INSERT, 보호 상태 RETURNED + 입양 상태 UNAVAILABLE, 해당 동물의 DRAFT/SUBMITTED/APPROVED 신청 자동 REJECTED를 **하나의 트랜잭션**으로 처리. 실종 신고·발견 신고 자동 종료 없음.
- **오류 [초안]** 409 `ANIMAL_ALREADY_RETURNED`, `ANIMAL_NOT_PROTECTING`.

### 11.8 PATCH /.../protection-status · 11.9 DELETE /...

- `11.8` **요청 [초안]** `{ "protectionStatus":"ENDED", "endReason":"NATURAL_DEATH" }` (종료 사유는 RETURNED/ADOPTED/NATURAL_DEATH/EUTHANIZED/OTHER 중 규칙에 맞게 사용). 서버가 `protectionEndedDttm` 설정. **RETURNED는 11.7**, **ADOPTED는 12.11/12.12**를 통해 처리하여 관련 테이블 누락을 막습니다. 단순 상태 PATCH로 반환·입양 기록을 회피할 수 없도록 합니다.
- ENDED 처리 시 활성 신청 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED(사유 자연어, 처리시각 기록). **[미정]** ENDED의 상세 업무 승인 요건.
- `11.9` 성공 200 `data:null`. DRAFT/SUBMITTED/APPROVED 신청서가 하나라도 있으면 **409 `ANIMAL_HAS_ACTIVE_APPLICATIONS`**. 삭제된 보호동물은 일반 조회/AI 제외.

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

- **예시의 실제 질문 문구/보기/ID는 공통 21문항 원본이 아니며 설명용 가상 값입니다.** DB v9에는 실제 질문 텍스트 21개가 수록되지 않았으므로 이 API의 실제 운영 데이터는 초기 데이터로 적재합니다(Q412). 문항 관리 API는 없습니다.
- **[확정 정책]** 현재 문항 21개만 조회, `displayOrder` 순서대로 UI Q1~Q21 표시. `questionType`: `TEXT/SINGLE_CHOICE/MULTI_CHOICE`. `options`는 DB `options_jsonb`의 문자열 배열; `condition`은 `condition_jsonb`에서 `depends_on`(questionSeq), `operator`, `value`, `required_when_matched` 등을 반환합니다.
- **질문 버전/템플릿 ID는 반환하지 않습니다.** 이전 제출 답변도 현재 문항으로 보여주는 정책입니다.

### 12.2 GET /users/me/adoption-applications · 12.3 GET /adoption-applications/{id}

**목록 [초안]** `?status=SUBMITTED&page=0&size=20` → `data.list[]`에 `applicationSeq`, `animalSeq`, `shelterSeq`, `status`, 보호동물 대표 이미지, `regDttm`, `submittedDttm`, `rejectionReason` 요약 포함.

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
- 신청자 본인 외에는 소속 보호소 담당자의 업무 조회로만 상세 노출. 탈퇴 신청자의 과거 신청서와 수료증도 업무상 유지합니다(일반 공개 API에는 노출하지 않음). 담당 보호소는 12.15로 신청자의 해당 동물 모의 입양 리포트를 함께 열람할 수 있습니다(M14·M17, FK 없는 파생 조회).
- 문항을 수정해도 과거 `answers`는 **현재 문항 문구**와 함께 표시합니다. 별도 이력/버전/원본 문항 스냅샷 요청 API는 만들지 않습니다.
- **오류 [초안]** 404 `APPLICATION_NOT_FOUND`, 403 `APPLICATION_ACCESS_DENIED`.

### 12.4 POST /adoption-applications (DRAFT 생성)

**요청 [초안]**
```json
{ "animalSeq": 700, "answers": {}, "certificateStorageKey": null }
```
**성공 201 `data` [초안]** `{ "applicationSeq":901, "status":"DRAFT", "animalSeq":700 }`.

- **[확정 정책]** USER만 생성. 동물이 PROTECTING + AVAILABLE인 경우만 신규 신청을 받습니다. 동일 사용자·동물 비삭제 DRAFT/SUBMITTED/APPROVED 합계 최대 1개. DRAFT는 빈 `answers` 허용.
- `shelterSeq`는 서버가 대상 보호동물에서 읽어 **신청 당시 스냅샷** 저장. 고객이 임의 전달하지 않습니다.
- **오류 [초안]** 409 `ACTIVE_APPLICATION_ALREADY_EXISTS`, `ANIMAL_NOT_AVAILABLE`; 404 `SHELTER_ANIMAL_NOT_FOUND`.

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
- **[초안]** 요청의 `answers`는 **객체 전체 교체**로 정의해 PATCH 부분 키 병합 혼동을 피합니다. `certificateStorageKey:null`은 삭제 의도로 취급할지, 미전달과 구분할지 20장 확인.
- **오류 [초안]** 409 `APPLICATION_NOT_DRAFT`, 400 `INVALID_ADOPTION_ANSWER`.

### 12.6 POST /adoption-applications/{id}/submit

**요청 [초안]** `{}` 또는 본문 없음. **성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"SUBMITTED", "submittedDttm":"2026-10-29T17:50:00+09:00" }`.

- **[확정 정책]** DRAFT→SUBMITTED, 별도 새 신청서 행 생성하지 않음. 현재 질문 **21개** 기준 조건/필수/보기 응답 검증. 숨겨진 답변 제거, 제출 후 답변 불변. 신청자 실명·생년월일·전화번호를 **제출 시점**에 스냅샷.
- 제출 시 대상 동물의 입양 가능 여부와 동시성 재검증. 이 시점 이후에도 동일 회원·동물 활성 신청서 중복 생성 불가.
- **오류 [초안]** 409 `APPLICATION_NOT_DRAFT`, `ANIMAL_NOT_AVAILABLE`; 400 `REQUIRED_ADOPTION_ANSWER_MISSING`, `INVALID_ADOPTION_ANSWER`.

### 12.7 DELETE /adoption-applications/{id} · 12.8 POST /.../cancel

- `12.7` DRAFT만 **DB에서 실제 물리 삭제**하고 답변도 제거. 성공 200 `data:null`; DRAFT가 아니면 409 `APPLICATION_NOT_DRAFT`. 저장소 수료증 객체 정리 실패 대응은 [미정].
- `12.8` **요청 [초안]** `{}` → 성공 200 `data:{applicationSeq,status:"CANCELED"}`. **SUBMITTED에서만 USER 직접 취소**; APPROVED 뒤 직접 취소 API 없음. `cancellationReason`이나 별도 취소 이력 컬럼은 추가하지 않음.
- 탈퇴 시 활성(DRAFT/SUBMITTED/APPROVED) 신청을 CANCELED로 자동 변경하는 것은 별도 사용자의 직접 취소 제한과 구분합니다.

### 12.9 GET /shelters/me/adoption-applications

**요청 [초안]** `?animalSeq=700&status=SUBMITTED&page=0&size=20`.

**성공 200 `data.list[]` [초안]** `{ "applicationSeq":901, "animalSeq":700, "applicantName":"김가람", "status":"SUBMITTED", "submittedDttm":"...", "certificateAttached":true }`.

- **[확정 정책]** 소속 보호소에 제출된 신청 및 보존된 과거 기록을 업무상 조회. 심사 대상은 SUBMITTED. DRAFT 신청서의 보호소 노출 허용 범위는 [미정](초안에서는 상세 답변을 공개하지 않는 방향 권고).

### 12.10 POST /adoption-applications/{id}/review

**APPROVE 요청 [초안]** `{ "decision":"APPROVE" }`.

**REJECT 요청 [초안]** `{ "decision":"REJECT", "rejectionReason":"현재 보호 환경이 입양 조건에 부합하지 않습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"APPROVED", "reviewedDttm":"2026-10-29T17:50:00+09:00", "reviewedUsrSeq":55 }`.

- **[확정 정책]** `SUBMITTED → APPROVED/REJECTED`; REJECTED면 공백 아닌 **자연어 설명** 필수. `reviewedUsrSeq`에는 수동 처리자, `reviewedDttm`에는 심사 시각. 상태 이력/거절 단계/거절 코드 생성 금지.
- 여러 사용자 신청서의 APPROVED가 동시에 존재할 수 있습니다. **서류 승인만으로 보호동물을 ADOPTED로 만들지 않습니다.**
- **오류 [초안]** 409 `APPLICATION_NOT_SUBMITTED`, 400 `REJECTION_REASON_REQUIRED`.

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

- **[확정 정책]** 대상 신청서는 APPROVED 상태이고 실제 입양이 완료된 경우에만 호출. **원자적 변경:** 채택 신청서 ADOPTED → 보호동물 `protection_status=ADOPTED` + `adoption_status=ADOPTED` + `completed_application_seq=해당 신청서` → 다른 신청자의 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED.
- 자동 거절 `rejectionReason`은 실제 사유를 설명하는 한국어 문장; `reviewedDttm`은 자동 거절 시각, `reviewedUsrSeq=null`. 이미 승인된 다른 신청의 예전 심사시각은 별도로 남지 않습니다.
- **오류 [초안]** 409 `APPLICATION_NOT_APPROVED`, `ANIMAL_ALREADY_FINISHED`.

### 12.12 POST /shelter-animals/{animalSeq}/external-adoption

**요청 [초안]** `{}`. **성공 200 `data` [초안]** `{ "animalSeq":700, "protectionStatus":"ADOPTED", "adoptionStatus":"ADOPTED", "completedApplicationSeq":null }`.

- **[확정 정책]** 앱 내 신청서 없이 외부 경로를 통해 실제 입양 완료 가능. 서비스 신청 완료 FK는 **NULL 유지**. 반면 동물 상태는 ADOPTED/ADOPTED로 변경하며 기존 활성 신청은 자연어 이유와 함께 자동 REJECTED.
- **[미정]** 외부 입양자의 신상/입양 시각은 현행 테이블에 별도 칼럼이 없으므로 추가 입력/별도 기록 저장 API를 임의로 만들지 않습니다.

### 12.13 POST /adoption-applications/{id}/fail

**요청 [초안]** `{ "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"REJECTED", "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

- **[확정 정책]** 승인 후 실제 입양 진행 실패 시 해당 APPROVED 신청서를 REJECTED로 변경하고 `reviewedDttm`을 실패 처리 시각으로 갱신. 별도 입양 프로세스/실패 이력 테이블 생성 금지. **[초안]** 호출 주체 담당 보호소.

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
| AVAILABLE→UNAVAILABLE | **기존 활성 상태 유지** | PROTECTING+UNAVAILABLE | 새 신청만 차단 |
| 회원 탈퇴 | 본인 활성→CANCELED | 없음 | ADOPTED 이력은 보존 |

**[초안]** `ADOPTION` 유형 DB 알림/FCM 발송 대상과 각 전이 시점은 별도 운영 이벤트 매핑표로 확정해야 합니다. 상태 전이의 존재만으로 특정 사용자에게 알림을 자동 보낸다고 단정하지 않습니다.

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

**요청 [초안]** `GET /comments?targetType=MISSING&targetSeq=101&page=0&size=20`; 답글은 `GET /comments/MISSING_COMMENT/44/replies?page=0&size=20`.

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
- **[초안]** 정렬(최신순/오래된 순), 삭제 댓글의 이미지 반환/표시 정책, 댓글 총 개수/답글 개수 산출 방식은 회의 결정 필요.

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
- **[확정 정책]** 새 답글은 루트에만 허용. 탈퇴한 루트 댓글에는 신규 답글 불가. 삭제된 부모 댓글에 대한 **새** 답글 허용 여부는 [미정]이며, 기존 답글 보존은 확정.
- `imageSeq`는 요청자가 업로드한 이미지만 연결 가능(403 `IMAGE_NOT_OWNED`, Q407).
- **오류 [초안]** 400 `EMPTY_COMMENT`, `COMMENT_DEPTH_EXCEEDED`; 403 `IMAGE_NOT_OWNED`; 404 `COMMENT_TARGET_NOT_FOUND`; 409 `PARENT_COMMENT_UNAVAILABLE`.

### 13.4 PATCH /comments/{type}/{id} · 13.5 DELETE /comments/{type}/{id}

- `13.4` **요청 [초안]** `{ "body":"수정된 설명", "imageSeq":null }` → 성공 200 수정 댓글. `null`과 미전달 구별 필요. `body`·`imageSeq` 둘 다 빈 상태가 되면 400 `EMPTY_COMMENT`.
- `13.5` 성공 200 `data:null`. **루트 삭제 시 자식 답글은 삭제하지 않음**; 삭제 표시를 유지. 원래 이미지 참조가 마지막이면 공통 이미지 원본 정리 조건 검증.
- 댓글에 신고 기능을 적용할 때 신고 API는 **§15**를 사용합니다(`targetType=MISSING_COMMENT/FOUND_COMMENT/SHELTER_ANIMAL_COMMENT`).

---

## 14. 채팅 (CHAT)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 14.1 | GET | `/chats/rooms` | USER/SHELTER | 내 대화방 목록·미읽음 계산 |
| 14.2 | POST | `/chats/rooms` | USER/SHELTER | 상대와 대화방 생성 또는 기존 방 재입장 |
| 14.3 | GET | `/chats/rooms/{chatRoomSeq}` | 참여자 | 방 상세·상대방 정보 |
| 14.4 | GET | `/chats/rooms/{chatRoomSeq}/messages` | 참여자 | 메시지 내역 조회 |
| 14.5 | POST | `/chats/rooms/{chatRoomSeq}/messages` | 참여자 | TEXT/IMAGE/SHARED_POST 발송 [REST 초안] |
| 14.6 | PATCH | `/chats/rooms/{chatRoomSeq}/read` | 참여자 | 마지막 읽은 메시지 갱신 |
| 14.7 | POST | `/chats/rooms/{chatRoomSeq}/leave` | 참여자 | 대화방 나가기/기존 기록 숨김 |

### 14.1 GET /chats/rooms

**목록 200 `data.list[]` [초안]**
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
- **[초안]** SHELTER가 먼저 USER에게 방 생성을 요청할 수 있는지/상대방 선택 화면은 구체 서비스 흐름과 함께 확인할 사항. `targetShelterSeq`와 `targetUserSeq`는 분기별로 하나만 허용.
- **오류 [초안]** 409 `UNSUPPORTED_CHAT_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED`; 404 `CHAT_TARGET_NOT_FOUND`.

### 14.3 GET /chats/rooms/{id} · 14.4 GET /chats/rooms/{id}/messages

- `14.3` 성공 200 `data:{chatRoomSeq,roomType,counterpart,lastReadMessageSeq,lastHiddenMessageSeq,unreadCount}`.
- `14.4` 요청 [초안] `?beforeMessageSeq=4000&size=30` (**채팅 메시지는 offset 대신 seq 기준 역방향 목록을 우선 제안**, 일반 목록 규약의 예외). 최초는 `beforeMessageSeq` 생략. **응답 [초안]** `{ "list":[ChatMessage], "nextBeforeMessageSeq":3990, "hasNext":true }`.
- **[확정 정책]** 현재 참여자의 `last_hidden_message_seq` 이후만 반환. 공유 게시물이 삭제되면 메시지는 유지하고 게시물 상세는 `sharedPostUnavailable:true`로 표시 [초안]. 탈퇴 송신자는 `(탈퇴한 사용자)`로 표시.
- **[초안]** 해당 메시지 방 소속/정렬/페이징 경계는 서버에서 검증합니다.

### 14.5 POST /chats/rooms/{id}/messages

**TEXT 요청 [초안]** `{ "messageType":"TEXT", "body":"안녕하세요." }`.

**IMAGE 요청 [초안]** `{ "messageType":"IMAGE", "imageSeq":1701, "body":"사진입니다." }`.

**SHARED_POST 요청 [초안]** `{ "messageType":"SHARED_POST", "sharedTargetType":"FOUND", "sharedTargetSeq":210, "body":null }`.

**성공 201 `data` [초안]**
```json
{
  "chatMessageSeq": 4003,
  "chatRoomSeq": 501,
  "senderUserSeq": 17,
  "messageType": "SHARED_POST",
  "body": null,
  "imageSeq": null,
  "sharedTargetType": "FOUND",
  "sharedTargetSeq": 210,
  "regDttm": "2026-10-29T17:50:00+09:00"
}
```

- **[확정 정책]** TEXT는 비공백 본문 필수. IMAGE는 `imageSeq` **정확히 하나 필수**(송신자가 업로드한 이미지만, 아니면 403 `IMAGE_NOT_OWNED`), body는 선택. SHARED_POST는 `sharedTargetType/sharedTargetSeq` 필수이며 코드 `MISSING/FOUND/SHELTER_ANIMAL`만. 다형성 대상 존재·접근 권한은 BE 검증. `senderUserSeq`는 인증 계정(보호소 담당자도 users PK)에서 설정.
- 상대가 탈퇴했다면 신규 메시지 거부. **차단 기능이 없으므로 차단 상태 검증 API는 작성하지 않습니다.** 메시지 개별 삭제 기능 없음.
- DB `notifications`에 채팅 메시지 알림을 저장하지 않으며 FCM 푸시만 발송합니다. 발송 실패 시 재시도 없음.
- **실시간 수신 [미정] S5:** WebSocket/STOMP(RabbitMQ STOMP 브로커 / SimpleBroker / Redis pub/sub) 등 프로토콜과 이벤트명/ACK/중복전송 처리 방식 미확정. 이 POST는 **초안 REST 전송 계약**이며 팀이 소켓 전송으로 바꿀 수 있습니다.
- **오류 [초안]** 400 `INVALID_CHAT_PAYLOAD`; 403 `CHAT_ROOM_NOT_PARTICIPANT`, `IMAGE_NOT_OWNED`; 409 `CHAT_COUNTERPART_DELETED`; 404 `SHARED_POST_NOT_FOUND`.

### 14.6 PATCH /chats/rooms/{id}/read · 14.7 POST /.../leave

- `14.6` 요청 [초안] `{ "lastReadMessageSeq":4003 }` → 성공 200 `data:{lastReadMessageSeq:4003, unreadCount:0}`. 메시지 ID는 **해당 방 소속**이어야 하고 읽음 포인터를 과거로 되돌리지 않는 규칙을 [초안] 제안.
- `14.7` 요청 [초안] `{}` → 성공 200 `data:{lastHiddenMessageSeq:4003}`. 마지막 메시지 기준으로 과거 대화를 **본인에게만** 숨기며 방/메시지는 삭제하지 않습니다. 재입장 시 기존 방을 사용하고 이후 새 메시지만 표시.

---

## 15. 신고 (REPORT)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 15.1 | POST | `/reports` | 로그인(신고자 역할 [미정]) | 회원/게시물/댓글 신고 접수 |
| 15.2 | GET | `/users/me/reports` | 로그인 | 내 접수 신고 내역 [초안, 화면 요구 확인] |

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

### 15.2 GET /users/me/reports **[미정]**

**목록 [초안]** `?page=0&size=20` → `{list:[{reportSeq,targetType,targetSeq,reasonDetail,status:"PENDING",regDttm}],...}`. **DB에는 기록이 존재하지만 이를 사용자 화면에 노출하는 기능은 별도 확정되지 않았습니다.** ADMIN 열람 화면이 없어진 v2에서는 신고 접수자 본인의 내역 조회만 남은 후보이며, 유지·삭제는 댓글·채팅·신고 묶음 질의에서 확정합니다.

---

## 16. 북마크 (BOOKMARK)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 16.1 | GET | `/users/me/bookmarks` | USER | 내가 저장한 게시물 목록 |
| 16.2 | POST | `/bookmarks` | USER | 게시물 북마크 등록 |
| 16.3 | DELETE | `/bookmarks` | USER | 게시물 북마크 해제 |

### 16.1 GET /users/me/bookmarks

**요청 [초안]** `?targetType=MISSING&page=0&size=20` (`targetType` 생략 시 세 종류 통합).

**목록 `data.list[]` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101, "regDttm":"...", "postSummary":{...} }`.

- **[확정 정책]** 원본 게시물 자체가 삭제되거나 작성자가 탈퇴하면 **목록에서 비노출**. 신고가 CLOSED거나 보호동물이 ADOPTED 상태여도 북마크는 유지됩니다.

### 16.2 POST /bookmarks · 16.3 DELETE /bookmarks

**요청 [초안]** `{ "targetType":"MISSING", "targetSeq":101 }`.

- `16.2` **성공 201 `data` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101 }`.
- `16.3` **성공 200 `data:null` [초안]**. `DELETE` 본문 사용을 피하려면 `/bookmarks/{targetType}/{targetSeq}`로 변경 가능 [미정].
- **[확정 정책]** 대상은 `MISSING/FOUND/SHELTER_ANIMAL` 3종. 회원+대상 UNIQUE, 해제는 **물리 DELETE**. 동일 북마크 추가 시 409 `BOOKMARK_ALREADY_EXISTS`를 [초안] 기본으로 제안(반복 POST를 멱등 200으로 처리할지는 **[미정] Q415**).

---

## 17. 알림·FCM (NOTIFICATION)

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

**요청 [초안]** `?isRead=false&notificationType=AI_MATCH&page=0&size=20`.

**목록 200 `data.list[]` [초안]**
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
- `ADOPTION` 클릭 대상 타입 `ADOPTION`은 입양 신청 `applicationSeq`를 가리킵니다. `SYSTEM`은 운영 공지/점검 안내(ADMIN 발송 API가 없으므로 발송 주체·트리거는 [미정]). `SHELTER`는 담당 발견 신고 배정 등 보호소 고유 업무 유형이며 보호소가 받는 모든 알림의 범용 타입이 아닙니다.

### 17.2~17.5 알림 읽음·삭제

- `17.2` 성공 200 `data:{notificationSeq,isRead:true,readDttm:"..."}`; 타인 알림 수정 금지.
- `17.3` 성공 200 `data:{updatedCount:5}` [초안]. 읽은 시간은 서버가 설정.
- `17.4` 및 `17.5` 성공 200 `data:null` [초안]. **[확정 정책]** `is_deleted=true`로 소프트 삭제(개별/전체), 별도 숨김 기능 없음.
- 삭제된 AI_MATCH 알림도 **과거 알림 존재 확인에 포함**되어 같은 쌍의 알림을 재생성하지 않습니다.

### 17.6 POST /notifications/device-tokens · 17.7 DELETE /.../current

**등록 요청 [초안]** `{ "fcmToken":"fcm-device-token" }`.

**등록 성공 200 `data` [초안]** `{ "active":true }`.

- **[확정 정책]** 계정당 활성 FCM 토큰 최대 1개, 동일 토큰을 여러 계정에서 동시에 활성화 금지. 등록 시 기존 토큰을 비활성화하고 새 토큰을 활성화합니다. 탈퇴 시 계정의 활성 토큰 전부 비활성화.
- `17.7` **요청 [초안]** 토큰을 BODY 또는 인증된 현재 기기의 등록 식별자로 지정하는 방식 [미정]. 성공 `data:null`; DB에는 활성 해제 시각 `mod_dttm` 기록. 로그아웃(2.4)과의 역할 분담은 **[미정] Q419**.
- 모의 입양 푸시(18장)도 **같은 활성 FCM 토큰**으로 발송합니다. 별도 토큰 등록은 없습니다(M16).

### 17.8 알림 생성·AI 매칭 발송 조건 (내부 비공개 동작)

| 이벤트 | 서버 규칙 | 상태 |
|---|---|---|
| 댓글/답글 | 저장 알림 생성 후보, 수신 대상·자기 댓글 제외 여부 미정 | [초안] |
| 실종×발견 매칭 | ACTIVE·비삭제·비탈퇴 양쪽 검증; 실종 작성자 **한 명**에게만 저장·FCM | [확정 정책] |
| 실종×보호동물 매칭 | 실종 ACTIVE, 보호동물 PROTECTING·비삭제; 입양 상태와 무관 | [확정 정책] |
| 매칭 발생 계기 | 실종/발견/보호동물 신규 등록, CLOSED→ACTIVE 재개, 유효 사진 추가/교체/삭제 | [확정 정책] |
| 같은 AI 매칭 쌍 | `notification_type='AI_MATCH'` + `(missing_report_seq,target_type,target_seq)` 생애 1회 | [확정 정책] |
| 보호소 업무 | 발견 신고 자동 담당 배정 관련 수신 이벤트 구체화 필요 | [초안] |
| 입양 심사·완료 | ADOPTION 알림의 구체 이벤트와 수신 대상 구체화 필요 | [초안] |
| 일반 시스템 안내 | SYSTEM 유형 저장 가능; ADMIN 발송 API가 없으므로 발송 주체/트리거 별도 정의 | [확정 정책]/[미정] |
| 채팅 수신 | **FCM만**, `notifications` DB 행 저장하지 않음 | [확정 정책] |
| 모의 입양 | `notifications`에 저장하지 않음. `simulation_notifications` 별도 저장 + FCM(18.22) | [확정 정책] |

**AI 중복 알림 필수 처리 순서 [확정 정책]**: ① 유효 동물/신고 상태 검사 → ② 해당 `missing_reports` 행에 `SELECT ... FOR UPDATE` 획득 → ③ `is_deleted=true` 알림까지 포함해 동일 쌍 `AI_MATCH` 중복 조회 → ④ 없으면 `message TEXT NOT NULL` 및 `targetType/targetSeq`로 한 행 INSERT → ⑤ 트랜잭션 커밋 → ⑥ DB에 저장한 **똑같은 message 문자열**을 FCM body로 발송. 별도 전용 AI UNIQUE 인덱스는 **없습니다**. FCM 실패 시 DB 알림은 보존하고 재시도하지 않습니다.

---

## 18. 모의 입양 (SIMULATION)

> v1의 18장(ADMIN)은 **삭제**했습니다. ADMIN API는 구현 범위 외(Q410·Q411)이며, v1에서 ADMIN에 두었던 보호소 기준정보·행정구역 배정·입양 문항·SYSTEM 알림 관리는 **API 없이 초기 데이터 적재·직접 DB 작업**으로 처리합니다(Q412). 이 장은 DB v9에서 추가된 모의 입양 11개 테이블(`simulation_*`)의 API입니다. 결정 번호 M1~M17은 DB v9 추적표와 같습니다.

### 18.0 공통 규칙

**[확정 정책]**
- **대상·시작(M1·M7·M8):** 보호동물 상세(11.2)의 "모의 입양" 버튼으로 **실제 보호동물**에서 시작합니다. 종(`DOG/CAT`)과 성격(`ACTIVE/TIMID/AGGRESSIVE/FRIENDLY`)은 시작 시 동물→조합→품종에서 읽어 세션에 **스냅샷**하고, 이후 동물·품종이 바뀌어도 세션에는 영향이 없습니다. 종이 `ETC`이면 시작 불가. 품종의 성격이 NULL이면 랜덤 배정 **[가안]**.
- **시간(M2):** 실시간. 1게임일 = 1실제일, 총 **7일**. 스탯은 **서버가 조회·행동·스케줄러 시점에 마지막 계산 시각부터 경과 tick만큼 지연 계산**합니다. 클라이언트는 스탯을 직접 올리거나 시간을 보내지 않습니다. 모든 응답의 스탯은 계산 후 값입니다.
- **스탯(M3):** 7종 `hunger/hydration/cleanliness/friendship/stress/health/activity`, 각 0~100 정수. `stress`만 낮을수록 좋습니다. 초기값·tick당 변화·성격/종 배율은 **코드 설정값 [가안]**이며 DB에 두지 않습니다(아래 표).
- **돈(M5):** 시작 잔액 = 입력한 일주일 예상 비용(원, 정수). 펫샵에서 인벤토리로 가져올 때 결제되며 **음수 잔액을 허용**합니다. 밥그릇·물그릇·화장실 같은 기구도 구매 대상입니다.
- **세션 상태(M6):** `IN_PROGRESS / COMPLETED / ABANDONED / FAILED`. USER당 진행 중 세션 **1개**. 예정 종료 시각 도달 → COMPLETED, 포기 → ABANDONED, 건강 0 → FAILED("돌봄 부족" 표현, 죽음 표현 없음). 종료 시 리포트를 1회 생성해 **영구 보존**하고 새 세션을 시작할 수 있습니다. 탈퇴 시 진행 중 세션은 ABANDONED.
- **이벤트(M11·M15):** 집 돌발 이벤트, 산책 중 동물 행동, 아픔(병)을 **시나리오→선택지 2개→효과 JSON** 엔진 하나로 처리합니다. 시나리오는 `HOME/WALK` 장소와 `RANDOM/CONDITION`(스탯 조건) 발생 유형을 가집니다. "아픔"은 건강 등 스탯 조건으로 발생하는 HOME·CONDITION 이벤트이고, 동물병원은 그 이벤트의 선택지(진료비·진단 문구 `resultText`)입니다. **별도 병원·진단 API는 없습니다.** 미응답(PENDING) 이벤트는 세션당 동시에 1개.
- **산책(M12):** 개·고양이 모두 가능. 고양이는 종 배율로 스트레스 증가·활동 회복 감소 **[가안]**. 산책 중 동물 행동은 WALK 시나리오로 하트비트 응답에 실려 오고, 사용자가 먼저 하는 행동은 물 주기·간식 주기 두 가지입니다.
- **알림(M13·M16):** 일반 `notifications`와 **완전 분리**된 `simulation_notifications`에 저장하고, 같은 활성 FCM 토큰으로 푸시합니다. 유형 `NEGLECT/STAT_WARNING/EVENT/SESSION_END`. 산책 중 WALK 이벤트는 푸시 없이 하트비트 응답으로 전달합니다.
- **리포트(M14·M17):** 유저에게 쌓이고 보호소는 열람만. "보호소"는 **세션 동물의 `shelter_seq` 소속 SHELTER 회원**을 뜻하며, 자기 보호동물로 진행된 세션의 리포트·이력과 입양 신청 상세 경로(12.15)로 열람합니다. 수정·삭제 API는 없습니다.
- **마스터 데이터(Q412):** 펫샵 물품·이벤트 시나리오·선택지는 초기 데이터로 적재하며 관리 API가 없습니다.

**권한 표기:** "본인" = `simulation_sessions.user_seq` 가 요청자. "보호소" = 위 정의. 그 외 접근은 403 `SIMULATION_ACCESS_DENIED`(존재 자체를 감추려면 404 `SIMULATION_NOT_FOUND`, [초안]). 변경 계열(행동·구매·이벤트 응답·산책·포기)은 세션이 `IN_PROGRESS`일 때만 허용하고 아니면 409 `SIMULATION_NOT_IN_PROGRESS`.

**코드 설정값 [가안]** (DB 저장 없음, 튜닝 대상)

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
| 18.4 | GET | `/simulations/{sessionSeq}` | 본인/보호소 | 세션 상세 |
| 18.5 | POST | `/simulations/{sessionSeq}/abandon` | 본인 | 중도 포기 → ABANDONED + 리포트 |
| 18.6 | POST | `/simulations/{sessionSeq}/actions` | 본인 | 돌봄 행동 `{actionType, itemSeq?}` |
| 18.7 | GET | `/simulations/{sessionSeq}/action-logs` | 본인/보호소 | 행동 이력 |
| 18.8 | GET | `/simulations/shop/items` | 로그인 | 펫샵 물품 목록 `?speciesType=` |
| 18.9 | POST | `/simulations/{sessionSeq}/purchases` | 본인 | 구매 `{itemSeq, quantity}` |
| 18.10 | GET | `/simulations/{sessionSeq}/inventory` | 본인 | 보유 물품 + 잔액 |
| 18.11 | GET | `/simulations/{sessionSeq}/transactions` | 본인/보호소 | 지출 내역 |
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
  "animal": { "animalSeq": 700, "shelterSeq": 7, "representativeImageUrl": "https://..." },
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

- **[확정 정책]** USER만. 선행 조건: 요청자에게 진행 중 세션 없음(부분 UNIQUE), 동물 비삭제, 종 `DOG/CAT`, **[가안]** 동물이 보호 중(`protectionStatus=PROTECTING`; 입양 완료·반환·종료 동물 불가). `expectedCost`는 0 이상 정수(원). 시작 트랜잭션: 활성 세션 검사(`FOR UPDATE`) → 종·성격 스냅샷 → 세션 INSERT(초기 스탯, `ends_dttm = started + 7일`, `balance = expectedCost`) → `action_logs(SYSTEM, SESSION_START)`.
- 인벤토리는 **비어 있는 상태**로 시작합니다. 밥·물·화장실을 쓰려면 18.9로 기구를 먼저 사야 합니다(M5·M9).
- **오류 [초안]** 409 `ACTIVE_SIMULATION_ALREADY_EXISTS`, `SIMULATION_ANIMAL_NOT_AVAILABLE`; 400 `SIMULATION_SPECIES_NOT_SUPPORTED`, `INVALID_PARAMETER`; 404 `SHELTER_ANIMAL_NOT_FOUND`.

### 18.2 GET /simulations/me/active · 18.3 GET /simulations/me · 18.4 GET /simulations/{sessionSeq}

- `18.2` 성공 200 `data`: 18.1과 같은 세션 상세. **진행 중 세션이 없으면 `data:null`**(404 아님, [초안]). 조회 시점에 스탯을 지연 계산해 저장·반환합니다. 계산 결과 `health=0`이면 FAILED, `endsDttm` 경과면 COMPLETED로 그 자리에서 종료 처리하며, 이 호출에 한해 **방금 종료된 세션 상세를 `status` 변경 상태로 한 번 반환**합니다(앱이 리포트 화면으로 이동할 수 있도록, [초안]). 다음 호출부터는 `data:null`.
- `18.3` 요청 `?status=COMPLETED&page=0&size=20` → `data.list[]` `{ sessionSeq, animal:{animalSeq, representativeImageUrl}, speciesType, personality, status, expectedCost, balance, startedDttm, endsDttm, endedDttm, hasReport }`. 기본 최신순.
- `18.4` 본인 또는 보호소. 응답은 18.1 상세. 보호소에는 `pendingEvent`·`activeWalkSeq`를 포함하되 읽기 전용.
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`, 403 `SIMULATION_ACCESS_DENIED`.

### 18.5 POST /simulations/{sessionSeq}/abandon

**요청 [초안]** `{}` → **성공 200 `data`** `{ "sessionSeq":3001, "status":"ABANDONED", "endedDttm":"...", "report": { ...18.18 리포트 DTO... } }`.

- **[확정 정책]** IN_PROGRESS → ABANDONED. 진행 중 산책은 ENDED, PENDING 이벤트는 EXPIRED 처리. 종료 트랜잭션: 스탯 최종 계산 → 상태·`ended_dttm` 갱신 → `simulation_reports` INSERT → `action_logs(SYSTEM, SESSION_END)` → 커밋 후 `SESSION_END` 알림(저장 + FCM).
- **오류 [초안]** 409 `SIMULATION_NOT_IN_PROGRESS`.

### 18.6 POST /simulations/{sessionSeq}/actions

**요청 [초안]** `{ "actionType": "FEED", "itemSeq": 11 }`.

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

- **[확정 정책] `actionType`과 필요 기구·소모품(M9):**

| actionType | 필요 기구(영구, 보유 여부) | 소모품(수량 -1) | `itemSeq` |
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
- **오류 [초안]** 409 `REQUIRED_EQUIPMENT_MISSING`(기구 미보유), `ITEM_NOT_IN_INVENTORY`(소모품 0개·미보유), `SIMULATION_NOT_IN_PROGRESS`; 400 `SHOP_ITEM_NOT_FOR_SPECIES`, `INVALID_PARAMETER`(actionType과 물품 분류 불일치); 404 `SHOP_ITEM_NOT_FOUND`.

### 18.7 GET /simulations/{sessionSeq}/action-logs

**요청 [초안]** `?actionType=CARE&page=0&size=50` → `data.list[]` `{ actionLogSeq, actionType, actionCode, item:{itemSeq,itemName}|null, walkSeq, occurrenceSeq, statDelta, statsAfter, regDttm }`. 최신순. 본인/보호소.

### 18.8 GET /simulations/shop/items

**요청 [초안]** `?speciesType=DOG` (생략 시 전체). `species_scope`가 요청 종 또는 `ALL`이고 `is_active=true`인 물품만, `display_order` 순.

**성공 200 `data` [초안]** (비페이지 전량)
```json
[
  { "itemSeq": 1, "itemName": "스테인리스 밥그릇", "category": "FOOD_BOWL", "isConsumable": false, "price": 12000, "description": "한 번 사면 계속 쓰는 기구입니다.", "image": { "imageSeq": 2001, "imageUrl": "https://..." }, "speciesScope": "ALL", "effects": null },
  { "itemSeq": 11, "itemName": "건식 사료 1kg", "category": "FOOD", "isConsumable": true, "price": 8000, "description": "한 끼 분량씩 소모됩니다.", "image": null, "speciesScope": "DOG", "effects": { "hunger": 30, "friendship": 1 } }
]
```
- **[확정 정책]** `category` 9종 `FOOD_BOWL, WATER_BOWL, LITTER_BOX, FOOD, SNACK, TOY, LITTER, SHAMPOO, MEDICINE`. 기구(`FOOD_BOWL/WATER_BOWL/LITTER_BOX/TOY`)는 `isConsumable=false`. 물은 무료이므로 물 자체는 물품이 아니고 물그릇만 판매합니다. 물품 사진은 초기 데이터 이미지(`images.reg_usr_seq` NULL).

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
- **[확정 정책]** 구매 시 `amount = price × quantity`를 잔액에서 차감하며 **잔액이 음수가 되어도 허용**합니다(M5). 기구는 `quantity=1`만 허용하고 이미 보유 중이면 409 `EQUIPMENT_ALREADY_OWNED`. `species_scope`가 세션 종과 맞지 않으면 400. 트랜잭션: 세션 잠금 → `simulation_transactions(PURCHASE)` INSERT → `balance` 갱신 → `simulation_inventory_items` UPSERT(`UNIQUE(session_seq,item_seq)`).
- **18.10 성공 200 `data`** `{ "balance":110000, "expectedCost":150000, "equipment":{SimulationEquipment}, "items":[ { "itemSeq":11, "itemName":"건식 사료 1kg", "category":"FOOD", "isConsumable":true, "quantity":5, "image":{...} } ] }`. 수량 0인 소모품 행도 포함(앱이 "다 썼음" 표시 가능, [초안]).
- **18.11 요청** `?page=0&size=50` → `data.list[]` `{ transactionSeq, transactionType:"PURCHASE"|"EVENT", item:{itemSeq,itemName,category}|null, occurrenceSeq|null, quantity, amount, balanceAfter, description, regDttm }`. 본인/보호소.
- **오류 [초안]** 404 `SHOP_ITEM_NOT_FOUND`; 400 `SHOP_ITEM_NOT_FOR_SPECIES`, `INVALID_PARAMETER`(quantity<1, 비활성 물품); 409 `EQUIPMENT_ALREADY_OWNED`, `SIMULATION_NOT_IN_PROGRESS`.

### 18.12 GET /simulations/{sessionSeq}/events · 18.13 POST /.../events/{occurrenceSeq}/answer

**18.12 요청 [초안]** `?status=PENDING&page=0&size=20` (생략 시 전체, 최신순).

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
- 만료 처리: `expires_dttm` 경과를 조회·스케줄러에서 감지하면 EXPIRED + `action_logs(EVENT, EVENT_EXPIRE)`, 효과·비용 없음. 이미 EXPIRED/ANSWERED면 409.
- 메인 화면의 **동물병원 오브젝트**는 PENDING인 CONDITION 이벤트가 있을 때 활성화되어 이 선택 화면으로 이동합니다(M15).
- **오류 [초안]** 404 `SIMULATION_EVENT_NOT_FOUND`; 409 `EVENT_NOT_PENDING`(ANSWERED·EXPIRED·만료 경과), `SIMULATION_NOT_IN_PROGRESS`; 400 `INVALID_CHOICE`.

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
- **오류 [초안]** 409 `WALK_ALREADY_IN_PROGRESS`(18.14), `WALK_NOT_IN_PROGRESS`(18.15~18.17), `ITEM_NOT_IN_INVENTORY`, `SIMULATION_NOT_IN_PROGRESS`; 404 `SIMULATION_WALK_NOT_FOUND`.

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
- **[확정 정책]** 세션 종료(COMPLETED/ABANDONED/FAILED) 트랜잭션에서 `simulation_reports`를 **1회 생성**해 영구 보존합니다(`isFinal:true`). 세션이 진행 중이면 같은 형식의 **중간 리포트를 조회 시 계산만** 하고 저장하지 않습니다(`isFinal:false`, `endedDttm:null`). 리포트 수정·삭제 API 없음. `spentByCategory`는 PURCHASE는 물품 분류, 이벤트 비용은 `EVENT` 키로 집계. `dailySpent` 키는 세션 1~7일차.
- FAILED면 `summary`에 "돌봄 부족으로 중단" 취지를 담고 죽음 표현을 쓰지 않습니다(M6).
- `18.19` 요청 `?page=0&size=20` → `data.list[]` `{ sessionSeq, animal:{...}, status, startedDttm, endedDttm, expectedCost, totalSpent, difference }`. USER 본인 전체.
- `18.20` 요청 `?animalSeq=700&page=0&size=20` → 같은 목록 DTO + `user:{ActorSummary}`. **자기 보호동물**(`shelter_animals.shelter_seq` = 소속 보호소)로 진행된 **종료된** 세션만. 진행 중 세션은 18.4로 상세만 열람.
- `18.21` = 12.15. 담당 SHELTER가 신청서의 `userSeq`·`animalSeq`로 **파생 조회**한 리포트 목록(FK 없음, M14). 신청자가 탈퇴했어도 보존된 리포트는 반환.
- **오류 [초안]** 404 `SIMULATION_NOT_FOUND`, `APPLICATION_NOT_FOUND`; 403 `SIMULATION_ACCESS_DENIED`, `APPLICATION_ACCESS_DENIED`.

### 18.22 GET /simulations/notifications · 18.23 PATCH /.../{notificationSeq}/read

**18.22 요청 [초안]** `?unreadOnly=true&page=0&size=20`.

**목록 200 `data.list[]` [초안]**
```json
{
  "notificationSeq": 120001,
  "sessionSeq": 3001,
  "notificationType": "STAT_WARNING",
  "message": "아이의 수분이 20 아래로 떨어졌어요. 물을 챙겨 주세요.",
  "occurrenceSeq": null,
  "isRead": false,
  "readDttm": null,
  "regDttm": "2026-11-02T03:10:00+09:00"
}
```
- **[확정 정책]** `simulation_notifications`만 조회하며 17장 목록에는 섞이지 않습니다. 유형 `NEGLECT/STAT_WARNING/EVENT/SESSION_END`. `EVENT`는 `occurrenceSeq`로 18.13 화면에 연결. 발송 규칙은 18.0 가안 표(방치 8시간, 위험 임계 20·스탯당 하루 1회, HOME 이벤트 발생, 세션 종료). 저장 후 커밋 → 같은 `message`를 활성 FCM 토큰으로 발송, 실패 시 재시도 없음(17장과 동일).
- `18.23` 성공 200 `data:{notificationSeq,isRead:true,readDttm}`. 전체 읽음·삭제 API는 두지 않습니다(세션 종료 후에도 이력 보존, [초안]).
- **오류 [초안]** 404 `SIMULATION_NOTIFICATION_NOT_FOUND`.

---

## 19. 도메인 에러 코드 후보 총괄 **[초안]**

> 아래 이름은 BE 구현 및 Android 분기용 **제안 코드**입니다. **DB v9에는 확정 API 오류 코드 목록이 없습니다.** 명명 규칙(의미 문자열 대문자 스네이크 유지 vs 접두 숫자 코드)과 공통 5종의 최종 이름은 **[미정] Q418**입니다. 실제 HTTP 매핑/표준 메시지/로그 필드는 팀 규약 확정 후 반영합니다.

| 도메인 | 에러 code | HTTP 후보 | 주된 발생 조건 |
|---|---|---|---|
| 공통 [미정 Q418] | `INVALID_REQUEST`(=현행 `INVALID_PARAMETER`), `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `INTERNAL_ERROR` | 400/401/403/404/500 | 도메인 코드가 없는 공통 실패. 1.3 참조 |
| 인증 | `INVALID_KAKAO_TOKEN`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `INVALID_REGISTRATION_CONTEXT` | 401 | 인증 실패(refresh 재사용 감지 포함) |
| 회원 | `DUPLICATE_NICKNAME`, `DUPLICATE_PHONE` | 409 | 활성 USER 유니크 충돌 |
| 회원 | `REQUIRED_USER_PROFILE_MISSING` | 400 | 최종 USER 필수 개인정보 불충족 |
| 이미지 | `IMAGE_NOT_FOUND`, `INVALID_IMAGE_COUNT`, `FILE_TOO_LARGE` | 404/400/413 | 이미지 업로드·연결 오류 |
| 이미지 | `IMAGE_NOT_OWNED` | 403 | 타인이 업로드한 `imageSeq` 연결 시도(Q407) |
| 분류 | `ANIMAL_COMBINATION_NOT_FOUND` | 404 | 등록되지 않은 품종·색상 조합 |
| 반려동물 | `PET_NOT_FOUND`, `PET_NOT_OWNED` | 404/403 | 대상·소유권 오류 |
| 실종 | `MISSING_REPORT_NOT_FOUND` | 404 | 실종 신고 접근 실패 |
| 실종 | `ACTIVE_MISSING_REPORT_ALREADY_EXISTS` | 409 | 회원당 ACTIVE 1건 제약 위반 |
| 발견 | `FOUND_REPORT_NOT_FOUND`, `SHELTER_ASSIGNMENT_UNAVAILABLE` | 404/409 | 발견 대상 또는 자동 배정 불가 |
| 구조 | `ACTIVE_RESCUE_ALREADY_EXISTS` | 409 | 동일 발견 PROCESSING/DONE 1건 제약 |
| 구조 | `RESCUE_NOT_FOUND`, `INVALID_RESCUE_TRANSITION` | 404/409 | 구조 조회·전이 오류 |
| 구조 | `RESCUE_COMPLETION_FIELDS_REQUIRED`, `RESCUE_RESULT_REASON_REQUIRED` | 400 | DONE 날짜·주소 / 실패·취소 사유 누락 |
| 구조 | `RESCUE_HAS_ANIMALS` | 409 | 참조 보호동물 있는 구조 삭제 시도 |
| 보호동물 | `SHELTER_ANIMAL_NOT_FOUND`, `RESCUE_NOT_DONE` | 404/409 | 조회 대상 없음/미완료 구조 참조 |
| 보호동물 | `INVALID_ANIMAL_STATUS_COMBINATION` | 409 | 보호·입양 상태 조합 CHECK 위반 |
| 보호동물 | `ANIMAL_ALREADY_RETURNED`, `ANIMAL_HAS_ACTIVE_APPLICATIONS` | 409 | 중복 반환·삭제 제한 |
| 입양 | `ACTIVE_APPLICATION_ALREADY_EXISTS`, `ANIMAL_NOT_AVAILABLE` | 409 | 활성 신청 중복/새 신청 불가 |
| 입양 | `APPLICATION_NOT_FOUND`, `APPLICATION_ACCESS_DENIED` | 404/403 | 신청서 조회·권한 |
| 입양 | `APPLICATION_NOT_DRAFT`, `APPLICATION_NOT_SUBMITTED`, `APPLICATION_NOT_APPROVED` | 409 | 상태 전이 원천 부적합 |
| 입양 | `REQUIRED_ADOPTION_ANSWER_MISSING`, `INVALID_ADOPTION_ANSWER` | 400 | 조건부 필수·보기 응답 오류 |
| 입양 | `REJECTION_REASON_REQUIRED`, `ANIMAL_ALREADY_FINISHED` | 400/409 | 사유 누락·종료 동물 완료 경합 |
| 댓글 | `EMPTY_COMMENT`, `COMMENT_DEPTH_EXCEEDED`, `PARENT_COMMENT_UNAVAILABLE` | 400/400/409 | 댓글 비어 있음·답글 제한 |
| 채팅 | `UNSUPPORTED_CHAT_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED` | 409 | 채팅 조합/탈퇴 상대 |
| 채팅 | `INVALID_CHAT_PAYLOAD`, `CHAT_ROOM_NOT_PARTICIPANT` | 400/403 | 메시지 종류·참여권한 오류 |
| 신고 | `REPORT_TARGET_NOT_ALLOWED`, `REPORT_ALREADY_EXISTS` | 400/409 | 허용 범위·중복 |
| 북마크 | `BOOKMARK_ALREADY_EXISTS` | 409 | 중복 북마크 등록 |
| 알림 | `NOTIFICATION_NOT_FOUND`, `DEVICE_TOKEN_CONFLICT` | 404/409 | 알림 소유권/활성 토큰 유일성 |
| 모의 입양 | `SIMULATION_NOT_FOUND`, `SIMULATION_ACCESS_DENIED` | 404/403 | 세션 없음/본인·보호소 아님 |
| 모의 입양 | `ACTIVE_SIMULATION_ALREADY_EXISTS`, `SIMULATION_NOT_IN_PROGRESS` | 409 | USER당 진행 중 1개 / 종료된 세션 변경 시도 |
| 모의 입양 | `SIMULATION_SPECIES_NOT_SUPPORTED`, `SIMULATION_ANIMAL_NOT_AVAILABLE` | 400/409 | 종 ETC / 보호 중 아닌 동물 |
| 모의 입양 | `SHOP_ITEM_NOT_FOUND`, `SHOP_ITEM_NOT_FOR_SPECIES`, `EQUIPMENT_ALREADY_OWNED` | 404/400/409 | 펫샵 물품 조회·종 범위·기구 중복 구매 |
| 모의 입양 | `REQUIRED_EQUIPMENT_MISSING`, `ITEM_NOT_IN_INVENTORY` | 409 | 기구 미보유 / 소모품 없음 |
| 모의 입양 | `SIMULATION_EVENT_NOT_FOUND`, `EVENT_NOT_PENDING`, `INVALID_CHOICE` | 404/409/400 | 이벤트 응답 오류 |
| 모의 입양 | `SIMULATION_WALK_NOT_FOUND`, `WALK_ALREADY_IN_PROGRESS`, `WALK_NOT_IN_PROGRESS` | 404/409/409 | 산책 상태 오류 |
| 모의 입양 | `SIMULATION_NOTIFICATION_NOT_FOUND` | 404 | 모의 입양 알림 소유권 |

---

## 20. 회의 확인 필요 (API 수준 미결 안건)

> **DB 재설계 질문이 아니라 API 계약을 위해 실제로 결정해야 하는 항목**만 정리합니다. DB v9에서 의도적으로 스킵한 차단/이관/AI 벡터 설계/보호소 삭제 엣지케이스는 여기서 재논의 대상으로 되살리지 않습니다. 번호는 DB 문서 추적표와 공유합니다(Q406 이후).

### 20.1 v1 이후 종결된 안건

| 안건 | 결정 | 반영 |
|---|---|---|
| 인증 토큰 운영 | **Q409 B** refresh는 Redis TTL, DB 테이블 없음. 수치(1시간/14일)는 [가안] | 2.3·2.4 |
| 프로필 이미지 방식 | **Q408 A** `users.profile_image_seq` FK, 3.3·3.4 삭제 | 3.2 |
| 카카오 프로필 사진 | **Q408-1 A** 미수집, Kakao Developers 테스트 앱 | 2.1·2.5 |
| 이미지 업로더·소유권 | **Q407 A** `images.reg_usr_seq`, 403 `IMAGE_NOT_OWNED` | 4장 |
| ADMIN 전용 API 범위 | **Q410·Q411** 구현 범위 외. role CHECK는 예약값 유지 | 18장 삭제, 전 장 권한 열 |
| ADMIN의 구조·보호동물 보호소 지정 | **Q410** 10.3·11.4 SHELTER 전용, `shelterSeq` 필드 없음 | 10.3·11.4 |
| 모의 입양 범위 | **M1~M17** 범위 안, DB 11개 테이블 | 18장 |

### 20.2 미결 안건

| 우선순위 | 번호 | 안건 | 지금 사용한 가안 | 선택지 / 확인 이유 |
|---|---|---|---|---|
| **P0** | Q413 | KAKAO 로그인 흐름 | 2.1 access token 검증 + 2.5 최종 가입 2단계 | A) 현행 2단계 유지 · B) 인가코드 서버 교환 |
| **P0** | Q414 | registrationToken 보관 | 서버 발급 단기 컨텍스트 | A) Redis `auth:register:{token}` TTL 10분, 사용 즉시 삭제 · B) 서명 토큰 10분 만료, 재사용 방지 없음 |
| **P0** | Q415 | API 공통 응답 규약(204·멱등) | 200/201 + envelope, `data:null`, 중복 409 | A) 204 미사용·중복 409 · B) 삭제·무응답 204 · C) 중복 멱등 200 |
| **P0** | — | SHELTER가 실종/발견 신고 생성·수정/종료 가능한 범위 | 일반 신고 생성은 USER만, 발견 종료는 담당 SHELTER 허용 | 역할별 기능 요구의 상세 정의 필요 |
| **P0** | — | 입양 DRAFT가 보호소에서 보이는 범위 | 제출 전 응답은 보호소 업무 목록에 비노출 우선 | 신청자 개인정보 보호/업무 UX |
| **P0** | — | 일반 게시물·댓글·채팅 권한 경계 | DB 소유권/소속 기준으로 제한 | 수정/종료/삭제 가능 상태 구체화(ADMIN 운영 범위는 종결) |
| **P1** | Q406 | 행정구역 코드 저장·도출 (**보류**) | 컬럼 없음, `districtCode` 필터 미구현, 배정은 최근접 | A) 세 테이블 `district_code` + 서버 역지오코딩(카카오 로컬) · B) 필터 삭제·최근접만·배정표 제거 · C) 컬럼 + 앱 전송. **"지도 관련 정리하자" 신호로 재개** |
| **P1** | Q412 | 초기 데이터 적재 경로 | API 없음 확정 | A) Flyway seed SQL · B) 앱 초기화 코드 · C) 수동 SQL. 적재 시점에 결정 |
| **P1** | S4 | 이미지 업로드 프로토콜·객체 저장소 | S3 presigned PUT + 메타데이터 등록 | AWS S3 / MinIO, presigned PUT / 서버 경유 multipart |
| **P1** | Q416 | 시간 응답 표기 | ISO-8601 `+09:00` | A) `+09:00` 고정 · B) UTC `Z` · C) 오프셋 없음 |
| **P1** | Q417 | 리스트 검색·정렬·페이징 | 일반 offset, 고정 최신순, 채팅은 messageSeq 커서 | A) page 0부터·size 기본 20 최대 100·고정 정렬 · B) `sort=필드,방향` 자유 정렬 |
| **P1** | S5 | 채팅 실시간 수신/전송 | REST 송신 + 수신 프로토콜 미정 | A) STOMP + RabbitMQ 브로커 · B) SimpleBroker · C) Redis pub/sub |
| **P1** | — | 사진 변경 DTO의 부분/전체 교체 의미 | `imageSeqs`는 전체 최종 배열 | 순서 중복/마지막 참조 정리 구현 |
| **P1** | — | 실종·발견 신고 CLOSED 상태의 본문 수정, 신고 재개 UX | 본문 수정 경로만 정의, 상태별 허용은 미정 | 서버 검증·앱 편집 화면 분기 |
| **P1** | — | 상태 전이·댓글/입양 세부 알림 이벤트 | DB 유형만 확정, 수신 이벤트 미정 | 불필요한 푸시·중복 발송 방지. SYSTEM 발송 주체도 포함 |
| **P1** | — | 이미지/수료증 저장소 객체 정리와 실패 복구 | 마지막 참조 확인 후 삭제 | DB 트랜잭션과 저장소 삭제 비원자성 |
| **P1** | S1~S3, S6~S8 | 백엔드 세부 스택 | Kotlin + Spring Boot, Redis만 확정 | S1 버전 · S2 ORM · S3 위치 검색(PostGIS 여부) · S6 RabbitMQ 용도 · S7 OpenSearch 용도(텍스트/임베딩 kNN) · S8 스케줄러(모의 입양·알림) |
| **P2** | Q419 | 로그아웃 시 FCM 토큰 | 2.4와 17.7 분리 | A) 2.4 요청에 `fcmToken` 선택 필드 · B) 앱이 17.7 별도 호출 |
| **P2** | Q418 | 구체 오류코드·명명 규칙 | 19장 후보(대문자 스네이크) | A) 유지 + 공통 5종 · B) 접두 숫자 코드 |
| **P2** | — | 신고 내역 조회 화면 | §15.2 임시 기재 | ADMIN 열람 주체가 없어진 뒤 유지·삭제 결정 |
| **P2** | — | 모의 입양 수치 튜닝 | 18.0 코드 설정 가안 | 스탯·배율·이벤트 빈도·방치 기준은 코드 설정, 플레이 테스트 후 조정 |

### 아직 스펙에 **없는 것** (의도된 제외)

- 회원 차단·차단 해제·신고 시 자동 차단, 관리자 신고 심사/신고 처리 완료 API, **ADMIN 전용 API 전체**(보호소 기준정보·행정구역 배정·입양 문항·SYSTEM 알림 관리 포함).
- 보호동물 보호소 간 이관, 보호소 삭제·정리, 사후관리.
- 모의 입양의 가상 동물(실제 보호동물 없이 시작), 동물병원·진단 전용 API, 상점·시나리오 관리 API, 리포트 수정·삭제.
- AI 이미지 임베딩 저장/벡터 차원·인덱스/모델 버전, AI 매칭 점수 보관, 실종↔발견/보호동물의 확정 동일 개체 FK.
- 입양 질문의 템플릿·버전/변경 이력, 별도 입양 프로세스/상태 이력/서류 심사 이력 API.
- 댓글 좋아요/채팅 메시지 개별 삭제, 채팅 푸시 DB 이력, 발견 신고 시 reporterName 별도 입력.

---

## 21. 핵심 서버 트랜잭션 흐름 (개발 체크리스트)

### 21.1 신고 등록 → AI 매칭 → 알림

1. 실종 신고 POST 요청의 `userSeq`는 로그인에서 결정. 유효 조합·사진 1~3장·필수 속성 검증.
2. 회원의 다른 비삭제 ACTIVE 신고 없음 확인 → DB UNIQUE `WHERE status='ACTIVE' AND is_deleted=false`도 적용 → 신고 및 연결 이미지 저장.
3. 비삭제·비탈퇴 유효 발견 신고/PROTECTING 보호동물과 AI 비교 요청. 매칭 구현 상세는 별도 POC.
4. 같은 매칭 쌍을 `missing_reports` 행 잠금 하에 확인하고 최초 1회만 `notifications`에 생성.
5. 저장된 `message`와 동일한 문자열을 커밋 후 FCM에 전달. 실패해도 DB 보존/재시도 없음.

### 21.2 발견 신고 → 구조 접수 → 완료 → 보호동물

1. 발견 신고 POST: 시군구 매핑/최근접 기준 서버 자동 배정, 발견 신고 ACTIVE.
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

### 21.4 채팅 나가기와 재입장

1. `roomType=USER`면 두 USER 정렬/단일방 제약, `roomType=SHELTER`면 USER+보호소 제약 확인.
2. 나갈 때 참여자의 `lastHiddenMessageSeq`를 해당 방 최신 메시지 기준으로 갱신(기존 메시지 삭제 없음).
3. 같은 상대와 재입장해도 방은 재사용하고, **그 참가자에게만** 과거 메시지 조회 제외.
4. `lastReadMessageSeq`와 `lastHiddenMessageSeq`는 반드시 그 방의 메시지를 참조. 미읽음은 쿼리 계산.

### 21.5 모의 입양 세션 시작 → 돌봄 → 종료 리포트

1. 세션 시작(18.1): 요청자의 `IN_PROGRESS` 세션을 `SELECT ... FOR UPDATE`로 확인(부분 UNIQUE 병행) → 동물 비삭제·보호 중·종 `DOG/CAT` 검증 → 품종에서 성격 스냅샷(NULL이면 랜덤) → 세션 INSERT(초기 스탯, `ends_dttm`, `balance=expected_cost`) → `action_logs(SYSTEM, SESSION_START)`.
2. 모든 조회·행동·스케줄러 진입 시 **스탯 지연 계산**: `stats_calculated_dttm`부터 경과 tick × (기본 변화 × 성격 배율)을 적용해 0~100으로 클램프하고 `stats_calculated_dttm` 갱신. 계산 결과 `health=0`이면 FAILED, `ends_dttm` 경과면 COMPLETED로 6단계 종료 처리.
3. 돌봄 행동(18.6): 세션 잠금 → 지연 계산 → 기구 보유·소모품 수량 검사 → 수량 -1 → 물품 효과 반영 → `action_logs(CARE)` → `last_action_dttm` 갱신. 돈은 쓰지 않음.
4. 구매(18.9): 세션 잠금 → `simulation_transactions(PURCHASE)` INSERT → `balance -= amount`(음수 허용) → `simulation_inventory_items` UPSERT. 기구는 수량 1·재구매 409.
5. 이벤트: HOME·RANDOM은 스케줄러가 하루 1~2회 추첨, HOME·CONDITION은 지연 계산 시점·스케줄러에서 조건 판정(쿨다운 적용), WALK는 하트비트(18.15)에서 추첨. PENDING 1개 부분 UNIQUE. 응답(18.13): `occurrence FOR UPDATE` → PENDING·미만료 확인 → 효과 반영 → `cost>0`이면 `transactions(EVENT)` + 잔액 차감 → `action_logs(EVENT_ANSWER)` → ANSWERED. 만료는 EXPIRED + `action_logs(EVENT_EXPIRE)`, 효과 없음.
6. 종료(COMPLETED/ABANDONED/FAILED 공통): 스탯 최종 계산 → 진행 중 산책 ENDED·PENDING 이벤트 EXPIRED → 상태·`ended_dttm` 갱신 → `simulation_reports` INSERT(지출 집계·최종 스탯·카운트·summary) → `action_logs(SYSTEM, SESSION_END)` → 커밋 → `simulation_notifications(SESSION_END)` 저장 + FCM. 리포트는 세션당 1건 UNIQUE, 영구 보존.
7. 알림 스케줄러(10분 가안): 진행 중 세션마다 2단계 계산 → 방치(`last_action_dttm` 8시간 경과) `NEGLECT`, 스탯 20 미만 `STAT_WARNING`(스탯당 하루 1회), 조건 이벤트 발생 `EVENT`를 `simulation_notifications`에 저장 후 커밋 → FCM. 일반 `notifications`에는 쓰지 않음. 하트비트 10분 끊긴 산책은 자동 종료.

---

## 22. DB v9 테이블 ↔ API 대응표

| DB 테이블 | 주요 API 절 | 주의 |
|---|---|---|
| `users`, `auth_providers`, `user_accounts` | 2·3 | USER 필수 개인정보/LOCAL 사전 발급(SHELTER만)/동시 소프트 삭제. `profile_image_seq` FK(Q408). refresh는 Redis(Q409) |
| `shelters`, `shelter_members`, `shelter_district_assignments` | 8·9 | 보호소당 계정 1개, districtCode 단일 PK. 기준정보·배정은 초기 데이터(Q412), 행정구역 코드 도출은 Q406 보류 |
| `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations` | 5·18 | 조합 FK 한 개로 저장. `species_code DOG/CAT/ETC`(M8), `breeds.personality`(M7) |
| `pets`, `pet_images` | 6 | 사진 1~3장, 실종 신고 스냅샷 별도 |
| `images` | 3·4·6~11·13·14·18 | `reg_usr_seq` 업로더(Q407), 마지막 참조 해제 후 원본 삭제(직접 FK 포함) |
| `missing_reports`, `missing_report_images` | 7·17 | 회원당 ACTIVE 최대 1건, AI 알림 기준 |
| `found_reports`, `found_report_images` | 8·10·17 | 담당 보호소 자동 배정, 수동 재배정 API 없음 |
| `rescue_records` | 10·11 | DONE 1:N 보호동물, 구조 정보 JOIN. SHELTER 전용 생성 |
| `shelter_animals`, `shelter_animal_images` | 11·12·18 | 최초 PROTECTING+UNAVAILABLE, 이름 없음. 모의 입양 시작 대상 |
| `animal_returns` | 11 | 반환 시점=regDttm, 소유권 검증 아님 |
| `adoption_form_questions` | 12 | 현재 21문항, 문항 이력/버전 없음, 관리 API 없음 |
| `adoption_applications` | 12 | JSONB questionSeq 키, 6상태, 별도 프로세스 없음. 12.15 리포트 파생 조회 |
| `chat_rooms`, `chat_participants`, `chat_messages` | 14 | 마지막 읽음/나가기 포인터, 직접 imageSeq |
| `missing_report_comments`, `found_report_comments`, `shelter_animal_comments` | 13 | 각각 imageSeq 직접 FK, 삭제 루트의 답글 유지 |
| `reports` | 15 | PENDING만, 신고 심사 없음, 열람 주체 없음 |
| `bookmarks` | 16 | 해제 물리 삭제 |
| `notifications`, `device_tokens` | 17·18 | 5종, AI 최초 1회/FCM 재시도 없음. 모의 입양 푸시도 같은 토큰 |
| `simulation_sessions` | 18.1~18.5 | USER당 IN_PROGRESS 1건 부분 UNIQUE, 종·성격 스냅샷, 스탯 7개 0~100, 잔액 음수 허용 |
| `simulation_shop_items` | 18.8 | 초기 데이터, 9분류, 기구 `is_consumable=false`, 관리 API 없음 |
| `simulation_inventory_items` | 18.6·18.9·18.10·18.16 | UNIQUE(session,item), 기구 수량 1, 소모품 0 유지 |
| `simulation_transactions` | 18.9·18.11·18.13 | PURCHASE/EVENT, `balance_after`, 리포트 집계 근거 |
| `simulation_action_logs` | 18.6·18.7·18.13~18.17 | CARE/WALK/EVENT/SYSTEM, `stats_after_jsonb`, 방치 판정 |
| `simulation_walks` | 18.14~18.17 | 세션당 IN_PROGRESS 1건, 하트비트 자동 종료 |
| `simulation_event_scenarios`, `simulation_event_choices` | 18.12·18.13 | 초기 데이터, HOME/WALK × RANDOM/CONDITION, 선택지 2개·`result_text` |
| `simulation_event_occurrences` | 18.12·18.13·18.15 | 세션당 PENDING 1건 부분 UNIQUE, 6시간 만료 |
| `simulation_reports` | 18.5·18.18~18.21·12.15 | 세션 1:1, 종료 시 1회 생성, 영구 보존, 보호소 열람만 |
| `simulation_notifications` | 18.22·18.23 | 4유형, 일반 알림과 분리, 같은 FCM 토큰 |

---

**초안 종료.** 이 문서는 DB v9의 현재 기능과 제약을 API 초안으로 변환한 것이며, 제안된 API 경로·DTO 및 미결 운영 정책(20장)은 사용자/팀의 확인 전까지 확정 사항으로 취급하지 않습니다. v1은 `animal_service_api_spec_v1.md`로 보존합니다.
