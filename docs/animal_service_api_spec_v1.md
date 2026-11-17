# 유실·발견동물·보호소·입양 플랫폼 API 명세서 — v1 (초안)

> **작성일:** 2026-10-29  
> **근거:** `animal_service_db_spec_v8(2).md`(DB 통합 명세 v8, 현행 33개 테이블, Q393~Q405 최신 결정)  
> **형식 참고:** `MOTIF_API_명세서_v0_11.md`(파일 내 문서 제목은 v0.13)  
> **문서 목적:** Android·BE·AI·보호소 화면이 공유할 1차 REST API 계약. **구현 완료 또는 팀 회의 확정 문서가 아닙니다.**

**표기 규칙**
- **[확정 정책]**: DB v8에서 확인되는 기능·권한·상태·제약. **[초안]**: API URL, HTTP 방식, DTO 이름, 오류 코드, 조회 정렬·페이징, 실시간 통신 방식 등 제안. **[미정]**: 별도 결정 필요. 확정 정책과 초안이 충돌하면 DB v8이 우선합니다.
- 표의 `/...` 경로는 모두 `/api/v1`을 앞에 붙인 상대 경로입니다. **[비인증]** 표시 외에는 로그인 필수입니다.
- 이 문서는 **클라이언트·보호소 업무 API**를 중심으로 하고 필요한 ADMIN API만 별도 장으로 묶습니다. 내부 AI 워커 및 공공데이터 ETL 전용 API는 임의로 확정하지 않습니다.
- **DB v8만으로 확정할 수 없는 내용을 실제 확정처럼 기재하지 않습니다.** 서버 스펙의 세부 구현은 이 문서의 [초안] 또는 [미정]으로 유지합니다.

## 0. 기준 및 적용 범위

### 0.1 MOTIF 명세서에서 참고한 작성 방식

| 참고한 요소 | 적용 방식 | 주의 |
|---|---|---|
| 도메인별 번호·API 인덱스 | 도메인별 엔드포인트 표 + 상세 항목 번호 | MOTIF의 도메인 경로를 그대로 복제하지 않음 |
| `result/code/message/data/timestamp` envelope | **[초안]** 같은 응답 껍데기 사용 | 동물 플랫폼 팀 공통 응답 규약으로 확정된 것은 아님 |
| 각 API의 요청·성공 `data`·실패 분기 | 대표 DTO, 상세 검증, 주요 에러 제시 | 모든 메시지·예시 ID는 가상 값 |
| page/size 및 별도 커서 규약 | 우선 일반 목록에 **[초안] offset** 제안 | 동적 채팅·알림 등 cursor 채택 여부 [미정] |
| HTTP 의미 및 오류 코드 분리 | 200/201/400/401/403/404/409 등 구분 | 204 vs envelope 통일 [미정] |

### 0.2 v8에서 특히 달라진 규칙 (API 강제)

1. **USER 한 명당 비삭제 `ACTIVE` 실종 신고는 최대 1건**입니다. 두 신고 모두 `CLOSED → ACTIVE` 재개가 가능합니다.
2. 발견 신고는 등록 시 담당 보호소를 자동 배정합니다(시군구 매핑 → 없으면 거리상 최근접). 등록·수정에 `shelterSeq`를 사용자가 지정하지 않습니다. **ADMIN 재배정만** 수동 허용합니다.
3. 구조 기록은 `PROCESSING/DONE/FAILED/CANCELED` 4종이며, **DONE 구조 1건에 보호동물 여러 마리**를 연결할 수 있습니다. 보호동물 구조 정보는 `rescueSeq`를 통해 조회하며 복사 저장하지 않습니다.
4. 보호동물은 **이름 컬럼이 없고** 첫 등록이 `PROTECTING+UNAVAILABLE`입니다. 반환 완료 시간은 `animal_returns.reg_dttm`을 사용합니다.
5. 입양 신청은 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` 6종이며, 동일 회원·동물의 비삭제 활성(DRAFT/SUBMITTED/APPROVED) 신청은 **통틀어 1건**입니다. `CLOSED` 상태는 없습니다.
6. 공통 입양 문항은 **현재 21개**, 답변은 `questionSeq`를 문자열 키로 하는 단일 JSON 객체입니다. 템플릿·버전·질문 이력·제출 당시 문항 스냅샷은 없습니다.
7. 댓글·채팅 이미지에는 각각 **`imageSeq` 단일 선택 필드(최대 1장)**를 사용합니다. 댓글의 부모 삭제 후 기존 답글은 유지합니다.
8. 사용자 차단·신고 심사·보호소 이관·모의 입양·입양 후 관리·AI 동일 동물 확정 링크 API는 **범위 밖**입니다.
9. 알림 저장 유형은 `COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM` **5종**, 채팅 푸시는 DB 알림 기록을 남기지 않습니다.
10. `TIMESTAMPTZ`를 유지하고 DB 세션 시간대는 `Asia/Seoul`입니다. API는 시간대 정보가 있는 ISO-8601 시각으로 직렬화합니다.

---

## 1. 공통 규약 (COMMON)

### 1.1 프로토콜·인증·명명

| 항목 | 명세 | 확정 수준 |
|---|---|---|
| Base URL | `https://{host}/api/v1` | [초안] 호스트·게이트웨이 미정 |
| 요청 본문 | UTF-8 JSON; `camelCase` | [초안] MOTIF 명세 형식 참고 |
| 식별자 | DB `*_seq` → API `*Seq` 숫자형. 클라이언트가 신규 DB PK 부여 금지 | [초안] 이름 / [확정 정책] 서버 PK 존재 |
| 인증 헤더 | `Authorization: Bearer {accessToken}` | [초안] JWT·토큰 수명·재발급 운영 미정 |
| 권한 | `USER` 일반회원 / `SHELTER` 소속 보호소 담당자 / `ADMIN` 전역 관리자 | [확정 정책] |
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

- [초안] 성공 시 `result/code=SUCCESS`, 실패 시 `result=FAIL`과 구체적인 에러 코드. 주요 HTTP: 조회/수정/삭제 200, 생성 201, 비동기 접수 202 후보. 본문 없는 성공도 `200 + data:null`을 기본으로 제안합니다. **204 No Content 채택 여부는 회의 안건**입니다.
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

