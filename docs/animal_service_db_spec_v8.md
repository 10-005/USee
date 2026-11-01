# 유실·발견동물·보호소·입양 플랫폼 DB 통합 명세서 — v8

> **기준일:** 2026-10-29  |  **기반:** `animal_service_db_spec_v7.md` 전체 통합 명세 + Q393~Q405 확정 결정  |  **구분:** 통합 설계 명세 및 개념 SQL(실행 DDL 아님)  |  **현행 테이블:** 33개

## 0. 문서 적용 원칙 및 변경 범위

- **최신 사용자 확정 결정이 이전 v5·v6·v7 문구나 UX 초안보다 우선**합니다. Q263~Q405의 확정·스킵 중 후속 결정이 앞선 결정에 우선합니다. 특히 Q393~Q405는 v7의 Q263~Q392 결정 중 충돌하는 내용을 대체합니다.
- 테이블/컬럼 중 **확정**과 **초안·보류**를 구별합니다. 원본의 PK 자료형과 일부 선택 컬럼·인덱스 이름은 물리 구현 초안일 수 있습니다. v7에서 제거된 3개와 이번 v8에서 제거된 5개 테이블은 현재 모델에 포함하지 않습니다.
- 현재 구현 범위에 포함하지 않는 것은 보호소 간 이관, 사용자 차단, 모의 입양, 관리자의 신고 심사, 이미지 임베딩 상세 설계 등입니다.
- **Q393~Q405의 최신 확정 내용까지 반영**했습니다. Q392의 SYSTEM 추가 스킵은 Q398에서 덮어써 SYSTEM 유형을 추가했으며 REPORT 유형은 추가하지 않습니다. Q336·Q356 및 기타 보류는 임의 결정하지 않습니다.
- DB 제약이 가능한 것은 UNIQUE/FK/CHECK로 기술하고, 여러 행/테이블·권한·API 동작은 BE 검증 및 트랜잭션으로 분리합니다.

### v6에서 계승한 핵심 구조 변경

| 영역 | v6 최종 변경 | 근거 |
|---|---|---|
| 인증·탈퇴 | users/user_accounts 동시 소프트 삭제; USER 필수 개인정보 역할 CHECK; 활성 닉네임/전화번호 UNIQUE | Q299~301, Q328~329, Q370 |
| 동물 기준 | animal_colors + animal_combinations 신설; 네 동물 엔터티의 조합 FK 단일화 | Q333~337 |
| 반려동물 | pets.image_seq 제거, pet_images 1:N 신설; weight 텍스트; 삭제 시 신고 유지 | Q293, Q348~349, Q362 |
| 발견 배정 | shelter_district_assignments 신설; 구역 우선·최근접 대체 | Q284~288, Q338 |
| 구조·보호 | PROCESSING/DONE/FAILED/CANCELED만; 구조 1:N 보호동물; 구조 정보 복사(당시 정책; Q404에서 대체) | Q294~298, Q351~357, Q360~361 |
| 입양 양식 | 공통 21문항·활성 버전 1개(당시 정책; Q402에서 대체); 선택지/조건 JSONB; 답변 통합 JSONB | Q273~276, Q320~324, Q358~359 |
| 입양 신청 | 동일 사용자·동물 활성 1건; 최종 ADOPTED 상태; 거절/탈퇴 처리 명확화 | Q263~272, Q318, Q325~326, Q363 |
| 커뮤니티 | user_blocks 제거, reports 전역 중복 제한; 댓글 삭제 시 답글 보존 | Q306~308, Q316~317, Q340 |
| AI·알림 | 유효 상태 비교, 양방향 신규 등록·사진 변경 재매칭, 실종 작성자 단방향 알림 | Q291, Q339, Q343~347 |
| 삭제·범위 | animal_transfers/모의 입양 현행 제외, 보호소 삭제 정책 스킵 | Q283, Q336, Q356 |

### v7에서 계승한 변경사항 (Q371~Q392, 일부는 v8에서 재변경)

| 영역 | v7 당시 정책 (v8 후속 변경 시 최신 결정 우선) | 근거 |
|---|---|---|
| 테이블 정리 | `adoption_processes`, `adoption_application_histories`, `shelter_animal_status_history` 제거(41→38개) | Q375·376·378 |
| 입양 신청 | 상태 6종(`DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED`), `CLOSED` 및 `closed_reason` 삭제. `rejection_reason TEXT`는 자연어 설명. 이력/단계 컬럼 없음 | Q371~377 |
| 자동 거절 | 자동 거절 시 `reviewed_dttm`을 거절 시각으로 갱신하고 `reviewed_usr_seq=NULL`, 이유 문장을 BE가 생성 | Q372·376 |
| 구조·발견 | `found_reports.closed_reason TEXT NULL`, `rescue_records.received_dttm` 삭제(접수 시각은 `reg_dttm`), `completed_dttm` 상태별 필수 CHECK | Q379·384 |
| 사진 | 실종·발견·보호동물 이미지 연결의 `image_type` 삭제; 공유 이미지는 모든 참조 해제 후에만 공통 DB 행/S3 원본 삭제 | Q383·385 |
| 알림 | **v7 당시** 유형 4개였으나 Q398에서 `SYSTEM`을 추가하여 **현행 5개**. `message TEXT NOT NULL` 저장·FCM body 재사용; 대상 다형성 참조, NULL 가능 | Q380~382·391·392→Q398 |
| AI 중복 | AI 매칭 쌍 최초 1회는 BE 검증. 전용 UNIQUE 없음. `missing_reports` 행 `FOR UPDATE` 잠금 후 중복 검사·생성 | Q389·390 |
| 공통 코드 | 다형성 대상 코드를 통일하고, 입양 프로세스 대상 코드는 `ADOPTION` 사용 | Q387 |
| 감사 컬럼 | 작성자 FK와 등록자가 겹치는 테이블의 `reg_usr_seq`만 제거; 보호소 업무/시스템 등록자·기존 수정자 및 시각 유지 | Q386 |
| 공고 기간 | 수동 보호동물 등록의 기존 기본 규칙 유지, 초기 공공데이터의 날짜는 적재자가 직접 정해 입력 | Q388 |

### v8 반영 변경사항 요약 (Q393~Q405)

| 영역 | 최신 v8 확정 정책 | 근거 |
|---|---|---|
| 시각 | PostgreSQL `TIMESTAMPTZ` 유지, DB 연결/세션 `TimeZone='Asia/Seoul'` 사용. 내부의 시점 정보는 UTC 기준으로 보존, 날짜 자체는 `DATE` | Q393 |
| 사용자 | `users.nickname/name/birth_date/phone`은 원래부터 NULL 허용+USER만 필수 CHECK이므로 변경 없음 | Q370 확인 |
| 보호소 | `shelters.background_image_seq BIGINT NULL` 추가, `images.image_seq` FK | Q394 |
| 행정구역·발견 배정 | `shelter_district_assignments.district_code` **단일 PK 유지**; 시군구당 지정 보호소 1곳, 발견 신고당 담당 보호소 1곳 | Q395 |
| 반려동물 | `pets.mod_usr_seq` 제거, `mod_dttm` 유지 | Q396 |
| 신고 | `missing_reports`, `found_reports`에서 `circumstances` 제거; 설명 컬럼은 동물 특징 용도로 유지. 회원당 비삭제 ACTIVE 실종 신고 최대 1건(새로 확인한 정책) | Q397 및 Q399 선행 확인 |
| 알림 | `notifications.notification_type`에 `SYSTEM` 추가(총 5종, NOT NULL). AI 매칭 상대는 `target_type/target_seq`를 재사용해 `matched_target_type/seq` 삭제; `missing_report_seq` 유지 | Q398~399 |
| 댓글 이미지 | 이미지 연결 테이블 3개 삭제하고 댓글 테이블 3개에 `image_seq BIGINT NULL` FK 추가 | Q400 |
| 채팅 이미지 | `chat_message_images` 삭제, `chat_messages.image_seq BIGINT NULL` FK 추가 | Q401 |
| 입양 질문·양식 | `adoption_form_templates` 삭제. 문항 테이블만 현재 21개를 관리하고 `template_seq`, `version`, `question_code`를 사용하지 않음. 답변 JSONB 키는 `question_seq`; 질문 변경 이력·제출 당시 문항 스냅샷 없음 | Q402 및 추가 결정 |
| 반환 | `animal_returns.returned_dttm` 삭제, `reg_dttm`이 반환 완료 시각 | Q403 |
| 구조·보호 | `shelter_animals`의 구조일·사유·장소·메모 4개 컬럼 삭제. `rescue_seq` FK로 원본 `rescue_records`를 LEFT JOIN하여 조회; 직접 등록/초기 적재 시 `rescue_seq=NULL` 허용 | Q404~405 |
| 테이블 수 | 38개 → **33개**(댓글 이미지 3개 + 채팅 이미지 1개 + 입양 템플릿 1개 제거) | Q400~402 |

> **문서 해석:** 이하 테이블 표와 개념 SQL은 **v8 현행 기준**입니다. 하단 Q263~Q392의 과거 추적표는 당시 결정 기록이고, 변경된 정책은 Q393~Q405가 우선합니다. `SYSTEM` 알림은 추가하되 보류 기능은 임의로 추가하지 않습니다.

## 1. DB 공통 설계 규칙

- DB: PostgreSQL. PK는 `*_seq` 원칙, `IDENTITY` 사용. 로그·이미지·메시지 일부 BIGINT는 기존 v5 초안을 유지.
- 시간: `TIMESTAMPTZ`로 절대 시점을 저장(내부 UTC 기준), **DB 연결·세션 `TimeZone='Asia/Seoul'` 설정**으로 한국 시간으로 조회·표시. `reg_dttm`은 생성 시점, `mod_dttm`은 수정 시점. `DATE`는 날짜 의미 그대로 보관. API 날짜 직렬화도 동일 기준 적용(Q393).
- 삭제: 도메인 본문은 `is_deleted DEFAULT FALSE` 소프트 삭제 기본. 특별히 입양 DRAFT와 북마크 해제는 물리 삭제.
- 이미지: 공통 `images` + 다중 이미지 콘텐츠/반려동물 연결 행. 댓글/채팅은 **각 본문 테이블의 `image_seq NULL` 직접 FK**. 콘텐츠/반려동물 사진 1~3장, 댓글/채팅 사진 최대 1장, JPG/JPEG/PNG, 최대 10MB. `display_order=1` 대표. 원본을 공유하면 마지막 참조 해제 전 공통 이미지 메타데이터/S3 객체를 삭제하지 않음(Q383).
- 관계: FK에 의한 물리 연쇄 삭제 대신 참조 정보 보존. 다형성 `target_type+target_seq`는 직접 FK 불가하므로 BE 존재·권한 검증. 공통 대상 코드와 테이블별 허용 코드를 구분(Q387).
- 일반회원만 `pets`·`adoption_applications` 생성. ADMIN은 전역 관리. SHELTER는 소속 업무와 댓글·보호소 채팅 수행.
- 일반 API는 로그인 필수. 탈퇴 작성자의 게시물·상세는 숨기되, 채팅·댓글·알림과 보호소 업무용 **과거 입양 신청서·현재 상태 기록**은 예외적으로 보존(별도 신청 상태 전이 이력 테이블 없음).

## 2. 관계 개요

```text
users 1:1 user_accounts N:1 auth_providers
  ├─1:N pets 1:N pet_images N:1 images
  ├─1:N missing_reports 1:N missing_report_images N:1 images
  ├─1:N found_reports 1:N found_report_images N:1 images
  ├─1:N adoption_applications N:1 shelter_animals
  └─1:N chat_messages / comments / notifications / device_tokens
shelters 1:0..1 shelter_members N:1 users(role=SHELTER)
  └─profile_image_seq/background_image_seq N:0..1 images (각각 선택 이미지 1장)
  ├─1:N shelter_district_assignments (각 district_code는 단일 PK로 보호소 하나)
  ├─1:N rescue_records N:0..1 found_reports
  └─1:N shelter_animals 1:N shelter_animal_images N:1 images
rescue_records 1:N shelter_animals (rescue_seq NULL 허용; DONE만 참조; 구조 4개 속성은 JOIN 조회; 보호소 달라도 가능)
shelter_animals 1:0..1 animal_returns (성공한 실제 반환만)
shelter_animals 1:N adoption_applications (answers_jsonb 질문 키=question_seq)
adoption_form_questions (전역 공통 현재 21문항만; 템플릿/버전/문항 이력 없음)
adoption_applications에 입양 신청·심사·최종 입양 상태 통합(별도 후속절차/이력 테이블 없음)
animal_species 1:N animal_breeds 1:N animal_combinations N:1 animal_colors
pets/missing_reports/found_reports/shelter_animals N:1 animal_combinations
chat_rooms 1:N chat_participants; 1:N chat_messages (image_seq NULL FK → images, 메시지당 최대 1장)
missing/found/shelter_animal_comments 각각 자체 부모 복합 FK + image_seq NULL FK → images (댓글당 최대 1장)
reports / bookmarks / notifications / chat_messages(SHARED_POST): 공통 코드 기반 다형성 대상 참조 (지원 대상은 테이블별로 제한)
실종 신고 ↛ 발견 신고·보호동물 (AI 유사도 알림만, 확정 링크 없음)
users 1:0..1 비삭제 ACTIVE missing_reports; CLOSED는 과거 행 보존·새 신고 가능
```


### v8 단순화 구조의 조회·정합성 참고

- **반환 완료 시각:** `animal_returns.reg_dttm`이 반환 처리 시각입니다. 실제 반환 시각을 별도 소급 입력하지 않는 현재 업무 정책을 전제로 합니다.
- **구조 기록 JOIN:** 구조 기반 보호동물은 `LEFT JOIN rescue_records r ON r.rescue_seq = shelter_animals.rescue_seq`로 구조일/사유/주소/메모를 조회합니다. `rescue_seq=NULL`인 동물은 네 정보가 모두 NULL입니다.
- **AI 알림 식별:** `notification_type='AI_MATCH'`인 알림의 `target_type`은 `FOUND` 또는 `SHELTER_ANIMAL`이고 `target_seq`는 해당 상대의 PK입니다. 같은 `missing_report_seq`와 상대 조합이 존재하면 재생성하지 않습니다(`is_deleted=TRUE` 포함).
- **입양 질문:** DB에는 활성 버전/질문 보관 이력이 없습니다. 현행 질문을 바꾸더라도 과거 `answers_jsonb` 자체는 남으며, `question_seq`가 같으면 현재 질문 문구에 붙여 표시합니다. 기존 응답과 질문 의미가 달라질 위험을 허용한 결정입니다.
- **시각 변환:** JDBC/DB pool 등 애플리케이션 연결별 PostgreSQL 세션 `TimeZone`을 `Asia/Seoul`로 맞춥니다. `TIMESTAMPTZ`를 `TIMESTAMP`로 바꾸거나 타임존이 없는 한국 시간 문자열을 저장하는 정책이 아닙니다.

## 3. 테이블별 통합 상세 명세

### 사용자·인증·보호소

#### 01. `users` — 공통 계정·역할 및 회원 개인정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `user_seq` | `INTEGER` | Y | PK, IDENTITY | 공통 사용자 키(확정) |
| `role` | `VARCHAR(15)` | Y | CHECK `USER/SHELTER/ADMIN` | 서비스 권한(확정) |
| `nickname` | VARCHAR(50) | N | USER 필수 CHECK; 활성 USER case-insensitive 부분 UNIQUE | 닉네임 |
| `name` | VARCHAR(100) | N | USER 필수 CHECK | 회원 실명; 보호소·관리자 NULL 허용 |
| `birth_date` | DATE | N | USER 필수 CHECK | 생년월일 |
| `phone` | VARCHAR(30) | N | USER 필수 CHECK; 활성 USER 부분 UNIQUE | 회원 전화번호; 회원가입 시 형식 검증 |
| `profile_image_key` | TEXT | N | - | 프로필 이미지 저장소 키(선택) |
| `account_status` | `VARCHAR(20)` | Y | 기본값 ACTIVE; 상태 세부 운영 보류 | ACTIVE/SUSPENDED 후보, 탈퇴와 별도 |
| `deleted_dttm` | `TIMESTAMPTZ` | N | ※초안 | 탈퇴 시각(잠정) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 탈퇴 소프트 삭제; 관련 작성자 콘텐츠 가상 비노출 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각; 최초에는 NULL |

