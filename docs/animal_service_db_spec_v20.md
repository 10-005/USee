# 유실·발견동물·보호소·입양 플랫폼 DB 통합 명세서 — v20

> **기준일:** 2026-11-09  |  **기반:** `animal_service_db_spec_v19.md` 전체 통합 명세 + **Q490~Q496 확정 결정(2026-11-09 모의 입양 구현 중 확정: 성격 배율은 시간 감소에만, 36번 `category` 14종·물품 시드 35개, 묶음 물품 사용 횟수 코드 설정, 목욕은 위생 관리 키트, 39번 `GIVE_CATNIP`·캣닢 남용, 산책 리드줄 필수, 세션 종료 처리 세부. 스키마 변경은 36번 `category` CHECK 확장뿐, §14)** + **Q497(같은 날 리포트 요약은 규칙 문구로 먼저·LLM은 나중에 교체, 요약 고아 복구 기준은 44번 `reg_dttm`. 스키마 변경 없음)** + **Q498(같은 날 41번 시나리오 15개 시드·RANDOM 발생 규칙·43번 만료 시각 기준 방치 처리. 스키마 변경 없음)** + **Q499(같은 날 CONDITION은 스케줄러에서만·RANDOM보다 먼저 판정. 스키마 변경 없음)**. 짝이 되는 API 문서는 `animal_service_api_spec_v14.md`. 아래는 v19 기준 문구이며 v20 변경은 "v20 반영 변경사항 요약"이 우선합니다.  
> **v19 기준일:** 2026-11-06  |  **v19 기반:** `animal_service_db_spec_v18.md` 전체 통합 명세 + Q485~Q487 확정 결정(2026-11-06 모의 입양 스탯 개명·방향 통일, 돌발이벤트를 선택지 2개에서 정답 행동 1개로 재구성, 리포트 요약 완료 후 `SESSION_END` 푸시(Q487, 스키마 변경 없음), §14. **스키마 변경 있음:** `simulation_sessions`의 스탯 컬럼 `hunger`·`friendship`·`stress`·`activity`를 `satiety`·`bond`·`calmness`·`energy`로 개명하고 7종 모두 높을수록 좋음으로 통일, `ck_sim_session_stats` 재작성, 스탯 키를 담는 JSONB의 키·부호 변경(Q485); **42번 `simulation_event_choices` 삭제**, 41번 `simulation_event_scenarios`에 정답 행동·비용·해결/방치 효과·문구 컬럼 6개 추가, 43번 `simulation_event_occurrences` 상태 `PENDING/RESOLVED/EXPIRED`·`resolved_dttm`·`resolved_action_log_seq`, 44번 `simulation_reports.event_resolved_count` 추가, 39번 `action_code` 값 변경(Q486)) + **Q488~Q489 확정 결정(2026-11-08 산책 개 전용·종 배율 삭제, 불필요한 `VET_VISIT` 409 유지. 둘 다 스키마 변경 없음)**. v18까지의 Q406·Q413~Q484·R1~R16·S1~S8·M1~M17 결정은 그대로 계승(M11·M15의 선택지 구조는 Q486으로, M12의 고양이 산책은 Q488로 대체). 짝이 되는 API 문서는 `animal_service_api_spec_v13.md`(v12 + Q485~Q489)  |  **구분:** 통합 설계 명세 및 개념 SQL(실행 DDL 아님)  |  **현행 테이블:** 42개(v18 43개 − `simulation_event_choices`)  |  **스키마:** 2개(`core` 33개 테이블, `simulation` 9개 테이블)

## 0. 문서 적용 원칙 및 변경 범위

- **최신 사용자 확정 결정이 이전 v5~v9 문구나 UX 초안보다 우선**합니다. Q263~Q429 및 M1~M17의 확정·스킵 중 후속 결정이 앞선 결정에 우선합니다. 특히 Q406·Q413~Q429·S1~S8은 v9의 [미정]·보류 항목을 모두 대체합니다.
- 테이블/컬럼 중 **[확정]**, **[가안]**(추천안을 임시 채택하거나 코드 설정값으로 둔 것)을 구별합니다. **v19 시점에 [미정] 항목은 없습니다**(팀 논의 대기 항목은 §9 "v19 팀 논의 대기"에 따로 둠). 원본의 PK 자료형과 일부 선택 컬럼·인덱스 이름은 물리 구현 초안일 수 있습니다. v7에서 제거된 3개와 v8에서 제거된 5개 테이블은 현재 모델에 포함하지 않습니다.
- 현재 구현 범위에 포함하지 않는 것은 보호소 간 이관, 사용자 차단, 관리자(ADMIN) 기능 일체, 관리자의 신고 심사, 신고 내역 조회(Q428), 이미지 임베딩 상세 설계 등입니다. **모의 입양은 v9부터 구현 범위에 포함**되었습니다(M1~M17).
- **Q406(행정구역 코드)·Q412(초기 데이터 적재 경로)·Q413~Q419(인증·공통 규약)·Q420~Q429(구조·정책)·S1~S8(백엔드 스택)이 모두 [확정]**되었습니다. Q336·Q356 등 의도적으로 스킵한 항목은 그대로 범위 밖입니다.
- DB 제약이 가능한 것은 UNIQUE/FK/CHECK로 기술하고, 여러 행/테이블·권한·API 동작은 BE 검증 및 트랜잭션으로 분리합니다. **스키마를 넘는 참조는 FK 없이 논리 참조**로 두고 BE가 검증합니다(Q420).
- 백엔드는 Kotlin + Spring Boot 4개 서비스(core-api·chat·notification·simulation), PostgreSQL 1개에 스키마 2개(`core`·`simulation`), Redis, RabbitMQ, AWS S3가 확정입니다. refresh token과 카카오 가입 컨텍스트는 Redis TTL로만 저장하고 DB 테이블을 두지 않습니다(Q409 B·Q414 A).

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

### v9 반영 변경사항 요약 (Q406~Q412, M1~M17)

| 영역 | 최신 v9 확정 정책 | 근거 |
|---|---|---|
| 문서 | v9 DB / v2 API 신규 파일 생성, v8/v1 보존 | Q0-1 |
| 스택 | Kotlin + Spring Boot, Redis 사용 [확정]. v9 당시 RabbitMQ·OpenSearch·S1~S8 보류 → **v10에서 S1~S8 확정** | Q0-2 |
| 행정구역 코드 | v9 당시 [보류]. **v10에서 Q406 A로 확정**(아래 v10 요약 참조) | Q406 |
| 이미지 업로더 | `images.reg_usr_seq INTEGER NULL FK→users` 추가. 공공데이터·시드 적재분은 NULL. 모든 이미지 연결은 업로더 본인만 허용 | Q407 |
| 프로필 이미지 | `users.profile_image_key TEXT` 삭제 → `users.profile_image_seq BIGINT NULL FK→images`. 공통 업로드·마지막 참조 해제 정책(Q383)으로 통일. 카카오 프로필 사진·이메일 미수집 | Q408·Q408-1 |
| refresh token | Redis TTL 저장, DB 테이블 없음. [가안] 키 `auth:refresh:{userSeq}:{jti}`, TTL 14일, access 1시간, rotation | Q409 |
| ADMIN | 이번 프로젝트 구현 범위 외. `users.role` CHECK의 `ADMIN`은 예약값으로 유지하되 계정 발급·권한·API 모두 미구현. 구조 접수·보호동물 등록은 SHELTER 전용 | Q410·Q411 |
| 초기 데이터 | 보호소·SHELTER 계정·행정구역 배정·동물 카탈로그·입양 질문·모의 입양 상점/시나리오는 **API로 관리하지 않음** [확정]. 적재 경로는 v10에서 Flyway로 확정 | Q412 |
| 종·품종 | `animal_species.species_code VARCHAR(10) NOT NULL UNIQUE` CHECK `DOG/CAT/ETC`. `animal_breeds.personality VARCHAR(20) NULL` CHECK 4종 | M7·M8 |
| 모의 입양 | `simulation_` 접두 11개 테이블 신설(v9 34~44번, v10 재번호 후 35~45번). 실제 보호동물에서 시작, 실시간 7일, 스탯 7종 지연 계산, 펫샵·인벤토리·거래, 시나리오→선택지 2개 이벤트 엔진(RANDOM/CONDITION), 산책, 리포트, 별도 알림 | M1~M17 |
| 알림 | 일반 `notifications`는 변경 없음. 모의 입양 알림은 `simulation_notifications`로 완전 분리 | M13·M16 |
| 테이블 수 | 33개 → **44개**(모의 입양 11개 추가) | M1~M17 |

### v10 반영 변경사항 요약 (Q406, Q413~Q429, S1~S8)

| 영역 | 최신 v10 확정 정책 | 근거 |
|---|---|---|
| 문서 | v10 DB / v3 API 신규 파일 생성, v9/v2 보존 | 2026-11-02 |
| 스키마 | PostgreSQL 1개에 **스키마 2개**. `core`(core-api·chat·notification 공유, 34개 테이블, FK 그대로) + `simulation`(simulation 전용, 11개). 교차 스키마 FK 없음, 교차 읽기 JOIN 허용, 쓰기는 자기 스키마만 | Q420 D |
| DDL·초기 데이터 | **Flyway**. 스키마마다 마이그레이션 1벌(core-api가 `core`, simulation이 `simulation`), chat·notification은 `ddl-auto=validate`. 시드(보호소·계정·행정구역·카탈로그·입양 문항·상점·시나리오)는 마이그레이션에 포함 | Q412 A |
| 행정구역 코드 | `shelters`·`missing_reports`·`found_reports`에 `district_code VARCHAR(5) NULL`(법정동 코드 앞 5자리) 추가. 서버가 네이버 역지오코딩으로 도출, 실패 시 NULL. 행정구역 마스터 `districts` 신설(07번, 이후 테이블 번호 +1, 총 45개) | Q406 A·Q406-1 A·Q406-2 A |
| 모의 입양 참조 | `simulation_*`의 `user_seq`·`animal_seq`는 논리 참조(FK 없음). `simulation_sessions`에 `shelter_seq`와 표시용 동물 스냅샷 컬럼 추가. `simulation_shop_items.image_seq` → `image_key` | Q420 D |
| 인증·가입 | 카카오는 앱 SDK access token을 서버가 검증하는 2단계 가입. `registrationToken`은 Redis `auth:register:{token}` TTL 10분. RS256, core-api만 발급 | Q413 A·Q414 A |
| 공통 규약 | 응답 봉투·204 미사용·시간 `+09:00`·페이징·에러 코드 명명은 백엔드 스켈레톤 코드 기준으로 확정(API v3 1장) | Q415~Q418 |
| 도메인 정책 | 신고 작성 USER만(Q421), DRAFT는 SUBMITTED 이후만 보호소 노출(Q422), 수정·삭제 작성자 본인(Q423), 사진은 전체 배열 교체(Q424), CLOSED 신고 읽기 전용(Q425), 알림 최소 집합(Q426), 저장소 삭제 best-effort(Q427), 신고 내역 조회 범위 외(Q428), 튜닝값 코드 설정(Q429), 로그아웃 시 `fcmToken` 선택 필드(Q419) | Q419·Q421~Q429 |
| 스택 | JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, JPA+QueryDSL, PostgreSQL 17, Redis 7.4, RabbitMQ(이벤트·STOMP relay), AWS S3(presigned PUT), OpenSearch 미도입, 위치 검색 하버사인, 스케줄러 simulation `@Scheduled` 10분 | S1~S8 |
| 테이블 수 | 44개 → **45개**(`districts` 추가) | Q406-1 |

### v11 반영 변경사항 요약 (Q446~Q453)

| 영역 | 최신 v11 확정 정책 | 근거 |
|---|---|---|
| 문서 | v11 DB / v4 API 신규 파일 생성, v10/v3 보존 | 2026-11-03 |
| 알림 이동 정보 | `notifications`에 `comment_seq`·`parent_comment_seq BIGINT NULL` 추가(FK 없는 다형성). COMMENT 계열 알림은 `target_type/target_seq`=댓글이 달린 게시물, 댓글·루트 댓글은 두 컬럼. 앱은 알림 진입 시 해당 댓글을 목록 최상단에 고정(API v4 13.6) | Q446 |
| 알림 유형·문구 | `notification_type` 5종 → **9종**(`COMMENT/COMMENT_REPLY/AI_MATCH/ADOPTION_SUBMITTED/ADOPTION_APPROVED/ADOPTION_REJECTED/ADOPTION_COMPLETED/SHELTER/SYSTEM`). title은 저장하지 않고 서버 enum 고정 문구, `message`는 body 스냅샷(템플릿 [가안]). `actor_user_seq` 삭제 | Q447 |
| 삭제 댓글 알림 | 댓글 소프트 삭제 시 `CommentDeleted` 이벤트로 notification이 관련 COMMENT 계열 알림의 `message`를 "삭제된 댓글입니다"로 갱신(쓰기 소유권 유지) | Q448 |
| 신고 | `reports.target_type`에서 `SHELTER_ANIMAL` 제거(6종). `reason_detail` 삭제, `reason_code VARCHAR(30) NOT NULL` 재도입(Q308 대체), 대상·사유 조합 CHECK | Q449 |
| API 전용 | 본문 없는 성공 204(Q450), `GET /users/me` USER 전용(Q451), `pet` 패키지를 `user`로 통합·경로 `/users/me/...`(Q452), API 5장 "기준정보 (CATALOG)"(Q453). **DB 영향 없음** | Q450~Q453 |
| 테이블 수 | 45개 유지(컬럼 변경만) | - |

### v12 반영 변경사항 요약 (Q454~Q461)

| 영역 | 최신 v12 확정 정책 | 근거 |
|---|---|---|
| 문서 | v12 DB / v6 API(파일 내 제목은 "v5"로 표기됐으나 저장소의 v5와 별개) 신규 파일 생성, v11/v4 보존 | 2026-11-04 |
| 채팅 미읽음·목록 | 미읽음은 **조회 시 계산**(카운터 컬럼 미채택). 정의: 내 읽음·숨김 포인터 이후 + 본인 발신 제외. 목록은 숨김 포인터 이후 메시지가 있는 방만 노출. `chat_participants`에 참여자 기준 조회 인덱스 2개 추가 | Q454 |
| STOMP 목적지 | `.` 구분자 전환 검토 후 **미반영**(현행 `/app/rooms/{id}/messages`·`/topic/rooms/{id}` 유지, RabbitMQ relay 연동 시 테스트) | Q455 |
| 댓글 | DB 변경 없음(작성자 프로필·정렬·커서는 API) | Q456 |
| 업로드 설계 정리 | 인프라 티켓(`public/tmp/` 이동·CloudFront)과 명세 정렬은 **보류**(팀 논의) | Q457 |
| 보호동물 | `shelter_animals.weight NUMERIC(5,2) NULL`(kg), `registration_number VARCHAR(20) NULL`(정부 공식 동물등록번호, 보호소 입력) 추가 | Q458 |
| 입양 문항 | `question_type`에 `DATE` 추가, 현재 문항 **20개**, `question_seq`는 시드에서 1~20 고정값 | Q458 |
| 입양 신청서 | `care_address`·`care_address_detail`·`email`·`visit_hope_date` 추가(NULL 허용, 제출 시 장소·희망일 필수). DRAFT 없이 바로 SUBMITTED INSERT 허용 | Q458 |
| 수료증 | 기능 제외. `certificate_storage_key`는 컬럼만 남기고 미사용(항상 NULL), `certificates/` 비공개 업로드·presigned GET 없음 | Q460 |
| 구조 기록 | `rescue_reason` → `rescue_reason_code NOT NULL`(4종), `rescue_address` NOT NULL, `rescue_address_detail` 추가, `rescued_date DATE` → `rescued_dttm TIMESTAMPTZ NOT NULL`. 장소·일시는 접수 시 필수 | Q461 |
| API 전용 | 신청 내역 `displayDttm`(Q459), 보호소 입양 건수·CANCELED 비노출(Q460) 등. DB 영향 없음 | Q459·Q460 |
| 테이블 수 | 45개 유지(컬럼 변경만) | - |

### v13 반영 변경사항 요약 (Q462~Q467)

| 영역 | 최신 v13 확정 정책 | 근거 |
|---|---|---|
| 문서 | v13 DB / v7 API 신규 파일 생성, v12/v6 보존. API v6이 v4에서 분기돼 빠졌던 v5의 Q454(경로 접두어 = 도메인 패키지)를 API v7에서 복원(DB 영향 없음) | 2026-11-04 |
| 수료증 | v12의 "컬럼만 유지"를 넘어 **`adoption_applications.certificate_storage_key` 컬럼 삭제**. `certificates/` 접두어·`upload:file:*` Redis 키 없음 | Q462 |
| 실종 신고 | **`missing_reports.name` 삭제**(동물 이름 없음), **`missing_reports.pet_seq` 삭제**(반려동물 연결·스냅샷·소유자 검증 없음). `pets` ↛ `missing_reports` | Q465·Q466 |
| 신고 검색 | 7.1/8.1 필터는 `status`·`occurred_dttm` 날짜 범위·`district_code`·종·`gender`·`is_neutered`·`description` ILIKE·`reg_dttm` 정렬. 품종·색상·보호소 필터 삭제. **포함 매칭:** `gender` 필터는 `UNKNOWN` 포함, `is_neutered` 필터는 NULL 포함. pg_trgm 가안 대상은 `description`만 | Q463 |
| 내 신고·담당 목록 | 7.3/8.3은 전량 배열. 8.8은 `status`·최근 N일(`occurred_dttm`)·`district_code`·종·**구조 상태(`rescue_records` 최신 비삭제 행 파생: NONE/PROCESSING/DONE/ABORTED)**·`reg_dttm` 정렬. DB 컬럼 추가 없음 | Q464 |
| 주소 검색 | API 5.6 `GET /geocode` 프록시(네이버 Geocoding). 등록은 현행 `address`·`latitude`·`longitude` 그대로, 상세 주소 컬럼 추가 없음. DB 영향 없음 | Q467 |
| 테이블 수 | 45개 유지(컬럼 3개 삭제) | - |

### v14 반영 변경사항 요약 (Q468~Q469)

| 영역 | 최신 v14 확정 정책 | 근거 |
|---|---|---|
| 문서 | v14 DB / v8 API 신규 파일 생성, v13/v7 보존 | 2026-11-04 |
| 신고 목록 응답 | 7.1 실종 목록에 `age`·`is_neutered`·`weight`, 8.1 발견 목록에 `gender`·`age`·`is_neutered`·`weight`·`reg_dttm`을 포함(7.1과 동일 구성). 7.3·8.3·8.8도 같은 목록 필드. **DB 컬럼 추가는 `weight`뿐**(아래 Q469), 나머지는 기존 컬럼을 목록 SELECT에 올리는 것 | Q468 |
| 몸무게 통일 | **`weight`는 네 동물 테이블 모두 `VARCHAR(30) NULL` 자유 텍스트.** `missing_reports.weight`·`found_reports.weight` 신설(선택). `shelter_animals.weight`는 Q458의 `NUMERIC(5,2)` kg 숫자를 `VARCHAR(30)`으로 변경하고 CHECK `weight > 0`(`ck_shelter_weight_positive`) 삭제. 앱의 숫자 변환 없음, 공공데이터 "3(Kg)"은 문자열 그대로 적재. `pets.weight`(Q362)는 이미 같은 형식 | Q469 |
| 테이블 수 | 45개 유지(컬럼 2개 추가, 1개 타입 변경) | - |

### v15 반영 변경사항 요약 (Q470~Q472)

| 영역 | 최신 v15 확정 정책 | 근거 |
|---|---|---|
| 문서 | v15 DB / v9 API 신규 파일 생성, v14/v8 보존. **컬럼·제약·인덱스 변경 없음**(조회 SELECT·응답 규칙만) | 2026-11-04 |
| 담당 목록 신고자 | API 8.8 각 항목에 신고자 `reporter{name, phone}`. `name`은 **`users.name` 실명**(닉네임 아님), `phone`은 `found_reports.reporter_phone`. 담당 보호소 범위이므로 둘 다 노출. 목록 SELECT에 `users` JOIN 추가 | Q470 |
| 구조 목록 | API 10.1 기간 필터를 날짜 범위에서 **최근 7/30/90일 프리셋(`rescued_dttm` 기준)**으로, size 기본 8. **상태별 건수는 목록 응답에 동봉**(`status` 제외 필터로 `GROUP BY status`)하고 API 10.7 `/rescues/status-counts`는 삭제. 항목에 `rescue_memo`와 동물 `is_neutered` 포함. 동물 정보 출처(REPORT=`found_reports`, DIRECT=연결 `shelter_animals` 최초 1마리)는 현행 유지 | Q471 |
| 구조 상세 | API 10.2 연결 발견 신고 요약에 `is_neutered`, 신고자는 `users.name` 실명 + `reporter_phone`(v14까지 닉네임) | Q472 |
| 테이블 수 | 45개 유지(스키마 변경 없음) | - |

### v20 반영 변경사항 요약 (Q490~Q499)

| 영역 | 최신 v20 확정 정책 | 근거 |
|---|---|---|
| 문서 | v20 DB / v14 API 신규 파일 생성, v19/v13 보존. **스키마 변경은 36번 `category` CHECK 확장 1건**(Flyway `V20261109_1057__extend_simulation_item_categories`), 나머지는 시드 데이터·코드 설정 | 2026-11-09 |
| 성격 배율 시간 감소 한정 | 성격 배율은 **tick당 감소에만** 곱함(실수 배율, 세션 시작 기준 누적 버림으로 계산을 나눠도 결과 동일). 돌봄·산책·이벤트 효과(36번 `effects_jsonb`, 41번 해결·방치 효과)에는 곱하지 않음. 회복 배율(TIMID 친밀도 0.7·FRIENDLY 친밀도 1.5·ACTIVE 산책 1.3) 삭제. AGGRESSIVE 짖기·싸우기 가중치 2는 WALK 시나리오 추첨 가중치(252·256에서 결정). M4 축소, **컬럼 변경 없음** | Q490 |
| 펫샵 분류 14종·시드 | **36번 `category` CHECK 9종 → 14종**: 기구 7종 `FOOD_BOWL/WATER_BOWL/LITTER_BOX/TOY/FURNITURE/LEASH/GROOMING_KIT`(`is_consumable=FALSE`), 소모품 7종 `FOOD/SNACK/LITTER/SHAMPOO/MEDICINE/POOP_BAG/CATNIP`. `ck_sim_item_consumable`도 기구 7종 기준으로 재작성. 기존 분류는 지우지 않고 늘림. 초기 물품 35개 시드(`V20261109_1110`, 가안 가격·효과, `image_key` 비움). M9 확장 | Q491 |
| 묶음 물품 사용 횟수 | 1개 구매 = N회 사용(사료 12끼·화장실 모래 10회·상비약 3회 등 [가안])을 **스키마 변경 없이** 코드 설정 `simulation.shop.uses-per-purchase`(item_seq→N, 없으면 1)로 관리. 37번 `quantity` += 구매 수 × N(남은 사용 횟수), 38번 PURCHASE `quantity`는 산 개수 | Q492 |
| 목욕은 위생 관리 키트 | `BATHE`는 `GROOMING_KIT` 보유만 확인하고 소모 없이 무제한(의도), 효과는 키트 `effects_jsonb`(청결도 +40·안정감 -5 [가안]). 종전 "SHAMPOO 1개" 대체. 순한 샴푸(34번 물품)는 `is_active=false`로 판매 중단(`V20261109_1316`), 이미 산 37번 행은 유지. 물 주기 효과는 코드 설정(수분 +50 [가안]) | Q493 |
| 장난감 선택·캣닢 | `PLAY`는 보유 TOY 중 고른 것의 효과, 39번 `item_seq`에 고른 장난감 기록. **39번 CARE `action_code`에 `GIVE_CATNIP` 추가**(고양이 전용 `CATNIP` 1개 소모, 효과 +·- 혼합 `V20261109_1451`). 직전 `GIVE_CATNIP` 뒤 6시간 [가안] 안이면 남용으로 캣닢 효과 대신 안정감 -20 [가안](39번 로그에서 판정, 이벤트 행 없음). `action_code`는 BE 검증이라 **DDL 변경 없음** | Q494 |
| 산책 리드줄 필수 | 산책(40번) 시작은 `LEASH` 보유가 조건(252에서 구현). `POOP_BAG`는 산책용 소모품. **컬럼 변경 없음** | Q495 |
| 세션 종료 처리 세부 | 조회·행동·구매·포기·스케줄러 모두 35번 `FOR UPDATE` 후 지연 계산 → 건강 0이 된 tick 시각에 FAILED, `ends_dttm` 경과면 그 시각에 COMPLETED(`ended_dttm`). 쓰기 요청 중 종료돼도 종료는 커밋(API 409). 44번 `spent_by_category_jsonb`는 분류 14종+`EVENT` 키를, `daily_spent_jsonb`는 `"1"`~`"7"`을 모두 채움(0 포함), `action_count`는 39번 CARE+WALK 행 수. 스케줄러는 세션별 트랜잭션. `UserDeleted`는 큐 `simulation.UserDeleted`로 소비해 ABANDONED 종료(재전달 무해). **컬럼 변경 없음** | Q496 |
| 리포트 요약 규칙 문구 우선 | 44번 `summary`는 요약 생성기 포트로 만들며 지금은 **규칙 문구 구현만**(LLM은 나중에 같은 포트로 교체, 교체 후에도 실패·빈 문구면 규칙 문구). 저장은 생성기 호출과 분리된 짧은 트랜잭션의 `UPDATE ... WHERE summary IS NULL`. **고아 복구 기준을 `ended_dttm`에서 44번 `reg_dttm`(리포트 생성 시각) + 10분 [가안]으로** 변경(예정 종료를 지나 늦게 종료 처리된 세션이 비동기 요약과 겹치지 않도록). **컬럼 변경 없음** | Q497 |
| 돌발 이벤트 시드·발생 규칙 | **41번 시나리오 15개 시드**(`V20261109_1800`: HOME RANDOM 7·HOME CONDITION 5(병원 `resolve_cost` 30,000원 포함)·WALK 3(개 전용)). 해결 효과(`resolve_effects_jsonb`)에도 활동한 만큼의 대가가 있을 수 있음(예: 놀아 주기 안정감 +·포만감·활력 -). HOME·RANDOM은 스케줄러 판정마다 확률 0.0104 [가안](하루 평균 약 1.5회)으로 43번 행 생성: PENDING이 없고 22~8시가 아니며 세션 일차(시작 기준 24시간)당 2회 미만일 때만, 종·성격 범위가 맞는 시나리오를 `weight`로 추첨(일차당 횟수는 43번 `occurred_dttm`과 41번 유형으로 집계). 만료는 35번 스탯을 `expires_dttm`까지 먼저 계산한 뒤 방치 효과 반영 → 39번 `EVENT_EXPIRE` → EXPIRED(건강 0이면 만료 시각에 FAILED). 해결·방치 효과에 성격 배율 없음(Q490). 종료 시 대기 이벤트 만료·리포트 이벤트 수는 JDBC 직접 갱신에서 엔티티로 옮김. **컬럼 변경 없음** | Q498 |
| CONDITION 발생 규칙 | 41번 CONDITION 시나리오는 **스케줄러 판정에서만**(조회 시점 판정 안 함) RANDOM보다 먼저 판정. PENDING 없음·22~8시 제외, 조건식(`<`·`<=`·`>`·`>=`)이 맞고 쿨다운(`cooldown_hours`, NULL이면 설정 24시간 [가안], 마지막 발생은 43번 `ix_sim_occurrence_scenario`로 조회)이 지난 시나리오를 `weight`로 고른 뒤 `trigger_probability`로 생성. 하루 횟수 제한 없음. **컬럼 변경 없음** | Q499 |
| 테이블 수 | 42개 유지(`core` 33 + `simulation` 9) | - |

### v19 반영 변경사항 요약 (Q485~Q489)

| 영역 | 최신 v19 확정 정책 | 근거 |
|---|---|---|
| 문서 | v19 DB / v13 API 신규 파일 생성, v18/v12 보존. **스키마 변경 있음**(Q485: 컬럼 개명 4개·CHECK 재작성 1개. Q486: 테이블 1개 삭제, 컬럼 6개 추가(41번)·2개 교체(43번)·1개 추가(44번), CHECK·FK 변경) | 2026-11-06 |
| 스탯 개명·방향 통일 | **35번 `simulation_sessions`** 스탯 컬럼 `hunger`→`satiety`(포만감), `friendship`→`bond`(친밀도), `stress`→`calmness`(안정감, **방향 반전**), `activity`→`energy`(활력). `hydration`(수분)·`cleanliness`(청결도)·`health`(건강)는 유지. **7종 모두 0~100, 높을수록 좋고 시간이 지나면 떨어짐**. `ck_sim_session_stats` 재작성. 100일 때 라벨(배부름·수분 충분·깨끗함·매우 친밀함·편안함·매우 건강함·활기참)은 앱 상수이며 DB·API에 두지 않음 | Q485 |
| JSONB 키·시드 부호 | 스탯 키를 담는 `simulation_shop_items.effects_jsonb`·`simulation_event_scenarios.trigger_condition_jsonb`·`simulation_action_logs.stat_delta_jsonb`/`stats_after_jsonb`·`simulation_reports.final_stats_jsonb`의 키를 새 이름으로. `stress`를 쓰던 시드 값은 부호를 뒤집어 `calmness`로(SNACK `calmness:+5`, SHAMPOO `calmness:-5`, 조건식 `stress>70`→`calmness<30`) | Q485 |
| 코드 설정 가안 | `calmness` 초기 50 / tick -2. 건강 추가 감소 조건 `stress>=80`→`calmness<=20`. 성격·종 배율의 "스트레스 증가"는 "안정감 감소"로, "활동"은 "활력"으로 | Q485 |
| 돌발이벤트 재구성 | **42번 `simulation_event_choices` 삭제**(43→42개, `simulation` 9개). 시나리오(41번)에 **정답 행동 1개** `resolve_action_code VARCHAR(30) NOT NULL`(HOME: 돌봄 행동 7종 + `VET_VISIT`, WALK: `GIVE_WATER/GIVE_SNACK`), `resolve_cost INTEGER NOT NULL DEFAULT 0`(병원만 >0), `resolve_effects_jsonb`·`result_text`(해결), `neglect_effects_jsonb`·`neglect_text`(방치) 추가. 선택지 응답 API(18.13) 삭제, 이벤트는 **행동(API 18.6·18.16) 처리 안에서** `resolve_action_code = action_code`(WALK는 같은 `walk_seq`)이면 해결. 오답은 패널티 없음, 시한 만료 시 `neglect_effects_jsonb` 적용 후 EXPIRED(산책 종료 시 동일, 세션 종료 시는 효과 없음) | Q486 |
| 이벤트 발생 행·로그 | 43번 `status` CHECK `PENDING/RESOLVED/EXPIRED`(`ANSWERED` 폐기), `chosen_choice_no`·`answered_dttm` 삭제 → `resolved_dttm`·`resolved_action_log_seq BIGINT NULL FK→simulation_action_logs`(해결한 CARE/WALK 로그) 추가, `ck_sim_occurrence_status` 재작성. 39번 `action_code`: CARE에 `VET_VISIT` 추가, EVENT는 `EVENT_EXPIRE`만(`EVENT_ANSWER` 삭제), CARE/WALK 행의 `occurrence_seq`=해결한 이벤트. 38번 `EVENT` 거래는 `VET_VISIT` 진료비(`resolve_cost`). 44번 `event_resolved_count INTEGER NOT NULL` 추가 | Q486 |
| 리포트 요약 완료 후 푸시 | 세션 종료 트랜잭션은 상태·44번 리포트(`summary=NULL`)·로그만 반영. 커밋 후 비동기 LLM `summary` UPDATE(`WHERE summary IS NULL`) → **갱신된 경우에만** `SESSION_END` 푸시 이벤트 발행(종전 Q481·Q444는 커밋 직후 푸시). 실패 시 규칙 문구 폴백이라 푸시는 반드시 1회. 스케줄러가 `summary IS NULL`·종료 후 10분 [가안] 지난 리포트를 복구. **컬럼 변경 없음** | Q487 |
| 산책 개 전용 | 산책(40번 `simulation_walks`)은 세션 `species_type='DOG'`일 때만 생성. BE가 API 18.14에서 검증하고 CAT이면 409 `SIMULATION_WALK_NOT_FOR_SPECIES`(앱은 고양이 세션의 산책 버튼 비활성화). M12의 "고양이도 산책 가능, 종 배율로 손해"와 코드 설정 종 배율(CAT 안정감 감소 2.0·활력 회복 0.5) 삭제, 산책 효과는 길이·성격 배율만. 고양이 세션의 44번 `walk_count`는 0. **컬럼·제약 변경 없음**(종 컬럼을 40번에 두지 않음) | Q488 (2026-11-08) |
| 불필요한 병원 방문 409 유지 | 정답이 `VET_VISIT`인 PENDING 이벤트가 없을 때의 `VET_VISIT`은 성공·무변화 응답으로 바꾸지 않고 409 유지(39번 로그·`last_action_dttm` 갱신 없음). 확인 결정, **변경 없음** | Q489 (2026-11-08) |
| 테이블 수 | 42개(`core` 33 + `simulation` 9). 번호는 유지하고 30번·42번·45번을 빈 번호로 둠 | - |

### v18 반영 변경사항 요약 (Q481~Q484)

| 영역 | 최신 v18 확정 정책 | 근거 |
|---|---|---|
| 문서 | v18 DB / v12 API 신규 파일 생성, v17/v11 보존. **스키마 변경 있음**(테이블 1개 삭제, 컬럼 5개 삭제·4개 추가, NOT NULL 1개, CHECK 변경) | 2026-11-06 |
| 모의 입양 알림 저장 폐지 | **45번 `simulation_notifications` 테이블 삭제**(44→43개, `simulation` 10개). 모의 입양 푸시는 DB 저장 없이 FCM만(`EVENT`·`SESSION_END` 2종, 앱을 모의 입양 화면으로 유도하는 용도까지). `NEGLECT`·`STAT_*_WARNING` 알림 폐지. 세션 종료 트랜잭션은 상태·리포트·로그만 반영하고 푸시 발행은 커밋 후. `ck_sim_notification_type` 제거 | Q481 |
| 이벤트 확률 발생 | 스탯 저하는 알림이 아니라 스케줄러의 CONDITION 판정으로 이벤트화. `simulation_event_scenarios.trigger_probability NUMERIC(3,2) NOT NULL DEFAULT 1.00` 추가(0 초과 1 이하, CONDITION 조건 충족 시 발생 확률, 시드). 쿨다운은 종전대로 `simulation_event_occurrences` 마지막 발생으로 판정 | Q481 |
| 품종 기반 시작 | 세션은 실제 보호동물과 연결하지 않음. `simulation_sessions`에서 `animal_seq`·`shelter_seq`·`animal_breed_name`·`animal_color_name`·`animal_image_key` **삭제**, `breed_seq INTEGER NOT NULL`(논리 참조 → `core.animal_breeds`, FK 없음) **추가**. 종·성격 스냅샷(`species_type`·`personality`)은 유지. `animal_breeds.personality`는 **NOT NULL**(ETC 품종 포함 시드 전량 배정, 랜덤 배정 폐기), `animal_breeds.representative_image_seq BIGINT NULL` FK→`images` 추가(품종당 대표 이미지 1장, 시드·`reg_usr_seq` NULL, 참조 정리 대상 아님). simulation이 읽는 core 테이블은 `users`·`animal_breeds`·`animal_species`·`images`로 축소(`shelter_animals`·`shelter_animal_images` 제외) | Q482 |
| 보호소 열람 전면 허용 | 세션에 `shelter_seq`가 없어지므로 "자기 보호동물 세션" 기준 폐기. SHELTER는 종료 리포트를 누구 것이든 열람(API 18.18·18.20, 12.15는 신청서 `user_seq` 기준 전체). 진행 중 세션·세션 상세·이력·지출은 본인 전용 유지. 컬럼 변경 없음(조회 규칙) | Q483 |
| 가상 이름 | `simulation_sessions.animal_name VARCHAR(6) NOT NULL` 추가(시작 시 필수 입력, 변경 없음. 한글 제한은 프론트 검증) | Q484 |
| 테이블 수 | 43개(`core` 33 + `simulation` 10). 번호는 유지하고 30번·45번을 빈 번호로 둠 | - |

### v17 반영 변경사항 요약 (Q476~Q479)

| 영역 | 최신 v17 확정 정책 | 근거 |
|---|---|---|
| 문서 | v17 DB / v11 API 신규 파일 생성, v16/v10 보존. **스키마 변경 있음**(테이블 1개 삭제, 컬럼 4개 추가, CHECK 2개 축소) | 2026-11-05 |
| 보호동물 상세 보호소 블록(11.2) | 앱 상세의 `shelter` 블록을 `shelters` JOIN으로 `shelter_seq`·`shelter_name`·`phone`·`address` 4개로 구성(목록은 `district_code`형 유지). 컬럼 변경 없음 | Q476 |
| 보호동물 댓글 제거 | **30번 `shelter_animal_comments` 테이블 삭제**(45→44개, `core` 33개). 댓글은 실종·발견 2종만. `reports.target_type`에서 `SHELTER_ANIMAL_COMMENT` 제거(6종→5종, `ck_report_target_type`·`ck_report_reason_by_target` 축소), `notifications` COMMENT 계열 `target_type`은 `MISSING`/`FOUND`만(`ck_notification_comment_ref`·`ck_notification_target_type` 축소). 공통 코드 `SHELTER_ANIMAL_COMMENT` 폐기. 11.2 응답에서 댓글 수 제거 | Q477 |
| 보호소 전용 상세(11.10) | 토큰 보호소 소속 보호동물 1건을 관리 정보와 함께 조회: 목록 항목 + 상세 컬럼 + `animal_returns` LEFT JOIN(반환 정보) + 완료 신청서 `applicant_name`·`adoption_reason`·`adoption_memo`(입양 정보) + `adoption_applications` 상태별 건수(DRAFT·CANCELED 제외). 북마크·모의 입양 파생 없음. 컬럼 변경 없음(조회 규칙) | Q478 |
| 상태·공고기간 관리(11.6) | **보호 상태는 어느 상태에서든 전환 허용.** `shelter_animals.adoption_reason VARCHAR(200) NULL`·`adoption_memo TEXT NULL` 추가(ADOPTED 처리 사유·비고). PROTECTING 복귀 시 `end_reason`·`protection_ended_dttm`을 NULL로 되돌리고 `completed_application_seq`·반환 행·사유·비고는 유지. `animal_returns`는 UNIQUE 유지하되 **재지정 시 덮어쓰기**(`mod_usr_seq`·`mod_dttm` 추가, 반환 시각은 `mod_dttm` 우선). 11.6의 ADOPTED는 `completed_application_seq`를 건드리지 않음(채택은 API 12.11 전용). 등록 시 공고 시작·종료일과 `adoption_status`를 입력할 수 있고 생략 시 기본값. 기존 CHECK(`ck_shelter_status_pair`·`ck_shelter_protection_end`·`ck_shelter_notice_range`)는 그대로 | Q479 |
| 테이블 수 | 44개(`core` 33 + `simulation` 11). 번호는 유지하고 30번을 빈 번호로 둠 | - |

### v16 반영 변경사항 요약 (Q473~Q475)

| 영역 | 최신 v16 확정 정책 | 근거 |
|---|---|---|
| 문서 | v16 DB / v10 API 신규 파일 생성, v15/v9 보존. **컬럼·제약·인덱스 변경 없음**(조회 SELECT·응답 규칙만) | 2026-11-04 |
| 보호동물 목록(11.1) | 신고 목록(7.1·8.1)과 같은 필터·항목 구성. 필터는 **`protection_status`+`adoption_status` 합성 파생 상태 5값**(11.3과 동일 매핑, 생략 시 전체)·**`reg_dttm` 날짜 범위**·보호소 소재지 `district_code`·종·`gender`·`is_neutered`(포함 매칭)·`description`/`health_check` ILIKE·`reg_dttm` 정렬. **모든 필터 생략 시 전체**(기본값 없음). 보호소·품종·색상 필터, 단독 `protection_status`·`adoption_status` 필터, `notice_end_date` 정렬 삭제. 항목에 `age`·`is_neutered`·`weight`·공고기간·보호소(`shelters` JOIN: PK·이름·`district_code`) 포함 | Q473 |
| 보호동물 상세(11.2) | 목록 항목 + 상세 전용 컬럼. 종·품종·색상 이름 JOIN, 보호소 블록, `mod_dttm` 포함. 구조 정보 LEFT JOIN(Q404)·모의 입양 파생 블록 유지 | Q474 |
| 우리 보호소 목록(11.3) | 보호소 웹 탭형 목록. 탭은 **`protection_status`+`adoption_status` 합성 파생 상태 5값**(PROTECTING_AVAILABLE/PROTECTING_UNAVAILABLE/RETURNED/ADOPTED/ENDED, 조합 CHECK `ck_shelter_status_pair`로 전체를 겹침 없이 덮음). 필터는 최근 7/30/90일(`reg_dttm`)·종·`gender`·`is_neutered`, size 8. **상태별 건수는 탭 제외 필터로 집계해 목록과 한 응답**. 컬럼 추가 없음 | Q475 |
| 목록 기본값 | 7.1·8.1·11.1·11.3 목록 조회의 **모든 조건은 생략 시 전체**. 7.1·8.1 `status` 기본 ACTIVE(Q463)와 11.1 `protection_status` 기본 PROTECTING은 폐기(API v10 공통 행) | Q473 보완 |
| 테이블 수 | 45개 유지(스키마 변경 없음) | - |

> **문서 해석:** 이하 테이블 표와 개념 SQL은 **v18 현행 기준**입니다. 하단 Q263~Q405의 과거 추적표는 당시 결정 기록이고, 변경된 정책은 Q406~Q484·M1~M17·S1~S8이 우선합니다(M1·M13·M14·M16·M17은 Q481~Q483이 대체). 남은 [가안]은 §9에 모아 두었으며 [미정]은 없습니다.

## 1. DB 공통 설계 규칙

- DB: PostgreSQL 17 **1개**, 스키마 **2개**(Q420 D). PK는 `*_seq` 원칙, `IDENTITY` 사용. 로그·이미지·메시지 일부 BIGINT는 기존 v5 초안을 유지.
- 스키마 배치: `core` 스키마에 01~34번 테이블(**33개**, `districts`는 07번, 30번 `shelter_animal_comments`는 Q477로 삭제되어 빈 번호), `simulation` 스키마에 35~44번 `simulation_*` 10개(45번 `simulation_notifications`는 Q481로 삭제되어 빈 번호). **교차 스키마 FK는 두지 않고**(논리 참조, BE 검증), 같은 DB이므로 **교차 스키마 읽기 JOIN은 허용**(simulation이 `core.users`·`animal_breeds`·`animal_species`·`images`를 읽고, core-api가 `simulation.simulation_reports`·`simulation_sessions`를 읽음. Q482부터 simulation은 `shelter_animals`를 읽지 않음). 쓰기는 자기 스키마만 하며 서비스별 DB 계정 권한(자기 스키마 쓰기, 타 스키마 읽기)으로 강제할 수 있음.
- 쓰기 소유권: 같은 `core` 스키마를 세 서비스가 쓰지만 **테이블마다 쓰는 서비스는 하나**. `chat_rooms`·`chat_participants`·`chat_messages`는 chat, `notifications`·`device_tokens`는 notification, 나머지 28개는 core-api. 소유하지 않는 테이블은 읽기 전용 매핑(`@Immutable`) 또는 QueryDSL 프로젝션으로만 접근. **유일한 예외:** `AI_MATCH` 알림 행은 `missing_reports` 행 잠금과 한 트랜잭션이어야 하므로(Q389·390) core-api가 `notifications`에 직접 INSERT하고 notification은 FCM 발송만 맡음.
- DDL·시드: **Flyway**(Q412 A). `core` 마이그레이션은 core-api가, `simulation` 마이그레이션은 simulation이 보유. chat·notification은 `ddl-auto=validate`. 초기 데이터(보호소·SHELTER 계정·행정구역·동물 카탈로그·입양 문항·상점·시나리오)는 마이그레이션에 포함하며 API로 관리하지 않음.
- 백엔드 스택(S1~S8 확정): JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, JPA + QueryDSL(OpenFeign 포크, KSP), Redis 7.4, RabbitMQ(서비스 간 도메인 이벤트·채팅 STOMP relay), AWS S3(presigned PUT), OpenSearch 미도입(키워드 검색은 `ILIKE`, pg_trgm GIN 인덱스 [가안]), 위치 검색은 위경도 + 하버사인(PostGIS 미사용), 스케줄러는 simulation `@Scheduled` 10분 주기 1개(core·notification 스케줄 없음).
- 시간: `TIMESTAMPTZ`로 절대 시점을 저장(내부 UTC 기준), **DB 연결·세션 `TimeZone='Asia/Seoul'` 설정**으로 한국 시간으로 조회·표시. `reg_dttm`은 생성 시점, `mod_dttm`은 수정 시점. `DATE`는 날짜 의미 그대로 보관. API 날짜 직렬화도 동일 기준 적용(Q393).
- 감사 시각 매핑(Q430 확정): `mod_dttm`은 **NULL 허용, INSERT 시 NULL, UPDATE 시에만 채움**. JPA는 `@UpdateTimestamp`(INSERT에도 값을 넣음)를 쓰지 않고 `@PreUpdate` 콜백으로 갱신. 엔티티 베이스는 둘로 나눔: `BaseCreatedEntity`(`reg_dttm`만; `images`·`districts`·`pet_images`·`auth_providers`·이미지 연결 테이블 등 `mod_dttm`이 없는 테이블)와 이를 상속한 `BaseTimeEntity`(`mod_dttm NULL` 추가; `users`·`pets`·신고·보호동물 등). Flyway DDL과 `ddl-auto=validate`가 이 매핑 기준으로 통과해야 함.
- 삭제: 도메인 본문은 `is_deleted DEFAULT FALSE` 소프트 삭제 기본. 특별히 입양 DRAFT와 북마크 해제는 물리 삭제.
- 이미지: 공통 `images` + 다중 이미지 콘텐츠/반려동물 연결 행. 댓글/채팅은 **각 본문 테이블의 `image_seq NULL` 직접 FK**, 회원·보호소 프로필은 **`profile_image_seq NULL` 직접 FK**(Q408). 콘텐츠/반려동물 사진 1~3장, 댓글/채팅 사진 최대 1장, JPG/JPEG/PNG, 최대 10MB. `display_order=1` 대표. 원본을 공유하면 마지막 참조 해제 전 공통 이미지 메타데이터/S3 객체를 삭제하지 않음(Q383). `images.reg_usr_seq`로 업로더를 기록하고 이미지 연결은 업로더 본인만 허용(Q407).
- 관계: FK에 의한 물리 연쇄 삭제 대신 참조 정보 보존. 다형성 `target_type+target_seq`는 직접 FK 불가하므로 BE 존재·권한 검증. 공통 대상 코드와 테이블별 허용 코드를 구분(Q387).
- 일반회원만 `pets`·`adoption_applications`·`simulation_sessions` 생성. SHELTER는 소속 업무와 댓글·보호소 채팅 수행. ADMIN은 `users.role`의 예약값일 뿐 이번 구현 범위에서 계정 발급·권한·API가 없음(Q411). 식별자 규칙: **`user_seq`는 행위자**(작성자·수신자·감사 컬럼), **`shelter_seq`는 보호소 자원의 소유·배정 범위**. 요청에서 shelterSeq를 받지 않고 토큰 클레임에서만 꺼냄.
- 인증 토큰: RS256 JWT, core-api만 발급하고 나머지 서비스는 공개키로 검증. access 클레임 `userSeq`·`role`(USER/SHELTER)·SHELTER만 `shelterSeq`·`typ=access`. **refresh도 같은 RS256 키로 서명한 JWT**(클레임 `userSeq`·`jti`·`typ=refresh`, Q433 확정)이며 Redis `auth:refresh:{userSeq}:{jti}` 키 존재로 유효성을 판단, DB 테이블 없음(Q409 B). 검증기는 `typ != access`인 토큰을 API 인증에서 거부(refresh를 access로 쓰는 것 차단). 카카오 가입 컨텍스트(registrationToken)는 Redis `auth:register:{token}` TTL 10분, 가입 성공 시 삭제(Q414 A). access 블랙리스트 없음(탈퇴·정지 후 최대 1시간 유효). 대신 **모든 쓰기 요청(POST·PATCH·DELETE·STOMP SEND)에서 `users.is_deleted`·`account_status`를 재확인**하고 조회는 자연 만료까지 허용(Q433).
- 행정구역: 법정동 코드 앞 5자리(시군구)를 `district_code VARCHAR(5)`로 통일(Q406 A). 마스터는 `districts`(시드). 신고 등록·위치 수정 시 서버가 네이버 역지오코딩(`legalcode`)으로 도출하고 실패하면 NULL 저장·등록은 진행(Q406-2 A). 보호소는 **오프라인에서 미리 계산한 코드를 시드 SQL에 직접 삽입**(Q431 확정; 마이그레이션이 외부 API를 호출하지 않음).
- 모의 입양: 접두어 `simulation_` 9개 테이블. 세션·로그·리포트는 소프트 삭제 없이 영구 보존. 스탯 수치·배율 같은 튜닝값은 DB가 아닌 코드 설정값(§3 "코드 설정 가안"). 단 CONDITION 이벤트의 발생 확률(`trigger_probability`, Q481)과 정답 행동·해결/방치 효과·비용(Q486)은 시나리오 컬럼(시드). 알림은 저장하지 않음(Q481).
- 일반 API는 로그인 필수. 탈퇴 작성자의 게시물·상세는 숨기되, 채팅·댓글·알림과 보호소 업무용 **과거 입양 신청서·현재 상태 기록**은 예외적으로 보존(별도 신청 상태 전이 이력 테이블 없음).

## 2. 관계 개요

```text
users 1:1 user_accounts N:1 auth_providers
  ├─profile_image_seq N:0..1 images (회원 프로필 사진 0~1장, Q408)
  ├─1:N images.reg_usr_seq (업로더; 시드·공공데이터 적재분은 NULL, Q407)
  ├─1:N pets 1:N pet_images N:1 images
  ├─1:N missing_reports 1:N missing_report_images N:1 images
  ├─1:N found_reports 1:N found_report_images N:1 images
  ├─1:N adoption_applications N:1 shelter_animals
  ├─1:N simulation_sessions ··· animal_breeds (USER당 IN_PROGRESS 1건; simulation 스키마, user_seq·breed_seq 논리 참조, 보호동물 연결 없음 Q482)
  └─1:N chat_messages / comments / notifications / device_tokens (core)
shelters 1:0..1 shelter_members N:1 users(role=SHELTER)
  └─profile_image_seq/background_image_seq N:0..1 images (각각 선택 이미지 1장)
  ├─N:0..1 districts (district_code NULL 허용; 시드 시 역지오코딩으로 도출, Q406)
  ├─1:N shelter_district_assignments (각 district_code는 단일 PK로 보호소 하나)
  ├─1:N rescue_records N:0..1 found_reports
  └─1:N shelter_animals 1:N shelter_animal_images N:1 images
districts(시군구 마스터, 시드) 1:N shelter_district_assignments ; 1:N shelters / missing_reports / found_reports (district_code NULL 허용, Q406-1)
rescue_records 1:N shelter_animals (rescue_seq NULL 허용; DONE만 참조; 구조 4개 속성은 JOIN 조회; 보호소 달라도 가능)
shelter_animals 1:0..1 animal_returns (반환 완료 1건; 재지정 시 같은 행 덮어쓰기, Q479)
shelter_animals 1:N adoption_applications (answers_jsonb 질문 키=question_seq)
adoption_form_questions (전역 공통 현재 20문항만; 템플릿/버전/문항 이력 없음, Q458)
adoption_applications에 입양 신청·심사·최종 입양 상태 통합(별도 후속절차/이력 테이블 없음)
animal_species(species_code DOG/CAT/ETC) 1:N animal_breeds(personality 4종 NOT NULL, representative_image_seq N:0..1 images, Q482) 1:N animal_combinations N:1 animal_colors
pets/missing_reports/found_reports/shelter_animals N:1 animal_combinations
[simulation 스키마] ── core 스키마(users, animal_breeds)를 가리키는 값은 모두 논리 참조, FK 없음(Q420 D)
simulation_sessions 1:N simulation_inventory_items / simulation_transactions / simulation_action_logs / simulation_walks
                    / simulation_event_occurrences; 1:0..1 simulation_reports  (simulation_notifications는 Q481로 삭제)
simulation_shop_items(마스터, image_key 문자열; images 참조 아님) 1:N simulation_inventory_items / simulation_transactions
simulation_event_scenarios(마스터, trigger_probability·정답 행동 resolve_action_code·해결/방치 효과; 선택지 테이블은 Q486으로 삭제) 1:N simulation_event_occurrences (walk_seq NULL 허용 → simulation_walks; resolved_action_log_seq N:0..1 simulation_action_logs)
모의 입양 세션은 animal_breeds의 종·성격을 시작 시 스냅샷하고(breed_seq 논리 참조, 가상 이름 animal_name) 실제 보호동물·보호소와 연결하지 않음(Q482). 보호소 리포트 열람은 소속 제한 없이 SHELTER 전체(Q483), 12.15는 adoption_applications.user_seq로 파생 조회.
core-api는 simulation.simulation_reports/simulation_sessions를 읽기 전용 JOIN으로 조회(Q483), simulation은 core.users/animal_breeds/animal_species/images를 읽기 전용 JOIN
chat_rooms 1:N chat_participants; 1:N chat_messages (image_seq NULL FK → images, 메시지당 최대 1장)
missing/found_report_comments 각각 자체 부모 복합 FK + image_seq NULL FK → images (댓글당 최대 1장; 보호동물 댓글은 Q477로 삭제)
reports / bookmarks / notifications / chat_messages(SHARED_POST): 공통 코드 기반 다형성 대상 참조 (지원 대상은 테이블별로 제한)
notifications.comment_seq / parent_comment_seq ··· target_type이 가리키는 게시물의 댓글 테이블(논리 참조, FK 없음, Q446)
실종 신고 ↛ 발견 신고·보호동물 (AI 유사도 알림만, 확정 링크 없음)
pets ↛ missing_reports (v13: `pet_seq` 삭제, 반려동물과 실종 신고는 FK 없음. 앱이 표시만 결합, Q466)
users 1:0..1 비삭제 ACTIVE missing_reports; CLOSED는 과거 행 보존·새 신고 가능
```


### v8 단순화 구조의 조회·정합성 참고

- **반환 완료 시각:** `animal_returns.reg_dttm`이 반환 처리 시각이며, 같은 동물을 다시 RETURNED로 지정해 행을 덮어쓴 경우 `mod_dttm`이 최신 반환 시각입니다(Q479). 실제 반환 시각을 별도 소급 입력하지 않는 현재 업무 정책을 전제로 합니다.
- **구조 기록 JOIN:** 구조 기반 보호동물은 `LEFT JOIN rescue_records r ON r.rescue_seq = shelter_animals.rescue_seq`로 구조일/사유/주소/메모를 조회합니다. `rescue_seq=NULL`인 동물은 네 정보가 모두 NULL입니다.
- **AI 알림 식별:** `notification_type='AI_MATCH'`인 알림의 `target_type`은 `FOUND` 또는 `SHELTER_ANIMAL`이고 `target_seq`는 해당 상대의 PK입니다. 같은 `missing_report_seq`와 상대 조합이 존재하면 재생성하지 않습니다(`is_deleted=TRUE` 포함).
- **입양 질문:** DB에는 활성 버전/질문 보관 이력이 없습니다. 현행 질문을 바꾸더라도 과거 `answers_jsonb` 자체는 남으며, `question_seq`가 같으면 현재 질문 문구에 붙여 표시합니다. 기존 응답과 질문 의미가 달라질 위험을 허용한 결정입니다.
- **시각 변환:** JDBC/DB pool 등 애플리케이션 연결별 PostgreSQL 세션 `TimeZone`을 `Asia/Seoul`로 맞춥니다. `TIMESTAMPTZ`를 `TIMESTAMP`로 바꾸거나 타임존이 없는 한국 시간 문자열을 저장하는 정책이 아닙니다.
- **모의 입양 스탯:** `simulation_sessions`의 스탯 7개 컬럼은 `stats_calculated_dttm` 시점의 값입니다. 조회·행동·스케줄러가 그 시각부터 경과한 tick만큼 코드 설정 규칙을 적용해 갱신한 뒤 사용하며, 클라이언트가 스탯을 직접 올리지 않습니다(M2).
- **모의 입양 성격·종(Q482):** 세션 시작 시 요청의 `breed_seq`로 `animal_breeds.personality`, `→ animal_species.species_code`를 읽어 `simulation_sessions.personality/species_type`에 스냅샷하고 `breed_seq`를 논리 참조로 저장합니다. 이후 품종 값이 바뀌어도 진행 중 세션은 영향을 받지 않습니다(M7·M8). 품종명·대표 이미지는 조회 시 `animal_breeds`를 JOIN해 파생합니다.
- **교차 스키마 참조(Q420 D·Q482):** `simulation_*`에서 `user_seq`·`breed_seq`는 FK가 아니라 값만 저장하는 논리 참조입니다. 존재·상태 검증은 simulation 서비스가 `core` 스키마를 읽기 전용 JOIN으로 수행하며 **잠그지 않습니다**(소유하지 않는 테이블의 행 잠금 금지). core-api는 보호소의 리포트 열람(Q483)을 위해 `simulation.simulation_reports`·`simulation_sessions`를 읽기 전용으로 JOIN합니다. 회원 탈퇴는 core-api가 `UserDeleted` 이벤트(RabbitMQ)를 내고 simulation이 세션 ABANDONED·리포트 생성을 처리합니다(M6).
- **행정구역 코드(Q406 A):** `district_code`는 법정동 코드 앞 5자리입니다. 신고 등록·위치 수정 시 서버가 네이버 역지오코딩 `legalcode` 응답에서 잘라 저장하고, 실패하면 NULL로 두되 등록은 진행합니다. 발견 신고 담당 보호소는 `found_reports.district_code → shelter_district_assignments` 매핑을 우선하고, 코드가 NULL이거나 배정표에 없으면 하버사인 최근접 보호소를 선택합니다(Q406-2 A). 목록 필터 `districtCode`는 코드가 NULL인 행을 제외합니다.

## 3. 테이블별 통합 상세 명세

### 사용자·인증·보호소

#### 01. `users` — 공통 계정·역할 및 회원 개인정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `user_seq` | `INTEGER` | Y | PK, IDENTITY | 공통 사용자 키(확정) |
| `role` | `VARCHAR(15)` | Y | CHECK `USER/SHELTER/ADMIN` | 서비스 권한(확정). `ADMIN`은 예약값이며 이번 구현 범위 외(Q411) |
| `nickname` | VARCHAR(50) | N | USER 필수 CHECK; 활성 USER case-insensitive 부분 UNIQUE | 닉네임 |
| `name` | VARCHAR(100) | N | USER 필수 CHECK | 회원 실명; 보호소·관리자 NULL 허용 |
| `birth_date` | DATE | N | USER 필수 CHECK | 생년월일 |
| `phone` | VARCHAR(30) | N | USER 필수 CHECK; 활성 USER 부분 UNIQUE | 회원 전화번호; 회원가입 시 형식 검증 |
| `profile_image_seq` | `BIGINT` | N | FK → images | 회원 프로필 이미지 0~1장(Q408). 종전 `profile_image_key TEXT`는 삭제 |
| `account_status` | `VARCHAR(20)` | Y | 기본값 ACTIVE; 상태 세부 운영 보류 | ACTIVE/SUSPENDED 후보, 탈퇴와 별도 |
| `deleted_dttm` | `TIMESTAMPTZ` | N | ※초안 | 탈퇴 시각(잠정) |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 탈퇴 소프트 삭제; 관련 작성자 콘텐츠 가상 비노출 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각; 최초에는 NULL |

**확정 규칙 / 참고사항**
- `role=USER`인 경우 `nickname/name/birth_date/phone` 전부 입력 필수(DB CHECK). SHELTER/ADMIN은 NULL 허용. 공백·형식은 BE 검증(Q370). **ADMIN은 CHECK 값으로만 예약**하며 이번 프로젝트에서 계정 발급·권한·API를 구현하지 않음(Q411). 이하 문서의 ADMIN 언급은 모두 "범위 외"로 읽음.
- `profile_image_seq`는 `images.image_seq` 직접 FK(Q408). 업로드는 공통 이미지 업로드 경로를 사용하고 교체·해제 시 마지막 참조 해제 정책(Q383) 적용. 연결 가능한 이미지는 `images.reg_usr_seq`가 본인인 것만(Q407). 카카오 프로필 사진·이메일은 가져오지 않으며 가입 시점에는 NULL, 가입 후 프로필 수정으로 설정(Q408-1).
- 활성 USER 기준 `lower(nickname)` 및 `phone` 각각 부분 UNIQUE(`role=USER AND is_deleted=FALSE`). 탈퇴 뒤 닉네임·번호 재사용 가능(Q300·328). SMS 인증은 구현하지 않음(Q329).
- 탈퇴 시 `users`·`user_accounts` 모두 소프트 삭제(core-api 트랜잭션). 활성 FCM 토큰은 core-api가 직접 바꾸지 않고 커밋 후 `UserDeleted` 이벤트를 받은 notification이 `is_active=FALSE`로 비활성화(행 삭제 아님, Q420). USER 소유 게시물은 행을 직접 변경하지 않고 서비스에서 비노출(가상 삭제)(Q301·312·327).
- 탈퇴 회원의 댓글/답글, 기존 채팅·알림은 작성자 표시를 `(탈퇴한 사용자)`로 대체해 유지. 탈퇴 회원 루트 댓글에는 신규 답글 금지(Q314~317).
- 탈퇴 회원 관련 일반 조회는 별도 오류로 구별. 보호소는 업무상 과거 입양 신청서/증명서를 계속 열람할 수 있음(Q313·319).
- USER는 KAKAO, SHELTER는 LOCAL 인증만 이용(서비스 검증; ADMIN은 범위 외). SUSPENDED 등 계정 상태 상세는 보류.
- 카카오 로그인은 Kakao Developers 앱(테스트 앱) 기준이며 팀원으로 등록된 계정만 로그인 가능(Q408-1). refresh token은 Redis TTL 저장(Q409 B), DB 테이블 없음.
- 탈퇴 시 진행 중인 모의 입양 세션은 ABANDONED로 종료하고 리포트를 생성·보존(M6).

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
- LOCAL 로그인 실패 제한(Q445): 연속 실패 횟수는 Redis `auth:login-fail:{loginId}`(TTL)로만 관리하고 DB 컬럼·잠금 상태는 두지 않음. [가안] 5회 실패 시 15분 차단, TTL 만료로 자동 해제, 성공 시 카운터 삭제. 영구 잠금 없음(재설정 경로가 없으므로).

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
| `district_code` | `VARCHAR(5)` | N | FK → districts | 보호소 소재 시·군·구 코드(Q406 A). 오프라인에서 미리 계산해 시드 SQL에 직접 삽입(Q431), 미확인 시 NULL |
| `profile_image_seq` | `BIGINT` | N | FK → images | 보호소 프로필 이미지 0~1장 |
| `background_image_seq` | `BIGINT` | N | FK → images | 보호소 배경 이미지 0~1장(Q394) |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `profile_image_seq`와 `background_image_seq`는 각각 `images.image_seq`를 직접 참조하고 공통 마지막 참조 해제 후 삭제 정책(Q383)을 적용.
- `district_code`는 목록 필터(API 9.1 `districtCode`)용이며 발견 신고 배정에는 쓰지 않음(배정은 `shelter_district_assignments`). 보호소 좌표·주소는 시드이므로 코드도 오프라인에서 한 번 계산해 시드 SQL에 넣음(Q406 A·Q431).
- 보호소 이름·주소·좌표·전화번호 필수. 운영 정보는 소속 담당자가 수정. 식별 정보(이름·외부 코드) 수정과 보호소 신규 등록은 ADMIN 범위 외(Q411)이므로 Flyway 시드로만 처리(Q412 A).
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
| `district_code` | VARCHAR(5) | Y | PK, FK → districts | 시·군·구 행정구역 코드(법정동 코드 앞 5자리, Q406 A) |
| `shelter_seq` | INTEGER | Y | FK → shelters | 해당 행정구역의 지정 담당 보호소 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 매핑 등록일 |

**확정 규칙 / 참고사항**
- 시·군·구 단위 **`district_code` 단일 PK 유지**로 지정 보호소 1개(Q395). **복합 PK `(district_code,shelter_seq)`는 도입하지 않음.** 동일 보호소가 여러 구역을 담당할 수 있음(Q287·338). v9의 `VARCHAR(20)`은 `districts`와 맞춰 `VARCHAR(5)`로 통일.
- 발견 신고 등록 시 `found_reports.district_code`로 이 표를 조회해 매핑 우선, 코드가 NULL이거나 미매핑이면 좌표상 가까운 보호소를 자동 선택(Q284~286·Q406-2 A). 삭제된 보호소 처리 엣지케이스는 Q356에서 스킵.
- 발견 신고 좌표에서 `district_code`를 도출하는 방식은 서버 역지오코딩(네이버 `legalcode`)으로 확정(Q406 A). 배정표 행과 `districts` 행은 Flyway 시드로 적재(Q412 A).

#### 07. `districts` — 시·군·구 행정구역 마스터 (v10 신설, Q406-1 A)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `district_code` | VARCHAR(5) | Y | PK | 법정동 코드 앞 5자리(시·군·구) |
| `sido_name` | VARCHAR(50) | Y | NOT NULL | 시·도 이름(예: 대구광역시) |
| `sigungu_name` | VARCHAR(50) | Y | NOT NULL | 시·군·구 이름(예: 중구) |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 적재 시각 |

**확정 규칙 / 참고사항**
- 전국 시·군·구 목록을 Flyway 시드로 적재하며 API로 관리하지 않음(Q412 A). 앱의 지역 필터 선택 목록은 `GET /districts`(API v4 5장 기준정보)로 내려줌.
- `shelters`·`missing_reports`·`found_reports`·`shelter_district_assignments`의 `district_code`가 이 표를 FK로 참조하므로 역지오코딩 결과 코드가 마스터에 없으면 NULL로 저장(존재 검증 근거).
- `core` 스키마 소속, 쓰기는 core-api(시드)만. 테이블 수 44 → 45.

### 동물 기준정보·반려동물

#### 08. `animal_species` — 동물 종 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `species_seq` | `INTEGER` | Y | PK, IDENTITY | 종류 식별자 |
| `species_name` | `VARCHAR(50)` | Y | UNIQUE 후보 | 예: 개, 고양이, 기타 |
| `species_code` | `VARCHAR(10)` | Y | **NOT NULL UNIQUE; CHECK `DOG/CAT/ETC`(M8)** | 종 코드. 모의 입양의 종 판별에 사용 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 종별 마스터. 실무 동물 데이터에서는 직접 종 FK 대신 유효 조합 ID를 사용.
- `species_code`는 v8의 "※초안 NULL 허용"에서 **NOT NULL UNIQUE + CHECK 3종**으로 확정(M8). `DOG`/`CAT`만 모의 입양 시작 가능, `ETC`는 시작 불가. 세션 시작 시 `simulation_sessions.species_type`에 스냅샷.

#### 09. `animal_breeds` — 종별 품종 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `breed_seq` | `INTEGER` | Y | PK, IDENTITY | 품종 식별자 |
| `species_seq` | `INTEGER` | Y | FK → animal_species | 해당 품종이 속하는 종; 종·품종 조회를 위한 계층 |
| `breed_name` | `VARCHAR(100)` | Y | UNIQUE `(species_seq,breed_name)` 후보 | 말티즈, 코리안 숏헤어 등 |
| `breed_code` | `VARCHAR(60)` | N | ※초안 | 공공데이터·외부 사전 코드 매핑 |
| `personality` | `VARCHAR(20)` | Y | **NOT NULL(Q482)**; CHECK `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY`(M7) | 모의 입양용 성격 4종. 모든 품종(ETC 포함)에 시드로 배정 |
| `representative_image_seq` | `BIGINT` | N | FK → images(Q482) | 품종 대표 이미지 1장(시드, `images.reg_usr_seq` NULL). 모의 입양 동물 입력 화면·세션·리포트 표시용 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 각 품종은 정확히 하나의 종에 속함. 실제 종/품종/색상 매핑 목록은 팀에서 별도로 정의.
- `personality`는 모의 입양 성격 4종(활동적 `ACTIVE`, 소심함 `TIMID`, 공격적 `AGGRESSIVE`, 사람친화적 `FRIENDLY`)을 품종 단위로 매칭(M7). `shelter_animals`에는 성격 컬럼을 두지 않음. **v18부터 NOT NULL**(Q482): 모의 입양이 품종에서 바로 시작하므로 DOG/CAT 품종은 물론 ETC 품종에도 시드에서 값을 배정하고, v17까지의 "NULL이면 랜덤 배정" 가안은 폐기. 세션 시작 시 품종 값을 `simulation_sessions.personality`에 스냅샷. 값은 초기 데이터로 적재(Q412).
- **대표 이미지(Q482):** 품종마다 사진 1장을 `representative_image_seq`로 `images`에 연결(색상 구분 없음, `animal_combinations`가 아닌 품종 단위). 시드가 S3에 먼저 올린 키로 `images` 행을 만들고(`reg_usr_seq` NULL, Q431) 그 PK를 기록. 요청으로 받지 않고 API 5.2 품종 목록·모의 입양 세션/리포트 응답에서 URL로 변환해 노출. 마스터 이미지라 해제되는 일이 없으므로 이미지 참조 정리(Q383·Q427)의 참조 확인 대상에는 넣지 않음. simulation 서비스가 읽기 전용 JOIN.

#### 10. `animal_colors` — 털색 기준정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `color_seq` | INTEGER | Y | PK, IDENTITY | 색상 기준 ID |
| `color_name` | VARCHAR(100) | Y | UNIQUE 후보 | 색상 표기; 기타 포함 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 등록시각 |

**확정 규칙 / 참고사항**
- 색상 마스터; 기타 값을 실제 기준 데이터로 관리(Q335).

#### 11. `animal_combinations` — 허용 품종·색상 조합

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `combination_seq` | INTEGER | Y | PK, IDENTITY | 품종·색상 유효 조합 키 |
| `breed_seq` | INTEGER | Y | FK → animal_breeds | 종은 품종에서 간접 확인 |
| `color_seq` | INTEGER | Y | FK → animal_colors | 색상 선택값 |
| `reg_dttm` | TIMESTAMPTZ | Y | DEFAULT CURRENT_TIMESTAMP | 등록시각 |

**확정 규칙 / 참고사항**
- `UNIQUE(breed_seq,color_seq)`로 유효 조합 중복 금지. 종은 animal_breeds를 통해 결정, combination에 별도의 species_seq 없음(Q334).
- 조합의 실제 행과 기타 처리 범위는 별도 마스터 시드에서 관리. Q336 관련 상세 질문은 스킵.

#### 12. `pets` — 일반회원 등록 반려동물

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `pet_seq` | `INTEGER` | Y | PK, IDENTITY | 회원 반려동물 ID |
| `user_seq` | `INTEGER` | Y | FK → users | 소유자 |
| `name` | `VARCHAR(100)` | Y | NOT NULL; BE 공백 금지 | 반려동물 이름 |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `weight` | VARCHAR(30) | N | NULL 허용 | 반려동물 몸무게 선택 자유 텍스트(예: "4.5kg"). 실종·발견·보호동물의 `weight`도 같은 형식(Q469) |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 자유형 특징: 색상·목줄·귀 모양 등 |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 등록 반려동물 소프트 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `mod_usr_seq` 제거(Q396). 소유자인 `user_seq`가 수정 권한의 기준이며 `mod_dttm`으로 마지막 수정시각만 남김.
- USER만 생성/수정/삭제. 이름·종/품종/색상 조합·성별 필수. `weight`는 선택 자유 텍스트(Q362). Q469로 실종·발견·보호동물의 `weight`도 이 형식(`VARCHAR(30)`)으로 통일됨.
- 사진 1~3장(`pet_images`), 첫 사진 대표. 종전 `pets.image_seq`는 제거(Q293).
- **실종 신고와 FK 연결 없음(Q466, v13):** `missing_reports.pet_seq`가 삭제되어 반려동물 삭제는 신고·AI 매칭에 영향을 주지 않음(Q348의 "연결 유지" 규칙은 연결 자체가 없어져 의미 없음). 앱이 신고 작성 폼에 반려동물 값을 미리 채우는 것은 앱 동작이며 서버는 저장하지 않음.
- 마이크로칩 등록 여부는 별도 컬럼으로 두지 않음(Q305·364). 중성화 미상은 NULL로 표현.

#### 13. `pet_images` — 등록 반려동물 이미지

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

#### 14. `images` — 공통 이미지 저장소 객체 메타데이터

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `image_seq` | `BIGINT` | Y | PK, IDENTITY | 이미지 공통 키 |
| `image_key` | `VARCHAR(512)` | Y | **UNIQUE(Q256)** | 저장소 객체 키(S3 등); 길이 ※초안 |
| `mime_type` | `VARCHAR(100)` | Y | - | MIME 종류; GIF·영상 업로드 불허 |
| `original_file_name` | `VARCHAR(255)` | N | ※초안 | 원본명(선택) |
| `byte_size` | `BIGINT` | N | CHECK `byte_size>=0` 후보 | 업로드 파일 크기(선택) |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 업로더(Q407). 공공데이터·시드 적재분은 NULL |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- `image_key UNIQUE`. 원본 이미지 객체는 S3 등 외부 저장소에 보관하고 DB에는 메타데이터 저장.
- **업로더 기록(Q407):** 회원이 업로드한 이미지는 `reg_usr_seq`에 업로더를 저장. 게시물 사진·반려동물 사진·프로필·댓글·채팅 첨부 등 **모든 이미지 연결은 `reg_usr_seq`가 요청자 본인인 이미지만 허용**(BE 검증, 아니면 403). 반려동물 사진을 실종 신고에 재사용하는 경우는 같은 사용자이므로 그대로 가능. ADMIN 전역 예외는 없음(ADMIN 범위 외). 품종 대표 이미지(`animal_breeds.representative_image_seq`, Q482)처럼 시드로 적재한 이미지는 NULL.
- 여러 도메인 첨부 관계가 하나의 `image_seq`를 참조할 수 있음. 이미지 연결을 해제할 때 **모든 연결 테이블과 직접 FK(`users.profile_image_seq`, `shelters.profile_image_seq/background_image_seq`, 댓글·채팅 `image_seq`)의 참조가 사라진 경우에만** 공통 `images` 행과 S3 원본 객체를 삭제(Q383·Q408). 참조 확인 대상은 모두 `core` 스키마 안에 있음(`simulation_shop_items`는 v10부터 `image_key` 문자열이라 대상 아님. `animal_breeds.representative_image_seq`는 시드 마스터라 해제 경로가 없어 대상에서 제외, Q482). 별도 참조 카운트 컬럼 없음. 저장소 삭제는 DB 커밋 후 best-effort로 시도하고 실패하면 로그만 남기며 고아 객체를 허용(Q427 A). 참조 확인은 트랜잭션·동시성 검증 대상.
- JPG/JPEG/PNG, 파일당 10MB. 이미지 임베딩 상세 구조는 별도 보류(저장 위치는 pgvector 후보, OpenSearch 미도입). 객체 저장소는 **AWS S3, 업로드는 presigned PUT**(S4 확정): 4.1에서 서버가 `image_key`와 업로드 URL을 발급하고 발급 컨텍스트(키·소유자)를 Redis에 임시 저장, 4.2에서 S3 HEAD로 실제 크기·MIME을 검증한 뒤 이 행을 생성. 4.2가 호출되지 않은 고아 객체는 버킷 수명주기 규칙으로 정리. **읽기(Q443):** `images/` 접두어는 버킷 정책으로 익명 GET을 허용하고 API의 `imageUrl`은 `{public-base-url}/{image_key}` 고정 URL(서명·만료 없음). 익명 쓰기는 열지 않음. 수료증용 `certificates/` 비공개 업로드·presigned GET은 **Q460에서 기능 제외, Q462에서 컬럼까지 삭제**. **업로드 순서 원칙(Q456):** 앱은 사진을 고르면 4.1 발급 → S3 PUT까지 바로 하고, **등록·전송 직전에 4.2를 호출**해 이 행을 만든 뒤 그 `image_seq`로 연결 API를 호출. 사용자가 사진을 지우거나 작성을 취소하면 4.2를 호출하지 않아 수명주기 규칙으로 정리됨.

### 실종·발견 신고

#### 15. `missing_reports` — 실종 신고 정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `missing_report_seq` | `INTEGER` | Y | PK, IDENTITY | 실종 신고 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `weight` | `VARCHAR(30)` | N | NULL 허용(Q469, v14 신설) | 몸무게 자유 텍스트(예: "4.5kg"). 반려동물과 같은 형식, 서버가 숫자로 해석하지 않음 |
| `is_neutered` | `BOOLEAN` | N | NULL 허용 | TRUE=완료, FALSE=미완료, NULL=모름(선택 단계 검증은 BE) |
| `microchip_number` | `VARCHAR(30)` | N | NULL 허용; 전역 UNIQUE 없음 | 마이크로칩 번호만 저장(등록 여부 컬럼 없음) |
| `description` | `TEXT` | N | - | 자유형 특징·목줄 등 |
| `address` | `VARCHAR(255)` | Y | - | 마지막 목격/실종 지점 확정 주소 |
| `latitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 실종 위치 위도 |
| `longitude` | `DOUBLE PRECISION` | Y | CHECK 범위 후보 | 실종 위치 경도 |
| `occurred_dttm` | `TIMESTAMPTZ` | Y | - | 실종 시각 |
| `district_code` | `VARCHAR(5)` | N | FK → districts | 실종 지점 시·군·구 코드(Q406 A). 등록·위치 수정 시 서버 역지오코딩으로 도출, 실패 시 NULL |
| `status` | `VARCHAR(15)` | Y | NOT NULL DEFAULT ACTIVE; CHECK ACTIVE/CLOSED | CLOSED→ACTIVE 재개 가능. 종료 사유·일시 컬럼 없음. CLOSED는 읽기 전용(Q425) |
| `contact_phone` | `VARCHAR(30)` | Y | NOT NULL | 신고 시점 연락처 스냅샷; 회원 번호 기본 입력·수정 가능 |
| `is_phone_public` | BOOLEAN | Y | NOT NULL DEFAULT FALSE | 로그인 회원 대상 연락처 공개 여부 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- `status=ACTIVE/CLOSED`; CLOSED→ACTIVE 재개 가능. 종료 사유/종료 시각은 별도 저장하지 않음(Q289).
- **비삭제 ACTIVE 신고는 회원당 최대 1건**(user_seq 기준 부분 UNIQUE). CLOSED 신고는 보존하며 CLOSED→ACTIVE 재개나 신규 등록 시 회원의 다른 ACTIVE 신고가 없어야 함(Q399 선행 확인).
- **`pet_seq`·`name` 없음(Q465·Q466, v13):** 반려동물 연결·속성 스냅샷·소유자 검증·동물 이름 컬럼을 모두 삭제. 신고의 동물 속성(조합·성별·나이·몸무게·중성화·칩 번호·특징)은 사용자가 직접 입력한 값만 저장. 이전 Q366("missing_reports.name NOT NULL")·Q348("pet_seq 유지")은 폐기.
- **몸무게(Q469, v14):** `weight VARCHAR(30) NULL` 자유 텍스트. 등록·수정 시 선택 입력이며 길이 외 검증 없음. **목록 응답(Q468, API 7.1·7.3):** `age`·`is_neutered`·`weight`를 상세뿐 아니라 목록 SELECT에도 포함(인덱스·컬럼 변경 없음).
- 위치 주소·좌표·실종 시각·연락처 필수. 별도 `circumstances` 컬럼 없음(Q397; Q369 대체). 연락처는 등록 회원 기본번호 복사 후 별도 수정 가능.
- **주소·좌표 입력(Q467):** `address`·`latitude`·`longitude`는 앱이 API 5.6 주소 검색 프록시(네이버 Geocoding) 후보 또는 지도 핀에서 얻어 보낸 값을 그대로 저장. 서버는 등록 시 주소→좌표 변환을 하지 않고, 상세 주소(동·호수 등) 컬럼은 두지 않음(대략적 위치만).
- 연락처 공개는 기본 비공개. 공개 시 로그인 회원에게, 비공개 시 작성자에게만 표시(ADMIN 열람은 범위 외, Q411).
- `district_code`는 목록 필터(API 7.1 `districtCode`) 전용(Q406 A). 좌표 변경 시 재도출하고, 역지오코딩 실패나 마스터에 없는 코드면 NULL로 두되 등록·수정은 진행(Q406-2 A). 필터는 NULL 행을 제외.
- **목록 검색 조건(Q463, API 7.1. 8.1도 동일):** 모든 조건은 생략 시 적용하지 않음(v16: 종전 `status` 기본 ACTIVE 폐기, 생략 시 CLOSED 포함 전체). `status`, `occurred_dttm`의 KST 날짜 범위(양끝 포함), `district_code`, 종(`combination_seq`→`animal_breeds`→`animal_species`), `gender`, `is_neutered`, `description ILIKE`, 정렬 `reg_dttm`만. **포함 매칭:** `gender=MALE` 필터는 `MALE OR UNKNOWN`, `FEMALE`은 `FEMALE OR UNKNOWN`; `is_neutered=true` 필터는 `TRUE OR IS NULL`, `false`는 `FALSE OR IS NULL`(확실히 아닌 것만 제외). 품종·색상 필터는 없음. 내 신고 목록(7.3)은 `user_seq` 기준 비삭제 전량을 `reg_dttm DESC`로 반환(페이지 없음).
- **CLOSED 상태에서는 본문·사진 수정 불가**(Q425 B). 수정하려면 먼저 ACTIVE로 재개해야 하며, 재개는 회원의 다른 ACTIVE 신고가 없을 때만 가능. 사진 변경은 `imageSeqs` 전체 최종 배열 교체 방식이며 서버가 diff와 `display_order`를 계산(Q424 A).
- 신고 작성은 USER만 가능(Q421 A). 수정·삭제·상태 변경은 작성자 본인만(Q423 A).
- ACTIVE·비삭제·작성자 비탈퇴 신고만 AI 비교 및 알림 대상. 반환/발견 신고 종료로 자동 종료하지 않음.

#### 16. `missing_report_images` — 실종 신고 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `missing_report_image_seq` | `BIGINT` | Y | PK, IDENTITY | 첨부 행 PK(※초안) |
| `missing_report_seq` | `INTEGER` | Y | FK → missing_reports | 실종 신고 |
| `image_seq` | `BIGINT` | Y | FK → images | 공통 이미지 |
| `display_order` | `SMALLINT` | Y | **UNIQUE `(missing_report_seq,display_order)` (Q232)** | 사진 표시 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 1~3장 필수, 대표는 `display_order=1`. 이미지 중복 및 개수는 BE 검증. `UNIQUE(missing_report_seq,display_order)` 유지.

#### 17. `found_reports` — 발견 신고 정보

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `found_report_seq` | `INTEGER` | Y | PK, IDENTITY | 발견 신고 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성자 |
| `shelter_seq` | `INTEGER` | Y | FK → shelters; NOT NULL | 구역 매핑 우선/미매핑 시 최근접 자동 배정. 수동 재배정은 ADMIN 기능이라 이번 범위 외(Q411) |
| `combination_seq` | INTEGER | Y | FK → animal_combinations, NOT NULL | 선택된 종→품종→색상 유효 조합의 ID |
| `gender` | `VARCHAR(15)` | Y | NOT NULL; CHECK `MALE/FEMALE/UNKNOWN` | 성별; 미확인 값은 UNKNOWN |
| `age` | `VARCHAR(30)` | N | - | 추정 나이 자유 표기(예: 7살, 6개월); 자동 갱신 없음 |
| `weight` | `VARCHAR(30)` | N | NULL 허용(Q469, v14 신설) | 몸무게 자유 텍스트(예: "약 3kg"). 추정값 표기 허용, 서버가 숫자로 해석하지 않음 |
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
| `district_code` | `VARCHAR(5)` | N | FK → districts | 발견 지점 시·군·구 코드(Q406 A). 등록 시 서버 역지오코딩으로 도출해 담당 보호소 매핑 입력값으로 사용, 실패 시 NULL |
| `status` | `VARCHAR(15)` | Y | NOT NULL DEFAULT ACTIVE; CHECK ACTIVE/CLOSED | 종료/재개 가능; 구조 완료·반환과 자동 연동하지 않음. CLOSED는 읽기 전용(Q425) |
| `closed_reason` | `TEXT` | N | NULL 허용; 자유 서술, 별도 코드 CHECK 없음 | 종료 사유 선택 입력, ACTIVE에서 NULL |
| `closed_dttm` | `TIMESTAMPTZ` | N | CLOSED일 때 NOT NULL CHECK | 종료 일시, ACTIVE에서 NULL; 재개 시 NULL로 초기화 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 신고 시 `shelter_seq`를 자동 배정. 등록 뒤 주소 변경에 따른 자동 재배정 없음(Q288; `district_code`는 재도출하되 `shelter_seq`는 유지). Q288의 "ADMIN만 수동 수정"은 ADMIN 범위 외(Q411)로 **이번 구현에서는 재배정 경로가 없음**. 구조 기록의 기존 shelter_seq는 유지. 좌표→행정구역 코드는 서버가 네이버 역지오코딩으로 도출(Q406 A), 배정은 코드 매핑 우선 → NULL·미매핑이면 하버사인 최근접(Q406-2 A).
- 작성은 USER만(Q421 A). 본문 수정·삭제는 작성자 본인, 상태 종료는 작성자 또는 **이 신고의 담당 보호소(SHELTER)**만 가능(Q421·Q423 A). CLOSED 상태에서는 본문·사진 수정 불가, 수정하려면 재개(Q425 B). 사진 변경은 전체 최종 배열 교체(Q424 A).
- ACTIVE이면 `closed_reason/closed_dttm` NULL, CLOSED이면 `closed_dttm` 필수·`closed_reason` 선택. 재개 시 종료 컬럼 초기화(Q289·290).
- 신고 등록/구조 접수·완료/직접 소유자 반환으로 상태 자동 종료되지 않음. 발견 신고 소프트 삭제는 연결 구조 기록을 수정하지 않음(Q342).
- 목격 상황 전용 `circumstances` 컬럼 제거(Q397). `description`은 동물의 외형적 특징 용도로 유지.
- **몸무게(Q469, v14):** `weight VARCHAR(30) NULL` 자유 텍스트, 선택 입력. v13까지 API 10장 구조일지 응답의 `foundReportSummary.weight`에 대응하는 컬럼이 없었으나 이 신설로 실제 값이 채워짐. **목록 응답(Q468, API 8.1·8.3·8.8):** v13까지 목록에 없던 `gender`·`age`·`is_neutered`·`weight`·`reg_dttm`을 목록 SELECT에 포함해 실종 목록과 같은 구성으로 맞춤(컬럼·인덱스 변경 없음).
- **담당 보호소 목록의 신고자(Q470, v15):** API 8.8은 각 항목에 신고자 `name`·`phone`을 내림. `name`은 `reg_usr_seq → users.name` **실명**(닉네임 `nickname` 아님. 보호소 현장 연락용이며 담당 보호소 범위라 열람 허용), `phone`은 `reporter_phone`. 목록 SELECT에 `users` JOIN 1회 추가, 컬럼 변경 없음. 탈퇴 회원이면 §5 "계정 탈퇴와 데이터 표시" 규칙을 따름. API 10.2 구조 상세의 연결 신고 요약도 같은 실명 기준(Q472).
- 연락처·위치·일시 필수. 연락처 공개는 기본 비공개이며 비공개 시 작성자·담당 보호소 열람(ADMIN 열람은 범위 외). `reporter_name` 별도 저장 없이 users.name 조회(Q332).
- **주소·좌표 입력(Q467):** 실종 신고와 같이 앱이 API 5.6 주소 검색 프록시 후보(또는 지도 핀)에서 얻은 `address`·`latitude`·`longitude`를 그대로 저장. 서버 지오코딩·상세 주소 컬럼 없음. `landmark_description`은 주소가 아닌 기준점 서술로 유지.
- **목록 검색 조건(Q463, API 8.1):** 실종 신고와 같은 8개 조건(`status`·`occurred_dttm` 날짜 범위·`district_code`·종·`gender`·`is_neutered`·`description ILIKE`·`reg_dttm` 정렬)과 같은 포함 매칭. 품종·색상·`shelter_seq` 공개 필터는 없음. `landmark_description`·`address`는 키워드 검색 대상이 아님. 내 신고 목록(8.3)은 `user_seq` 기준 비삭제 전량 `reg_dttm DESC`(페이지 없음).
- **담당 보호소 목록(Q464, API 8.8):** `shelter_seq` = 토큰 보호소인 비삭제 신고. 필터 `status`, 최근 N일(`occurred_dttm >= now() - N일`, 7/30/90), `district_code`, 종, **구조 상태**, 정렬 `reg_dttm`, 페이지 size 기본 8. 구조 상태는 컬럼이 아니라 이 신고의 **최신 비삭제 `rescue_records` 행**(`reg_dttm DESC, rescue_seq DESC` 1건)에서 파생: 행 없음=`NONE`, `PROCESSING`, `DONE`, `FAILED`/`CANCELED`=`ABORTED`. 응답의 `activeRescueSeq`는 비삭제 PROCESSING/DONE 행의 PK(부분 UNIQUE로 최대 1건). 인덱스는 기존 `uq_rescue_found_active_done`과 `rescue_records(found_report_seq, reg_dttm DESC)` 후보 [가안].
- ACTIVE·비삭제·작성자 비탈퇴 신고만 AI 신규 매칭 대상으로 포함(Q343).

#### 18. `found_report_images` — 발견 신고 이미지

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

#### 19. `rescue_records` — 보호소 구조 기록

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `rescue_seq` | `INTEGER` | Y | PK, IDENTITY | 구조 기록 PK |
| `shelter_seq` | `INTEGER` | Y | FK → shelters | 구조 담당 보호소 |
| `found_report_seq` | `INTEGER` | N | FK → found_reports, NULL 허용; 진행+완료 부분 UNIQUE | 발견 신고 연계 구조 또는 보호소 자체 접수 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PROCESSING; CHECK 4종 | PROCESSING / DONE / FAILED / CANCELED |
| `completed_dttm` | `TIMESTAMPTZ` | N | PROCESSING에서 NULL, DONE/FAILED/CANCELED에서 NOT NULL(CHECK) | 처리 완료·실패·취소 시각 |
| `rescued_dttm` | `TIMESTAMPTZ` | Y | NOT NULL(Q461) | 실제 구조 일시(분 단위). 접수 시 필수 |
| `rescue_reason_code` | `VARCHAR(30)` | Y | NOT NULL; CHECK 4종(Q461) | `WANDERING`(배회/유기 의심)·`INJURY_ILLNESS`(부상/질병)·`DANGEROUS_PLACE`(위험 장소)·`OTHER`(기타) |
| `rescue_address` | VARCHAR(255) | Y | NOT NULL, 공백 금지 CHECK(Q461) | 실제 구조 장소. 보호소가 직접 입력(자동 채움·역지오코딩 없음) |
| `rescue_address_detail` | VARCHAR(200) | N | NULL 허용(Q461) | 건물명 또는 주변 지형 |
| `rescue_memo` | TEXT | N | NULL 허용 | 구조 관련 자유 메모 |
| `result_reason` | `TEXT` | N | FAILED/CANCELED 시 비공백 필수 CHECK | 구조 실패·취소 사유; 구조 사유와 구분 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 보호소가 발견 신고를 구조 대상으로 선택하면 즉시 PROCESSING 행 생성. 신고 없이 직접 생성도 허용(`found_report_seq NULL`)(Q294~297). 구조 접수는 **SHELTER 전용**이며 `shelter_seq`는 요청자의 `shelter_members` 소속으로 결정, 요청에 보호소를 지정하는 필드 없음(Q410).
- 동일 발견 신고의 비삭제 PROCESSING/DONE 합계 최대 1개(부분 UNIQUE). FAILED/CANCELED 이후 재시도 가능. **접수/등록 시각은 `reg_dttm`**으로 통일하고 `received_dttm` 컬럼은 생성하지 않음(Q384).
- 구조 사유 코드·장소·구조 일시는 **접수(PROCESSING 생성) 시점부터 필수**(Q461; Q357의 "DONE 시 필수"를 대체). 상세 장소·메모는 선택. FAILED/CANCELED에서는 비공백 `result_reason` 필수(DB CHECK)(Q357·360). `status=PROCESSING`에서는 `completed_dttm IS NULL`, 종료 상태 세 종류는 `completed_dttm IS NOT NULL`(Q384).
- 완료 구조 기록 1개로 보호동물 여러 마리 등록 가능. 보호동물이 연결된 구조 기록은 삭제 제한(Q298·353).
- 발견 신고가 삭제되어도 구조 기록·FK·진행 상태 그대로 보존, 구조 진행 가능(Q342). 구조 기록의 shelter_seq와 보호동물의 shelter_seq가 달라도 연결 허용(Q354).
- **구조 목록 조회(Q471, v15, API 10.1):** 범위는 토큰 보호소의 `is_deleted = FALSE` 행. 필터는 `status`, 최근 7/30/90일 프리셋(**`rescued_dttm >= now() - N일`**, v14까지의 `rescued_dttm` 날짜 범위 대체), 축종(연결 발견 신고 또는 연결 보호동물의 `combination_seq → animal_combinations.species_seq`), 접수 경로(`found_report_seq IS NOT NULL` = REPORT / `IS NULL` = DIRECT), `found_report_seq`. 정렬 `reg_dttm`·`completed_dttm`, size 기본 8. **상태별 건수는 같은 요청에서 `status`를 제외한 필터로 `GROUP BY status` 집계**해 목록과 함께 응답(API 10.7 `/rescues/status-counts` 삭제. `total`은 네 값의 합). 항목에 `rescue_memo`와 동물 정보(종·품종·색상·나이·성별·**`is_neutered`**·몸무게·대표 사진)를 포함하며 동물 정보 출처는 `found_report_seq`가 있으면 `found_reports`, 없으면 이 구조에 연결된 `shelter_animals` 중 `animal_seq` 최솟값 1건(둘 다 없으면 NULL). 컬럼·인덱스 변경 없음(가안 인덱스 `(shelter_seq, is_deleted, rescued_dttm)`은 §9 참고).
- **구조 상세의 연결 신고 요약(Q472, v15, API 10.2):** `found_reports`의 종·품종·색상·나이·성별·**`is_neutered`**·몸무게·주소·발생 일시·대표 사진·상태와 신고자(`users.name` 실명 + `reporter_phone`). 접수한 보호소에 공개(R3).

#### 20. `shelter_animals` — 보호동물 및 보호·입양 상태

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
| `registration_number` | `VARCHAR(20)` | N | NULL 허용; UNIQUE 없음(Q458) | 정부 동물등록제의 공식 동물등록번호. 보호소가 직접 입력, 서버가 만들지 않음. 화면의 "등록번호" 표시값(없으면 "-"/"미등록") |
| `weight` | `VARCHAR(30)` | N | NULL 허용(Q458 신설, **Q469로 타입 변경**) | 몸무게 자유 텍스트(예: "3kg"). v13까지의 `NUMERIC(5,2)` kg 숫자·CHECK `> 0`은 폐기. 공공데이터 "3(Kg)" 문자열은 변환 없이 그대로 적재 |
| `description` | `TEXT` | N | - | 외형·성격 등 설명 |
| `health_check` | TEXT | N | NULL 허용 | 보호동물 건강검진 정보 자유 텍스트 |
| `vaccination_status` | TEXT | N | NULL 허용 | 접종 상태 자유 텍스트 |
| `notice_start_date` | `DATE` | Y | NOT NULL | 등록 시 입력 가능(Q479), 생략 시 등록일; 공공데이터 초기 적재 시 적재자가 날짜 결정·입력. API 11.6으로 수정 가능 |
| `notice_end_date` | `DATE` | Y | NOT NULL; CHECK 시작≤종료 | 등록 시 입력 가능(Q479), 생략 시 시작+10일; 공공데이터 초기 적재 시 적재자가 날짜 결정·입력; 자동 상태 변경 없음. API 11.6으로 수정 가능 |
| `protection_status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PROTECTING; CHECK 4종 | PROTECTING/RETURNED/ADOPTED/ENDED. **어느 값에서든 다른 값으로 전환 가능**(Q479, API 11.6) |
| `end_reason` | `VARCHAR(20)` | N | CHECK `RETURNED/ADOPTED/NATURAL_DEATH/EUTHANIZED/OTHER` | 보호 종료 사유(확정). PROTECTING 복귀 시 NULL로 되돌림 |
| `protection_ended_dttm` | `TIMESTAMPTZ` | N | - | 보호 종료 일시(마지막 종료 상태 전환 시각). PROTECTING 복귀 시 NULL |
| `adoption_status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT UNAVAILABLE; 보호 상태 조합 CHECK | AVAILABLE/UNAVAILABLE/ADOPTED; 등록 시 AVAILABLE/UNAVAILABLE 선택 가능(Q479, 생략 시 UNAVAILABLE) |
| `adoption_reason` | `VARCHAR(200)` | N | NULL 허용(Q479 신설) | API 11.6으로 ADOPTED 처리할 때의 처리 사유. 재지정 시 덮어쓰기, PROTECTING 복귀 후에도 유지 |
| `adoption_memo` | `TEXT` | N | NULL 허용(Q479 신설) | API 11.6 ADOPTED 처리의 비고(외부 입양자 정보 등 자유 기재). 재지정 시 덮어쓰기 |
| `is_deleted` | `BOOLEAN` | Y | DEFAULT FALSE | 논리 삭제 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 주체; 시스템 적재 시 NULL(※) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 수정 주체(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 등록 시 보호 상태는 항상 `PROTECTING`, 입양 상태는 요청값(기본 `UNAVAILABLE`, Q479). 보호 상태 PROTECTING/RETURNED/ADOPTED/ENDED와 입양 상태의 허용 조합은 CHECK(Q258).
- 보호소 **직접 등록의 기본 규칙**은 등록일을 공고 시작일로 사용하고 종료일은 시작+10일로 저장(NOT NULL)하되, 등록 요청에 두 날짜를 보내면 그 값을 저장(Q479). 초기 공공데이터 적재 시에는 날짜를 적재자가 직접 정해 넣으며, 원천 날짜 우선순위는 별도로 확정하지 않음(Q388). 공통 CHECK는 시작≤종료. 날짜 경과로 상태를 자동 변경하지 않음. 공고 기간 중 수동 AVAILABLE 전환 가능(Q292·330). 두 날짜는 API 11.6으로 언제든 수정(Q479, v16까지의 "시작일 수정 불가" 폐기).
- **상태 전환(Q479, API 11.6):** 보호 상태는 **어느 값에서든 다른 값으로** 바꿀 수 있고 DB는 조합 CHECK만 강제한다. 한 트랜잭션에서 `protection_status`·`adoption_status`·`end_reason`·`protection_ended_dttm` 네 컬럼을 함께 설정한다. PROTECTING 복귀 시 종료 컬럼 두 개를 NULL로 되돌리고 `completed_application_seq`·`adoption_reason`·`adoption_memo`·`animal_returns` 행은 그대로 둔다(이력). ENDED는 `end_reason`(기본 OTHER), RETURNED는 `animal_returns` INSERT 또는 덮어쓰기, ADOPTED는 `adoption_reason`·`adoption_memo` 저장. **11.6의 ADOPTED는 `completed_application_seq`를 건드리지 않는다**(채택은 API 12.11 전용; 12.11은 이미 ADOPTED이고 FK가 NULL인 동물에도 채택 가능). 종료 상태로 바뀔 때마다 활성 신청(DRAFT/SUBMITTED/APPROVED)은 자동 REJECTED, 복귀해도 되살리지 않음. 상태 이력 테이블은 두지 않음(Q378 유지).
- **구조 정보 4종은 복사하지 않고 `rescue_seq`로 `rescue_records`를 LEFT JOIN해 조회(Q404)**. 직접 등록/공공데이터 초기 적재 시 `rescue_seq=NULL` 가능하며 그 경우 구조 날짜·사유·장소·메모는 NULL로 표시(Q405). 동일 구조 기록에 복수 동물 허용. 과거 Q352·361의 복사·독립 수정 정책은 대체됨.
- 구조 담당 보호소와 보호동물 현재 보호소는 달라도 허용(Q354). 이관 기능 자체는 구현 제외(Q283).
- 보호동물 1~3장, 이름 컬럼 없음. 상태가 PROTECTING이며 비삭제면 입양 가능 여부에 무관하게 AI 매칭 대상(Q344).
- 보호동물 등록은 **SHELTER 전용**, `shelter_seq`는 요청자 소속으로 결정(Q410). 성격 컬럼은 두지 않음(M7). **모의 입양과는 연결하지 않음**(Q482: 세션은 품종에서 시작하며 `shelter_animals`를 읽지 않음. v17까지의 "PROTECTING 동물만 시작" 가안 폐기).
- 실제 반환·입양/보호 종료에서 신청서 자동 REJECTED 등 최신 규칙 적용. 활성 DRAFT/SUBMITTED/APPROVED 신청서가 존재하면 보호동물 소프트 삭제 요청 거부(Q355).
- `age`는 자유 텍스트 유지("2026년생", "7살" 등). `weight`는 **자유 텍스트 `VARCHAR(30)`**(Q469, 반려동물·실종·발견과 동일 형식. Q458의 kg 숫자·CHECK는 폐기), `registration_number`는 공식 등록번호이며 `animal_seq`(내부 PK)와 다름. 내부 PK는 화면에 등록번호로 노출하지 않음(Q458).
- 건강검진/접종 정보는 `health_check`, `vaccination_status` 자유 입력. `completed_application_seq`는 같은 동물의 최종 입양 신청서만 참조하며 외부 입양은 NULL.
- **사용자 목록 조회(Q473, v16, API 11.1):** 비삭제 행 대상. **모든 필터는 생략 시 조건 없음(전체)**. 필터는 `protection_status`+`adoption_status` 합성 파생 상태 5값(아래 Q475와 같은 매핑, 생략 시 종료·반환·입양 완료 포함 전체), **`reg_dttm` 날짜 범위**(KST 일 단위 양끝 포함, 보호동물에는 발생 일시가 없어 등록일 기준), 보호소 소재지 `shelters.district_code`(JOIN), 종(`combination_seq → animal_breeds.species_seq`), `gender`·`is_neutered`(Q463 포함 매칭: `MALE/FEMALE` 필터는 `UNKNOWN` 포함, `true/false` 필터는 NULL 포함), `description`·`health_check` ILIKE, 정렬 `reg_dttm`만. v15까지의 `shelter_seq`·품종·색상 필터, 단독 `protection_status`·`adoption_status` 필터, `notice_end_date` 정렬은 삭제. 항목 SELECT는 신고 목록과 같은 구성(종·품종·색상 이름, `gender`·`age`·`is_neutered`·`weight`, 두 상태, `notice_start_date`·`notice_end_date`, `shelters` PK·이름·`district_code`, 대표 사진, `reg_dttm`). 컬럼·인덱스 변경 없음.
- **상세 조회(Q474·Q476·Q477, API 11.2, USER 전용):** 목록 항목 + `rescue_seq`·`microchip_number`·`registration_number`·`description`·`health_check`·`vaccination_status`·`end_reason`·`protection_ended_dttm`·`mod_dttm`, 구조 정보 LEFT JOIN(Q404), 사진 전체, 북마크 여부. 모의 입양 파생 블록은 Q482로 삭제. 종·품종·색상은 PK와 이름을 함께 내림. 보호소 블록은 `shelters` JOIN으로 **`shelter_seq`·`shelter_name`·`phone`·`address`**(Q476, 목록은 `district_code`형). 댓글 수는 없음(Q477).
- **보호소 전용 상세(Q478, v17, API 11.10):** 토큰 보호소 소속 비삭제 행 1건. 11.2 구성에서 북마크를 빼고, `animal_returns` LEFT JOIN(수령인·연락처·`returned_user_seq`·반환 시각 = `mod_dttm` 우선, 없으면 `reg_dttm`), 완료 신청서(`completed_application_seq → adoption_applications.applicant_name`)·`adoption_reason`·`adoption_memo`·`protection_ended_dttm`으로 입양 정보, `adoption_applications`를 `status`별 COUNT(DRAFT·CANCELED 제외, API 12.16과 같은 규칙)한 신청 건수를 함께 내림. 보호소 블록 없음. 컬럼·인덱스 변경 없음.
- **우리 보호소 목록(Q475, v16, API 11.3):** 토큰 보호소의 비삭제 행 대상. 탭 상태는 **`protection_status`와 `adoption_status`를 합친 파생값 5종** — `PROTECTING_AVAILABLE`(PROTECTING+AVAILABLE) / `PROTECTING_UNAVAILABLE`(PROTECTING+UNAVAILABLE) / `RETURNED`(RETURNED+UNAVAILABLE) / `ADOPTED`(ADOPTED+ADOPTED) / `ENDED`(ENDED+UNAVAILABLE). §7의 조합 CHECK(`ck_shelter_status_pair`)가 이 다섯 조합만 허용하므로 파생값은 전체를 겹침 없이 덮는다. 그 외 필터는 최근 7/30/90일(**`reg_dttm >= now() - N일`**)·종·`gender`·`is_neutered`(포함 매칭), 정렬 `reg_dttm`, size 기본 8. **상태별 건수는 같은 요청에서 탭(파생 상태)을 제외한 필터로 `GROUP BY protection_status, adoption_status` 집계**해 목록과 함께 응답(`total`은 다섯 값의 합). 별도 건수 API 없음. 컬럼·인덱스 변경 없음(가안 인덱스 §9).

#### 21. `shelter_animal_images` — 보호동물 이미지

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `shelter_animal_image_seq` | `BIGINT` | Y | PK, IDENTITY | 연결 행 PK |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals | 보호동물 |
| `image_seq` | `BIGINT` | Y | FK → images | 공통 이미지 |
| `display_order` | `SMALLINT` | Y | **UNIQUE `(animal_seq,display_order)` (Q232)** | 표시 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 1~3장 필수. 첫 이미지 대표, `(animal_seq,display_order)` UNIQUE. 사진 수정 시 자격 대상에 한해 AI 재매칭.

#### 22. `animal_returns` — 실제 보호동물 반환 완료

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `return_seq` | `BIGINT` | Y | PK, IDENTITY · ※초안 | 반환 완료 기록 ID |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals; UNIQUE | 보호동물당 완료 반환 1건 |
| `returned_user_seq` | `INTEGER` | N | FK → users, NULL 허용 | 활성 사용자 전화번호와 정확히 1명 일치할 때만 자동 연결; 신원 검증 의미 없음 |
| `recipient_name` | VARCHAR(100) | Y | NOT NULL | 실제 반환받는 사람의 이름(회원/비회원 공통) |
| `recipient_phone` | VARCHAR(30) | Y | NOT NULL | 실제 반환받는 사람의 전화번호 스냅샷 |
| `reg_usr_seq` | `INTEGER` | N | FK → users · ※초안 | 최초 기록자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 기록 생성 시각=최초 반환 완료 처리 시각(Q403) |
| `mod_usr_seq` | `INTEGER` | N | FK → users(Q479 신설) | 덮어쓴 처리자(※) |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음(Q479 신설) | 덮어쓴 시각 = 최신 반환 완료 시각. INSERT 시 NULL(Q430 규칙) |

**확정 규칙 / 참고사항**
- 별도 `returned_dttm` 없이 `reg_dttm`을 반환 완료 시각으로 사용; 소급 입력 없이 완료 시점에 기록(Q403). 같은 동물을 다시 RETURNED로 지정하면 행을 **덮어쓰고** `mod_dttm`이 최신 반환 시각이 됨(Q479). 표시·`protection_ended_dttm`에는 `COALESCE(mod_dttm, reg_dttm)`을 씀.
- 보호동물당 `animal_seq UNIQUE` 1건. API 11.6 `status=RETURNED` 처리 시 행이 없으면 INSERT, 있으면 `recipient_name`·`recipient_phone`·`returned_user_seq`를 갱신(Q479). 반환 기록과 보호동물 변경(`protection_status=RETURNED`, `adoption_status=UNAVAILABLE`, **`end_reason='RETURNED'`, `protection_ended_dttm`=이 행의 `reg_dttm` 또는 `mod_dttm`**)은 동일 트랜잭션. 종료 컬럼 두 개를 함께 바꾸지 않으면 `ck_shelter_protection_end` CHECK에 걸림. 입양 완료(ADOPTED)도 같은 방식으로 `end_reason='ADOPTED'`·`protection_ended_dttm`을 설정. 동물이 PROTECTING으로 복귀해도 이 행은 삭제하지 않음(이력).
- 반환받은 사람의 이름/전화번호는 필수, 회원 여부와 독립 보관(Q309·368).
- 해당 전화번호를 가진 비삭제 USER가 정확히 1명일 경우 `returned_user_seq` 연결, 아니면 NULL. 본인 확인/소유권 검증 의미는 없음(Q310).
- 실종 신고 FK·실종 신고 자동 종료 없음. 발견 신고 단계에서 직접 반환된 사건은 해당 신고 자체의 상태로 관리.

### 입양 신청·서식·심사

#### 23. `adoption_form_questions` — 현재 공통 20개 질문/선택지·조건

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `question_seq` | `INTEGER` | Y | PK, IDENTITY; **시드에서 1~20 고정값**(Q458) | 문항 ID. 답변 JSONB 키이자 웹·앱 프론트의 섹션·placeholder 매핑 키 |
| `question_text` | `TEXT` | Y | - | 문항 문구 |
| `question_type` | `VARCHAR(30)` | Y | NOT NULL CHECK TEXT/SINGLE_CHOICE/MULTI_CHOICE/DATE(Q458) | 주관식/단일 선택/복수 선택/날짜(답변 `"YYYY-MM-DD"`) |
| `is_required` | `BOOLEAN` | Y | DEFAULT FALSE 후보 | 필수 답변 여부 |
| `display_order` | `INTEGER` | Y | UNIQUE `(display_order)` 후보 | 문항 순서 |
| `options_jsonb` | `JSONB` | N | 선택형 질문의 문자열 배열(JSONB) | 보기 문구 직접 저장(별도 선택지 테이블 없음) |
| `condition_jsonb` | JSONB | N | NULL 허용; FE/BE 검증 | 조건부 문항 규칙: depends_on(질문 `question_seq`)/operator/value/required_when_matched |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 서비스 공통 현재 문항(**현재 20개**, 숫자를 고정하지 않고 "시드된 현재 문항 전체")만 유지; `adoption_form_templates` 및 템플릿·버전 관리 없음(Q402·Q458). TEXT/SINGLE_CHOICE/MULTI_CHOICE/DATE 4종(MULTI_CHOICE는 현재 문항에서 미사용).
- **`question_seq` 고정 시드(Q458):** 섹션 제목·placeholder는 저장하지 않고 웹·앱 프론트가 `question_seq`로 매핑해 고정 표시하므로, Flyway 시드는 `OVERRIDING SYSTEM VALUE`로 1~20을 직접 지정해 모든 환경에서 번호가 같아야 함. 화면 순서는 `display_order`. 조건부 하위 문항(동거인 동의, 함께 사는 동물 설명, 교육기관·과정·이수일)은 `condition_jsonb`로 표현. 문항과 보기 수정 시 해당 현재 행을 갱신하며 이전 질문·보기·버전 이력은 저장하지 않음.
- `options_jsonb`는 표시 문자열 배열. **`question_seq`가 답변 JSONB의 키**이고, 화면 순서는 `display_order`로 표시. 질문을 교체할 때 기존 행의 `question_seq`를 유지해야 과거 답변과의 식별 연결이 유지됨(Q402 추가 결정; 과거 Q321 대체).
- `condition_jsonb`의 질문 의존성은 `question_seq`로 참조. 조건부 노출·필수 검증은 FE/BE에서 수행. 숨겨진 응답은 제거; DB CHECK/트리거 추가 없음(Q322·323).
- 질문 변경 이력과 제출 당시 문항 스냅샷을 저장하지 않으므로 **기존 제출 답변도 현재 질문 문구와 함께 표시**. 질문 의미 변경 시 과거 답변의 의미가 달라 보일 수 있는 위험을 수용한 정책(Q402 추가 결정).

#### 24. `adoption_applications` — 신청서 및 JSONB 답변

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `application_seq` | `BIGINT` | Y | PK, IDENTITY | 실제 입양 신청 PK |
| `user_seq` | `INTEGER` | Y | FK → users | 신청자 |
| `animal_seq` | `INTEGER` | Y | FK → shelter_animals | 대상 보호동물 |
| `shelter_seq` | `INTEGER` | Y | FK → shelters, **신청 시점 스냅샷(Q214·250)** | 신청 당시 보호소; 이관 후 변경하지 않음 |
| `answers_jsonb` | JSONB | Y | NOT NULL DEFAULT `{}` | 질문별 응답을 문자열화한 `question_seq` 키로 저장(조건부 미응답 문항은 생략 가능) |
| `care_address` | VARCHAR(200) | N | NULL 허용; 제출 시 필수(BE)(Q458) | 실제 양육 예정 장소(도로명 주소) |
| `care_address_detail` | VARCHAR(100) | N | NULL 허용(Q458) | 동·호수 등 상세 주소 |
| `email` | VARCHAR(254) | N | NULL 허용; 형식은 BE 검증(Q458) | 연락용 이메일(선택). `users`에 이메일 컬럼이 없어 신청서마다 입력 |
| `visit_hope_date` | DATE | N | NULL 허용; 제출 시 필수·제출일 이후(BE)(Q458) | 방문 상담 희망일 |
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
- 동일 `(user_seq,animal_seq)`의 비삭제 상태 DRAFT/SUBMITTED/APPROVED *통틀어* 최대 1건(부분 UNIQUE; Q264·363). DRAFT→SUBMITTED는 같은 행 수정. **DRAFT 없이 제출하면 새 행을 바로 SUBMITTED로 INSERT**(Q458, API 12.6 한 트랜잭션).
- 신청서 고정 필드(`care_address`·`care_address_detail`·`email`·`visit_hope_date`)는 문항이 아니라 컬럼으로 관리(Q458 A). DRAFT에서는 비어도 되고 제출 시 장소·희망일 필수, 제출 후 답변과 함께 불변.
- DRAFT는 빈/미완성 응답 허용; 제출시 **현재 질문 전체(20개)**와 조건부 필수 문항, 고정 필드를 BE 검증. 제출 후 응답 불변. DRAFT 삭제는 물리 DELETE 및 해당 답변도 함께 제거(Q269).
- 질문의 버전이나 제출 당시 질문 문구는 저장하지 않으며, 과거 제출 답변도 **현재 질문 문구로 조회**(Q402 A). 반면 신청자 인적 사항은 기존 정책대로 제출 시 `applicant_name/birth_date/phone` 스냅샷을 보존. DRAFT 작성 시에는 실시간 users 프로필 사용(Q325).
- **수료증 기능은 Q460에서 제외, Q462(v13)에서 `certificate_storage_key` 컬럼 삭제.** Q277~281·Q443의 수료증 정책은 보존 기록일 뿐 현행 모델에 흔적이 없음(`certificates/` 접두어·`upload:file:*` Redis 키·`ADOPTION_CERTIFICATE_*` 코드 모두 없음).
- SUBMITTED 상태만 신청자 직접 취소 가능; CANCELED는 취소 사유 컬럼 및 이력 사유 없음(Q326).
- `APPROVED`는 서류 승인, `ADOPTED`는 실제 입양 완료. 별도 `adoption_processes` 없이 이 신청서 한 행에서 통합 입양 프로세스를 관리(Q375). 동일 동물의 다른 사용자들도 APPROVED 가능. REJECTED 이유는 **구분 가능한 자연어 텍스트**로 기록하며 별도 상태 이력·거절 종류·단계 컬럼을 생성하지 않음(Q376·377).
- 보호동물 RETURNED/ADOPTED/ENDED 시 비완료 활성 신청서 DRAFT/SUBMITTED/APPROVED는 자동 REJECTED. 시스템이 상황에 맞는 실제 한국어 `rejection_reason` 문장을 생성하고 `reviewed_dttm=자동 거절 시각`, `reviewed_usr_seq=NULL`로 저장(Q372·376). 기존 APPROVED에서 자동 REJECTED가 되면 이전 승인 처리시각은 별도 보존하지 않음(Q376·377). 동물이 PROTECTING으로 복귀해도 자동 거절된 신청서는 되살리지 않음(Q479, 재신청은 새 행). 회원 탈퇴 시 본인 활성 신청서는 CANCELED, 이미 ADOPTED는 보존(Q318).
- **완료 신청서 FK와 API 11.6(Q479):** `shelter_animals.completed_application_seq`는 API 12.11(채택)만 설정하고, 보호동물 관리 API 11.6의 ADOPTED 처리는 이 값을 건드리지 않음(NULL이면 외부 입양). 12.11은 동물이 이미 ADOPTED여도 FK가 NULL이면 채택 가능하며, 이미 다른 신청서가 연결돼 있으면 거부.
- 기존 활성 동물의 단순 AVAILABLE→UNAVAILABLE에서는 기존 SUBMITTED/APPROVED를 유지하고 DRAFT 행도 남기되, **신규 신청과 기존 DRAFT의 제출은 모두 차단**(다시 AVAILABLE이 되면 제출 가능, Q437). 거절/취소된 뒤 동물 입양 가능 시 재신청은 신규 행(Q271).

### 채팅

#### 25. `chat_rooms` — 일반회원·보호소 채팅방

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

#### 26. `chat_participants` — 채팅방 읽음·나가기 기준

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
- 채팅방 나가기 후 재입장은 같은 방을 사용하되 이전 메시지 비노출.
- **미읽음(Q454):** 미읽음 메시지 = 그 방에서 `chat_message_seq`가 내 `last_read_message_seq`와 `last_hidden_message_seq`보다 모두 크고 **발신자가 본인이 아닌** 메시지. 방별 수와 전체 합계 모두 **조회 시 계산**하며 카운터 컬럼은 두지 않음(전송 시 +1·읽음 시 0 방식은 읽음 경쟁·이중 원천 문제로 미채택). `uq_chat_message_room_id(chat_room_seq, chat_message_seq)`로 포인터 이후 구간만 스캔.
- **목록 노출(Q454):** 내 `last_hidden_message_seq` 이후 메시지가 하나라도 있는 방만 채팅 목록에 노출(메시지 없는 방·나간 뒤 새 메시지 없는 방 제외). "내 방" 조회는 `participant_user_seq`/`participant_shelter_seq` 인덱스 사용.

#### 27. `chat_messages` — 텍스트·이미지·게시물 공유 메시지

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
- 쓰기 경로는 **STOMP SEND 하나**(R16 B, API 14장): chat 서비스가 `/app/rooms/{chatRoomSeq}/messages`로 받은 메시지를 검증·저장한 뒤 `/topic/rooms/{chatRoomSeq}`로 브로드캐스트. REST 전송 경로는 없음. 저장 후 `ChatMessageSent` 이벤트로 notification이 보낸 사람을 제외한 참여자 전원에게 **FCM 데이터 메시지**를 보내고, 표시 여부는 앱이 현재 열린 방 기준으로 판단(R9 C). 서버는 구독 상태를 저장하지 않음.

### 댓글·답글

#### 28. `missing_report_comments` — 실종 신고 댓글·답글

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

#### 29. `found_report_comments` — 발견 신고 댓글·답글

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

#### 30. (삭제, Q477) ~~`shelter_animal_comments`~~ — 보호동물 댓글·답글

- **v17에서 삭제.** 보호동물 게시글에는 댓글 기능을 두지 않기로 확정(Q477). v16까지의 테이블 정의(실종 댓글과 같은 1-depth·복합 FK·`image_seq` 구조)는 `animal_service_db_spec_v16.md` 30번에 보존. 번호 30은 빈 번호로 두고 재번호하지 않음.
- 함께 폐기: 공통 코드 `SHELTER_ANIMAL_COMMENT`(§4), `reports.target_type`·`notifications.target_type` CHECK의 해당 값, `notifications` COMMENT 계열의 `SHELTER_ANIMAL` 게시물 참조, §7의 `uq_shelter_comment_parent_post`·`fk_shelter_comment_parent_post`. 기존 DB가 있다면 `reports`·`notifications`의 해당 행을 정리한 뒤 `DROP TABLE`.

### 신고·북마크·알림

#### 31. `reports` — 회원/게시물/댓글 통합 신고

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `report_seq` | `BIGINT` | Y | PK, IDENTITY · ※초안 | 통합 신고 ID |
| `reporter_user_seq` | `INTEGER` | Y | FK → users | 신고 접수한 회원 |
| `target_type` | `VARCHAR(40)` | Y | CHECK: USER/MISSING/FOUND 및 2종 COMMENT(Q449·Q477) | 공통 대상 타입(Q387). 보호동물 게시글 `SHELTER_ANIMAL`은 제외, 보호동물 댓글은 기능 삭제로 `SHELTER_ANIMAL_COMMENT`도 제외 |
| `target_seq` | `BIGINT` | Y | 다형성 참조 | 대상 PK(사용자, 콘텐츠, 댓글) |
| `reason_code` | `VARCHAR(30)` | Y | NOT NULL; CHECK 대상별 허용 조합(`ck_report_reason_by_target`, Q449) | 신고 사유 코드. 게시글(MISSING/FOUND) `FALSE_INFO/SPAM_AD/OFFENSIVE/OTHER`, 사용자·댓글 `HARASSMENT/SPAM_AD/OBSCENE/OTHER`. 자유 입력 컬럼 없음 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PENDING; CHECK PENDING | 관리자 심사 기능 제외; 단일 상태만 사용 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 등록 시각 |
| `mod_usr_seq` | `INTEGER` | N | FK → users · ※초안 | 마지막 수정 주체(관리자 심사 기능 없음) |
| `mod_dttm` | `TIMESTAMPTZ` | N | ※초안 | 마지막 처리시각 |

**확정 규칙 / 참고사항**
- 신고 대상은 USER, MISSING, FOUND 및 2종 댓글(`MISSING_COMMENT`/`FOUND_COMMENT`)의 **5종**(Q449, Q477로 `SHELTER_ANIMAL_COMMENT` 제외). 보호동물 게시글(`SHELTER_ANIMAL`)과 보호소/보호소 계정 자체는 신고 불가. 채팅 메시지 직접 신고 없음(채팅방의 상대 신고는 `USER`). 댓글 신고는 작성자가 아니라 **댓글 자체**가 대상.
- 신고 사유는 `reason_code` 선택만 받고 자유 텍스트는 저장하지 않음(Q449; Q308의 `reason_detail` 필수·`reason_code` 삭제를 대체). 사유 코드 2세트: 게시글용 `FALSE_INFO`(허위 정보/잘못된 동물 정보)·`SPAM_AD`(스팸/광고)·`OFFENSIVE`(욕설/불쾌한 내용)·`OTHER`(기타), 사용자·댓글용 `HARASSMENT`(욕설/비방/괴롭힘)·`SPAM_AD`·`OBSCENE`(음란/부적절한 내용)·`OTHER`. 대상·사유 조합은 DB CHECK `ck_report_reason_by_target`로 강제.
- 현재 `status=PENDING`만 사용하며 관리자 신고 심사/제재 구현 제외. `(reporter_user_seq,target_type,target_seq)` 전역 UNIQUE로 같은 대상 재신고 불가(Q307).
- `user_blocks` 테이블과 자동 차단/수동 차단 기능 일체 제외(Q306).

#### 32. `bookmarks` — 게시물 북마크

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

#### 33. `notifications` — 일반·AI·업무 알림

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `notification_seq` | `BIGINT` | Y | PK, IDENTITY | 일반 알림 PK |
| `recipient_user_seq` | `INTEGER` | Y | FK → users | 수신 사용자별 알림 1행 |
| `notification_type` | `VARCHAR(40)` | Y | NOT NULL; CHECK 9종(Q447) | `COMMENT/COMMENT_REPLY/AI_MATCH/ADOPTION_SUBMITTED/ADOPTION_APPROVED/ADOPTION_REJECTED/ADOPTION_COMPLETED/SHELTER/SYSTEM`. 앱 title을 결정하는 정확한 이벤트 유형(REPORT 미추가) |
| `message` | `TEXT` | Y | NOT NULL | 알림 **본문(body) 스냅샷**. 발행 서비스가 유형별 템플릿으로 생성 시점에 완성; DB 저장값과 FCM body 문자열 동일(Q381·Q447). title은 저장하지 않음 |
| `target_type` | `VARCHAR(40)` | N | NULL 허용, 공통 대상 코드(Q387) | 클릭 시 이동 대상 종류; AI_MATCH는 매칭 상대 FOUND/SHELTER_ANIMAL, COMMENT 계열은 댓글이 달린 게시물 MISSING/FOUND(Q446, Q477로 SHELTER_ANIMAL 제외) |
| `target_seq` | `BIGINT` | N | NULL 허용, 다형성 참조(Q391) | 클릭 시 이동 대상 ID; AI_MATCH는 매칭 상대 PK, COMMENT 계열은 게시물 PK |
| `missing_report_seq` | `INTEGER` | N | FK → missing_reports, NULL 허용 | AI 매칭 기준 신고; 중복 검사 키(BE) |
| `comment_seq` | `BIGINT` | N | NULL 허용; FK 없음(다형성, Q446) | COMMENT 계열 알림을 발생시킨 댓글·답글 PK. 댓글 테이블은 `target_type`의 게시물 종류로 결정 |
| `parent_comment_seq` | `BIGINT` | N | NULL 허용; FK 없음(다형성, Q446) | `COMMENT_REPLY`이면 루트 댓글 PK, `COMMENT`이면 NULL |
| `is_read` | `BOOLEAN` | Y | DEFAULT FALSE | 읽음 여부 |
| `read_dttm` | `TIMESTAMPTZ` | N | ※초안 | 읽은 시각 |
| `is_deleted` | `BOOLEAN` | Y | NOT NULL DEFAULT FALSE | 개별/전체 알림 삭제 소프트 삭제; 숨김 기능 없음 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 발생/저장 시각 |

**확정 규칙 / 참고사항**
- 사용자별 행 저장. 알림 유형은 **9개**(Q447; Q398의 5종을 세분화): `COMMENT`(내 게시물에 루트 댓글), `COMMENT_REPLY`(내 루트 댓글에 답글), `AI_MATCH`, `ADOPTION_SUBMITTED`(보호소 수신), `ADOPTION_APPROVED`·`ADOPTION_REJECTED`·`ADOPTION_COMPLETED`(신청자 수신; 수동 거절·자동 거절·승인 후 실패는 모두 `ADOPTION_REJECTED`), `SHELTER`, `SYSTEM`. `SYSTEM`은 서비스 점검·운영 공지 등 일반 시스템 알림(유형만 예약), `REPORT`는 미추가. `SHELTER`는 보호소 고유 업무(담당 발견 신고 배정 등)용으로, 보호소에 수신되는 모든 알림을 뜻하지 않음. AI 매칭은 실종 신고 작성자에게만 전송/저장(Q345).
- AI 중복 기준 `(missing_report_seq,target_type,target_seq)` 및 `notification_type='AI_MATCH'`은 생애 최초 1회이며, 소프트 삭제된 알림도 중복 검사에 포함(Q291). **전용 UNIQUE 인덱스는 생성하지 않음**(Q389). BE는 관련 `missing_reports` 행에 `SELECT ... FOR UPDATE` 잠금을 획득한 후 기존 알림 조회·삽입을 하나의 트랜잭션으로 처리(Q390). AI_MATCH에서 `missing_report_seq`와 `target_type`, `target_seq`는 세 값 모두 필수이며 `target_type`은 `FOUND`/`SHELTER_ANIMAL`만 허용하도록 BE 검증. 그 외 유형의 `missing_report_seq`는 NULL. AI 매칭 상대 전용 `matched_target_type/seq`는 제거(Q399).
- 알림 개별/전체 삭제는 `is_deleted=TRUE`로 변경, 숨김 별도 기능 없음(Q339). `target_type/target_seq`는 둘 다 NULL 허용(Q391); 연결 대상이 있는 경우 BE에서 값의 완결성 및 존재·권한을 검증(Q382). `ADOPTION_*` 알림의 이동 대상 `target_type=ADOPTION`은 `adoption_applications.application_seq`로 통합 입양 프로세스 화면을 지정(Q387).
- 탈퇴한 사용자가 발생시킨 기존 알림은 삭제하지 않고 보존(행위자 컬럼이 없으므로 표시 대체도 없음). `message`에 **BE가 생성한 정확히 같은 문자열을 DB에 저장하고 FCM 푸시 body로 재사용**(Q381). DB 저장 확정 후 FCM 발송하며, 실패해도 DB 알림은 보존하고 재시도하지 않음. 채팅 푸시 이력은 저장하지 않음.
- **COMMENT 계열 이동 정보(Q446·Q477):** `COMMENT`/`COMMENT_REPLY`는 `target_type/target_seq`에 댓글이 달린 게시물(`MISSING`/`FOUND`)과 PK, `comment_seq`에 알림을 발생시킨 댓글·답글 PK, `parent_comment_seq`에 답글일 때 루트 댓글 PK를 저장. 그 외 유형은 두 컬럼 NULL(`ck_notification_comment_ref`). 댓글 테이블이 2개라 FK를 두지 않고 값은 core-api가 이벤트 페이로드로 채움. 댓글 코드(`*_COMMENT`)는 알림 target에 쓰지 않음. 보호동물 댓글이 없으므로 COMMENT 계열의 수신자는 항상 USER(신고 작성자).
- **title·body(Q447):** title은 DB에 저장하지 않고 서버 enum의 유형별 고정 문구로 API 응답·FCM title을 채움(문구 변경 시 과거 알림도 새 문구). body는 `message`에 생성 시점 스냅샷으로 저장하며 원본 수정은 반영하지 않음. 템플릿·대체 문구·댓글 본문 최대 100자는 [가안](§9). 행위자 컬럼 `actor_user_seq`는 화면에서 쓰지 않아 **삭제**(Q447).
- **삭제된 댓글(Q448):** 댓글·답글 소프트 삭제 커밋 후 core-api가 `CommentDeleted { targetType, commentSeq }` 이벤트를 발행하고, notification이 `notification_type IN ('COMMENT','COMMENT_REPLY')`이며 `(target_type, comment_seq)`가 일치하는 행의 `message`를 "삭제된 댓글입니다"로 갱신(쓰기 소유권 유지). title은 그대로, 이미 발송된 푸시는 회수하지 않음. 댓글 수정은 알림을 갱신하지 않음.

#### 34. `device_tokens` — FCM 기기 토큰

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
- 새 기기 로그인 시 기존 토큰 비활성화. 회원 탈퇴 시 본인 활성 토큰 모두 비활성화(Q327; core-api가 `UserDeleted` 이벤트를 내고 notification이 처리). FCM 재시도/발송 로그 테이블 없음. 로그아웃 요청(API 2.4)에 `fcmToken` 선택 필드가 있으면 그 기기 토큰만 비활성화(Q419 A). 토큰 등록(API 17.6)은 같은 토큰 재등록 시 upsert 200(Q415)이며, **다른 계정에서 활성인 토큰은 그 계정 행을 비활성화하고 요청 계정으로 옮김(기기 양도)**. 부분 UNIQUE 충돌을 모두 서버가 해소하므로 409 응답 코드는 없음(Q439).
- `core` 스키마, 쓰기 주체는 notification 서비스. `user_seq` FK는 유지(같은 스키마). 예외로 `AI_MATCH` 행만 core-api가 직접 INSERT(§1 쓰기 소유권 예외, Q389·390).
- 모의 입양 푸시(`SimulationPushRequested`, DB 저장 없음)도 같은 활성 토큰으로 발송(Q481).

### 모의 입양 (M1~M17, v18 Q481~Q484, v19 Q485, v20 Q490~Q499)

> 접두어 `simulation_`. **품종을 골라 가상의 이름을 붙인 동물**을 실시간 7일 동안 돌보는 체험이며(실제 보호동물과 연결하지 않음, Q482), 세션·로그·리포트는 영구 보존(소프트 삭제 없음). PK는 `BIGINT IDENTITY`, 시각은 `TIMESTAMPTZ`. 스탯 수치·배율 같은 튜닝값은 DB에 저장하지 않고 코드 설정으로 관리(§"코드 설정 가안", Q429 A). CONDITION 이벤트 발생 확률(`trigger_probability`, Q481)과 이벤트의 정답 행동·해결/방치 효과·비용(Q486)만 시나리오 컬럼. 상점 물품·시나리오는 마스터(초기 데이터)이며 Flyway 시드로 적재, API로 관리하지 않음(Q412 A). 이벤트는 선택지 없이 **정답 행동 1개**로 해결(Q486). 알림은 저장하지 않고 FCM만 보냄(Q481).
>
> **스키마(Q420 D·Q482):** 9개 테이블 모두 `simulation` 스키마에 있고 쓰기 주체는 simulation 서비스뿐입니다. `core` 스키마를 가리키는 `user_seq`·`breed_seq`는 **FK 없는 논리 참조**이며 존재 검증은 simulation 서비스가 `core` 테이블을 읽기 전용 JOIN으로 수행합니다. 9개 테이블 사이의 FK는 그대로입니다. 42번 `simulation_event_choices`는 Q486으로, 45번 `simulation_notifications`는 Q481로 삭제(빈 번호).

#### 35. `simulation_sessions` — 세션 본체 및 현재 스탯

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `session_seq` | `BIGINT` | Y | PK, IDENTITY | 세션 키 |
| `user_seq` | `INTEGER` | Y | NOT NULL; **논리 참조 → core.users(FK 없음, Q420)** | 체험 사용자(USER만) |
| `breed_seq` | `INTEGER` | Y | NOT NULL; **논리 참조 → core.animal_breeds(FK 없음, Q482)** | 시작 시 고른 품종. 품종명·대표 이미지는 조회 시 JOIN 파생 |
| `animal_name` | `VARCHAR(6)` | Y | NOT NULL(Q484) | 시작 시 붙인 가상 이름(최대 6자, 한글 제한은 프론트 검증). 변경 없음 |
| `species_type` | `VARCHAR(10)` | Y | CHECK `DOG/CAT` | 시작 시 품종→종 스냅샷(M8) |
| `personality` | `VARCHAR(20)` | Y | CHECK `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY` | 시작 시 품종 성격 스냅샷(M7) |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT IN_PROGRESS; CHECK `IN_PROGRESS/COMPLETED/ABANDONED/FAILED` | 세션 상태(M6) |
| `expected_cost` | `INTEGER` | Y | CHECK `>= 0` | 입력한 일주일 예상 비용(원) |
| `balance` | `INTEGER` | Y | 음수 허용 | 현재 잔액; 시작값 = expected_cost(M5) |
| `started_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 시작 시각 |
| `ends_dttm` | `TIMESTAMPTZ` | Y | CHECK `> started_dttm` | 예정 종료(시작 + 7일, M2) |
| `ended_dttm` | `TIMESTAMPTZ` | N | IN_PROGRESS에서 NULL, 종료 상태에서 NOT NULL(CHECK 후보) | 실제 종료 시각 |
| `satiety` | `SMALLINT` | Y | CHECK 0~100 | 포만감(100=배부름). v18의 `hunger` 개명(Q485) |
| `hydration` | `SMALLINT` | Y | CHECK 0~100 | 수분(100=수분 충분) |
| `cleanliness` | `SMALLINT` | Y | CHECK 0~100 | 청결도(100=깨끗함) |
| `bond` | `SMALLINT` | Y | CHECK 0~100 | 친밀도(100=매우 친밀함). v18의 `friendship` 개명(Q485) |
| `calmness` | `SMALLINT` | Y | CHECK 0~100 | 안정감(100=편안함). v18의 `stress`를 **방향 반전**해 개명(Q485) |
| `health` | `SMALLINT` | Y | CHECK 0~100 | 건강(100=매우 건강함); 0이면 FAILED |
| `energy` | `SMALLINT` | Y | CHECK 0~100 | 활력(100=활기참). v18의 `activity` 개명(Q485) |
| `stats_calculated_dttm` | `TIMESTAMPTZ` | Y | - | 스탯 7개의 마지막 지연 계산 기준 시각 |
| `last_action_dttm` | `TIMESTAMPTZ` | N | - | 마지막 돌봄 행동 시각(방치 판정) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- **USER당 진행 중 세션 1개**: 부분 UNIQUE `(user_seq) WHERE status='IN_PROGRESS'`(M6). 종료 후 새 세션 시작 가능.
- 시작 조건(BE, Q482·Q484): 요청자 `role=USER`, `breed_seq`가 존재하는 품종, 품종의 종이 `DOG`/`CAT`(`ETC`는 시작 불가, M8), `animal_name` 필수 1~6자. simulation 서비스가 `core.animal_breeds`·`animal_species`를 읽기 전용 JOIN(잠금 없음)으로 확인한 뒤 `species_type`·`personality`를 스냅샷(품종 성격은 NOT NULL이라 랜덤 배정 없음). 스탯은 코드 설정 초기값, `balance=expected_cost`, `ends_dttm=started_dttm+7일`, `stats_calculated_dttm=started_dttm`. 실제 보호동물·보호소는 읽지도 저장하지도 않음.
- 보호소 열람(Q483): SHELTER 토큰이면 종료 리포트를 누구 것이든 열람(소속 판정 없음). core-api의 입양 신청 상세 파생 조회(12.15)는 신청서 `user_seq`로 `simulation.simulation_reports`·`simulation_sessions`를 읽기 전용 JOIN.
- **스탯 방향(Q485):** 7종 모두 **높을수록 좋고 시간이 지나면 떨어지는** 한 방향. 낮을수록 좋은 스탯(v18의 `stress`)은 없으며, 스탯 계산기·시드 효과·조건식에 방향 예외를 두지 않음. 100일 때 라벨은 앱 상수(서버는 숫자만).
- **지연 계산(M2):** 조회·행동·스케줄러가 `stats_calculated_dttm` 이후 경과 tick만큼 스탯 변화를 적용하고 시각을 갱신. 클라이언트는 스탯을 올리지 못함. 종료 판정도 이때 수행: `ends_dttm` 도달 → `COMPLETED`, `health=0` → `FAILED`("돌봄 부족", 죽음 표현 없음). 두 경우 모두 `ended_dttm` 기록 + `simulation_reports` 생성을 한 트랜잭션에서 처리하고, 커밋 후 비동기로 리포트 `summary`를 채운 뒤 `SESSION_END` 푸시 이벤트 발행(저장 없음, Q481·Q487).
- 중도 포기는 `ABANDONED`(리포트 생성). 회원 탈퇴 시 진행 중 세션도 `ABANDONED`. 품종 마스터가 바뀌어도 세션은 스냅샷으로 진행.
- `last_action_dttm`은 CARE·WALK 행동에서만 갱신(이벤트 해결은 행동 안에서 일어나므로 포함, Q486. 표시·통계용이며 방치 알림은 Q481로 폐지). `balance`는 구매·이벤트 비용으로 음수까지 내려갈 수 있음(M5).

#### 36. `simulation_shop_items` — 펫샵 물품 마스터(초기 데이터)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `item_seq` | `BIGINT` | Y | PK, IDENTITY | 물품 키 |
| `item_name` | `VARCHAR(100)` | Y | - | 물품명 |
| `category` | `VARCHAR(20)` | Y | CHECK `FOOD_BOWL/WATER_BOWL/LITTER_BOX/TOY/FURNITURE/LEASH/GROOMING_KIT/FOOD/SNACK/LITTER/SHAMPOO/MEDICINE/POOP_BAG/CATNIP` | 분류 14종(M9 → Q491) |
| `is_consumable` | `BOOLEAN` | Y | 기구 7종은 FALSE, 소모품 7종은 TRUE(CHECK) | 소모품 여부 |
| `price` | `INTEGER` | Y | CHECK `>= 0` | 가격(원) |
| `description` | `TEXT` | N | - | 상세 설명 |
| `image_key` | `VARCHAR(512)` | N | - | 물품 사진의 S3 객체 키(시드 적재). v9의 `image_seq FK→images`를 대체(Q420 D). `images` 행을 만들지 않으며 참조 카운트(Q383) 대상 아님 |
| `species_scope` | `VARCHAR(10)` | Y | NOT NULL DEFAULT ALL; CHECK `DOG/CAT/ALL` | 적용 종 |
| `effects_jsonb` | `JSONB` | N | - | 사용 시 스탯 변화. 예 `{"satiety":30,"bond":2}`(키는 35번 스탯 컬럼명, 양수가 좋은 방향, Q485) |
| `is_active` | `BOOLEAN` | Y | NOT NULL DEFAULT TRUE | 판매 여부 |
| `display_order` | `INTEGER` | Y | NOT NULL DEFAULT 0 | 정렬 순서 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- 기구 7종(`FOOD_BOWL`, `WATER_BOWL`, `LITTER_BOX`, `TOY`, `FURNITURE`(이동장·쉼터·숨숨 하우스·캣타워, 지금은 보유만), `LEASH`(하네스와 리드줄, 산책 조건 Q495), `GROOMING_KIT`(위생 관리 키트, 목욕 조건 Q493))은 `is_consumable=FALSE`로 한 번 사면 세션 내 영구. 소모품 7종(`FOOD`, `SNACK`, `LITTER`, `SHAMPOO`(판매 중단), `MEDICINE`, `POOP_BAG`(산책용), `CATNIP`(고양이 전용))은 사용 시 수량 차감(M9·Q491). 물은 무료이며 물그릇만 구매. 메인 화면의 밥그릇·물그릇·화장실·장난감 오브젝트는 해당 기구를 보유해야 활성.
- 초기 물품은 35개 시드(`V20261109_1110`, Q491). 순한 샴푸(34번)는 `is_active=FALSE`(Q493). 묶음 물품의 1개당 사용 횟수는 컬럼이 아니라 코드 설정(Q492, §"코드 설정 가안").
- 펫샵→인벤토리 이동 시 결제(M5). 물품 행은 Flyway 시드로 적재하고 관리 API 없음(Q412 A). `effects_jsonb` 키는 세션 스탯 컬럼명(`satiety/hydration/cleanliness/bond/calmness/health/energy`)과 동일하며 양수가 좋은 방향(Q485; 예 SNACK `{"satiety":10,"bond":4,"calmness":5}`, GROOMING_KIT `{"cleanliness":40,"calmness":-5}`, CATNIP `{"energy":15,"bond":3,"calmness":-5}`). 성격 배율을 곱하지 않음(Q490). 물품 사진은 시드 시 S3에 올린 객체 키를 `image_key`에 직접 기록하며 API 응답에서 URL로 변환.

#### 37. `simulation_inventory_items` — 세션별 보유 물품

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `inventory_item_seq` | `BIGINT` | Y | PK, IDENTITY | 보유 행 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions | 세션 |
| `item_seq` | `BIGINT` | Y | FK → simulation_shop_items | 물품 |
| `quantity` | `INTEGER` | Y | CHECK `>= 0` | 보유 수량 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 최초 보유 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수량 변경 시각 |

**확정 규칙 / 참고사항**
- `UNIQUE(session_seq,item_seq)`. 구매 시 UPSERT로 수량 증가. 기구는 수량 1 고정이며 재구매 요청은 409. 소모품은 사용 시 -1, 0이 되어도 행은 유지.

#### 38. `simulation_transactions` — 지출 내역(리포트 근거)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `transaction_seq` | `BIGINT` | Y | PK, IDENTITY | 거래 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions | 세션 |
| `transaction_type` | `VARCHAR(20)` | Y | CHECK `PURCHASE/EVENT` | 펫샵 구매 / 이벤트 해결 비용(`VET_VISIT` 진료비, M15·Q486) |
| `item_seq` | `BIGINT` | N | FK → simulation_shop_items; PURCHASE면 NOT NULL(CHECK 후보) | 구매 물품 |
| `occurrence_seq` | `BIGINT` | N | FK → simulation_event_occurrences; EVENT면 NOT NULL(CHECK 후보) | 비용이 발생한 이벤트 |
| `quantity` | `INTEGER` | Y | NOT NULL DEFAULT 1; CHECK `>= 1` | 수량 |
| `amount` | `INTEGER` | Y | CHECK `>= 0` | 지출액(원) |
| `balance_after` | `INTEGER` | Y | - | 지출 후 잔액(음수 가능) |
| `description` | `VARCHAR(200)` | N | - | 표시 문구 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 거래 시각 |

**확정 규칙 / 참고사항**
- 종전 초안의 `VET` 유형은 없음. 병원 진료비는 정답 행동이 `VET_VISIT`인 이벤트를 해결할 때 시나리오 `resolve_cost`로 `EVENT` 거래에 기록(M15·Q486). `VET_VISIT` 외의 행동은 거래를 만들지 않음.
- 리포트의 카테고리별 합계는 PURCHASE는 물품 `category`, EVENT는 `EVENT`로 집계(분류 14종+`EVENT` 키를 모두 채움, Q496). 일자별 지출은 `reg_dttm`을 세션 시작 기준 1~7일차(24시간 단위)로 환산하고 7일차를 넘는 값은 7일차에 넣음.

#### 39. `simulation_action_logs` — 행동 로그(스탯 변화 근거·방치 판정)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `action_log_seq` | `BIGINT` | Y | PK, IDENTITY | 로그 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions | 세션 |
| `action_type` | `VARCHAR(20)` | Y | CHECK `CARE/WALK/EVENT/SYSTEM` | 분류 |
| `action_code` | `VARCHAR(30)` | Y | BE 검증 | CARE: `FEED/WATER/CLEAN_LITTER/BATHE/PLAY/SNACK/MEDICINE/GIVE_CATNIP/VET_VISIT` · WALK: `WALK_START/WALK_END/GIVE_WATER/GIVE_SNACK` · EVENT: `EVENT_EXPIRE` · SYSTEM: `SESSION_START/SESSION_END` (Q486: `VET_VISIT` 추가, `EVENT_ANSWER` 삭제. Q494: `GIVE_CATNIP` 추가) |
| `item_seq` | `BIGINT` | N | FK → simulation_shop_items | 소모한 물품 |
| `walk_seq` | `BIGINT` | N | FK → simulation_walks | 산책 중 행동 |
| `occurrence_seq` | `BIGINT` | N | FK → simulation_event_occurrences | CARE/WALK 행: 이 행동이 **해결한** 이벤트 · EVENT 행(`EVENT_EXPIRE`): 만료된 이벤트(Q486) |
| `stat_delta_jsonb` | `JSONB` | N | - | 이 행동으로 바뀐 스탯 |
| `stats_after_jsonb` | `JSONB` | Y | NOT NULL | 행동 직후 스탯 7종 스냅샷 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 행동 시각 |

**확정 규칙 / 참고사항**
- 돌봄 행동별 필요 기구·소모품(BE 검증): `FEED`=FOOD_BOWL 보유+FOOD 1개 · `WATER`=WATER_BOWL 보유(물 무료) · `CLEAN_LITTER`=LITTER_BOX 보유+LITTER 1개 · `BATHE`=GROOMING_KIT 보유(소모 없음, Q493) · `PLAY`=TOY 보유(고른 장난감을 `item_seq`에, Q494) · `SNACK`=SNACK 1개 · `MEDICINE`=MEDICINE 1개 · `GIVE_CATNIP`=CATNIP 1개(직전 `GIVE_CATNIP` 6시간 [가안] 안이면 남용 효과, Q494). 산책 중 `GIVE_WATER`는 소모 없음, `GIVE_SNACK`=SNACK 1개. `VET_VISIT`=기구·소모품 없음, **정답이 `VET_VISIT`인 PENDING 이벤트가 있을 때만** 허용(아니면 409. 성공·무변화 응답으로 바꾸지 않으며 로그 행도 남기지 않음, Q489), 비용은 시나리오 `resolve_cost`(Q486).
- **이벤트 해결 기록(Q486):** 행동이 PENDING 이벤트의 정답이면 그 CARE/WALK 로그 행에 `occurrence_seq`를 채우고 `stat_delta_jsonb`는 물품 효과 + `resolve_effects_jsonb` 합산. 만료는 `EVENT`/`EVENT_EXPIRE` 행에 `occurrence_seq`와 `neglect_effects_jsonb` 적용분을 `stat_delta_jsonb`로 기록. 별도 응답 로그(`EVENT_ANSWER`)는 없음.
- 종전 초안의 `diagnosis_seq` 컬럼은 없음(M15). `VET_VISIT`은 Q486으로 행동 코드가 됨(진단 마스터·병원 API는 여전히 없음). `last_action_dttm` 갱신은 `CARE`·`WALK`만(SYSTEM·`EVENT_EXPIRE` 제외).

#### 40. `simulation_walks` — 산책 1회

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `walk_seq` | `BIGINT` | Y | PK, IDENTITY | 산책 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions | 세션 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT IN_PROGRESS; CHECK `IN_PROGRESS/ENDED` | 산책 상태 |
| `started_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 시작 |
| `last_heartbeat_dttm` | `TIMESTAMPTZ` | N | - | 마지막 하트비트(자동 종료 판정) |
| `ended_dttm` | `TIMESTAMPTZ` | N | ENDED면 NOT NULL(CHECK 후보) | 종료 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 세션당 진행 중 산책 1건: 부분 UNIQUE `(session_seq) WHERE status='IN_PROGRESS'`.
- **개(`DOG`) 세션만 산책 가능(Q488, M12 대체).** BE가 API 18.14에서 `simulation_sessions.species_type='DOG'`를 검증하고 CAT이면 409 `SIMULATION_WALK_NOT_FOR_SPECIES`(앱은 고양이 세션의 산책 버튼을 비활성화하므로 방어용). 이 테이블에 종 컬럼·제약은 두지 않음. 종 배율은 삭제했고 산책 효과(활력·안정감 회복, 포만감·수분 감소)는 종료 시 길이와 성격 배율로만 계산.
- 산책 중 동물 행동 이벤트는 앱이 켜진 상태라 푸시 대신 하트비트 응답으로 전달(M11). [가안] 하트비트 60초 간격, 10분 끊기면 스케줄러가 자동 종료.

#### 41. `simulation_event_scenarios` — 이벤트 시나리오 마스터(초기 데이터)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `scenario_seq` | `BIGINT` | Y | PK, IDENTITY | 시나리오 키 |
| `title` | `VARCHAR(100)` | Y | - | 제목 |
| `description` | `TEXT` | Y | - | 상황 설명 |
| `trigger_context` | `VARCHAR(10)` | Y | CHECK `HOME/WALK` | 발생 장소(M11) |
| `trigger_type` | `VARCHAR(20)` | Y | CHECK `RANDOM/CONDITION` | 랜덤 추첨 / 스탯 조건 발생(M15) |
| `trigger_condition_jsonb` | `JSONB` | N | CONDITION이면 NOT NULL(CHECK) | 예 `{"stat":"health","op":"<","value":40}` |
| `cooldown_hours` | `SMALLINT` | N | CHECK `> 0` | 같은 시나리오 재발생 억제. NULL이면 코드 기본값(24시간) |
| `trigger_probability` | `NUMERIC(3,2)` | Y | NOT NULL DEFAULT 1.00; CHECK `> 0 AND <= 1`(Q481) | CONDITION 조건 충족 시 발생 확률(판정 1회당). RANDOM은 `weight` 추첨을 쓰므로 1.00 고정 |
| `species_scope` | `VARCHAR(10)` | Y | NOT NULL DEFAULT ALL; CHECK `DOG/CAT/ALL` | 적용 종 |
| `personality_scope` | `VARCHAR(20)` | N | CHECK 4종 | NULL이면 전 성격 |
| `weight` | `SMALLINT` | Y | NOT NULL DEFAULT 1; CHECK `> 0` | 추첨 가중치 |
| `resolve_action_code` | `VARCHAR(30)` | Y | NOT NULL; CHECK: HOME이면 `FEED/WATER/CLEAN_LITTER/BATHE/PLAY/SNACK/MEDICINE/VET_VISIT`, WALK면 `GIVE_WATER/GIVE_SNACK`(Q486) | **정답 행동 1개.** 시한 안에 이 행동을 하면 해결(RESOLVED) |
| `resolve_cost` | `INTEGER` | Y | NOT NULL DEFAULT 0; CHECK `>= 0`, `VET_VISIT`이 아니면 0(Q486) | 해결 비용(진료비). 해결 시 38번 `EVENT` 거래 |
| `resolve_effects_jsonb` | `JSONB` | N | - | 해결 시 추가 스탯 효과(물품 효과와 별도 합산). 예 `{"health":30,"calmness":10}` |
| `result_text` | `TEXT` | N | - | 해결 문구(진단서 등, M15) |
| `neglect_effects_jsonb` | `JSONB` | N | - | 시한 만료 시 스탯 효과(감소). 예 `{"health":-15}` |
| `neglect_text` | `TEXT` | N | - | 만료 문구. 정답 행동을 알려주는 문장 권장(예 "병원에 데려갔어야 해요") |
| `is_active` | `BOOLEAN` | Y | NOT NULL DEFAULT TRUE | 사용 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `mod_dttm` | `TIMESTAMPTZ` | N | DEFAULT 없음 | 수정 시각 |

**확정 규칙 / 참고사항**
- **이벤트 엔진 통합(M11·Q486):** 집 돌발 이벤트와 산책 중 동물 행동(짖기·싸우기, 오줌, 빨리 달리기, 냄새 맡기, 앉기, 똥)을 **시나리오→정답 행동 1개→해결/방치 효과** 하나로 처리. 선택지 테이블(종전 42번)은 없으며 정답·효과·문구·비용이 모두 이 행에 있음. 산책 행동은 `WALK`+`RANDOM`이고 정답은 `GIVE_WATER`/`GIVE_SNACK` 중 하나(산책 중 행동이 그 둘뿐이라 똥 치우기 같은 상황을 넣으려면 소모품 없는 산책 행동 코드를 먼저 추가해야 함).
- **해결·방치(Q486):** 사용자는 평소처럼 돌봄 행동(API 18.6·18.16)을 하고, 행동 처리 안에서 세션의 PENDING 이벤트(1건)의 `resolve_action_code`와 행동 코드를 `=`로 비교(WALK는 같은 `walk_seq`). 일치하면 `resolve_effects_jsonb`(성격 배율 없음, Q490. 활동 대가로 일부 스탯이 줄 수 있음, Q498) 추가 반영·`resolve_cost` 차감·RESOLVED. 불일치(오답)는 평소 행동으로만 처리하고 패널티 없음. 시한(HOME 6시간·WALK 5분 [가안]) 만료 시 `neglect_effects_jsonb` 적용 후 EXPIRED(산책 종료 시 PENDING WALK 이벤트도 동일, 세션 종료 시는 효과 없이 EXPIRED). 정답 행동은 해결 전 API에 노출하지 않고(설명이 힌트) `neglect_text`로 알려줌. 예외로 정답이 `VET_VISIT`이면 진료비만 미리 노출.
- **아픔은 별도 진단 마스터 없이 CONDITION 이벤트(M15·Q486):** 건강 하락 등 스탯 조건이 충족되면 `HOME`+`CONDITION` 시나리오가 발생하고, 정답 행동 `VET_VISIT`(비용=`resolve_cost` 진료비, 효과=`resolve_effects_jsonb`, `result_text`=진단서 문구)으로 해결. 메인 화면 동물병원 오브젝트는 정답이 `VET_VISIT`인 PENDING 이벤트가 있을 때 활성. 같은 방식으로 청결도 낮음→피부 트러블(정답 `BATHE`), 안정감 낮음→무기력(정답 `PLAY`), 포만감 낮음→영양 부족(정답 `FEED`)을 콘텐츠로 추가 가능(조건식 키는 35번 스탯 컬럼명, Q485). 종전 초안의 `simulation_diagnoses` 테이블은 만들지 않음.
- 조건 판정 시점은 스탯 지연 계산 때와 스케줄러 주기. 조건이 맞으면 쿨다운(`simulation_event_occurrences`의 같은 시나리오 마지막 `occurred_dttm`으로 판정, 별도 저장 없음)을 확인한 뒤 **`trigger_probability`로 발생 여부를 추첨**(Q481). 스탯이 낮다는 이유로 알림을 보내지 않으며, 이 CONDITION 이벤트가 사용자를 부르는 수단. 시나리오 행은 초기 데이터로 적재(Q412).

#### ~~42. `simulation_event_choices`~~ — Q486으로 삭제

> v18까지 있던 시나리오별 선택지 2개 테이블(`choice_no 1/2`, `choice_text`, `effects_jsonb`, `cost`, `result_text`)은 **v19에서 삭제**했습니다. 돌발이벤트는 선택지가 아니라 **정답 행동 1개**로 해결하며, 정답 행동·비용·해결/방치 효과·문구는 41번 시나리오 컬럼(`resolve_action_code`·`resolve_cost`·`resolve_effects_jsonb`·`result_text`·`neglect_effects_jsonb`·`neglect_text`)으로 옮겼습니다. 선택지 응답 API(종전 18.13)도 없습니다. 42번은 빈 번호로 둡니다.

#### 43. `simulation_event_occurrences` — 세션별 발생 이벤트

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `occurrence_seq` | `BIGINT` | Y | PK, IDENTITY | 발생 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions | 세션 |
| `scenario_seq` | `BIGINT` | Y | FK → simulation_event_scenarios | 시나리오 |
| `walk_seq` | `BIGINT` | N | FK → simulation_walks; WALK 시나리오면 NOT NULL(BE 검증) | 산책 중 발생 |
| `status` | `VARCHAR(20)` | Y | NOT NULL DEFAULT PENDING; CHECK `PENDING/RESOLVED/EXPIRED`(Q486) | 해결 상태 |
| `occurred_dttm` | `TIMESTAMPTZ` | Y | - | 발생 시각 |
| `expires_dttm` | `TIMESTAMPTZ` | Y | - | 해결 시한(HOME 6시간·WALK 5분 [가안]) |
| `resolved_dttm` | `TIMESTAMPTZ` | N | RESOLVED면 NOT NULL(CHECK) | 정답 행동으로 해결한 시각(Q486) |
| `resolved_action_log_seq` | `BIGINT` | N | FK → simulation_action_logs; RESOLVED면 NOT NULL(CHECK) | 해결한 행동 로그(CARE/WALK 행, Q486) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- **미해결 이벤트는 세션당 동시 1개**: 부분 UNIQUE `(session_seq) WHERE status='PENDING'`(M11).
- [가안] 집 이벤트는 하루 1~2회 랜덤, 해결 시한 HOME 6시간·WALK 5분. 산책 이벤트는 산책당 0~3회. CONDITION 이벤트 만료 규칙은 RANDOM과 동일하며 조건이 계속되면 쿨다운 뒤 재발생.
- **해결(Q486):** 별도 응답 트랜잭션 없음. 행동 트랜잭션(세션 `FOR UPDATE`)이 PENDING 행을 읽어 시나리오 `resolve_action_code`와 비교 → 일치하면 효과·비용 반영 → `action_logs(CARE|WALK)` INSERT(`occurrence_seq`) → 이 행을 `RESOLVED`·`resolved_dttm`·`resolved_action_log_seq`로 UPDATE. 39번의 `occurrence_seq`와 이 표의 `resolved_action_log_seq`는 양쪽 NULL 허용 상호 FK이며 로그 INSERT 후 occurrence UPDATE 순서로 쓴다. 오답 행동은 이 행을 건드리지 않음.
- **만료(Q441·Q486):** 스케줄러뿐 아니라 세션 상세·이벤트 목록 조회 시에도 `expires_dttm` 경과를 감지해 시나리오 `neglect_effects_jsonb`를 스탯에 적용하고 EXPIRED + `EVENT_EXPIRE` 로그(`occurrence_seq`, `stat_delta_jsonb`=방치 효과). 산책 종료(사용자 종료·하트비트 끊김 자동 종료) 시 그 산책의 PENDING WALK 이벤트도 같은 처리. **세션 종료 시 남은 PENDING은 방치 효과 없이 EXPIRED.** 방치 효과로 `health=0`이면 FAILED 종료 판정.

#### 44. `simulation_reports` — 종료 리포트(세션 1:1)

| 컬럼 | 타입 | 필수 | 키·제약 | 의미 |
|---|---|:---:|---|---|
| `report_seq` | `BIGINT` | Y | PK, IDENTITY | 리포트 키 |
| `session_seq` | `BIGINT` | Y | FK → simulation_sessions; UNIQUE | 세션당 1건 |
| `expected_cost` | `INTEGER` | Y | - | 예상 비용 스냅샷 |
| `total_spent` | `INTEGER` | Y | - | 실제 총 지출 |
| `spent_by_category_jsonb` | `JSONB` | Y | NOT NULL | 카테고리별 합계(물품 category + `EVENT`) |
| `daily_spent_jsonb` | `JSONB` | Y | NOT NULL | 일자별 지출(1~7일차) |
| `final_stats_jsonb` | `JSONB` | Y | NOT NULL | 종료 시 스탯 7종 |
| `action_count` | `INTEGER` | Y | - | 행동 수(39번 `CARE`·`WALK` 행, Q496) |
| `event_count` | `INTEGER` | Y | - | 발생 이벤트 수(해결·만료 포함) |
| `event_resolved_count` | `INTEGER` | Y | NOT NULL DEFAULT 0 | 정답 행동으로 해결한 이벤트 수(Q486, 입양 준비도 표시) |
| `walk_count` | `INTEGER` | Y | - | 산책 수 |
| `summary` | `TEXT` | N | - | 결과 문구(예상 대비 초과/절약, FAILED 사유 등) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

**확정 규칙 / 참고사항**
- 세션 종료(COMPLETED/ABANDONED/FAILED) 트랜잭션에서 1회 생성. 진행 중 중간 리포트는 조회 시 계산만 하고 저장하지 않음. 영구 보존(사용자 탈퇴 후에도 보호소 열람 유지).
- **`summary` 생성·푸시 순서(Q435·Q444·Q487, 비동기):** 종료 트랜잭션은 집계 컬럼만 채우고 `summary=NULL`로 INSERT·커밋. 커밋 후 simulation 내부 비동기 리스너가 이 리포트의 집계값과 세션 스냅샷을 요약 생성기(`port/out`. **지금은 규칙 문구 구현만, LLM은 나중에 교체**, Q497)에 보내 받은 문구로 `summary`만 `UPDATE ... WHERE summary IS NULL` → **갱신된 행이 있을 때만 `SESSION_END` 푸시 이벤트 발행**(Q487). [가안] 타임아웃 20초(LLM 구현 시)·재시도 1회, 그래도 실패하거나 빈 문구면 집계값 기반 규칙 문구 저장(따라서 푸시는 반드시 1회). 커밋 직후 재시작 등으로 리스너가 유실된 리포트는 스케줄러가 `summary IS NULL AND reg_dttm <= now() - 10분` [가안](Q497: 종료 처리 시각 기준. 종전 `ended_dttm` 기준)으로 찾아 같은 흐름을 재실행(고아 복구, `WHERE summary IS NULL` 갱신 조건이 중복 푸시를 막음). DB 트랜잭션 안에서 LLM을 호출하지 않으며 `summary`가 NULL인 동안 앱은 "요약 준비 중" 표시. 세션 상세·이력·지출의 원본은 보호소에 노출하지 않고 이 리포트로만 집약.
- **열람 범위(Q483):** 본인은 자기 리포트 전체와 세션 상세·행동 이력·지출 내역. **SHELTER는 종료 리포트를 누구 것이든 열람**(세션에 보호소 연결이 없어 소속 제한 없음): API 18.18 단건·18.20 목록(`user_seq` 필터)·12.15 입양 신청 상세에서 신청자의 종료 리포트 전체(`adoption_applications.user_seq`로 FK 없이 파생 조회, 동물·품종 무관). 진행 중 세션은 보호소에 노출하지 않음. ADMIN 전역 열람은 범위 외.

#### ~~45. `simulation_notifications`~~ — Q481로 삭제

> v17까지 있던 모의 입양 알림 테이블(유형 10종, 읽음 여부)은 **v18에서 삭제**했습니다. 모의 입양 푸시는 DB에 저장하지 않고 simulation이 `SimulationPushRequested` 이벤트(`user_seq`·`push_type EVENT|SESSION_END`·title·body)를 내면 notification이 활성 FCM 토큰으로 발송만 합니다(data `{target:SIMULATION}`, 앱을 모의 입양 화면으로 유도하는 용도까지). `NEGLECT`·`STAT_*_WARNING`은 폐지하고 스탯 저하는 CONDITION 이벤트(41번 `trigger_probability`)로 처리합니다. 알림 조회·읽음 API(종전 18.22·18.23)는 없습니다. 45번은 빈 번호로 둡니다.

#### 코드 설정 가안 (DB에 저장하지 않음; M3·M4·M11·M12)

> 아래 수치는 **코드 설정값/상수로 관리하며 DB 컬럼이 아닙니다.** 모두 [가안]이며 튜닝 대상입니다.

| 항목 | 가안 |
|---|---|
| tick · 기간 | tick 1시간, 세션 7일(실시간, M2) |
| 스탯 초기값 / tick당 변화 | `satiety` 70 / -4 · `hydration` 70 / -5 · `cleanliness` 80 / -2 · `bond` 30 / -1 · `energy` 60 / -3 · `calmness` 50 / -2 · `health` 90 / 0, 단 satiety·hydration·cleanliness 중 하나라도 20 미만이면 health -3, calmness 20 이하면 health 추가 -2 (M3·Q485. 7종 모두 시간이 지나면 떨어지는 한 방향) |
| 성격 배율(시간 감소에만, Q490) | `ACTIVE` 활력·포만감 감소 1.3 · `TIMID` 안정감 감소 1.5 · `AGGRESSIVE` 친밀도 감소 1.3 · `FRIENDLY` 안정감 감소 1.3 (M4). 실수 배율, 소수는 세션 시작 기준 누적 버림. ~~회복 배율(ACTIVE 산책 1.3·TIMID 친밀도 0.7·FRIENDLY 친밀도 1.5)~~ 삭제. AGGRESSIVE 산책 중 짖기·싸우기 가중치 2는 WALK 시나리오 추첨 가중치(252·256) |
| 돌봄(Q493·Q494) | 물 주기 효과 수분 +50(`simulation.care.water-effects`). 캣닢 남용 판정 창 6시간, 남용 효과 안정감 -20(`simulation.care.catnip-overuse-*`) |
| 펫샵 묶음 수(Q492) | `simulation.shop.uses-per-purchase`: 22번 사료 12 · 23번 5 · 24번 6 · 25번 8 · 26번 4 · 27번 6 · 28번 5 · 29~32번 10 · 33번 캣닢 4 · 34번 샴푸 10 · 35번 상비약 3, 나머지 1 |
| ~~종 배율~~ | **Q488로 삭제.** 산책은 DOG 세션 전용이라 `CAT` 배율(안정감 감소 2.0·활력 회복 0.5)이 없음 |
| 이벤트 | HOME RANDOM은 스케줄러 판정마다 확률 0.0104(하루 평균 약 1.5회)·세션 일차당 최대 2회·22~8시 제외(`simulation.event.*`, Q498). CONDITION은 스케줄러에서만 RANDOM보다 먼저 판정, 하루 횟수 제한 없음(Q499), 해결 시한 HOME 6시간·WALK 5분(만료 시 시나리오 방치 효과 적용, Q486), CONDITION 쿨다운 기본 24시간(`cooldown_hours` NULL일 때), WALK 산책당 0~3회 (M11·M15). CONDITION 발생 확률·정답 행동·해결/방치 효과·비용은 코드 설정이 아니라 시나리오 컬럼(Q481·Q486) |
| 스케줄러 | 10분 주기 작업 1개(`simulation.scheduler.interval`, 세션별 트랜잭션 Q496): 진행 중 세션 스탯 계산 → FAILED/COMPLETED 종료·리포트 → PENDING 만료(방치 효과 적용, `health=0`이면 종료) → CONDITION 판정(확률 적용) → RANDOM 추첨 → 산책 자동 종료 → `summary` NULL 리포트 복구(종료 후 10분 경과, Q487). 방치·스탯 경고 알림은 없음(Q481). simulation 서비스 `@Scheduled`, 인스턴스 1개 전제이며 다중 인스턴스 시 ShedLock 추가(S8 확정). core·notification에는 스케줄 작업 없음 |
| 산책 | 하트비트 60초, 10분 무응답 시 자동 종료 |

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
| ~~`SHELTER_ANIMAL_COMMENT`~~ | (폐기, Q477) 보호동물 댓글 기능·테이블 삭제 | — |

| 참조 테이블/컬럼 | 허용 코드 | 비고 |
|---|---|---|
| `reports.target_type` | USER, MISSING, FOUND, MISSING_COMMENT, FOUND_COMMENT | `ADOPTION`, 보호동물 게시글 `SHELTER_ANIMAL`, 보호동물 댓글(Q477), 보호소 자체 신고 없음(Q449) |
| `bookmarks.target_type` | MISSING, FOUND, SHELTER_ANIMAL | 북마크 대상 게시물만 |
| `chat_messages.shared_target_type` | MISSING, FOUND, SHELTER_ANIMAL | `SHARED_POST` 전용 |
| `notifications.target_type` | 공통 7종 중 서비스가 연결하는 대상 | NULL 허용, `ADOPTION`은 신청서 PK로 이동; COMMENT 계열은 게시물 코드 `MISSING`/`FOUND`(댓글 코드는 `comment_seq`로 분리, Q446·Q477); 유형별 이동 정책은 BE 판단 |

- **주의:** `notifications.notification_type=ADOPTION_*`(알림의 기능 분류, Q447)과 `notifications.target_type=ADOPTION`(이동 대상 종류)은 서로 다른 의미의 컬럼입니다.
- Q391에 의해 알림 대상 두 컬럼의 **NULL 허용**을 유지합니다. 두 컬럼이 한쪽만 NULL인 경우를 막기 위한 별도 DB CHECK는 확정하지 않았으며, BE에서 유효한 대상 쌍을 관리합니다.
- Q392에서 보류했던 일반 시스템 알림은 **Q398로 변경**되었습니다. `SYSTEM`은 서비스 점검·운영 공지용으로 **현행 알림 유형에 포함**하고(Q447 세분화 후 총 9종), `REPORT`와 별도 신고 접수 알림 이벤트는 추가하지 않습니다.

## 5. 상태·생명주기 및 자동 변경 정책

### 신고·구조
- 실종·발견 신고: `ACTIVE ↔ CLOSED` 재개 허용. **회원당 비삭제 ACTIVE 실종 신고는 최대 1건**; 과거 CLOSED 신고는 유지하고 새 신고를 생성할 수 있음. 실종에는 종료 사유/일시 컬럼 없음; 발견은 CLOSED에 종료 일시 필수·사유 선택.
- 구조 기록: `PROCESSING → DONE / FAILED / CANCELED`; `DONE`에서 날짜·주소 필수, `FAILED/CANCELED`에서 결과 사유 필수. 보호동물 등록 시 구조 연결은 **선택**이며 연결한다면 `DONE` 구조만 가능(`rescue_seq=NULL` 직접 등록·초기 적재 허용, Q404·405). CLOSED·삭제 여부와 무관하게 발견 신고 기준 비삭제 PROCESSING/DONE 구조는 1건(삭제된 신고로는 신규 접수 불가, CLOSED 신고로는 접수 가능, Q442).
- 발견 신고 삭제/종료는 진행 중 구조에 영향을 주지 않음. 보호동물 반환·입양·보호 종료는 실종/발견 신고를 자동 종료하지 않음.

### 보호·입양
- 보호 상태 `PROTECTING/RETURNED/ADOPTED/ENDED`. 입양 상태 `AVAILABLE/UNAVAILABLE/ADOPTED`. PROTECTING→AVAILABLE/UNAVAILABLE; RETURNED/ENDED→UNAVAILABLE; ADOPTED→ADOPTED.
- **보호 상태 전환은 어느 상태에서든 허용**(Q479, API 11.6 하나로 처리). 종료 상태(RETURNED/ADOPTED/ENDED)로 갈 때 `end_reason`·`protection_ended_dttm`을 함께 설정하고, PROTECTING으로 돌아올 때 두 컬럼을 NULL로 되돌린다. 반환 행·완료 신청서 FK·입양 사유/비고는 복귀해도 유지(이력). 자동 상태 변경(공고 만료 등)은 없음.
- 반환 완료(RETURNED)는 `animal_returns` 1행을 INSERT 또는 덮어쓰기. 입양 완료(ADOPTED)는 신청서 채택(API 12.11, `completed_application_seq` 설정)과 신청서 없는 완료(API 11.6, FK 불변·`adoption_reason`·`adoption_memo` 저장) 두 경로. 보호 종료(ENDED)는 `end_reason` NATURAL_DEATH/EUTHANIZED/OTHER.
- 입양 신청 상태는 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` **6종**. 입양 문서 승인(APPROVED)과 실제 최종 입양(ADOPTED)은 구분하며 `CLOSED`는 없음(Q371).
- 입양 완료 시 최종 채택 신청서 상태 ADOPTED, 해당 동물 `protection_status=ADOPTED`, `adoption_status=ADOPTED`, `completed_application_seq` 설정을 같은 트랜잭션에 반영.
- 타 신청자의 DRAFT/SUBMITTED/APPROVED는 실제 입양 완료 후 REJECTED. 보호 상태 RETURNED/ENDED에도 활성 신청서는 REJECTED. 시스템은 자연어 `rejection_reason`을 기록하며 `reviewed_dttm`에 자동 거절 시각, `reviewed_usr_seq=NULL` 저장; 상태 이력/거절 단계 컬럼은 없음(Q372·376·377).
- 회원 탈퇴 시 자신의 활성 DRAFT/SUBMITTED/APPROVED는 CANCELED; 취소 사유는 별도 보존하지 않음. 이미 ADOPTED 신청은 보존.
- 승인 후 최종 입양을 진행하는 과정에서 실패하면 해당 신청서를 REJECTED로 전환하고 자연어 사유를 저장(Q282·375·376). 별도 `adoption_processes` 행은 생성하지 않음. 외부 경로 입양은 완료 신청서 FK 없이 기록 가능.
- 단순한 AVAILABLE→UNAVAILABLE 전환에서는 신규 입양 신청만 제한, 기존 신청은 강제 종료하지 않음. 거절·취소 뒤 재신청 가능(현재 입양 가능한 경우).

### 계정 탈퇴와 데이터 표시
- users/user_accounts 소프트 삭제 + 활성 FCM 토큰 비활성화, 사용자 소유 게시물은 조회·AI 대상에서 가상 삭제.
- 댓글 본문·기존 답글, 채팅 메시지·이미지, 저장된 알림은 보존하고 탈퇴 사용자 표시를 대체; 새 채팅·탈퇴한 루트 댓글에 신규 답글은 제한.
- 탈퇴 신청자의 과거 입양 신청서는 소속 보호소 업무 조회에서 보존(수료증 기능은 Q460에서 제외). 실제 ADOPTED 처리 결과 및 완료 신청서 FK의 정합성 유지(별도 상태 변경 이력 테이블 없음).
- 탈퇴 시 진행 중 모의 입양 세션은 ABANDONED로 종료하고 리포트·로그 행은 보존(M6. 알림 행은 Q481로 없음).

### 모의 입양 (M2·M6·M11·M15)
- 세션 `IN_PROGRESS → COMPLETED`(예정 종료 도달) / `ABANDONED`(중도 포기·탈퇴) / `FAILED`(건강 0). 종료 상태는 되돌리지 않으며 종료 시 `simulation_reports` 1건 생성. USER당 IN_PROGRESS 1건.
- 산책 `IN_PROGRESS → ENDED`(사용자 종료 또는 하트비트 끊김 자동 종료). 세션당 IN_PROGRESS 1건. 세션 종료 시 진행 중 산책도 ENDED.
- 이벤트 발생 `PENDING → RESOLVED`(정답 행동 1개를 시한 안에 수행, 행동 트랜잭션 안에서 전이, Q486) / `EXPIRED`(해결 시한 경과·세션 종료·**산책 종료 시 PENDING WALK 이벤트**). 세션당 PENDING 1건. 만료 감지는 **조회(세션 상세·이벤트 목록)와 스케줄러 양쪽**에서 수행하며 시나리오 방치 효과 적용·EXPIRED 전이·`EVENT_EXPIRE` 로그를 함께 기록(Q441·Q486). 세션 종료로 만료되는 이벤트만 효과 없음. 오답 행동은 상태를 바꾸지 않음.
- 스탯은 시간 경과(tick)로만 자동 변화하고 행동·물품·이벤트 효과로 즉시 변화. 0~100 범위를 벗어나는 결과는 경계값으로 보정(BE).

## 6. AI 이미지 매칭·알림 정의

- 대상 실종 신고는 `ACTIVE`, 비삭제, 작성자 비탈퇴만. 비교 대상 발견 신고도 `ACTIVE`, 비삭제, 작성자 비탈퇴만(Q343).
- 보호동물 비교 대상은 `PROTECTING`이며 비삭제인 기록. 입양 상태 AVAILABLE/UNAVAILABLE 무관, RETURNED/ENDED/ADOPTED 제외(Q344).
- 실종 신고 등록 시 기존 발견 신고/보호동물과, 발견 신고/보호동물 등록 시 기존 실종 신고와 비교(Q346).
- 사진의 추가·교체·삭제가 발생하면 유효 상태의 신고·동물에 대해 다시 비교(Q347). 재개된 ACTIVE 신고도 다시 비교 대상.
- 알림 수신자는 실종 신고 작성자 **1명만**. 동일 `(missing_report_seq,target_type,target_seq)`에 `notification_type='AI_MATCH'`인 알림을 한 번만 생성, 알림 소프트 삭제해도 재발행하지 않음(Q291·345). **AI 알림 전용 DB UNIQUE는 없음**. 동일 실종 신고 행 잠금(`SELECT ... FOR UPDATE`) → BE 중복 검사 → 미존재 시 생성 → 커밋 후 FCM 발송(Q389·390). 이 트랜잭션은 **core-api**가 수행하므로 `AI_MATCH` 행은 core-api가 `notifications`에 직접 INSERT하고(쓰기 소유권 예외) notification은 `NotificationCreated` 이벤트(저장 완료, 발송만 요청)를 받아 FCM만 발송. 저장까지 맡기는 다른 유형의 `NotificationRequested`와 이름을 구분함.
- **AI 워커 연동(Q432 확정): 양방향 모두 RabbitMQ.** 요청은 core-api가 실종·발견·보호동물의 등록·재개·사진 변경 시 `{ sourceType: MISSING|FOUND|SHELTER_ANIMAL, sourceSeq, imageKeys[], speciesCode, occurredAt }`를 발행하고, 워커는 DB를 읽지 않고 이벤트 페이로드와 S3 키만으로 동작. 결과는 워커가 쌍마다 `AiMatchFound { missingReportSeq, targetType: FOUND|SHELTER_ANIMAL, targetSeq, score, modelVersion }`를 발행하며 `score`·`modelVersion`은 저장하지 않는 로그용 선택 필드. 유사도 임계값 판단은 워커, 수신 시점의 ACTIVE·PROTECTING 자격 재검사와 중복 처리는 core-api(위 순서). 워커의 임베딩 저장 위치는 기존대로 POC 범위.
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
-- Q411: role CHECK는 USER/SHELTER/ADMIN 유지. ADMIN은 예약값이며 이번 구현 범위 외.
-- Q408: 회원 프로필 이미지는 images 직접 FK (종전 profile_image_key TEXT 삭제)
ALTER TABLE users ADD CONSTRAINT fk_users_profile_image
  FOREIGN KEY (profile_image_seq) REFERENCES images(image_seq);
-- Q407: 이미지 업로더 (시드·공공데이터 적재분 NULL). 연결 시 본인 이미지만 허용은 BE 검증.
ALTER TABLE images ADD CONSTRAINT fk_images_uploader
  FOREIGN KEY (reg_usr_seq) REFERENCES users(user_seq);
-- Q409 B: refresh token은 Redis TTL 저장. refresh_tokens 테이블 없음.

-- Q350: 외부 코드 있는 보호소 중복 등록 방지 (NULL 여러 건 허용)
ALTER TABLE shelters ADD CONSTRAINT uq_shelters_external_code UNIQUE (external_shelter_code);
-- Q338: 행정구역별 보호소 매핑의 district_code는 PK

-- ===== Q406 A · Q406-1 A (v10): 행정구역 마스터와 시군구 코드 =====
-- 법정동 코드 앞 5자리(시군구). 시드 적재, API 관리 없음.
CREATE TABLE districts (
  district_code VARCHAR(5)  PRIMARY KEY,
  sido_name     VARCHAR(50) NOT NULL,
  sigungu_name  VARCHAR(50) NOT NULL,
  reg_dttm      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE shelter_district_assignments ALTER COLUMN district_code TYPE VARCHAR(5);
ALTER TABLE shelter_district_assignments ADD CONSTRAINT fk_assignment_district
  FOREIGN KEY (district_code) REFERENCES districts(district_code);
ALTER TABLE shelters        ADD COLUMN district_code VARCHAR(5) NULL REFERENCES districts(district_code);
ALTER TABLE missing_reports ADD COLUMN district_code VARCHAR(5) NULL REFERENCES districts(district_code);
ALTER TABLE found_reports   ADD COLUMN district_code VARCHAR(5) NULL REFERENCES districts(district_code);
-- 목록 필터용 인덱스(코드 NULL 행은 필터에서 제외)
CREATE INDEX ix_missing_district ON missing_reports(district_code) WHERE district_code IS NOT NULL;
CREATE INDEX ix_found_district   ON found_reports(district_code)   WHERE district_code IS NOT NULL;
-- Q406-2 A: 역지오코딩 실패·마스터에 없는 코드는 NULL 저장, 등록 거부 없음(BE).
-- Q463: 7.1/8.1 목록 필터용 인덱스 후보 [가안] (occurred_dttm 날짜 범위 + reg_dttm 정렬)
CREATE INDEX ix_missing_occurred ON missing_reports(occurred_dttm) WHERE is_deleted = false;
CREATE INDEX ix_found_occurred   ON found_reports(occurred_dttm)   WHERE is_deleted = false;
-- Q464: 8.8 구조 상태 파생(신고별 최신 비삭제 구조 기록) [가안]
CREATE INDEX ix_rescue_found_latest ON rescue_records(found_report_seq, reg_dttm DESC)
  WHERE found_report_seq IS NOT NULL AND is_deleted = false;

-- S7: 키워드 검색은 ILIKE. pg_trgm GIN 인덱스는 [가안]. Q463부터 검색 대상은 description 한 컬럼뿐.
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX ix_missing_description_trgm ON missing_reports USING gin (description gin_trgm_ops);
-- CREATE INDEX ix_found_description_trgm   ON found_reports   USING gin (description gin_trgm_ops);

-- Q334: 품종-색상 조합으로 네 도메인의 종/품종/색상 정합성 보장
ALTER TABLE animal_combinations ADD CONSTRAINT uq_animal_combination_breed_color
  UNIQUE (breed_seq,color_seq);
-- pets/missing_reports/found_reports/shelter_animals.combination_seq는 각각 FK 및 NOT NULL
-- M8: 종 코드 확정 (DOG/CAT만 모의 입양 시작 가능)
ALTER TABLE animal_species ALTER COLUMN species_code SET NOT NULL;
ALTER TABLE animal_species ADD CONSTRAINT uq_animal_species_code UNIQUE (species_code);
ALTER TABLE animal_species ADD CONSTRAINT ck_animal_species_code
  CHECK (species_code IN ('DOG','CAT','ETC'));
-- Q482: 품종 성격 NOT NULL(시드 전량 배정) + 품종 대표 이미지 1장(시드, 참조 정리 대상 아님)
ALTER TABLE animal_breeds ALTER COLUMN personality SET NOT NULL;
ALTER TABLE animal_breeds ADD COLUMN representative_image_seq BIGINT NULL;
ALTER TABLE animal_breeds ADD CONSTRAINT fk_animal_breed_image
  FOREIGN KEY (representative_image_seq) REFERENCES images(image_seq);
-- M7: 품종 단위 성격 4종 (NULL 허용; 세션 시작 시 NULL이면 랜덤 배정 가안)
ALTER TABLE animal_breeds ADD CONSTRAINT ck_animal_breed_personality
  CHECK (personality IS NULL OR personality IN ('ACTIVE','TIMID','AGGRESSIVE','FRIENDLY'));

-- Q399 선행 확인: 회원당 비삭제 ACTIVE 실종 신고 최대 1건 (CLOSED 후 새 신고 가능)
CREATE UNIQUE INDEX uq_missing_user_active ON missing_reports(user_seq)
  WHERE status='ACTIVE' AND is_deleted=FALSE;
-- Q466: missing_reports.pet_seq 삭제(FK 없음). Q465: missing_reports.name 삭제. Q462: adoption_applications.certificate_storage_key 삭제.
-- (v12 이전 DB가 있다면) ALTER TABLE missing_reports DROP COLUMN pet_seq, DROP COLUMN name;
--                        ALTER TABLE adoption_applications DROP COLUMN certificate_storage_key;
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
-- Q461: 사유 코드·장소·구조 일시는 접수 시부터 필수(NOT NULL 컬럼). ck_rescue_done_fields 제거
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_reason_code
  CHECK (rescue_reason_code IN ('WANDERING','INJURY_ILLNESS','DANGEROUS_PLACE','OTHER'));
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_address_not_blank
  CHECK (length(btrim(rescue_address))>0);
-- Q360: 실패·취소 시 사유 필수
ALTER TABLE rescue_records ADD CONSTRAINT ck_rescue_result_reason
  CHECK (status NOT IN ('FAILED','CANCELED') OR
        (result_reason IS NOT NULL AND length(btrim(result_reason))>0));

-- Q469: 몸무게는 네 동물 테이블 모두 VARCHAR(30) 자유 텍스트. Q458의 ck_shelter_weight_positive는 제거
-- (v13 이전 DB가 있다면) ALTER TABLE shelter_animals DROP CONSTRAINT ck_shelter_weight_positive;
--                        ALTER TABLE shelter_animals ALTER COLUMN weight TYPE VARCHAR(30) USING (weight::text || 'kg');
--                        ALTER TABLE missing_reports ADD COLUMN weight VARCHAR(30) NULL;
--                        ALTER TABLE found_reports   ADD COLUMN weight VARCHAR(30) NULL;
-- Q330/Q388/Q479: 공고 날짜 NOT NULL, 역전 방지(등록 시 입력 가능, 생략 시 시작=등록일·종료=시작+10일;
--   공공 적재 날짜는 담당자 입력; 두 날짜 모두 API 11.6으로 수정)
ALTER TABLE shelter_animals ADD CONSTRAINT ck_shelter_notice_range
  CHECK (notice_start_date <= notice_end_date);
-- Q479: API 11.6 ADOPTED 처리 사유·비고(신청서 없는 입양 완료). 재지정 시 덮어쓰기
-- (v16 이전 DB가 있다면) ALTER TABLE shelter_animals ADD COLUMN adoption_reason VARCHAR(200) NULL;
--                        ALTER TABLE shelter_animals ADD COLUMN adoption_memo TEXT NULL;
-- Q479: 보호 상태는 어느 값에서든 전환 가능. DB는 아래 두 조합 CHECK만 강제하고
--   전환 규칙(종료 컬럼 NULL 복귀, 반환 행 덮어쓰기, 신청 자동 거절)은 BE 트랜잭션.
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
-- Q190: 완료된 보호동물 반환은 동물별 최대 1건. Q479: 재지정 시 같은 행 덮어쓰기(mod_usr_seq·mod_dttm)
ALTER TABLE animal_returns ADD CONSTRAINT uq_animal_returns_animal UNIQUE(animal_seq);
-- (v16 이전 DB가 있다면) ALTER TABLE animal_returns ADD COLUMN mod_usr_seq INTEGER NULL REFERENCES users(user_seq);
--                        ALTER TABLE animal_returns ADD COLUMN mod_dttm TIMESTAMPTZ NULL;
-- Q238: 해당 동물의 입양 신청서만 완료 신청서로 선택
ALTER TABLE adoption_applications ADD CONSTRAINT uq_app_application_animal UNIQUE(application_seq,animal_seq);
ALTER TABLE shelter_animals ADD CONSTRAINT fk_completed_application_for_animal
  FOREIGN KEY (completed_application_seq,animal_seq)
  REFERENCES adoption_applications(application_seq,animal_seq);
CREATE UNIQUE INDEX uq_shelter_completed_application ON shelter_animals(completed_application_seq)
  WHERE completed_application_seq IS NOT NULL;

-- Q402: 입양 양식 버전 테이블 제거. 현재 문항(20개)은 질문 테이블만 관리.
-- Q458: DATE 유형 추가. question_seq는 시드에서 OVERRIDING SYSTEM VALUE로 1~20 고정
ALTER TABLE adoption_form_questions ADD CONSTRAINT ck_adoption_question_type
  CHECK (question_type IN ('TEXT','SINGLE_CHOICE','MULTI_CHOICE','DATE'));
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
-- Q449: reason_detail 삭제(ck_reports_reason 제거). reason_code는 대상별 허용 조합만
ALTER TABLE reports ADD CONSTRAINT ck_report_reason_by_target
  CHECK ((target_type IN ('MISSING','FOUND')
          AND reason_code IN ('FALSE_INFO','SPAM_AD','OFFENSIVE','OTHER'))
      OR (target_type IN ('USER','MISSING_COMMENT','FOUND_COMMENT')
          AND reason_code IN ('HARASSMENT','SPAM_AD','OBSCENE','OTHER')));
-- Q477: SHELTER_ANIMAL_COMMENT 제거(보호동물 댓글 테이블 삭제)

-- Q380~382/Q387/Q389~399 → Q447: 알림 유형 9종(title은 유형별 서버 고정 문구, 저장 없음)
ALTER TABLE notifications ADD CONSTRAINT ck_notification_type
  CHECK (notification_type IN ('COMMENT','COMMENT_REPLY','AI_MATCH',
         'ADOPTION_SUBMITTED','ADOPTION_APPROVED','ADOPTION_REJECTED','ADOPTION_COMPLETED',
         'SHELTER','SYSTEM'));
-- Q447: actor_user_seq 삭제(행위자 FK 없음)
-- Q446: COMMENT 계열만 댓글 참조 컬럼 사용. target은 게시물, 답글이면 루트 댓글 포함
ALTER TABLE notifications ADD CONSTRAINT ck_notification_comment_ref
  CHECK ((notification_type IN ('COMMENT','COMMENT_REPLY')
          AND comment_seq IS NOT NULL
          AND target_type IN ('MISSING','FOUND') AND target_seq IS NOT NULL  -- Q477: SHELTER_ANIMAL 제외
          AND ((notification_type='COMMENT' AND parent_comment_seq IS NULL)
            OR (notification_type='COMMENT_REPLY' AND parent_comment_seq IS NOT NULL)))
      OR (notification_type NOT IN ('COMMENT','COMMENT_REPLY')
          AND comment_seq IS NULL AND parent_comment_seq IS NULL));
-- Q448: CommentDeleted 처리 시 (target_type, comment_seq)로 대상 알림 조회
CREATE INDEX ix_notification_comment ON notifications(target_type, comment_seq)
  WHERE comment_seq IS NOT NULL;
-- notifications.message TEXT NOT NULL은 테이블 정의에서 선언. DB의 문자열을 FCM body로 재사용.
-- 대상 target_type/target_seq는 각각 NULL 허용(Q391); 연결 대상 존재는 BE 검증.
-- AI 중복 검사: notification_type='AI_MATCH' AND
--   (missing_report_seq,target_type,target_seq) 동일 쌍을 is_deleted와 무관하게 조회(Q399).
-- AI_MATCH에 한해 missing_report_seq/target_type/target_seq 모두 필수,
-- target_type IN ('FOUND','SHELTER_ANIMAL'); 다른 알림은 missing_report_seq NULL(BE 검증).
-- AI 쌍 중복 방지 UNIQUE 인덱스를 만들지 않음(Q389).
-- Q390: missing_reports 해당 행 FOR UPDATE 잠금 후 BE 조회/생성을 단일 트랜잭션으로 수행.

-- Q387: 다형성 대상별 허용 코드 (컬럼의 NOT NULL/NULL은 테이블 표 참조)
-- Q449: 보호동물 게시글(SHELTER_ANIMAL) 신고 제외. Q477: 보호동물 댓글(SHELTER_ANIMAL_COMMENT)도 제외
ALTER TABLE reports ADD CONSTRAINT ck_report_target_type
  CHECK (target_type IN ('USER','MISSING','FOUND',
                        'MISSING_COMMENT','FOUND_COMMENT'));
ALTER TABLE bookmarks ADD CONSTRAINT ck_bookmark_target_type
  CHECK (target_type IN ('MISSING','FOUND','SHELTER_ANIMAL'));
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_shared_type
  CHECK (shared_target_type IS NULL OR shared_target_type IN ('MISSING','FOUND','SHELTER_ANIMAL'));
ALTER TABLE notifications ADD CONSTRAINT ck_notification_target_type
  CHECK (target_type IS NULL OR target_type IN ('USER','MISSING','FOUND',
               'SHELTER_ANIMAL','ADOPTION','MISSING_COMMENT','FOUND_COMMENT'));  -- Q477: SHELTER_ANIMAL_COMMENT 제거

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

-- ===== 모의 입양 (M1~M17, Q481~Q484) — simulation 스키마 (Q420 D) =====
-- 아래 simulation_* 테이블은 모두 simulation 스키마에 있으며, core 스키마를 가리키는
-- user_seq / breed_seq 컬럼에는 FK를 두지 않는다(논리 참조, BE 검증).
-- 9개 테이블 사이의 FK(session_seq, item_seq, scenario_seq, walk_seq, occurrence_seq, resolved_action_log_seq)는 유지.
-- Q482·Q484: 보호동물 연결·표시 스냅샷 폐기, 품종 논리 참조 + 가상 이름
-- (v17 이전 DB가 있다면) ALTER TABLE simulation_sessions
--   DROP COLUMN animal_seq, DROP COLUMN shelter_seq, DROP COLUMN animal_breed_name,
--   DROP COLUMN animal_color_name, DROP COLUMN animal_image_key;
ALTER TABLE simulation_sessions ADD COLUMN breed_seq INTEGER NOT NULL;      -- 논리 참조 → core.animal_breeds
ALTER TABLE simulation_sessions ADD COLUMN animal_name VARCHAR(6) NOT NULL; -- Q484
-- M6: USER당 진행 중 세션 1건, 상태·시각 정합성, 스탯 0~100
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_status
  CHECK (status IN ('IN_PROGRESS','COMPLETED','ABANDONED','FAILED'));
CREATE UNIQUE INDEX uq_sim_session_user_active ON simulation_sessions(user_seq)
  WHERE status='IN_PROGRESS';
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_species
  CHECK (species_type IN ('DOG','CAT'));
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_personality
  CHECK (personality IN ('ACTIVE','TIMID','AGGRESSIVE','FRIENDLY'));
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_period
  CHECK (expected_cost >= 0 AND ends_dttm > started_dttm);
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_ended
  CHECK ((status='IN_PROGRESS' AND ended_dttm IS NULL)
      OR (status<>'IN_PROGRESS' AND ended_dttm IS NOT NULL));
-- Q485: 스탯 7종 개명(hunger→satiety, friendship→bond, stress→calmness(방향 반전), activity→energy), 모두 높을수록 좋음
-- (v18 DB가 있다면) ALTER TABLE simulation_sessions RENAME COLUMN hunger TO satiety; ... RENAME COLUMN stress TO calmness;
--                   UPDATE simulation_sessions SET calmness = 100 - calmness;  -- stress 값을 안정감으로 반전
ALTER TABLE simulation_sessions ADD CONSTRAINT ck_sim_session_stats
  CHECK (satiety BETWEEN 0 AND 100 AND hydration BETWEEN 0 AND 100
     AND cleanliness BETWEEN 0 AND 100 AND bond BETWEEN 0 AND 100
     AND calmness BETWEEN 0 AND 100 AND health BETWEEN 0 AND 100
     AND energy BETWEEN 0 AND 100);
-- balance는 음수 허용(M5)이므로 CHECK 없음.

-- M9: 상점 물품 분류 9종, 기구 4종은 비소모
ALTER TABLE simulation_shop_items ADD CONSTRAINT ck_sim_item_category
  CHECK (category IN ('FOOD_BOWL','WATER_BOWL','LITTER_BOX','TOY','FURNITURE','LEASH','GROOMING_KIT',
                      'FOOD','SNACK','LITTER','SHAMPOO','MEDICINE','POOP_BAG','CATNIP'));  -- Q491 14종
ALTER TABLE simulation_shop_items ADD CONSTRAINT ck_sim_item_consumable
  CHECK ((category IN ('FOOD_BOWL','WATER_BOWL','LITTER_BOX','TOY',
                       'FURNITURE','LEASH','GROOMING_KIT')) = (is_consumable = FALSE));    -- 기구 7종
ALTER TABLE simulation_shop_items ADD CONSTRAINT ck_sim_item_scope
  CHECK (species_scope IN ('DOG','CAT','ALL') AND price >= 0);
-- Q420 D: 물품 사진은 images FK 대신 image_key VARCHAR(512) 문자열(시드). fk_sim_item_image 없음.

-- 인벤토리: 세션·물품 1행, 수량 0 이상
ALTER TABLE simulation_inventory_items ADD CONSTRAINT uq_sim_inventory_session_item
  UNIQUE (session_seq,item_seq);
ALTER TABLE simulation_inventory_items ADD CONSTRAINT ck_sim_inventory_qty
  CHECK (quantity >= 0);
-- 기구 수량 1 고정·재구매 거부는 BE 검증.

-- M15: 거래 유형 2종(VET 없음), 유형별 참조 필수
ALTER TABLE simulation_transactions ADD CONSTRAINT ck_sim_transaction_type
  CHECK ((transaction_type='PURCHASE' AND item_seq IS NOT NULL)
      OR (transaction_type='EVENT' AND occurrence_seq IS NOT NULL));
ALTER TABLE simulation_transactions ADD CONSTRAINT ck_sim_transaction_amount
  CHECK (quantity >= 1 AND amount >= 0);

-- 행동 로그 분류 4종(action_code 값은 BE 검증)
ALTER TABLE simulation_action_logs ADD CONSTRAINT ck_sim_action_type
  CHECK (action_type IN ('CARE','WALK','EVENT','SYSTEM'));

-- 산책: 세션당 진행 중 1건
ALTER TABLE simulation_walks ADD CONSTRAINT ck_sim_walk_status
  CHECK ((status='IN_PROGRESS' AND ended_dttm IS NULL)
      OR (status='ENDED' AND ended_dttm IS NOT NULL));
CREATE UNIQUE INDEX uq_sim_walk_session_active ON simulation_walks(session_seq)
  WHERE status='IN_PROGRESS';

-- M11·M15: 시나리오 발생 장소·유형, CONDITION은 조건 JSON 필수
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_trigger
  CHECK (trigger_context IN ('HOME','WALK') AND trigger_type IN ('RANDOM','CONDITION')
     AND (trigger_type <> 'CONDITION' OR trigger_condition_jsonb IS NOT NULL));
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_scope
  CHECK (species_scope IN ('DOG','CAT','ALL')
     AND (personality_scope IS NULL OR personality_scope IN ('ACTIVE','TIMID','AGGRESSIVE','FRIENDLY'))
     AND weight > 0 AND (cooldown_hours IS NULL OR cooldown_hours > 0));
-- Q481: CONDITION 발생 확률(판정 1회당), RANDOM은 1.00
ALTER TABLE simulation_event_scenarios ADD COLUMN trigger_probability NUMERIC(3,2) NOT NULL DEFAULT 1.00;
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_probability
  CHECK (trigger_probability > 0 AND trigger_probability <= 1);
-- Q486: 정답 행동 1개(장소별 허용 코드) + 해결 비용(병원만) + 해결/방치 효과·문구
ALTER TABLE simulation_event_scenarios ADD COLUMN resolve_action_code VARCHAR(30) NOT NULL;
ALTER TABLE simulation_event_scenarios ADD COLUMN resolve_cost INTEGER NOT NULL DEFAULT 0;
ALTER TABLE simulation_event_scenarios ADD COLUMN resolve_effects_jsonb JSONB;
ALTER TABLE simulation_event_scenarios ADD COLUMN result_text TEXT;
ALTER TABLE simulation_event_scenarios ADD COLUMN neglect_effects_jsonb JSONB;
ALTER TABLE simulation_event_scenarios ADD COLUMN neglect_text TEXT;
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_resolve
  CHECK ((trigger_context='HOME' AND resolve_action_code IN
            ('FEED','WATER','CLEAN_LITTER','BATHE','PLAY','SNACK','MEDICINE','VET_VISIT'))
      OR (trigger_context='WALK' AND resolve_action_code IN ('GIVE_WATER','GIVE_SNACK')));
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_cost
  CHECK (resolve_cost >= 0 AND (resolve_action_code='VET_VISIT' OR resolve_cost=0));
-- Q486: 선택지 테이블 삭제. uq_sim_choice_scenario_no·ck_sim_choice 제거
-- (v18 DB가 있다면) DROP TABLE simulation_event_choices;

-- M11·Q486: 세션당 미해결 이벤트 1건, 해결 완료 정합성, 해결한 행동 로그 FK
ALTER TABLE simulation_event_occurrences ADD CONSTRAINT ck_sim_occurrence_status
  CHECK ((status='RESOLVED' AND resolved_dttm IS NOT NULL AND resolved_action_log_seq IS NOT NULL)
      OR (status IN ('PENDING','EXPIRED') AND resolved_dttm IS NULL AND resolved_action_log_seq IS NULL));
ALTER TABLE simulation_event_occurrences ADD CONSTRAINT fk_sim_occurrence_resolved_log
  FOREIGN KEY (resolved_action_log_seq) REFERENCES simulation_action_logs(action_log_seq);
-- simulation_action_logs.occurrence_seq ↔ simulation_event_occurrences.resolved_action_log_seq는
-- 양쪽 NULL 허용 상호 FK. 두 테이블 생성 후 ALTER로 FK 추가, 쓰기는 로그 INSERT → occurrence UPDATE 순서.
CREATE UNIQUE INDEX uq_sim_occurrence_session_pending ON simulation_event_occurrences(session_seq)
  WHERE status='PENDING';
-- WALK 시나리오의 walk_seq NOT NULL은 BE 검증(시나리오 JOIN 필요).
-- Q486: 리포트 해결 이벤트 수
ALTER TABLE simulation_reports ADD COLUMN event_resolved_count INTEGER NOT NULL DEFAULT 0;

-- M14·M17: 리포트 세션당 1건
ALTER TABLE simulation_reports ADD CONSTRAINT uq_sim_report_session UNIQUE (session_seq);

-- Q481: simulation_notifications 테이블 삭제. ck_sim_notification_type 제거
-- (v17 이전 DB가 있다면) DROP TABLE simulation_notifications;
```

### v19 모의 입양 핵심 조회 예시 (개념 SQL, 교차 스키마 읽기)

```sql
-- M7·M8·Q482: 세션 시작 시 스냅샷 원천 조회 (simulation 서비스가 core 스키마를 읽기 전용 JOIN)
SELECT sp.species_code, br.personality
FROM core.animal_breeds br
JOIN core.animal_species sp ON sp.species_seq = br.species_seq
WHERE br.breed_seq = :breed_seq;
-- 없으면 SIMULATION_BREED_NOT_FOUND(404), species_code='ETC'면 SIMULATION_SPECIES_NOT_SUPPORTED(409).
-- personality는 NOT NULL(시드 전량 배정). 결과를 simulation.simulation_sessions의 species_type/personality에 복사하고
-- breed_seq·animal_name(요청값)을 함께 저장. 실제 보호동물·보호소는 읽지 않음.

-- Q482: 세션·리포트 응답의 품종 표시 블록 (조회 시 파생, 세션에 이름·이미지를 저장하지 않음)
SELECT s.session_seq, s.animal_name, s.species_type, s.personality,
       br.breed_seq, br.breed_name, i.image_key AS breed_image_key
FROM simulation.simulation_sessions s
JOIN core.animal_breeds br ON br.breed_seq = s.breed_seq
LEFT JOIN core.images i ON i.image_seq = br.representative_image_seq
WHERE s.session_seq = :session_seq;

-- Q483: 입양 신청 상세에서 신청자의 종료 리포트 전체 (core-api가 simulation 스키마를 읽기 전용 JOIN, 동물·품종 무관)
SELECT r.*
FROM simulation.simulation_reports r
JOIN simulation.simulation_sessions s ON s.session_seq = r.session_seq
JOIN core.adoption_applications a ON a.user_seq = s.user_seq
WHERE a.application_seq = :application_seq
ORDER BY r.reg_dttm DESC;

-- Q483: 보호소용 종료 리포트 목록 (SHELTER 전체, user_seq 필터 선택)
SELECT r.*, s.user_seq
FROM simulation.simulation_reports r
JOIN simulation.simulation_sessions s ON s.session_seq = r.session_seq
WHERE (:user_seq IS NULL OR s.user_seq = :user_seq)
ORDER BY r.reg_dttm DESC;

-- Q481: CONDITION 시나리오 쿨다운 판정 (별도 저장 없이 마지막 발생으로 판단)
SELECT 1 FROM simulation.simulation_event_occurrences o
WHERE o.session_seq = :session_seq AND o.scenario_seq = :scenario_seq
  AND o.occurred_dttm > now() - make_interval(hours => COALESCE(:cooldown_hours, 24))
LIMIT 1;
-- 행이 없고 조건이 충족되면 random() < trigger_probability 일 때만 occurrence INSERT.

-- Q486: 행동 처리 중 PENDING 이벤트 해결 판정 (세션 FOR UPDATE 이후, 세션당 PENDING 1건)
SELECT o.occurrence_seq, o.walk_seq, sc.resolve_action_code, sc.resolve_cost,
       sc.resolve_effects_jsonb, sc.result_text
FROM simulation.simulation_event_occurrences o
JOIN simulation.simulation_event_scenarios sc ON sc.scenario_seq = o.scenario_seq
WHERE o.session_seq = :session_seq AND o.status = 'PENDING' AND o.expires_dttm > now();
-- resolve_action_code = :action_code (WALK면 o.walk_seq = :walk_seq 추가)일 때만
-- 효과·비용 반영 → action_logs INSERT(occurrence_seq) → o.status='RESOLVED', resolved_dttm, resolved_action_log_seq UPDATE.
-- 불일치면 이벤트는 그대로 두고 평소 행동만 처리.

-- Q406: 발견 신고 담당 보호소 배정 (매핑 우선, NULL·미매핑이면 하버사인 최근접)
SELECT a.shelter_seq FROM shelter_district_assignments a
WHERE a.district_code = :district_code;   -- :district_code가 NULL이면 건너뜀
-- 없으면: SELECT shelter_seq FROM shelters WHERE is_deleted=FALSE
--         ORDER BY haversine(latitude, longitude, :lat, :lng) LIMIT 1;
```

### v8 핵심 조회 예시 (개념 SQL)

```sql
-- Q404~405: 보호동물과 구조 기록 결합 (직접 등록은 구조 정보 NULL)
SELECT sa.animal_seq, sa.rescue_seq,
       rr.rescued_dttm, rr.rescue_reason_code, rr.rescue_address, rr.rescue_address_detail, rr.rescue_memo
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
-- Q454: "내 채팅방" 목록·전체 미읽음 수 조회용(위 UNIQUE는 방이 선두 컬럼이라 참여자 기준 조회에 못 씀)
CREATE INDEX ix_chat_participant_user ON chat_participants(participant_user_seq)
  WHERE participant_type='USER';
CREATE INDEX ix_chat_participant_shelter ON chat_participants(participant_shelter_seq)
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
-- Q477: shelter_animal_comments 테이블 삭제. uq_shelter_comment_parent_post·fk_shelter_comment_parent_post 제거
-- (v16 이전 DB가 있다면) DROP TABLE shelter_animal_comments;

-- 북마크·보호소 회원 계정 관계
ALTER TABLE bookmarks ADD CONSTRAINT uq_bookmark_user_target UNIQUE(user_seq,target_type,target_seq);
ALTER TABLE shelter_members ADD CONSTRAINT uq_shelter_member_shelter UNIQUE(shelter_seq);
ALTER TABLE shelter_members ADD CONSTRAINT uq_shelter_member_user UNIQUE(user_seq);
```

## 8. 서비스 트랜잭션 및 BE 검증 체크리스트

| 사건 | 단일 트랜잭션·BE 검증 항목 |
|---|---|
| 회원가입·탈퇴 | 카카오는 앱 SDK access token을 서버가 카카오 사용자 조회 API로 검증, 미가입자는 Redis `auth:register:{token}`(TTL 10분) 컨텍스트로 2.5에서 최종 가입(Q413 A·Q414 A); USER 필수 개인정보 CHECK + 활성 닉네임·전화 UNIQUE; 카카오 프로필 사진·이메일 미수집, 가입 시 `profile_image_seq=NULL`(Q408-1); 탈퇴 시 core-api 트랜잭션에서 users/user_accounts 소프트 삭제 + 본인 활성 신청 CANCELED + Redis refresh 키 삭제, 커밋 후 `UserDeleted` 이벤트(RabbitMQ) → notification이 토큰 비활성, simulation이 진행 중 세션 ABANDONED·리포트 생성(Q420); 옛 콘텐츠 일반 API 비노출 |
| 로그인·토큰 | RS256, core-api만 발급(개인키), 나머지 서비스는 공개키 검증. access 클레임 `userSeq`·`role`·`shelterSeq`·`typ=access`; refresh는 같은 키로 서명한 JWT(`userSeq`·`jti`·`typ=refresh`, Q433)이며 검증기는 `typ != access`를 API 인증에서 거부; access 1시간/refresh 14일 [가안]; refresh는 Redis `auth:refresh:{userSeq}:{jti}` TTL 저장, 갱신 시 기존 키 삭제 후 재발급(rotation), 삭제된 jti 재사용 시 사용자 키 전부 삭제, 로그아웃 시 키 삭제 + 요청의 `fcmToken`이 있으면 해당 기기 토큰 비활성(Q409 B·Q419 A). access 블랙리스트 없음, 대신 모든 쓰기 요청에서 `users.is_deleted` 재확인(Q433). LOCAL 로그인은 Redis `auth:login-fail:{loginId}` 카운터로 5회 실패 시 15분 차단 [가안], 429 응답, DB 상태 없음(Q445) |
| 이미지 연결 소유권 | 게시물·반려동물·프로필·댓글·채팅에 `image_seq`를 연결할 때 `images.reg_usr_seq = 요청자`만 허용, 아니면 403(Q407). 시드 이미지(NULL, 품종 대표 이미지 포함)는 회원 연결 불가. 모의 입양은 회원 이미지를 연결하지 않음(Q482) |
| 실종 등록·재개 | 반려동물 연결·스냅샷 없음(Q466), 동물 이름 없음(Q465); 주소·좌표는 앱이 API 5.6 주소 검색(네이버 Geocoding 프록시)·지도에서 얻어 보낸 값 그대로(Q467); **회원당** 비삭제 ACTIVE 1건 부분 UNIQUE(신규 등록·재개 모두 적용), AI 비교 작업 요청 |
| 신고 검색(7.1·8.1·8.8) | 필터는 Q463 8개 조건, 포함 매칭(`gender` 필터에 `UNKNOWN` 포함, `is_neutered` 필터에 NULL 포함), 키워드는 `description ILIKE`만, 정렬 `reg_dttm`만; 7.3/8.3은 본인 전량 배열; 8.8 구조 상태는 `rescue_records` 최신 비삭제 행 파생(NONE/PROCESSING/DONE/ABORTED), 최근 N일은 `occurred_dttm` 기준, size 기본 8(Q464); 8.8 항목의 신고자는 `users.name` 실명 + `reporter_phone`(Q470) |
| 구조 목록·상세(10.1·10.2) | 토큰 보호소의 비삭제 구조만; 최근 7/30/90일은 `rescued_dttm` 기준, 축종·접수 경로(`found_report_seq` 유무)·`status` 필터, size 기본 8; **상태별 건수는 `status` 제외 필터로 `GROUP BY status` 집계해 목록과 한 응답**(10.7 삭제); 항목에 `rescue_memo`·동물 `is_neutered`, 동물 출처는 REPORT=`found_reports`/DIRECT=연결 `shelter_animals` 최초 1건(Q471); 상세 연결 신고 요약에 `is_neutered`·신고자 실명(Q472) |
| 보호동물 목록·상세(11.1·11.2·11.3·11.10) | 11.1은 비삭제 전체에 합성 파생 상태 5값(생략 시 전체)·`reg_dttm` 날짜 범위·보호소 소재지·종·`gender`·`is_neutered` 포함 매칭·`description`/`health_check` ILIKE·`reg_dttm` 정렬, 항목은 신고 목록 구성 + 두 상태·공고기간·보호소 블록(Q473); 11.2(USER 전용)는 목록 + 상세 전용 컬럼·구조 LEFT JOIN(Q474, 모의 입양 파생은 Q482로 삭제), 보호소 블록은 `shelters` JOIN의 PK·이름·`phone`·`address`(Q476), 댓글 수 없음(Q477); 11.10(SHELTER 전용)은 토큰 보호소 소속 1건에 `animal_returns` LEFT JOIN·완료 신청서 `applicant_name`·`adoption_reason`/`adoption_memo`·신청 상태별 COUNT(DRAFT·CANCELED 제외)를 동봉(Q478); 11.3은 토큰 보호소 범위, 탭은 `protection_status`+`adoption_status` 합성 5값, 최근 7/30/90일(`reg_dttm`)·종·`gender`·`is_neutered`, size 8, **건수는 탭 제외 필터로 `GROUP BY protection_status, adoption_status` 집계해 목록과 한 응답**(Q475) |
| 주소 검색 프록시 | API 5.6: core-api `catalog` port/out 어댑터가 네이버 Geocoding 호출 → 후보(도로명·지번·위경도) 배열. DB 저장·캐시 없음, 키는 서버 설정(Q467) |
| 발견 등록 | USER만 작성(Q421); 서버가 좌표를 네이버 역지오코딩해 `district_code`(법정동 앞 5자리) 저장, 실패·미등록 코드면 NULL(Q406 A·Q406-2 A); 코드로 `shelter_district_assignments` 매핑 우선, NULL·미매핑이면 하버사인 최근접 보호소; 수동 재배정은 ADMIN 범위 외라 경로 없음(Q411); 기존 구조 기록 보호소는 유지. 실종 신고도 같은 방식으로 `district_code`만 저장(배정 없음) |
| 신고 수정·사진 | CLOSED 상태는 본문·사진 수정 거부(Q425 B); 위치 변경 시 `district_code` 재도출(발견 신고 `shelter_seq`는 유지); 사진은 `imageSeqs` 전체 최종 배열을 받아 서버가 diff 계산, 연결 행 전체 삭제 후 `display_order=index+1`로 재삽입, 제거된 이미지는 마지막 참조 해제 검사, ACTIVE이고 diff가 있으면 AI 재매칭(Q424 A) |
| 발견 신고 종료·재개 | CLOSED 시 closed_dttm 필수·closed_reason TEXT 자유 입력 선택, ACTIVE 재개 시 두 필드 NULL |
| 구조 접수·결과 변경 | SHELTER 전용, `shelter_seq`는 요청자 소속(Q410); 접수 시각 reg_dttm 사용; 접수 시 사유 코드·장소·구조 일시 필수(Q461), 항상 PROCESSING으로 생성; PROCESSING 시 completed_dttm NULL, DONE/FAILED/CANCELED 시 NOT NULL; FAILED/CANCELED 사유 CHECK |
| 구조 기반 보호동물 등록 | DONE 구조 확인 후 `rescue_seq` FK만 연결; 구조 정보는 **복사하지 않고 JOIN**. 직접 등록·초기 적재 시 `rescue_seq=NULL` 허용, 구조 정보 NULL; 담당 보호소 동일 여부 강제 안 함 |
| 구조 삭제 | 연결된 보호동물이 1건이라도 있으면 소프트 삭제 거부. 발견 신고 삭제는 기존 구조 기록을 변경하지 않음 |
| 보호동물 등록·삭제 | SHELTER 전용, `shelter_seq`는 요청자 소속(Q410); 보호 상태는 PROTECTING 고정, 입양 상태·공고 시작/종료일은 요청값 또는 기본값(UNAVAILABLE, 등록일, +10일; Q479); 공공데이터 초기 적재 날짜는 담당자가 직접 결정·입력; 상태 조합 CHECK; 활성 신청서 존재 시 삭제 거부 |
| 보호동물 상태·공고기간 관리(11.6) | 담당 SHELTER 전용, 어느 상태에서든 전환 허용(Q479). 한 트랜잭션에서 `protection_status`·`adoption_status`·`end_reason`·`protection_ended_dttm` 네 컬럼 동시 설정 → PROTECTING이면 종료 컬럼 NULL(FK·반환 행·사유·비고 유지), ENDED면 `end_reason`(기본 OTHER), RETURNED면 인계자 이름·번호 필수 + 활성 회원 전화번호 정확히 1건 일치 시 사용자 연결 + `animal_returns` INSERT 또는 덮어쓰기(`mod_dttm`), ADOPTED면 `adoption_reason`(필수)·`adoption_memo` 저장·`completed_application_seq` 불변 → 종료 상태면 활성 신청 REJECTED(자연어 사유·자동 거절 시각) → 공고 두 날짜는 상태와 무관하게 갱신(CHECK 시작≤종료). 커밋 후 `ADOPTION_REJECTED` 알림 이벤트 |
| 입양 신청 DRAFT·제출 | 동일 회원·동물의 비삭제 활성 1건 부분 UNIQUE; 현재 질문 20개(`question_seq` 키)/JSONB 답변 + 고정 필드 4개(Q458); 제출은 `animal_seq` 기준 한 트랜잭션(DRAFT 있으면 갱신 후 제출, 없으면 SUBMITTED INSERT, `FOR UPDATE`); 버전·질문 이력·문항 스냅샷 없음; 제출 시 필수·조건 검증 및 개인정보 스냅샷 생성; DRAFT 삭제는 하드 삭제 |
| 입양 질문 변경 | 현행 문항 행을 수정하며 이전 문항/버전은 남기지 않음; 이전 답변 JSONB는 question_seq로 연결하여 최신 질문으로 표시(의미 변경 위험 수용) |
| 입양 심사 | SUBMITTED→APPROVED 또는 REJECTED; 수동 처리자/시각 기록, REJECTED 자연어 사유 필수. 별도 이력 테이블 없음 |
| 승인 이후 입양 완료(12.11) | 해당 신청 ADOPTED + 해당 동물 보호·입양 상태 ADOPTED + `end_reason='ADOPTED'` + `protection_ended_dttm` + completed_application_seq 설정 + 다른 활성 신청 자동 REJECTED를 **한 트랜잭션**에 반영. 동물이 11.6으로 이미 ADOPTED(FK NULL)여도 채택 허용, 다른 신청서가 이미 연결돼 있으면 거부. 신청서 없는 입양 완료는 11.6(FK NULL 유지·사유·비고, Q479) |
| 승인 후 진행 실패 | 신청서 REJECTED 및 자연어 사유, 처리시각 갱신. 후속절차 테이블이나 거절 단계 컬럼 없음 |
| 입양 신청 자동 거절 | 시스템 사유 문구 생성, reviewed_dttm=거절 시각, reviewed_usr_seq=NULL. 과거 승인 시각은 보존하지 않음 |
| 이미지 업로드 | 4.1에서 서버가 S3 presigned PUT URL과 `image_key`를 발급하고 Redis에 발급 컨텍스트(키·요청자, TTL 10분 [가안]) 저장; 4.2에서 같은 요청자인지 확인 후 S3 HEAD로 크기·MIME 검증 → `images` INSERT(`reg_usr_seq`=요청자). 4.2 미호출 객체는 S3 수명주기 규칙으로 정리(S4 A). 앱은 4.2를 등록·전송 직전에 호출(Q456) |
| 이미지 수정·제거 | 연결 참조 해제 후 **모든 이미지 참조**(회원 profile, 보호소 profile/background, 댓글·채팅 image_seq 직접 FK; 모두 `core` 스키마)를 조회; 마지막 참조가 사라진 경우에만 images 행 삭제. 회원 프로필 교체·해제도 같은 경로(Q408). S3 객체 삭제는 DB 커밋 후 best-effort, 실패 시 로그만 남기고 고아 객체 허용(Q427 A) |
| 댓글·채팅 | 댓글은 실종·발견 신고 2종만(보호동물 댓글 없음, Q477); 본문 또는 이미지 최소 1개, 답글 깊이 1, 부모 게시물 복합 FK; 루트 삭제 시 답글 보존, 탈퇴 사용자 신규 답글·메시지 제한 |
| 채팅 미읽음·목록 갱신 | 미읽음은 읽음·숨김 포인터 이후 + 본인 발신 제외, 조회 시 계산(Q454). 메시지 저장 후 chat이 참여자별 `/user/queue/room-updates`(MESSAGE)로 방 요약·방별/전체 미읽음 수를 받는 사람 기준으로 계산해 전송, 읽음·나가기 시 본인에게 READ/LEFT. DB 쓰기는 기존 그대로(chat 소유) |
| 다형성 참조 | 동일 대상 공통 타입 코드 사용; reports/bookmarks/chat 공유의 허용 타입 CHECK; 실제 대상 존재·접근 권한은 BE 검사. 알림 target 두 컬럼은 NULL 허용 |
| AI 알림 | ACTIVE/PROTECTING 자격 확인, 등록·재개·사진 수정 시 비교; `missing_reports` 행 `FOR UPDATE` 잠금 → `AI_MATCH`와 `(missing_report_seq,target_type,target_seq)`로 삭제된 알림까지 포함해 쌍 존재 조회 → 최초 1회 저장. AI 중복 UNIQUE 없음 |
| 일반 알림·푸시 | 9종(Q447)만 저장; `message TEXT NOT NULL`(body 스냅샷)과 같은 값으로 FCM body, 유형별 서버 고정 문구로 FCM title 발송; FCM data에 `notificationSeq`·`notificationType`·`targetType`·`targetSeq`·`commentSeq`·`parentCommentSeq`(Q446); 실패 시 DB 보존·재시도 없음; 채팅 푸시는 별도 저장 없음 |
| FCM 토큰 | 기존 활성 토큰 비활성화 후 신규 활성화(재등록은 upsert 200); 사용자/토큰 활성 부분 UNIQUE; 탈퇴 시 전부 비활성(`UserDeleted` 이벤트); 로그아웃 요청의 `fcmToken`이 있으면 그 기기 토큰만 비활성(Q419 A) |
| 일반 알림 생성 이벤트 | 최소 집합(Q426 A, 유형명 Q447): `COMMENT`는 내 실종·발견 신고의 루트 댓글(게시물 작성자 USER; 보호동물 댓글 없음, Q477), `COMMENT_REPLY`는 내 루트 댓글의 답글(루트 댓글 작성자만, Q436), `AI_MATCH`는 실종 신고자에게 쌍당 1회, `ADOPTION_SUBMITTED`는 보호소에, `ADOPTION_APPROVED`·`ADOPTION_REJECTED`·`ADOPTION_COMPLETED`는 신청자에게, `SHELTER`는 담당 발견 신고 배정. SYSTEM은 발송 주체(ADMIN)가 없어 유형만 예약. COMMENT 계열은 게시물 target + `comment_seq`·`parent_comment_seq`(Q446). core-api(댓글·입양·배정)만 RabbitMQ `NotificationRequested` 이벤트(수신자·유형·완성된 message·target·commentSeq·parentCommentSeq)를 내고 notification이 저장·FCM(chat은 `ChatMessageSent`로 FCM만 요청, 저장 없음). AI_MATCH만 core-api가 행을 직접 INSERT하고 `NotificationCreated` 이벤트로 notification에 FCM만 요청(쓰기 소유권 예외, 이벤트 2종 구분) |
| 댓글 삭제 → 알림 body | 댓글·답글 소프트 삭제 커밋 후 core-api가 `CommentDeleted {targetType, commentSeq}` 발행 → notification이 COMMENT 계열·`(target_type, comment_seq)` 일치 행의 `message`를 "삭제된 댓글입니다"로 UPDATE(Q448). 쓰기 소유권 유지, 발송된 푸시 회수 없음, 댓글 수정은 갱신 없음 |
| 신고 접수 | `target_type` 5종(Q449·Q477)·`reason_code` 대상별 조합 CHECK, `(reporter_user_seq,target_type,target_seq)` UNIQUE, 대상 존재·SHELTER 계정 제외는 BE 검증. 상태 PENDING만 |
| 모의 입양 세션 시작 | USER만; `animal_name` 필수 1~6자(Q484); simulation이 `core.animal_breeds`·`animal_species`를 읽기 전용 JOIN(잠금 없음)으로 품종 존재·종 DOG/CAT 검증(Q482); 활성 세션 조회를 잠근 뒤 IN_PROGRESS 부분 UNIQUE 확인; `breed_seq`·`animal_name` 저장 + 종·성격 스냅샷(성격 NOT NULL, 랜덤 없음); 초기 스탯·잔액·예정 종료 설정; `SESSION_START` 로그. 실제 보호동물·보호소는 읽지 않음 |
| 모의 입양 돌봄 행동 | 세션 행 `FOR UPDATE` → IN_PROGRESS 확인 → 지연 계산 → 필요 기구 보유·소모품 수량 검증(`VET_VISIT`은 정답이 `VET_VISIT`인 PENDING 이벤트 존재 검증, 없으면 409) → 소모품 -1 → 효과·성격 배율 적용·0~100 보정 → **PENDING 이벤트 해결 판정(Q486):** `resolve_action_code = action_code`(WALK는 같은 `walk_seq`)이면 `resolve_effects_jsonb` 추가 반영, `resolve_cost>0`이면 `simulation_transactions(EVENT)` INSERT·잔액 차감 → `action_logs` INSERT(해결했으면 `occurrence_seq`) → 해결했으면 occurrence `RESOLVED`·`resolved_dttm`·`resolved_action_log_seq` UPDATE → `last_action_dttm` 갱신 → 종료 조건(건강 0) 판정 |
| 모의 입양 구매 | 세션 행 잠금 → 물품 활성·종 범위 검증 → 기구 중복 보유 거부 → `simulation_transactions(PURCHASE)` INSERT → `balance` 차감(음수 허용) → 인벤토리 UPSERT |
| 모의 입양 이벤트 | 발생: 세션 PENDING 없음 확인 후 RANDOM 추첨 또는 CONDITION 판정(쿨다운은 `simulation_event_occurrences` 마지막 발생으로 확인 → `trigger_probability` 추첨, Q481) → occurrence INSERT → 커밋 후 HOME이면 `SimulationPushRequested(EVENT)`(FCM만, 저장 없음). 해결: 별도 응답 API·트랜잭션 없이 돌봄 행동 행에서 처리(Q486). 만료: 조회·스케줄러 양쪽에서 감지해 시나리오 `neglect_effects_jsonb` 적용(성격 배율·0~100 보정) → EXPIRED + `EVENT_EXPIRE` 로그(`occurrence_seq`·`stat_delta_jsonb`) → `health=0`이면 종료, 산책 종료 시 PENDING WALK 이벤트도 동일(Q441·Q486). 세션 종료 시 남은 PENDING은 효과 없이 EXPIRED |
| 모의 입양 산책 | 시작: 세션 `species_type='DOG'` 검증(CAT이면 409, Q488) + 세션당 IN_PROGRESS 1건 부분 UNIQUE; 하트비트: `last_heartbeat_dttm` 갱신 + 경과 반영 + WALK 이벤트 추첨; 종료: 길이·성격 배율로 효과 적용(종 배율 없음), `WALK_END` 로그; 10분 무응답 시 스케줄러 자동 종료 |
| 모의 입양 종료·리포트 | 예정 종료·건강 0·포기·탈퇴 → 상태 변경 + `ended_dttm` + 진행 중 산책 ENDED·PENDING 이벤트 EXPIRED + 거래·로그 집계로 `simulation_reports` INSERT(`summary=NULL`) + `SESSION_END` 로그를 **한 트랜잭션**에 반영하고(포기 API는 여기서 응답), 커밋 후 비동기 LLM `summary` UPDATE(`WHERE summary IS NULL`, 실패 시 규칙 문구, Q444) → 갱신됐으면 `SimulationPushRequested(SESSION_END)` 발행(저장 없음, Q481·Q487). 스케줄러가 `summary` NULL·리포트 생성 10분 경과 리포트를 복구(Q497). 요약은 지금 규칙 문구 생성기, LLM은 나중에 같은 포트로 교체. 진행 중 중간 리포트는 저장 없이 계산 |
| 모의 입양 푸시 | DB 저장 없음(Q481). `EVENT`(HOME 이벤트 발생, 커밋 후)·`SESSION_END`(종료, 리포트 `summary` 저장 후 Q487) 2종만 RabbitMQ `SimulationPushRequested`로 notification에 넘겨 활성 FCM 토큰으로 발송(title·body 고정 문구, data `{target:SIMULATION}`); 방치·스탯 경고 알림 없음; 일반 `notifications` 미사용 |
| 모의 입양 보호소 열람 | SHELTER 토큰이면 종료 리포트를 누구 것이든 조회(18.18·18.20, 소속 판정 없음, Q483); 12.15는 core-api가 신청서 `user_seq`로 `simulation.simulation_reports`·`simulation_sessions` 읽기 전용 JOIN; 진행 중 세션·세션 상세·이력·지출은 본인만 |
| 초기 데이터 | 행정구역 마스터·보호소(`district_code`는 오프라인 계산값을 SQL에 직접 삽입, Q431)·SHELTER 계정·행정구역 배정·종/품종(성격 전량 배정 + 대표 이미지 `images` 행, Q482)/색상/조합·입양 질문 20개(`question_seq` 1~20 고정)·상점 물품(`image_key`)·시나리오(`trigger_probability`(Q481)·정답 행동 `resolve_action_code`·`resolve_cost`·해결/방치 효과·문구(Q486) 포함, 선택지 테이블 없음)는 Flyway 마이그레이션에 포함해 적재(Q412 A). 시드 SQL은 외부 API·S3를 호출하지 않으며, 시드 이미지는 팀이 S3에 먼저 올린 뒤 그 키를 SQL에 적음(`reg_usr_seq` NULL). 세부 적재 절차는 적재 시점에 별도 논의(Q431) |
| 교차 스키마 접근 | 쓰기는 자기 스키마만(서비스별 DB 계정 권한으로 강제 가능). simulation → `core.users`(리포트 목록의 사용자 표시)·`animal_breeds`·`animal_species`·`images`(품종 대표 이미지) 읽기(Q482부터 `shelter_animals`·`shelter_animal_images`는 읽지 않음), core-api → `simulation.simulation_reports`·`simulation_sessions` 읽기. 서비스별 DB 계정 권한은 이 목록을 테이블 단위 GRANT 기준으로 사용. 교차 FK 없음, 존재·상태 검증은 읽는 쪽 BE(Q420 D) |

## 9. 구현 제외·보류·추가 검증

### 현 프로젝트 구현/물리 ERD에서 제외
- `animal_transfers`: 보호소 간 보호동물 이관 기능·이력 자체 제외(Q283).
- `user_blocks`: 차단, 자동 차단, 차단 해제, 채팅 차단 규칙 전체 제외(Q306).
- `adoption_form_answers`: 정규화된 답변 테이블 삭제, `adoption_applications.answers_jsonb` 사용(Q276). `adoption_form_templates`도 Q402에 따라 현행 모델에서 제거. `adoption_processes`, `adoption_application_histories`, `shelter_animal_status_history`도 현행 모델에서 제거(Q375·376·378).
- `chat_message_images`, `missing_report_comment_images`, `found_report_comment_images`, `shelter_animal_comment_images`: 0~1 이미지의 직접 FK 통합으로 삭제(Q400~401). `chat_notifications`: DB에 채팅 푸시 이력을 저장하지 않음.
- `shelter_animal_comments`: 보호동물 댓글 기능 자체를 제외해 v17에서 테이블 삭제(Q477). 공통 코드 `SHELTER_ANIMAL_COMMENT`도 폐기.
- 모의 입양은 v9에서 **구현 범위에 포함**(M1~M17, v10 번호 35~45번 테이블). v8까지 보류 이름이던 `mock_adoption_runs`·`mock_adoption_results`는 `simulation_sessions`·`simulation_reports`로 대체되어 만들지 않음. 설계 과정의 초안이던 `simulation_diagnoses`(진단 마스터)와 `simulation_action_logs.diagnosis_seq`도 M15에 따라 만들지 않음(아픔은 CONDITION 이벤트).
- `simulation_notifications`: v18에서 **삭제**(Q481). 모의 입양 푸시는 저장 없이 FCM만 보내며 알림 조회·읽음 API도 없음. 45번은 빈 번호.
- `simulation_event_choices`: v19에서 **삭제**(Q486). 돌발이벤트는 선택지 없이 시나리오의 정답 행동 1개로 해결하며, 정답·비용·효과·문구는 `simulation_event_scenarios` 컬럼. 선택지 응답 API(18.13)·`EVENT_ANSWER` 로그 코드도 없음. 42번은 빈 번호.
- 모의 입양의 실제 보호동물 연동(`simulation_sessions.animal_seq`·`shelter_seq`·표시 스냅샷 3컬럼, 11.2 `simulation` 파생 블록, 보호소 소속 기준 열람): v18에서 **폐기**(Q482·Q483). 세션은 품종 논리 참조와 가상 이름으로만 구성.
- `refresh_tokens`: Redis TTL 저장으로 대체되어 만들지 않음(Q409 B). `users.profile_image_key`: Q408로 삭제.
- ADMIN 관련 테이블·컬럼·기능 일체(관리자 계정 발급, 신고 심사, 발견 신고 수동 재배정, 마스터 데이터 관리 API)는 이번 구현 범위 외(Q411). `users.role`의 `ADMIN` 값만 예약.
- `confirmed_animal_links`: 실종↔발견/보호동물 동일 개체 확인 링크 저장하지 않음. `animal_returns.missing_report_seq`도 없음.
- 입양 이후 사후관리 테이블·API는 프로젝트 범위 밖. 보호소 삭제 정책은 Q356에서 엣지케이스로 스킵.

### v19 확정 완료 — 남은 [가안] 목록 (튜닝·구현 수치, 스키마 결정 아님)

> v9의 [미정]·보류 목록(Q406, Q412, Q413~Q419, S1~S8)은 2026-11-02에 모두 확정되어 §14 추적표로 옮겼습니다. 2026-11-03에 Q430~Q445(감사 시각 매핑·시드 적재·AI 워커 MQ·refresh JWT·403/404 정책·보호소 리포트 열람·알림 유형 세분화·오류 코드 재검토·이미지 읽기 URL·리포트 요약 비동기·로그인 실패 제한)를 추가 확정했고, 같은 날 Figma 대조로 Q446~Q453(알림 이동 정보·유형 세분화·삭제 댓글 알림·신고 사유 코드, API 전용 4건)을, 2026-11-04 Figma·미리보기 대조로 Q454~Q461(채팅 목록·댓글·입양 신청서·신청 내역·보호소 입양·구조일지)을, 같은 날 신고·입양·주소 검색 질의응답으로 Q462~Q467(수료증 컬럼 삭제·신고 검색 조건·내 신고/담당 목록·실종 `name`·`pet_seq` 삭제·주소 검색 프록시)을, 이어서 Q468~Q469(신고 목록 필드 보강·몸무게 `VARCHAR(30)` 통일)와 Q470~Q472(담당 목록 신고자 실명·구조 목록 기간 프리셋·건수 동봉·구조 상세 보강, 스키마 변경 없음), Q473~Q475(보호동물 목록·상세·우리 보호소 목록을 신고 형식과 통일, 스키마 변경 없음)를, 2026-11-05에 Q476~Q479(상세 보호소 블록·보호동물 댓글 테이블 삭제·보호소 전용 상세·상태/공고기간 관리 API와 `adoption_reason`·`adoption_memo`·반환 행 덮어쓰기)와 Q480(댓글·답글 전량 조회, API 전용)을, 2026-11-06에 Q481~Q484(모의 입양 알림 테이블 삭제·품종 기반 시작·보호소 열람 전면 허용·가상 이름)와 Q485~Q487(모의 입양 스탯 개명·방향 통일, 돌발이벤트를 정답 행동 1개 구조로 재구성·선택지 테이블 삭제, 리포트 요약 완료 후 `SESSION_END` 푸시)을, 2026-11-08에 Q488~Q489(산책 개 전용·종 배율 삭제, 불필요한 `VET_VISIT` 409 유지. 스키마 변경 없음)를 확정했습니다(§14). 새 안건은 Q490부터 번호를 붙입니다. 아래는 **값만 조정 가능한 [가안]**이며 구조 재질문 대상이 아닙니다.

| 번호 | 항목 | 현재 가안 |
|---|---|---|
| Q409 보조 | 토큰 수명·Redis 키 | access 1시간, refresh 14일, 키 `auth:refresh:{userSeq}:{jti}`(해시 저장) |
| Q414 보조 | 가입 컨텍스트 | 키 `auth:register:{token}`, TTL 10분 |
| S4 보조 | presigned PUT 발급 컨텍스트 | Redis `upload:image:{imageKey}` → 요청자·contentType·fileName, TTL 10분. 고아 객체 수명주기 규칙 1일(수료증용 `upload:file:*`는 Q460에서 제외) |
| S7 보조 | 키워드 검색 인덱스 | `missing_reports.description`·`found_reports.description` pg_trgm GIN 인덱스(Q463부터 검색 대상이 `description`뿐). 느려질 때 추가해도 무방 |
| Q463·Q464 보조 | 신고 목록 인덱스 | `occurred_dttm` 부분 인덱스 2개, `rescue_records(found_report_seq, reg_dttm DESC)` 부분 인덱스. 실측 후 조정 |
| Q471 보조 | 구조 목록 인덱스 | `rescue_records(shelter_seq, rescued_dttm DESC) WHERE is_deleted = FALSE`(최근 N일 필터·건수 집계용). 보호소당 행 수가 적어 없어도 무방, 실측 후 추가 |
| Q473·Q475 보조 | 보호동물 목록 인덱스 | `shelter_animals(shelter_seq, reg_dttm DESC) WHERE is_deleted = FALSE`(11.3 최근 N일·건수 집계), `shelter_animals(protection_status, adoption_status, reg_dttm DESC) WHERE is_deleted = FALSE`(11.1 합성 상태 필터 + 등록일 정렬). `health_check` pg_trgm은 S7 보조와 같이 느려질 때 추가 |
| Q467 보조 | 주소 검색 프록시 | 네이버 Geocoding 타임아웃 3초, 후보 최대 10건, 호출 빈도 제한 없음(남용 시 Redis 카운터) |
| S8 보조 | 스케줄러 주기·잠금 | 10분 주기, simulation 인스턴스 1개. 다중이면 ShedLock |
| ~~Q420 보조~~ | ~~세션 스냅샷 컬럼 구성~~ | **Q482로 종결.** 표시 스냅샷 컬럼 폐기, 세션은 `breed_seq` 논리 참조 + 종·성격 스냅샷만 |
| M3·M4·M11 | 모의 입양 튜닝값 | 모두 [가안]이며 코드 설정값(Q429 A). §3 "코드 설정 가안" 참조(스탯 키·방향은 Q485로 확정, 수치만 가안). CONDITION 발생 확률은 시나리오 컬럼(Q481)이라 제외. M12의 종 배율은 Q488로 삭제(산책 DOG 전용) |
| ~~M7 보조~~ | ~~품종 성격 NULL 처리~~ | **Q482로 종결.** `personality NOT NULL`, 시드 전량 배정 |
| ~~세션 시작 동물 상태~~ | ~~모의 입양 시작 조건~~ | **Q482로 종결.** 보호동물 연결 없음, 품종 존재·종 DOG/CAT만 검증 |
| Q447 보조 | 알림 title·body 문구 | 유형별 title 고정 문구와 body 템플릿·대체 문구는 API v4 17.1 표. 댓글 본문 body 최대 100자, 초과 시 `…`, 이미지만 있으면 "사진을 남겼어요" |
| Q448 보조 | 삭제 댓글 알림 body | "삭제된 댓글입니다" |
| Q454 보조 | STOMP 백그라운드 유예 | 앱이 백그라운드로 간 뒤 10~30초 후 DISCONNECT |

### v19 팀 논의 대기 (구조 미확정, 다음 버전에서 결정)

| 항목 | 내용 | 관련 |
|---|---|---|
| ~~보호동물 상태 변경 방식~~ | **Q479에서 종결.** API 11.6 관리 API 하나로 상태·입양 가능 여부·공고기간 변경, 어느 상태에서든 전환 허용 | 20번 |
| ~~공고기간 직접 입력~~ | **Q479에서 종결.** 등록 시 입력 가능(기본값 유지), 11.6으로 두 날짜 수정 | 20번 |
| 발생장소 직접 입력 | 구조 기록 없는 직접 등록 동물용 `shelter_animals.occurred_place` 추가 제안 | Q404 |
| 공고번호 | "경북-구미-2026-00001" 형식 `notice_number` 컬럼·채번 규칙 필요 여부 | 신규 |
| 동물 등록 시점 | 진행 중 구조에서 바로 동물 등록 시 (A) 진행 중 연결 허용 + 실패·취소 처리 규칙 또는 (B) 동물 등록 시 구조 자동 DONE | 11.4 |
| 업로드 설계(Q457) | 티켓의 `public/tmp/` → `public/` 이동, CloudFront URL과 명세 정렬 | 14번 `images` |
| 모의 입양 화면 | API 12.15·모의 입양 화면(12.12 외부 입양은 Q479로 11.6에 흡수되어 종결) | 보류 |

### 초안·추후 논의 (확정된 구현 필드로 오해 금지)
- `image_embeddings`: pgvector 사용 후보 초안만 보존. 이미지당 임베딩, 모델 버전/차원/인덱스/재생성 정책은 사용자 요청까지 보류. **저장 위치는 pgvector 후보로 고정**(S7에서 OpenSearch 미도입 확정, kNN 용도도 없음). 상세 설계 재질문은 계속 금지.
- **제거 확정 테이블은 구현 보류가 아니라 현행 ERD에서 제외**: `shelter_animal_status_history`(Q378), `adoption_processes`(Q375), `adoption_application_histories`(Q376). 이들 테이블을 추가하거나 임시 FK를 연결하지 않음. `rejection_type/rejection_stage`도 만들지 않고 신청서의 자연어 `rejection_reason`을 사용.
- `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations`의 실제 기준값과 기타 옵션 매핑은 팀이 직접 결정(Q336 스킵).
- 공공데이터 보호소 외부 코드 유일성과 주소·좌표 필수값 수급 가능성 검증. 초기 적재 공고 날짜는 적재자가 정해 입력(Q388); 외부 보호동물 원천 ID 중복 판별은 실제 적재 구현 시 확인(새 필드 임의 추가 금지).
- 공유 이미지 원본은 모든 참조 해제 뒤에만 삭제(Q383). DB/S3 비원자성은 "커밋 후 best-effort 삭제, 실패 시 로그·고아 허용"으로 확정(Q427 A), 재처리 큐 없음. 임베딩 생명주기는 보류.
- 교차 스키마 읽기 전용 매핑(`@Immutable` 슬림 엔티티 또는 QueryDSL 프로젝션)의 컬럼 집합은 구현 단계에서 서비스별로 정함. 읽는 쪽 서비스는 `ddl-auto=validate`이므로 소유 서비스가 컬럼을 바꾸면 읽는 서비스도 같이 배포해야 함(Q420 D의 수용된 결합).
- 최종 DDL의 PK 정수 범위, 감사 컬럼/인덱스 이름, 순환 FK 생성 순서 및 데이터 마이그레이션 경로는 구현 단계에서 확인. 특히 기존 `template_seq`/`question_code` 답변 키 → `question_seq` 변환, 제거된 이미지 연결 테이블의 참조 이전, AI 알림 중복 식별자 이전, 구조 정보 복사 컬럼 삭제 전 데이터 보존 여부, `users.profile_image_key` → `profile_image_seq` 전환(기존 키가 있으면 images 행 생성 후 연결)은 **기존 데이터가 있을 때만** 마이그레이션 검증 필요. Q386 대상인 작성자 중복 reg_usr_seq는 표에서 이미 제거. `pets.mod_usr_seq`도 Q396에서 제거. 모의 입양 테이블 간 FK 순서는 `simulation_sessions` → `simulation_walks` → `simulation_event_occurrences` → `simulation_transactions`/`simulation_action_logs`(`simulation_notifications`는 Q481로 삭제). `animal_breeds.representative_image_seq` FK 때문에 `images`가 `animal_breeds`보다 먼저 생성돼야 함(Q482).
- 모의 입양 `effects_jsonb`·`trigger_condition_jsonb`·`stat_delta_jsonb`의 키 집합과 연산자(`<`, `<=`, `>`, `>=`)는 코드의 공통 Enum으로 고정하고 DB CHECK는 두지 않음. 시드 데이터 검증은 적재 시 수행.
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
| v6: 일반 시스템 알림 유형 미확정 | Q392에서 스킵했으나 Q398에서 **SYSTEM 추가**, 5종(`REPORT` 미추가) → Q447에서 9종으로 세분화 |
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
| v8: `users.profile_image_key TEXT` 저장소 키 | Q408: `users.profile_image_seq BIGINT NULL` → images FK로 보호소와 통일 |
| v8: `images`에 업로더 없음 | Q407: `images.reg_usr_seq INTEGER NULL` 추가, 연결 시 본인 이미지만 허용 |
| v8: `animal_species.species_code` ※초안 NULL 허용 | M8: NOT NULL UNIQUE, CHECK `DOG/CAT/ETC` |
| v8: 품종에 성격 없음 | M7: `animal_breeds.personality VARCHAR(20) NULL` CHECK 4종 → Q482: NOT NULL(시드 전량 배정) + `representative_image_seq` FK→images |
| v8: ADMIN 전역 관리·발견 신고 수동 재배정·식별 정보 수정 | Q410·411: ADMIN 범위 외. 구조 접수·보호동물 등록 SHELTER 전용, 재배정 경로 없음, 역할 값만 예약 |
| v8: 모의 입양 보류(`simulation_notifications`·`mock_adoption_*` 이름만) | M1~M17: `simulation_` 11개 테이블로 구현 범위 포함 → Q481: `simulation_notifications` 삭제, 10개 |
| 설계 초안: 진단 마스터 `simulation_diagnoses`, `VET_VISIT` 행동, `VET` 거래, 병원 API | M15: 아픔은 CONDITION 이벤트, 병원은 선택지. 진단 테이블·행동·거래 유형·API 없음 → **Q486:** 선택지를 없애면서 `VET_VISIT`은 돌봄 행동 코드로 부활(비용은 시나리오 `resolve_cost`, `EVENT` 거래). 진단 테이블·`VET` 거래 유형·병원 API는 여전히 없음 |
| 설계 초안: `notifications.notification_type`에 `SIMULATION` 추가 | M13·M16: 추가하지 않고 `simulation_notifications` 분리 → Q481: 저장 자체를 폐지, FCM만(`EVENT`·`SESSION_END`) |
| v9~v17: 실제 보호동물에서 시작(`simulation_sessions.animal_seq`·`shelter_seq`·표시 스냅샷), 보호소는 자기 동물 세션 리포트만 열람 | Q482·Q483: 품종(`breed_seq`)에서 시작, 보호동물·보호소 연결 없음, SHELTER는 종료 리포트 전체 열람. 가상 이름 `animal_name`(Q484) |
| 설계 초안: 개만 산책 | M12: 고양이도 산책 가능, 종 배율로 손해 → **Q488:** 다시 개 전용으로 확정(CAT 세션은 409 `SIMULATION_WALK_NOT_FOR_SPECIES`), 종 배율 삭제. 테이블에 종 컬럼 없이 세션 `species_type`으로 BE 검증 |
| 설계 초안: `shelter_animals.personality` | M7: 품종(`animal_breeds`)에 저장 |
| 설계 초안: refresh token 테이블 | Q409 B: Redis TTL, 테이블 없음 |
| v10: `reports.reason_detail` 자유 텍스트 필수·`reason_code` 없음(Q308) | Q449: `reason_detail` 삭제, `reason_code` 선택형 재도입, 대상별 조합 CHECK |
| v10: 보호동물 게시글(`SHELTER_ANIMAL`) 신고 허용 | Q449: 제외. 보호동물 댓글 신고는 유지 |
| v10: 알림 5종·`message` 단일 문구·`actor_user_seq` | Q447: 9종 세분화, title은 서버 고정 문구(비저장)·`message`는 body 스냅샷, actor 컬럼 삭제 |
| v10: COMMENT 알림 이동 대상 미정 | Q446: target=게시물 + `comment_seq`·`parent_comment_seq` |
| v11: 구조 사유 자유 텍스트, 구조일 DATE, 장소·날짜는 DONE 시 필수(Q357) | Q461: 사유 코드 4종, `rescued_dttm` NOT NULL, 장소 NOT NULL·상세 장소 추가, 접수 시 필수 |
| v11: 입양 문항 21개 | Q458: 현재 20개, `DATE` 유형, `question_seq` 1~20 고정 시드 |
| v11: 수료증 비공개 업로드(Q277~281·Q443) | Q460: 기능 제외, 컬럼만 유지 → **Q462(v13): 컬럼 삭제** |
| v11: 채팅 미읽음 "조회 시 계산"만 기재 | Q454: 정의(본인 발신 제외)·노출 기준·참여자 인덱스 명시, 카운터 미채택 |
| v12: `missing_reports.name NOT NULL`(Q366), `pet_seq` 선택 연결·스냅샷(Q348) | Q465·Q466(v13): 두 컬럼 삭제. 반려동물과 실종 신고는 FK 없음 |
| v12: 7.1/8.1 필터에 품종·색상·보호소, 키워드는 `description`·`address`(·`landmark_description`) | Q463(v13): 품종·색상·보호소 삭제, 기간·중성화 추가, 키워드는 `description`만, 포함 매칭 |
| v12: 8.8 `hasRescue` 불리언(R10) | Q464(v13): `rescue_records` 파생 구조 상태 4값, 최근 N일·지역·종 필터, size 8 |
| v12: 신고 위치는 앱이 좌표 전송(서버 지오코딩 없음, 암묵) | Q467(v13): API 5.6 주소 검색 프록시 신설로 명시. 등록 저장 방식은 그대로, 상세 주소 컬럼 없음 |
| v12~v13: `shelter_animals.weight NUMERIC(5,2)` kg 숫자 + CHECK `> 0`(Q458), 실종·발견에 몸무게 없음 | Q469(v14): 네 동물 테이블 모두 `weight VARCHAR(30)` 자유 텍스트. 실종·발견 컬럼 신설, 보호동물 타입 변경·CHECK 삭제 |
| v13: 발견 목록(8.1)에 `gender`·`reg_dttm` 없음, 신고 목록에 나이·중성화 없음 | Q468(v14): 7.1/8.1 목록에 `age`·`is_neutered`·`weight`(8.1은 `gender`·`reg_dttm`도) 포함. 컬럼 변경 없음 |
| v12~v14: 8.8 담당 목록은 `reporter_phone`만, 10.2 요약의 신고자는 닉네임 | Q470·Q472(v15): 보호소 화면의 신고자는 `users.name` **실명** + `reporter_phone`. 컬럼 변경 없음 |
| v12~v14: 10.1 구조 목록은 `rescued_dttm` 날짜 범위 필터·size 20, 상태별 건수는 별도 API 10.7(Q461) | Q471(v15): 최근 7/30/90일 프리셋(`rescued_dttm`)·size 8, 건수는 `status` 제외 필터의 `GROUP BY status`로 목록 응답에 동봉, 10.7 삭제. 항목에 `rescue_memo`·`is_neutered`. 컬럼 변경 없음 |
| v12~v15: 11.1 필터에 보호소·품종·색상·`protection_status`(기본 PROTECTING)·`adoption_status`, 정렬 `notice_end_date`, 목록에 나이·중성화·몸무게·공고 시작일 없음, 상세 분류는 PK만 | Q473·Q474(v16): 신고 목록과 같은 필터(보호+입양 합성 파생 상태 5값·`reg_dttm` 날짜 범위·소재지·종·성별·중성화 포함 매칭·특징 키워드, 모두 생략 시 전체)·`reg_dttm` 정렬, 항목에 동물 속성·공고기간·보호소 블록, 상세는 목록 + 상세 전용. 컬럼 변경 없음 |
| v12~v15: 11.3 필터는 `protection_status`·`adoption_status` 두 개, 건수 없음, size 20 | Q475(v16): 두 상태를 합친 파생 탭 5값, 최근 7/30/90일(`reg_dttm`)·종·성별·중성화, size 8, 건수는 탭 제외 필터의 `GROUP BY` 집계로 목록 응답에 동봉. 컬럼 변경 없음 |
| v5~v16: 보호동물 댓글 테이블 `shelter_animal_comments`, 신고 대상 6종, COMMENT 알림 대상에 `SHELTER_ANIMAL` | Q477(v17): 보호동물 댓글 기능·테이블 삭제, 신고 대상 5종, COMMENT 계열 알림 대상 `MISSING`/`FOUND`만 |
| v5~v16: 반환(11.7)·보호 종료(11.8)·외부 입양(12.12)·입양 상태(11.6) 별도 API, 종료 상태는 되돌릴 수 없음, `ANIMAL_NOT_PROTECTING`·`ANIMAL_ALREADY_RETURNED`, 공고 시작일 수정 불가(R5) | Q479(v17): API 11.6 관리 API 하나로 통합, 어느 상태에서든 전환 허용, PROTECTING 복귀 시 종료 컬럼 NULL, 반환 행 덮어쓰기(`mod_usr_seq`·`mod_dttm` 추가), `adoption_reason`·`adoption_memo` 추가, 두 공고 날짜 수정 가능·등록 시 입력 가능 |
| v16: 11.2 보호소 블록은 PK·이름·`district_code`, 댓글 수 포함, USER·SHELTER 공용 | Q476·Q478(v17): 11.2는 USER 전용이며 보호소 블록은 PK·이름·`phone`·`address`, 댓글 수 없음. 보호소 웹은 11.10 전용 상세(반환·입양 정보·신청 건수) |

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
| Q288 | ADMIN만 발견 신고 담당 보호소 수동 변경; 위치 수정 시 자동 재배정 없음(**Q411에서 ADMIN 범위 외 → 이번 구현에 재배정 경로 없음**) |
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
| Q299 | users에 이름/생년월일/전화번호/프로필 이미지 키 추가(**프로필 이미지 키는 Q408에서 `profile_image_seq` images FK로 대체**) |
| Q300 | 활성 유저 닉네임 대소문자 무시 중복 제한 |
| Q301 | 탈퇴 시 users와 user_accounts 소프트 삭제, 재가입 신규 계정 |
| Q302 | 실종·발견 신고 연락처 스냅샷 저장 |
| Q303 | 로그인 필수, 신고별 연락처 공개·비공개 설정 |
| Q304 | 실종·발견 is_phone_public 기본 FALSE |
| Q305 | 마이크로칩 등록 여부 컬럼 없이 번호만 선택 저장 |
| Q306 | user_blocks/차단 기능 전부 제거 |
| Q307 | reports PENDING만 사용, 신고자·대상 전역 중복 금지 |
| Q308 | 신고 사유 자유 입력 reason_detail 필수, reason_code 제거 **→ Q449로 대체(reason_code 선택형, reason_detail 삭제)** |
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
| Q398 | A: SYSTEM 일반 알림 유형 추가 | 알림 유형 5종, NOT NULL. 과거 Q392 스킵 대체(**Q447에서 9종으로 세분화**) |
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

## 14. Q406~Q489 · M1~M17 · S1~S8 결정 추적표 (v10~v19)

> Q406~Q412는 두 명세서 간극 정리의 1차 질의응답, Q413~Q419는 2차(인증·공통 규약), Q420~Q429·S1~S8은 2026-11-02 3차(구조·정책·스택)이며 **모두 [확정]**입니다. M1~M17은 모의 입양 설계 질의응답이며 M1·M13·M14·M16·M17은 2026-11-06 Q481~Q483이, M12는 2026-11-08 Q488이 대체했습니다. 표기: [확정] / [가안](추천안을 임시 채택, 코드 설정값). v18에 [미정]은 없습니다.

### Q0·Q406~Q419

| 질문 | 최종 결정 | 현행 데이터 모델 영향 |
|---|---|---|
| Q0-1 | [확정] A: 새 파일 생성 | v9/v2에 이어 v10/v3 신규. 이전 버전 보존 |
| Q0-2 | [확정] Kotlin + Spring Boot, Redis 사용. RabbitMQ·OpenSearch·S1~S8은 v10에서 확정(아래 S1~S8) | refresh token Redis 저장의 근거 |
| Q406 | **[확정] A**: `shelters`·`missing_reports`·`found_reports`에 `district_code VARCHAR(5) NULL`(법정동 코드 앞 5자리) + 서버 역지오코딩(네이버 `legalcode`, port/out 어댑터). 보호소는 오프라인 계산값을 시드에 삽입(Q431), 신고는 등록·위치 수정 시 재도출 | 컬럼 3개 추가, `districts` FK. `shelter_district_assignments.district_code`는 `VARCHAR(5)`로 통일 |
| Q406-1 | [확정] A: 행정구역 마스터 `districts(district_code PK, sido_name, sigungu_name)` 시드 테이블 + `GET /districts` | 07번 테이블(이후 번호 +1, 총 45개). 네 테이블의 `district_code` FK 대상 |
| Q406-2 | [확정] A: 역지오코딩 실패·미등록 코드는 NULL 저장, 발견 신고 배정은 최근접으로 진행, 필터에서 제외, 등록 거부 없음 | 스키마 영향 없음(NULL 허용 전제) |
| Q407 | [확정] A: 이미지 업로더 기록 | `images.reg_usr_seq INTEGER NULL FK→users`. 시드·공공데이터 NULL. 연결 시 본인만(BE), ADMIN 예외 없음 |
| Q408 | [확정] A: 프로필 이미지를 images FK로 통일 | `users.profile_image_key` 삭제 → `users.profile_image_seq BIGINT NULL FK→images`. 참조 해제 정책(Q383) 적용 |
| Q408-1 | [확정] A: 카카오 프로필 사진 미수집 | 가입 시 `profile_image_seq=NULL`. 카카오 로그인은 Kakao Developers 테스트 앱 기준, 이메일도 미수집 |
| Q409 | [확정] B: refresh token Redis TTL | DB 테이블 없음. [가안] 키 `auth:refresh:{userSeq}:{jti}`, 해시 저장, TTL 14일, access 1시간, rotation, 재사용 감지 시 사용자 키 전부 삭제, 로그아웃 시 키 삭제, access 블랙리스트 없음, 다중 기기 허용 |
| Q410 | [확정] ADMIN 범위 외 | 구조 접수·보호동물 등록은 SHELTER 전용, `shelter_seq`는 요청자 소속. 요청에 보호소 지정 필드 없음 |
| Q411 | [확정] A: `users.role` CHECK 유지 | `USER/SHELTER/ADMIN` 유지, ADMIN은 예약값·미구현. 문서의 ADMIN 동작 서술은 모두 범위 외 |
| Q412 | [확정] 초기 데이터는 API로 관리하지 않음 / **[확정] A 적재 경로 Flyway** | 보호소·SHELTER 계정·행정구역 마스터·배정·동물 카탈로그·입양 질문·상점 물품·시나리오는 Flyway 마이그레이션의 시드. `core`는 core-api, `simulation`은 simulation이 보유, chat·notification은 validate |
| Q413 | [확정] A: 앱이 카카오 SDK access token 전달, 서버가 카카오 사용자 조회 API로 검증, 미가입자는 2.5로 2단계 가입 | DB 영향 없음. `user_accounts.login_identifier`는 카카오 회원번호 |
| Q414 | [확정] A: registrationToken은 Redis `auth:register:{token}` → 카카오 id, TTL 10분, 가입 성공 시 삭제 | DB 테이블 없음 |
| Q415 | [확정] A(코드 기준): 봉투 `{status,code,message,data,errors,traceId}`, 204 미사용, 반환값 없으면 200 + `data:null`, 생성 201, 중복 등록 409(디바이스 토큰 등록만 upsert 200) | DB 영향 없음 |
| Q416 | [확정] A: ISO-8601 `+09:00`(`OffsetDateTime`, Asia/Seoul) | DB는 TIMESTAMPTZ + 세션 Asia/Seoul 유지(Q393) |
| Q417 | [확정] B 변형: `{content,page,size,totalElements,totalPages,hasNext}`, page 0부터, 기본 20·최대 100, **엔드포인트별 정렬 화이트리스트(기본 `regDttm`) + asc/desc + 필드 필터**. 화이트리스트 밖 `sort`는 400 | 정렬·필터 컬럼 인덱스는 구현 단계 |
| Q418 | [확정] A 변형(코드 기준): enum 상수명이 `code`, 규칙 `{도메인}_상황`, 공통 `COMMON_*`·`AUTH_*`(만료·위조 401 분리). 도메인 접두어는 서비스 안의 도메인 패키지 단위이며 DB 스키마와 무관 | DB 영향 없음 |
| Q419 | [확정] A: 로그아웃 요청에 `fcmToken` 선택 필드, 있으면 그 기기 토큰만 비활성 | `device_tokens` 비활성 시점만 정해짐, 스키마 동일 |

### Q420~Q429 (3차, 2026-11-02)

| 질문 | 최종 결정 | 현행 데이터 모델 영향 |
|---|---|---|
| Q420 | **[확정] D 스키마 2개**: `core`(core-api·chat·notification 공유, FK 그대로) + `simulation`(simulation 전용). 교차 스키마 FK 없음, 교차 읽기 JOIN 허용, 쓰기는 자기 스키마만(테이블당 쓰는 서비스 하나). Spring 서비스 4개·경로 접두어·포트는 코드대로 유지 | `simulation_*`의 `user_seq`·`animal_seq`는 논리 참조. `simulation_sessions`에 `shelter_seq`·표시용 스냅샷 추가(→ Q482로 `animal_seq`·`shelter_seq`·스냅샷 삭제, `breed_seq` 논리 참조), `simulation_shop_items.image_seq` → `image_key`. 식별자 규칙: `user_seq`=행위자, `shelter_seq`=보호소 자원 범위 |
| Q421 | [확정] A: 실종·발견 신고 작성은 USER만. 담당 SHELTER는 배정된 발견 신고의 종료만 | 스키마 영향 없음(BE 권한) |
| Q422 | [확정] A: 입양 DRAFT는 SUBMITTED 이후만 보호소 업무 목록에 노출 | 스키마 영향 없음 |
| Q423 | [확정] A: 게시물·댓글·채팅 수정·삭제는 작성자 본인만 | 스키마 영향 없음 |
| Q424 | [확정] A: 사진 수정은 `imageSeqs` 전체 최종 배열. 서버가 diff 계산, `display_order=index+1`, 연결 행 전체 재삽입, 제거분은 참조 해제 검사 | 연결 테이블 구조 동일 |
| Q425 | [확정] B: CLOSED 신고는 읽기 전용. 수정하려면 ACTIVE 재개 | 스키마 영향 없음 |
| Q426 | [확정] A: 알림 생성 이벤트 최소 집합(COMMENT·AI_MATCH·ADOPTION·SHELTER). SYSTEM은 발송 주체 없어 유형만 예약 | `notifications` 동일 |
| Q427 | [확정] A: 저장소 객체 삭제는 DB 커밋 후 best-effort, 실패 시 로그, 고아 허용 | 삭제 대기 테이블 없음 |
| Q428 | [확정] B: 신고 내역 조회(API 15.2)는 범위 외·미구현 | `reports` 테이블은 접수용으로 유지 |
| Q429 | [확정] A: 모의 입양 튜닝값은 코드 설정(application.yml), 플레이 테스트 후 조정 | DB 튜닝 테이블 없음 |

### S1~S8 (백엔드 스택, 2026-11-02 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| S1 | JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, PostgreSQL 17, Redis 7.4 | - |
| S2 | JPA + QueryDSL(OpenFeign 포크 7.7, KSP). 엔티티가 곧 도메인 모델, `BaseTimeEntity`(`regDttm`/`modDttm`) | 감사 컬럼 명명 유지 |
| S3 | 위치 검색은 위경도 + 하버사인(사각 범위 사전 필터), PostGIS 미사용. 위경도→주소 변환은 네이버 지도 API | 좌표 컬럼 `DOUBLE PRECISION` 유지 |
| S4 | AWS S3 + presigned PUT(4.1 발급 → S3 업로드 → 4.2 메타 확정). 발급 컨텍스트 Redis, HEAD 검증, 고아 객체 수명주기 규칙 | `images` 동일 |
| S5 | STOMP over WebSocket + RabbitMQ STOMP relay. CONNECT 프레임 JWT 인증, SUBSCRIBE는 `chat_participants`로 인가 | - |
| S6 | RabbitMQ: 채팅 relay + 서비스 간 도메인 이벤트(알림 생성, `UserDeleted`, AI 매칭 요청, 모의 입양 푸시 위임 `SimulationPushRequested`(저장 없음, Q481)) | - |
| S7 | OpenSearch 미도입. `keyword` 검색은 `ILIKE`, pg_trgm GIN 인덱스 [가안]. 임베딩 저장은 pgvector 후보(보류) | 인덱스만 |
| S8 | simulation `@Scheduled` 10분 주기 작업 1개. 인스턴스 1개 전제, 다중이면 ShedLock. core·notification 스케줄 없음 | - |

### R1~R16 (v10·v3 2차 검토, 2026-11-02 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| R1 | AI_MATCH 알림 행만 core-api가 `notifications`에 직접 INSERT(쓰기 소유권 예외, Q389·390 유지) | 없음 |
| R2 | 세션 스냅샷 컬럼 `shelter_seq`·`animal_breed_name`·`animal_color_name`·`animal_image_key` 확정 → **Q482로 폐기**(보호동물 연결 없음, `breed_seq` 논리 참조만) | 35번 `simulation_sessions`(컬럼 삭제) |
| R3 | 담당이 아닌 보호소도 발견 신고로 구조 접수 가능 | 없음(BE 제약 없음) |
| R4 | 종료된 구조(DONE·FAILED·CANCELED)는 기존 PATCH에서 `rescue_memo`만 수정, 전용 API 없음 | 없음 |
| R5 | 공고 종료일 수정 허용(기존 PATCH 필드), 전용 API 없음 | 없음 |
| R6 | 반환·입양 없이 ENDED 단독 처리 허용, 사유 선택 | 없음 |
| R7 | 삭제된 댓글은 자리 유지("(삭제된 댓글)"), 삭제된 루트에 새 답글 불가, 기존 답글 유지 | 없음 |
| R8 | USER·SHELTER 모두 채팅방 먼저 생성 가능 | 없음 |
| R9 | **C** 채팅 푸시는 보낸 사람 제외 참여자 전원에게 FCM 데이터 메시지, 표시 판단은 앱. 서버 구독 상태·Redis 키 없음 | 없음 |
| R10 | 담당 발견 신고 목록(API 8.8) `hasRescue` 선택 필터·`activeRescueSeq` 응답. 기본은 전량(**Q464에서 `hasRescue` → 파생 구조 상태 4값으로 대체**) | 없음 |
| R11·R12 | 도메인 재분류: 동물 기준정보 + `districts` → `catalog`, 북마크 → `user`, 보호동물 `animal`. 에러 코드 접두어 `CATALOG_`·`USER_BOOKMARK_` | 없음(패키지·코드 이름) |
| R13 | 발견 신고 등록 응답 `assignmentMethod`는 응답 전용, 컬럼 없음 | 없음 |
| R14 | **B** `districts`를 07번으로 넣고 이후 테이블 전체 재번호(08~45) | 번호만 |
| R15 | 고아 S3 객체 수명주기 1일 | 없음 |
| R16 | **B** 채팅 전송은 STOMP SEND `/app/rooms/{chatRoomSeq}/messages`, REST 전송 삭제, 오류는 `/user/queue/errors`에 같은 봉투 | 없음(27번 `chat_messages` 규칙에 기재) |

### Q430~Q445 (4차, 2026-11-03 확정) 및 문구 통일

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q430 | [확정] `mod_dttm`은 NULL 허용·INSERT 시 NULL·UPDATE 시에만 채움. JPA는 `@UpdateTimestamp` 대신 `@PreUpdate`. 베이스 엔티티를 `BaseCreatedEntity`(`reg_dttm`만)와 `BaseTimeEntity`(`mod_dttm` 추가)로 분리 | 컬럼 변경 없음(§1 매핑 규칙 추가). 코드 수정 |
| Q431 | [확정] 보호소 `district_code`는 오프라인에서 미리 계산해 시드 SQL에 직접 삽입. 시드 이미지는 팀이 S3에 올린 뒤 키를 SQL에 기록. 마이그레이션은 외부 API·S3 호출 없음. 세부 절차는 적재 시점에 논의 | 없음 |
| Q432 | [확정] AI 워커 연동은 양방향 RabbitMQ. 요청 `{sourceType, sourceSeq, imageKeys[], speciesCode, occurredAt}`, 결과 `AiMatchFound {missingReportSeq, targetType, targetSeq, score, modelVersion}`. 워커는 DB 미접근, 임계값은 워커, 자격 재검사·중복 처리는 core-api | 없음(`score`·`modelVersion` 미저장) |
| Q433 | [확정] refresh는 access와 같은 RS256 키로 서명한 JWT(`userSeq`·`jti`·`typ=refresh`), access에 `typ=access`. 검증기는 `typ != access` 거부. "민감한 쓰기 API" 목록 대신 **모든 쓰기 요청(POST·PATCH·DELETE·STOMP SEND)**에서 `users.is_deleted` 재확인, 조회는 1시간 유예 | 없음 |
| 문구 | simulation은 `device_tokens`를 읽지 않음(푸시는 `SimulationPushRequested`로 notification 위임) · 알림 이벤트 2종 구분(`NotificationRequested` 저장+발송 / `NotificationCreated` AI_MATCH 발송만) · 세션 스냅샷은 `animal_image_key`(키) 복사, URL은 응답 변환(Q482로 폐기, 품종 대표 이미지로 대체) · 탈퇴 시 FCM 토큰 비활성화는 `UserDeleted` 이벤트로 notification이 처리(soft) · STOMP SUBSCRIBE 참여자 검증은 chat 모듈 별도 인터셉터 | 없음 |
| Q434 | [확정] A: REST에서 타인·타 보호소·비노출 자원 접근은 403 대신 도메인 `*_NOT_FOUND` 404(존재 감춤). 403은 `IMAGE_NOT_OWNED`·STOMP 참여 권한·역할 불일치만 | 없음(API 코드 정리) |
| Q435 | [확정] 보호소의 모의 입양 열람은 **종료 리포트만**. 세션 상세·행동 이력·지출 내역은 본인 전용이며 종료 시 LLM으로 `summary`에 집약(실패 시 규칙 문구 [가안]). **Q483에서 범위 변경:** "자기 보호동물 세션" 제한 없이 SHELTER 전체 열람 | 44번 규칙(열람·`summary` 생성) |
| Q436 | [확정] 답글 COMMENT 알림은 루트 댓글 작성자만 | 없음 |
| Q437 | [확정] 동물 UNAVAILABLE 시 새 신청과 기존 DRAFT 제출 모두 차단, DRAFT 행 유지 | 24번 규칙 |
| Q438 | [확정] `STAT_WARNING`을 스탯별 7종 `STAT_{HUNGER|HYDRATION|CLEANLINESS|FRIENDSHIP|STRESS|HEALTH|ACTIVITY}_WARNING`으로 세분화(총 10종). 하루 1회 억제는 같은 세션·같은 유형 24시간 내 행 존재로 판정 → **Q481로 폐기**(스탯 경고·방치 알림 없음, 테이블 삭제) | ~~45번 CHECK 10종~~ 삭제 |
| Q439 | [확정] FCM 토큰 등록은 upsert·양도로 충돌 해소, 409 코드 없음 | 34번 규칙 |
| Q440 | [확정] 세션 종료·리포트·`SESSION_END` 알림 INSERT는 한 트랜잭션, MQ 발행만 커밋 후 → **Q481로 조정:** 알림 행이 없어 종료 트랜잭션은 상태·리포트·로그만, 푸시 발행은 커밋 후 | 없음(§8) |
| Q441 | [확정] 이벤트 만료는 조회·스케줄러 양쪽에서 감지, 산책 종료 시 PENDING WALK 이벤트 EXPIRED | §5·43번 규칙 |
| Q442 | [확정] 오류 코드 재검토: 11.4 DONE 아닌 구조 409, 11.8 코드 확정, 10.3 삭제 신고 404·CLOSED 접수 허용, `SIMULATION_ANIMAL_NOT_FOUND` 신설, `CHAT_ROOM_NOT_FOUND`·`CHAT_MESSAGE_NOT_IN_ROOM` 신설, 400↔409 재배정, `CATALOG_INVALID_DISTRICT_CODE` 개명 | §5 구조 규칙 |
| 문구 2차 | §5 구조 연결 선택 · 반환·입양 완료 시 `end_reason`·`protection_ended_dttm` 설정 명시(24번·§8) · `NotificationRequested` 발행 주체에서 chat 제거(§8) · 교차 스키마 읽기 목록에 `core.users`·`simulation_sessions` 추가(§8) | 없음 |
| Q443 | [확정] A: 사진(`images/`)은 버킷 공개 읽기 + 고정 URL `{public-base-url}/{image_key}`(만료 없음), 수료증(`certificates/`)은 비공개 + 상세 조회 시 presigned GET(TTL 10분 [가안]). 업로드는 계속 presigned PUT만 | 없음(14번·24번 규칙에 읽기 방식 기재, 저장 키 그대로) |
| Q444 | [확정] 리포트 `summary`는 비동기 생성: 종료 트랜잭션은 `summary=NULL`로 커밋, 커밋 후 리스너가 LLM 어댑터 호출 → UPDATE. [가안] 타임아웃 20초·재시도 1회, 실패 시 규칙 문구 → **Q487:** `SESSION_END` 푸시는 이 UPDATE 뒤에 발행, 고아 리포트는 스케줄러가 복구 | 44번 규칙(`summary TEXT NULL` 그대로) |
| Q445 | [확정] LOCAL 로그인 연속 실패 제한은 Redis 카운터, [가안] 5회·15분, 429 `AUTH_LOGIN_LOCKED`, DB 컬럼 없음 | 03번 규칙 |

### Q446~Q453 (5차, 2026-11-03 Figma 대조 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q446 | [확정] A: COMMENT 계열 알림은 `target_type/target_seq`=게시물, 댓글 정보는 `notifications.comment_seq`·`parent_comment_seq BIGINT NULL`(FK 없음). 앱은 알림 진입 시 게시물 상세와 댓글 단건 조회(API 13.6)를 함께 호출해 해당 댓글을 목록 최상단에 고정. FCM data 페이로드에 이동 정보 포함 | 33번 컬럼 2개, `ck_notification_comment_ref`, `ix_notification_comment` |
| Q447 | [확정] 알림 유형 5종 → 9종(`COMMENT/COMMENT_REPLY/AI_MATCH/ADOPTION_SUBMITTED/ADOPTION_APPROVED/ADOPTION_REJECTED/ADOPTION_COMPLETED/SHELTER/SYSTEM`). title 비저장(서버 enum 고정 문구), `message`=body 스냅샷(템플릿 [가안]), `actor_user_seq` 삭제 | 33번 `ck_notification_type`, 컬럼 1개 삭제 |
| Q448 | [확정] 댓글 삭제 시 `CommentDeleted` 이벤트 → notification이 관련 COMMENT 계열 알림 `message`를 "삭제된 댓글입니다"로 갱신 | 없음(§8, 인덱스는 Q446) |
| Q449 | [확정] 신고 대상 6종(`SHELTER_ANIMAL` 제외), `reason_detail` 삭제·`reason_code` 재도입(Q308 대체), 대상별 사유 2세트 CHECK, UNIQUE 유지 | 31번 컬럼·CHECK |
| Q450 | [확정] API 본문 없는 성공은 204(Q415의 "204 미사용" 대체). 조회 결과 없음(18.2)은 200 + `data:null` 유지 | 없음 |
| Q451 | [확정] API 3.1 `GET /users/me` USER 전용, `shelterSeq` 제거 | 없음 |
| Q452 | [확정] `pet` 패키지를 `user`로 통합, 경로 `/users/me/pets`·`/users/me/bookmarks`, 코드 `USER_PET_NOT_FOUND`. API 장 번호는 유지 | 없음 |
| Q453 | [확정] API 5장을 "기준정보 (CATALOG)"로 개칭(동물 기준정보 + 행정구역) | 없음 |

### Q454~Q461 (6차, 2026-11-04 Figma·미리보기 대조 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q454 | [확정] 채팅 목록 커서(마지막 메시지 seq), `unreadOnly`, 숨김 이후 메시지 있는 방만 노출, 상대 프로필 사진, `/user/queue/room-updates`(MESSAGE/READ/LEFT) 실시간 갱신, 전체 미읽음 수 API, 연결 생명주기. 미읽음은 조회 시 계산(카운터 미채택) | 26번 규칙, `ix_chat_participant_user`·`ix_chat_participant_shelter` |
| Q455 | [미반영] STOMP 목적지 `.` 구분자 전환. 현행 이름 유지, RabbitMQ relay 연동 시 `/` 동작 테스트 | 없음 |
| Q456 | [확정] 댓글 작성자 `profileImage`, `commentCount`=루트+답글·삭제 제외, 루트 최신순+커서·답글 오래된 순, 깊이 1 유지, 이미지 업로드 순서(4.2는 전송 직전) | 없음(14번 규칙 문구) |
| Q457 | [보류] 업로드 설계를 인프라 티켓(296·297)에 맞추기 | 없음(§9 팀 논의 대기) |
| Q458 | [확정] 보호동물 `weight`·`registration_number`, 입양 문항 20개·`DATE`·`question_seq` 고정 시드, 신청서 고정 필드 4개(A안), 제출은 `animal_seq` 기준 한 트랜잭션, 섹션·placeholder는 프론트 고정 | 20·23·24번 |
| Q459 | [확정] 신청자 신청 내역 `displayDttm` 정렬 고정, `animalSummary.shelterAddress` | 없음 |
| Q460 | [확정] 보호소 입양 상태별 건수 API, 보호소 화면 CANCELED 비노출, 신청번호는 `applicationSeq`, 수료증 기능 제외 | 24번 `certificate_storage_key` 미사용 |
| Q461 | [확정] 구조 사유 코드 4종, 장소·구조 일시 접수 시 필수, 상세 장소 추가, 구조 목록 필터·건수 API, 연결 발견 신고 요약 | 19번 컬럼·CHECK |

> **번호 주의:** API v5(2026-11-03)의 Q454는 "경로 접두어 = 도메인 패키지"(API 전용, DB 영향 없음)였으나 API v6이 v4에서 분기되면서 위 Q454~Q461 번호가 다른 결정에 쓰였습니다. API v7이 v5의 Q454를 복원했고, 두 Q454는 별개 결정입니다. DB 문서에는 영향이 없습니다.

### Q462~Q467 (7차, 2026-11-04 신고·입양·주소 검색 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q462 | [확정] 입양 수료증 기능을 DB에서도 제거. `certificate_storage_key` 컬럼 삭제, `certificates/`·`upload:file:*` 없음 | 24번 컬럼 삭제 |
| Q463 | [확정] 7.1/8.1 검색 조건 재정의: `status`·`occurred_dttm` 날짜 범위·`district_code`·종·`gender`·`is_neutered`·`description ILIKE`·`reg_dttm` 정렬. 품종·색상·보호소 필터 삭제. 포함 매칭(`gender` 필터에 `UNKNOWN`, `is_neutered` 필터에 NULL 포함) | 15·17번 규칙, 인덱스 가안 |
| Q464 | [확정] 7.3/8.3 본인 전량 배열(`reg_dttm DESC`). 8.8은 `status`·최근 7/30/90일(`occurred_dttm`)·`district_code`·종·구조 상태(`rescue_records` 최신 비삭제 행 파생 NONE/PROCESSING/DONE/ABORTED)·`reg_dttm` 정렬·size 8. 앱(날짜 범위)과 웹(프리셋)의 기간 형식 이원화는 의도 | 17번 규칙, 인덱스 가안. 컬럼 추가 없음 |
| Q465 | [확정] `missing_reports.name` 삭제(실종 신고에 동물 이름 없음) | 15번 컬럼 삭제 |
| Q466 | [확정] `missing_reports.pet_seq` 삭제(반려동물 연결·스냅샷·소유자 검증 없음). 반려동물 정보 결합은 앱 표시 | 15번 컬럼 삭제, 12번 규칙, ERD |
| Q467 | [확정] API 5.6 `GET /geocode` 주소 검색 프록시(네이버 Geocoding, 서버 키). 앱이 후보를 골라 좌표를 얻고 등록은 `address`·`latitude`·`longitude` 그대로. 서버 지오코딩·상세 주소 컬럼 없음(대략적 위치만) | 없음 |

### Q468~Q469 (8차, 2026-11-04 신고 목록 필드·몸무게 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q468 | [확정] 7.1 실종 목록에 `age`·`is_neutered`·`weight`, 8.1 발견 목록에 `gender`·`age`·`is_neutered`·`weight`·`reg_dttm` 포함(7.1과 동일 구성). 7.3·8.3·8.8도 같은 목록 필드 | 없음(목록 SELECT 컬럼만, 15·17번 규칙 문구) |
| Q469 | [확정] 몸무게는 네 동물 테이블 모두 `weight VARCHAR(30) NULL` 자유 텍스트. `missing_reports`·`found_reports`에 신설(선택 입력), `shelter_animals`는 Q458의 `NUMERIC(5,2)`에서 타입 변경·CHECK `> 0` 삭제. 앱 숫자 변환 없음, 공공데이터 문자열 그대로 적재 | 15·17번 컬럼 추가, 20번 타입 변경, §7 `ck_shelter_weight_positive` 제거 |

### Q470~Q472 (9차, 2026-11-04 보호소 담당 목록·구조일지 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q470 | [확정] API 8.8 담당 발견 신고 목록 각 항목에 신고자 `reporter{name, phone}`. `name`은 `users.name` **실명**(보호소 화면은 닉네임이 아닌 실명), `phone`은 `reporter_phone`. v14까지의 항목 `reporterPhone`은 `reporter.phone`으로 흡수. 나이·성별·중성화·몸무게는 Q468로 이미 포함 | 없음(17번 목록 SELECT에 `users` JOIN, 17번 규칙 문구) |
| Q471 | [확정] API 10.1 구조 목록: 기간 필터를 날짜 범위에서 최근 7/30/90일 프리셋(**`rescued_dttm` 기준**)으로 교체, size 기본 8, **상태별 건수를 목록 응답에 동봉**(`status` 제외 필터로 `GROUP BY status`, `total`은 합)하고 API 10.7 `/rescues/status-counts` 삭제. 항목에 `rescue_memo`·동물 `is_neutered` 추가. 동물 정보 출처(REPORT=`found_reports`, DIRECT=연결 `shelter_animals` 최초 1건)는 현행 유지 | 없음(19번 규칙 문구, §8 체크리스트. 가안 인덱스 §9) |
| Q472 | [확정] API 10.2 구조 상세의 연결 발견 신고 요약에 `is_neutered` 추가, 신고자를 `users.name` 실명 + `reporter_phone`으로 변경(v14까지 닉네임) | 없음(19번 규칙 문구) |

### Q473~Q475 (10차, 2026-11-04 보호동물 목록·상세 통일 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q473 | [확정] API 11.1 보호동물 목록을 신고 목록(7.1·8.1)과 같은 형식으로 통일. 필터 `protection_status`+`adoption_status` 합성 파생 상태 5값(Q475와 동일 매핑)·`reg_dttm` 날짜 범위·보호소 소재지 `district_code`·종·`gender`·`is_neutered`(Q463 포함 매칭)·`description`/`health_check` ILIKE·`reg_dttm` 정렬. **모든 필터 생략 시 전체**(기본값 없음). 보호소·품종·색상 필터, 단독 `protection_status`·`adoption_status` 필터, `notice_end_date` 정렬 삭제. 항목에 종·품종·색상 이름, `age`·`is_neutered`·`weight`, 두 상태, 공고기간, 보호소 PK·이름·`district_code`, 대표 사진, `reg_dttm` | 없음(20번 규칙 문구, `shelters` JOIN. 가안 인덱스 §9) |
| Q474 | [확정] API 11.2 상세 = 목록 항목 + 상세 전용 컬럼(`rescue_seq`·칩·등록번호·설명·건강·접종·종료 사유·종료 시각·`mod_dttm`) + 구조 LEFT JOIN·사진·북마크·댓글 수·모의 입양 파생(댓글 수는 Q477, 모의 입양 파생은 Q482로 삭제). 분류는 PK와 이름 함께 | 없음(20번 규칙 문구) |
| Q475 | [확정] API 11.3 우리 보호소 목록: 탭은 `protection_status`+`adoption_status` 합성 파생 상태 5값(PROTECTING_AVAILABLE/PROTECTING_UNAVAILABLE/RETURNED/ADOPTED/ENDED, 조합 CHECK가 보장), 필터 최근 7/30/90일(`reg_dttm`)·종·`gender`·`is_neutered`, 정렬 `reg_dttm`, size 8. 상태별 건수는 탭 제외 필터로 `GROUP BY protection_status, adoption_status` 집계해 목록 응답에 동봉, 별도 건수 API 없음 | 없음(20번 규칙 문구, §8 체크리스트. 가안 인덱스 §9) |

### Q476~Q479 (11차, 2026-11-05 보호동물 상세·댓글·상태 관리 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q476 | [확정] API 11.2 보호동물 상세의 보호소 블록을 `shelter_seq`·`shelter_name`·`phone`·`address`로 구성(`shelters` JOIN). 목록 11.1·11.3은 PK·이름·`district_code` 유지 | 없음(20번 조회 규칙 문구) |
| Q477 | [확정] 보호동물 댓글 기능 제거. **30번 `shelter_animal_comments` 삭제**(45→44개), 공통 코드 `SHELTER_ANIMAL_COMMENT` 폐기, `reports.target_type` 5종, COMMENT 계열 알림 `target_type`은 `MISSING`/`FOUND`만. 11.2 댓글 수 제거 | 30번 삭제; 31번·33번 CHECK 축소(`ck_report_target_type`·`ck_report_reason_by_target`·`ck_notification_comment_ref`·`ck_notification_target_type`); §2·§4·§7·§8·§9 |
| Q478 | [확정] 보호소 전용 상세 API 11.10 신설(앱 11.2는 USER 전용). 토큰 보호소 소속 1건 + `animal_returns` LEFT JOIN + 완료 신청서 `applicant_name`·입양 사유/비고 + 신청 상태별 COUNT(DRAFT·CANCELED 제외). 북마크·모의 입양 파생 없음 | 없음(20번·22번·24번 조회 규칙 문구, §8) |
| Q479 | [확정] API 11.6 관리 API 하나로 공고 시작·종료일·보호 상태·입양 가능 여부 변경. **보호 상태는 어느 값에서든 전환 허용**: PROTECTING 복귀 시 `end_reason`·`protection_ended_dttm` NULL(FK·반환 행·사유·비고 유지), ENDED는 `end_reason`, RETURNED는 `animal_returns` INSERT/덮어쓰기, ADOPTED는 `adoption_reason`·`adoption_memo` 저장하고 `completed_application_seq` 불변(채택은 12.11 전용). 종료 전환 시 활성 신청 자동 REJECTED, 복귀해도 되살리지 않음. 등록(11.4) 시 두 공고 날짜·`adoption_status` 입력 가능(기본값 유지). API 11.7·11.8·12.12 삭제, R5·R6 대체 | 20번 `adoption_reason VARCHAR(200) NULL`·`adoption_memo TEXT NULL` 추가; 22번 `mod_usr_seq`·`mod_dttm` 추가; 기존 CHECK 유지; §5·§7·§8 |

### Q481~Q484 (12차, 2026-11-06 모의 입양 재설계 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q481 | [확정] 모의 입양 알림을 저장하지 않음. `simulation_notifications` 삭제, API 18.22·18.23 삭제. 푸시는 FCM만(`EVENT`·`SESSION_END` 2종, title·body 고정 문구, data `{target:SIMULATION}`, 앱을 모의 입양 화면으로 유도하는 용도까지). `NEGLECT`·`STAT_*_WARNING` 폐지, 스탯 저하는 스케줄러의 CONDITION 판정 + 시나리오 발생 확률로 이벤트화. 세션 종료 트랜잭션은 상태·리포트·로그만, 푸시 발행은 커밋 후. M13·M16·Q438·Q440 대체 | **45번 `simulation_notifications` 삭제**(44→43개), `ck_sim_notification_type` 제거; **41번 `trigger_probability NUMERIC(3,2) NOT NULL DEFAULT 1.00`** 추가 + `ck_sim_scenario_probability`; §2·§3·§5·§7·§8·§9 |
| Q482 | [확정] 모의 입양은 보호동물이 아니라 **품종**(`breed_seq`)에서 시작. 종·성격은 품종→종에서 스냅샷, 실제 보호동물·보호소 연결 폐기. 품종 성격은 시드 전량 배정(NOT NULL, 랜덤 없음), 품종마다 대표 이미지 1장(`images` FK, 시드, 참조 정리 대상 아님, 요청으로 받지 않음). API 11.2 `simulation` 블록 삭제, 5.2에 `representativeImageUrl`, 세션·리포트 응답 `animal`→`breed` 블록. `SIMULATION_ANIMAL_*` 오류 코드 삭제, `SIMULATION_BREED_NOT_FOUND` 신설. M1·R2 대체 | **35번** `animal_seq`·`shelter_seq`·`animal_breed_name`·`animal_color_name`·`animal_image_key` 삭제, `breed_seq INTEGER NOT NULL`(논리 참조) 추가; **09번** `personality` NOT NULL, `representative_image_seq BIGINT NULL` FK→images(`fk_animal_breed_image`); §1·§2·§3·§7·§8·§9 |
| Q483 | [확정] SHELTER는 모의 입양 **종료 리포트를 누구 것이든 열람**(세션에 보호소 연결이 없어 소속 제한 없음). API 18.18 보호소 호출 허용(진행 중은 404), 18.20 `user_seq` 필터의 전체 목록, 12.15는 신청서 `user_seq`의 종료 리포트 전체(동물·품종 무관). 진행 중 세션·세션 상세·이력·지출은 본인 전용 유지. M14·M17·Q435 대체 | 없음(44번 열람 규칙 문구, §8). 12.15 파생 조회 JOIN 조건이 `user_seq`+`animal_seq`에서 `user_seq`로 |
| Q484 | [확정] 시작 시 가상 이름 필수(최대 6자, 한글 여부는 프론트 검증, BE는 필수·길이). 변경 API 없음. 세션 상세·목록·리포트 응답에 노출 | **35번** `animal_name VARCHAR(6) NOT NULL` 추가 |

### Q485~Q487 (13차, 2026-11-06 모의 입양 스탯 개명·돌발이벤트 재구성·리포트 푸시 순서 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q485 | [확정] 모의 입양 스탯 7종을 `satiety/hydration/cleanliness/bond/calmness/health/energy`(포만감·수분·청결도·친밀도·안정감·건강·활력)로 개명하고 **모두 높을수록 좋음**으로 통일. `stress`는 부호를 뒤집어 `calmness`로(초기 50·tick -2 [가안], 건강 추가 감소 조건 `calmness<=20`). 100일 때 라벨(배부름·수분 충분·깨끗함·매우 친밀함·편안함·매우 건강함·활기참)은 앱 상수, 서버는 숫자만. 시드 효과·조건식의 `stress` 항목은 부호 반전. M3 스탯 키 대체 | **35번** `hunger`→`satiety`, `friendship`→`bond`, `stress`→`calmness`, `activity`→`energy` 컬럼 개명, `ck_sim_session_stats` 재작성; 36·39·41·44번 JSONB 키 변경(스키마 변경 없음); §3·§7·§8 |
| Q486 | [확정] 돌발이벤트를 **선택지 2개 → 정답 행동 1개**로 재구성. 시나리오마다 정답 행동(`resolve_action_code`: HOME은 돌봄 행동 7종 + `VET_VISIT`, WALK는 `GIVE_WATER`/`GIVE_SNACK`), 해결 비용(병원만), 해결 효과·문구, 방치 효과·문구를 둠. 선택지 응답 API(18.13) 삭제, 이벤트는 돌봄 행동(18.6·18.16) 처리 안에서 `=` 비교로 해결(응답 `resolvedEvent`). 오답은 패널티 없음, 시한(HOME 6시간·WALK 5분 [가안]) 만료 시 방치 효과 적용 후 EXPIRED(산책 종료 시 동일, 세션 종료 시는 효과 없음). 정답은 해결 전 비노출(`VET_VISIT` 이벤트만 진료비 노출), 만료 문구로 정답 안내. `VET_VISIT` 행동 신설(409 `SIMULATION_VET_NOT_NEEDED`), `EVENT_ANSWER` 로그·`SIMULATION_EVENT_*`·`SIMULATION_INVALID_CHOICE` 삭제. M11·M15의 선택지 구조 대체 | **42번 `simulation_event_choices` 삭제**(43→42개), `uq_sim_choice_scenario_no`·`ck_sim_choice` 제거; **41번** `resolve_action_code VARCHAR(30) NOT NULL`·`resolve_cost INTEGER NOT NULL DEFAULT 0`·`resolve_effects_jsonb`·`result_text`·`neglect_effects_jsonb`·`neglect_text` 추가 + `ck_sim_scenario_resolve`·`ck_sim_scenario_cost`; **43번** `status` CHECK `PENDING/RESOLVED/EXPIRED`, `chosen_choice_no`·`answered_dttm` 삭제, `resolved_dttm`·`resolved_action_log_seq`(FK→39번) 추가, `ck_sim_occurrence_status` 재작성; **39번** `action_code` 값(`VET_VISIT` 추가, `EVENT_ANSWER` 삭제); **44번** `event_resolved_count INTEGER NOT NULL DEFAULT 0`; §2·§3·§5·§7·§8·§9·§10 |
| Q487 | [확정] 세션 종료 트랜잭션(포기 API·조회·스케줄러 공통)은 상태·리포트(`summary=NULL`)·로그만 반영하고 응답. 커밋 후 비동기 리스너가 LLM으로 `summary`를 생성해 `UPDATE ... WHERE summary IS NULL`한 **뒤에** `SESSION_END` 푸시 이벤트를 발행(갱신된 경우에만). 실패 시 1회 재시도 후 규칙 문구라 푸시는 반드시 1회, 푸시 유형은 2종 유지. 스케줄러가 `summary IS NULL`·종료 후 10분 [가안] 지난 리포트를 같은 흐름으로 복구. 앱의 "요약 준비 중" 표시(Q444) 유지. Q481·Q444의 푸시 시점 조정 | 없음(44번 규칙·§3 스케줄러·§8 체크리스트 문구) |

### Q488~Q489 (14차, 2026-11-08 모의 입양 마무리: 산책 개 전용·불필요한 병원 방문 409 유지)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q488 | [확정] 산책(API 18.14~18.17)은 세션 종이 `DOG`일 때만 시작 가능. CAT 세션이면 18.14가 409 `SIMULATION_WALK_NOT_FOR_SPECIES`(신설, 종 불일치 = 자원 상태 충돌 Q442·`SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`와 같은 꼴). 앱은 고양이 세션의 산책 버튼을 비활성화하므로 409는 방어용. M12의 "고양이도 산책 가능, 종 배율로 손해"와 코드 설정 종 배율(CAT 안정감 감소 2.0·활력 회복 0.5) 삭제, 산책 효과는 길이·성격 배율만. 고양이 세션의 `walk_count`=0. M12 대체 | 없음(40번 규칙·§3 코드 설정 가안·§8 체크리스트 문구. 종 컬럼을 40번에 두지 않고 35번 `species_type`으로 BE 검증) |
| Q489 | [확정] 정답이 `VET_VISIT`인 PENDING 이벤트가 없을 때의 `VET_VISIT`(API 18.6)은 성공·무변화 응답으로 바꾸지 않고 409 `SIMULATION_VET_NOT_NEEDED` 유지. 앱이 병원 오브젝트를 PENDING `vetVisit`가 있을 때만 켜므로 화면 진입 후 이벤트가 만료된 경쟁 상황에서만 발생하며, 409는 세션 재조회 신호. 200이면 효과 없는 39번 로그 행·`last_action_dttm` 갱신·44번 `action_count` 증가가 생겨 채택 안 함. 확인 결정 | 없음(39번 규칙 문구) |

### Q490~Q499 (15차, 2026-11-09 모의 입양 구현 중 확정)

| 번호 | 결정 | 데이터 모델 영향 |
|---|---|---|
| Q490 | [확정] 성격 배율은 tick당 감소에만(실수 배율, 세션 시작 기준 누적 버림). 돌봄·산책·이벤트 효과에는 곱하지 않음. 회복 배율 3종 삭제. AGGRESSIVE 가중치는 WALK 시나리오 추첨용(252·256). M4 축소 | 없음(§3 코드 설정 가안) |
| Q491 | [확정] 펫샵 분류 14종(기구 `FURNITURE`·`LEASH`·`GROOMING_KIT`, 소모품 `POOP_BAG`·`CATNIP` 추가), 기존 분류 유지. 초기 물품 35개 시드(가격·효과 가안, 이미지 키 비움). M9 확장 | 36번 `ck_sim_item_category`·`ck_sim_item_consumable` 재작성, 시드 |
| Q492 | [확정] 묶음 물품은 1개 구매 = N회 사용. N은 코드 설정(item_seq별, 기본 1), 37번 `quantity` += 구매 수 × N | 없음(코드 설정) |
| Q493 | [확정] `BATHE`는 `GROOMING_KIT` 보유만, 소모 없이 무제한(의도). 샴푸 판매 중단(`is_active=FALSE`). 물 주기 효과 코드 설정 | 36번 시드 데이터(키트 효과, 샴푸 비활성) |
| Q494 | [확정] `PLAY`는 고른 장난감 효과·39번 `item_seq` 기록. CARE `GIVE_CATNIP` 신설(캣닢 1개, +·- 혼합 효과), 직전 캣닢 6시간 [가안] 안이면 남용 효과(안정감 -20 [가안]), 판정은 39번 로그 | 39번 `action_code` 값 추가(BE 검증, DDL 없음), 36번 캣닢 시드 효과 |
| Q495 | [확정] 산책 시작은 `LEASH` 보유 필수(252에서 구현). `POOP_BAG`는 산책용 소모품 | 없음(40번 규칙) |
| Q496 | [확정] 모든 진입점에서 잠금 후 지연 계산 → 건강 0 tick 시각 FAILED / `ends_dttm` COMPLETED. 쓰기 요청 중 종료도 커밋(API 409). 리포트 분류·일차 키 전부 채움, `action_count`=CARE+WALK. 스케줄러 세션별 트랜잭션, `UserDeleted` 큐 소비로 ABANDONED | 없음(44번 규칙·§3 스케줄러) |
| Q497 | [확정] 리포트 요약은 생성기 포트 하나로 두고 지금은 규칙 문구 구현만 사용, LLM은 나중에 같은 포트로 교체(실패·빈 문구면 규칙 문구, Q444 유지). 요약 저장은 생성기 호출과 분리된 `UPDATE ... WHERE summary IS NULL`. 고아 복구 기준은 `reg_dttm` + 10분 [가안](종전 `ended_dttm`) | 없음(44번 규칙 문구) |
| Q498 | [확정] 41번 시나리오 15개 시드(해결 효과에 활동 대가 허용). HOME·RANDOM은 판정마다 확률 0.0104 [가안]·세션 일차당 2회·PENDING 없음·22~8시 제외, 종·성격 맞는 시나리오 가중치 추첨. 만료는 만료 시각까지 스탯 계산 후 방치 효과(건강 0이면 만료 시각 FAILED), 예정 종료 뒤 시한은 효과 없이 만료. 해결·방치 효과 성격 배율 없음. 해결 비용은 38번 EVENT | 41번 시드, 43번 규칙 문구(컬럼 변경 없음) |
| Q499 | [확정] CONDITION은 스케줄러 판정에서만(조회 시 판정 안 함), RANDOM보다 먼저. PENDING 없음·22~8시 제외, 조건·쿨다운(NULL이면 24시간) 맞는 시나리오 가중치 선택 후 그 확률로 생성, 하루 횟수 제한 없음 | 없음(41·43번 규칙 문구) |

### M1~M17 (모의 입양)

| 질문 | 최종 결정 | 현행 데이터 모델 영향 |
|---|---|---|
| M1 | [확정] A: 실제 보호동물에서 시작, 성격 4종을 미리 정해 품종에 매칭 → **Q482로 대체:** 품종에서 시작, 보호동물 연결 없음 | ~~`simulation_sessions.animal_seq`~~ 삭제, `breed_seq` 논리 참조 |
| M2 | [확정] A: 실시간, 1게임일=1실제일, 7일 | `started_dttm/ends_dttm`, 스탯 지연 계산 기준 `stats_calculated_dttm`. tick은 코드 설정(기본 1시간) |
| M3 | [가안] 스탯 7종 0~100, 초기값·tick당 변화는 코드 설정 → **Q485:** 키 개명·모두 높을수록 좋음 | `satiety/hydration/cleanliness/bond/calmness/health/energy SMALLINT` CHECK 0~100 |
| M4 | [가안] 성격 4종 공통, 배율은 코드 상수 | `personality` CHECK `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY`(품종·세션). 배율 테이블 없음 |
| M5 | [확정] 시작 잔액=예상 비용, 펫샵→인벤토리 이동 시 결제, 음수 허용, 기구도 구매 | `expected_cost`, `balance`(CHECK 없음), `simulation_shop_items`, `simulation_inventory_items`, `simulation_transactions` |
| M6 | [확정] A: USER당 진행 중 1개, `IN_PROGRESS/COMPLETED/ABANDONED/FAILED`, 건강 0이면 FAILED, 리포트 영구 보존 | 부분 UNIQUE `(user_seq) WHERE status='IN_PROGRESS'`, `simulation_reports` 1:1 |
| M7 | [확정] 성격은 품종에 저장, 세션에 스냅샷. [가안] NULL이면 랜덤 → **Q482:** NOT NULL, 시드 전량 배정 | `animal_breeds.personality VARCHAR(20) NOT NULL` CHECK 4종. `shelter_animals`에 성격 없음 |
| M8 | [확정] A: `species_code` `DOG/CAT/ETC`, ETC 시작 불가 | `animal_species.species_code VARCHAR(10) NOT NULL UNIQUE` CHECK 3종. `simulation_sessions.species_type` CHECK `DOG/CAT` |
| M9 | [확정] A: 분류 9종, 기구 4종 영구, 소모품 5종, 물 무료 | `simulation_shop_items.category` CHECK 9종, `is_consumable` CHECK 후보 |
| M10 | [확정] 병원은 진단 문구·진료비만 → M15로 구조 확정 | 진단 전용 테이블 없음 |
| M11 | [확정] A: 집 이벤트·산책 행동을 시나리오→선택지 2개→효과 JSON 엔진으로 통합. [가안] 하루 1~2회, 시한 6시간, 산책당 0~3회, 동시 PENDING 1개 → **Q486:** 선택지 2개 대신 **정답 행동 1개**(시한 HOME 6시간·WALK 5분) | `simulation_event_scenarios`(`trigger_context HOME/WALK`, `resolve_action_code`), ~~`simulation_event_choices`~~ 삭제, `simulation_event_occurrences` 부분 UNIQUE PENDING 1건 |
| M12 | [확정] 고양이도 산책 가능, 종 배율로 손해 → **Q488으로 대체:** 산책은 DOG 세션 전용, CAT이면 409, 종 배율 삭제 | `simulation_walks`에 종 컬럼·제약 없음(세션 `species_type`으로 BE 검증). 종 배율 상수 없음 |
| M13 | [확정] 모의 입양 알림 완전 분리 → **Q481로 대체:** 저장 폐지, FCM만 | ~~`simulation_notifications`~~ 삭제. `notifications.notification_type` 변경 없음 |
| M14 | [확정] 리포트는 유저에게 쌓이고 보호소는 열람만, 입양 신청 상세에서 파생 조회 → **Q483:** 신청자의 리포트 전체 | `adoption_applications`와 FK 없음. `user_seq` JOIN |
| M15 | [확정] 아픔은 공통 이벤트(CONDITION), 병원은 선택지, 진단 마스터 없음 → **Q486:** 병원은 정답 행동 `VET_VISIT`(비용 `resolve_cost`) | `trigger_type RANDOM/CONDITION`, `trigger_condition_jsonb`, `cooldown_hours`, `scenarios.result_text`. `simulation_diagnoses`·`diagnosis_seq`·`VET` 거래 없음, `VET_VISIT` 행동은 Q486으로 부활 |
| M16 | [확정] A: 유형 `NEGLECT/STAT_WARNING/EVENT/SESSION_END`, 별도 테이블 + FCM 푸시. [가안] 방치 8시간, 위험 20. Q438에서 10종 → **Q481로 대체:** 저장 없이 `EVENT`·`SESSION_END` 2종 FCM만, 방치·스탯 경고 없음 | 테이블 삭제 |
| M17 | [확정] A: 본인 전체, 보호소는 자기 동물 세션 + 입양 신청 경로, 열람만(ADMIN 전역은 삭제) → **Q483:** 보호소 소속 제한 없이 종료 리포트 전체 | 조회 권한은 BE. `shelter_seq` 삭제 |

## 15. 새 대화·팀원 인수인계

### 프로젝트 목적과 운영 범위
- SSAFY 팀 프로젝트: 유실·발견 신고, 구조·보호동물 등록, 보호자 반환, 입양 신청·최종 입양, AI 이미지 매칭·알림, **모의 입양(품종을 골라 가상 이름을 붙인 동물을 7일 돌보는 체험, Q482·Q484)**을 하나의 플랫폼으로 연결.
- 일반회원 USER는 KAKAO 로그인(Kakao Developers 테스트 앱, 앱 SDK access token을 서버가 검증, 프로필 사진·이메일 미수집), 보호소 담당자 SHELTER는 사전 발급 LOCAL 로그인. 보호소당 SHELTER 계정 1개. ADMIN은 역할 값만 예약하고 이번 범위에서 구현하지 않음.
- 백엔드는 Kotlin + Spring Boot **4개 서비스**(core-api 8080 `/api/core/v1`, chat 8081 `/api/chat/v1`, notification 8082 `/api/notification/v1`, simulation 8083 `/api/simulation/v1`), 공통 라이브러리 `common`. 스택은 S1~S8 확정: JDK 21, Boot 4.1.2, Kotlin 2.3.21, JPA+QueryDSL, PostgreSQL 17, Redis 7.4, RabbitMQ, AWS S3(presigned PUT), OpenSearch 미도입, 하버사인 위치 검색, simulation `@Scheduled`.
- DB는 PostgreSQL **1개, 스키마 2개**(Q420 D): `core`(33개 테이블, core-api·chat·notification 공유, FK 그대로) + `simulation`(9개). 교차 스키마 FK 없음·읽기 JOIN 허용·쓰기는 자기 스키마만. DDL·시드는 Flyway(Q412 A).
- 인증은 RS256 JWT를 core-api만 발급하고 나머지는 공개키 검증. 클레임 `userSeq`·`role`·`shelterSeq`. `user_seq`는 행위자, `shelter_seq`는 보호소 자원 범위. 모든 일반 서비스 API는 로그인 필수. 보호소 업무 기록의 관계와 권한은 서비스 계층에서 검증.

### 반드시 기억할 확정 규칙
- 반려동물/실종/발견/보호동물은 공통 `animal_combinations.combination_seq`를 사용하며 종·품종·색상 직접 컬럼 조합은 사용하지 않음.
- 회원당 비삭제 ACTIVE 실종 신고는 최대 1건, CLOSED 후 신규 신고 가능. 실종 신고에 동물 이름·반려동물 FK 없음(Q465·Q466). 실종↔발견/보호동물 직접 FK 없음. AI 동일 개체 확정 기능 없음. 매칭 알림은 실종 신고자만·쌍당 최초 1회. `notifications`의 `AI_MATCH`+`missing_report_seq/target_type/target_seq`로 BE 중복 조회 및 실종 신고 행 잠금 처리(별도 AI UNIQUE 없음).
- 발견 신고는 구조와 독립이며 구조 접수/완료/신고 삭제로 상태를 자동 종료하거나 구조 이력을 삭제하지 않음.
- 보호동물 1건은 DONE 구조 0~1건을 참조할 수 있고, 한 구조 기록이 여러 보호동물을 생성할 수 있음. 보호소 코드가 서로 달라도 연결 허용. 구조 정보는 보호동물에 복사하지 않고 JOIN 조회, 직접 등록/초기 적재 시 rescue_seq=NULL.
- 반환과 입양 완료는 서로 다른 최종 처리. 반환 보호자 이름/번호 필수, 반환 완료는 실종 신고 종료를 자동 실행하지 않음. **보호 상태·입양 가능 여부·공고기간은 API 11.6 하나로 바꾸며 어느 상태에서든 전환 가능**(Q479): 복귀 시 종료 컬럼만 NULL, 반환 행은 덮어쓰기, 11.6의 ADOPTED는 `completed_application_seq`를 건드리지 않음(채택은 12.11).
- 댓글은 실종·발견 신고 2종에만 있음. 보호동물 댓글 테이블·코드는 v17에서 삭제(Q477). 보호동물 상세는 앱(11.2, USER)과 보호소 웹(11.10, SHELTER)으로 분리(Q478).
- 입양 질문은 공통 현재 20문항(Q458, `question_seq` 1~20 고정)만 유지하며 템플릿/버전·질문 변경 이력 없음. 답변은 `question_seq` 키의 JSONB, 이전 답변도 현행 질문과 함께 표시. 사용자·동물 활성 신청서 1개, 상태 6종(별도 CLOSED 없음). 서류 승인 APPROVED/완료 ADOPTED를 신청서 하나에서 관리하며 완료 FK와 동물 상태를 원자적으로 갱신. 별도 후속 절차·상태 이력 테이블 없음.
- 댓글/채팅 이미지 별도 테이블 없이 각각 `image_seq NULL` FK 사용. 회원·보호소 프로필과 보호소 배경 이미지도 `images` FK. `images.reg_usr_seq`로 업로더를 기록하고 이미지 연결은 본인 것만. `TIMESTAMPTZ` 유지, DB 세션은 `Asia/Seoul`.
- 회원 탈퇴 시 콘텐츠의 원본 행을 일괄 삭제하지 않고 공개 조회/AI에서 비노출. 댓글·채팅·알림·보호소 업무상 과거 입양 신청서 및 완료 기록·모의 입양 리포트는 보존(상태 변경 이력 테이블 없음).
- 모의 입양: `simulation` 스키마의 `simulation_` 9개 테이블. USER당 진행 중 세션 1개, **품종(`breed_seq`, DOG/CAT)을 골라 가상 이름(`animal_name`)을 붙여 시작**하고 종·성격만 세션에 스냅샷(core 참조는 FK 없는 논리 참조, 실제 보호동물·보호소 연결 없음, Q482·Q484). 품종 성격은 시드 전량 배정(NOT NULL), 품종당 대표 이미지 1장(`animal_breeds.representative_image_seq`). 스탯 7종(`satiety/hydration/cleanliness/bond/calmness/health/energy`, 모두 높을수록 좋음, Q485)은 서버가 경과 시간으로 지연 계산. 펫샵→인벤토리 결제·잔액 음수 허용. 이벤트는 시나리오→**정답 행동 1개**→해결/방치 효과 엔진 하나(RANDOM/CONDITION, HOME/WALK, 선택지 테이블·응답 API 없음, 돌봄 행동 처리 안에서 해결, Q486)이며 아픔·병원도 CONDITION 이벤트(정답 `VET_VISIT`)로 처리(진단 테이블 없음), CONDITION은 시나리오 `trigger_probability`로 확률 발생(Q481). 알림은 저장하지 않고 `EVENT`·`SESSION_END` 푸시만 이벤트로 notification에 위임(Q481). SHELTER는 종료 리포트를 누구 것이든 열람(Q483). 튜닝 수치는 코드 설정.
- 행정구역: `district_code`는 법정동 코드 앞 5자리. `districts` 마스터(시드)와 `shelters`·`missing_reports`·`found_reports`의 NULL 허용 컬럼. 서버가 네이버 역지오코딩으로 도출하고 실패하면 NULL, 발견 신고 배정은 매핑 우선·최근접 대체.
- 구조 접수·보호동물 등록은 SHELTER 전용. 신고 작성은 USER만. 초기 데이터(행정구역·보호소·계정·배정·카탈로그·입양 질문·상점·시나리오)는 Flyway 시드로 적재.
- 유저 차단/보호소 이관/ADMIN 기능/신고 내역 조회/입양 이후 사후 관리는 구현하지 않음.
- **Q430~Q445(2026-11-03) 핵심:** `mod_dttm`은 NULL 허용·INSERT 시 NULL·`@PreUpdate`로만 갱신, 베이스 엔티티는 `BaseCreatedEntity`/`BaseTimeEntity` 둘(Q430). 시드의 보호소 `district_code`·이미지 키는 오프라인에서 준비해 SQL에 직접 삽입(Q431). AI 워커 연동은 양방향 RabbitMQ, 워커는 DB 미접근(Q432). refresh는 같은 RS256 키의 JWT(`typ=refresh`), 모든 쓰기 요청에서 `users.is_deleted` 재확인(Q433). REST에서 타인·타 보호소 자원은 403이 아니라 도메인 `*_NOT_FOUND` 404(Q434). 보호소는 모의 입양 **종료 리포트만** 열람하고 세션 상세·이력·지출은 본인 전용(Q435. Q483으로 소속 제한 없이 전체 열람). 답글 알림은 루트 작성자만(Q436). UNAVAILABLE 동물은 DRAFT 제출도 차단(Q437). 모의 입양 알림 유형은 스탯별 경고 7종 포함 10종(Q438. Q481로 저장·경고 알림 폐지). FCM 토큰 충돌 409 없음(Q439). 세션 종료·리포트는 한 트랜잭션, 푸시는 커밋 후(Q440→Q481). 이벤트 만료는 조회·스케줄러 양쪽에서 감지(Q441). 사진은 버킷 공개 읽기 고정 URL, 수료증은 비공개 + presigned GET(Q443). 리포트 `summary`는 커밋 후 비동기 LLM 생성(Q444). LOCAL 로그인 5회 실패 시 15분 차단(Q445, Redis). 알림 이벤트는 `NotificationRequested`(저장+발송, core-api만)와 `NotificationCreated`(AI_MATCH 발송만) 둘.

- **Q446~Q453(2026-11-03 Figma 대조) 핵심:** 알림 유형은 9종이며 title은 서버 enum 고정 문구(저장 없음), `message`는 body 스냅샷, 행위자 컬럼 없음(Q447). COMMENT 계열 알림은 target=게시물 + `comment_seq`·`parent_comment_seq`(Q446), 댓글이 삭제되면 `CommentDeleted` 이벤트로 body를 "삭제된 댓글입니다"로 갱신(Q448). 신고는 대상 6종(`SHELTER_ANIMAL` 제외)·`reason_code` 2세트 선택형, 자유 입력 없음(Q449). API 전용으로 본문 없는 성공 204(Q450), `GET /users/me` USER 전용(Q451), 반려동물·북마크는 `user` 패키지·`/users/me/...` 경로(Q452), API 5장 "기준정보"(Q453).
- **Q454~Q461(2026-11-04) 핵심:** 채팅 미읽음은 읽음·숨김 포인터 이후 + 본인 발신 제외로 조회 시 계산하고 목록은 숨김 이후 메시지가 있는 방만, 실시간은 `/user/queue/room-updates`(Q454). 보호동물 `weight`(kg 숫자, Q469에서 문자열로 변경)·`registration_number`(공식 등록번호), 입양 문항 20개·`DATE`·`question_seq` 1~20 고정, 신청서 고정 필드 4개, 제출은 `animal_seq` 기준 한 트랜잭션(Q458). 수료증 기능 제외(Q460). 구조는 사유 코드 4종·장소·`rescued_dttm`을 접수 시 필수(Q461). 보호동물 상태 변경 방식·공고기간·발생장소·공고번호·업로드 설계는 §9 "팀 논의 대기".
- **Q462~Q467(2026-11-04) 핵심:** 수료증은 컬럼까지 삭제(Q462). 실종 신고에 `name`·`pet_seq` 없음, 반려동물과 FK 없음(Q465·Q466). 신고 목록 필터는 상태·발생 기간·지역·종·성별·중성화·특징 키워드(`description`만)·등록일 정렬이며 성별·중성화 필터는 "모름"을 포함(Q463). 내 신고 목록은 전량 배열, 보호소 담당 목록은 최근 N일·구조 상태(`rescue_records` 파생) 필터(Q464). 신고 위치는 앱이 API 5.6 주소 검색 프록시(네이버 Geocoding, 서버 키)에서 고른 주소·좌표를 보내고 서버는 그대로 저장, 상세 주소 컬럼 없음(Q467).
- **Q468~Q469(2026-11-04) 핵심:** 실종·발견 목록 응답에 나이·중성화·몸무게(발견은 성별·등록일도)를 포함(Q468, 컬럼 변경 없음). 몸무게는 반려동물·실종·발견·보호동물 모두 `weight VARCHAR(30)` 자유 텍스트로 통일: 실종·발견 컬럼 신설, 보호동물은 Q458의 kg 숫자·CHECK를 폐기(Q469).
- **Q470~Q472(2026-11-04) 핵심:** 보호소 화면(8.8 담당 목록·10.2 구조 상세)의 신고자는 `users.name` **실명** + `reporter_phone`(Q470·Q472). 구조 목록(10.1)은 최근 7/30/90일 프리셋(`rescued_dttm` 기준)·size 8이고 **상태별 건수를 `status` 제외 필터의 `GROUP BY status`로 목록 응답에 동봉**하며 10.7 건수 API는 삭제, 항목에 `rescue_memo`·동물 `is_neutered` 포함(Q471). 세 건 모두 **스키마 변경 없음**.
- **Q473~Q475(2026-11-04) 핵심:** 보호동물 목록(11.1)은 신고 목록과 같은 필터(보호+입양 합성 파생 상태 5값, `reg_dttm` 날짜 범위, 보호소 소재지, 종, 성별·중성화 포함 매칭, `description`/`health_check` 키워드, `reg_dttm` 정렬. 모든 필터 생략 시 전체)와 항목(동물 속성·공고기간·보호소 블록)으로 통일(Q473), 상세는 목록 + 상세 전용(Q474). 우리 보호소 목록(11.3)은 `protection_status`+`adoption_status`를 합친 **파생 탭 5값**과 최근 7/30/90일(`reg_dttm`)·종·성별·중성화 필터, size 8, 건수는 탭 제외 필터의 `GROUP BY` 집계로 목록 응답에 동봉(Q475). 세 건 모두 **스키마 변경 없음**.
- **Q476~Q479(2026-11-05) 핵심:** 앱 상세(11.2)의 보호소 블록은 PK·이름·`phone`·`address`(Q476). **보호동물 댓글 제거:** `shelter_animal_comments` 삭제(44개 테이블), 신고 대상 5종, COMMENT 알림 대상 `MISSING`/`FOUND`만(Q477). 보호소 웹 상세는 11.10 전용(반환 정보·입양 정보·신청 건수, Q478). **상태·공고기간 관리 API 11.6**: 어느 상태에서든 전환 허용, PROTECTING 복귀 시 종료 컬럼 NULL, RETURNED는 반환 행 INSERT/덮어쓰기(`mod_usr_seq`·`mod_dttm` 추가), ADOPTED는 `adoption_reason`·`adoption_memo`(신설) 저장·FK 불변, 종료 전환 시 활성 신청 자동 거절, 등록 시 공고 날짜·입양 가능 여부 입력 가능. 11.7·11.8·12.12 삭제(Q479).
- **Q481~Q484(2026-11-06) 핵심:** 모의 입양 알림은 **저장하지 않고 FCM만**(`simulation_notifications` 삭제, 43개 테이블, `EVENT`·`SESSION_END` 2종, 앱을 모의 입양 화면으로 유도하는 용도까지). 방치·스탯 경고 알림은 폐지하고 스탯 저하는 스케줄러가 CONDITION 시나리오를 `trigger_probability`로 확률 판정해 이벤트화(Q481). 모의 입양은 **실제 보호동물이 아니라 품종에서 시작**: `simulation_sessions`는 `breed_seq` 논리 참조 + 종·성격 스냅샷 + 가상 이름 `animal_name VARCHAR(6) NOT NULL`로만 구성하고 `animal_seq`·`shelter_seq`·표시 스냅샷 3컬럼은 삭제, `animal_breeds.personality`는 NOT NULL(시드 전량 배정), `animal_breeds.representative_image_seq` FK→`images`(품종당 1장, 시드). 11.2의 모의 입양 파생 블록 삭제(Q482·Q484). 보호소는 종료 리포트를 **누구 것이든** 열람하고 12.15는 신청서 `user_seq` 기준 전체(Q483).
- **Q485(2026-11-06) 핵심:** 모의 입양 스탯 7종 키를 `satiety/hydration/cleanliness/bond/calmness/health/energy`로 개명하고 **모두 높을수록 좋음**으로 통일(`stress`는 부호 반전해 `calmness`, 초기 50·tick -2). 35번 컬럼 4개 개명·`ck_sim_session_stats` 재작성, 스탯 키를 담는 JSONB와 시드 효과·조건식 부호 반전. 100일 때 라벨은 앱 상수.
- **Q486(2026-11-06) 핵심:** 돌발이벤트는 **선택지 없이 정답 행동 1개**로 해결. `simulation_event_choices` 삭제(42개 테이블), 시나리오에 `resolve_action_code`(HOME: 돌봄 행동 7종 + `VET_VISIT`, WALK: `GIVE_WATER`/`GIVE_SNACK`)·`resolve_cost`·해결/방치 효과·문구. 돌봄 행동 트랜잭션이 PENDING 이벤트와 `=` 비교해 일치하면 효과·비용 반영·RESOLVED(`resolved_action_log_seq`), 오답은 패널티 없음, 시한 만료 시 방치 효과 적용 후 EXPIRED. `VET_VISIT` 행동 신설, `EVENT_ANSWER` 폐기, 리포트 `event_resolved_count`.
- **Q487(2026-11-06) 핵심:** `SESSION_END` 푸시는 종료 커밋 직후가 아니라 **리포트 `summary`가 비동기로 저장된 뒤** 발행(`UPDATE ... WHERE summary IS NULL`이 갱신한 경우에만). 규칙 문구 폴백으로 푸시는 반드시 1회, 고아 리포트(`summary` NULL·종료 10분 경과)는 스케줄러가 복구. 스키마 변경 없음.
- **Q488~Q489(2026-11-08) 핵심:** 산책은 **DOG 세션 전용**(API 18.14에서 세션 `species_type` 검증, CAT이면 409 `SIMULATION_WALK_NOT_FOR_SPECIES`), M12의 고양이 산책·종 배율 삭제, 산책 효과는 길이·성격 배율만(Q488). 정답이 병원인 PENDING 이벤트가 없을 때의 `VET_VISIT`은 성공·무변화 응답이 아니라 409 유지(Q489). 둘 다 스키마 변경 없음.
- **Q490~Q496(2026-11-09) 핵심:** 성격 배율은 시간 감소에만(Q490). 펫샵 분류 14종·물품 35개 시드(Q491, 36번 CHECK 확장이 유일한 스키마 변경), 묶음 물품 사용 횟수는 코드 설정(Q492). 목욕은 위생 관리 키트 보유만·무제한, 샴푸 판매 중단(Q493). 장난감 선택·`GIVE_CATNIP`과 6시간 남용 규칙(Q494). 산책은 리드줄 필수(Q495). 종료는 모든 진입점에서 같은 지연 계산·종료 처리, 리포트 키 전부 채움, `action_count`=CARE+WALK, 스케줄러 세션별 트랜잭션, `UserDeleted` 큐 소비(Q496). 리포트 요약은 규칙 문구 생성기로 먼저, LLM은 나중에 같은 포트로 교체하고 고아 복구는 `reg_dttm` 기준(Q497). 돌발 이벤트는 시나리오 15개 시드와 RANDOM 발생 규칙(확률·일차당 2회·22~8시 제외), 만료 시각 기준 방치 처리(Q498). CONDITION은 스케줄러에서만 RANDOM보다 먼저 판정(Q499).

### 이후 개발 작업 순서 제안 (추가 정책 확정 아님)
- ① `core`·`simulation` 두 스키마의 Flyway 마이그레이션 초안 작성(V1 테이블·제약, V2 시드) 및 시드 검증(행정구역 마스터, 종·품종(성격 전량·대표 이미지)·색상 조합, 구역→보호소 매핑, 보호소 `district_code` 오프라인 계산값(Q431), 상점 물품 `image_key`, 시나리오(`trigger_probability`·정답 행동 `resolve_action_code`·`resolve_cost`·해결/방치 효과·문구, 선택지 없음)). 백엔드 코드의 스키마 4개 초기화 SQL과 yml `default_schema`를 2개 체계로 정리.
- ② FK·부분 UNIQUE·CHECK 제약과 실제 마이그레이션 순서 검증(신규/기존 DB 각각; 모의 입양 9개 테이블 FK 순서(39번↔43번 상호 FK는 생성 후 ALTER), 교차 스키마 읽기 전용 매핑의 `validate` 통과 여부 포함).
- ③ API 명세서(v13)대로 서비스별 경로·봉투·에러 코드 적용, 신고/구조/입양 상태 전이, 모의 입양 세션·행동·이벤트·산책·리포트 흐름, 역할별 조회·탈퇴 콘텐츠 필터 구현.
- ④ RabbitMQ 도메인 이벤트(알림 생성·`UserDeleted`·AI 매칭·모의 입양 푸시)와 STOMP relay 연결. AI 중복 알림 경쟁 조건은 `missing_reports` 행 잠금 및 BE 중복 조회·트랜잭션으로 검증(전용 UNIQUE 없음). 모의 입양 스케줄러는 단일 인스턴스 `@Scheduled`.
- ⑤ 남은 [가안](§9 표)은 값만 조정하며 구조를 바꾸지 않음. 개발 범위를 임의 확대하지 않음.

### 인수인계 시 권장 요청문

> 현재 DB 기준 문서는 `animal_service_db_spec_v20.md`(API `animal_service_api_spec_v14.md`)이고 2026-11-09에 Q490~Q499(모의 입양 구현 중 확정)를 반영했습니다. 아래는 v19 시점 문구이며 새 안건은 Q500부터 번호를 붙입니다. v19 시점: DB 기준 문서는 `animal_service_db_spec_v19.md`이고 2026-11-02에 Q406·Q413~Q429·S1~S8을, 2026-11-03에 R1~R16·Q430~Q445와 Q446~Q453을, 2026-11-04에 Q454~Q461·Q462~Q467·Q468~Q469·Q470~Q472·Q473~Q475를, 2026-11-05에 Q476~Q480을, 2026-11-06에 Q481~Q484와 Q485~Q487을, 2026-11-08에 Q488~Q489를 확정해 반영했습니다(42개 현행 테이블 = `core` 스키마 33개 + `simulation` 스키마 9개. 30번 `shelter_animal_comments`·42번 `simulation_event_choices`·45번 `simulation_notifications`는 삭제된 빈 번호). API 기준 문서는 `animal_service_api_spec_v13.md`입니다(v6은 v4에서 분기돼 v5의 Q454 경로 결정이 빠졌고 Q454~Q461 번호를 다른 결정에 썼으므로, v7이 v5의 Q454를 복원했습니다. 두 Q454는 별개 결정). 기존 v18보다 최신 결정을 우선하십시오. [미정] 항목은 없고 §9의 [가안]은 값 조정만 가능하며 "팀 논의 대기" 표는 다음 버전에서 결정하고, 새 안건은 Q490부터 번호를 붙입니다. 백엔드는 Kotlin + Spring Boot 서비스 4개(core-api·chat·notification·simulation)에 PostgreSQL 1개·스키마 2개입니다. `core`는 세 서비스가 공유하되 테이블마다 쓰는 서비스는 하나이고 FK는 그대로입니다. `simulation`은 교차 스키마 FK 없이 `user_seq`·`breed_seq`를 논리 참조로 두고 교차 읽기 JOIN만 허용합니다(실제 보호동물·보호소와 연결하지 않음, Q482). DDL·시드는 Flyway입니다. 행정구역 코드는 법정동 코드 앞 5자리이며 `districts` 마스터와 `shelters`·`missing_reports`·`found_reports`의 NULL 허용 `district_code`로 관리하고, 서버가 네이버 역지오코딩으로 도출합니다. 알림은 SYSTEM을 포함한 9종(title은 서버 고정 문구로 저장하지 않고 `message`는 body 스냅샷, 행위자 컬럼 없음)이고 AI 매칭 식별은 notifications의 target_type/target_seq를 활용하며, COMMENT 계열은 target=게시물(`MISSING`/`FOUND`) + `comment_seq`·`parent_comment_seq`이고 댓글 삭제 시 body를 "삭제된 댓글입니다"로 갱신하며, 댓글은 실종·발견 2종에만 있고 보호동물 댓글 테이블은 삭제됐으며(Q477), 신고는 대상 5종·`reason_code` 선택형이고, 모의 입양 푸시는 DB에 저장하지 않고 FCM만 보냅니다(Q481). 보호동물의 보호 상태·입양 가능 여부·공고기간은 API 11.6 하나로 바꾸며 어느 상태에서든 전환 가능하고(복귀 시 종료 컬럼 NULL, 반환 행 덮어쓰기, ADOPTED 사유·비고는 `adoption_reason`·`adoption_memo`, 신청서 채택은 12.11 전용), 보호소 웹 상세는 11.10 전용입니다(Q478·Q479). 입양 템플릿·버전·과거 질문 이력은 없으며 question_seq를 답변 JSONB 키로 사용합니다. 구조 정보는 rescue_records에서 JOIN 조회하며 사유는 코드 4종, 장소·구조 일시(`rescued_dttm`)는 접수 시 필수입니다. 채팅 미읽음은 조회 시 계산합니다. 회원당 ACTIVE 실종 신고는 최대 1건이고 CLOSED 신고는 읽기 전용입니다. 회원 프로필 이미지는 `users.profile_image_seq`(images FK)이고 `images.reg_usr_seq`로 업로더를 기록하며, 이미지 업로드는 S3 presigned PUT, 사진 읽기는 버킷 공개 읽기 고정 URL(수료증은 Q460 기능 제외·Q462 컬럼 삭제), 앱은 4.2를 등록·전송 직전에 호출, 원본 삭제는 커밋 후 best-effort입니다. 실종 신고에 동물 이름·반려동물 FK는 없고(Q465·Q466), 신고 목록 필터는 Q463의 8개 조건과 포함 매칭, 보호소 담당 목록의 구조 상태는 `rescue_records`에서 파생합니다(Q464). 신고 목록 응답은 나이·중성화·몸무게를 포함하고(Q468), 몸무게는 반려동물·실종·발견·보호동물 모두 `weight VARCHAR(30)` 자유 텍스트입니다(Q469, 보호동물의 kg 숫자는 폐기). 보호소 화면의 신고자는 `users.name` 실명 + `reporter_phone`이고(Q470·Q472), 구조 목록은 최근 7/30/90일(`rescued_dttm`)·size 8에 상태별 건수를 한 응답에 동봉하며 10.7 건수 API는 없습니다(Q471). 보호동물 목록(11.1)은 신고 목록과 같은 필터·항목 구성(보호+입양 합성 파생 상태 5값, `reg_dttm` 날짜 범위, 보호소 소재지, 종, 성별·중성화 포함 매칭, 특징 키워드. 모든 필터 생략 시 전체)이고(Q473), 우리 보호소 목록(11.3)은 `protection_status`+`adoption_status` 합성 탭 5값·최근 7/30/90일(`reg_dttm`)·size 8에 건수를 동봉합니다(Q475). 신고 위치는 앱이 API 5.6 주소 검색 프록시(네이버 Geocoding)에서 고른 주소·좌표를 보내며 서버는 변환 없이 저장하고 상세 주소 컬럼은 없습니다(Q467). refresh token(같은 RS256 키의 JWT, `typ=refresh`)과 가입 컨텍스트는 Redis TTL 저장이며 DB 테이블이 없고, 모든 쓰기 요청에서 `users.is_deleted`를 재확인합니다. `mod_dttm`은 NULL 허용·INSERT 시 NULL·`@PreUpdate`로만 갱신하며 베이스 엔티티는 `BaseCreatedEntity`/`BaseTimeEntity` 둘입니다. REST에서 타인·타 보호소 자원 접근은 403이 아니라 도메인 `*_NOT_FOUND` 404입니다. 알림 이벤트는 `NotificationRequested`(저장+발송, core-api만)와 `NotificationCreated`(AI_MATCH 발송만) 둘이고 AI 워커 연동은 양방향 RabbitMQ입니다. ADMIN은 `users.role`의 예약값일 뿐 이번 범위에서 구현하지 않습니다(구조 접수·보호동물 등록은 SHELTER 전용, 신고 작성은 USER 전용). 모의 입양은 품종(DOG/CAT)을 골라 가상 이름을 붙여 시작하며(실제 보호동물 연결 없음, 품종 성격 NOT NULL·대표 이미지 FK, Q482·Q484) 실시간 7일, 스탯 7종(`satiety/hydration/cleanliness/bond/calmness/health/energy`, 모두 높을수록 좋음, Q485) 지연 계산, 펫샵·인벤토리·거래, 시나리오→정답 행동 1개 이벤트 엔진(RANDOM/CONDITION, 아픔·병원(`VET_VISIT`) 포함, 선택지 테이블·응답 API 없이 돌봄 행동 처리 안에서 해결하고 시한 만료 시 방치 효과, CONDITION은 `trigger_probability` 확률 발생, Q481·Q486), 산책(DOG 세션 전용, CAT이면 409, 종 배율 없음, Q488), 리포트로 구성되며 튜닝 수치는 코드 설정입니다. 정답이 병원인 PENDING 이벤트가 없을 때의 `VET_VISIT`은 409를 유지합니다(Q489). 보호소는 모의 입양의 종료 리포트를 누구 것이든 열람하고(Q483) 세션 상세·이력·지출은 본인 전용이며, 리포트 `summary`는 커밋 후 비동기 LLM 생성이고 `SESSION_END` 푸시는 그 `summary`가 저장된 뒤에 보내며(Q487), 모의 입양 알림은 저장하지 않고 `EVENT`·`SESSION_END` 푸시만 보냅니다(Q481). 이미지 임베딩 상세, 보호소 이관, 차단, 보호소 삭제 엣지케이스, 신고 내역 조회는 재질문하지 마십시오. 추가 명세 변경은 먼저 질의응답으로 확정하고 사용자가 요청할 때만 새 버전 파일을 생성하십시오.

---
**버전:** v20  |  **기준:** Q406~Q412·Q413~Q419·Q420~Q429·Q430~Q445·Q446~Q453·Q454~Q461·Q462~Q467·Q468~Q469·Q470~Q472·Q473~Q475·Q476~Q480·Q481~Q484·Q485~Q487·Q488~Q489·Q490~Q499·S1~S8 전부 확정(Q455 미반영, Q457 보류) + 모의 입양 M1~M17 확정(M1·M13·M14·M16·M17은 Q481~Q483이, M3 스탯 키는 Q485가, M11·M15의 선택지 구조는 Q486이, M12의 고양이 산책은 Q488이, M4 회복 배율은 Q490이, M9 분류는 Q491이 대체·확장, M3·M4 등 튜닝값은 가안)  |  **현행 테이블:** 42개(`core` 33 + `simulation` 9)  |  **작성 목적:** 전체 테이블 명세 + 개념 제약 SQL + 충돌 처리 + 결정 추적 + 설계 인수인계 일체형