**공통 필터:** 로그인 필수; `users.is_deleted=true` 작성자의 일반 게시물·AI 후보를 비노출합니다. 단 채팅 메시지, 기존 댓글·답글, 저장 알림, 보호소 업무상 과거 입양 기록 등은 보존하고 작성자를 `(탈퇴한 사용자)`로 표기합니다. 동물 및 신고의 `is_deleted` 등 비노출은 각 API에서도 확인합니다. 별도 관리자 업무 권한이 없는 404/403 구분은 공개하지 않을 정보의 존재를 감추는 쪽을 [초안]으로 제안합니다.

### 1.4 목록 응답 DTO **[초안]**

- `Page<T>`: `{list: T[], page, size, totalElements, totalPages, hasNext, hasPrevious}`.
- `ImageRef`: `{imageSeq: number, imageUrl: string|null, displayOrder: number}`. `imageUrl`은 서버가 반환한 접근 URL이며 DB의 `image_key`를 임의 조합하지 않습니다.
- `AnimalClassification`: `{combinationSeq, speciesSeq, speciesName, breedSeq, breedName, colorSeq, colorName}`. **저장은 `combinationSeq`만** 사용합니다. `speciesSeq/breedSeq/colorSeq`는 표시를 위한 JOIN 파생 필드입니다.
- `ActorSummary`: `{userSeq, displayName, role, profileImageUrl?}`. 회원 탈퇴 시 표시 이름 대체. SHELTER는 표시 이름으로 해당 보호소명 사용.
- `GeoPoint`: `{address, latitude, longitude}`. 위도 -90~90/경도 -180~180 검증은 [초안] 구현 규약.
- `[초안]` 목록의 검색 조건, `sort` 화이트리스트, cursor 기반 전환, 공유 URL 수명은 20장의 미결 안건 참조.

### 1.5 접근 권한 요약 (DB 정책과 직접 연동)

| 기능 | USER | SHELTER | ADMIN |
|---|---|---|---|
| 반려동물 등록/수정/삭제 | **본인만** | 불가 | 운영 전역 [초안] |
| 실종·발견 신고 작성 | USER | [미정: 해당 역할 작성 지원 여부] | 관리 목적 [초안] |
| 자신의 실종·발견 신고 수정/재개/종료 | 본인 | 발견 신고는 담당 업무 권한 범위 별도 검토 | 전역 |
| 보호소 정보 변경 | 불가 | 소속 보호소 운영정보만 | 식별정보 포함 전역 |
| 구조·보호동물 등록·관리 | 불가 | 소속 보호소 업무 | 전역 |
| 보호동물 반환/입양 심사·완료 | 불가 | 소속 보호소 | 전역 |
| 입양 신청·저장·제출·취소 | **본인만** | 불가 | 과거 기록 열람 [초안] |
| 댓글·채팅 | 허용 | 허용(대화 조합 제한) | 구체 권한 [미정] |
| 신고 | 신고자 역할의 최종 제한 [미정]; 보호소 계정 대상 신고 불가 | USER 대상 신고 가능 여부 확인 필요 | 심사 기능 제외 |
| 북마크/일반 알림 | 일반회원 중심 [초안] | 알림 수신/업무 알림 | 시스템/운영 |

---

## 2. 인증·회원 (AUTH)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 2.1 | POST | `/auth/kakao/verify` | [비인증] | 카카오 검증·기존 회원 로그인/최초 가입 컨텍스트 |
| 2.2 | POST | `/auth/local/login` | [비인증] | 사전 발급 SHELTER/ADMIN 로그인 |
| 2.3 | POST | `/auth/refresh` | [비인증] | access 갱신 [초안] |
| 2.4 | POST | `/auth/logout` | 로그인 | 세션/토큰 종료 [초안] |
| 2.5 | POST | `/auth/kakao/register` | [비인증: 가입 컨텍스트 필요] | 신규 USER 필수 개인정보 포함 최종 가입 |
| 2.6 | DELETE | `/users/me` | USER | 회원 탈퇴 |

### 2.1 POST /auth/kakao/verify

**[확정 정책]** USER는 KAKAO 계정만 사용합니다. 탈퇴 후 재가입하면 기존 회원 행을 복구하지 않고 새 `users/user_accounts` 행을 만듭니다. **[초안]** 카카오 access token을 받아 서버에서 검증하고, 기존 회원이면 로그인 완료, 미가입자면 **가입 완료용 임시 컨텍스트만 반환**합니다. 카카오 인가코드 서버 교환 방식도 후보입니다.

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

- **핵심 정합성:** DB v8에서 USER의 `nickname/name/birth_date/phone` 네 필드는 모두 필수 CHECK입니다. 따라서 **카카오 검증 직후 개인정보가 없는 USER 레코드를 미리 INSERT하지 않습니다.** 2.5에서 필수 정보를 받은 뒤 최종 사용자/인증 행을 같은 트랜잭션에서 생성합니다.
- `registrationToken`은 미가입 카카오 계정의 확인 결과를 넘기기 위한 **[초안] 일시적 가입 컨텍스트**입니다. 보관 방식/TTL/재검증/사용 후 폐기는 **[미정]**. 일반 access token 대신 사용하며 서비스 일반 API 접근 권한이 없습니다.
- **주요 실패 [초안]:** 401 `INVALID_KAKAO_TOKEN`, 403 `ACCOUNT_UNAVAILABLE`, 409 `KAKAO_ACCOUNT_CONFLICT`.

### 2.2 POST /auth/local/login

**요청 [초안]** `{ "loginId": "shelter01", "password": "..." }`.

**성공 200 `data` [초안]** `{ "accessToken":"...", "refreshToken":"...", "role":"SHELTER", "userSeq":55, "shelterSeq":7 }` (ADMIN은 `shelterSeq:null`).

- **[확정 정책]** LOCAL은 SHELTER/ADMIN 전용, 계정 사전 발급. `password_hash`만 보관하며 비밀번호 재설정 API는 구현 제외.
- **[초안]** 존재하지 않는 ID/틀린 비밀번호는 동일 401 `INVALID_CREDENTIALS`.

### 2.3 POST /auth/refresh · 2.4 POST /auth/logout