**확정 규칙 / 참고사항**
- `role=USER`인 경우 `nickname/name/birth_date/phone` 전부 입력 필수(DB CHECK). SHELTER/ADMIN은 NULL 허용. 공백·형식은 BE 검증(Q370).
- 활성 USER 기준 `lower(nickname)` 및 `phone` 각각 부분 UNIQUE(`role=USER AND is_deleted=FALSE`). 탈퇴 뒤 닉네임·번호 재사용 가능(Q300·328). SMS 인증은 구현하지 않음(Q329).
- 탈퇴 시 `users`·`user_accounts` 모두 소프트 삭제, 모든 활성 FCM 토큰 비활성화. USER 소유 게시물은 행을 직접 변경하지 않고 서비스에서 비노출(가상 삭제)(Q301·312·327).
- 탈퇴 회원의 댓글/답글, 기존 채팅·알림은 작성자 표시를 `(탈퇴한 사용자)`로 대체해 유지. 탈퇴 회원 루트 댓글에는 신규 답글 금지(Q314~317).
- 탈퇴 회원 관련 일반 조회는 별도 오류로 구별. 보호소는 업무상 과거 입양 신청서/증명서를 계속 열람할 수 있음(Q313·319).
- USER는 KAKAO, SHELTER/ADMIN은 LOCAL 인증만 이용(서비스 검증). SUSPENDED 등 계정 상태 상세는 보류.

#### 02. `auth_providers` — KAKAO/LOCAL 인증 제공자

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `provider_seq` | `INTEGER` | Y | PK, IDENTITY | 인증 제공자 PK |
| `provider_code` | `VARCHAR(20)` | Y | UNIQUE | `KAKAO`, `LOCAL` |
| `provider_name` | `VARCHAR(50)` | N | ※초안 | 표시명 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 사전 값 `KAKAO`, `LOCAL`. 인증 제공자별 로그인 식별자 관리.

#### 03. `user_accounts` — 로그인 식별자 및 계정 생명주기

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `user_account_seq` | `INTEGER` | Y | PK, IDENTITY | 인증 계정 PK(※명명 초안) |
| `user_seq` | `INTEGER` | Y | FK → users, UNIQUE | 계정당 인증 수단 1개 |
| `provider_seq` | `INTEGER` | Y | FK → auth_providers | `KAKAO` 또는 `LOCAL` |
| `login_identifier` | `VARCHAR(255)` | Y | 활성 계정 부분 UNIQUE `(provider_seq,login_identifier)` | 카카오 주체 ID 또는 LOCAL 로그인 ID; 재가입 시 새 행 허용 |
| `password_hash` | `VARCHAR(255)` | N | 조건부 CHECK 후보 | LOCAL만 필수, KAKAO는 NULL |
| `is_deleted` | BOOLEAN | Y | NOT NULL DEFAULT FALSE | 탈퇴 시 인증 행 소프트 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 한 회원에 인증 수단 1개(`user_seq UNIQUE`). 탈퇴 시 인증 행 소프트 삭제, 재가입은 새로운 회원+인증 행으로 생성.
- `(provider_seq,login_identifier)`는 `WHERE is_deleted=FALSE`에 대해서만 UNIQUE, 과거 계정 이력은 보존.
- LOCAL 비밀번호는 해시만 저장. 사전 발급 계정이며 비밀번호 재설정은 구현 제외.

#### 04. `shelters` — 보호소 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `shelter_seq` | `INTEGER` | Y | PK, IDENTITY | 내부 보호소 키(확정) |
| `shelter_name` | `VARCHAR(150)` | Y | ※초안 | 보호소 명칭 |
| `external_shelter_code` | `VARCHAR(100)` | N | UNIQUE (NULL 허용) | 외부 공공데이터 보호소 식별 코드; 내부 관계는 shelter_seq 사용 |
| `address` | `VARCHAR(255)` | Y | - | 보호소 주소(확정) |
| `latitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 보호소 위도(확정 필수) |
| `longitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 보호소 경도(확정 필수) |
| `phone` | `VARCHAR(30)` | Y | - | 전화번호(확정 필수) |
| `description` | `TEXT` | N | ※초안 | 소개·운영 안내 |
| `operating_hours` | `TEXT` | N | - | 운영시간(확정) |
| `closed_days` | `TEXT` | N | - | 휴무일(확정) |
| `profile_image_seq` | `BIGINT` | N | FK → images | 보호소 프로필 이미지 0~1장 |
| `background_image_seq` | `BIGINT` | N | FK → images | 보호소 배경 이미지 0~1장(Q394) |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `profile_image_seq`와 `background_image_seq`는 각각 `images.image_seq`를 직접 참조하고 공통 마지막 참조 해제 후 삭제 정책(Q383)을 적용.
- 보호소 이름·주소·좌표·전화번호 필수. 운영 정보는 소속 담당자/ADMIN, 식별 정보는 ADMIN 수정.
- `external_shelter_code`는 NULL 허용 UNIQUE이며 공공데이터 재적재 시 식별키. 원천 코드 고유성은 사전 검증(Q350).
- 보호소당 SHELTER 계정은 1개. 보호소 삭제 운영정책은 Q356 사용자 요청에 따라 질문 제외/보류.

#### 05. `shelter_members` — 보호소당 담당 계정 1명

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `shelter_member_seq` | `INTEGER` | Y | PK, IDENTITY | 관계 PK(※초안) |
| `shelter_seq` | `INTEGER` | Y | FK → shelters, **UNIQUE** | 보호소당 SHELTER 계정 최대 1개(Q229) |
| `user_seq` | `INTEGER` | Y | FK → users, UNIQUE | 담당자 1명당 보호소 1곳(확정) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- `shelter_seq UNIQUE`, `user_seq UNIQUE`로 보호소↔담당자 1:1. 담당 계정은 role=SHELTER.
- 소속 변경 관리 API·이력은 구현 제외.

#### 06. `shelter_district_assignments` — 시·군·구별 지정 보호소

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `district_code` | VARCHAR(20) | Y | PK | 시·군·구 행정구역 코드 |
| `shelter_seq` | INTEGER | Y | FK → shelters | 해당 행정구역의 지정 담당 보호소 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 매핑 등록일 |

**확정 규칙 / 참고사항**
- 시·군·구 단위 **`district_code` 단일 PK 유지**로 지정 보호소 1개(Q395). **복합 PK `(district_code,shelter_seq)`는 도입하지 않음.** 동일 보호소가 여러 구역을 담당할 수 있음(Q287·338).
- 발견 신고 등록 시 행정구역 매핑 우선, 미매핑이면 좌표상 가까운 보호소를 자동 선택(Q284~286). 삭제된 보호소 처리 엣지케이스는 Q356에서 스킵.

### 동물 기준정보·반려동물

#### 07. `animal_species` — 동물 종 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `species_seq` | `INTEGER` | Y | PK, IDENTITY | 종류 식별자 |
| `species_name` | `VARCHAR(50)` | Y | UNIQUE 후보 | 예: 개, 고양이, 기타 |
| `species_code` | `VARCHAR(30)` | N | ※초안, UNIQUE 검토 | 표준 코드/원천 매핑 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 종별 마스터. 실무 동물 데이터에서는 직접 종 FK 대신 유효 조합 ID를 사용.

#### 08. `animal_breeds` — 종별 품종 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `breed_seq` | `INTEGER` | Y | PK, IDENTITY | 품종 식별자 |
| `species_seq` | `INTEGER` | Y | FK → animal_species | 해당 품종이 속하는 종; 종·품종 조회를 위한 계층 |
| `breed_name` | `VARCHAR(100)` | Y | UNIQUE `(species_seq,breed_name)` 후보 | 말티즈, 코리안 숏헤어 등 |
| `breed_code` | `VARCHAR(60)` | N | ※초안 | 공공데이터·외부 사전 코드 매핑 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 각 품종은 정확히 하나의 종에 속함. 실제 종/품종/색상 매핑 목록은 팀에서 별도로 정의.

#### 09. `animal_colors` — 털색 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `color_seq` | INTEGER | Y | PK, IDENTITY | 색상 기준 ID |
| `color_name` | VARCHAR(100) | Y | UNIQUE 후보 | 색상 표기; 기타 포함 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 등록시각 |

**확정 규칙 / 참고사항**
- 색상 마스터; 기타 값을 실제 기준 데이터로 관리(Q335).

#### 10. `animal_combinations` — 허용 품종·색상 조합

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `combination_seq` | INTEGER | Y | PK, IDENTITY | 품종·색상 유효 조합 키 |
| `breed_seq` | INTEGER | Y | FK → animal_breeds | 종은 품종에서 간접 확인 |
| `color_seq` | INTEGER | Y | FK → animal_colors | 색상 선택값 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 등록시각 |

**확정 규칙 / 참고사항**
- `UNIQUE(breed_seq,color_seq)`로 유효 조합 중복 금지. 종은 animal_breeds를 통해 결정, combination에 별도의 species_seq 없음(Q334).
- 조합의 실제 행과 기타 처리 범위는 별도 마스터 시드에서 관리. Q336 관련 상세 질문은 스킵.

#### 11. `pets` — 일반회원 등록 반려동물

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `pet_seq` | `INTEGER` | Y | PK, IDENTITY | 회원 반려동물 ID |
| `user_seq` | `INTEGER` | Y | FK → users | 소유자 |
| `name` | `VARCHAR(100)` | Y | NOT NULL; BE 공백 금지 | 반려동물 이름/실종 당시 이름 스냅샷 |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `weight` | VARCHAR(30) | N | NULL 허용 | 반려동물 몸무게 선택 자유 텍스트 |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 자유형 특징: 색상·목줄·귀 모양 등 |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 등록 반려동물 소프트 삭제(연결 신고 유지) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `mod_usr_seq` 제거(Q396). 소유자인 `user_seq`가 수정 권한의 기준이며 `mod_dttm`으로 마지막 수정시각만 남김.
- USER만 생성/수정/삭제. 이름·종/품종/색상 조합·성별 필수. `weight`는 선택 자유 텍스트(Q362).
- 사진 1~3장(`pet_images`), 첫 사진 대표. 종전 `pets.image_seq`는 제거(Q293).
- 반려동물 소프트 삭제해도 `missing_reports.pet_seq` 및 기존 ACTIVE 신고와 AI 매칭을 유지(Q348).
- 마이크로칩 등록 여부는 별도 컬럼으로 두지 않음(Q305·364). 중성화 미상은 NULL로 표현.

#### 12. `pet_images` — 등록 반려동물 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `pet_image_seq` | BIGINT | Y | PK, IDENTITY | 반려동물 이미지 연결 식별자 |
| `pet_seq` | INTEGER | Y | FK → pets | 소속 등록 반려동물 |
| `image_seq` | BIGINT | Y | FK → images | 공통 이미지 참조 |
| `display_order` | SMALLINT | Y | UNIQUE `(pet_seq,display_order)` | 1번이 대표 이미지 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 등록시각 |

**확정 규칙 / 참고사항**
- `UNIQUE(pet_seq,display_order)`로 이미지 순서 중복 방지. 1~3장·동일 이미지 중복 방지는 BE.
- 기존 이미지가 실종 신고에 사용되면 images 레코드 및 저장소 키 재사용, 연결 행만 `missing_report_images`에 추가(Q349).

### 이미지 공통

#### 13. `images` — 공통 이미지 저장소 객체 메타데이터

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `image_seq` | `BIGINT` | Y | PK, IDENTITY | 이미지 공통 키 |
| `image_key` | `VARCHAR(512)` | Y | **UNIQUE(Q256)** | 저장소 객체 키(S3 등); 길이 ※초안 |
| `mime_type` | `VARCHAR(100)` | Y | - | MIME 종류; GIF·영상 업로드 불허 |
| `original_file_name` | `VARCHAR(255)` | N | ※초안 | 원본명(선택) |
| `byte_size` | `BIGINT` | N | CHECK `byte_size>=0` 후보 | 업로드 파일 크기(선택) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- `image_key UNIQUE`. 원본 이미지 객체는 S3 등 외부 저장소에 보관하고 DB에는 메타데이터 저장.
- 여러 도메인 첨부 관계가 하나의 `image_seq`를 참조할 수 있음. 이미지 연결을 해제할 때 **모든 연결 테이블과 직접 FK(`shelters.profile_image_seq` 등)의 참조가 사라진 경우에만** 공통 `images` 행과 S3 원본 객체를 삭제(Q383). 별도 참조 카운트 컬럼 없음. DB 변경/참조 확인은 트랜잭션·동시성 검증 대상이며 S3 삭제 실패 정리는 구현 검증 항목.
- JPG/JPEG/PNG, 파일당 10MB. 이미지 임베딩 상세 구조는 별도 보류.

### 실종·발견 신고

#### 14. `missing_reports` — 실종 신고 정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `missing_report_seq` | `INTEGER` | Y | PK, IDENTITY | 실종 신고 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `pet_seq` | `INTEGER` | N | FK → pets, NULL 허용 | 선택 연결; 반려동물 소프트 삭제 시 유지 |
| `name` | `VARCHAR(100)` | Y | NOT NULL; BE 공백 금지 | 반려동물 이름/실종 당시 이름 스냅샷 |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 자유형 특징·목줄 등 |
| `address` | `VARCHAR(255)` | Y | - | 마지막 목격/실종 지점 확정 주소 |
| `latitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 실종 위치 위도 |
| `longitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 실종 위치 경도 |
| `occurred_dttm` | `TIMESTAMPTZ` | Y | - | 실종 시각 |
| `status` | `VARCHAR(15)` | Y | NOT NULL DEFAULT ACTIVE; CHECK ACTIVE/CLOSED | CLOSED→ACTIVE 재개 가능. 종료 사유·일시 컬럼 없음 |
| `contact_phone` | `VARCHAR(30)` | Y | NOT NULL | 신고 시점 연락처 스냅샷; 회원 번호 기본 입력·수정 가능 |
| `is_phone_public` | BOOLEAN | Y | NOT NULL DEFAULT FALSE | 로그인 회원 대상 연락처 공개 여부 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `status=ACTIVE/CLOSED`; CLOSED→ACTIVE 재개 가능. 종료 사유/종료 시각은 별도 저장하지 않음(Q289).
- **비삭제 ACTIVE 신고는 회원당 최대 1건**(user_seq 기준 부분 UNIQUE; 기존 pet_seq 기반 중복 제약보다 강한 정책). CLOSED 신고는 보존하며 CLOSED→ACTIVE 재개나 신규 등록 시 회원의 다른 ACTIVE 신고가 없어야 함(Q399 선행 확인).
- `pet_seq`는 선택 참조. 등록 반려동물의 속성·이름은 신고 시 스냅샷으로 별도 관리. 동일 반려동물에 대해서도 해당 회원당 ACTIVE 1건 정책 및 소유자 검증을 적용.
- 위치 주소·좌표·실종 시각·이름·연락처 필수. 별도 `circumstances` 컬럼 없음(Q397; Q369 대체). 연락처는 등록 회원 기본번호 복사 후 별도 수정 가능.
- 연락처 공개는 기본 비공개. 공개 시 로그인 회원에게, 비공개 시 작성자/ADMIN에게 표시. 등록된 반려동물 삭제는 신고에 영향 없음.
- ACTIVE·비삭제·작성자 비탈퇴 신고만 AI 비교 및 알림 대상. 반환/발견 신고 종료로 자동 종료하지 않음.

#### 15. `missing_report_images` — 실종 신고 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `missing_report_image_seq` | `BIGINT` | Y | PK, IDENTITY | 첨부 행 PK(※초안) |
| `missing_report_seq` | `INTEGER` | Y | FK → missing_reports | 실종 신고 |
| `image_seq` | `BIGINT` | Y | FK → images | 공통 이미지 |
| `display_order` | `SMALLINT` | Y | **UNIQUE `(missing_report_seq,display_order)` (Q232)** | 사진 표시 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 1~3장 필수, 대표는 `display_order=1`. 이미지 중복 및 개수는 BE 검증. `UNIQUE(missing_report_seq,display_order)` 유지.

#### 16. `found_reports` — 발견 신고 정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `found_report_seq` | `INTEGER` | Y | PK, IDENTITY | 발견 신고 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `shelter_seq` | `INTEGER` | Y | FK → shelters; NOT NULL | 구역 매핑 우선/미매핑 시 최근접 자동 배정; 이후 ADMIN 수동 변경 |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 목줄 색·털색 등 외형 특징 |
| `reporter_phone` | `VARCHAR(30)` | Y | NOT NULL | 발견 신고 당시 연락처 스냅샷; 회원 번호 기본 입력 |
| `is_phone_public` | BOOLEAN | Y | NOT NULL DEFAULT FALSE | 로그인 회원 대상 신고자 연락처 공개 여부 |
| `landmark_description` | `TEXT` | N | ※잠정 | 위치 기준점/주변 설명(GPS와 중복 가능성 검토) |
| `address` | `VARCHAR(255)` | Y | - | 발견 장소 |
| `latitude` | `DOUBLE PRECISION` | Y | 범위 CHECK 후보 | 위도 |
| `longitude` | `DOUBLE PRECISION` | Y | 범위 CHECK 후보 | 경도 |
| `occurred_dttm` | `TIMESTAMPTZ` | Y | - | 발견/목격 시각 |
| `status` | `VARCHAR(15)` | Y | NOT NULL DEFAULT ACTIVE; CHECK ACTIVE/CLOSED | 종료/재개 가능; 구조 완료·반환과 자동 연동하지 않음 |
| `closed_reason` | `TEXT` | N | NULL 허용; 자유 서술, 별도 코드 CHECK 없음 | 종료 사유 선택 입력, ACTIVE에서 NULL |
| `closed_dttm` | `TIMESTAMPTZ` | N | CLOSED일 때 NOT NULL CHECK | 종료 일시, ACTIVE에서 NULL; 재개 시 NULL로 초기화 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 신고 시 `shelter_seq`를 자동 배정. 등록 뒤 주소 변경에 따른 자동 재배정 없음, ADMIN만 담당 보호소 수동 수정(Q288). 구조 기록의 기존 shelter_seq는 유지.
- ACTIVE이면 `closed_reason/closed_dttm` NULL, CLOSED이면 `closed_dttm` 필수·`closed_reason` 선택. 재개 시 종료 컬럼 초기화(Q289·290).
- 신고 등록/구조 접수·완료/직접 소유자 반환으로 상태 자동 종료되지 않음. 발견 신고 소프트 삭제는 연결 구조 기록을 수정하지 않음(Q342).
- 목격 상황 전용 `circumstances` 컬럼 제거(Q397). `description`은 동물의 외형적 특징 용도로 유지.
- 연락처·위치·일시 필수. 연락처 공개는 기본 비공개이며 비공개 시 작성자·ADMIN·담당 보호소 열람. `reporter_name` 별도 저장 없이 users.name 조회(Q332).
- ACTIVE·비삭제·작성자 비탈퇴 신고만 AI 신규 매칭 대상으로 포함(Q343).

#### 17. `found_report_images` — 발견 신고 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `found_report_image_seq` | `BIGINT` | Y | PK, IDENTITY | 첨부 행 PK(※초안) |
| `found_report_seq` | `INTEGER` | Y | FK → found_reports | 발견 신고 |
| `image_seq` | `BIGINT` | Y | FK → images | 공통 이미지 |
| `display_order` | `SMALLINT` | Y | **UNIQUE `(found_report_seq,display_order)` (Q232)** | 사진 정렬 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 1~3장 필수. `UNIQUE(found_report_seq,display_order)`; 첫 이미지 대표, 개수/중복은 BE 검증.

### 구조·보호·반환

#### 18. `rescue_records` — 보호소 구조 기록

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `rescue_seq` | `INTEGER` | Y | PK, IDENTITY | 구조 기록 PK |
| `shelter_seq` | `INTEGER` | Y | FK → shelters | 구조 담당 보호소 |
| `found_report_seq` | `INTEGER` | N | FK → found_reports, NULL 허용; 진행+완료 부분 UNIQUE | 발견 신고 연계 구조 또는 보호소 자체 접수 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PROCESSING; CHECK 4종 | PROCESSING / DONE / FAILED / CANCELED |
| `completed_dttm` | `TIMESTAMPTZ` | N | PROCESSING에서 NULL, DONE/FAILED/CANCELED에서 NOT NULL(CHECK) | 처리 완료·실패·취소 시각 |
| `rescued_date` | `DATE` | N | DONE 시 NOT NULL CHECK | 실제 구조 날짜; 진행 중에는 NULL |
| `rescue_reason` | `TEXT` | N | - | 구조 사유 |
| `rescue_address` | VARCHAR(255) | N | DONE 시 NOT NULL 및 공백 금지 CHECK | 실제 구조 장소 |
| `rescue_memo` | TEXT | N | NULL 허용 | 구조 관련 자유 메모 |
| `result_reason` | `TEXT` | N | FAILED/CANCELED 시 비공백 필수 CHECK | 구조 실패·취소 사유; 구조 사유와 구분 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 보호소가 발견 신고를 구조 대상으로 선택하면 즉시 PROCESSING 행 생성. 신고 없이 직접 생성도 허용(`found_report_seq NULL`)(Q294~297).
- 동일 발견 신고의 비삭제 PROCESSING/DONE 합계 최대 1개(부분 UNIQUE). FAILED/CANCELED 이후 재시도 가능. **접수/등록 시각은 `reg_dttm`**으로 통일하고 `received_dttm` 컬럼은 생성하지 않음(Q384).
- DONE에서 `rescued_date`와 비공백 `rescue_address` 필수(DB CHECK). FAILED/CANCELED에서는 비공백 `result_reason` 필수(DB CHECK)(Q357·360). `status=PROCESSING`에서는 `completed_dttm IS NULL`, 종료 상태 세 종류는 `completed_dttm IS NOT NULL`(Q384).
- 완료 구조 기록 1개로 보호동물 여러 마리 등록 가능. 보호동물이 연결된 구조 기록은 삭제 제한(Q298·353).
- 발견 신고가 삭제되어도 구조 기록·FK·진행 상태 그대로 보존, 구조 진행 가능(Q342). 구조 기록의 shelter_seq와 보호동물의 shelter_seq가 달라도 연결 허용(Q354).

#### 19. `shelter_animals` — 보호동물 및 보호·입양 상태

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `animal_seq` | `INTEGER` | Y | PK, IDENTITY | 보호동물 단위 내부 식별자; 보호소 간 이관 기능 없음 |
| `shelter_seq` | `INTEGER` | Y | FK → shelters | 현재 보호소 |
| `rescue_seq` | `INTEGER` | N | FK → rescue_records, NULL 허용; UNIQUE 없음 | DONE 구조 기록만 연결; 연결된 구조 날짜/사유/주소/메모는 JOIN 조회 |
| `completed_application_seq` | `BIGINT` | N | UNIQUE 및 `(completed_application_seq,animal_seq)` 복합 FK | 서비스 내 실제 입양 완료 신청서; 외부 입양 NULL |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 외형·성격 등 설명 |
| `health_check` | TEXT | N | NULL 허용 | 보호동물 건강검진 정보 자유 텍스트 |
| `vaccination_status` | TEXT | N | NULL 허용 | 접종 상태 자유 텍스트 |
| `notice_start_date` | `DATE` | Y | NOT NULL | 직접 등록 기본값: 등록일; 공공데이터 초기 적재 시 적재자가 날짜 결정·입력 |
| `notice_end_date` | `DATE` | Y | NOT NULL; CHECK 시작≤종료 | 직접 등록 기본값: 시작+10일; 공공데이터 초기 적재 시 적재자가 날짜 결정·입력; 자동 상태 변경 없음 |
| `protection_status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PROTECTING; CHECK 4종 | PROTECTING/RETURNED/ADOPTED/ENDED |
| `end_reason` | `VARCHAR(20)` | N | CHECK `RETURNED/ADOPTED/NATURAL_DEATH/EUTHANIZED/OTHER` | 보호 종료 사유(확정) |
| `protection_ended_dttm` | `TIMESTAMPTZ` | N | - | 보호 종료 일시 |
| `adoption_status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT UNAVAILABLE; 보호 상태 조합 CHECK | AVAILABLE/UNAVAILABLE/ADOPTED; 초기 입양 불가 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 기본 `PROTECTING`+`UNAVAILABLE`. 보호 상태 PROTECTING/RETURNED/ADOPTED/ENDED와 입양 상태의 허용 조합은 CHECK(Q258).
- 보호소 **직접 등록의 기본 규칙**은 등록일을 공고 시작일로 사용하고 종료일은 시작+10일로 저장(NOT NULL). 초기 공공데이터 적재 시에는 날짜를 적재자가 직접 정해 넣으며, 원천 날짜 우선순위는 별도로 확정하지 않음(Q388). 공통 CHECK는 시작≤종료. 날짜 경과로 상태를 자동 변경하지 않음. 공고 기간 중 수동 AVAILABLE 전환 가능(Q292·330).
- **구조 정보 4종은 복사하지 않고 `rescue_seq`로 `rescue_records`를 LEFT JOIN해 조회(Q404)**. 직접 등록/공공데이터 초기 적재 시 `rescue_seq=NULL` 가능하며 그 경우 구조 날짜·사유·장소·메모는 NULL로 표시(Q405). 동일 구조 기록에 복수 동물 허용. 과거 Q352·361의 복사·독립 수정 정책은 대체됨.
- 구조 담당 보호소와 보호동물 현재 보호소는 달라도 허용(Q354). 이관 기능 자체는 구현 제외(Q283).
- 보호동물 1~3장, 이름 컬럼 없음. 상태가 PROTECTING이며 비삭제면 입양 가능 여부에 무관하게 AI 매칭 대상(Q344).
- 실제 반환·입양/보호 종료에서 신청서 자동 REJECTED 등 최신 규칙 적용. 활성 DRAFT/SUBMITTED/APPROVED 신청서가 존재하면 보호동물 소프트 삭제 요청 거부(Q355).
- 건강검진/접종 정보는 `health_check`, `vaccination_status` 자유 입력. `completed_application_seq`는 같은 동물의 최종 입양 신청서만 참조하며 외부 입양은 NULL.

#### 20. `shelter_animal_images` — 보호동물 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `shelter_animal_image_seq` | `BIGINT` | Y | PK, IDENTITY | 연결 행 PK |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals | 보호동물 |
| `image_seq` | `BIGINT` | Y | FK → images | 공통 이미지 |
| `display_order` | `SMALLINT` | Y | **UNIQUE `(animal_seq,display_order)` (Q232)** | 표시 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 1~3장 필수. 첫 이미지 대표, `(animal_seq,display_order)` UNIQUE. 사진 수정 시 자격 대상에 한해 AI 재매칭.

#### 21. `animal_returns` — 실제 보호동물 반환 완료

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `return_seq` | `BIGINT` | Y | PK, IDENTITY · ※초안 | 반환 완료 기록 ID |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals; UNIQUE | 보호동물당 완료 반환 1건 |
| `returned_user_seq` | `INTEGER` | N | FK → users, NULL 허용 | 활성 사용자 전화번호와 정확히 1명 일치할 때만 자동 연결; 신원 검증 의미 없음 |
| `recipient_name` | VARCHAR(100) | Y | NOT NULL | 실제 반환받는 사람의 이름(회원/비회원 공통) |
| `recipient_phone` | VARCHAR(30) | Y | NOT NULL | 실제 반환받는 사람의 전화번호 스냅샷 |
| `reg_usr_seq` | `INTEGER` | N | FK → users · ※초안 | 기록자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 기록 생성 시각=반환 완료 처리 시각(Q403) |

**확정 규칙 / 참고사항**
- 별도 `returned_dttm` 없이 `reg_dttm`을 반환 완료 시각으로 사용; 소급 입력 없이 완료 시점에 기록(Q403).
- 실제 보호자가 인계받은 완료 사건만 기록하며 `animal_seq UNIQUE`. 반환 등록·보호 상태 RETURNED 변경은 동일 트랜잭션.
- 반환받은 사람의 이름/전화번호는 필수, 회원 여부와 독립 보관(Q309·368).
- 해당 전화번호를 가진 비삭제 USER가 정확히 1명일 경우 `returned_user_seq` 연결, 아니면 NULL. 본인 확인/소유권 검증 의미는 없음(Q310).
- 실종 신고 FK·실종 신고 자동 종료 없음. 발견 신고 단계에서 직접 반환된 사건은 해당 신고 자체의 상태로 관리.

### 입양 신청·서식·심사

#### 22. `adoption_form_questions` — 현재 공통 21개 질문/선택지·조건

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `question_seq` | `INTEGER` | Y | PK, IDENTITY | 문항 ID |
| `question_text` | `TEXT` | Y | - | 문항 문구 |
| `question_type` | `VARCHAR(30)` | Y | NOT NULL CHECK TEXT/SINGLE_CHOICE/MULTI_CHOICE | 주관식/단일 선택/복수 선택 |
| `is_required` | `BOOLEAN` | Y | DEFAULT FALSE 후보 | 필수 답변 여부 |
| `display_order` | `INTEGER` | Y | UNIQUE `(display_order)` 후보 | 문항 순서 |
| `options_jsonb` | `JSONB` | N | 선택형 질문의 문자열 배열(JSONB) | 보기 문구 직접 저장(별도 선택지 테이블 없음) |
| `condition_jsonb` | JSONB | N | NULL 허용; FE/BE 검증 | 조건부 문항 규칙: depends_on(질문 `question_seq`)/operator/value/required_when_matched |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 서비스 공통 현재 문항 21개만 유지; `adoption_form_templates` 및 템플릿·버전 관리 없음(Q402). TEXT/SINGLE_CHOICE/MULTI_CHOICE 3종. 문항과 보기 수정 시 해당 현재 행을 갱신하며 이전 질문·보기·버전 이력은 저장하지 않음.
- `options_jsonb`는 표시 문자열 배열. **`question_seq`가 답변 JSONB의 키**이고, 화면의 Q1~Q21 번호는 `display_order`로 표시. 질문을 교체할 때 기존 행의 `question_seq`를 유지해야 과거 답변과의 식별 연결이 유지됨(Q402 추가 결정; 과거 Q321 대체).
- `condition_jsonb`의 질문 의존성은 `question_seq`로 참조. 조건부 노출·필수 검증은 FE/BE에서 수행. 숨겨진 응답은 제거; DB CHECK/트리거 추가 없음(Q322·323).
- 질문 변경 이력과 제출 당시 문항 스냅샷을 저장하지 않으므로 **기존 제출 답변도 현재 질문 문구와 함께 표시**. 질문 의미 변경 시 과거 답변의 의미가 달라 보일 수 있는 위험을 수용한 정책(Q402 추가 결정).