- `2.3` **요청 [초안]** `{ "refreshToken":"..." }` → 성공 200 `data:{accessToken,refreshToken,expiresIn}`. **refresh token 저장 테이블·rotation·만료·폐기 정책이 DB v8에 없습니다.** MOTIF의 토큰 운영 규칙을 동물 플랫폼 확정 규칙으로 간주하지 않습니다.
- `2.4` **요청 [초안]** `{ "refreshToken":"..." }` → 성공 200 `data:null`. 로그아웃 시 해당 기기의 활성 FCM 토큰 해제 정책도 [미정]입니다(탈퇴 시 전량 비활성화만 확정).
- **실패 [초안]** 401 `INVALID_REFRESH_TOKEN`(2.3), 공통 오류.

### 2.5 POST /auth/kakao/register

**요청 [초안]**
```json
{
  "registrationToken": "short-lived-registration-context",
  "nickname": "반려가족",
  "name": "김가람",
  "birthDate": "1997-05-12",
  "phone": "01012345678",
  "profileImageKey": null
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

- **[확정 정책]** `users`의 필수 개인정보 4종이 모두 충족될 때만 최종 USER+KAKAO `user_accounts` 생성. 활성 닉네임은 대소문자 무관, 전화번호는 활성 USER 간 중복 금지. **SMS 인증 없음.** 프로필 사진은 `users.profile_image_key`(공통 `images.imageSeq` 아님).
- **[초안]** 2.1의 가입 컨텍스트 유효성 및 KAKAO 동일 계정 동시 가입 방지 후 하나의 트랜잭션에서 최종 계정 생성. 실패 시 미완성 USER DB 행을 남기지 않습니다.
- **실패 [초안]** 401 `INVALID_REGISTRATION_CONTEXT`; 409 `DUPLICATE_NICKNAME`, `DUPLICATE_PHONE`, `KAKAO_ACCOUNT_CONFLICT`; 400 `REQUIRED_USER_PROFILE_MISSING`.

### 2.6 DELETE /users/me

**요청 [초안]** 본문 없음, 성공 200 `data:null`.

- **[확정 정책]** `users`+`user_accounts` 소프트 삭제, 활성 device_tokens 비활성, 본인의 DRAFT/SUBMITTED/APPROVED 입양 신청을 CANCELED, 실제 ADOPTED 기록 보존. 일반 게시물은 소유자가 탈퇴했으므로 가상 비노출, 댓글·채팅·저장 알림 및 보호소 과거 신청서 보존.
- **[초안]** 탈퇴 전 확인 절차·비밀번호/카카오 재검증·비동기 푸시 세션 처리 미정.

---

## 3. 사용자 프로필 (USER)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 3.1 | GET | `/users/me` | 로그인 | 내 프로필·소속 보호소 |
| 3.2 | PATCH | `/users/me` | USER | 닉네임/이름/생년월일/전화 수정 |
| 3.3 | PATCH | `/users/me/profile-image` | USER | 프로필 사진 저장소 키 수정 |
| 3.4 | DELETE | `/users/me/profile-image` | USER | 프로필 사진 해제 |
| 3.5 | GET | `/users/{userSeq}/profile` | 로그인 | 다른 회원의 공개 프로필 |

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
  "profileImageUrl": null,
  "shelterSeq": null,
  "accountStatus": "ACTIVE"
}
```
- **[확정 정책]** SHELTER/ADMIN은 이름/생년월일/전화/닉네임 NULL이 가능. SHELTER인 경우 `shelterSeq`를 소속 관계로 조회.

### 3.2 PATCH /users/me · 3.3~3.4 프로필 사진

- `3.2` **요청 [초안]** `{ "nickname":"새닉네임", "name":"김가람", "birthDate":"1997-05-12", "phone":"01012345678" }` (부분 갱신). **응답** 수정된 프로필. **오류** 409 `DUPLICATE_NICKNAME`/`DUPLICATE_PHONE`. USER의 필수 4종을 null로 바꾸는 요청은 400.
- `3.3` **요청 [초안]** `{ "profileImageKey":"profiles/uuid.jpg" }` → 성공 200 `data:{profileImageUrl}`. 공통 images `imageSeq`를 요구하지 않습니다.
- `3.4` 성공 200 `data:null`, 기존 객체 삭제·재사용 정책은 [미정].

### 3.5 GET /users/{userSeq}/profile

**성공 200 `data` [초안]** `{ "userSeq":18, "displayName":"발견도우미", "role":"USER", "profileImageUrl":null }`. 비공개 실명/생년월일/전화는 반환하지 않습니다. 탈퇴 계정은 404 `USER_NOT_FOUND` [초안].

---

## 4. 이미지·파일 업로드 (MEDIA)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 4.1 | POST | `/images/upload-urls` | 로그인 | JPG/PNG 업로드 주소 발급 [초안] |
| 4.2 | POST | `/images` | 로그인 | 업로드한 이미지의 공통 메타데이터 확정 [초안] |
| 4.3 | POST | `/files/upload-urls` | USER | 입양 수료증 이미지/PDF 업로드 주소 발급 [초안] |

### 4.1 POST /images/upload-urls · 4.2 POST /images

- **[확정 정책]** 게시물/반려동물 사진은 JPG/JPEG/PNG, 각 **최대 10MB**, 게시물별 **1~3장**, 댓글·채팅은 **최대 1장**. 공통 `images`는 `image_seq/image_key/mime_type/original_file_name/byte_size`를 관리합니다. 동일 이미지를 다수 도메인에서 재참조할 수 있습니다.
- **[초안]** S3 presigned PUT을 예시로 사용합니다. **MOTIF의 S3 presigned URL 정책을 현재 프로젝트의 아키텍처 확정으로 간주하지 않습니다.** 직접 multipart 업로드 방식도 구현 후보입니다.

**4.1 요청 [초안]** `{ "fileName":"dog.jpg", "contentType":"image/jpeg", "byteSize":1234567 }`.

**4.1 성공 200 `data` [초안]** `{ "uploadUrl":"https://storage.example.invalid/...", "imageKey":"images/generated-key.jpg", "expiresInSeconds":600 }`.

**4.2 요청 [초안]** `{ "imageKey":"images/generated-key.jpg", "mimeType":"image/jpeg", "originalFileName":"dog.jpg", "byteSize":1234567 }`.

**4.2 성공 201 `data` [초안]** `{ "imageSeq":1001, "imageUrl":"https://cdn.example.invalid/images/generated-key.jpg" }`.