#### 23. `adoption_applications` — 신청서 및 JSONB 답변

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `application_seq` | `BIGINT` | Y | PK, IDENTITY | 실제 입양 신청 PK |
| `user_seq` | `INTEGER` | Y | FK → users | 신청자 |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals | 대상 보호동물 |
| `shelter_seq` | `INTEGER` | Y | FK → shelters, **신청 시점 스냅샷(Q214·250)** | 신청 당시 보호소; 이관 후 변경하지 않음 |
| `answers_jsonb` | JSONB | Y | NOT NULL DEFAULT `{}` | 질문별 응답을 문자열화한 `question_seq` 키로 저장(조건부 미응답 문항은 생략 가능) |
| `certificate_storage_key` | TEXT | N | NULL 허용 | 선택 교육 수료증 이미지/PDF 저장소 키, 1건만 |
| `applicant_name` | VARCHAR(100) | N | SUBMITTED부터 스냅샷 사용 | 제출 당시 신청자 실명; DRAFT는 실시간 회원 프로필 |
| `applicant_birth_date` | DATE | N | SUBMITTED부터 스냅샷 사용 | 제출 당시 신청자 생년월일 |
| `applicant_phone` | VARCHAR(30) | N | SUBMITTED부터 스냅샷 사용 | 제출 당시 신청자 전화번호 |
| `status` | `VARCHAR(30)` | Y | NOT NULL, CHECK `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` | DRAFT→SUBMITTED→APPROVED→ADOPTED 또는 REJECTED/CANCELED |
| `submitted_dttm` | `TIMESTAMPTZ` | N | - | 제출 시각 |
| `reviewed_dttm` | `TIMESTAMPTZ` | N | APPROVED/REJECTED 시 필수 | 가장 최근 승인·거절 처리시각; 자동 거절 시각으로 덮어씀 |
| `reviewed_usr_seq` | `INTEGER` | N | FK → users, NULL 허용 | 수동 처리자; 시스템 자동 거절에서는 NULL |
| `rejection_reason` | `TEXT` | N | REJECTED에서 필수; BE 비공백 검증 | 공개 가능한 자연어 거절 사유 문장(코드·별도 거절 단계 없음) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 기존 신청서는 유지; DRAFT 삭제 요청 시 실제 행과 답변 하드 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 신청자 역할 USER만 허용. `answers_jsonb`에 전체 응답 1개 JSONB 객체 저장; 키는 `adoption_form_questions.question_seq`의 문자열. `adoption_form_answers`와 `adoption_form_templates` 테이블은 없음(Q276·402).
- 동일 `(user_seq,animal_seq)`의 비삭제 상태 DRAFT/SUBMITTED/APPROVED *통틀어* 최대 1건(부분 UNIQUE; Q264·363). DRAFT→SUBMITTED는 같은 행 수정.
- DRAFT는 빈/미완성 응답 허용; 제출시 **현재 질문 21개**와 조건부 필수 문항을 BE 검증. 제출 후 응답 불변. DRAFT 삭제는 물리 DELETE 및 해당 답변도 함께 제거(Q269).
- 질문의 버전이나 제출 당시 질문 문구는 저장하지 않으며, 과거 제출 답변도 **현재 질문 문구로 조회**(Q402 A). 반면 신청자 인적 사항은 기존 정책대로 제출 시 `applicant_name/birth_date/phone` 스냅샷을 보존. DRAFT 작성 시에는 실시간 users 프로필 사용(Q325).
- 수료증은 선택 1장(또는 PDF) JPG/PNG/PDF 최대 10MB, `certificate_storage_key`만 저장(Q277~281).
- SUBMITTED 상태만 신청자 직접 취소 가능; CANCELED는 취소 사유 컬럼 및 이력 사유 없음(Q326).
- `APPROVED`는 서류 승인, `ADOPTED`는 실제 입양 완료. 별도 `adoption_processes` 없이 이 신청서 한 행에서 통합 입양 프로세스를 관리(Q375). 동일 동물의 다른 사용자들도 APPROVED 가능. REJECTED 이유는 **구분 가능한 자연어 텍스트**로 기록하며 별도 상태 이력·거절 종류·단계 컬럼을 생성하지 않음(Q376·377).
- 보호동물 RETURNED/ADOPTED/ENDED 시 비완료 활성 신청서 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED. 시스템이 상황에 맞는 실제 한국어 `rejection_reason` 문장을 생성하고 `reviewed_dttm=자동 거절 시각`, `reviewed_usr_seq=NULL`로 저장(Q372·376). 기존 APPROVED에서 자동 REJECTED가 되면 이전 승인 처리시각은 별도 보존하지 않음(Q376·377). 회원 탈퇴 시 본인 활성 신청서는 CANCELED, 이미 ADOPTED는 보존(Q318).
- 기존 활성 동물의 단순 AVAILABLE→UNAVAILABLE에서는 신규 신청만 차단하고 기존 신청은 유지. 거절/취소된 뒤 동물 입양 가능 시 재신청은 신규 행(Q271).

### 채팅

#### 24. `chat_rooms` — 일반회원·보호소 채팅방

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `chat_room_seq` | `BIGINT` | Y | PK, IDENTITY | 방 ID |
| `room_type` | `VARCHAR(15)` | Y | CHECK `USER/SHELTER` | 방 종류 |
| `user_a_seq` | `INTEGER` | Y | FK → users | 공통 사용자 측(※초안) |
| `user_b_seq` | `INTEGER` | N | FK → users | USER형 상대방 |
| `shelter_seq` | `INTEGER` | N | FK → shelters | SHELTER형 상대 보호소 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- USER↔USER 또는 USER↔SHELTER만. 동일 상대 조합에 방 1개; 개인간 ID는 오름차순으로 저장하고 유형별 부분 UNIQUE.
- 보호소당 담당 계정 1개. 보호소↔보호소 채팅 없음. 참여자가 탈퇴해도 채팅방 유지.

#### 25. `chat_participants` — 채팅방 읽음·나가기 기준

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `chat_participant_seq` | `BIGINT` | Y | PK, IDENTITY | 참여 행 키(※초안) |
| `chat_room_seq` | `BIGINT` | Y | FK → chat_rooms | 채팅방 |
| `participant_type` | `VARCHAR(15)` | Y | CHECK `USER/SHELTER` | 참여 주체 유형 |
| `participant_user_seq` | `INTEGER` | N | FK → users | 사용자 참여자 |
| `participant_shelter_seq` | `INTEGER` | N | FK → shelters | 보호소 참여자 |
| `last_read_message_seq` | `BIGINT` | N | **복합 FK (방+메시지)(Q233)** | 읽음 기준 마지막 메시지 |
| `last_hidden_message_seq` | `BIGINT` | N | **복합 FK (방+메시지)(Q233)** | 나간 뒤 보이지 않을 과거 메시지 기준 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 채팅방 양쪽 참여자 행을 같이 생성하며 방의 주체와 일치하는지 BE 검증. 사용자별 마지막 읽음·숨김 메시지 참조.
- 읽음/숨김 포인터는 같은 방에 소속된 메시지만 가리키도록 복합 FK 적용(Q233).
- 채팅방 나가기 후 재입장은 같은 방을 사용하되 이전 메시지 비노출. 미읽음 수는 조회 시 계산.

#### 26. `chat_messages` — 텍스트·이미지·게시물 공유 메시지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `chat_message_seq` | `BIGINT` | Y | PK, IDENTITY | 메시지 키 |
| `chat_room_seq` | `BIGINT` | Y | FK → chat_rooms | 소속 채팅방 |
| `sender_user_seq` | `INTEGER` | Y | FK → users | 전송한 실제 계정(보호소 담당자 포함) |
| `message_type` | `VARCHAR(20)` | Y | CHECK TEXT/IMAGE/SHARED_POST | 메시지 종류, 유형별 payload는 BE 검증 |
| `body` | `TEXT` | N | - | 본문(이미지·공유 시 선택 가능) |
| `image_seq` | `BIGINT` | N | FK → images | IMAGE 메시지의 이미지 최대 1장(Q401) |
| `shared_target_type` | `VARCHAR(30)` | N | SHARED_POST 시 `MISSING/FOUND/SHELTER_ANIMAL` (Q387) | 공유 게시물 종류; 대상 코드는 공통 정의 사용 |
| `shared_target_seq` | `BIGINT` | N | ※초안 | 공유 원본 PK; 다형성 참조 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 이미지 전용 `chat_message_images` 테이블은 제거(Q401). 직접 FK `image_seq`의 NULL 여부로 이미지 첨부를 표현. TEXT(비공백 본문), IMAGE(이미지 정확히 1장; body는 선택), SHARED_POST(공유 대상 ID) 지원. `shared_target_type`에는 게시물 대상 코드 `MISSING/FOUND/SHELTER_ANIMAL`만 사용(Q387). 유형별 payload·권한·존재 검증은 BE. 개별 메시지 삭제 미지원.
- 공유 게시물이 삭제되어도 메시지는 유지하고 삭제 안내 표시. 탈퇴 사용자의 과거 대화/이미지는 보존·표시명 대체(Q314).
- 상대 계정이 탈퇴했으면 신규 메시지는 거부; 별도 채팅방 CLOSED 컬럼 없음(Q341).

### 댓글·답글

#### 27. `missing_report_comments` — 실종 신고 댓글·답글

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `missing_report_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 PK |
| `missing_report_seq` | `INTEGER` | Y | FK → missing_reports | 댓글 대상 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `parent_comment_seq` | `BIGINT` | N | **복합 FK (`parent_comment_seq`, `missing_report_seq`) → missing_report_comments(`missing_report_comment_seq`, `missing_report_seq`)(Q230)** | NULL 루트 / 값 있으면 1-depth 답글 |
| `body` | `TEXT` | N | **NULL 허용(Q228 A)** | 본문; 이미지만 있는 댓글 가능 |
| `image_seq` | `BIGINT` | N | FK → images | 댓글 이미지 최대 1장(Q400) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 루트 댓글 소프트 삭제 시 자식 답글은 유지, 삭제 안내 표시 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 이미지 연결 전용 테이블 없이 `image_seq NULL` 직접 FK 사용(Q400). 댓글 본문·이미지 중 적어도 하나는 있어야 함(BE 검증).
- 루트/답글 최대 깊이 1. 부모와 답글은 동일 게시물을 참조하도록 복합 FK. 내용/이미지 중 최소 1개 필수.
- 루트 댓글 삭제 시 해당 댓글만 is_deleted=TRUE, 기존 자식 답글은 보존하고 삭제 문구 표시(Q340).
- 회원 탈퇴 시 댓글 내용·답글은 유지하되 탈퇴한 루트 댓글에는 신규 답글 제한(Q316~317).

#### 28. `found_report_comments` — 발견 신고 댓글·답글

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `found_report_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 PK |
| `found_report_seq` | `INTEGER` | Y | FK → found_reports | 댓글 대상 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `parent_comment_seq` | `BIGINT` | N | **복합 FK (`parent_comment_seq`, `found_report_seq`) → found_report_comments(`found_report_comment_seq`, `found_report_seq`)(Q230)** | NULL 루트 / 값 있으면 1-depth 답글 |
| `body` | `TEXT` | N | **NULL 허용(Q228 A)** | 본문; 이미지만 있는 댓글 가능 |
| `image_seq` | `BIGINT` | N | FK → images | 댓글 이미지 최대 1장(Q400) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 루트 댓글 소프트 삭제 시 자식 답글은 유지, 삭제 안내 표시 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 이미지 연결 전용 테이블 없이 `image_seq NULL` 직접 FK 사용(Q400). 댓글 본문·이미지 중 적어도 하나는 있어야 함(BE 검증).
- 실종 댓글과 동일한 1-depth/복합 FK 정책. 루트 소프트 삭제 시 답글 보존, 탈퇴 댓글에 신규 답글 금지.

#### 29. `shelter_animal_comments` — 보호동물 댓글·답글

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `shelter_animal_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 PK |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals | 댓글 대상 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `parent_comment_seq` | `BIGINT` | N | **복합 FK (`parent_comment_seq`, `animal_seq`) → shelter_animal_comments(`shelter_animal_comment_seq`, `animal_seq`)(Q230)** | NULL 루트 / 값 있으면 1-depth 답글 |
| `body` | `TEXT` | N | **NULL 허용(Q228 A)** | 본문; 이미지만 있는 댓글 가능 |
| `image_seq` | `BIGINT` | N | FK → images | 댓글 이미지 최대 1장(Q400) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 루트 댓글 소프트 삭제 시 자식 답글은 유지, 삭제 안내 표시 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 이미지 연결 전용 테이블 없이 `image_seq NULL` 직접 FK 사용(Q400). 댓글 본문·이미지 중 적어도 하나는 있어야 함(BE 검증).
- 실종 댓글과 동일한 1-depth/복합 FK 정책. 보호소 담당자도 `user_seq`로 작성하며 표시명은 보호소명 조회.

### 신고·북마크·알림

#### 30. `reports` — 회원/게시물/댓글 통합 신고

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `report_seq` | `BIGINT` | Y | PK, IDENTITY · ※초안 | 통합 신고 ID |
| `reporter_user_seq` | `INTEGER` | Y | FK → users | 신고 접수한 회원 |
| `target_type` | `VARCHAR(40)` | Y | CHECK: USER/MISSING/FOUND/SHELTER_ANIMAL 및 3종 COMMENT | 공통 대상 타입(Q387) |
| `target_seq` | `BIGINT` | Y | 다형성 참조 | 대상 PK(사용자, 콘텐츠, 댓글) |
| `reason_detail` | `TEXT` | Y | NOT NULL, 공백 금지 | 신고 사유 자유 입력, 코드형 사유 없음 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PENDING; CHECK PENDING | 관리자 심사 기능 제외; 단일 상태만 사용 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users · ※초안 | 마지막 수정 주체(관리자 심사 기능 없음) |
| `mod_dttm` | `TIMESTAMPTZ` | N | ※초안 | 마지막 처리시각 |

**확정 규칙 / 참고사항**
- 신고 대상은 USER, MISSING, FOUND, SHELTER_ANIMAL 및 3종 댓글. 보호소/보호소 계정 자체는 신고 불가. 채팅 메시지 직접 신고 없음.
- 신고 사유는 자유 텍스트 `reason_detail` 비공백 필수. `reason_code` 삭제(Q308).
- 현재 `status=PENDING`만 사용하며 관리자 신고 심사/제재 구현 제외. `(reporter_user_seq,target_type,target_seq)` 전역 UNIQUE로 같은 대상 재신고 불가(Q307).
- `user_blocks` 테이블과 자동 차단/수동 차단 기능 일체 제외(Q306).

#### 31. `bookmarks` — 게시물 북마크

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `bookmark_seq` | `BIGINT` | Y | PK, IDENTITY | 북마크 PK(※초안) |
| `user_seq` | `INTEGER` | Y | FK → users | 저장 사용자 |
| `target_type` | `VARCHAR(30)` | Y | CHECK MISSING/FOUND/SHELTER_ANIMAL | 공통 대상 타입(Q387) |
| `target_seq` | `BIGINT` | Y | 다형성 참조 | 저장 대상 ID |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 저장 시각 |

**확정 규칙 / 참고사항**
- 실종/발견/보호동물만 지원. `(user_seq,target_type,target_seq)` UNIQUE. 해제는 물리 DELETE.
- 원본 삭제·작성자 탈퇴 시 목록 비노출. CLOSED 신고·ADOPTED 보호동물은 북마크 유지.

#### 32. `notifications` — 일반·AI·업무 알림

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `notification_seq` | `BIGINT` | Y | PK, IDENTITY | 일반 알림 PK |
| `recipient_user_seq` | `INTEGER` | Y | FK → users | 수신 사용자별 알림 1행 |
| `notification_type` | `VARCHAR(40)` | Y | NOT NULL; CHECK COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM | 기능별 알림 5종(일반 시스템 공지 포함, REPORT 미추가) |
| `actor_user_seq` | `INTEGER` | N | FK → users | 이벤트 행위자(없으면 NULL) |
| `message` | `TEXT` | Y | NOT NULL | 알림 사용자 노출 문구; DB 저장값과 FCM body 문자열 동일(Q381) |
| `target_type` | `VARCHAR(40)` | N | NULL 허용, 공통 대상 코드(Q387) | 클릭 시 이동 대상 종류; AI_MATCH에서는 매칭 상대 FOUND/SHELTER_ANIMAL |
| `target_seq` | `BIGINT` | N | NULL 허용, 다형성 참조(Q391) | 클릭 시 이동 대상 ID; AI_MATCH에서는 매칭 상대 PK |
| `missing_report_seq` | `INTEGER` | N | FK → missing_reports, NULL 허용 | AI 매칭 기준 신고; 중복 검사 키(BE) |
| `is_read` | `BOOLEAN` | Y | DEFAULT FALSE | 읽음 여부 |
| `read_dttm` | `TIMESTAMPTZ` | N | ※초안 | 읽은 시각 |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 개별/전체 알림 삭제 소프트 삭제; 숨김 기능 없음 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 발생/저장 시각 |

**확정 규칙 / 참고사항**
- 사용자별 행 저장. 알림 유형은 `COMMENT`, `AI_MATCH`, `ADOPTION`, `SHELTER`, `SYSTEM` **5개**(Q398; Q392 스킵 대체). `SYSTEM`은 서비스 점검·운영 공지 등 일반 시스템 알림, `REPORT`는 미추가. `SHELTER`는 보호소 고유 업무(담당 발견 신고 배정 등)용으로, 보호소에 수신되는 모든 알림을 뜻하지 않음. AI 매칭은 실종 신고 작성자에게만 전송/저장(Q345).
- AI 중복 기준 `(missing_report_seq,target_type,target_seq)` 및 `notification_type='AI_MATCH'`은 생애 최초 1회이며, 소프트 삭제된 알림도 중복 검사에 포함(Q291). **전용 UNIQUE 인덱스는 생성하지 않음**(Q389). BE는 관련 `missing_reports` 행에 `SELECT ... FOR UPDATE` 잠금을 획득한 후 기존 알림 조회·삽입을 하나의 트랜잭션으로 처리(Q390). AI_MATCH에서 `missing_report_seq`와 `target_type`, `target_seq`는 세 값 모두 필수이며 `target_type`은 `FOUND`/`SHELTER_ANIMAL`만 허용하도록 BE 검증. 그 외 유형의 `missing_report_seq`는 NULL. AI 매칭 상대 전용 `matched_target_type/seq`는 제거(Q399).
- 알림 개별/전체 삭제는 `is_deleted=TRUE`로 변경, 숨김 별도 기능 없음(Q339). `target_type/target_seq`는 둘 다 NULL 허용(Q391); 연결 대상이 있는 경우 BE에서 값의 완결성 및 존재·권한을 검증(Q382). 알림의 `ADOPTION` 대상은 `adoption_applications.application_seq`로 통합 입양 프로세스 화면을 지정(Q387).
- 탈퇴한 사용자가 발생시킨 기존 알림은 삭제하지 않고 표기만 대체. `message`에 **BE가 생성한 정확히 같은 문자열을 DB에 저장하고 FCM 푸시 body로 재사용**(Q381). DB 저장 확정 후 FCM 발송하며, 실패해도 DB 알림은 보존하고 재시도하지 않음. 채팅 푸시 이력은 저장하지 않음.

#### 33. `device_tokens` — FCM 기기 토큰

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `device_token_seq` | `BIGINT` | Y | PK, IDENTITY | 토큰 등록 키 |
| `user_seq` | `INTEGER` | Y | FK → users | 토큰 소유 사용자 |
| `fcm_token` | `TEXT` | Y | **활성 토큰 부분 UNIQUE(Q249)** | 실제 FCM 토큰 |
| `is_active` | `BOOLEAN` | Y | NOT NULL DEFAULT TRUE; 사용자/토큰 활성 부분 UNIQUE | 새 로그인 시 기존 토큰 비활성화; 탈퇴 시 모두 FALSE |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 최초 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 갱신/비활성화 시각 |

**확정 규칙 / 참고사항**
- 사용자당 활성 토큰 1개 + 동일 토큰을 여러 계정에서 활성화 금지(각각 부분 UNIQUE).
- 새 기기 로그인 시 기존 토큰 비활성화. 회원 탈퇴 시 본인 활성 토큰 모두 비활성화(Q327). FCM 재시도/발송 로그 테이블 없음.

## 4. 다형성 참조의 공통 코드 및 테이블별 범위

> Q387 A안. 공통 코드 문자열은 BE의 공통 Enum 등에서 관리하며 DB에서는 테이블별로 필요한 코드에 한정해 CHECK를 적용합니다. 다형성 `target_seq`가 실제 대상 레코드에 존재하고 접근 가능한지는 BE가 확인하며 일반 FK가 아닙니다.

| 공통 코드 | 참조 대상 | 대상 PK 예시 |
|---|---|---|
| `USER` | 일반회원 | `users.user_seq` |
| `MISSING` | 실종 신고 | `missing_reports.missing_report_seq` |
| `FOUND` | 발견 신고 | `found_reports.found_report_seq` |
| `SHELTER_ANIMAL` | 보호동물 | `shelter_animals.animal_seq` |
| `ADOPTION` | 입양 신청부터 완료까지 통합한 프로세스 | `adoption_applications.application_seq` |
| `MISSING_COMMENT` | 실종 신고 댓글 | `missing_report_comments.missing_report_comment_seq` |
| `FOUND_COMMENT` | 발견 신고 댓글 | `found_report_comments.found_report_comment_seq` |
| `SHELTER_ANIMAL_COMMENT` | 보호동물 댓글 | `shelter_animal_comments.shelter_animal_comment_seq` |

| 참조 테이블/컬럼 | 허용 코드 | 비고 |
|---|---|---|
| `reports.target_type` | USER, MISSING, FOUND, SHELTER_ANIMAL, MISSING_COMMENT, FOUND_COMMENT, SHELTER_ANIMAL_COMMENT | `ADOPTION` 및 보호소 자체 신고 없음 |
| `bookmarks.target_type` | MISSING, FOUND, SHELTER_ANIMAL | 북마크 대상 게시물만 |
| `chat_messages.shared_target_type` | MISSING, FOUND, SHELTER_ANIMAL | `SHARED_POST` 전용 |
| `notifications.target_type` | 공통 8종 중 서비스가 연결하는 대상 | NULL 허용, `ADOPTION`은 신청서 PK로 이동; 유형별 이동 정책은 BE 판단 |

- **주의:** `notifications.notification_type=ADOPTION`(알림의 기능 분류)과 `notifications.target_type=ADOPTION`(이동 대상 종류)은 서로 다른 의미의 컬럼입니다.
- Q391에 의해 알림 대상 두 컬럼의 **NULL 허용**을 유지합니다. 두 컬럼이 한쪽만 NULL인 경우를 막기 위한 별도 DB CHECK는 확정하지 않았으며, BE에서 유효한 대상 쌍을 관리합니다.
- Q392에서 보류했던 일반 시스템 알림은 **Q398로 변경**되었습니다. `SYSTEM`은 서비스 점검·운영 공지용으로 **현행 5개 알림 유형에 포함**하고, `REPORT`와 별도 신고 접수 알림 이벤트는 추가하지 않습니다.

## 5. 상태·생명주기 및 자동 변경 정책

### 신고·구조
- 실종·발견 신고: `ACTIVE ↔ CLOSED` 재개 허용. **회원당 비삭제 ACTIVE 실종 신고는 최대 1건**; 과거 CLOSED 신고는 유지하고 새 신고를 생성할 수 있음. 실종에는 종료 사유/일시 컬럼 없음; 발견은 CLOSED에 종료 일시 필수·사유 선택.
- 구조 기록: `PROCESSING → DONE / FAILED / CANCELED`; `DONE`에서 날짜·주소 필수, `FAILED/CANCELED`에서 결과 사유 필수. 완료 구조 기록을 연결해야 보호동물 신규 등록 가능.
- 발견 신고 삭제/종료는 진행 중 구조에 영향을 주지 않음. 보호동물 반환·입양·보호 종료는 실종/발견 신고를 자동 종료하지 않음.

### 보호·입양
- 보호 상태 `PROTECTING/RETURNED/ADOPTED/ENDED`. 입양 상태 `AVAILABLE/UNAVAILABLE/ADOPTED`. PROTECTING→AVAILABLE/UNAVAILABLE; RETURNED/ENDED→UNAVAILABLE; ADOPTED→ADOPTED.
- 입양 신청 상태는 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` **6종**. 입양 문서 승인(APPROVED)과 실제 최종 입양(ADOPTED)은 구분하며 `CLOSED`는 없음(Q371).
- 입양 완료 시 최종 채택 신청서 상태 ADOPTED, 해당 동물 `protection_status=ADOPTED`, `adoption_status=ADOPTED`, `completed_application_seq` 설정을 같은 트랜잭션에 반영.
- 타 신청자의 DRAFT/SUBMITTED/APPROVED는 실제 입양 완료 후 REJECTED. 보호 상태 RETURNED/ENDED에도 활성 신청서는 REJECTED. 시스템은 자연어 `rejection_reason`을 기록하며 `reviewed_dttm`에 자동 거절 시각, `reviewed_usr_seq=NULL` 저장; 상태 이력/거절 단계 컬럼은 없음(Q372·376·377).
- 회원 탈퇴 시 자신의 활성 DRAFT/SUBMITTED/APPROVED는 CANCELED; 취소 사유는 별도 보존하지 않음. 이미 ADOPTED 신청은 보존.
- 승인 후 최종 입양을 진행하는 과정에서 실패하면 해당 신청서를 REJECTED로 전환하고 자연어 사유를 저장(Q282·375·376). 별도 `adoption_processes` 행은 생성하지 않음. 외부 경로 입양은 완료 신청서 FK 없이 기록 가능.
- 단순한 AVAILABLE→UNAVAILABLE 전환에서는 신규 입양 신청만 제한, 기존 신청은 강제 종료하지 않음. 거절·취소 뒤 재신청 가능(현재 입양 가능한 경우).

### 계정 탈퇴와 데이터 표시
- users/user_accounts 소프트 삭제 + 활성 FCM 토큰 비활성화, 사용자 소유 게시물은 조회·AI 대상에서 가상 삭제.
- 댓글 본문·기존 답글, 채팅 메시지·이미지, 저장된 알림은 보존하고 탈퇴 사용자 표시를 대체; 새 채팅·탈퇴한 루트 댓글에 신규 답글은 제한.
- 탈퇴 신청자의 과거 입양 신청서·수료증은 소속 보호소 업무 조회에서 보존. 실제 ADOPTED 처리 결과 및 완료 신청서 FK의 정합성 유지(별도 상태 변경 이력 테이블 없음).

## 6. AI 이미지 매칭·알림 정의

- 대상 실종 신고는 `ACTIVE`, 비삭제, 작성자 비탈퇴만. 비교 대상 발견 신고도 `ACTIVE`, 비삭제, 작성자 비탈퇴만(Q343).
- 보호동물 비교 대상은 `PROTECTING`이며 비삭제인 기록. 입양 상태 AVAILABLE/UNAVAILABLE 무관, RETURNED/ENDED/ADOPTED 제외(Q344).
- 실종 신고 등록 시 기존 발견 신고/보호동물과, 발견 신고/보호동물 등록 시 기존 실종 신고와 비교(Q346).
- 사진의 추가·교체·삭제가 발생하면 유효 상태의 신고·동물에 대해 다시 비교(Q347). 재개된 ACTIVE 신고도 다시 비교 대상.
- 알림 수신자는 실종 신고 작성자 **1명만**. 동일 `(missing_report_seq,target_type,target_seq)`에 `notification_type='AI_MATCH'`인 알림을 한 번만 생성, 알림 소프트 삭제해도 재발행하지 않음(Q291·345). **AI 알림 전용 DB UNIQUE는 없음**. 동일 실종 신고 행 잠금(`SELECT ... FOR UPDATE`) → BE 중복 검사 → 미존재 시 생성 → 커밋 후 FCM 발송(Q389·390).
- AI 결과/확정 동일개체 관계를 따로 저장하지 않음. 임베딩 저장 방식/벡터 모델/벡터 인덱스는 기존 보류 상태.

## 7. 개념 제약 SQL (정책 일치성 참고용)

> 다음 SQL은 **운영 DB에 그대로 실행하는 마이그레이션이 아닙니다.** 해당 컬럼·테이블 생성 이후 검토하여 적용해야 합니다. 동일 제약 중복 여부, 기존 데이터 정제, 순환 FK 생성 순서는 별도 마이그레이션 설계가 필요합니다.

```sql
-- Q370: 일반회원만 개인정보 4종 필수
ALTER TABLE users ADD CONSTRAINT ck_users_user_required
  CHECK (role <> 'USER' OR
        (nickname IS NOT NULL AND name IS NOT NULL AND
         birth_date IS NOT NULL AND phone IS NOT NULL));
CREATE UNIQUE INDEX uq_users_active_nickname_ci ON users (lower(nickname))
  WHERE role='USER' AND is_deleted=FALSE;
CREATE UNIQUE INDEX uq_users_active_phone ON users (phone)
  WHERE role='USER' AND is_deleted=FALSE;
CREATE UNIQUE INDEX uq_user_accounts_login_active ON user_accounts(provider_seq,login_identifier)
  WHERE is_deleted=FALSE;

-- Q350: 외부 코드 있는 보호소 중복 등록 방지 (NULL 여러 건 허용)
ALTER TABLE shelters ADD CONSTRAINT uq_shelters_external_code UNIQUE (external_shelter_code);
-- Q338: 행정구역별 보호소 매핑의 district_code는 PK

-- Q334: 품종-색상 조합으로 네 도메인의 종/품종/색상 정합성 보장
ALTER TABLE animal_combinations ADD CONSTRAINT uq_animal_combination_breed_color
  UNIQUE (breed_seq,color_seq);
-- pets/missing_reports/found_reports/shelter_animals.combination_seq는 각각 FK 및 NOT NULL

-- Q399 선행 확인: 회원당 비삭제 ACTIVE 실종 신고 최대 1건 (CLOSED 후 새 신고 가능)
CREATE UNIQUE INDEX uq_missing_user_active ON missing_reports(user_seq)
  WHERE status='ACTIVE' AND is_deleted=FALSE;
-- 기존 pet_seq 기준 ACTIVE 제한은 회원 단위 조건에 포함되므로 별도 UNIQUE 인덱스 불필요.
-- CLOSED → ACTIVE 재개 시에도 UNIQUE 충돌을 확인해야 함.

-- Q290: 발견 신고 종료 사유는 선택, 종료시각은 필수
ALTER TABLE found_reports ADD CONSTRAINT ck_found_status_closure
  CHECK ((status='ACTIVE' AND closed_reason IS NULL AND closed_dttm IS NULL)
      OR (status='CLOSED' AND closed_dttm IS NOT NULL));

-- Q294/Q237: 발견 신고 당 비삭제 PROCESSING/DONE 구조 합계 1건
CREATE UNIQUE INDEX uq_rescue_found_active_done ON rescue_records(found_report_seq)
  WHERE found_report_seq IS NOT NULL AND is_deleted=FALSE
    AND status IN ('PROCESSING','DONE');
-- Q384: 등록/접수 시각은 reg_dttm; 완료·실패·취소 시 completed_dttm 필수
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_completion_time
  CHECK ((status='PROCESSING' AND completed_dttm IS NULL)
      OR (status IN ('DONE','FAILED','CANCELED') AND completed_dttm IS NOT NULL));
-- Q357/Q360: 구조 완료 날짜·장소 필수, 실패·취소 시 사유 필수
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_done_fields
  CHECK (status <> 'DONE' OR
        (rescued_date IS NOT NULL AND rescue_address IS NOT NULL
         AND length(btrim(rescue_address))>0));
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_result_reason
  CHECK (status NOT IN ('FAILED','CANCELED') OR
        (result_reason IS NOT NULL AND length(btrim(result_reason))>0));

-- Q330/Q388: 공고 날짜 NOT NULL, 역전 방지(직접 등록 기본 시작+10일; 공공 적재 날짜는 담당자 입력)
ALTER TABLE shelter_animals ADD CONSTRAINT ck_shelter_notice_range
  CHECK (notice_start_date <= notice_end_date);
ALTER TABLE shelter_animals ADD CONSTRAINT ck_shelter_status_pair
  CHECK ((protection_status='PROTECTING' AND adoption_status IN ('AVAILABLE','UNAVAILABLE'))
      OR (protection_status IN ('RETURNED','ENDED') AND adoption_status='UNAVAILABLE')
      OR (protection_status='ADOPTED' AND adoption_status='ADOPTED'));

-- 보호 종료 상태 ↔ 종료 사유/일시 정합성 (기존 Q135)
ALTER TABLE shelter_animals ADD CONSTRAINT ck_shelter_protection_end
  CHECK ((protection_status='PROTECTING' AND end_reason IS NULL
          AND protection_ended_dttm IS NULL)
      OR (protection_status='RETURNED' AND end_reason='RETURNED'
          AND protection_ended_dttm IS NOT NULL)
      OR (protection_status='ADOPTED' AND end_reason='ADOPTED'
          AND protection_ended_dttm IS NOT NULL)
      OR (protection_status='ENDED' AND end_reason IN ('NATURAL_DEATH','EUTHANIZED','OTHER')
          AND protection_ended_dttm IS NOT NULL));

-- Q404·405: shelter_animals의 구조 정보 4개는 삭제. rescue_seq가 있으면
--   rescue_records JOIN으로만 구조 날짜/사유/장소/메모 조회. NULL이면 모두 NULL.
-- Q298: shelter_animals.rescue_seq의 UNIQUE는 설정하지 않음(구조 1:N)
-- Q354: (rescue_seq,shelter_seq) 복합 FK/보호소 동일성 제약을 추가하지 않음
-- Q190: 완료된 보호동물 반환은 동물별 최대 1건
ALTER TABLE animal_returns ADD CONSTRAINT uq_animal_returns_animal UNIQUE(animal_seq);
-- Q238: 해당 동물의 입양 신청서만 완료 신청서로 선택
ALTER TABLE adoption_applications ADD CONSTRAINT uq_app_application_animal UNIQUE(application_seq,animal_seq);
ALTER TABLE shelter_animals ADD CONSTRAINT fk_completed_application_for_animal
  FOREIGN KEY (completed_application_seq,animal_seq)
  REFERENCES adoption_applications(application_seq,animal_seq);
CREATE UNIQUE INDEX uq_shelter_completed_application ON shelter_animals(completed_application_seq)
  WHERE completed_application_seq IS NOT NULL;

-- Q402: 입양 양식 버전 테이블 제거. 현재 21문항은 질문 테이블만 관리.
-- question_seq(PK)를 answers_jsonb 문자열 키 및 condition_jsonb 의존 질문 참조로 사용.
-- display_order 중복 금지(현행 전역 공통 문항)
ALTER TABLE adoption_form_questions ADD CONSTRAINT uq_adoption_question_display_order
  UNIQUE(display_order);
-- Q363: DRAFT/SUBMITTED/APPROVED 통틀어 동일 사용자·동물 최대 1건
CREATE UNIQUE INDEX uq_app_active_user_animal ON adoption_applications(user_seq,animal_seq)
  WHERE is_deleted=FALSE AND status IN ('DRAFT','SUBMITTED','APPROVED');
-- Q375/Q376: adoption_processes 및 adoption_application_histories는 제거(관련 UNIQUE/FK 없음).

-- Q307: 신고 1인·대상 조합은 재신고도 불가(전체 행 UNIQUE)
ALTER TABLE reports ADD CONSTRAINT uq_reports_reporter_target
  UNIQUE(reporter_user_seq,target_type,target_seq);
ALTER TABLE reports ADD CONSTRAINT ck_reports_reason
  CHECK (length(btrim(reason_detail)) > 0);

-- Q380~382/Q387/Q389~399: 알림 유형과 공통 대상 코드
ALTER TABLE notifications ADD CONSTRAINT ck_notification_type
  CHECK (notification_type IN ('COMMENT','AI_MATCH','ADOPTION','SHELTER','SYSTEM'));
-- notifications.message TEXT NOT NULL은 테이블 정의에서 선언. DB의 문자열을 FCM body로 재사용.
-- 대상 target_type/target_seq는 각각 NULL 허용(Q391); 연결 대상 존재는 BE 검증.
-- AI 중복 검사: notification_type='AI_MATCH' AND
--   (missing_report_seq,target_type,target_seq) 동일 쌍을 is_deleted와 무관하게 조회(Q399).
-- AI_MATCH에 한해 missing_report_seq/target_type/target_seq 모두 필수,
-- target_type IN ('FOUND','SHELTER_ANIMAL'); 다른 알림은 missing_report_seq NULL(BE 검증).
-- AI 쌍 중복 방지 UNIQUE 인덱스를 만들지 않음(Q389).
-- Q390: missing_reports 해당 행 FOR UPDATE 잠금 후 BE 조회/생성을 단일 트랜잭션으로 수행.

-- Q387: 다형성 대상별 허용 코드 (컬럼의 NOT NULL/NULL은 테이블 표 참조)
ALTER TABLE reports ADD CONSTRAINT ck_report_target_type
  CHECK (target_type IN ('USER','MISSING','FOUND','SHELTER_ANIMAL',
                        'MISSING_COMMENT','FOUND_COMMENT','SHELTER_ANIMAL_COMMENT'));
ALTER TABLE bookmarks ADD CONSTRAINT ck_bookmark_target_type
  CHECK (target_type IN ('MISSING','FOUND','SHELTER_ANIMAL'));
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_shared_type
  CHECK (shared_target_type IS NULL OR shared_target_type IN ('MISSING','FOUND','SHELTER_ANIMAL'));
ALTER TABLE notifications ADD CONSTRAINT ck_notification_target_type
  CHECK (target_type IS NULL OR target_type IN ('USER','MISSING','FOUND',
               'SHELTER_ANIMAL','ADOPTION','MISSING_COMMENT','FOUND_COMMENT',
               'SHELTER_ANIMAL_COMMENT'));

-- Q371: 입양 상태 CLOSED 제거, 6종만 사용
ALTER TABLE adoption_applications ADD CONSTRAINT ck_adoption_application_status
  CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELED','ADOPTED'));
-- Q376: REJECTED의 자연어 rejection_reason 비공백 여부는 BE 검증.

-- Q293/Q232: 사진 표시 순서 중복 방지
CREATE UNIQUE INDEX uq_pet_image_order ON pet_images(pet_seq,display_order);
CREATE UNIQUE INDEX uq_missing_image_order ON missing_report_images(missing_report_seq,display_order);
CREATE UNIQUE INDEX uq_found_image_order ON found_report_images(found_report_seq,display_order);
CREATE UNIQUE INDEX uq_shelter_image_order ON shelter_animal_images(animal_seq,display_order);
-- Q400·401: 댓글/이미지 채팅은 각 본문 테이블의 image_seq FK 하나로 최대 1장.
-- 각 image_seq는 images(image_seq) 참조. 고유 제약을 image_seq에 걸면 공유 재사용을 막으므로 걸지 않음.

-- Q249: 활성 FCM 토큰 사용자/토큰별 중복 금지
CREATE UNIQUE INDEX uq_device_user_active ON device_tokens(user_seq) WHERE is_active=TRUE;
CREATE UNIQUE INDEX uq_device_token_active ON device_tokens(fcm_token) WHERE is_active=TRUE;
```