- 파일 키는 **클라이언트가 임의 지정하는 것이 아니라 서버가 발급한 업로드 컨텍스트에 한정**하는 것을 제안합니다. 저장소에 업로드된 객체의 실제 크기/MIME/소유권 서버 검증 필요.
- 이미지 수정 API는 각 도메인의 `imageSeqs` 전체 순서 배열을 교체하는 [초안] 방식으로 설계합니다. 공통 이미지는 **모든 참조가 해제된 경우에만 삭제**하며, 비즈니스 클라이언트에 임의의 `DELETE /images/{imageSeq}`는 제공하지 않습니다.
- **오류 [초안]** 400 `INVALID_IMAGE_COUNT`, 413 `FILE_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, 404 `IMAGE_NOT_FOUND`.

### 4.3 POST /files/upload-urls

**요청 [초안]** `{ "purpose":"ADOPTION_CERTIFICATE", "fileName":"certificate.pdf", "contentType":"application/pdf", "byteSize":823456 }`.

**성공 200 `data` [초안]** `{ "uploadUrl":"https://storage.example.invalid/...", "storageKey":"certificates/generated.pdf" }`.

- **[확정 정책]** 교육 수료증은 신청서당 선택 1건, JPG/PNG/PDF 최대 10MB. 저장 위치는 `adoption_applications.certificate_storage_key`이고 공통 `images.imageSeq` FK가 아닙니다.
- 프로필 이미지(`profileImageKey`)·수료증(`certificateStorageKey`)·공통 사진(`imageSeq`)의 참조 방식은 서로 다르므로 섞지 않습니다.

---

## 5. 동물 기준정보 (ANIMAL CATALOG)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 5.1 | GET | `/animal-species` | 로그인 | 종 전체 목록 |
| 5.2 | GET | `/animal-species/{speciesSeq}/breeds` | 로그인 | 종의 품종 목록 |
| 5.3 | GET | `/animal-colors` | 로그인 | 색상 기준 목록 |
| 5.4 | GET | `/animal-combinations` | 로그인 | 유효 품종·색상 조합 조회 |

### 5.1~5.4 조회 규약

- `5.1` 성공 `data:[{"speciesSeq":1,"speciesName":"개"}, ...]`.
- `5.2` 성공 `data:[{"breedSeq":21,"speciesSeq":1,"breedName":"말티즈"}, ...]`.
- `5.3` 성공 `data:[{"colorSeq":3,"colorName":"흰색"}, ...]`.
- `5.4` 요청 `?breedSeq=21&colorSeq=3`(각 필터 선택) → `data:[{"combinationSeq":88,"speciesSeq":1,"breedSeq":21,"colorSeq":3, ...}]`.
- **[확정 정책]** 네 동물 엔터티는 모두 `combinationSeq` 단일 FK를 저장합니다. 종은 품종에서 결정되고 `UNIQUE(breed_seq,color_seq)`이며 실제 마스터 시드/기타 옵션 범위는 [미정]입니다.
- **[초안]** 기준정보는 비페이지 전량(수량 증가 시 서버 페이징 재검토). ADMIN의 기준정보 수정/시드 관리 API는 18장에 [초안]으로 둡니다.

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
| 7.5 | PATCH | `/missing-reports/{missingReportSeq}` | 작성자/ADMIN | 신고 내용 수정 |
| 7.6 | PATCH | `/missing-reports/{missingReportSeq}/status` | 작성자/ADMIN | ACTIVE↔CLOSED 변경 |
| 7.7 | DELETE | `/missing-reports/{missingReportSeq}` | 작성자/ADMIN | 실종 신고 소프트 삭제 |

### 7.1 GET /missing-reports · 7.2 GET /missing-reports/{id}

**검색 파라미터 [초안]** `?status=ACTIVE&speciesSeq=1&breedSeq=21&districtCode=...&page=0&size=20`; 위치 검색 반경/정렬 기준은 [미정]. 서버는 `combinationSeq→animal_breeds→animal_species`로 검색합니다.

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

- **[확정 정책]** 공개 연락처는 **로그인 사용자에게만** 보이며, 기본 비공개 시에는 **작성자와 ADMIN만** 실제 번호를 열람합니다. 접근 불가 시 `contactPhone:null`로 응답 [초안]. `isPhonePublic` 자체는 반환 가능.
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
| 8.5 | PATCH | `/found-reports/{foundReportSeq}` | 작성자/ADMIN | 발견 신고 내용 수정 |
| 8.6 | PATCH | `/found-reports/{foundReportSeq}/status` | 작성자/담당 SHELTER/ADMIN | ACTIVE↔CLOSED |
| 8.7 | DELETE | `/found-reports/{foundReportSeq}` | 작성자/ADMIN | 발견 신고 소프트 삭제 |
| 8.8 | GET | `/shelters/me/found-reports` | SHELTER | 내 보호소 담당 발견 신고 목록 |

### 8.1 GET /found-reports · 8.2 GET /found-reports/{id}

**검색 [초안]** `?status=ACTIVE&speciesSeq=1&districtCode=...&shelterSeq=7&page=0&size=20`.

**목록 200 `data.list[]` [초안]** `{ "foundReportSeq":210, "classification":{...}, "status":"ACTIVE", "shelterSeq":7, "address":"대구광역시 ...", "occurredDttm":"...", "representativeImageUrl":"..." }`.

**상세 200 `data` [초안]** 목록 필드 + `reporter:{ActorSummary}`, `shelter:{shelterSeq,shelterName}`, `age`, `isNeutered`, `microchipNumber`, `description`, `landmarkDescription`, `latitude`, `longitude`, `reporterPhone`, `isPhonePublic`, `closedReason`, `closedDttm`, `images`, `isBookmarked`, `commentCount`.

- **[확정 정책]** 비공개 `reporterPhone`은 작성자·ADMIN·**이 발견 신고의 담당 보호소**만 열람. 다른 사용자·다른 보호소에는 `null` 반환 [초안]. `reporterName` 별도 DB 컬럼 없음. 필요한 경우 `users.name`을 권한 범위 내 JOIN합니다.
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
- **담당 보호소 자동 배정은 서버 수행**: `district_code` 단일 매핑 우선, 미매핑 시 위경도 최근접. 자동 배정 실패 처리/행정구역 조회 방식은 [미정]. `shelterSeq` 임의 입력 불허.
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

**요청 [초안]** `?keyword=...&districtCode=...&page=0&size=20`.

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
- `9.4` **요청 [초안]** `{ "description":"...", "operatingHours":"...", "closedDays":"...", "profileImageSeq":1701, "backgroundImageSeq":1702 }` (부분 수정). **주소·이름·전화·좌표 등 식별정보는 SHELTER 수정 불가**, ADMIN만 수정.
- **오류 [초안]** 403 `SHELTER_NOT_OWNED`, 404 `SHELTER_NOT_FOUND`.

---

## 10. 구조 기록 (RESCUE)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 10.1 | GET | `/shelters/me/rescues` | SHELTER | 우리 보호소 구조 기록 목록 |
| 10.2 | GET | `/rescues/{rescueSeq}` | 담당 SHELTER/ADMIN | 구조 상세 |
| 10.3 | POST | `/rescues` | SHELTER/ADMIN | 발견 신고 연계/직접 구조 접수 |
| 10.4 | PATCH | `/rescues/{rescueSeq}` | 담당 SHELTER/ADMIN | 구조 진행 기록 수정 |
| 10.5 | PATCH | `/rescues/{rescueSeq}/status` | 담당 SHELTER/ADMIN | DONE/FAILED/CANCELED 처리 |
| 10.6 | DELETE | `/rescues/{rescueSeq}` | 담당 SHELTER/ADMIN | 구조 기록 논리 삭제(참조 있으면 거부) |

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

- **[확정 정책]** 발견 신고를 선택하여 접수하면 즉시 PROCESSING 생성, 신고 없이 자체 접수도 가능. 담당 보호소 기준을 서버가 설정합니다. 발견 신고 1건에 비삭제 `PROCESSING/DONE` 합계 최대 1건.
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
| 11.4 | POST | `/shelter-animals` | SHELTER/ADMIN | 직접/완료 구조 연계 보호동물 등록 |
| 11.5 | PATCH | `/shelter-animals/{animalSeq}` | 담당 SHELTER/ADMIN | 보호동물 기초 정보·사진 수정 |
| 11.6 | PATCH | `/shelter-animals/{animalSeq}/adoption-status` | 담당 SHELTER/ADMIN | AVAILABLE↔UNAVAILABLE 전환 |
| 11.7 | POST | `/shelter-animals/{animalSeq}/returns` | 담당 SHELTER/ADMIN | 실제 반환 완료 처리 |
| 11.8 | PATCH | `/shelter-animals/{animalSeq}/protection-status` | 담당 SHELTER/ADMIN | 반환/입양 외 종료(ENDED) 처리 등 |
| 11.9 | DELETE | `/shelter-animals/{animalSeq}` | 담당 SHELTER/ADMIN | 보호동물 논리 삭제(활성 신청 있으면 거부) |

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
  "isBookmarked": false
}
```
- **[확정 정책]** 보호동물에 `name` 없음. `rescueInfo`는 **완료 구조 기록을 LEFT JOIN해 파생**하며 `rescueSeq=null`이면 `rescueInfo`의 4개 필드도 모두 `null` [초안: 객체 자체 null 대신]. DB에 `rescued_date/rescue_reason/rescue_address/rescue_memo`를 보호동물 컬럼으로 추가하지 않습니다.
- **[확정 정책]** PROTECTING이면서 비삭제이면 AVAILABLE/UNAVAILABLE 관계없이 AI 매칭 후보.

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

- `rescueSeq` 선택. 값이 있으면 **DONE인 구조**만 참조 가능. 같은 구조에서 여러 마리를 등록할 수 있으며 `rescue_records.shelter_seq`와 새 보호동물의 보호소가 서로 달라도 DB FK에서 강제 금지하지 않습니다. 단 SHELTER의 타 보호소 구조 기록에 접근할 권한은 별개로 검토해야 합니다.
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
| 12.1 | GET | `/adoption/questions` | USER/담당 SHELTER/ADMIN | 현재 공통 21개 질문 조회 |
| 12.2 | GET | `/users/me/adoption-applications` | USER | 내 입양 신청 내역 |
| 12.3 | GET | `/adoption-applications/{applicationSeq}` | 신청자/담당 SHELTER/ADMIN | 신청서 상세·답변·심사 결과 |
| 12.4 | POST | `/adoption-applications` | USER | DRAFT 생성 |
| 12.5 | PATCH | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | 미제출 답변 저장·수정 |
| 12.6 | POST | `/adoption-applications/{applicationSeq}/submit` | 신청자(USER) | 문항 검증 후 제출 |
| 12.7 | DELETE | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | DRAFT 물리 삭제 |
| 12.8 | POST | `/adoption-applications/{applicationSeq}/cancel` | 신청자(USER), SUBMITTED만 | 제출 신청 취소 |
| 12.9 | GET | `/shelters/me/adoption-applications` | SHELTER | 소속 보호소 신청 목록/심사 대기 |
| 12.10 | POST | `/adoption-applications/{applicationSeq}/review` | 담당 SHELTER/ADMIN | 서류 APPROVED/REJECTED |
| 12.11 | POST | `/adoption-applications/{applicationSeq}/complete` | 담당 SHELTER/ADMIN | 승인 신청 기준 실제 입양 완료 |
| 12.12 | POST | `/shelter-animals/{animalSeq}/external-adoption` | 담당 SHELTER/ADMIN | 서비스 외부 경로 실제 입양 완료 |
| 12.13 | POST | `/adoption-applications/{applicationSeq}/fail` | 담당 SHELTER/ADMIN | 승인 후 진행 실패 → REJECTED |

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

- **예시의 실제 질문 문구/보기/ID는 공통 21문항 원본이 아니며 설명용 가상 값입니다.** DB v8에는 실제 질문 텍스트 21개가 수록되지 않았으므로 이 API의 실제 운영 데이터는 별도 시드 필요.
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
- 신청자 본인 외에는 소속 보호소 담당자/ADMIN의 업무 조회로만 상세 노출. 탈퇴 신청자의 과거 신청서와 수료증도 업무상 유지합니다(일반 공개 API에는 노출하지 않음).
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