### v8 핵심 조회 예시 (개념 SQL)

```sql
-- Q404~405: 보호동물과 구조 기록 결합 (직접 등록은 구조 정보 NULL)
SELECT sa.animal_seq, sa.rescue_seq,
       rr.rescued_date, rr.rescue_reason, rr.rescue_address, rr.rescue_memo
FROM shelter_animals sa
LEFT JOIN rescue_records rr ON rr.rescue_seq = sa.rescue_seq;

-- Q399: AI 매칭 쌍에 대해 알림 생성 기록이 있는지 확인 (소프트 삭제 행도 포함)
-- 같은 트랜잭션에서 missing_reports 해당 행을 먼저 FOR UPDATE로 잠글 것.
SELECT 1
FROM notifications n
WHERE n.notification_type = 'AI_MATCH'
  AND n.missing_report_seq = :missing_report_seq
  AND n.target_type = :matched_target_type
  AND n.target_seq = :matched_target_seq
LIMIT 1;
-- 이 결과가 없을 때만 알림 INSERT. 커밋 후 동일 message로 FCM 발송.
-- DB 알림 존재는 푸시 수신 성공을 의미하지 않으며 FCM 실패 재시도는 없음.
```

### v5의 확정 복합 FK·채팅 UNIQUE 유지

```sql
-- USER/보호소 채팅방 조합 및 USER ID 순서
ALTER TABLE chat_rooms ADD CONSTRAINT ck_chat_room_pair
  CHECK ((room_type='USER' AND user_b_seq IS NOT NULL AND shelter_seq IS NULL AND user_a_seq < user_b_seq)
     OR (room_type='SHELTER' AND user_b_seq IS NULL AND shelter_seq IS NOT NULL));
CREATE UNIQUE INDEX uq_chat_room_user_pair ON chat_rooms(user_a_seq,user_b_seq)
  WHERE room_type='USER';
CREATE UNIQUE INDEX uq_chat_room_user_shelter ON chat_rooms(user_a_seq,shelter_seq)
  WHERE room_type='SHELTER';
ALTER TABLE chat_participants ADD CONSTRAINT ck_chat_participant_owner
  CHECK ((participant_type='USER' AND participant_user_seq IS NOT NULL AND participant_shelter_seq IS NULL)
     OR (participant_type='SHELTER' AND participant_user_seq IS NULL AND participant_shelter_seq IS NOT NULL));
CREATE UNIQUE INDEX uq_chat_participant_user ON chat_participants(chat_room_seq,participant_user_seq)
  WHERE participant_type='USER';
CREATE UNIQUE INDEX uq_chat_participant_shelter ON chat_participants(chat_room_seq,participant_shelter_seq)
  WHERE participant_type='SHELTER';
ALTER TABLE chat_messages ADD CONSTRAINT uq_chat_message_room_id UNIQUE(chat_room_seq,chat_message_seq);
ALTER TABLE chat_participants ADD CONSTRAINT fk_chat_participant_read_room
  FOREIGN KEY (chat_room_seq,last_read_message_seq)
  REFERENCES chat_messages(chat_room_seq,chat_message_seq);
ALTER TABLE chat_participants ADD CONSTRAINT fk_chat_participant_hidden_room
  FOREIGN KEY (chat_room_seq,last_hidden_message_seq)
  REFERENCES chat_messages(chat_room_seq,chat_message_seq);

-- 댓글 부모의 게시물 일치성 보장(최대 깊이 1 여부는 BE 검증)
ALTER TABLE missing_report_comments ADD CONSTRAINT uq_missing_comment_parent_post
  UNIQUE(missing_report_comment_seq,missing_report_seq);
ALTER TABLE missing_report_comments ADD CONSTRAINT fk_missing_comment_parent_post
  FOREIGN KEY(parent_comment_seq,missing_report_seq)
  REFERENCES missing_report_comments(missing_report_comment_seq,missing_report_seq);
ALTER TABLE found_report_comments ADD CONSTRAINT uq_found_comment_parent_post
  UNIQUE(found_report_comment_seq,found_report_seq);
ALTER TABLE found_report_comments ADD CONSTRAINT fk_found_comment_parent_post
  FOREIGN KEY(parent_comment_seq,found_report_seq)
  REFERENCES found_report_comments(found_report_comment_seq,found_report_seq);
ALTER TABLE shelter_animal_comments ADD CONSTRAINT uq_shelter_comment_parent_post
  UNIQUE(shelter_animal_comment_seq,animal_seq);
ALTER TABLE shelter_animal_comments ADD CONSTRAINT fk_shelter_comment_parent_post
  FOREIGN KEY(parent_comment_seq,animal_seq)
  REFERENCES shelter_animal_comments(shelter_animal_comment_seq,animal_seq);

-- 북마크·보호소 회원 계정 관계
ALTER TABLE bookmarks ADD CONSTRAINT uq_bookmark_user_target UNIQUE(user_seq,target_type,target_seq);
ALTER TABLE shelter_members ADD CONSTRAINT uq_shelter_member_shelter UNIQUE(shelter_seq);
ALTER TABLE shelter_members ADD CONSTRAINT uq_shelter_member_user UNIQUE(user_seq);
```

## 8. 서비스 트랜잭션 및 BE 검증 체크리스트

| 사건 | 단일 트랜잭션·BE 검증 항목 |
|---|---|
| 회원가입·탈퇴 | USER 필수 개인정보 CHECK + 활성 닉네임·전화 UNIQUE; 탈퇴 시 users/user_accounts 소프트 삭제 + 토큰 비활성 + 본인 활성 신청 CANCELED; 옛 콘텐츠 일반 API 비노출 |
| 실종 등록·재개 | 선택한 pet_seq 본인 소유 검증, 당시 속성 스냅샷 복사, **회원당** 비삭제 ACTIVE 1건 부분 UNIQUE(신규 등록·재개 모두 적용), AI 비교 작업 요청 |
| 발견 등록·재배정 | 시군구 지정 보호소 우선, 미지정 시 좌표 최근접 보호소; ADMIN만 재배정; 기존 구조 기록 보호소는 유지 |
| 발견 신고 종료·재개 | CLOSED 시 closed_dttm 필수·closed_reason TEXT 자유 입력 선택, ACTIVE 재개 시 두 필드 NULL |
| 구조 접수·결과 변경 | 접수 시각 reg_dttm 사용; PROCESSING 시 completed_dttm NULL, DONE/FAILED/CANCELED 시 NOT NULL; DONE 구조 날짜·주소 및 FAILED/CANCELED 사유 CHECK |
| 구조 기반 보호동물 등록 | DONE 구조 확인 후 `rescue_seq` FK만 연결; 구조 정보는 **복사하지 않고 JOIN**. 직접 등록·초기 적재 시 `rescue_seq=NULL` 허용, 구조 정보 NULL; 담당 보호소 동일 여부 강제 안 함 |
| 구조 삭제 | 연결된 보호동물이 1건이라도 있으면 소프트 삭제 거부. 발견 신고 삭제는 기존 구조 기록을 변경하지 않음 |
| 보호동물 등록·삭제 | 직접 등록 공고 기본 시작=등록일·종료=시작+10일; 공공데이터 초기 적재 날짜는 담당자가 직접 결정·입력; 상태 조합 CHECK; 활성 신청서 존재 시 삭제 거부 |
| 반환 완료 | 인계자 이름·번호 필수; 활성 회원 전화번호와 정확히 1건 일치할 때만 사용자 연결; 반환 INSERT + 동물 RETURNED + 활성 신청 REJECTED, 자연어 사유·자동 거절 시각 기록 |
| 입양 신청 DRAFT·제출 | 동일 회원·동물의 비삭제 활성 1건 부분 UNIQUE; 현재 질문 21개(`question_seq` 키)/JSONB 답변; 버전·질문 이력·문항 스냅샷 없음; 제출 시 필수·조건 검증 및 개인정보 스냅샷 생성; DRAFT 삭제는 하드 삭제 |
| 입양 질문 변경 | 현행 문항 행을 수정하며 이전 문항/버전은 남기지 않음; 이전 답변 JSONB는 question_seq로 연결하여 최신 질문으로 표시(의미 변경 위험 수용) |
| 입양 심사 | SUBMITTED→APPROVED 또는 REJECTED; 수동 처리자/시각 기록, REJECTED 자연어 사유 필수. 별도 이력 테이블 없음 |
| 승인 이후 입양 완료 | 해당 신청 ADOPTED + 해당 동물 보호·입양 상태 ADOPTED + completed_application_seq 설정 + 다른 활성 신청 자동 REJECTED를 **한 트랜잭션**에 반영 |
| 승인 후 진행 실패 | 신청서 REJECTED 및 자연어 사유, 처리시각 갱신. 후속절차 테이블이나 거절 단계 컬럼 없음 |
| 입양 신청 자동 거절 | 시스템 사유 문구 생성, reviewed_dttm=거절 시각, reviewed_usr_seq=NULL. 과거 승인 시각은 보존하지 않음 |
| 이미지 수정·제거 | 연결 참조 해제 후 **모든 이미지 참조**(보호소 profile/background, 댓글·채팅 image_seq 직접 FK 포함)를 조회; 마지막 참조가 사라진 경우에만 images 행/S3 객체 삭제. S3 실패 시 안전한 정리 방식은 구현 검증 |
| 댓글·채팅 | 본문 또는 이미지 최소 1개, 답글 깊이 1, 부모 게시물 복합 FK; 루트 삭제 시 답글 보존, 탈퇴 사용자 신규 답글·메시지 제한 |
| 다형성 참조 | 동일 대상 공통 타입 코드 사용; reports/bookmarks/chat 공유의 허용 타입 CHECK; 실제 대상 존재·접근 권한은 BE 검사. 알림 target 두 컬럼은 NULL 허용 |
| AI 알림 | ACTIVE/PROTECTING 자격 확인, 등록·재개·사진 수정 시 비교; `missing_reports` 행 `FOR UPDATE` 잠금 → `AI_MATCH`와 `(missing_report_seq,target_type,target_seq)`로 삭제된 알림까지 포함해 쌍 존재 조회 → 최초 1회 저장. AI 중복 UNIQUE 없음 |
| 일반 알림·푸시 | COMMENT/AI_MATCH/ADOPTION/SHELTER/SYSTEM만 저장; `message TEXT NOT NULL` 저장한 같은 값으로 FCM body 발송, 실패 시 DB 보존·재시도 없음; 채팅 푸시는 별도 저장 없음 |
| FCM 토큰 | 기존 활성 토큰 비활성화 후 신규 활성화; 사용자/토큰 활성 부분 UNIQUE; 탈퇴 시 전부 비활성 |

## 9. 구현 제외·보류·추가 검증

### 현 프로젝트 구현/물리 ERD에서 제외
- `animal_transfers`: 보호소 간 보호동물 이관 기능·이력 자체 제외(Q283).
- `user_blocks`: 차단, 자동 차단, 차단 해제, 채팅 차단 규칙 전체 제외(Q306).
- `adoption_form_answers`: 정규화된 답변 테이블 삭제, `adoption_applications.answers_jsonb` 사용(Q276). `adoption_form_templates`도 Q402에 따라 현행 모델에서 제거. `adoption_processes`, `adoption_application_histories`, `shelter_animal_status_history`도 현행 모델에서 제거(Q375·376·378).
- `chat_message_images`, `missing_report_comment_images`, `found_report_comment_images`, `shelter_animal_comment_images`: 0~1 이미지의 직접 FK 통합으로 삭제(Q400~401). `chat_notifications`: DB에 채팅 푸시 이력을 저장하지 않음. `simulation_notifications`, `mock_adoption_runs`, `mock_adoption_results`: 모의 입양 도메인 보류.
- `confirmed_animal_links`: 실종↔발견/보호동물 동일 개체 확인 링크 저장하지 않음. `animal_returns.missing_report_seq`도 없음.
- 입양 이후 사후관리 테이블·API는 프로젝트 범위 밖. 보호소 삭제 정책은 Q356에서 엣지케이스로 스킵.

### 초안·추후 논의 (확정된 구현 필드로 오해 금지)
- `image_embeddings`: pgvector 사용 후보 초안만 보존. 이미지당 임베딩, 모델 버전/차원/인덱스/재생성 정책은 사용자 요청까지 보류.
- **제거 확정 테이블은 구현 보류가 아니라 현행 ERD에서 제외**: `shelter_animal_status_history`(Q378), `adoption_processes`(Q375), `adoption_application_histories`(Q376). 이들 테이블을 추가하거나 임시 FK를 연결하지 않음. `rejection_type/rejection_stage`도 만들지 않고 신청서의 자연어 `rejection_reason`을 사용.
- `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations`의 실제 기준값과 기타 옵션 매핑은 팀이 직접 결정(Q336 스킵).
- 공공데이터 보호소 외부 코드 유일성과 주소·좌표 필수값 수급 가능성 검증. 초기 적재 공고 날짜는 적재자가 정해 입력(Q388); 외부 보호동물 원천 ID 중복 판별은 실제 적재 구현 시 확인(새 필드 임의 추가 금지).
- 공유 이미지 원본은 모든 참조 해제 뒤에만 삭제(Q383). DB/S3 비원자성 및 장애 시 재처리는 구현 검증 사항, 임베딩 생명주기는 보류.
- 최종 DDL의 PK 정수 범위, 감사 컬럼/인덱스 이름, 순환 FK 생성 순서 및 데이터 마이그레이션 경로는 구현 단계에서 확인. 특히 기존 `template_seq`/`question_code` 답변 키 → `question_seq` 변환, 제거된 이미지 연결 테이블의 참조 이전, AI 알림 중복 식별자 이전, 구조 정보 복사 컬럼 삭제 전 데이터 보존 여부는 **기존 데이터가 있을 때만** 마이그레이션 검증 필요. Q386 대상인 작성자 중복 reg_usr_seq는 표에서 이미 제거. `pets.mod_usr_seq`도 Q396에서 제거.
- `adoption_form_questions`의 문항 내용 변경 시 기존 답변이 현재 질문으로 표시되는 정책이며 과거 문항·보기 스냅샷을 별도로 보관하지 않음(Q402). 질문 행 삭제/새 PK 생성 시 이전 `answers_jsonb` 키의 의미를 잃을 수 있으므로 질문 ID 안정성 필요.
- 실제 보호동물 구조 정보가 없는 `rescue_seq=NULL` 행은 구조 상세를 NULL로 표시하는 정책(Q405), 별도 구조 속성을 생성하지 않음.

### `image_embeddings` 보류 초안 (참고용, 현행 확정 ERD에서 제외)

| 컬럼 | 타입 초안 | 설명 |
|---|---|---|
| `image_embedding_seq` | `BIGINT` | 벡터 행 키(※초안) |
| `image_seq` | `BIGINT` | 임베딩 대상 이미지 |
| `embedding` | `VECTOR(차원 미정)` | 사진 임베딩; 모델별 차원 확정 필요 |
| `model_version` | `VARCHAR(100)` | 모델 식별 및 재생성 정책 미정 |
| `reg_dttm` | `TIMESTAMPTZ` | 생성 시각 |

## 10. 과거 명세서·기능 명세서와의 충돌 처리 목록

| 과거 내용/기능 명세 | v8 현행 적용 결과 |
|---|---|
| v5: 입양 DRAFT 1개 + SUBMITTED 1개 동시 허용 | Q363: DRAFT/SUBMITTED/APPROVED 통틀어 동일 사용자·동물 최대 1개 |
| v5: DRAFT 삭제 소프트 삭제 | Q269: DRAFT 삭제 물리 삭제 |
| v5: 입양 완료/반환/보호 종료 시 CLOSED | Q267·270: 해당 대상 활성 신청 REJECTED; 입양 확정 선택 신청서는 ADOPTED |
| v5: 입양 후속 절차 실패해도 APPROVED 유지 | Q282: 절차 실패 시 REJECTED |
| v5: 구조 5상태 RECEIVED/IN_PROGRESS/COMPLETED/... | Q294: PROCESSING/DONE/FAILED/CANCELED 네 상태 |
| v5: 구조 완료 1:1 보호동물, 단일 UNIQUE | Q298: 하나의 구조 기록에서 여러 보호동물(1:N), rescue_seq UNIQUE 제거 |
| v5: 보호소 간 이관 및 이관 이력 | Q283: 기능 및 테이블 제외 |
| v5: 사용자 차단 및 신고 자동 차단 | Q306: 기능/테이블/차단 규칙 모두 제거 |
| v5: 신고 PENDING/REVIEWING 중복만 제한 | Q307: PENDING만 이용, 동일 신고자·대상 전체 UNIQUE |
| v5: 반려동물 이미지 1개 FK | Q293: pet_images 1:N, 기존 image_seq 삭제 |
| v5: 종·품종 FK와 coat_color 자유 텍스트 | Q334: 네 동물 엔터티의 combination_seq FK |
| v5: 탈퇴 콘텐츠 행별 소프트 삭제 불명확 | Q312: 탈퇴한 회원의 게시물은 users.is_deleted로 가상 비노출 |
| v5: 루트 댓글 삭제 시 답글 동반 삭제 | Q340: 루트만 소프트 삭제, 자식 답글 보존 |
| v5: ACTIVE 재개 불허 | Q289: 실종/발견 신고 모두 CLOSED→ACTIVE 재개 허용 |
| v5: 발견 종료 사유 필수 | Q290: 종료 시각만 필수, 종료 사유 선택 |
| 최신 기능 명세: 마이크로칩 등록 여부 필수 | Q305·364: 등록 여부 컬럼/필수 항목 제거, 번호만 NULL 허용 |
| 최신 기능 명세: 실종 상황 자유 서술 필수 표현 | Q369의 선택 `circumstances`도 Q397에서 제거; 장소·일시 필수는 유지 |
| 최신 기능 명세: 알림 숨김+삭제 | Q339: 숨김 미제공, 소프트 삭제만 |
| 최신 기능 명세: 취소 사유 저장 여부 미확정 | Q326: CANCELED 사유 보존하지 않음 |
| v6: `adoption_processes` 후속 입양 처리 테이블 | Q375: 제거, `adoption_applications`에 입양 프로세스 통합 |
| v6: `adoption_application_histories` 거절 단계 추적 | Q376·377: 테이블/단계 컬럼 제거; `rejection_reason TEXT` 자연어 설명만 |
| v6: `shelter_animal_status_history` 보류 초안 | Q378: 물리 ERD에서 제거 |
| v6: 신청서 `CLOSED` 및 `closed_reason` | Q371: 삭제, 신청 상태 6종만 |
| v6: 자동 REJECTED의 리뷰 시각 정의 불분명 | Q372: 자동 거절 시각 `reviewed_dttm`, 처리자 NULL |
| v6: `found_reports.closed_reason VARCHAR(30)` | Q379: `TEXT NULL` |
| v6: 알림 타입 미확정/문구 미저장 | Q380·381: 네 유형 고정, `message TEXT NOT NULL` 추가 |
| v6: AI 알림 부분 UNIQUE | Q389·390: 제거; BE 중복 조회 + missing_reports 행 잠금 |
| v6: `rescue_records.received_dttm` 초안 | Q384: 제거, `reg_dttm`이 접수 시각 |
| v6: 이미지 첨부 `image_type` 초안 | Q385: 세 테이블에서 제거 |
| v6: 작성자 중복 `reg_usr_seq` | Q386: 작성자와 동등한 8개 테이블에서 제거 |
| v6: 대상 타입 코드 혼재 | Q387: 공통 코드, 입양 대상명 `ADOPTION` |
| v6: 초기 공공데이터 공고 날짜 처리 미정 | Q388: 적재 담당자가 직접 정해 입력(원천 우선순위 미확정) |
| v6: 일반 시스템 알림 유형 미확정 | Q392에서 스킵했으나 Q398에서 **SYSTEM 추가**, 현행 5종(`REPORT` 미추가) |
| v7: UTC 기준 세션 전제 | Q393: `TIMESTAMPTZ` 유지 + DB 연결/세션 `Asia/Seoul` 설정 |
| v7: 보호소 프로필 이미지 1개 | Q394: `shelters.background_image_seq` 추가(선택, 공통 images 참조) |
| v7: 회원·반려동물 실종 신고 중복 정책 | Q399 선행 확인: 회원당 비삭제 ACTIVE 신고 최대 1개, user_seq 부분 UNIQUE |
| v7: pets 수정자 정보 | Q396: `pets.mod_usr_seq`만 제거, 수정시각 유지 |
| v7: 실종/발견 상황 설명 | Q397: 두 신고의 `circumstances` 컬럼 삭제 |
| v7: AI 알림 매칭 상대 전용 2개 컬럼 | Q399: `notifications.target_type/target_seq`로 통합, 별도 matched_target_type/seq 제거 |
| v7: 댓글 이미지 3개 별도 테이블 | Q400: 3개 삭제하고 각 댓글 본문 `image_seq NULL` FK로 통합 |
| v7: chat_message_images 별도 테이블 | Q401: 삭제하고 `chat_messages.image_seq NULL` FK로 통합 |
| v7: 공통 입양 신청서 버전/과거 문항 보존 | Q402: templates 삭제, 현재 질문 21개만 수정 관리. 질문 이력·스냅샷 없음, question_code도 삭제 |
| v7: 반환 시각과 기록 시각 분리 | Q403: `returned_dttm` 삭제, `reg_dttm`으로 통일 |
| v7: 보호동물에 구조 속성 최초 복사 | Q404·405: 구조 속성 4개 삭제, rescue_seq LEFT JOIN; rescue_seq NULL 허용 |

## 11. Q263~370 결정 추적표 (이후 변경은 별도 표 우선)

> 번호별 결정 요지만 압축 기재했습니다. 상세 데이터 모델은 앞의 테이블별 정의와 정책이 우선합니다.

| 질문 | 확정 내용 |
|---|---|
| Q263 | 동일 회원·동물의 동시 제출 제한, 타 동물/타 회원 신청 허용 |
| Q264 | 동일 회원·동물 활성 DRAFT/SUBMITTED/APPROVED 합계 1건 |
| Q265 | 최종 입양 완료 시 동물 ADOPTED 및 완료 신청서 FK 설정 |
| Q266 | 신청서 최종 ADOPTED 상태 추가(서류 승인과 구분) |
| Q267 | 다른 신청자는 실제 입양 완료 시 REJECTED |
| Q268 | 모든 거절은 REJECTED. **단계/이력 기록 부분은 Q376·377에서 폐기**, 자연어 rejection_reason만 유지 |
| Q269 | 임시저장 DRAFT 삭제는 하드 삭제 |
| Q270 | 반환·보호 종료 시 활성 신청서 REJECTED(과거 CLOSED 대체) |
| Q271 | 거절·취소 이후 재신청 가능(현재 동물 상태 적합) |
| Q272 | 타인 입양 완료 시 DRAFT도 REJECTED |
| Q273 | 보호소 전체에 동일 신청 양식 1개 |
| Q274 | 21개 문항 고정, 운영 중 수정 불가(**Q402에서 대체: 현재 문항 변경 허용, 과거 이력 없음**) |
| Q275 | 문항 선택지는 options_jsonb |
| Q276 | 답변 21개는 adoption_applications.answers_jsonb 1행, 답변 테이블 제거 |
| Q277 | 교육 수료증은 전체 보호소 공통 선택사항 |
| Q278 | 수료증 JPG/PNG/PDF 허용 |
| Q279 | 수료증 최대 1개 |
| Q280 | 수료증 최대 10MB |
| Q281 | 수료증 저장소 키 1개만 저장, 추가 메타컬럼 없음 |
| Q282 | 승인 후 실제 입양 진행 실패 시 신청서 REJECTED; **별도 절차 테이블은 Q375에서 폐기** |
| Q283 | 보호소 간 이관 기능 및 animal_transfers 제외 |
| Q284 | 발견 신고 등록 때 담당 보호소 자동 배정 |
| Q285 | 행정구역 지정 보호소 우선 |
| Q286 | 행정구역 매핑 없으면 최근접 보호소 대체 |
| Q287 | 시·군·구 단위, 구역당 담당 보호소 1개 |
| Q288 | ADMIN만 발견 신고 담당 보호소 수동 변경; 위치 수정 시 자동 재배정 없음 |
| Q289 | 실종·발견 신고 CLOSED→ACTIVE 재개 허용 |
| Q290 | 발견 CLOSED: closed_dttm 필수/closed_reason 선택; ACTIVE 둘 다 NULL |
| Q291 | 동일 AI 매칭 쌍 알림 생애 최초 1회 |
| Q292 | 보호동물 기본 UNAVAILABLE, 공고 중에도 수동 AVAILABLE 가능 |
| Q293 | pet_images 1:N; pets.image_seq 제거 |
| Q294 | 구조 상태 PROCESSING/DONE/FAILED/CANCELED 네 가지 |
| Q295 | 발견 신고 등록과 구조 생성 별도, 보호소가 선택해야 구조 생성 |
| Q296 | 발견 신고 없는 보호소 직접 구조 가능 |
| Q297 | 발견 신고 구조 대상으로 선택 시 즉시 PROCESSING |
| Q298 | 완료 구조 1건에서 보호동물 다수 연결 가능, rescue_seq 비유일 |
| Q299 | users에 이름/생년월일/전화번호/프로필 이미지 키 추가 |
| Q300 | 활성 유저 닉네임 대소문자 무시 중복 제한 |
| Q301 | 탈퇴 시 users와 user_accounts 소프트 삭제, 재가입 신규 계정 |
| Q302 | 실종·발견 신고 연락처 스냅샷 저장 |
| Q303 | 로그인 필수, 신고별 연락처 공개·비공개 설정 |
| Q304 | 실종·발견 is_phone_public 기본 FALSE |
| Q305 | 마이크로칩 등록 여부 컬럼 없이 번호만 선택 저장 |
| Q306 | user_blocks/차단 기능 전부 제거 |
| Q307 | reports PENDING만 사용, 신고자·대상 전역 중복 금지 |
| Q308 | 신고 사유 자유 입력 reason_detail 필수, reason_code 제거 |
| Q309 | animal_returns에 반환 보호자 이름/번호 스냅샷 |
| Q310 | 반환 전화번호 일치 활성 회원 정확히 1명일 때만 returned_user_seq 자동 연결 |
| Q311 | 회원 탈퇴 시 신고 원본 전화번호 및 공개 설정은 그대로 보존 |
| Q312 | 회원 탈퇴 콘텐츠는 개별 삭제하지 않고 users.is_deleted로 가상 숨김 |
| Q313 | 탈퇴자 원본 게시물은 일반 서비스 API에서 보호소/관리자에게도 비노출 |
| Q314 | 탈퇴자의 채팅 메시지/이미지 보존, 표시명 변경 |
| Q315 | 탈퇴자와 관련된 기존 알림도 보존, 표시명 변경 |
| Q316 | 탈퇴자 댓글과 답글은 원문 유지, 작성자 표기만 변경 |
| Q317 | 탈퇴자 루트 댓글에 신규 답글 금지 |
| Q318 | 탈퇴 회원의 활성 신청서는 CANCELED, ADOPTED 보존 |
| Q319 | 보호소는 탈퇴 신청자의 입양 양식/답변/수료증 및 신청 상태 열람 가능; **별도 변경 이력 테이블은 Q376에서 폐기** |
| Q320 | 공통 양식 template·questions 유지(**Q402에서 templates 삭제**), 보호소별 양식 소유 FK 제거 |
| Q321 | 문항 question_code 버전별 고정(**Q402에서 폐기, question_seq 답변 키로 변경**) |
| Q322 | condition_jsonb로 조건부 문항 표시·필수 기준 관리 |
| Q323 | 조건 미충족 문항 답변 제거/정합성 검증은 FE·BE만 |
| Q324 | 선택지 및 답변은 별도 option 코드가 아닌 표시 문자열 |
| Q325 | 신청서 제출 때 신청자 이름/생년월일/전화번호 스냅샷 |
| Q326 | 취소 사유 컬럼·상태 이력 사유 없음 |
| Q327 | 탈퇴시 모든 FCM 토큰 비활성 처리 |
| Q328 | USER의 활성 전화번호 부분 UNIQUE |
| Q329 | 전화번호 인증 SMS 없음, 형식+중복만 확인 |
| Q330 | 직접 등록 기본 공고 시작=등록일·종료=시작+10일, 두 값 NOT NULL; **공공데이터 초기 적재는 Q388에 따라 담당자가 입력** |
| Q331 | 건강검진·접종 상태 텍스트 컬럼 추가 |
| Q332 | found_reports.reporter_name 삭제, users.name 활용 |
| Q333 | 종·품종·색상 모두 필수 |
| Q334 | animal_colors, animal_combinations 신설, 4개 동물 테이블 combination_seq FK |
| Q335 | 사전 등록된 유효 조합 및 기타 선택값 사용 |
| Q336 | 실제 마스터 데이터 조합 값 정의 질문 스킵 |
| Q337 | 중성화 BOOLEAN NULL (미상= NULL) |
| Q338 | district_code PK→shelter_seq FK 행정구역 매핑 |
| Q339 | 알림 숨김 제외, 삭제만 is_deleted |
| Q340 | 부모 댓글 삭제시 답글 유지 |
| Q341 | 상대 탈퇴 시 채팅 이전 기록 보존, 신규 메시지 금지 |
| Q342 | 발견 신고 삭제해도 연계 구조 기록 유지·진행 허용 |
| Q343 | ACTIVE 발견 신고만 AI 매칭 대상 |
| Q344 | PROTECTING 보호동물만 AI 매칭 대상 |
| Q345 | AI 매칭 알림은 실종 신고 작성자에게만 |
| Q346 | 실종/발견/보호동물 신규 등록 시 양방향 AI 비교 |
| Q347 | 사진 변경 시 기존 매칭 자격 맞는 범위에서 재매칭 |
| Q348 | 반려동물 소프트 삭제해도 실종 신고 유지 |
| Q349 | 반려동물 사진은 images 원본 재사용 + 실종 신고 별도 첨부 |
| Q350 | shelters.external_shelter_code NULL 허용 UNIQUE |
| Q351 | 구조 기록 rescue_address/rescue_memo 분리, location_description 제거 |
| Q352 | DONE 구조 정보 최초 보호동물 복사 후 독립 관리(**Q404에서 대체: 복사 없이 JOIN**) |
| Q353 | 연결된 보호동물이 있는 구조 기록 삭제 불가 |
| Q354 | 연결 구조·보호동물 담당 보호소가 달라도 DB 제약 금지 |
| Q355 | 활성 입양 신청서가 있는 보호동물 삭제 불가 |
| Q356 | 보호소 삭제·구역 배정 처리 엣지케이스 스킵 |
| Q357 | DONE 시 구조 날짜·장소 필수(DB CHECK) |
| Q358 | 공통 입양 양식 활성 버전 서비스 전체 최대 1개(**Q402에서 버전 관리 제거**) |
| Q359 | 기존 DRAFT는 옛 템플릿으로 계속 작성·제출 가능(**Q402에서 템플릿 제거; 현행 문항 사용**) |
| Q360 | FAILED/CANCELED 시 구조 실패·취소 이유 필수(DB CHECK) |
| Q361 | 보호동물 rescue_memo 컬럼 추가 및 최초 복사(**Q404에서 제거**) |
| Q362 | pets.weight VARCHAR(30) NULL; 타 동물 테이블 제외 |
| Q363 | 동일 사용자·동물 활성 입양 신청서 최대 1개 재확정 |
| Q364 | 마이크로칩 등록 여부 제거, microchip_number NULL만 유지 재확정 |
| Q365 | 네 동물 테이블 gender NOT NULL, UNKNOWN 명시 |
| Q366 | pets.name, missing_reports.name NOT NULL |
| Q367 | 실종/발견 신고 당시 연락처 NOT NULL |
| Q368 | animal_returns.recipient_name/recipient_phone NOT NULL |
| Q369 | 실종 상황 circumstances 텍스트는 선택(**Q397에서 컬럼 제거**) |
| Q370 | USER 닉네임/실명/생년월일/전화번호는 역할별 DB CHECK 필수 |