- **[확정 정책]** 승인 후 실제 입양 진행 실패 시 해당 APPROVED 신청서를 REJECTED로 변경하고 `reviewedDttm`을 실패 처리 시각으로 갱신. 별도 입양 프로세스/실패 이력 테이블 생성 금지. **[초안]** 호출 주체 담당 보호소/ADMIN.

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
| 13.5 | DELETE | `/comments/{commentType}/{commentSeq}` | 작성자/ADMIN(운영 처리 [초안]) | 댓글 소프트 삭제 |

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
- **오류 [초안]** 400 `EMPTY_COMMENT`, `COMMENT_DEPTH_EXCEEDED`; 404 `COMMENT_TARGET_NOT_FOUND`; 409 `PARENT_COMMENT_UNAVAILABLE`.

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

- **[확정 정책]** TEXT는 비공백 본문 필수. IMAGE는 `imageSeq` **정확히 하나 필수**, body는 선택. SHARED_POST는 `sharedTargetType/sharedTargetSeq` 필수이며 코드 `MISSING/FOUND/SHELTER_ANIMAL`만. 다형성 대상 존재·접근 권한은 BE 검증. `senderUserSeq`는 인증 계정(보호소 담당자도 users PK)에서 설정.
- 상대가 탈퇴했다면 신규 메시지 거부. **차단 기능이 없으므로 차단 상태 검증 API는 작성하지 않습니다.** 메시지 개별 삭제 기능 없음.
- DB `notifications`에 채팅 메시지 알림을 저장하지 않으며 FCM 푸시만 발송합니다. 발송 실패 시 재시도 없음.
- **실시간 수신 [미정]:** WebSocket/STOMP 등 프로토콜과 이벤트명/ACK/중복전송 처리 방식 미확정. 이 POST는 **초안 REST 전송 계약**이며 팀이 소켓 전송으로 바꿀 수 있습니다.
- **오류 [초안]** 400 `INVALID_CHAT_PAYLOAD`; 403 `CHAT_ROOM_NOT_PARTICIPANT`; 409 `CHAT_COUNTERPART_DELETED`; 404 `SHARED_POST_NOT_FOUND`.

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
- 같은 `(reporterUserSeq,targetType,targetSeq)`는 **전역 UNIQUE**로 단 한 번만 접수 가능; 현재 `PENDING`만 사용. 관리자 심사/처리/제재 API는 **구현 제외**. 사용자 자동 차단/차단 해제도 없음.
- **오류 [초안]** 409 `REPORT_ALREADY_EXISTS`; 400 `REPORT_TARGET_NOT_ALLOWED`, `REPORT_REASON_REQUIRED`; 404 `REPORT_TARGET_NOT_FOUND`.

### 15.2 GET /users/me/reports

**목록 [초안]** `?page=0&size=20` → `{list:[{reportSeq,targetType,targetSeq,reasonDetail,status:"PENDING",regDttm}],...}`. **DB에는 기록이 존재하지만 이를 사용자 화면에 노출하는 기능은 별도 확정되지 않았습니다.** 필요 없으면 제거할 후보입니다.

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
- **[확정 정책]** 대상은 `MISSING/FOUND/SHELTER_ANIMAL` 3종. 회원+대상 UNIQUE, 해제는 **물리 DELETE**. 동일 북마크 추가 시 409 `BOOKMARK_ALREADY_EXISTS`를 [초안] 기본으로 제안(반복 POST를 멱등 200으로 처리할지는 확인 필요).

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

- **[확정 정책]** 5개 유형 `COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM`; `REPORT` 없음. `targetType/targetSeq` 둘 다 NULL인 운영 알림 허용. 연결 대상이 있다면 서버에서 쌍과 접근 권한 검사.
- `AI_MATCH`에서 `targetType`은 **`FOUND` 또는 `SHELTER_ANIMAL`**, `targetSeq`는 매칭 상대 PK. `missingReportSeq`는 원본 실종 신고. **`matchedTargetType/matchedTargetSeq` 중복 필드 없음**.
- `ADOPTION` 클릭 대상 타입 `ADOPTION`은 입양 신청 `applicationSeq`를 가리킵니다. `SYSTEM`은 운영 공지/점검 안내. `SHELTER`는 담당 발견 신고 배정 등 보호소 고유 업무 유형이며 보호소가 받는 모든 알림의 범용 타입이 아닙니다.

### 17.2~17.5 알림 읽음·삭제

- `17.2` 성공 200 `data:{notificationSeq,isRead:true,readDttm:"..."}`; 타인 알림 수정 금지.
- `17.3` 성공 200 `data:{updatedCount:5}` [초안]. 읽은 시간은 서버가 설정.
- `17.4` 및 `17.5` 성공 200 `data:null` [초안]. **[확정 정책]** `is_deleted=true`로 소프트 삭제(개별/전체), 별도 숨김 기능 없음.
- 삭제된 AI_MATCH 알림도 **과거 알림 존재 확인에 포함**되어 같은 쌍의 알림을 재생성하지 않습니다.

### 17.6 POST /notifications/device-tokens · 17.7 DELETE /.../current

**등록 요청 [초안]** `{ "fcmToken":"fcm-device-token" }`.

**등록 성공 200 `data` [초안]** `{ "active":true }`.

- **[확정 정책]** 계정당 활성 FCM 토큰 최대 1개, 동일 토큰을 여러 계정에서 동시에 활성화 금지. 등록 시 기존 토큰을 비활성화하고 새 토큰을 활성화합니다. 탈퇴 시 계정의 활성 토큰 전부 비활성화.
- `17.7` **요청 [초안]** 토큰을 BODY 또는 인증된 현재 기기의 등록 식별자로 지정하는 방식 [미정]. 성공 `data:null`; DB에는 활성 해제 시각 `mod_dttm` 기록.

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
| 일반 시스템 안내 | SYSTEM 유형 저장 가능; 발송 주체/트리거 별도 정의 | [확정 정책]/[초안] |
| 채팅 수신 | **FCM만**, `notifications` DB 행 저장하지 않음 | [확정 정책] |

**AI 중복 알림 필수 처리 순서 [확정 정책]**: ① 유효 동물/신고 상태 검사 → ② 해당 `missing_reports` 행에 `SELECT ... FOR UPDATE` 획득 → ③ `is_deleted=true` 알림까지 포함해 동일 쌍 `AI_MATCH` 중복 조회 → ④ 없으면 `message TEXT NOT NULL` 및 `targetType/targetSeq`로 한 행 INSERT → ⑤ 트랜잭션 커밋 → ⑥ DB에 저장한 **똑같은 message 문자열**을 FCM body로 발송. 별도 전용 AI UNIQUE 인덱스는 **없습니다**. FCM 실패 시 DB 알림은 보존하고 재시도하지 않습니다.