## 12. Q371~Q392 변경 결정·스킵 추적표

> 사용자가 확정한 내용만 반영합니다. 질문 당시 잠깐 제시했던 초안이나 추천안은 최종 결정으로 취급하지 않습니다.

| 질문 | 최종 결정 | 데이터 모델 영향 |
|---|---|---|
| Q371 | A: 신청 상태 CLOSED 미사용 | `adoption_applications.status` 6종, `closed_reason` 삭제 |
| Q372 | B: 시스템 자동 거절도 reviewed_dttm 갱신 | `reviewed_usr_seq=NULL`, 처리시각은 자동 거절 시각 |
| Q373 | 초기 A, **Q376에서 대체** | 이력 테이블 제거로 거절 유형 컬럼 추가 없음 |
| Q374 | 초기 A, **Q376에서 대체** | DRAFT 이력 논의 종료(전체 이력 테이블 제거) |
| Q375 | A: 후속 절차 테이블 제거 | `adoption_processes` 제거; 입양 프로세스는 신청서에 통합 |
| Q376 | 별도 이력 제거·자연어 거절 이유 | `adoption_application_histories` 제거, `rejection_reason TEXT` 문장 |
| Q377 | A: 거절 단계 컬럼 없음 | `rejection_stage` 생성 안 함 |
| Q378 | A: 보호동물 상태 변경 이력 제거 | `shelter_animal_status_history` 제거 |
| Q379 | A: 발견 신고 종료 이유 자유 텍스트 | `found_reports.closed_reason TEXT NULL` |
| Q380 | A: 당시 알림 기능별 4종(**Q398에서 SYSTEM 추가로 대체**) | `COMMENT/AI_MATCH/ADOPTION/SHELTER` + 현행 `SYSTEM`; SHELTER는 보호소 업무 전용 |
| Q381 | A: 알림 DB 메시지와 FCM body 동일 | `notifications.message TEXT NOT NULL` |
| Q382 | A: 다형성 이동 대상 유지 | `notifications.target_type/target_seq`, BE 대상·권한 검증 |
| Q383 | B: 이미지 마지막 참조 해제 시에만 제거 | 이미지 연결 해제 후 모든 참조 확인, 최종적으로 `images`·S3 삭제 |
| Q384 | B: 구조 접수 시간 컬럼 정리 | `received_dttm` 삭제, `reg_dttm` 사용, `completed_dttm` 상태 CHECK |
| Q385 | A: 첨부 이미지 종류 불필요 | 실종·발견·보호동물 이미지 3개 테이블의 `image_type` 삭제 |
| Q386 | B: 작성자 중복 등록자만 제거 | 작성자 FK가 있는 관련 8개 테이블의 `reg_usr_seq` 제거; 나머지 유지 |
| Q387 | A: 대상 타입 코드 통합 | 코드 표준화, 통합 입양 화면 참조 `ADOPTION` |
| Q388 | 사용자가 초기 적재 날짜 직접 입력 | 공고 날짜 자동 원천 우선순위 미확정, NOT NULL/역전 CHECK 유지 |
| Q389 | B: AI 중복 검증은 BE | AI 쌍 전용 DB UNIQUE 제거 |
| Q390 | A: PostgreSQL 잠금으로 동시성 제어 | missing_reports 행 FOR UPDATE 이후 검사·생성 |
| Q391 | B: 알림 이동 대상 선택 | notifications.target_type/target_seq 각각 NULL 허용 |
| Q392 | **당시 스킵 → Q398로 대체** | SYSTEM은 추가하여 5종, REPORT는 미추가 |

## 13. Q393~Q405 최신 확정·변경 추적표

> Q393~Q405는 v7에 대한 사용자 후속 수정 결정입니다. **Q395는 기존 유지**, **Q402는 후속 추가 결정 2건까지 확정**되었고, Q399 이전에 확인한 회원당 ACTIVE 실종 신고 1건 정책도 현행에 반영합니다.

| 질문 | 최종 확정 결정 | 현행 데이터 모델 영향 |
|---|---|---|
| Q393 | A: TIMESTAMPTZ 유지+한국 시간 세션 | PostgreSQL 세션 `TimeZone='Asia/Seoul'`, `DATE` 유지 |
| Q394 | A: 보호소 배경 이미지 0~1장 | `shelters.background_image_seq BIGINT NULL` → images FK |
| Q395 | **기존 정책 유지** | 지역별 보호소 1곳; `district_code` 단일 PK. 복합 PK 변경 없음 |
| Q396 | A: 반려동물 수정자 제거 | `pets.mod_usr_seq` 삭제, `mod_dttm` 유지 |
| Q397 | A: 상황 설명 삭제 | `missing_reports.circumstances`, `found_reports.circumstances` 삭제 |
| Q398 | A: SYSTEM 일반 알림 유형 추가 | 알림 유형 5종, NOT NULL. 과거 Q392 스킵 대체 |
| Q399 | B: AI 상대 식별자 중복 제거 | `matched_target_type/seq` 삭제, AI 중복은 `(missing_report_seq,target_type,target_seq)`와 `AI_MATCH`. 별도 UNIQUE 미사용 |
| Q399 선행 확인 | 회원당 ACTIVE 실종 신고 최대 1개 | user_seq 부분 UNIQUE(비삭제 ACTIVE). CLOSED 후 신규 신고 가능, 재개 시에도 중복 검사 |
| Q400 | A: 댓글 이미지 본문 통합 | 이미지 연결 테이블 3개 삭제, 3종 댓글 `image_seq NULL` 직접 FK |
| Q401 | A: 채팅 이미지 본문 통합 | `chat_message_images` 삭제, `chat_messages.image_seq NULL` 직접 FK |
| Q402 | C: 질문 테이블만 사용, 템플릿/버전 이력 없음 | `adoption_form_templates` 삭제, questions/applications.template_seq 삭제; 현재 질문만 관리 |
| Q402 추가 확인 | A: 기존 답변도 현재 질문으로 표시 | 제출 당시 질문 스냅샷 없음, answers_jsonb 기존 값은 보존 |
| Q402 추가 결정 | A: question_code 제거 | `adoption_form_questions.question_code` 삭제, `question_seq`를 JSONB 키/조건 참조에 사용 |
| Q403 | A: 반환 완료 시각=등록 시각 | `animal_returns.returned_dttm` 삭제, `reg_dttm` 유지 |
| Q404 | 구조 정보 중복 저장 제거 | `shelter_animals` 구조 날짜/사유/장소/메모 삭제, `rescue_seq` JOIN으로 조회 |
| Q405 | A: rescue_seq NULL 허용 | 직접 등록·초기 적재 동물은 구조 기록 없이 등록 가능; 구조 속성 NULL |

## 14. 새 대화·팀원 인수인계

### 프로젝트 목적과 운영 범위
- SSAFY 팀 프로젝트: 유실·발견 신고, 구조·보호동물 등록, 보호자 반환, 입양 신청·최종 입양, AI 이미지 매칭·알림을 하나의 플랫폼으로 연결.
- 일반회원 USER는 KAKAO 로그인, 보호소 담당자 SHELTER/관리자 ADMIN은 사전 발급 LOCAL 로그인. 보호소당 SHELTER 계정 1개.
- 모든 일반 서비스 API는 로그인 필수. 보호소 업무 기록의 관계와 권한은 서비스 계층에서 검증.

### 반드시 기억할 확정 규칙
- 반려동물/실종/발견/보호동물은 공통 `animal_combinations.combination_seq`를 사용하며 종·품종·색상 직접 컬럼 조합은 사용하지 않음.
- 회원당 비삭제 ACTIVE 실종 신고는 최대 1건, CLOSED 후 신규 신고 가능. 실종↔발견/보호동물 직접 FK 없음. AI 동일 개체 확정 기능 없음. 매칭 알림은 실종 신고자만·쌍당 최초 1회. `notifications`의 `AI_MATCH`+`missing_report_seq/target_type/target_seq`로 BE 중복 조회 및 실종 신고 행 잠금 처리(별도 AI UNIQUE 없음).
- 발견 신고는 구조와 독립이며 구조 접수/완료/신고 삭제로 상태를 자동 종료하거나 구조 이력을 삭제하지 않음.
- 보호동물 1건은 DONE 구조 0~1건을 참조할 수 있고, 한 구조 기록이 여러 보호동물을 생성할 수 있음. 보호소 코드가 서로 달라도 연결 허용. 구조 정보는 보호동물에 복사하지 않고 JOIN 조회, 직접 등록/초기 적재 시 rescue_seq=NULL.
- 반환과 입양 완료는 서로 다른 최종 처리. 반환 보호자 이름/번호 필수, 반환 완료는 실종 신고 종료를 자동 실행하지 않음.
- 입양 질문은 공통 현재 21문항만 유지하며 템플릿/버전·질문 변경 이력 없음. 답변은 `question_seq` 키의 JSONB, 이전 답변도 현행 질문과 함께 표시. 사용자·동물 활성 신청서 1개, 상태 6종(별도 CLOSED 없음). 서류 승인 APPROVED/완료 ADOPTED를 신청서 하나에서 관리하며 완료 FK와 동물 상태를 원자적으로 갱신. 별도 후속 절차·상태 이력 테이블 없음.
- 댓글/채팅 이미지 별도 테이블 없이 각각 `image_seq NULL` FK 사용. 보호소 배경 이미지도 `images` FK. `TIMESTAMPTZ` 유지, DB 세션은 `Asia/Seoul`.
- 회원 탈퇴 시 콘텐츠의 원본 행을 일괄 삭제하지 않고 공개 조회/AI에서 비노출. 댓글·채팅·알림·보호소 업무상 과거 입양 신청서 및 완료 기록은 보존(상태 변경 이력 테이블 없음).
- 유저 차단/보호소 이관/모의 입양/입양 이후 사후 관리는 구현하지 않음.

### 이후 개발 작업 순서 제안 (추가 정책 확정 아님)
- ① 본 명세서에 따른 DB 마이그레이션 초안 작성 및 데이터 시드 검증(종·품종·색상 조합, 구역→보호소 매핑).
- ② FK·부분 UNIQUE·CHECK 제약과 실제 마이그레이션 순서 검증(신규/기존 DB 각각).
- ③ API 명세서에 신고/구조/입양 상태 전이 및 역할별 조회·탈퇴 콘텐츠 필터를 반영.
- ④ AI 작업 큐/FCM의 중복 알림 경쟁 조건을 `missing_reports` 행 잠금 및 BE 중복 조회·트랜잭션으로 검증(전용 UNIQUE 없음).
- ⑤ 보류 항목만 별도 백로그로 관리하며 개발 범위를 임의 확대하지 않음.

### 인수인계 시 권장 요청문

> 현재 DB 기준 문서는 `animal_service_db_spec_v8.md`이고 Q393~Q405의 최신 확정 내용까지 반영했습니다(33개 현행 테이블). 기존 v7보다 최신 결정을 우선하십시오. 알림은 SYSTEM을 포함한 5종이고 AI 매칭 식별은 notifications의 기존 target_type/target_seq를 활용합니다. 입양 템플릿·버전·과거 질문 이력은 없으며 질문 식별 question_seq를 답변 JSONB 키로 사용합니다. 댓글/채팅 이미지 연결 테이블 4개는 제거했고 구조 정보는 rescue_records에서 JOIN 조회합니다. 회원당 ACTIVE 실종 신고는 최대 1건입니다. 이미지 임베딩 상세, 모의 입양, 보호소 이관, 차단, 보호소 삭제 엣지케이스는 재질문하지 마십시오. 추가 명세 변경은 먼저 질의응답으로 확정하고 사용자가 요청할 때만 새 버전 파일을 생성하십시오.

---
**버전:** v8  |  **기준:** Q393~Q405 확정(Q395는 유지; Q399 선행 회원당 ACTIVE 신고 제한 및 Q402 추가 결정 포함)  |  **현행 테이블:** 33개  |  **작성 목적:** 전체 테이블 명세 + 개념 제약 SQL + 충돌 처리 + 결정 추적 + 설계 인수인계 일체형