---

## 18. 관리자·기준정보 운영 (ADMIN — 초안 범위)

> MOTIF 문서처럼 ADMIN 전용 API를 완전히 별도 문서로 분리할 수 있습니다. 이 장은 **DB v8에서 허용한 운영 역할을 빠뜨리지 않기 위한 최소 초안**입니다. ADMIN 화면 구현 범위·경로는 [미정]입니다. **신고 심사/회원 차단/보호소 이관은 여기에 넣지 않습니다.**

| # | Method | Path | 권한 | 기능 | 확정 여부 |
|---|---|---|---|---|---|
| 18.1 | GET | `/admin/shelters` | ADMIN | 전체 보호소 업무 목록 | [초안] |
| 18.2 | POST | `/admin/shelters` | ADMIN | 보호소 기준정보 생성 | [초안] |
| 18.3 | PATCH | `/admin/shelters/{shelterSeq}` | ADMIN | 식별/운영 정보 수정 | [확정 정책: 역할], [초안: API] |
| 18.4 | PATCH | `/admin/found-reports/{foundReportSeq}/shelter` | ADMIN | 담당 보호소 수동 재배정 | [확정 정책: 동작], [초안: API] |
| 18.5 | PUT | `/admin/shelter-district-assignments/{districtCode}` | ADMIN | 시군구→보호소 매핑 설정 | [초안] |
| 18.6 | GET | `/admin/adoption/questions` | ADMIN | 현재 질문 관리 목록 | [초안] |
| 18.7 | PATCH | `/admin/adoption/questions/{questionSeq}` | ADMIN | 현재 질문 문구/보기/조건 변경 | [초안] |
| 18.8 | POST | `/admin/notifications/system` | ADMIN | SYSTEM 알림 생성·발송 | [초안] |
| 18.9 | GET | `/admin/rescues` | ADMIN | 전체 구조 기록 필터 조회 | [초안] |
| 18.10 | GET | `/admin/adoption-applications` | ADMIN | 전체 입양 신청 업무 조회 | [초안] |

### 18.2 POST /admin/shelters · 18.3 PATCH /admin/shelters/{id}

- **요청 [초안]** `{ "shelterName":"행복동물보호센터", "externalShelterCode":"...", "address":"...", "latitude":35.87, "longitude":128.60, "phone":"0531234567", "description":"...", "operatingHours":"...", "closedDays":"...", "profileImageSeq":1701, "backgroundImageSeq":1702 }`.
- 이름/주소/좌표/전화는 보호소 기준정보에서 필수. `externalShelterCode`는 NULL 허용 UNIQUE. 보호소 계정 사전 발급·1:1 매핑의 실제 운영 API는 [미정]이므로 자동 발급을 여기서 단정하지 않습니다.
- 보호소 소프트 삭제 API는 Q356 보류이므로 **작성하지 않습니다**.

### 18.4 PATCH /admin/found-reports/{id}/shelter

**요청 [초안]** `{ "shelterSeq":9 }` → 성공 200 `data:{foundReportSeq:210,shelterSeq:9}`.

- **[확정 정책]** ADMIN만 수동 재배정. 이미 생성된 `rescue_records.shelter_seq`는 **변경하지 않음**. USER가 주소를 수정해도 자동 배정 재실행 금지.

### 18.5 PUT /admin/shelter-district-assignments/{districtCode}

**요청 [초안]** `{ "shelterSeq":7 }` → 성공 200 `data:{districtCode:"...",shelterSeq:7}`.

- **[확정 정책]** `district_code`는 단일 PK이므로 한 시군구당 지정 보호소 하나. 지역 중복 지도 등록 불허. 행정구역 코드 데이터 마스터/API는 DB v8에서 별도 테이블로 정의되지 않았으므로 **새 마스터를 임의 추가하지 않습니다**.

### 18.6~18.7 질문 관리

- `18.7` **요청 [초안]** `{ "questionText":"...", "questionType":"TEXT", "isRequired":true, "displayOrder":1, "options":null, "condition":null }`.
- **[확정 정책]** 현재 21개 질문 행을 수정. `questionSeq` 안정성 유지 필수. 변경 이력/스냅샷 미보관, **과거 신청서도 변경된 현재 문항 문구로 표시되는 위험을 허용한 결정**입니다. 문항 PK를 새로 발급하거나 질문을 지우는 API는 넣지 않았습니다.

### 18.8 POST /admin/notifications/system

- **요청 [초안]** `{ "recipientUserSeqs":[17,18], "message":"서비스 점검 안내입니다.", "targetType":null, "targetSeq":null }` → 성공 `data:{createdCount:2}`.
- 단순히 SYSTEM 타입이 DB에 있다는 사실만으로 관리자 대량 발송 화면이 요구된 것은 아닙니다. 필요 여부 확인 후 남깁니다. 실제 전송은 수신 사용자별 `notifications` 행을 생성한 뒤 커밋 후 FCM 송신.

---

## 19. 도메인 에러 코드 후보 총괄 **[초안]**

> 아래 이름은 BE 구현 및 Android 분기용 **제안 코드**입니다. **DB v8에는 확정 API 오류 코드 목록이 없습니다.** 실제 HTTP 매핑/표준 메시지/로그 필드는 팀 규약 확정 후 반영합니다.

| 도메인 | 에러 code | HTTP 후보 | 주된 발생 조건 |
|---|---|---|---|
| 인증 | `INVALID_KAKAO_TOKEN`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` | 401 | 인증 실패 |
| 회원 | `DUPLICATE_NICKNAME`, `DUPLICATE_PHONE` | 409 | 활성 USER 유니크 충돌 |
| 회원 | `REQUIRED_USER_PROFILE_MISSING` | 400 | 최종 USER 필수 개인정보 불충족 |
| 이미지 | `IMAGE_NOT_FOUND`, `INVALID_IMAGE_COUNT`, `FILE_TOO_LARGE` | 404/400/413 | 이미지 업로드·연결 오류 |
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

---

## 20. 회의 확인 필요 (API 수준 미결 안건)

> **DB 재설계 질문이 아니라 API 계약을 위해 실제로 결정해야 하는 항목**만 정리합니다. DB v8에서 의도적으로 스킵한 차단/이관/AI 벡터 설계/보호소 삭제 엣지케이스는 여기서 재논의 대상으로 되살리지 않습니다.

| 우선순위 | 안건 | 지금 사용한 가안 | 확인 이유 |
|---|---|---|---|
| **P0** | KAKAO 최초 가입 완료 흐름 | 기존 로그인과 개인정보 4종을 받는 최종 등록 분리 | USER 4개 필드 NOT NULL CHECK와 모순 없는 가입 필수 |
| **P0** | 인증 토큰 운영 | Bearer JWT + refresh를 단순 가안 | refresh 저장 구조·rotation·만료/로그아웃 규칙이 DB v8에 없음 |
| **P0** | API 공통 응답 규약 | MOTIF의 5필드 envelope 제안 | 이번 프로젝트 팀 공통 규약 확정 필요 |
| **P0** | SHELTER가 실종/발견 신고 생성·수정/종료 가능한 범위 | 일반 신고 생성은 USER만, 발견 종료는 담당 SHELTER 허용 | 역할별 기능 요구의 상세 정의 필요 |
| **P0** | 입양 DRAFT가 보호소에서 보이는 범위 | 제출 전 응답은 보호소 업무 목록에 비노출 우선 | 신청자 개인정보 보호/업무 UX |
| **P0** | 일반 게시물·댓글·채팅 권한 경계 | DB 소유권/소속 기준으로 제한 | 수정/종료/삭제 가능 상태, 관리자 운영 범위 구체화 |
| **P1** | 이미지 업로드 프로토콜 | S3 presigned PUT + 메타데이터 등록 | DB에는 저장소만 있어 업로드 형식은 미확정 |
| **P1** | 시간 응답 표기 | ISO-8601 `+09:00` (동등 UTC Z 허용) | Android 파싱/DB 세션 규약 일치 |
| **P1** | 리스트 검색·정렬·페이징 | 일반 offset, 채팅은 messageSeq 기반 | 화면별 UX, 대용량 데이터 목록 안정성 |
| **P1** | 채팅 실시간 수신/전송 | REST 송신 + 수신 프로토콜 미정 | WebSocket/STOMP 등 연결 계약 필요 |
| **P1** | 사진 변경 DTO의 부분/전체 교체 의미 | `imageSeqs`는 전체 최종 배열 | 순서 중복/마지막 참조 정리 구현 |
| **P1** | 실종·발견 신고 CLOSED 상태의 본문 수정, 신고 재개 UX | 본문 수정 경로만 정의, 상태별 허용은 미정 | 서버 검증·앱 편집 화면 분기 |
| **P1** | 상태 전이·댓글/입양 세부 알림 이벤트 | DB 유형만 확정, 수신 이벤트 미정 | 불필요한 푸시·중복 발송 방지 |
| **P1** | 이미지/수료증 저장소 객체 정리와 실패 복구 | 마지막 참조 확인 후 삭제 | DB 트랜잭션과 S3 삭제 비원자성 |
| **P2** | ADMIN 전용 API 실구현 범위 | 최소 운영 API 후보만 포함 | 어드민 별도 명세 여부 |
| **P2** | 신고 내역 조회 화면 | §15.2 임시 기재 | 신고 심사 기능 구현 제외와 혼동 방지 |
| **P2** | 204 vs 200 및 409 vs 멱등 응답 | 200 envelope / 중복 시 409 | Android 에러 처리 통일 |
| **P2** | 구체 오류코드·API 경로 명명 | 본 문서 후보 | 컨트롤러/프론트 DTO naming 합의 |

### 아직 스펙에 **없는 것** (의도된 제외)

- 회원 차단·차단 해제·신고 시 자동 차단, 관리자 신고 심사/신고 처리 완료 API.
- 보호동물 보호소 간 이관, 보호소 삭제·정리, 사후관리, 모의 입양 기능 및 테이블/API.
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

---

## 22. DB v8 테이블 ↔ API 대응표

| DB 테이블 | 주요 API 절 | 주의 |
|---|---|---|
| `users`, `auth_providers`, `user_accounts` | 2·3 | USER 필수 개인정보/LOCAL 사전 발급/동시 소프트 삭제 |
| `shelters`, `shelter_members`, `shelter_district_assignments` | 8·9·18 | 보호소당 계정 1개, districtCode 단일 PK |
| `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations` | 5 | 조합 FK 한 개로 저장 |
| `pets`, `pet_images` | 6 | 사진 1~3장, 실종 신고 스냅샷 별도 |
| `images` | 4·6~11·13·14 | 마지막 참조 해제 후 원본 삭제 |
| `missing_reports`, `missing_report_images` | 7·17 | 회원당 ACTIVE 최대 1건, AI 알림 기준 |
| `found_reports`, `found_report_images` | 8·10·17·18 | 담당 보호소 자동 배정/ADMIN 재배정 |
| `rescue_records` | 10·11 | DONE 1:N 보호동물, 구조 정보 JOIN |
| `shelter_animals`, `shelter_animal_images` | 11·12 | 최초 PROTECTING+UNAVAILABLE, 이름 없음 |
| `animal_returns` | 11 | 반환 시점=regDttm, 소유권 검증 아님 |
| `adoption_form_questions` | 12·18 | 현재 21문항, 문항 이력/버전 없음 |
| `adoption_applications` | 12 | JSONB questionSeq 키, 6상태, 별도 프로세스 없음 |
| `chat_rooms`, `chat_participants`, `chat_messages` | 14 | 마지막 읽음/나가기 포인터, 직접 imageSeq |
| `missing_report_comments`, `found_report_comments`, `shelter_animal_comments` | 13 | 각각 imageSeq 직접 FK, 삭제 루트의 답글 유지 |
| `reports` | 15 | PENDING만, 신고 심사 없음 |
| `bookmarks` | 16 | 해제 물리 삭제 |
| `notifications`, `device_tokens` | 17 | 5종, AI 최초 1회/FCM 재시도 없음 |

---

**초안 종료.** 이 문서는 DB v8의 현재 기능과 제약을 API 초안으로 변환한 것이며, 제안된 API 경로·DTO 및 미결 운영 정책은 사용자/팀의 확인 전까지 확정 사항으로 취급하지 않습니다.
