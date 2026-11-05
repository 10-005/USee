# 유실·발견동물·보호소·입양 플랫폼 API 명세서 — v11

> **작성일:** 2026-11-05  
> **근거:** `animal_service_db_spec_v17.md`(DB 통합 명세 v17, 현행 44개 테이블, 스키마 2개) + v10까지의 확정 사항(Q406·Q413~Q453·R1~R16·S1~S8·v6의 Q454~Q461·Q462~Q475) + **Q476~Q480(2026-11-05 보호동물 상세 보호소 블록·보호동물 댓글 제거·보호소 전용 상세·상태/공고기간 관리 API·댓글/답글 전량 조회, 20.1)** + 백엔드 스켈레톤 코드(`backend/common` 응답·예외·인증 규약). 이전 버전 `animal_service_api_spec_v10.md`는 보존합니다.  
> **버전 계보 주의:** v6은 v5가 아닌 v4에서 분기되어 v5의 Q454(경로 접두어 = 도메인 패키지)가 빠져 있었고, Q454~Q461 번호를 별개 결정(채팅·댓글·입양 신청서·구조일지)에 다시 썼습니다. v7은 v6 위에 v5의 Q454를 복원했으며, **v6의 Q454~Q461은 번호를 유지**합니다. 아래에서 "Q454"는 문맥에 따라 v5의 경로 결정(Q454 경로)과 v6의 채팅 결정(Q454 채팅)으로 구분해 표기합니다. v8은 v7 위에 Q468~Q469를, v9는 v8 위에 Q470~Q472를, v10은 v9 위에 Q473~Q475를, v11은 v10 위에 Q476~Q480을 반영했습니다. 새 안건은 Q481부터 번호를 붙입니다.  
> **문서 목적:** Android·BE·AI·보호소 화면이 공유하는 REST API 계약. 공통 규약(1장)은 코드와 일치하며, 각 엔드포인트의 DTO는 구현 중 조정될 수 있는 [초안]입니다.  
> **백엔드 스택 [확정]:** JDK 21, Spring Boot 4.1.2, Kotlin 2.3.21, JPA + QueryDSL, PostgreSQL 17, Redis 7.4, RabbitMQ(STOMP relay + 서비스 간 이벤트), AWS S3(presigned PUT), 네이버 지도 역지오코딩. OpenSearch는 도입하지 않습니다(S7). 서비스 4개: core-api(8080) / chat(8081) / notification(8082) / simulation(8083).

**표기 규칙**
- **[확정 정책]**: DB v17에서 확인되는 기능·권한·상태·제약, 또는 Q406~Q480·S1~S8 질의응답으로 확정한 사항. **[가안]**: 추천안을 채택했고 수치는 코드 설정값으로 둔 사항(플레이 테스트·운영 후 조정). **[초안]**: DTO 필드 이름·예시 값·세부 검증 등 구현 중 조정 가능한 제안. 확정 정책과 초안이 충돌하면 DB v17과 1장 공통 규약이 우선합니다. **v11에는 [미정] 항목이 없습니다.** 팀 논의 대기 항목(11장 발생장소·공고번호 등)은 20.3에 따로 모았습니다.
- 표의 `/...` 경로는 **해당 장의 서비스 접두어**를 앞에 붙인 상대 경로입니다. core-api `/api/core/v1`(2~13·15·16장), chat `/api/chat/v1`(14장), notification `/api/notification/v1`(17장), simulation `/api/simulation/v1`(18장). **[비인증]** 표시 외에는 로그인 필수입니다.
- 이 문서는 **클라이언트·보호소 업무 API**를 다룹니다. **ADMIN API는 이번 프로젝트 구현 범위 외**(Q410·Q411)이며 별도 장을 두지 않습니다. 내부 AI 워커 및 공공데이터 ETL 전용 API는 다루지 않습니다.

## v11 변경 요약 (v10 → v11)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q476 | **보호동물 상세(11.2) 보호소 블록 교체.** `shelter`를 `{ shelterSeq, shelterName, phone, address }`로 바꿔 상세 화면에서 보호소 연락처·주소를 9.2 추가 호출 없이 표시합니다. 목록(11.1·11.3)의 `shelter{ shelterSeq, shelterName, districtCode }`는 유지 | 11.2 |
| Q477 | **보호동물 댓글 제거.** 댓글은 실종·발견 신고 2종에만 붙습니다. 13장 타입 매핑에서 `SHELTER_ANIMAL`/`SHELTER_ANIMAL_COMMENT` 행 삭제, 11.2 `commentCount` 삭제, 15장 신고 대상 6종→**5종**, `COMMENT`/`COMMENT_REPLY` 알림 대상에서 `SHELTER_ANIMAL` 제외. DB `shelter_animal_comments` 테이블 삭제(44개) | 0.2·1.5·11.2·13장·15.1·17.1·17.8·19장·22장 |
| Q478 | **보호소 전용 보호동물 상세(11.10) 신설.** 앱(USER)용 11.2와 웹(SHELTER)용 `GET /shelter-animals/me/{animalSeq}`를 분리합니다. 11.2는 USER 전용이 되고 `isBookmarked`·`simulation` 블록을 유지, 11.10은 그 대신 관리 정보(`adoptionStatus`·공고기간·`returnInfo`·`adoptionInfo`·`applicationCounts`)를 내립니다. 토큰 보호소 소속이 아니면 404 | 11장 표·11.2·11.10·12.16 |
| Q479 | **보호동물 상태·공고기간 관리 API(11.6) 신설.** `PATCH /shelter-animals/{animalSeq}/management` 하나로 `noticeStartDate`·`noticeEndDate`·`status`(PROTECTING/RETURNED/ADOPTED/ENDED)와 상태별 추가 입력(PROTECTING→`adoptionStatus`, ENDED→`endReason`, RETURNED→`recipientName`·`recipientPhone`, ADOPTED→`reason`·`memo`)을 받습니다. **상태 전환은 어느 상태에서든 허용**(PROTECTING 복귀 포함). 11.6 입양 상태 전환·11.7 반환·11.8 보호 종료·12.12 외부 입양은 이 API로 흡수(삭제). 11.4 등록 요청에 `noticeStartDate`·`noticeEndDate`·`adoptionStatus` 선택 입력 추가, 11.5의 `noticeEndDate`는 11.6으로 이동. 신청서 채택(`completed_application_seq`)은 12.11 전용이며 이 API는 그 값을 건드리지 않음. DB `shelter_animals.adoption_reason`·`adoption_memo` 추가, `animal_returns` 행 덮어쓰기 허용, 오류 코드 `ANIMAL_ALREADY_RETURNED`·`ANIMAL_NOT_PROTECTING` 삭제 | 0.2·1.5·11장 표·11.4·11.5·11.6·12장 표·12.11·12.12·12.14·19장·20.1·20.3·21.3·22장 |
| Q480 | **댓글·답글 전량 조회.** 13.1 루트 댓글과 13.2 답글을 **페이징 없는 배열 응답**으로 바꿉니다. `beforeCommentSeq` 커서·`page`/`size`를 받지 않고 대상 게시물(또는 루트)의 댓글 전량을 내립니다. 정렬은 그대로(루트 `commentSeq desc`, 답글 `regDttm asc`). 1.1 커서 예외에서 13.1을 빼고 7.3·8.3과 같은 배열 예외로 옮김. DB 변경 없음 | 1.1·13.1·13.2·20.1 |

## v10 변경 요약 (v9 → v10)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q473 | **보호동물 목록(11.1)을 신고 목록(7.1·8.1)과 같은 형식으로 통일.** 필터는 `status`(**보호 상태+입양 상태 합성 파생값 5종**, 11.3과 동일)·`regFrom`·`regTo`(**`regDttm` 기준** 날짜 범위, 7.1 규칙)·`districtCode`·`speciesSeq`·`gender`·`isNeutered`·`keyword`(특징 검색 유지)이고, **모든 파라미터는 생략 시 전체**(`status`도 기본값 없음). `shelterSeq`·`breedSeq`·`colorSeq`·`protectionStatus`·`adoptionStatus` 필터와 `noticeEndDate` 정렬은 **삭제**. 정렬은 `regDttm`만. 포함 매칭은 Q463과 동일. 목록 항목은 7.1 구성에 맞춰 `classification`(이름 포함)·`age`·`isNeutered`·`weight`·`noticeStartDate`·`noticeEndDate`·`shelter{shelterSeq, shelterName, districtCode}`·`regDttm`을 추가 | 1.4·11.1 |
| Q474 | **보호동물 상세(11.2) 재정리.** 목록 필드 + 상세 전용 필드 구조로 재작성. `classification`은 1.4 전체형(이름 포함), 8.2처럼 `shelter` 블록, `modDttm` 추가. `rescueInfo`·`simulation` 블록은 유지 | 11.2 |
| 공통 | **목록 쿼리 파라미터는 모두 생략 시 전체.** 7.1·8.1의 `status` 기본 ACTIVE(Q463)와 11.1의 `protectionStatus` 기본 PROTECTING을 폐기해 1.1 규칙("생략 = 조건 없음")과 일치시킴. 8.8·10.1·11.3은 이미 전체가 기본 | 1.1·7.1·8.1·11.1 |
| Q475 | **우리 보호소 목록(11.3) 보호소 웹용으로 재정의.** 탭은 보호 상태와 입양 상태를 합친 **파생 `status` 5값**(`PROTECTING_AVAILABLE`/`PROTECTING_UNAVAILABLE`/`RETURNED`/`ADOPTED`/`ENDED`, 생략 시 전체)이고 그 외 필터는 `period`(`RECENT_7D/30D/90D`, `regDttm` 기준)·`speciesSeq`·`gender`·`isNeutered`·`sort=regDttm`. size 기본 8. 응답은 10.1과 같은 `data:{ statusCounts, page }`로 **상태별 건수를 동봉**(`status` 제외 필터로 집계). 항목은 11.1과 동일 | 1.1·1.4·11.3 |

## v9 변경 요약 (v8 → v9)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q470 | **보호소 담당 목록(8.8)에 신고자 정보.** 각 항목에 `reporter: { name, phone }`를 추가합니다. `name`은 `users.name` **실명**(닉네임 아님, 보호소 현장 대응용)이고, v8까지 항목에 따로 있던 `reporterPhone`은 `reporter.phone`으로 흡수합니다. 8.8이 이미 8.1 목록 필드를 따르므로 나이·성별·중성화·몸무게는 Q468로 포함돼 있음을 재확인 | 8.8·8.3 문구 |
| Q471 | **구조 목록(10.1) 개편.** ① 기간 필터를 날짜 범위(`rescuedFrom`·`rescuedTo`)에서 8.8과 같은 프리셋 `period=RECENT_7D/30D/90D`(**`rescuedDttm` 기준**)로 교체. ② size 기본 **8**(8.8과 같은 1.1 예외). ③ 응답을 `data: { statusCounts, page }`로 바꿔 **상태별 건수를 목록과 한 번에** 내리고, 건수 API **10.7 `GET /rescues/status-counts`는 삭제**. `statusCounts`는 `status`를 제외한 필터(`period`·`speciesSeq`·`source`·`foundReportSeq`)만 적용. ④ 항목 `animal`에 `isNeutered`, 항목에 `rescueMemo` 추가. 동물 정보 출처 정책(REPORT=발견 신고, DIRECT=연결 보호동물)은 유지 | 1.1·1.4·10장 표·10.1·19장·20.1 |
| Q472 | **구조 상세(10.2) 보강.** `foundReportSummary`에 `isNeutered`를 추가하고, `reporter`를 `{ name, phone }`(실명, 8.8과 동일 구성)으로 변경(v8까지 `displayName` 닉네임) | 10.2 |

## v8 변경 요약 (v7 → v8)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q468 | **신고 목록 필드 보강.** 7.1 실종 목록에 `age`·`isNeutered`·`weight` 추가, 8.1 발견 목록에 `gender`·`age`·`isNeutered`·`weight`·`regDttm` 추가(7.1과 동일 구성). 7.3·8.3·8.8은 각각 7.1/8.1 목록 필드를 그대로 따르므로 자동 반영 | 7.1·7.3·8.1·8.3·8.8 |
| Q469 | **몸무게는 전 도메인 자유 텍스트 문자열.** 실종·발견 신고에 `weight`(선택, 최대 30자) 신설(DB 컬럼 추가). 보호동물 `weight`는 v6 Q458의 kg 숫자(`NUMERIC(5,2)`)에서 **반려동물과 같은 `VARCHAR(30)` 문자열로 변경**(CHECK `> 0` 삭제, 앱의 숫자 변환 없음, 공공데이터 "3(Kg)"은 문자열 그대로 적재). 요청(7.4·7.5·8.4·8.5·11.4·11.5)·상세·목록·`AnimalSummary`·10장 요약에 반영 | 6·7·8·10·11·12·22장 |

## v7 변경 요약 (v6 → v7)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q454 경로 복원 | v5의 **경로 접두어 = 도메인 패키지** 결정을 v6 위에 복원. "내 것만" 목록을 각 도메인 경로 아래로: 7.3 `/missing-reports/me`, 8.3 `/found-reports/me`, 8.8 `/found-reports/assigned`, 10.1 `/rescues`, 11.3 `/shelter-animals/me`, 12.2 `/adoption-applications`, 12.9 `/adoption-applications/received`, 18.19 `/simulations/reports`, 18.20 `/simulations/reports/shelter`. v6에서 신설된 10.7·12.16도 같은 규칙으로 `/rescues/status-counts`·`/adoption-applications/received/status-counts`. 1.1 경로 구조 행과 11.3 상세 절 복원. 반려동물·북마크·`/users/me`·`/shelters/me`·18.2·18.3은 유지 | 1.1·7·8·10·11·12·18·19장 |
| Q462 | 입양 수료증 **DB 컬럼까지 삭제**(v6 Q460은 기능만 제외하고 `certificate_storage_key`를 남김). 업로드는 공개 사진 4.1·4.2 한 가지, 19장 `ADOPTION_CERTIFICATE_*` 제거 | 1.4·4장·12장·19장 |
| Q463 | 7.1/8.1 검색 파라미터 재정의: `status`·`occurredFrom`/`occurredTo`(날짜)·`districtCode`·`speciesSeq`·`gender`·`isNeutered`·`keyword`(`description`만)·`sort`(`regDttm`만). `breedSeq`·`colorSeq`·`shelterSeq` 삭제. **포함 매칭:** `gender=MALE/FEMALE`은 `UNKNOWN` 포함, `isNeutered=true/false`는 NULL(모름) 포함 | 1.1·7.1·8.1 |
| Q464 | 7.3/8.3 내 신고 목록은 **파라미터 없음·배열 응답·`regDttm desc` 고정**. 8.8 담당 발견 신고 목록은 `status`·`period`(RECENT_7D/30D/90D)·`districtCode`·`speciesSeq`·`rescueStatus`(NONE/PROCESSING/DONE/ABORTED)·`sort`, size 기본 8. 앱(사용자)과 웹(보호소)의 기간 형식 이원화는 의도된 것 | 7.3·8.3·8.8 |
| Q465 | 실종 신고 `name` 삭제(알림 템플릿 17.1은 이름을 쓰지 않아 영향 없음) | 7.1·7.2·7.4·22장 |
| Q466 | 실종 신고 `petSeq` 연결 삭제(스냅샷·소유자 검증·`USER_PET_NOT_FOUND` 제거) | 6.5·7.2·7.4·7.5·19장·21.1 |
| Q467 | **5.6 `GET /geocode?query=` 주소 검색 프록시 신설**(catalog). 서버가 네이버 Geocoding을 호출해 후보 목록(도로명·지번·위경도)을 반환, 앱은 후보를 골라 좌표를 얻고 등록 요청은 현행 `address + latitude/longitude` 유지. 상세 주소 텍스트는 받지 않음(대략적 위치만). API 키는 서버에만 둠 | 5장·7.4·8.4·19장·21.1 |

## v6 변경 요약 (v4 → v6, v5 미반영)

> v6은 파일 제목이 "v5"로 표기돼 있었으나 저장소의 `animal_service_api_spec_v5.md`(Q454 경로)와는 별개 파일입니다. 아래 Q454~Q461은 v6 고유 번호입니다.

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q454 (채팅) | 채팅 목록 **커서**(`beforeLastMessageSeq`, 마지막 메시지 seq 내림차순), `unreadOnly`, 숨김 이후 메시지 있는 방만 노출, `counterpart.profileImage`, 미리보기 문구는 앱. **14.8 전체 미읽음 수 신설.** `/user/queue/room-updates`(MESSAGE/READ/LEFT) 실시간 갱신, STOMP 연결 생명주기·토큰 만료 처리. 미읽음은 조회 시 계산(본인 발신 제외) | 1.1·14장·21.4 |
| Q455 | STOMP 목적지 `.` 구분자 전환 **미반영**(현행 이름 유지, 연동 시 테스트) | - |
| Q456 | 댓글 `author.profileImage`, `commentCount`=루트+답글·삭제 제외, **루트 최신순 + 커서**(`beforeCommentSeq`)·답글 오래된 순 offset(**페이징은 Q480으로 폐기, 정렬만 유지**), 깊이 1 유지, 이미지 업로드 순서(4.2는 전송 직전) | 1.1·4.1·13장·11.2 |
| Q457 | 업로드 설계를 인프라 티켓에 맞추기 **보류** | 20.2 |
| Q458 | 보호동물 `weight`·`registrationNumber`, 공통 `AnimalSummary`, 입양 문항 20개·`DATE`·`questionSeq` 1~20 고정(섹션·placeholder는 프론트), 신청서 고정 필드 4개, **12.6 `POST /adoption-applications/submit`(animalSeq 기준 저장+제출 한 트랜잭션)**, 저장은 임시저장·프로필 편집에서만 | 11·12장 |
| Q459 | 신청 내역 `displayDttm`(정렬 고정), `AnimalSummary.shelterAddress`, 상세 화면 동작 | 12.2·12.3 |
| Q460 | **12.16 보호소 상태별 건수 신설**, 보호소 화면 DRAFT·CANCELED 비노출, 신청번호=`applicationSeq`, **수료증 기능 제외(4.3 삭제)** | 4장·12장·19장 |
| Q461 | 구조 사유 코드 4종, 장소·구조 일시 접수 시 필수, 상세 장소, 10.1 필터 3종·항목, **10.7 상태별 건수 신설**, 10.2 `modDttm`·`foundReportSummary` | 10장·11.2·21.2 |
| 보류 | 12.12 외부 입양(디자인 없음), 12.15·18장 모의 입양 화면, 11장 보호동물 상태 변경 방식 등 | 20.3 |

## v5 변경 요약 (v4 → v5)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q454 (경로) | **경로 접두어 = 도메인 패키지.** "내 것만" 목록 9개를 `/users/me/...`·`/shelters/me/...`에서 각 도메인 경로 아래로 이동. 세그먼트 부여 규칙을 1.1에 신설. 반려동물·북마크(Q452)·`/users/me`·`/shelters/me`·18.2·18.3은 유지 | 1.1·7·8·10·11·12·18·19장 |
| 문서 정리 | 표에만 있던 7.3·8.3·11.3의 상세 절 신설 | 7.3·8.3·11.3 |

## v4 변경 요약 (v3 → v4)

| 번호 | 결정 | API 반영 |
|---|---|---|
| Q446 | COMMENT 계열 알림의 target은 **게시물**, 댓글 정보는 `commentSeq`·`parentCommentSeq`(DB 컬럼 2개). 댓글 단건 조회 **13.6 신설**. 알림 진입 시 앱이 해당 댓글을 목록 최상단에 고정. FCM data 페이로드 규칙 신설 | 13.3·13.6·17.1·17.8·21.6 |
| Q447 | 알림 유형 5종 → **9종**(`COMMENT/COMMENT_REPLY/AI_MATCH/ADOPTION_SUBMITTED/ADOPTION_APPROVED/ADOPTION_REJECTED/ADOPTION_COMPLETED/SHELTER/SYSTEM`). `title`은 서버 enum 고정 문구(저장 없음), `message`는 body 스냅샷 템플릿 [가안]. `actor` 제거 | 0.2·12장·13.3·17.1·17.8·21.6 |
| Q448 | 댓글 삭제 시 `CommentDeleted` 이벤트 → 관련 알림 body를 "삭제된 댓글입니다"로 갱신 | 13.5·17.1·21.6 |
| Q449 | 신고 대상 6종(`SHELTER_ANIMAL` 제외), `reasonDetail` 삭제·`reasonCode` 선택형 2세트, 조합 불일치 400 `REPORT_REASON_NOT_ALLOWED` | 1.5·15.1·19장 |
| Q450 | **본문 없는 성공은 204**(Q415의 "204 미사용" 대체). 조회 결과 없음(18.2)은 200 + `data:null` 유지 | 0.1·1.2·2.4·2.6·6.5·7.7·8.7·10.6·11.9·12.7·13.5·16.3·17.4·17.5·17.7 |
| Q451 | 3.1 `GET /users/me`는 USER 전용, `shelterSeq` 제거 | 3.1 |
| Q452 | `pet` 패키지를 `user`로 통합, 경로 `/users/me/pets`·`/users/me/bookmarks`, `PET_NOT_FOUND`→`USER_PET_NOT_FOUND`. 장 번호는 유지하고 제목에 (USER) 표기 | 6·16·19·22장 |
| Q453 | 5장을 "기준정보 (CATALOG)"로 개칭(동물 기준정보 5.1~5.4 + 행정구역 5.5) | 5장·19장 |
| 문서 정리 | 22장 `simulation_notifications` 10종 표기, 21.4 번호 중복, 21.5 `summary` 문구 | 21·22장 |

## v3 변경 요약 (v2 → v3)

| 번호 | 결정 | API 반영 |
|---|---|---|
| 구조 | Spring 서비스 4개, 서비스별 경로 접두어·포트. DB는 `core`(core-api·chat·notification 공유) + `simulation` 스키마 2개(Q420 D) | 머리말·1.1·14·17·18장·22장 |
| Q406 A | 실종·발견·보호소에 `districtCode`(법정동 코드 앞 5자리), 서버가 네이버 역지오코딩으로 도출. `districts` 시드 테이블 + `GET /districts`(Q406-1). 실패 시 NULL·최근접 배정·등록 거부 없음(Q406-2) | 5.5·7.1·7.5·8.1·8.4·8.5·9.1·21.1 |
| Q412 | 초기 데이터는 Flyway 시드 SQL(스키마마다 마이그레이션 하나) | 5장·12.1·18장 |
| Q413 A | 앱이 카카오 SDK access token 전달, 서버가 카카오 사용자 조회로 검증, 2단계 가입 유지 | 2.1·2.5 |
| Q414 A | `registrationToken`은 Redis `auth:register:{token}` TTL 10분, 가입 성공 시 삭제 | 2.1·2.5 |
| Q415 A | 봉투 `{status, code, message, data, errors, traceId}`, 204 미사용(**v4 Q450으로 대체: 본문 없는 성공은 204**), 중복 등록 409, 디바이스 토큰 등록만 upsert 200 | 1.2·16.2·17.6 |
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
| 응답 envelope | v3부터 MOTIF 형식 대신 **백엔드 코드의 `ApiResponse`** `{status, code, message, data, errors, traceId}`를 사용 [확정 정책] | 1.2 참조 |
| 각 API의 요청·성공 `data`·실패 분기 | 대표 DTO, 상세 검증, 주요 에러 제시 | 모든 메시지·예시 ID는 가상 값 |
| page/size 규약 | 일반 목록은 offset(`PageResponse`), 채팅 메시지만 `beforeMessageSeq` 커서 [확정 정책] | 1.1·14.4 참조 |
| HTTP 의미 및 오류 코드 분리 | 200/201/204/400/401/403/404/409 구분, **본문 없는 성공은 204**(Q450) [확정 정책] | 1.2 참조 |

### 0.2 DB v17이 API에 강제하는 규칙

1. **USER 한 명당 비삭제 `ACTIVE` 실종 신고는 최대 1건**입니다. 두 신고 모두 `CLOSED → ACTIVE` 재개가 가능합니다. **CLOSED 상태에서는 본문을 수정할 수 없고**(Q425), 수정하려면 먼저 ACTIVE로 재개합니다.
2. 발견 신고는 등록 시 담당 보호소를 자동 배정합니다. 서버가 좌표를 네이버 역지오코딩으로 **시군구 코드(`districtCode`, 법정동 코드 앞 5자리)**로 바꿔 `shelter_district_assignments` 매핑을 먼저 찾고, 없으면 거리상 최근접 보호소를 고릅니다(Q406 A). 역지오코딩 실패 시 `districtCode`는 NULL로 두고 최근접으로 배정하며 **등록을 거부하지 않습니다**(Q406-2). 등록·수정에 `shelterSeq`를 사용자가 지정하지 않고, 수동 재배정 API는 ADMIN 범위 외로 **없습니다**.
3. 구조 기록은 `PROCESSING/DONE/FAILED/CANCELED` 4종이며, **DONE 구조 1건에 보호동물 여러 마리**를 연결할 수 있습니다. 보호동물 구조 정보는 `rescueSeq`를 통해 조회하며 복사 저장하지 않습니다.
4. 보호동물은 **이름 컬럼이 없고** 첫 등록이 `PROTECTING`입니다(입양 가능 여부는 등록 시 선택, 생략하면 `UNAVAILABLE`, Q479). 보호 상태·입양 가능 여부·공고기간은 **관리 API 11.6 하나**로 바꾸며 어느 상태에서든 다른 상태로 전환할 수 있습니다. 반환 완료 시간은 `animal_returns.reg_dttm`(재지정 시 `mod_dttm`)을 사용합니다.
5. 입양 신청은 `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED/ADOPTED` 6종이며, 동일 회원·동물의 비삭제 활성(DRAFT/SUBMITTED/APPROVED) 신청은 **통틀어 1건**입니다. `CLOSED` 상태는 없습니다.
6. 공통 입양 문항은 **현재 20개**(Q458, `questionSeq` 1~20 고정), 답변은 `questionSeq`를 문자열 키로 하는 단일 JSON 객체입니다. 템플릿·버전·질문 이력·제출 당시 문항 스냅샷은 없습니다.
7. 댓글은 **실종·발견 신고 2종에만** 붙습니다(보호동물 댓글은 Q477로 삭제). 댓글·채팅 이미지에는 각각 **`imageSeq` 단일 선택 필드(최대 1장)**를 사용합니다(채팅은 STOMP SEND 본문의 `imageSeq`, R16 B). 댓글의 부모 삭제 후 기존 답글은 유지합니다. 게시물·반려동물·보호동물의 사진 수정은 **`imageSeqs` 전체 최종 배열**로 보내고 서버가 diff와 순서를 계산합니다(Q424).
8. 사용자 차단·신고 심사·신고 내역 조회(Q428)·보호소 이관·입양 후 관리·AI 동일 동물 확정 링크 API와 **ADMIN 전용 API 전체**는 **범위 밖**입니다. 모의 입양은 **범위 안**(18장)입니다.
9. 알림 저장 유형은 `COMMENT/COMMENT_REPLY/AI_MATCH/ADOPTION_SUBMITTED/ADOPTION_APPROVED/ADOPTION_REJECTED/ADOPTION_COMPLETED/SHELTER/SYSTEM` **9종**(Q447)이며, `title`은 유형별 서버 고정 문구(저장 없음), `message`는 body 스냅샷입니다. 채팅 푸시는 DB 알림 기록을 남기지 않습니다. 생성 이벤트는 17.8의 최소 집합(Q426)이며 `SYSTEM`은 유형만 예약하고 이번 범위에서 발송하지 않습니다. 모의 입양 알림은 별도 테이블 `simulation_notifications`와 별도 API(18장)로 분리합니다.
10. `TIMESTAMPTZ`를 유지하고 DB 세션 시간대는 `Asia/Seoul`입니다. API는 `OffsetDateTime`을 ISO-8601 `+09:00`으로 직렬화합니다(Q416).
11. 프로필 이미지는 USER·SHELTER 모두 공통 `images`의 `imageSeq`를 참조합니다. 이미지는 **업로더 본인만** 자기 리소스에 연결할 수 있습니다(Q407·Q408).
12. refresh token은 **Redis TTL**로 관리하며 DB 테이블이 없습니다(Q409). 초기 데이터(보호소·SHELTER 계정·행정구역 마스터·배정·동물 카탈로그·입양 문항·모의 입양 상점/시나리오)는 API로 관리하지 않고 **Flyway 시드 SQL**로 적재합니다(Q412).
13. **DB는 하나, 스키마는 둘**입니다(Q420 D). `core` 스키마를 core-api·chat·notification이 공유하고(FK 그대로, 테이블마다 쓰는 서비스는 하나), 모의 입양 11개 테이블은 `simulation` 스키마에 있으며 core를 **읽기 전용 JOIN**으로만 참조합니다(교차 FK 없음). 세션은 시작 시 동물의 `shelterSeq`·표시 정보를 스냅샷합니다.
14. 신고는 대상 5종(`USER/MISSING/FOUND/MISSING_COMMENT/FOUND_COMMENT`)과 대상별 `reasonCode` 선택형이며 자유 입력이 없습니다(Q449, Q477로 `SHELTER_ANIMAL_COMMENT` 제외).

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
| 페이지네이션 | `page=0&size=20`(0-base, size 기본 20·최대 100). 응답은 1.4의 `Page<T>`. **예외:** 8.8 담당 발견 신고 목록·10.1 구조 목록·11.3 우리 보호소 보호동물 목록은 size 기본 8(Q464·Q471·Q475). 7.3·8.3 내 신고 목록(Q464)과 13.1 루트 댓글·13.2 답글(Q480)은 페이지 없이 배열. 10.1·11.3은 `Page<T>`를 `statusCounts`와 함께 한 겹 감싼 전용 DTO(1.4, Q471·Q475) | [확정 정책] Q417·Q464·Q471·Q475 |
| 정렬 | `sort=필드,asc\|desc`. 엔드포인트마다 **허용 필드 화이트리스트**를 명시하고 기본값은 `regDttm,desc`(동률은 PK desc). 화이트리스트 밖 필드·방향은 400 `COMMON_INVALID_INPUT`. **커서 예외:** 14.4 채팅 메시지, 14.1 채팅방 목록(Q454). 실시간으로 앞쪽에 항목이 끼어드는 목록이라 offset 중복을 막기 위함. 13.1 루트 댓글·13.2 답글은 v11부터 페이징 없이 전량 배열(Q480, 커서 예외에서 제외) | [확정 정책] Q417·Q480 |
| 필드 필터 | 목록마다 허용 필터 파라미터를 명시(상태·종·행정구역·기간 등). 명시되지 않은 파라미터는 무시. **포함 매칭(Q463):** 값이 "모름"일 수 있는 속성(성별 `UNKNOWN`, 중성화 NULL)은 필터 시 모름을 **포함**한다(확실히 아닌 것만 거름). 7.1·8.1 참조 | [확정 정책] Q417·Q463 |
| 경로 구조 | **URL 첫 세그먼트 = 도메인 패키지**(19장). "내 것만" 모아 보는 목록도 해당 도메인 경로 아래에 둔다. **빈 컬렉션 경로는 호출자가 볼 수 있는 전부**를 돌려주며(예: 10.1 `GET /rescues` = 내 보호소 구조 기록), 그 경로가 이미 공개 목록이거나 두 역할이 다른 형태를 볼 때만 세그먼트를 붙인다(`/me` 내 것, `/assigned` 담당 보호소 배정분, `/received` 보호소 수신분, `/shelter` 보호소 열람분). 건수 API는 그 목록 경로 아래 `/status-counts`(12.16. 구조의 10.7은 Q471로 삭제되어 10.1 응답에 `statusCounts`로 포함). 소유자 밖에서 의미가 없는 부속 자원(반려동물·북마크)만 `/users/me/...`, 소유자 자신의 자원은 `/users/me`·`/shelters/me` | [확정 정책] Q454 경로 (v5, v7에서 복원) |
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

- 성공은 `code=SUCCESS`, 실패는 `code`에 19장의 에러 코드. HTTP: 조회/수정 200, 생성 201. **본문 없는 성공(삭제·로그아웃·탈퇴·토큰 비활성화 등)은 `204 No Content`이며 응답 본문(봉투)이 없습니다**(Q450, Q415의 "204 미사용" 대체). 이때 `traceId`는 응답 헤더 `X-Trace-Id`로만 전달됩니다. `ApiResponseBodyAdvice`는 204 응답을 감싸지 않으며, 컨트롤러는 `ResponseEntity.noContent()` 또는 `@ResponseStatus(HttpStatus.NO_CONTENT)` + `Unit` 반환을 씁니다. 조회 결과가 "없음"인 GET(18.2 진행 중 세션 없음)은 204가 아니라 `200 + data:null`입니다. 204 대상은 v4 변경 요약의 Q450 행에 나열한 엔드포인트입니다. 성공 메시지는 기본 "요청이 성공했습니다."이고 API별로 `@SuccessMessage`로 바꿀 수 있습니다.
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

- `Page<T>` **[확정 정책 — 코드 `PageResponse`]**: `{content: T[], page, size, totalElements, totalPages, hasNext}`. `page`는 0부터. **예외(Q471·Q475):** 보호소 웹의 탭형 목록인 구조 목록 10.1과 우리 보호소 보호동물 목록 11.3은 `data: { statusCounts: {...}, page: Page<T> }`로 `Page<T>`를 한 겹 감쌉니다(상태별 건수를 목록과 한 번에 내리기 위한 전용 DTO, `PageResponse` 자체는 변경 없음). 다른 목록은 `data`가 곧 `Page<T>`입니다.
- `ImageRef`: `{imageSeq: number, imageUrl: string|null, displayOrder: number}`. `imageUrl`은 서버가 반환한 접근 URL이며 DB의 `image_key`를 앱이 임의 조합하지 않습니다. **[확정 정책] 읽기 방식(Q443):** 사진(`images/` 접두어)은 버킷 정책으로 **공개 읽기**를 허용한 **고정 URL**(`{public-base-url}/{image_key}`, 만료 없음, 앱 캐시 가능)입니다. 비공개 객체·presigned GET을 쓰던 수료증은 Q460에서 기능 제외, Q462에서 DB 컬럼까지 삭제되어 현재 모든 업로드 파일은 이 공개 사진 방식 하나입니다.
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
| 보호동물 상태·공고기간·입양 가능 여부 변경(11.6) / 입양 심사·채택(12장) | 불가 | 소속 보호소 |
| 보호동물 상세 | 11.2(앱용, 북마크·모의 입양 블록) | 11.10(웹용, 관리 정보·신청 건수. 소속 보호소만) |
| 입양 신청·저장·제출·취소 | **본인만** | 불가(SUBMITTED 이후 업무 열람만, Q422) |
| 모의 입양 세션·돌봄·구매·이벤트·산책 | **본인만** | 불가(자기 보호동물 세션의 **종료 리포트만** 열람, Q435) |
| 댓글 작성(실종·발견 신고만, Q477) | 허용 | 허용 |
| 채팅방 생성·메시지 전송 | 허용(USER↔USER, USER↔SHELTER). 전송은 STOMP SEND(R16 B) | 허용(USER 대상만, SHELTER가 먼저 생성 가능 R8). 전송은 STOMP SEND |
| 댓글 수정/삭제 | 작성자 본인(Q423) | 작성자 본인(Q423) |
| 채팅 메시지 수정/삭제 | 없음(기능 자체 없음) | 없음 |
| 신고 | 허용(회원·실종/발견 게시글·실종/발견 댓글, 15.1). 보호소 계정·보호동물 게시글 신고 불가(Q449), 보호동물 댓글은 기능 자체가 없음(Q477) | 허용(같은 대상) |
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

- **핵심 정합성:** DB v17에서 USER의 `nickname/name/birth_date/phone` 네 필드는 모두 필수 CHECK입니다. 따라서 **카카오 검증 직후 개인정보가 없는 USER 레코드를 미리 INSERT하지 않습니다.** 2.5에서 필수 정보를 받은 뒤 최종 사용자/인증 행을 같은 트랜잭션에서 생성합니다.
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
- `2.4` **요청 [초안]** `{ "refreshToken":"...", "fcmToken":"fcm-device-token" }` → 성공 **204**(본문 없음, Q450). **[확정 정책] 로그아웃 시 FCM(Q419 A):** `fcmToken`은 선택 필드이며 있으면 그 기기의 활성 디바이스 토큰만 비활성화합니다(17.7과 같은 처리). 없으면 토큰은 그대로 둡니다. 탈퇴 시에는 전량 비활성화합니다.
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

**요청 [초안]** 본문 없음, 성공 **204**(본문 없음, Q450).

- **[확정 정책]** core-api 트랜잭션에서 `users`+`user_accounts` 소프트 삭제, 본인의 DRAFT/SUBMITTED/APPROVED 입양 신청을 CANCELED, 실제 ADOPTED 기록 보존, Redis의 refresh 키 전부 삭제. 커밋 후 `UserDeleted` 이벤트를 RabbitMQ로 발행하면 notification이 활성 device_tokens를 비활성화하고 simulation이 진행 중 세션을 ABANDONED 처리해 리포트를 생성·보존합니다(M6). 일반 게시물은 소유자가 탈퇴했으므로 가상 비노출, 댓글·채팅·저장 알림 및 보호소 과거 신청서 보존.
- **[초안]** 탈퇴 전 확인 절차·카카오 재검증은 앱 화면 흐름에서 정합니다. 서버는 재검증을 요구하지 않습니다.

---

## 3. 사용자 프로필 (USER)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 3.1 | GET | `/users/me` | USER | 내 프로필(Q451) |
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
  "accountStatus": "ACTIVE"
}
```
- **[확정 정책] USER 전용(Q451):** SHELTER가 호출하면 403 `AUTH_FORBIDDEN`. SHELTER 계정은 로그인 응답(2.2)과 토큰 클레임의 `userSeq`·`role`·`shelterSeq`로 자신을 식별하고, 보호소 정보는 9.3 `GET /shelters/me`로 조회합니다(운영정보 수정은 9.4). 응답에 `shelterSeq`는 없습니다.
- `profileImage`는 `users.profile_image_seq`를 통해 공통 `images`에서 읽은 `{ "imageSeq":1301, "imageUrl":"https://..." }` 형태이며 없으면 `null`(보호소 9.2의 `profileImage`와 같은 형태, Q408).

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
| 4.3 | - | (삭제) | - | 수료증 기능 제외(Q460), DB 컬럼 삭제(Q462) |

### 4.1 POST /images/upload-urls · 4.2 POST /images

- **[확정 정책]** 게시물/반려동물 사진은 JPG/JPEG/PNG, 각 **최대 10MB**, 게시물별 **1~3장**, 댓글·채팅은 **최대 1장**, 프로필은 **0~1장**. 공통 `images`는 `image_seq/image_key/mime_type/original_file_name/byte_size/reg_usr_seq`를 관리합니다. 동일 이미지를 다수 도메인에서 재참조할 수 있습니다.
- **[확정 정책] 업로더 기록(Q407):** 4.2로 메타데이터를 확정할 때 서버가 `images.reg_usr_seq`에 인증 주체를 기록합니다. 공공데이터·시드 적재분은 NULL입니다. 이후 **모든 이미지 연결 요청**(게시물·반려동물 `imageSeqs`, 댓글·채팅 `imageSeq`, 프로필·보호소 `profileImageSeq/backgroundImageSeq`)은 `reg_usr_seq`가 요청자와 같은 이미지만 허용하고, 다르면 403 `IMAGE_NOT_OWNED`. 같은 사용자의 반려동물 사진→실종 신고 재사용은 그대로 가능합니다. ADMIN 전역 예외는 없습니다.
- **[확정 정책] 업로드 순서(Q456):** 앱은 사진을 고르면 **4.1 발급 → S3 PUT까지 바로** 수행(미리보기·속도), **보내기·등록을 누를 때 4.2로 확정**하고 받은 `imageSeq`로 연결 API(댓글 13.3, 게시글, 반려동물, 채팅 SEND 등)를 호출합니다. 사용자가 사진을 지우거나 작성을 취소하면 4.2를 호출하지 않으므로 객체는 수명주기 규칙으로 자동 정리되고, 어디에도 연결되지 않은 `images` 행이 남지 않습니다.
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

### 4.3 (삭제, Q460·Q462)

v4의 `POST /files/upload-urls`(입양 수료증 비공개 업로드)는 **수료증 기능 제외(Q460)**로 삭제했습니다. 디자인의 신청서 작성·보호소 상세 어디에도 수료증 첨부·열람이 없습니다. **Q462:** v6에서 "컬럼만 남김"이던 DB `adoption_applications.certificate_storage_key`도 **삭제**했습니다(DB v13 이후). S3 `certificates/` 접두어, Redis `upload:file:*` 컨텍스트, 19장 `ADOPTION_CERTIFICATE_*` 코드도 모두 없습니다. 이로써 업로드는 공개 사진(4.1·4.2) 한 가지 방식만 남습니다. 인프라 티켓(`public/tmp/` → `public/` 이동, CloudFront URL)과의 정렬은 Q457로 **보류**(20.3)입니다.

---

## 5. 기준정보 (CATALOG)

> **[확정 정책] Q453:** 이 장은 `catalog` 패키지의 기준정보 전체입니다. **동물 기준정보**(5.1~5.4: 종·품종·색상·조합)와 **행정구역**(5.5: `districts`)으로 나뉘며, 모두 Flyway 시드로 적재하고 관리 API가 없습니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 5.1 | GET | `/animal-species` | 로그인 | 종 전체 목록 |
| 5.2 | GET | `/animal-species/{speciesSeq}/breeds` | 로그인 | 종의 품종 목록 |
| 5.3 | GET | `/animal-colors` | 로그인 | 색상 기준 목록 |
| 5.4 | GET | `/animal-combinations` | 로그인 | 유효 품종·색상 조합 조회 (동물 기준정보) |
| 5.5 | GET | `/districts` | 로그인 | 시·도/시·군·구 행정구역 마스터(Q406-1) |
| 5.6 | GET | `/geocode` | 로그인 | 주소 검색 프록시: 주소 텍스트 → 후보(주소·위경도) 목록(Q467) |

### 5.1~5.5 조회 규약

- `5.1` 성공 `data:[{"speciesSeq":1,"speciesCode":"DOG","speciesName":"개"}, ...]`. **[확정 정책]** `speciesCode`는 `DOG/CAT/ETC`(M8). 모의 입양은 `DOG/CAT`만 시작 가능합니다.
- `5.2` 성공 `data:[{"breedSeq":21,"speciesSeq":1,"breedName":"말티즈","personality":"FRIENDLY"}, ...]`. **[확정 정책]** `personality`는 `ACTIVE/TIMID/AGGRESSIVE/FRIENDLY` 또는 `null`(M7). 모의 입양 시작 시 이 값을 세션에 스냅샷합니다.
- `5.3` 성공 `data:[{"colorSeq":3,"colorName":"흰색"}, ...]`.
- `5.4` 요청 `?breedSeq=21&colorSeq=3`(각 필터 선택) → `data:[{"combinationSeq":88,"speciesSeq":1,"breedSeq":21,"colorSeq":3, ...}]`.
- `5.5` 요청 `?sidoName=대구광역시`(선택) → 성공 `data:[{"districtCode":"27110","sidoName":"대구광역시","sigunguName":"중구"}, ...]`. **[확정 정책]** `districtCode`는 법정동 코드 앞 5자리(시·군·구)이며 `districts` 시드 테이블에서 읽습니다. 7.1·8.1·9.1의 `districtCode` 필터 값과 신고·보호소 응답의 `districtCode`가 이 목록의 값입니다. 앱의 지역 선택 UI는 이 API로 시/도 → 시/군/구 목록을 구성합니다.
- **[확정 정책]** 네 동물 엔터티는 모두 `combinationSeq` 단일 FK를 저장합니다. 종은 품종에서 결정되고 `UNIQUE(breed_seq,color_seq)`입니다. 실제 마스터 값은 시드 데이터로 팀이 정합니다.
- **[확정 정책]** 기준정보는 비페이지 전량(수량 증가 시 서버 페이징 재검토). 기준정보 수정·시드 관리 API는 **없으며** Flyway 시드 SQL로 적재합니다(Q412).

### 5.6 GET /geocode

**요청 [초안]** `?query=대구광역시 중구 동성로 1`(필수, 공백 제거 후 1자 이상, 최대 100자 [가안]).

**성공 200 `data:[...]` [초안]**
```json
[
  {
    "roadAddress": "대구광역시 중구 동성로 1",
    "jibunAddress": "대구광역시 중구 동성로2가 1",
    "latitude": 35.8714,
    "longitude": 128.6014
  }
]
```

- **[확정 정책] 용도(Q467):** 실종(7.4·7.5)·발견(8.4·8.5) 신고의 위치 입력용입니다. 앱이 사용자가 친 주소 텍스트로 이 API를 호출해 후보를 보여 주고, 사용자가 고른 후보의 주소와 위경도를 등록 요청의 `address`·`latitude`·`longitude`에 그대로 넣습니다. **서버는 등록 시점에 주소를 좌표로 바꾸지 않고**, 상세 주소(동·호수, "○○ 앞") 텍스트 필드도 받지 않습니다. 대략적인 위치만 저장합니다.
- **[확정 정책] 구현:** core-api `catalog` 패키지의 `port/out` 어댑터가 **네이버 클라우드 Maps Geocoding API**를 호출합니다(역지오코딩과 같은 NCP 키, 같은 어댑터 자리). API 키가 앱에 들어가지 않도록 **앱은 네이버를 직접 호출하지 않습니다**. 결과는 네이버 응답의 `roadAddress`·`jibunAddress`·`x`(경도)·`y`(위도)를 그대로 옮기며 DB 저장·캐시는 없습니다. 후보 수는 네이버 기본값(최대 10 [가안]), 타임아웃 3초 [가안].
- 결과가 0건이면 **빈 배열 200**(오류 아님). 앱은 "검색 결과가 없습니다"를 띄우고 다른 표현으로 재검색을 유도합니다. `address`는 `roadAddress`가 있으면 그것, 없으면 `jibunAddress`를 쓰는 것을 권장합니다 [초안].
- 로그인 필수이며 역할 제한 없음(USER·SHELTER). 호출 빈도 제한은 두지 않습니다 [가안: 남용 시 Redis 카운터 추가].
- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(`query` 누락·공백·길이 초과), 503 `CATALOG_GEOCODE_UNAVAILABLE`(네이버 호출 실패·타임아웃·인증 오류).

---

## 6. 내 반려동물 (USER)

> **[확정 정책] Q452:** 반려동물은 `user` 패키지에 속하고 경로는 `/users/me/pets`입니다. 에러 코드 접두어도 `USER_`(`USER_PET_NOT_FOUND`)입니다. 장 번호는 유지합니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 6.1 | GET | `/users/me/pets` | USER | 내 반려동물 목록 |
| 6.2 | GET | `/users/me/pets/{petSeq}` | 소유 USER | 내 반려동물 상세 |
| 6.3 | POST | `/users/me/pets` | USER | 반려동물 등록 |
| 6.4 | PATCH | `/users/me/pets/{petSeq}` | 소유 USER | 반려동물 수정 |
| 6.5 | DELETE | `/users/me/pets/{petSeq}` | 소유 USER | 반려동물 소프트 삭제 |

### 6.1 GET /users/me/pets · 6.2 GET /users/me/pets/{petSeq}

**요청 [초안]** `GET /users/me/pets?page=0&size=20&sort=regDttm,desc`. 정렬 화이트리스트 `regDttm`, `name`. 필터 없음(본인 소유 전량).

**목록 200 `data.content[]` [초안]** `{ "petSeq":5, "name":"초코", "classification":{"combinationSeq":88,"speciesName":"개","breedName":"말티즈","colorName":"흰색"}, "gender":"MALE", "representativeImageUrl":"https://...", "regDttm":"..." }`.

**상세 200 `data` [초안]** 목록 필드에 `age`, `weight`, `isNeutered`, `microchipNumber`, `description`, `images:[ImageRef]`, `modDttm`을 추가. 비삭제 소유자만 조회.

### 6.3 POST /users/me/pets

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

### 6.4 PATCH /users/me/pets/{petSeq} · 6.5 DELETE /users/me/pets/{petSeq}

- `6.4` **요청 [초안]** 6.3의 수정할 필드만 보냄; 이미지 변경 시 `imageSeqs` **전체 최종 배열**을 원하는 순서대로 전송하고 서버가 diff·순서를 계산합니다(Q424, 4.1). 성공 200 상세 DTO. `pets.mod_dttm`만 갱신(제거된 `mod_usr_seq`를 되살리지 않음). 소유자 본인만(Q423).
- `6.5` 성공 **204**(본문 없음, Q450). 실종 신고는 반려동물과 연결되지 않으므로(Q466) 삭제가 신고·AI 매칭에 영향을 주지 않습니다. 이미지 참조도 다른 도메인에서 참조 중이면 원본 삭제 금지.
- **오류 [초안]** 404 `USER_PET_NOT_FOUND`(없음·삭제·타인 소유 모두, Q434); 6.4는 추가로 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`.

---

## 7. 실종 신고 (MISSING)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 7.1 | GET | `/missing-reports` | 로그인 | 실종 신고 검색/목록 |
| 7.2 | GET | `/missing-reports/{missingReportSeq}` | 로그인 | 상세/연락처 가시성 |
| 7.3 | GET | `/missing-reports/me` | USER | 내 실종 신고 목록/상태 (전량, 배열) |
| 7.4 | POST | `/missing-reports` | USER | 실종 신고 등록 |
| 7.5 | PATCH | `/missing-reports/{missingReportSeq}` | 작성자 | 신고 내용 수정 |
| 7.6 | PATCH | `/missing-reports/{missingReportSeq}/status` | 작성자 | ACTIVE↔CLOSED 변경 |
| 7.7 | DELETE | `/missing-reports/{missingReportSeq}` | 작성자 | 실종 신고 소프트 삭제 |

### 7.1 GET /missing-reports · 7.2 GET /missing-reports/{id}

**검색 파라미터 [확정 정책] (Q463)** `?status=ACTIVE&occurredFrom=2026-10-01&occurredTo=2026-10-31&districtCode=27110&speciesSeq=1&gender=MALE&isNeutered=true&keyword=목줄&page=0&size=20&sort=regDttm,desc`.
- 필터(모두 선택, **생략 시 그 조건 없이 전체**): `status`(`ACTIVE`/`CLOSED`. v9까지의 기본 ACTIVE는 v10에서 폐기, 생략 시 CLOSED 포함 전체), `occurredFrom`·`occurredTo`(`YYYY-MM-DD`, **`occurredDttm` 기준**, KST 일 단위 양끝 포함, 한쪽만 보내도 됨, `from > to`면 400), `districtCode`(5.5 값, Q406 A), `speciesSeq`(서버는 `combinationSeq→animal_breeds→animal_species`로 종을 찾음), `gender`, `isNeutered`, `keyword`. v6까지의 `breedSeq`·`colorSeq`는 **삭제**(보내도 1.1 규칙에 따라 무시).
- **[확정 정책] 포함 매칭(Q463):** 확실히 아닌 것만 거릅니다. `gender=MALE`은 `MALE`+`UNKNOWN`, `gender=FEMALE`은 `FEMALE`+`UNKNOWN`, `gender=UNKNOWN`은 `UNKNOWN`만. `isNeutered=true`는 TRUE+NULL(모름), `isNeutered=false`는 FALSE+NULL. 생략하면 그 속성은 조건 없음. "모름만" 고르는 필터값은 없습니다.
- `keyword`는 **`description`에만** `ILIKE '%키워드%'`(S7, pg_trgm 인덱스 [가안]; v6까지의 `address` 검색은 제외). 비노출 필터는 같은 쿼리에서 적용합니다.
- 정렬 화이트리스트: **`regDttm`만**. 생략 시 `regDttm,desc`(최신순), `sort=regDttm,asc`(오래된순). 다른 필드는 400 `COMMON_INVALID_INPUT`. 위치 반경 검색·거리순은 이번 범위에 없습니다.

**목록 200 `data.content[]` [초안]**
```json
{
  "missingReportSeq": 101,
  "classification": { "combinationSeq":88, "speciesName":"개", "breedName":"말티즈", "colorName":"흰색" },
  "gender": "MALE",
  "age": "3살",
  "isNeutered": true,
  "weight": "4.5kg",
  "status": "ACTIVE",
  "address": "대구광역시 ...",
  "districtCode": "27110",
  "occurredDttm": "2026-10-28T18:00:00+09:00",
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00"
}
```
- **[확정 정책] (Q468·Q469)** 목록에도 동물 속성 `age`·`isNeutered`·`weight`를 내려 카드에서 바로 표시합니다. 세 값 모두 null 가능(`isNeutered:null`=모름). `weight`는 반려동물(6장)과 같은 **자유 텍스트**("4.5kg")이며 서버가 숫자로 해석하지 않습니다.

**상세 200 `data` [초안]** 목록 필드 + `user:{ActorSummary}`, `microchipNumber`, `description`, `latitude`, `longitude`, `images:[ImageRef]`, `isPhonePublic`, `contactPhone`, `isBookmarked`, `commentCount`, `modDttm`.

- **[확정 정책]** 공개 연락처는 **로그인 사용자에게만** 보이며, 기본 비공개 시에는 **작성자만** 실제 번호를 열람합니다. 접근 불가 시 `contactPhone:null`로 응답 [초안]. `isPhonePublic` 자체는 반환 가능.
- **[확정 정책]** 탈퇴 작성자·삭제 신고는 일반 목록/상세에서 비노출. `circumstances` 필드는 존재하지 않습니다. **실종 신고에 `name`(동물 이름)과 `petSeq`(반려동물 연결)는 없습니다**(Q465·Q466). 반려동물 정보와 이어 붙이는 표시는 앱이 6장 데이터로 처리합니다.
- **오류 [초안]** 7.2: 404 `MISSING_REPORT_NOT_FOUND`(없음·삭제·비노출). 7.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`(정렬 화이트리스트 밖, 날짜 형식·역전, 허용값 밖 `gender`·`isNeutered`).

### 7.3 GET /missing-reports/me

**요청 [확정 정책] (Q464)** 파라미터 없음(보내도 무시). 범위는 토큰 `userSeq`가 작성한 **비삭제** 신고 전체(ACTIVE·CLOSED 모두). 회원당 ACTIVE 1건 정책상 건수가 적어 페이지네이션·필터·정렬 파라미터를 두지 않습니다.

**성공 200 `data:[...]` [초안]** `Page<T>`가 아닌 **배열**. 각 항목은 7.1 목록 필드와 동일하며 **`regDttm desc` 고정**(동률은 PK desc). 신고가 없으면 빈 배열.

### 7.4 POST /missing-reports

**요청 [초안]**
```json
{
  "combinationSeq": 88,
  "gender": "MALE",
  "age": "3살",
  "weight": "4.5kg",
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

- **[확정 정책] (Q465·Q466)** 요청에 `name`·`petSeq`가 없습니다. 반려동물 등록 여부와 무관하게 동물 속성(조합·성별·나이·몸무게·중성화·칩 번호·특징)을 직접 입력합니다. 앱이 등록 반려동물의 값을 폼에 미리 채워 주는 것은 앱 동작이며 서버는 연결을 저장하지 않습니다.
- **[확정 정책] 몸무게(Q469):** `weight`는 **선택, 자유 텍스트 최대 30자**(예: "4.5kg", "약 5kg"). 6.3 반려동물과 같은 형식이며 서버 검증은 길이뿐입니다. 비우면 null.
- 실종 주소/좌표/시각/연락처/유효 조합/성별/사진 1~3장을 검증합니다. `contactPhone`은 회원 전화번호로 초기 제안하되 신고별로 수정 가능. **USER만 작성**(Q421).
- **[확정 정책] 주소·좌표(Q467):** `address`·`latitude`·`longitude`는 앱이 5.6 주소 검색 프록시의 후보에서 고른 값(또는 지도 핀)을 그대로 보냅니다. 서버가 주소를 좌표로 바꾸지 않으며, 상세 주소 텍스트 필드는 받지 않습니다(대략적 위치만).
- **[확정 정책] 행정구역 코드(Q406 A):** 서버가 좌표를 네이버 역지오코딩(`legalcode`)으로 조회해 앞 5자리를 `districtCode`에 저장합니다. 실패하면 `null`로 저장하고 등록은 그대로 진행합니다(Q406-2). 앱이 코드를 보내지 않습니다.
- **[확정 정책]** 작성 USER당 비삭제 ACTIVE 실종 신고 최대 1건. 최초 등록/재개 시 유효한 발견 신고·보호동물과 AI 매칭 후보. 서버 트랜잭션과 부분 UNIQUE로 경합 차단.
- **오류 [초안]** 409 `MISSING_ACTIVE_ALREADY_EXISTS`; 404 `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`.

### 7.5 PATCH /missing-reports/{id} · 7.6 PATCH /.../status · 7.7 DELETE /...

- `7.5` **요청 [초안]** 변경 가능한 7.4 필드 일부(`weight` 포함, 자유 텍스트 Q469) + `imageSeqs`(전체 최종 배열, Q424). 상태는 별도 7.6 호출. 성공 200은 7.2 상세 DTO(6.4와 같은 규칙). 좌표·주소를 바꾸면 `districtCode`를 **재도출**합니다. 위치 수정은 **발견 신고와 달리 자동 보호소 배정 대상 자체가 아닙니다.** 이미지 배열에 diff가 있으면 AI 재매칭 요청. **[확정 정책] CLOSED는 읽기 전용(Q425 B):** `status=CLOSED`인 신고에 7.5를 호출하면 409 `MISSING_REPORT_CLOSED`이며, 고치려면 먼저 7.6으로 ACTIVE 재개합니다. 작성자 본인만(Q423).
- `7.6` **요청 [초안]** `{ "status":"CLOSED" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{missingReportSeq,status}`. ACTIVE 재개 시 다른 ACTIVE 신고가 있으면 409 `MISSING_ACTIVE_ALREADY_EXISTS`. 실종 신고의 종료 사유/종료 시각 API 필드는 **없습니다**. 작성자 본인만.
- `7.7` 성공 **204**(본문 없음, Q450). [확정 정책] 논리 삭제(ACTIVE·CLOSED 모두 가능); AI 대상 제외, 사진은 마지막 참조 해제 시만 정리. 작성자 본인만.
- 신고/보호동물 반환, 발견/구조 완료를 이유로 실종 신고가 자동 종료되지는 않습니다.
- **오류 [초안]** 7.5~7.7 공통 404 `MISSING_REPORT_NOT_FOUND`(없음·삭제·**타인 신고**, Q434). 7.5: 409 `MISSING_REPORT_CLOSED`, 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`. 7.6: 409 `MISSING_ACTIVE_ALREADY_EXISTS`(재개), 400 `COMMON_INVALID_INPUT`(현재와 같은 상태).

---

## 8. 발견 신고 (FOUND)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 8.1 | GET | `/found-reports` | 로그인 | 발견 신고 목록/검색 |
| 8.2 | GET | `/found-reports/{foundReportSeq}` | 로그인 | 발견 신고 상세 |
| 8.3 | GET | `/found-reports/me` | USER | 내 발견 신고 목록 (전량, 배열) |
| 8.4 | POST | `/found-reports` | USER | 발견 신고 등록·보호소 자동 배정 |
| 8.5 | PATCH | `/found-reports/{foundReportSeq}` | 작성자 | 발견 신고 내용 수정 |
| 8.6 | PATCH | `/found-reports/{foundReportSeq}/status` | 작성자/담당 SHELTER | ACTIVE↔CLOSED |
| 8.7 | DELETE | `/found-reports/{foundReportSeq}` | 작성자 | 발견 신고 소프트 삭제 |
| 8.8 | GET | `/found-reports/assigned` | SHELTER | 내 보호소 담당 발견 신고 목록 |

### 8.1 GET /found-reports · 8.2 GET /found-reports/{id}

**검색 [확정 정책] (Q463)** `?status=ACTIVE&occurredFrom=2026-10-01&occurredTo=2026-10-31&districtCode=27110&speciesSeq=1&gender=UNKNOWN&isNeutered=false&keyword=목줄&page=0&size=20&sort=regDttm,desc`.
- 필터(모두 선택, **생략 시 그 조건 없이 전체**): 7.1과 동일한 8개 — `status`(`ACTIVE`/`CLOSED`. v9까지의 기본 ACTIVE는 v10에서 폐기, 생략 시 CLOSED 포함 전체), `occurredFrom`·`occurredTo`(`occurredDttm` 기준 날짜 범위), `districtCode`(Q406 A), `speciesSeq`, `gender`, `isNeutered`, `keyword`. v6까지의 `breedSeq`·`colorSeq`·`shelterSeq`(담당 보호소 공개 필터)는 **삭제**.
- **포함 매칭(Q463)**은 7.1과 같습니다(`MALE/FEMALE`은 `UNKNOWN` 포함, `true/false`는 NULL 포함).
- `keyword`는 **`description`에만** ILIKE(S7). v6까지의 `landmarkDescription`·`address` 검색은 제외.
- 정렬 화이트리스트: **`regDttm`만**(기본 desc, `asc` 허용).

**목록 200 `data.content[]` [초안]**
```json
{
  "foundReportSeq": 210,
  "classification": { "combinationSeq":88, "speciesName":"개", "breedName":"말티즈", "colorName":"흰색" },
  "gender": "UNKNOWN",
  "age": null,
  "isNeutered": null,
  "weight": null,
  "status": "ACTIVE",
  "shelterSeq": 7,
  "address": "대구광역시 ...",
  "districtCode": "27110",
  "occurredDttm": "2026-10-29T10:00:00+09:00",
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00"
}
```
- **[확정 정책] (Q468·Q469)** v7까지 목록에 없던 `gender`·`age`·`isNeutered`·`weight`·`regDttm`을 추가해 7.1 실종 목록과 같은 구성으로 맞췄습니다(`shelterSeq`만 발견 고유). `regDttm`은 8.3의 고정 정렬·8.8의 정렬 기준이기도 합니다. `weight`는 자유 텍스트(7.1과 동일).

**상세 200 `data` [초안]** 목록 필드 + `reporter:{ActorSummary}`, `shelter:{shelterSeq,shelterName}`, `microchipNumber`, `description`, `landmarkDescription`, `latitude`, `longitude`, `reporterPhone`, `isPhonePublic`, `closedReason`, `closedDttm`, `images`, `isBookmarked`, `commentCount`.

- **[확정 정책]** 비공개 `reporterPhone`은 작성자·**이 발견 신고의 담당 보호소**만 열람. 다른 사용자·다른 보호소에는 `null` 반환 [초안]. `reporterName` 별도 DB 컬럼 없음. 필요한 경우 `users.name`을 권한 범위 내 JOIN합니다.
- 삭제·탈퇴 작성자는 일반 노출에서 제외합니다. **발견 당시 목격 상황 전용 `circumstances` 없음**.
- **오류 [초안]** 8.2: 404 `FOUND_REPORT_NOT_FOUND`(없음·삭제·비노출). 8.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`(정렬 화이트리스트 밖, 날짜 형식·역전, 허용값 밖 `gender`·`isNeutered`).

### 8.3 GET /found-reports/me

**요청 [확정 정책] (Q464)** 파라미터 없음(보내도 무시). 범위는 토큰 `userSeq`가 작성한 **비삭제** 발견 신고 전체(ACTIVE·CLOSED 모두). 페이지네이션·필터·정렬 파라미터를 두지 않습니다.

**성공 200 `data:[...]` [초안]** `Page<T>`가 아닌 **배열**. 각 항목은 8.1 목록 필드와 동일하며 **`regDttm desc` 고정**. 담당 보호소 쪽 보조 필드(`reporter{name,phone}`·`rescueStatus`·`activeRescueSeq`)는 8.8에만 있습니다.

### 8.4 POST /found-reports

**요청 [초안]**
```json
{
  "combinationSeq": 88,
  "gender": "UNKNOWN",
  "age": null,
  "weight": null,
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
- **[확정 정책] 몸무게(Q469):** `weight`는 7.4와 같이 **선택, 자유 텍스트 최대 30자**. 발견 신고는 추정값이 많으므로 "약 3kg" 같은 표기를 그대로 받습니다. 비우면 null.
- **[확정 정책] 주소·좌표(Q467):** 7.4와 같이 앱이 5.6 주소 검색 프록시 후보(또는 지도 핀)에서 얻은 `address`·`latitude`·`longitude`를 보냅니다. 서버 지오코딩 없음, 상세 주소 텍스트 필드 없음. `landmarkDescription`은 기존대로 자유 서술(주소가 아니라 기준점 설명)입니다.
- **[확정 정책] 담당 보호소 자동 배정(Q406 A·Q406-2 A), 서버 수행:** ① 좌표를 네이버 역지오코딩(`legalcode`)으로 조회해 앞 5자리를 `districtCode`로 저장 → ② `shelter_district_assignments`에 그 코드가 있으면 지정 보호소(`assignmentMethod:"DISTRICT"`) → ③ 코드가 없거나(역지오코딩 실패, `districtCode:null`) 배정표에 없으면 위경도 하버사인 최근접 비삭제 보호소(`assignmentMethod:"NEAREST"`, S3 A). 역지오코딩 실패로 **등록을 거부하지 않습니다**. `assignmentMethod`는 **응답 전용** 값이며 DB에 저장하지 않습니다(R13 A). 보호소가 하나도 없을 때만 409 `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE`(시드 전 환경). `shelterSeq` 임의 입력 불허. 수동 재배정 API는 없습니다(ADMIN 범위 외).
- **[확정 정책]** 이 시점에 발견 신고 상태는 ACTIVE; 기존 ACTIVE 실종 신고와 AI 비교 후보. 담당 보호소의 SHELTER 계정에 `SHELTER` 알림(17.8, Q426).
- **오류 [초안]** 409 `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE`; 404 `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`.

### 8.5 PATCH /found-reports/{id} · 8.6 PATCH /.../status · 8.7 DELETE /...

- `8.5` **요청 [초안]** 8.4의 수정 필드 일부(`weight` 포함, 자유 텍스트 Q469) + `imageSeqs`(전체 최종 배열, Q424). `shelterSeq` 필드 불허. 성공 200은 8.2 상세 DTO. 좌표·주소를 바꾸면 `districtCode`는 **재도출**하지만 **소속 보호소는 자동 재배정되지 않습니다**. 이미지 diff가 있으면 AI 재매칭. **CLOSED는 읽기 전용(Q425 B):** 409 `FOUND_REPORT_CLOSED`, 고치려면 8.6으로 ACTIVE 재개. 작성자 본인만(Q423).
- `8.6` **요청 [초안]** `{ "status":"CLOSED", "closedReason":"보호자가 직접 찾아감" }` 또는 `{ "status":"ACTIVE" }`. 성공 200 `data:{foundReportSeq,status,closedReason,closedDttm}`. CLOSED에서 `closedDttm`은 서버 생성 필수, `closedReason` 선택. ACTIVE 재개 시 두 값은 `null`로 초기화. **권한(Q421):** 작성자는 CLOSED·ACTIVE 모두 가능, **담당 SHELTER(토큰 `shelterSeq` = 신고의 `shelterSeq`)는 CLOSED만** 가능하고 재개는 불가.
- `8.7` 성공 **204**(본문 없음, Q450). 작성자 본인만. 기존 구조 기록이 연결되어 있더라도 **구조 기록/FK/진행 상태는 변경하지 않습니다**.
- **중요:** 구조 접수·완료, 직접 반환, 보호동물 상태 전환 시에도 발견 신고를 자동 CLOSED로 바꾸지 않습니다.
- **오류 [초안]** 8.5~8.7 공통 404 `FOUND_REPORT_NOT_FOUND`(없음·삭제·**타인 신고**; 8.6은 작성자도 담당 보호소도 아닌 경우, Q434). 8.5: 409 `FOUND_REPORT_CLOSED`, 404 `CATALOG_COMBINATION_NOT_FOUND`·`IMAGE_NOT_FOUND`, 403 `IMAGE_NOT_OWNED`, 400 `IMAGE_INVALID_COUNT`. 8.6: 담당 SHELTER가 `ACTIVE` 재개를 요청하면 403 `AUTH_FORBIDDEN`(역할상 허용되지 않는 동작), 현재와 같은 상태면 400 `COMMON_INVALID_INPUT`.

### 8.8 GET /found-reports/assigned

**요청 [확정 정책] (Q464)** `?status=ACTIVE&period=RECENT_30D&districtCode=27110&speciesSeq=1&rescueStatus=NONE&page=0&size=8&sort=regDttm,desc`. 보호소 웹 "제보 관리" 화면용이며 범위는 토큰 `shelterSeq`가 담당인 비삭제 발견 신고입니다.

| 파라미터 | 값 | 의미 |
|---|---|---|
| `status` | `ACTIVE` / `CLOSED` | 신고 상태. 생략 시 전체 |
| `period` | `RECENT_7D` / `RECENT_30D` / `RECENT_90D` | 발견 기간. `occurredDttm`이 요청 시각 기준 최근 N일 이내. 생략 시 전체 |
| `districtCode` | 5자리 | 지역(5.5 값). 7.1과 동일 |
| `speciesSeq` | | 축종. 7.1과 동일 |
| `rescueStatus` | `NONE` / `PROCESSING` / `DONE` / `ABORTED` | 구조 상태. 이 신고의 **최신 비삭제 구조 기록** 기준. `NONE`=구조 기록 없음, `ABORTED`=`FAILED` 또는 `CANCELED`. v6까지의 `hasRescue` 불리언을 대체 |
| `sort` | `regDttm,desc`(기본) / `regDttm,asc` | 최신순/오래된순. 화이트리스트 `regDttm`만 |
| `page`·`size` | size 기본 **8**, 최대 100 | 1.1 기본 20의 예외 |

- 모든 필터는 선택이며 생략하면 담당 신고 전량(R10 A). 사용자 앱(7.1·8.1)은 날짜 범위, 보호소 웹(8.8)은 최근 N일 프리셋으로 **기간 형식이 다른 것은 의도된 설계**입니다(화면이 다름).
- **응답 200 `Page<T>` [초안]** 각 항목에 8.1 목록 필드(Q468로 `gender`·`age`·`isNeutered`·`weight`·`regDttm` 포함) + `reporter:{ name, phone }` + `rescueStatus`(위 4값) + `activeRescueSeq`(비삭제 PROCESSING/DONE 구조의 `rescueSeq`, 없으면 null).
- **[확정 정책] 신고자 정보(Q470):** `reporter.name`은 `users.name` **실명**이고 `reporter.phone`은 신고의 `reporterPhone`입니다(담당 보호소이므로 둘 다 열람 가능, 현장 연락용). 사용자 앱의 `ActorSummary.displayName`(닉네임)과 달리 **보호소 화면은 실명**을 씁니다. v8까지 항목에 따로 두던 `reporterPhone`은 `reporter.phone`으로 흡수합니다. 작성자가 탈퇴했으면 `name`은 탈퇴 회원 표시 규칙(1.4 `ActorSummary`와 동일)을 따릅니다. 10.2 `foundReportSummary.reporter`와 같은 구성입니다.
- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(허용값 밖 `status`·`period`·`rescueStatus`, 화이트리스트 밖 정렬), `CATALOG_INVALID_DISTRICT_CODE`.

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

### 9.3 GET /shelters/me

**요청 [초안]** 파라미터·본문 없음(보내도 무시). 대상 보호소는 토큰 `shelterSeq`로만 정해집니다. SHELTER 전용이며 USER가 호출하면 403 `AUTH_FORBIDDEN`.

**성공 200 `data` [초안]** 9.2 상세 DTO와 동일 구조(`shelterSeq`~`backgroundImage`). 보호소 웹 "보호소 정보" 화면용으로, 이 중 보호소 계정이 직접 관리할 수 있는 항목은 9.4의 다섯 필드(`description`·`operatingHours`·`closedDays`·`profileImageSeq`·`backgroundImageSeq`)이고 나머지는 읽기 전용입니다.

- **[확정 정책]** SHELTER 계정은 3.1 `GET /users/me`를 쓰지 않고(Q451) 이 API로 소속 보호소를 조회합니다. `externalShelterCode`는 9.2와 마찬가지로 노출하지 않습니다.
- **오류 [초안]** 404 `SHELTER_NOT_FOUND`(토큰의 `shelterSeq`가 삭제된 보호소).

### 9.4 PATCH /shelters/me

**요청 [초안]**
```json
{
  "description": "센터 소개",
  "operatingHours": "09:00~18:00",
  "closedDays": "일요일",
  "profileImageSeq": 1701,
  "backgroundImageSeq": 1702
}
```
**성공 200 `data` [초안]** 9.2 상세 DTO(수정 반영 후 전체).

- **부분 수정.** 보낸 필드만 반영하고 생략한 필드는 유지합니다. 다섯 필드 모두 선택이며, 이미지 필드에 `null`을 보내면 연결 해제(이미지 0장).
- **[확정 정책] 식별정보 수정 불가(Q412):** **`shelterName`·`address`·`phone`·`latitude`·`longitude`·`districtCode` 등 식별정보는 SHELTER가 수정할 수 없습니다.** 요청에 포함해도 무시하지 않고 400 `COMMON_INVALID_INPUT`. 변경은 Flyway 시드·직접 DB 작업으로만 하며 ADMIN API는 두지 않습니다.
- **[확정 정책] 대상 보호소:** URL에 `shelterSeq`가 없고 토큰 `shelterSeq`로만 정해집니다. 따라서 "타 보호소 수정" 상황이 구조적으로 없습니다(Q442).
- **[확정 정책] 이미지(Q407):** `profileImageSeq`·`backgroundImageSeq`는 **보호소 계정이 직접 업로드한 이미지만** 연결 가능. 각각 images 직접 FK, 0~1장(9.2 정책과 동일).
- **오류 [초안]** 403 `IMAGE_NOT_OWNED`; 404 `SHELTER_NOT_FOUND`(토큰의 `shelterSeq`가 삭제된 보호소), `IMAGE_NOT_FOUND`; 400 `COMMON_INVALID_INPUT`(식별정보 필드 포함). 대상이 토큰에서만 정해지므로 `SHELTER_NOT_OWNED`는 두지 않습니다(Q442).

---

## 10. 구조 기록 (RESCUE)

> **v5 변경(Q461):** 구조 사유는 코드 4종, 장소·구조 일시는 **접수 시 필수**, 상세 장소 추가, 목록 필터·상태별 건수 API 추가, 상세에 연결 발견 신고 요약과 `modDttm` 추가. **v9 변경(Q471·Q472):** 목록 기간 필터를 최근 N일 프리셋(`rescuedDttm` 기준)으로, size 기본 8, 상태별 건수는 10.1 응답에 `statusCounts`로 포함하고 10.7은 삭제, 목록·상세에 중성화·구조 메모·신고자 실명 추가. 10장 API는 모두 **SHELTER 전용(보호소 웹 "구조일지")**입니다. 일반 사용자는 11.2 `rescueInfo`로만 구조 정보를 봅니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 10.1 | GET | `/rescues` | SHELTER | 우리 보호소 구조 기록 목록 + 상태별 건수(Q471) |
| 10.2 | GET | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 상세 |
| 10.3 | POST | `/rescues` | SHELTER | 발견 신고 연계/직접 구조 접수 |
| 10.4 | PATCH | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 진행 기록 수정 |
| 10.5 | PATCH | `/rescues/{rescueSeq}/status` | 담당 SHELTER | DONE/FAILED/CANCELED 처리 |
| 10.6 | DELETE | `/rescues/{rescueSeq}` | 담당 SHELTER | 구조 기록 논리 삭제(참조 있으면 거부) |
| ~~10.7~~ | ~~GET~~ | ~~`/rescues/status-counts`~~ | - | **삭제(Q471).** 상태별 건수는 10.1 응답 `statusCounts`로 통합 |

**[확정 정책] 구조 사유 코드(Q461)**

| `rescueReasonCode` | 화면 문구 |
|---|---|
| `WANDERING` | 배회/유기 의심 |
| `INJURY_ILLNESS` | 부상/질병 |
| `DANGEROUS_PLACE` | 위험 장소 |
| `OTHER` | 기타 |

### 10.1 GET /rescues · 10.2 GET /rescues/{id}

**목록 파라미터 [확정 정책] (Q471)** `?status=PROCESSING&period=RECENT_30D&speciesSeq=1&source=REPORT&page=0&size=8&sort=regDttm,desc`. 보호소 웹 "구조일지" 목록 화면용이며 범위는 토큰 `shelterSeq`의 비삭제 구조 기록입니다.

| 파라미터 | 값 | 의미 |
|---|---|---|
| `status` | `PROCESSING` / `DONE` / `FAILED` / `CANCELED` | 구조 상태(화면 상단 탭). 생략 시 전체 |
| `period` | `RECENT_7D` / `RECENT_30D` / `RECENT_90D` | 구조 기간 칩. **`rescuedDttm`(실제 구조 일시)이 요청 시각 기준 최근 N일 이내.** 생략 시 전체. v8까지의 `rescuedFrom`·`rescuedTo` 날짜 범위를 대체 |
| `speciesSeq` | | 축종. 7.1과 동일 |
| `source` | `REPORT` / `DIRECT` | 접수 경로. `REPORT`=발견 신고 연계(`foundReportSeq` 있음) / `DIRECT`=직접 접수(`foundReportSeq` null). 생략 시 전체 |
| `foundReportSeq` | | 특정 발견 신고에 연결된 구조만 |
| `sort` | `regDttm,desc`(기본) / `regDttm,asc` / `completedDttm,desc` / `completedDttm,asc` | 화이트리스트 `regDttm`(화면 "등록일 최신순")·`completedDttm` |
| `page`·`size` | size 기본 **8**, 최대 100 | 1.1 기본 20의 예외(8.8과 동일) |

- 모든 필터는 선택이며 생략하면 우리 보호소 구조 기록 전량입니다. 구조 사유 필터는 두지 않습니다(Q461). 페이지 번호 방식(offset).

**목록 200 `data` [초안]** — `Page<T>`를 한 겹 감싼 전용 DTO(1.4 예외, Q471)
```json
{
  "statusCounts": { "total": 24, "PROCESSING": 3, "DONE": 16, "FAILED": 3, "CANCELED": 2 },
  "page": {
    "content": [
      {
        "rescueSeq": 301,
        "status": "PROCESSING",
        "source": "REPORT",
        "foundReportSeq": 210,
        "rescuedDttm": "2026-10-23T17:47:00+09:00",
        "rescueReasonCode": "WANDERING",
        "rescueMemo": "공원 벤치 아래 은신 중, 포획틀 필요",
        "resultReason": null,
        "animal": {
          "speciesName": "고양이",
          "breedName": "코리안숏헤어",
          "age": "0살",
          "gender": "UNKNOWN",
          "isNeutered": null,
          "colorName": "흰색",
          "weight": "약 3kg",
          "representativeImageUrl": "https://..."
        },
        "regDttm": "2026-10-10T22:20:00+09:00"
      }
    ],
    "page": 0, "size": 8, "totalElements": 3, "totalPages": 1, "hasNext": false
  }
}
```
- **[확정 정책] 상태별 건수 동봉(Q471):** `statusCounts`는 v8까지 별도 API 10.7이 내리던 `{ total, PROCESSING, DONE, FAILED, CANCELED }`와 같은 값이며, 화면이 칩(기간·축종·접수 경로)을 바꿀 때마다 두 번 호출하지 않도록 목록과 **한 응답**에 담습니다. 건수는 **`status`를 제외한** 필터(`period`·`speciesSeq`·`source`·`foundReportSeq`)만 적용해 세므로 "진행 중" 탭을 눌러도 다른 탭의 숫자가 유지됩니다. 비삭제 구조만 세고 `total`은 네 값의 합입니다. `page.totalElements`는 `status`까지 적용한 현재 탭의 건수이므로 `statusCounts`의 해당 값과 같습니다(`status` 생략 시 `total`과 같음). 10.7은 삭제합니다.
- **[확정 정책] 동물 정보 출처:** `source=REPORT`는 연결된 **발견 신고**의 동물 정보, `source=DIRECT`는 이 구조에 연결된 **보호동물**(DONE 후 등록, 여러 마리면 가장 먼저 등록된 1마리)의 정보입니다(Q471에서 현행 유지 확인). 아직 없으면 `animal:null`이고 화면은 "-"로 표시합니다. `isNeutered`는 두 출처 모두 `true/false/null`(null=모름, Q471 추가). `weight`는 두 출처 모두 자유 텍스트 문자열(Q469)이며, v7까지 `found_reports`에 컬럼이 없어 근거가 없던 발견 신고 쪽 `weight`는 Q469의 컬럼 신설로 실제 값이 채워집니다.
- **[확정 정책] 구조 메모(Q471):** 항목에 `rescueMemo`(접수·수정 시 입력한 자유 메모, 없으면 null)를 포함해 목록에서 바로 보여줍니다.
- 화면의 "비고"는 `resultReason`(실패·취소 사유), "등록번호"는 `rescueSeq`(표기 형식은 프론트), "접수 경로"는 `source`·`foundReportSeq`(예: "신고 #F-123")로 표시합니다.

**상세 성공 200 `data` [초안]**
```json
{
  "rescueSeq": 301,
  "shelterSeq": 7,
  "source": "REPORT",
  "foundReportSeq": 210,
  "status": "PROCESSING",
  "rescueReasonCode": "WANDERING",
  "rescueAddress": "구미시 인동동 123-12",
  "rescueAddressDetail": "송산도서관 인근 마을",
  "rescuedDttm": "2026-10-23T17:47:00+09:00",
  "rescueMemo": "공원 벤치 아래 은신 중, 포획틀 필요",
  "resultReason": null,
  "regDttm": "2026-10-10T22:20:00+09:00",
  "modDttm": "2026-11-12T22:20:00+09:00",
  "completedDttm": null,
  "linkedAnimalCount": 0,
  "foundReportSummary": {
    "foundReportSeq": 210,
    "classification": { "speciesName": "고양이", "breedName": "코리안숏헤어", "colorName": "흰색" },
    "age": "0살",
    "gender": "UNKNOWN",
    "isNeutered": null,
    "weight": "약 3kg",
    "address": "구미시 인동동 123-123 구미아파트",
    "occurredDttm": "2026-10-19T18:30:00+09:00",
    "representativeImageUrl": "https://...",
    "reporter": { "name": "홍길동", "phone": "01012341234" },
    "status": "ACTIVE"
  }
}
```
- `regDttm`은 접수 시각(화면 "접수일자"), `modDttm`은 마지막 수정 시각(화면 "업데이트", 수정 전이면 null). DB에 `received_dttm` 컬럼은 없습니다.
- **[확정 정책] 연결된 발견 신고(Q461·Q472):** `source=DIRECT`면 `foundReportSummary:null`. 요약에는 `isNeutered`(`true/false/null`, Q472 추가)를 포함합니다. `reporter`는 8.8과 같은 `{ name, phone }`이며 **`name`은 `users.name` 실명**입니다(Q472. v8까지의 `displayName` 닉네임을 대체. 보호소 화면은 실명 기준). 신고자 실명·전화번호는 **이 구조를 접수한 보호소에 공개**합니다(현장 대응용, 담당 배정 여부와 무관, R3). 발견 신고가 소프트 삭제되었으면 요약은 남은 정보로 채우고 `status`로 상태를 알립니다. 작성자가 탈퇴했으면 `name`은 탈퇴 회원 표시 규칙(1.4)을 따릅니다. 구조가 완료되어도 발견 신고는 자동 종료되지 않습니다(8.7).
- **오류 [초안]** 10.1: 400 `COMMON_INVALID_INPUT`(허용값 밖 `status`·`period`·`source`, 화이트리스트 밖 정렬). 10.2·10.4~10.6 공통 404 `RESCUE_NOT_FOUND`(없음·삭제·**다른 보호소의 구조**, Q434).

### 10.3 POST /rescues

**요청 [초안]**
```json
{
  "foundReportSeq": 210,
  "rescueReasonCode": "WANDERING",
  "rescueAddress": "구미시 인동동 123-12",
  "rescueAddressDetail": "송산도서관 인근 마을",
  "rescuedDttm": "2026-10-23T17:47:00+09:00",
  "rescueMemo": null
}
```
직접 접수는 `foundReportSeq:null`. **성공 201 `data` [초안]** `{ "rescueSeq":301, "status":"PROCESSING", "regDttm":"..." }`.

- **[확정 정책] 필수값(Q461):** `rescueReasonCode`, `rescueAddress`(공백 금지, 보호소가 직접 입력), `rescuedDttm`(분 단위, 화면 `YYYY-MM-DD HH:MM`)는 필수. `rescueAddressDetail`(건물명·주변 지형)과 `rescueMemo`는 선택. 항상 **PROCESSING으로 생성**하며 요청에 상태 필드는 없습니다(화면의 진행 상태 표시는 접수 후 10.5에서 변경).
- **[확정 정책]** 발견 신고를 선택하여 접수하면 즉시 PROCESSING 생성, 신고 없이 자체 접수도 가능. **SHELTER 전용**이며 `shelter_seq`는 토큰 `shelterSeq`로 서버가 설정합니다. 요청에 `shelterSeq` 필드는 **없습니다**(Q410). 발견 신고 1건에 비삭제 `PROCESSING/DONE` 합계 최대 1건. **[확정 정책] R3 A:** 담당 보호소가 아닌 발견 신고도 접수할 수 있습니다(구조는 실제 현장 대응이라 배정과 무관). **[확정 정책] 대상 신고 상태(Q442):** 소프트 삭제된 발견 신고는 404 `FOUND_REPORT_NOT_FOUND`, **CLOSED 신고는 접수 허용**(구조는 신고 상태와 독립, 8.5의 CLOSED 읽기 전용은 신고 본문에만 적용).
- **오류 [초안]** 409 `RESCUE_ACTIVE_ALREADY_EXISTS`; 404 `FOUND_REPORT_NOT_FOUND`; 400 `COMMON_INVALID_INPUT`(필수값 누락·공백 주소·정의되지 않은 사유 코드).

### 10.4 PATCH /rescues/{id} · 10.5 PATCH /.../status

- `10.4` **요청 [초안]** `{ "rescueReasonCode":"INJURY_ILLNESS", "rescueAddress":"...", "rescueAddressDetail":"...", "rescuedDttm":"...", "rescueMemo":"..." }` (보내는 필드만 변경, 필수 필드를 null·공백으로 바꿀 수 없음). **[확정 정책] R4 A:** 종료 상태(DONE/FAILED/CANCELED)에서는 같은 PATCH로 `rescueMemo`만 수정 가능하고, 다른 필드가 포함되면 400 `COMMON_INVALID_INPUT`. 나머지 필드는 PROCESSING에서만. **종료 후 수정 전용 엔드포인트는 두지 않습니다.**
- `10.5` **DONE 요청 [초안]** `{ "status":"DONE" }`. 구조 정보는 접수 때 이미 필수로 받았으므로 DONE 시 추가 필수값이 없습니다(Q461; 종전 "DONE 시 구조일·장소 필수" 검증 제거).
- `10.5` **FAILED/CANCELED 요청 [초안]** `{ "status":"FAILED", "resultReason":"현장 도착 전 이동" }` 또는 CANCELED.
- **[확정 정책]** 최초 `PROCESSING → DONE/FAILED/CANCELED`, 종료 상태에서 되돌릴 수 없음. PROCESSING은 `completedDttm:null`; 종료 상태 3개는 서버가 시각 기록. FAILED/CANCELED이면 공백 아닌 `resultReason` 필수(상세 화면의 사유 입력란은 FE 확인 대기, 20.3).
- **오류 [초안]** 404 `RESCUE_NOT_FOUND`; 409 `RESCUE_INVALID_TRANSITION`; 400 `RESCUE_RESULT_REASON_REQUIRED`, `COMMON_INVALID_INPUT`(종료 상태에서 `rescueMemo` 외 필드, 필수 필드 비우기).

### 10.6 DELETE /rescues/{id}

성공 **204**(본문 없음, Q450). **연결된 보호동물이 한 마리라도 있으면 삭제 거부**(409 `RESCUE_HAS_ANIMALS`), 없으면 404 `RESCUE_NOT_FOUND`. 발견 신고가 삭제되었다는 이유로 기존 구조 기록이 삭제되거나 중단되지 않습니다.

### 10.7 (삭제, Q471)

v8까지의 `GET /rescues/status-counts`는 **삭제**합니다. 목록 상단 탭(전체/진행 중/완료/실패/취소)의 숫자는 10.1 응답의 `statusCounts`로 받습니다(건수 규칙은 10.1 참조). 번호는 비워 두고 재사용하지 않습니다.

---

## 11. 보호동물·반환 (SHELTER ANIMAL)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 11.1 | GET | `/shelter-animals` | 로그인 | 보호동물 목록·검색 |
| 11.2 | GET | `/shelter-animals/{animalSeq}` | **USER** | 보호동물 상세(앱용, Q478) |
| 11.3 | GET | `/shelter-animals/me` | SHELTER | 우리 보호소 등록 보호동물 목록 |
| 11.4 | POST | `/shelter-animals` | SHELTER | 직접/완료 구조 연계 보호동물 등록 |
| 11.5 | PATCH | `/shelter-animals/{animalSeq}` | 담당 SHELTER | 보호동물 기초 정보·사진 수정 |
| 11.6 | PATCH | `/shelter-animals/{animalSeq}/management` | 담당 SHELTER | **보호 상태·입양 가능 여부·공고기간 관리(Q479).** v10의 11.6 입양 상태 전환·11.7 반환·11.8 보호 종료·12.12 외부 입양을 흡수 |
| 11.7 | — | (삭제, Q479) | — | 반환 완료는 11.6 `status=RETURNED` |
| 11.8 | — | (삭제, Q479) | — | 보호 종료는 11.6 `status=ENDED` |
| 11.9 | DELETE | `/shelter-animals/{animalSeq}` | 담당 SHELTER | 보호동물 논리 삭제(활성 신청 있으면 거부) |
| 11.10 | GET | `/shelter-animals/me/{animalSeq}` | 담당 SHELTER | **보호소 전용 보호동물 상세(웹용, Q478).** 관리 정보·반환/입양 정보·입양 신청 건수 동봉 |

### 11.1 GET /shelter-animals · 11.2 GET /shelter-animals/{id}

> **v10 변경(Q473·Q474):** 사용자 앱의 보호동물 목록·상세를 실종·발견 신고(7.1·8.1)와 같은 필터·항목 구성으로 통일했습니다. v9까지의 `shelterSeq`·`breedSeq`·`colorSeq`·`adoptionStatus` 필터와 `noticeEndDate` 정렬은 삭제, 기간 필터와 동물 속성 필드를 추가했습니다.  
> **v11 변경(Q476~Q478):** 11.2는 **앱(USER) 전용**이 되고(보호소 웹은 11.10), `shelter` 블록이 `{ shelterSeq, shelterName, phone, address }`로 바뀌며, 보호동물 댓글 제거로 `commentCount`가 빠집니다. 11.1 목록은 변경 없습니다.

**검색 파라미터 [확정 정책] (Q473)** `?status=PROTECTING_AVAILABLE&regFrom=2026-10-01&regTo=2026-10-31&districtCode=27110&speciesSeq=1&gender=MALE&isNeutered=true&keyword=순함&page=0&size=20&sort=regDttm,desc`. **모든 파라미터는 선택이며 생략하면 그 조건 없이 전체**입니다(`status`도 기본값 없음. 7.1·8.1과 같은 규칙).

| 파라미터 | 값 | 의미 |
|---|---|---|
| `status` | `PROTECTING_AVAILABLE` / `PROTECTING_UNAVAILABLE` / `RETURNED` / `ADOPTED` / `ENDED` | **보호 상태와 입양 상태를 합친 파생값**(아래 매핑표). 생략 시 전체. `protectionStatus`·`adoptionStatus`를 따로 받지 않음 |
| `regFrom`·`regTo` | `YYYY-MM-DD` | **`regDttm`(등록일) 기준** 날짜 범위. KST 일 단위 양끝 포함, 한쪽만 보내도 됨, `from > to`면 400. 7.1 `occurredFrom/To`와 같은 규칙이며 보호동물에는 발생 일시가 없어 등록일을 씀 |
| `districtCode` | 5자리 | 지역. **보호소 소재지**(`shelters.district_code`) 기준 JOIN. 5.5 값, 7.1과 동일 |
| `speciesSeq` | | 축종. 7.1과 동일(`combinationSeq→animal_breeds→animal_species`) |
| `gender` | `MALE` / `FEMALE` / `UNKNOWN` | 포함 매칭(Q463): `MALE`·`FEMALE`은 `UNKNOWN` 포함 |
| `isNeutered` | `true` / `false` | 포함 매칭(Q463): `true`는 TRUE+NULL, `false`는 FALSE+NULL |
| `keyword` | | 특징 검색. `description`·`healthCheck` `ILIKE '%키워드%'`(S7, v9와 동일 대상 유지) |
| `sort` | `regDttm,desc`(기본) / `regDttm,asc` | 등록일 최신순/오래된순. **화이트리스트 `regDttm`만**(v9의 `noticeEndDate` 정렬 삭제) |
| `page`·`size` | size 기본 20, 최대 100 | 1.1 기본 |

**[확정 정책] 파생 `status` 매핑(Q473·Q475, 11.1·11.3 공통)** — DB `protection_status`·`adoption_status` 조합 CHECK(`ck_shelter_status_pair`, DB v17 §7)에 따라 다섯 값이 겹침 없이 전체를 덮습니다. 응답 항목에는 두 원본 상태(`protectionStatus`·`adoptionStatus`)를 그대로 내리고 합성값은 요청 필터·탭에만 씁니다.

| `status` | 화면 표시 | `protectionStatus` | `adoptionStatus` |
|---|---|---|---|
| `PROTECTING_AVAILABLE` | 보호중·입양 가능 | `PROTECTING` | `AVAILABLE` |
| `PROTECTING_UNAVAILABLE` | 보호중·입양 불가 | `PROTECTING` | `UNAVAILABLE` |
| `RETURNED` | 반환 완료 | `RETURNED` | `UNAVAILABLE` |
| `ADOPTED` | 입양 완료 | `ADOPTED` | `ADOPTED` |
| `ENDED` | 보호 종료 | `ENDED` | `UNAVAILABLE` |

- v9까지의 `shelterSeq`·`breedSeq`·`colorSeq`·`protectionStatus`·`adoptionStatus`는 **삭제**(보내도 1.1 규칙에 따라 무시). 특정 보호소의 동물만 보는 화면은 9.2 보호소 상세와 별도로 두지 않습니다. 범위는 비삭제 보호동물 전체이며 비노출 필터는 같은 쿼리에서 적용합니다.

**목록 200 `data.content[]` [초안]**
```json
{
  "animalSeq": 700,
  "classification": { "combinationSeq":88, "speciesName":"개", "breedName":"말티즈", "colorName":"흰색" },
  "gender": "UNKNOWN",
  "age": "1살 추정",
  "isNeutered": null,
  "weight": "3kg",
  "protectionStatus": "PROTECTING",
  "adoptionStatus": "UNAVAILABLE",
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-08",
  "shelter": { "shelterSeq": 7, "shelterName": "대구동물보호센터", "districtCode": "27110" },
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00"
}
```
- **[확정 정책] (Q473)** 7.1·8.1 목록과 같은 순서·형식으로 `classification`(이름 포함 축약형)·`gender`·`age`·`isNeutered`·`weight`를 내려 카드에서 동물 속성을 바로 표시합니다. `age`·`isNeutered`·`weight`는 null 가능(`isNeutered:null`=모름), `weight`는 자유 텍스트(Q469). 신고의 `address`·`districtCode` 자리에는 **보호소 블록 `shelter`**(보호소 PK·이름·소재지 코드)를 둡니다. 보호동물 자체의 발생장소 컬럼은 없습니다(20.3 팀 논의 대기). **공고기간 `noticeStartDate`·`noticeEndDate`**는 목록에 포함합니다(DB NOT NULL, 직접 등록 기본값 등록일/+10일).

**상세 200 `data` [초안]** 목록 필드 + 아래 상세 전용 필드. `classification`은 1.4 `AnimalClassification` **전체형**(`speciesSeq`·`breedSeq`·`colorSeq` 포함). **USER 전용**(Q478, SHELTER는 11.10).
```json
{
  "animalSeq": 700,
  "classification": { "combinationSeq": 88, "speciesSeq": 1, "speciesName": "개", "breedSeq": 21, "breedName": "말티즈", "colorSeq": 3, "colorName": "흰색" },
  "gender": "UNKNOWN",
  "age": "1살 추정",
  "isNeutered": null,
  "weight": "3kg",
  "protectionStatus": "PROTECTING",
  "adoptionStatus": "UNAVAILABLE",
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-08",
  "shelter": { "shelterSeq": 7, "shelterName": "대구동물보호센터", "phone": "0531234567", "address": "대구광역시 ..." },
  "representativeImageUrl": "https://...",
  "regDttm": "2026-10-29T12:00:00+09:00",
  "modDttm": null,
  "rescueSeq": 301,
  "microchipNumber": null,
  "registrationNumber": null,
  "description": "사람을 잘 따름",
  "healthCheck": "기초 검진 완료",
  "vaccinationStatus": "예방접종 확인 중",
  "endReason": null,
  "protectionEndedDttm": null,
  "rescueInfo": {
    "rescuedDttm": "2026-10-29T09:30:00+09:00",
    "rescueReasonCode": "WANDERING",
    "rescueAddress": "대구광역시 ...",
    "rescueAddressDetail": null,
    "rescueMemo": null
  },
  "images": [{ "imageSeq": 1701, "imageUrl":"https://...", "displayOrder":1 }],
  "isBookmarked": false,
  "simulation": { "speciesCode": "DOG", "personality": "FRIENDLY", "canStart": true, "activeSessionSeq": null }
}
```
- **[확정 정책] (Q474·Q476·Q477)** 상세는 "목록 필드 + 상세 전용"의 7.2·8.2와 같은 구조입니다. **`shelter`는 상세에서 `{ shelterSeq, shelterName, phone, address }`**(Q476. `shelters.phone`·`address` JOIN, 9.2 추가 호출 불필요. `shelterSeq`는 14.2 채팅방 생성용으로 유지. 목록 11.1·11.3의 블록은 `districtCode`형 그대로), `modDttm`은 마지막 수정 시각(수정 전 null). 상세 전용 필드는 `rescueSeq`·`microchipNumber`·`registrationNumber`·`description`·`healthCheck`·`vaccinationStatus`·`endReason`·`protectionEndedDttm`·`rescueInfo`·`images`·`isBookmarked`·`simulation`입니다. **`commentCount`는 Q477(보호동물 댓글 제거)로 삭제**되었고 보호동물 상세에는 댓글 영역이 없습니다.
- **[확정 정책] 역할 분리(Q478):** 11.2는 `@PreAuthorize` USER 전용이며 SHELTER가 호출하면 403 `AUTH_FORBIDDEN`입니다. 보호소 웹의 상세는 11.10을 씁니다(응답 구성이 달라 토큰 분기 대신 경로를 나눔).
- **[확정 정책]** 보호동물에 `name` 없음. `rescueInfo`는 **완료 구조 기록을 LEFT JOIN해 파생**하며 `rescueSeq=null`이면 `rescueInfo`의 5개 필드도 모두 `null` [초안: 객체 자체 null 대신]. 구조 필드는 Q461에 맞춰 `rescuedDttm`·`rescueReasonCode`·`rescueAddressDetail`을 씁니다. DB에 구조 필드를 보호동물 컬럼으로 추가하지 않습니다.
- **[확정 정책] (Q458·Q469)** `registrationNumber`는 보호소가 입력한 정부 공식 동물등록번호(없으면 null, 화면 "미등록"), `weight`는 **자유 텍스트 문자열**(null 가능, Q469로 v6 Q458의 kg 숫자에서 변경. 반려동물·실종·발견과 동일 형식), `age`는 자유 텍스트.
- **[확정 정책]** PROTECTING이면서 비삭제이면 AVAILABLE/UNAVAILABLE 관계없이 AI 매칭 후보.
- **[초안] `simulation`** 블록은 상세 화면의 "모의 입양" 버튼용 파생 정보입니다. `speciesCode`는 조합→품종→종의 `species_code`, `personality`는 품종의 값(NULL 가능), `canStart`는 요청자가 USER이고 종이 DOG/CAT이며 동물이 보호 중이고 요청자에게 진행 중 세션이 없을 때 `true`, `activeSessionSeq`는 요청자의 진행 중 세션(있으면). 18.1 참조.
- **오류 [초안]** 11.2: 404 `ANIMAL_NOT_FOUND`(없음·삭제); 403 `AUTH_FORBIDDEN`(SHELTER 호출). 11.5·11.6·11.9·11.10: 404 `ANIMAL_NOT_FOUND`에 **다른 보호소 소속**도 포함(Q434). 11.1: 400 `CATALOG_INVALID_DISTRICT_CODE`, `COMMON_INVALID_INPUT`(정렬 화이트리스트 밖, 날짜 형식·역전, 허용값 밖 `status`·`gender`·`isNeutered`).

### 11.3 GET /shelter-animals/me

> **v10 변경(Q475):** 보호소 웹 "보호동물 관리" 화면용으로 8.8·10.1과 같은 형태(최근 N일 프리셋·size 8·상태 탭 건수 동봉)로 재정의했습니다. (v5에서 신설, v6에서 누락, v7 복원, v10 재정의.)

**요청 [확정 정책] (Q475)** `?status=PROTECTING_AVAILABLE&period=RECENT_30D&speciesSeq=1&gender=FEMALE&isNeutered=true&page=0&size=8&sort=regDttm,desc`. 범위는 토큰 `shelterSeq` 소속 **비삭제** 보호동물 전체입니다.

| 파라미터 | 값 | 의미 |
|---|---|---|
| `status` | `PROTECTING_AVAILABLE` / `PROTECTING_UNAVAILABLE` / `RETURNED` / `ADOPTED` / `ENDED` | 화면 상단 탭. **보호 상태와 입양 상태를 합친 파생값**(11.1의 매핑표와 동일). 생략 시 전체(ENDED 포함). v9의 `protectionStatus`·`adoptionStatus` 두 필터를 대체 |
| `period` | `RECENT_7D` / `RECENT_30D` / `RECENT_90D` | 등록 기간. **`regDttm`이 요청 시각 기준 최근 N일 이내.** 생략 시 전체. 8.8·10.1과 같은 프리셋(사용자 앱 11.1은 날짜 범위, 기간 형식 이원화는 의도) |
| `speciesSeq` | | 축종. 7.1과 동일 |
| `gender` | `MALE` / `FEMALE` / `UNKNOWN` | 포함 매칭(Q463) |
| `isNeutered` | `true` / `false` | 포함 매칭(Q463) |
| `sort` | `regDttm,desc`(기본) / `regDttm,asc` | 등록일 최신순/오래된순. 화이트리스트 `regDttm`만 |
| `page`·`size` | size 기본 **8**, 최대 100 | 1.1 기본 20의 예외(8.8·10.1과 동일) |

- 파생 `status`의 값·매핑은 **11.1의 매핑표**를 그대로 씁니다(Q473·Q475 공통).
- 모든 필터는 선택이며 생략하면 그 조건 없이 우리 보호소 보호동물 전량입니다. 11.1의 `districtCode`·`keyword`·날짜 범위는 받지 않습니다.

**성공 200 `data` [초안]** — 10.1과 같은 `Page<T>` 래퍼(1.4 예외, Q475)
```json
{
  "statusCounts": { "total": 31, "PROTECTING_AVAILABLE": 9, "PROTECTING_UNAVAILABLE": 12, "RETURNED": 4, "ADOPTED": 5, "ENDED": 1 },
  "page": {
    "content": [ { "...": "11.1 목록 항목과 동일" } ],
    "page": 0, "size": 8, "totalElements": 9, "totalPages": 2, "hasNext": true
  }
}
```
- **[확정 정책] 상태별 건수 동봉(Q475):** `statusCounts`는 탭 6개(전체/보호중·입양 가능/보호중·입양 불가/반환 완료/입양 완료/보호 종료)의 숫자이며, 10.1과 같은 규칙으로 **`status`를 제외한** 필터(`period`·`speciesSeq`·`gender`·`isNeutered`)만 적용해 셉니다. 비삭제만 세고 `total`은 다섯 값의 합입니다. `page.totalElements`는 현재 탭의 건수이므로 `statusCounts`의 해당 값과 같습니다(`status` 생략 시 `total`). 별도 건수 API는 두지 않습니다.
- **항목**은 11.1 목록 항목과 동일합니다(`shelter` 블록 포함, 항상 토큰 보호소).
- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(화이트리스트 밖 정렬, 허용값 밖 `status`·`period`·`gender`·`isNeutered`).

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
  "registrationNumber": null,
  "weight": "3kg",
  "description": "사람을 잘 따름",
  "healthCheck": "기초 검진 완료",
  "vaccinationStatus": null,
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-08",
  "adoptionStatus": "UNAVAILABLE",
  "imageSeqs": [1701, 1702]
}
```
**성공 201 `data` [초안]** `{ "animalSeq":700, "protectionStatus":"PROTECTING", "adoptionStatus":"UNAVAILABLE", "noticeStartDate":"2026-10-29", "noticeEndDate":"2026-11-08" }`.

- **[확정 정책] 등록 시 공고기간·입양 가능 여부(Q479):** `noticeStartDate`·`noticeEndDate`·`adoptionStatus`는 **모두 선택**입니다. 생략하면 시작일=등록일(KST), 종료일=시작일+10일, `adoptionStatus=UNAVAILABLE`. 한쪽만 보내면 나머지는 기본 규칙으로 채우되 `시작 ≤ 종료`여야 합니다(아니면 400 `COMMON_INVALID_INPUT`). `adoptionStatus`는 `AVAILABLE`/`UNAVAILABLE`만 허용. **보호 상태는 등록 시 항상 `PROTECTING`**이며 요청에 `protectionStatus` 필드는 없습니다(종료 상태로의 변경은 등록 뒤 11.6).

- **SHELTER 전용.** `shelter_seq`는 토큰 `shelterSeq`로 서버가 설정하며 요청에 `shelterSeq` 필드는 **없습니다**(Q410). `rescueSeq` 선택. 값이 있으면 **DONE인 구조**만 참조 가능. 같은 구조에서 여러 마리를 등록할 수 있으며 `rescue_records.shelter_seq`와 새 보호동물의 보호소가 서로 달라도 연결을 허용합니다(DB v17 확정 규칙: 구조한 보호소와 보호하는 보호소가 다를 수 있음). 존재하지 않거나 삭제된 구조는 404 `RESCUE_NOT_FOUND`, 존재하지만 DONE이 아니면 409 `RESCUE_NOT_DONE`(Q442).
- `rescueSeq=null`이면 직접 등록이며 `rescueInfo`는 전부 null. USER는 작성 불가.
- **[확정 정책] (Q458·Q469)** `registrationNumber`(선택, 최대 20자, 서버가 만들지 않음)와 `weight`(선택, **자유 텍스트 최대 30자**, 화면 입력 "3kg"을 그대로 저장. v6 Q458의 숫자 변환은 Q469로 폐기)를 받습니다. 등록 화면의 상태·공고기간·발생장소 입력과 등록 시점은 팀 논의 대기입니다(20.3).
- **[확정 정책]** 초기 보호 상태 `PROTECTING`, 입양 상태는 요청값(기본 `UNAVAILABLE`), 사진 1~3장, 공고기간은 요청값(기본 시작=등록일·종료=시작+10일). 공공데이터 시드 적재 시에는 적재자가 공고 날짜를 직접 결정·입력합니다. 날짜 경과로 자동 상태 변경하지 않습니다(core에 스케줄러 없음, S8).
- `11.4`는 SHELTER의 수동 등록용입니다. 공공데이터 적재는 API가 아니라 Flyway 시드입니다(Q412).
- **오류 [초안]** 409 `RESCUE_NOT_DONE`; 404 `RESCUE_NOT_FOUND`, `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`(Q407); 400 `IMAGE_INVALID_COUNT`, `COMMON_INVALID_INPUT`(공고 날짜 형식·역전, 허용값 밖 `adoptionStatus`).

### 11.5 PATCH /shelter-animals/{id}

- **요청 [초안]** `{ "combinationSeq":88, "gender":"UNKNOWN", "age":"2살 추정", "isNeutered":true, "weight":"3.5kg", "microchipNumber":null, "registrationNumber":null, "description":"...", "healthCheck":"...", "vaccinationStatus":"...", "imageSeqs":[1702] }` (보내는 필드만 변경). `imageSeqs`는 전체 최종 배열(Q424). **[확정 정책] (Q479, R5 A 대체):** 공고기간(`noticeStartDate`·`noticeEndDate`)·보호 상태·입양 가능 여부는 이 PATCH가 아니라 **11.6 관리 API**로만 바꿉니다(보내도 1.1 규칙에 따라 무시). 기존 구조 정보는 보호동물 수정 요청에 포함하지 않으며 구조 수정이 필요하면 `10.4`에서 수정합니다. 범위는 토큰 `shelterSeq`.
- 사진 diff가 있고 PROTECTING이면 재매칭 작업 요청.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(타 보호소 포함), `CATALOG_COMBINATION_NOT_FOUND`, `IMAGE_NOT_FOUND`; 403 `IMAGE_NOT_OWNED`; 400 `IMAGE_INVALID_COUNT`, `COMMON_INVALID_INPUT`.

### 11.6 PATCH /shelter-animals/{animalSeq}/management (보호 상태·입양 가능 여부·공고기간 관리)

> **v11 신설(Q479):** 보호소 웹의 등록·수정 폼이 공고기간·보호 상태·입양 가능 여부를 한 화면에서 고르므로, v10의 11.6(입양 상태 전환)·11.7(반환)·11.8(보호 종료)·12.12(외부 입양)를 이 API 하나로 합쳤습니다. **상태 전환은 어느 상태에서든 허용**하며 막지 않습니다(20.3 "11장 상태 변경 방식" 종결).

**요청 [초안]** — 보내는 필드만 변경(PATCH). `status`를 보내면 그 상태의 추가 입력을 함께 보냅니다.

```json
{ "noticeStartDate": "2026-10-29", "noticeEndDate": "2026-11-15", "status": "PROTECTING", "adoptionStatus": "AVAILABLE" }
```
```json
{ "status": "ENDED", "endReason": "NATURAL_DEATH" }
```
```json
{ "status": "RETURNED", "recipientName": "김가람", "recipientPhone": "01012345678" }
```
```json
{ "status": "ADOPTED", "reason": "보호소 방문 상담 후 직접 인계", "memo": "예방접종 2차 안내 완료" }
```

| 필드 | 값 | 의미 |
|---|---|---|
| `noticeStartDate`·`noticeEndDate` | `YYYY-MM-DD` | 공고기간. 둘 다 선택이며 보낸 쪽만 바꿈. 변경 결과가 `시작 ≤ 종료`가 아니면 400. 상태와 무관하게 언제든 수정 가능(`noticeStartDate`도 수정 가능, v10 R5 A의 "시작일 수정 불가" 폐기) |
| `status` | `PROTECTING` / `RETURNED` / `ADOPTED` / `ENDED` | 보호 상태. 선택. 생략하면 상태는 그대로 두고 공고기간만 바꿈 |
| `adoptionStatus` | `AVAILABLE` / `UNAVAILABLE` | **`status=PROTECTING`일 때만** 받음(생략하면 현재 값 유지, 다른 상태에서 PROTECTING으로 복귀할 때 생략하면 `UNAVAILABLE`). 다른 `status`와 함께 보내면 400 |
| `endReason` | `NATURAL_DEATH` / `EUTHANIZED` / `OTHER` | **`status=ENDED`일 때만.** 선택, 생략 시 `OTHER`(R6 A 유지). `RETURNED`·`ADOPTED` 값은 서버가 정하므로 입력 불가(400) |
| `recipientName`·`recipientPhone` | 문자열 | **`status=RETURNED`일 때 필수**(각각 최대 100자·30자). 반환받는 사람의 이름·연락처, 회원/비회원 무관 |
| `reason`·`memo` | 문자열 | **`status=ADOPTED`일 때.** `reason`은 처리 사유(필수, 최대 200자), `memo`는 비고(선택, TEXT). `shelter_animals.adoption_reason`·`adoption_memo`에 저장 |

**성공 200 `data` [초안]**
```json
{
  "animalSeq": 700,
  "protectionStatus": "RETURNED",
  "adoptionStatus": "UNAVAILABLE",
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-15",
  "endReason": "RETURNED",
  "protectionEndedDttm": "2026-11-05T14:00:00+09:00",
  "returnInfo": { "returnSeq": 801, "recipientName": "김가람", "recipientPhone": "01012345678", "returnedUserSeq": 17, "returnedDttm": "2026-11-05T14:00:00+09:00" },
  "adoptionInfo": null,
  "autoRejectedApplications": 2
}
```
- `returnInfo`·`adoptionInfo`는 11.10과 같은 블록(해당 상태가 아니거나 기록이 없으면 `null`). `autoRejectedApplications`는 이번 호출로 자동 REJECTED된 신청 수(종료 상태 전환이 아니면 0).

**[확정 정책] 상태별 서버 처리(Q479)** — 한 트랜잭션. `protection_status`·`adoption_status`·`end_reason`·`protection_ended_dttm`은 DB CHECK(`ck_shelter_status_pair`·`ck_shelter_protection_end`)에 맞게 **항상 네 컬럼을 함께** 설정합니다.

| `status` | 입양 상태 | 종료 컬럼 | 추가 처리 |
|---|---|---|---|
| `PROTECTING` | 요청 `adoptionStatus`(복귀 시 기본 `UNAVAILABLE`) | `end_reason=NULL`, `protection_ended_dttm=NULL` | 종료 상태에서 복귀해도 `completed_application_seq`·`animal_returns` 행·`adoption_reason`·`adoption_memo`는 **건드리지 않음**(이력으로 남음). PROTECTING+비삭제가 되면 AI 매칭 후보로 복귀(사진 변경 없으면 재매칭 요청은 하지 않음) |
| `ENDED` | `UNAVAILABLE` | `end_reason`=요청값(기본 `OTHER`), `protection_ended_dttm=now()` | 활성 신청(DRAFT/SUBMITTED/APPROVED) 자동 REJECTED |
| `RETURNED` | `UNAVAILABLE` | `end_reason='RETURNED'`, `protection_ended_dttm`=반환 행의 `reg_dttm`(신규) 또는 `mod_dttm`(덮어쓰기) | `animal_returns`는 보호동물당 1건 UNIQUE: **없으면 INSERT, 있으면 이름·연락처·`returned_user_seq`를 덮어쓰고 `mod_dttm` 갱신**. 동일 전화번호의 활성 USER가 정확히 1명이면 `returned_user_seq` 연결(본인·소유권 검증 아님). 활성 신청 자동 REJECTED |
| `ADOPTED` | `ADOPTED` | `end_reason='ADOPTED'`, `protection_ended_dttm=now()` | `adoption_reason`·`adoption_memo` 저장(재지정 시 덮어쓰기). **`completed_application_seq`는 건드리지 않음**(12.11로 채택된 값이 있으면 유지, 없으면 NULL 유지 = 외부 입양). 활성 신청 자동 REJECTED |

- **[확정 정책] 신청서 채택과의 관계:** 입양 신청서를 채택해 완료하는 흐름은 **12.11 전용**입니다(신청서 ADOPTED + 동물 ADOPTED + `completed_application_seq` 연결). 이 API의 `ADOPTED`는 신청서를 연결하지 않으며, v10 12.12 외부 입양을 대체합니다. 같은 상태를 다시 보내면(예: ADOPTED→ADOPTED) 사유·비고·종료 시각만 갱신합니다.
- **[확정 정책] 자동 거절:** 종료 상태(RETURNED/ADOPTED/ENDED)로 바뀔 때 해당 동물의 DRAFT/SUBMITTED/APPROVED 신청을 자연어 `rejectionReason`·`reviewedDttm=now()`·`reviewedUsrSeq=null`로 REJECTED 처리하고 각 신청자에게 `ADOPTION_REJECTED` 알림(12.14). 이미 종료 상태에서 다른 종료 상태로 바꿀 때는 보통 활성 신청이 없으므로 0건. **PROTECTING+AVAILABLE→UNAVAILABLE 전환은 기존 신청을 거절하지 않고 새 신청(12.4)·DRAFT 제출(12.6)만 차단**합니다(Q437 유지).
- **[확정 정책] 되돌리기:** 종료 상태에서 PROTECTING으로 돌아오면 자동 거절된 신청은 **되살리지 않습니다**(신청자가 다시 신청). 반환·입양 기록도 삭제하지 않습니다. 실종·발견 신고 자동 종료 없음.
- 범위는 토큰 `shelterSeq`. 공고기간만 바꾸는 호출(`status` 생략)은 상태 처리 없이 두 날짜만 갱신합니다.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(타 보호소 포함); 400 `COMMON_INVALID_INPUT`(날짜 형식·역전, 허용값 밖 `status`·`adoptionStatus`·`endReason`, 상태와 맞지 않는 추가 필드, RETURNED의 이름·번호 누락, ADOPTED의 `reason` 누락); 409 `ANIMAL_INVALID_STATUS_COMBINATION`(서버 계산 결과가 조합 CHECK에 어긋나는 경합). v10의 `ANIMAL_ALREADY_RETURNED`·`ANIMAL_NOT_PROTECTING`은 삭제(19장).

### 11.7 · 11.8 (삭제, Q479)

- v10의 11.7 `POST /.../returns`(반환 완료)와 11.8 `PATCH /.../protection-status`(보호 종료)는 11.6 관리 API의 `status=RETURNED`·`status=ENDED`로 흡수되어 삭제했습니다. 번호는 비워 둡니다.

### 11.9 DELETE /shelter-animals/{id}

- 성공 **204**(본문 없음, Q450). DRAFT/SUBMITTED/APPROVED 신청서가 하나라도 있으면 **409 `ANIMAL_HAS_ACTIVE_APPLICATIONS`**, 없으면 404 `ANIMAL_NOT_FOUND`. 삭제된 보호동물은 일반 조회/AI 제외.

### 11.10 GET /shelter-animals/me/{animalSeq} (보호소 전용 상세)

> **v11 신설(Q478):** 보호소 웹의 보호동물 상세·수정 화면용입니다. 11.2(앱)와 경로를 나눠 토큰 분기 없이 역할별 응답을 고정합니다. 범위는 토큰 `shelterSeq` 소속 비삭제 보호동물.

**성공 200 `data` [초안]** — 11.2의 목록 필드·상세 전용 필드에서 `isBookmarked`·`simulation`을 빼고 관리 정보를 더한 구성.
```json
{
  "animalSeq": 700,
  "classification": { "combinationSeq": 88, "speciesSeq": 1, "speciesName": "개", "breedSeq": 21, "breedName": "말티즈", "colorSeq": 3, "colorName": "흰색" },
  "gender": "UNKNOWN",
  "age": "1살 추정",
  "isNeutered": null,
  "weight": "3kg",
  "microchipNumber": null,
  "registrationNumber": null,
  "description": "사람을 잘 따름",
  "healthCheck": "기초 검진 완료",
  "vaccinationStatus": "예방접종 확인 중",
  "protectionStatus": "ADOPTED",
  "adoptionStatus": "ADOPTED",
  "noticeStartDate": "2026-10-29",
  "noticeEndDate": "2026-11-08",
  "endReason": "ADOPTED",
  "protectionEndedDttm": "2026-11-05T14:00:00+09:00",
  "rescueSeq": 301,
  "rescueInfo": { "rescuedDttm": "2026-10-29T09:30:00+09:00", "rescueReasonCode": "WANDERING", "rescueAddress": "대구광역시 ...", "rescueAddressDetail": null, "rescueMemo": null },
  "images": [{ "imageSeq": 1701, "imageUrl":"https://...", "displayOrder":1 }],
  "returnInfo": null,
  "adoptionInfo": {
    "completedApplicationSeq": 901,
    "applicantName": "김가람",
    "reason": "서류·방문 심사 통과",
    "memo": null,
    "completedDttm": "2026-11-05T14:00:00+09:00"
  },
  "applicationCounts": { "total": 5, "SUBMITTED": 0, "APPROVED": 0, "ADOPTED": 1, "REJECTED": 4 },
  "regDttm": "2026-10-29T12:00:00+09:00",
  "modDttm": "2026-11-05T14:00:00+09:00"
}
```
- **`returnInfo`** `{ returnSeq, recipientName, recipientPhone, returnedUserSeq, returnedDttm }`: `animal_returns` 행이 있으면 현재 상태와 무관하게 채움(반환 뒤 PROTECTING으로 복귀한 이력도 보임), 없으면 `null`. `returnedDttm`은 행의 `mod_dttm`이 있으면 그 값, 없으면 `reg_dttm`.
- **`adoptionInfo`** `{ completedApplicationSeq, applicantName, reason, memo, completedDttm }`: `protection_status=ADOPTED`이거나 `adoption_reason`/`completed_application_seq`가 있으면 채움, 아니면 `null`. `completedApplicationSeq`·`applicantName`은 12.11로 채택한 신청서(`applicant_name` 스냅샷)이고 외부 입양(11.6)이면 둘 다 `null`. `reason`·`memo`는 11.6으로 입력한 값(12.11만 거친 동물은 `null`). `completedDttm`은 `protection_ended_dttm`.
- **`applicationCounts`**: 12.16과 같은 규칙으로 이 동물의 신청을 상태별로 센 값(DRAFT·CANCELED 제외, `total`은 네 값의 합). 상세 화면의 "입양 신청" 박스용이며 "자세히 보기"는 `12.9?animalSeq=`로 연결합니다. 12.16의 `animalSeq` 파라미터는 유지합니다.
- `shelter` 블록은 **없습니다**(항상 토큰 보호소). `rescueInfo`·`classification`·구조 LEFT JOIN 규칙은 11.2와 같습니다. 댓글·북마크·모의 입양 정보는 없습니다(Q477·Q478).
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(없음·삭제·**다른 보호소 소속**, Q434); 403 `AUTH_FORBIDDEN`(USER 호출).

---
## 12. 입양 문항·신청·심사·완료 (ADOPTION)

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 12.1 | GET | `/adoption/questions` | USER/SHELTER | 현재 공통 문항(20개) 조회 |
| 12.2 | GET | `/adoption-applications` | USER | 내 입양 신청 내역 |
| 12.3 | GET | `/adoption-applications/{applicationSeq}` | 신청자/담당 SHELTER | 신청서 상세·답변·심사 결과 |
| 12.4 | POST | `/adoption-applications` | USER | DRAFT 생성 |
| 12.5 | PATCH | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | 미제출 답변 저장·수정 |
| 12.6 | POST | `/adoption-applications/submit` | USER | 최종 내용 저장 + 제출(animalSeq 기준, DRAFT 유무 무관, Q458) |
| 12.7 | DELETE | `/adoption-applications/{applicationSeq}` | 신청자(USER), DRAFT만 | DRAFT 물리 삭제 |
| 12.8 | POST | `/adoption-applications/{applicationSeq}/cancel` | 신청자(USER), SUBMITTED만 | 제출 신청 취소 |
| 12.9 | GET | `/adoption-applications/received` | SHELTER | 소속 보호소 신청 목록/심사 대기 |
| 12.10 | POST | `/adoption-applications/{applicationSeq}/review` | 담당 SHELTER | 서류 APPROVED/REJECTED |
| 12.11 | POST | `/adoption-applications/{applicationSeq}/complete` | 담당 SHELTER | 승인 신청 기준 실제 입양 완료 |
| 12.12 | — | (삭제, Q479) | — | 외부 입양 완료는 11.6 `status=ADOPTED`(신청서 미연결) |
| 12.13 | POST | `/adoption-applications/{applicationSeq}/fail` | 담당 SHELTER | 승인 후 진행 실패 → REJECTED |
| 12.15 | GET | `/adoption-applications/{applicationSeq}/simulation-reports` | 담당 SHELTER | 신청자가 이 동물로 진행한 모의 입양 리포트(18.21 참조) **[보류: 모의 입양 화면 미정]** |
| 12.16 | GET | `/adoption-applications/received/status-counts` | SHELTER | 상태별 건수(탭·보호동물 상세 박스, Q460) |

**[확정 정책] 공통 DTO `AnimalSummary`(Q458·Q459):** 12.2·12.3·12.9(보호소 목록)에 같은 형태로 들어가는 보호동물 요약입니다. 서버가 신청서의 `animal_seq`와 `shelter_seq`(신청 당시 보호소, 이관 기능이 없어 현재 보호소와 같음)로 JOIN해 채웁니다.
```json
{
  "animalSeq": 700,
  "speciesName": "개",
  "breedName": "믹스견",
  "colorName": "갈색",
  "gender": "FEMALE",
  "age": "2026년생",
  "weight": "3kg",
  "isNeutered": true,
  "microchipNumber": "410987654321098",
  "registrationNumber": null,
  "representativeImageUrl": "https://...",
  "shelterSeq": 7,
  "shelterName": "박범준보호소",
  "shelterAddress": "경북 구미시 3공단 3로 302"
}
```
- 화면의 "등록번호"는 `registrationNumber`(정부 공식 동물등록번호)이고 없으면 "-"(또는 "미등록")입니다. `animalSeq`는 내부 번호라 등록번호로 노출하지 않습니다(Q458). `weight`는 자유 텍스트 문자열(Q469).

### 12.1 GET /adoption/questions

**성공 200 `data` [초안]**
```json
{
  "questions": [
    {
      "questionSeq": 1,
      "displayOrder": 1,
      "questionText": "주거 형태",
      "questionType": "SINGLE_CHOICE",
      "isRequired": true,
      "options": ["아파트", "단독주택", "다세대", "오피스텔", "기타"],
      "condition": null
    },
    {
      "questionSeq": 20,
      "displayOrder": 20,
      "questionText": "이수일",
      "questionType": "DATE",
      "isRequired": true,
      "options": null,
      "condition": { "depends_on": 17, "operator": "EQ", "value": "이수", "required_when_matched": true }
    }
  ]
}
```

- **예시의 문구·보기·ID는 설명용입니다.** 실제 문항은 Flyway 시드로 적재하며(Q412), 문항 관리 API는 없습니다.
- **[확정 정책] 문항 구성(Q458):** 시드된 현재 문항 전체(**현재 20개**: 주거 및 동거 환경 10개 + 조건부 2개, 입양 신청서 4개, 입양 전 교육 1개 + 조건부 3개). 숫자를 고정해 검증하지 않습니다. `questionType`: `TEXT/SINGLE_CHOICE/MULTI_CHOICE/DATE`(DATE 답변은 `"YYYY-MM-DD"`, 화면은 `YYYY.MM.DD`). `options`는 DB `options_jsonb`의 문자열 배열; `condition`은 `condition_jsonb`의 `depends_on`(questionSeq), `operator`, `value`, `required_when_matched`.
- **[확정 정책] 섹션·안내 문구(Q458):** 단계·섹션 제목과 TEXT 문항의 placeholder는 응답에 없고, 웹·앱 프론트가 `questionSeq`로 매핑해 각자 디자인대로 고정 표시합니다. 그래서 `questionSeq`는 모든 환경에서 **1~20 고정**입니다(시드에서 직접 지정). `displayOrder`는 화면 순서용입니다.
- **질문 버전/템플릿 ID는 반환하지 않습니다.** 이전 제출 답변도 현재 문항으로 보여주는 정책입니다.

### 12.2 GET /adoption-applications · 12.3 GET /adoption-applications/{id}

**목록 [초안]** `?status=SUBMITTED&page=0&size=20`. 필터 `status`, `animalSeq`. **정렬은 `displayDttm,desc` 고정**(화이트리스트 없음, Q459).

**목록 200 `data.content[]` [초안]**
```json
{
  "applicationSeq": 901,
  "status": "SUBMITTED",
  "displayDttm": "2026-10-27T10:00:00+09:00",
  "submittedDttm": "2026-10-27T10:00:00+09:00",
  "rejectionReason": null,
  "animalSummary": { "animalSeq": 700, "speciesName": "개", "breedName": "믹스견", "shelterName": "박범준보호소", "shelterAddress": "경북 구미시 3공단 3로 302", "...": "AnimalSummary 전체" }
}
```
- **[확정 정책] `displayDttm`(Q459):** DRAFT는 마지막 임시저장 시각(`mod_dttm`, 없으면 `reg_dttm`), 그 외 상태는 `submittedDttm`(승인·거절·취소 뒤에도 제출일 유지). 제출 전 DRAFT가 자동 거절된 경우는 마지막 임시저장 시각. 카드의 날짜 표시와 정렬에 모두 이 값을 씁니다.
- 상태 라벨: `DRAFT` 임시저장, `SUBMITTED` 서류 제출 완료, `CANCELED` 서류 제출 취소, `APPROVED` 서류 승인, `ADOPTED` 최종 입양 승인, `REJECTED` 입양 거절. "전체 N건"은 `totalElements`. `rejectionReason`은 목록에도 유지합니다.

**상세 성공 200 `data` [초안]**
```json
{
  "applicationSeq": 901,
  "userSeq": 17,
  "status": "SUBMITTED",
  "animalSummary": { "animalSeq": 700, "...": "AnimalSummary 전체" },
  "careAddress": "경상북도 구미시 구미로 123길 45-6",
  "careAddressDetail": "구미아파트 7층 809호",
  "email": "example@email.com",
  "visitHopeDate": "2026-10-10",
  "answers": {
    "1": "아파트",
    "2": "가능"
  },
  "questions": [
    { "questionSeq":1, "questionText":"주거 형태", "questionType":"SINGLE_CHOICE", "displayOrder":1 }
  ],
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

- **[확정 정책]** DRAFT에서는 신청자 인적사항을 현재 `users`에서 읽고, SUBMITTED 이후에는 `applicant_name/birth_date/phone` 스냅샷을 조회합니다. 작성된 답변 `answers`는 JSONB 객체이며 키는 **문자열 `questionSeq`**입니다. 신청서 고정 필드 4개(`careAddress`·`careAddressDetail`·`email`·`visitHopeDate`, Q458)도 같은 응답에 있어 **한 번의 조회로 신청서 전체(Step 1 정보 + 문항 답변)**를 보여줄 수 있습니다.
- **[확정 정책] 화면 동작(Q459):** 진행 표시(신청서 작성→서류 심사→입양 심사→최종 승인)와 하단 버튼은 앱이 `status`로 그립니다. DRAFT는 삭제(12.7)·신청서 카드 탭 시 **이어 쓰기**, SUBMITTED는 제출 취소(12.8), REJECTED는 거절 사유 확인(`rejectionReason`), 그 외는 신청서 읽기 전용. APPROVED 이후 신청자 직접 취소는 없습니다.
- 수료증 기능은 **Q460에서 제외, Q462에서 컬럼 삭제**되어 `certificateUrl`은 없습니다.
- 신청자 본인 외에는 소속 보호소 담당자의 업무 조회로만 상세 노출. 탈퇴 신청자의 과거 신청서도 업무상 유지합니다(일반 공개 API에는 노출하지 않음). 담당 보호소는 12.15로 신청자의 해당 동물 모의 입양 리포트를 함께 열람할 수 있습니다(M14·M17, FK 없는 파생 조회, 화면 보류).
- 문항을 수정해도 과거 `answers`는 **현재 문항 문구**와 함께 표시합니다. 별도 이력/버전/원본 문항 스냅샷 요청 API는 만들지 않습니다.
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`. 신청자 본인도 담당 보호소도 아니면 403이 아니라 이 404로 존재를 감춥니다(Q434). **SHELTER가 DRAFT·CANCELED 신청서를 조회해도 404**(Q422·Q460).

### 12.4 POST /adoption-applications (DRAFT 생성) · 12.5 PATCH /adoption-applications/{id} (DRAFT 저장)

**12.4 요청 [초안]**
```json
{
  "animalSeq": 700,
  "answers": {},
  "careAddress": null,
  "careAddressDetail": null,
  "email": null,
  "visitHopeDate": null
}
```
**12.4 성공 201 `data` [초안]** `{ "applicationSeq":901, "status":"DRAFT", "animalSeq":700 }`.

**12.5 요청 [초안]**
```json
{
  "answers": { "1":"아파트", "2":"가능" },
  "careAddress": "경상북도 구미시 구미로 123길 45-6",
  "careAddressDetail": "구미아파트 7층 809호",
  "email": "example@email.com",
  "visitHopeDate": "2026-10-10"
}
```
**12.5 성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"DRAFT", "savedQuestionCount":2 }`.

- **[확정 정책] 저장 시점(Q458):** 앱은 신청 화면에 들어올 때 `12.2?animalSeq={id}&status=DRAFT`로 임시저장을 찾아 이어 씁니다. 저장은 **"임시저장" 버튼과 "프로필 편집" 버튼(저장 후 마이페이지 이동)에서만** 합니다. DRAFT가 없으면 12.4, 있으면 12.5. **"다음" 버튼은 저장하지 않으며**, 앱이 종료되면 마지막 임시저장 이후 입력은 사라지는 것을 감수합니다. 12.4와 12.5는 생성·수정의 성격이 달라 분리를 유지합니다(업서트 미채택).
- **[확정 정책] 12.4:** USER만 생성. 동물이 PROTECTING + AVAILABLE인 경우만 신규 신청을 받습니다. 동일 사용자·동물 비삭제 DRAFT/SUBMITTED/APPROVED 합계 최대 1개. DRAFT는 빈 `answers`·빈 고정 필드 허용. `shelterSeq`는 서버가 대상 보호동물에서 읽어 **신청 당시 스냅샷** 저장.
- **[확정 정책] 12.5:** DRAFT만 수정. 동물이 UNAVAILABLE이 되어도 기존 DRAFT 저장은 허용(제출만 차단, Q437). 요청의 `answers`는 **객체 전체 교체**이며 `questionSeq` 이외의 임의 키 불허. 조건부 숨김 답변은 FE/BE에서 제거. 주관식·날짜는 문자열, 단일 선택은 보기 문자열, 복수 선택은 문자열 배열 **[초안]**. 고정 필드는 `null`이면 비움, 미전달이면 유지.
- **오류 [초안]** 12.4: 409 `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`, `ADOPTION_ANIMAL_NOT_AVAILABLE`; 404 `ANIMAL_NOT_FOUND`. 12.5: 409 `ADOPTION_APPLICATION_NOT_DRAFT`, 400 `ADOPTION_INVALID_ANSWER`.

### 12.6 POST /adoption-applications/submit

**요청 [초안]** 12.5와 같은 최종 내용 전체 + `animalSeq`
```json
{
  "animalSeq": 700,
  "answers": { "1":"아파트", "2":"가능", "...": "..." },
  "careAddress": "경상북도 구미시 구미로 123길 45-6",
  "careAddressDetail": "구미아파트 7층 809호",
  "email": "example@email.com",
  "visitHopeDate": "2026-10-10"
}
```
**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"SUBMITTED", "submittedDttm":"2026-10-29T17:50:00+09:00" }`.

- **[확정 정책] 한 번의 호출·한 트랜잭션(Q458):** v4의 `/{applicationSeq}/submit`(본문 없음)을 대체합니다. 서버는 (1) 동물이 PROTECTING + AVAILABLE인지 확인하고, (2) 이 회원·동물의 활성 신청서를 `FOR UPDATE`로 조회해 **DRAFT가 있으면 그 행을 최종 내용으로 덮어쓴 뒤 SUBMITTED**, **없으면 새 행을 바로 SUBMITTED로 INSERT**, SUBMITTED·APPROVED가 이미 있으면 409. (3) 현재 문항 전체의 필수·조건·보기 검증과 고정 필드 검증(장소·희망일 필수, 희망일은 제출일 이후, 이메일 형식), (4) 숨겨진 답변 제거, (5) 신청자 실명·생년월일·전화번호 **제출 시점 스냅샷**, (6) 커밋 후 보호소에 `ADOPTION_SUBMITTED` 알림(17.8). DRAFT 유무와 관계없이 앱 호출은 항상 1회이고, 저장은 됐는데 제출은 실패하는 중간 상태가 없습니다. 제출 버튼 중복 탭은 부분 UNIQUE·잠금으로 두 번째가 409.
- **동물이 UNAVAILABLE로 바뀐 뒤에는 기존 DRAFT도 제출할 수 없습니다**(409 `ADOPTION_ANIMAL_NOT_AVAILABLE`, Q437). DRAFT 행 자체는 유지되며 다시 AVAILABLE이 되면 제출 가능합니다.
- 제출 후 답변·고정 필드는 불변입니다.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`; 409 `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`(SUBMITTED·APPROVED 존재), `ADOPTION_ANIMAL_NOT_AVAILABLE`; 400 `ADOPTION_REQUIRED_ANSWER_MISSING`(필수 문항·장소·희망일 누락), `ADOPTION_INVALID_ANSWER`(보기 밖 값, 과거 희망일, 이메일 형식).

### 12.7 DELETE /adoption-applications/{id} · 12.8 POST /.../cancel

- `12.7` DRAFT만 **DB에서 실제 물리 삭제**하고 답변도 제거. 성공 **204**(본문 없음, Q450); DRAFT가 아니면 409 `ADOPTION_APPLICATION_NOT_DRAFT`. 화면 "입양신청서 삭제".
- `12.8` **요청 [초안]** `{}` → 성공 200 `data:{applicationSeq,status:"CANCELED"}`. **SUBMITTED에서만 USER 직접 취소**(화면 "제출 취소"); APPROVED 뒤 직접 취소 API 없음. 취소 후 같은 동물 재신청은 새 행(Q271). `cancellationReason`이나 별도 취소 이력 컬럼은 추가하지 않음. "서류 제출 전 취소" 상태·화면은 없습니다(Q459).
- 탈퇴 시 활성(DRAFT/SUBMITTED/APPROVED) 신청을 CANCELED로 자동 변경하는 것은 별도 사용자의 직접 취소 제한과 구분합니다.

### 12.9 GET /adoption-applications/received

**요청 [초안]** `?animalSeq=700&status=SUBMITTED&page=0&size=20&sort=submittedDttm,desc`. 필터 `animalSeq`, `status`(`SUBMITTED/APPROVED/ADOPTED/REJECTED`만, DRAFT·CANCELED 지정 불가). 정렬 화이트리스트 `submittedDttm`(기본, 화면 "등록일 최신순"), `regDttm`. 페이지 번호 방식.

**성공 200 `data.content[]` [초안]**
```json
{
  "applicationSeq": 901,
  "status": "SUBMITTED",
  "applicantName": "김가람",
  "submittedDttm": "2026-10-10T10:00:00+09:00",
  "animalSummary": { "animalSeq": 700, "speciesName": "개", "breedName": "푸들", "representativeImageUrl": "https://...", "...": "AnimalSummary 전체" }
}
```
- **[확정 정책]** 소속 보호소(토큰 `shelterSeq`)에 제출된 신청 및 보존된 과거 기록을 업무상 조회. 심사 대상은 SUBMITTED. **DRAFT와 CANCELED는 목록·상세·건수 어디에도 노출하지 않습니다**(Q422 A·Q460). 신청자가 취소한 신청의 `ADOPTION_SUBMITTED` 알림을 보호소가 누르면 12.3이 404이므로 웹이 "취소된 신청입니다"를 안내합니다.
- 화면 라벨: `SUBMITTED` 심사 대기, `APPROVED` 서류 승인, `ADOPTED` 최종 승인, `REJECTED` 거절. 신청번호는 `applicationSeq`이며 표기 형식은 프론트가 정합니다(Q460). 수료증 첨부 여부(`certificateAttached`)는 제거했습니다(Q460).
- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(`status=DRAFT/CANCELED` 지정, 화이트리스트 밖 정렬).

### 12.16 GET /adoption-applications/received/status-counts

**요청 [초안]** `?animalSeq=700`(선택). **성공 200 `data` [초안]** `{ "total":23, "SUBMITTED":3, "APPROVED":16, "ADOPTED":2, "REJECTED":2 }`.

- **[확정 정책] (Q460)** 보호소 입양 신청 목록 상단 탭(전체/심사 대기/서류 승인/최종 승인/거절)의 숫자. DRAFT·CANCELED는 세지 않으며 `total`은 네 값의 합입니다. `animalSeq`를 주면 그 동물만 셉니다. 보호동물 상세의 "입양 신청" 박스는 v11부터 **11.10 응답의 `applicationCounts`**(같은 규칙)로 받으므로 별도 호출이 필요 없고, "자세히 보기"는 `12.9?animalSeq=`로 연결합니다(Q478). 범위는 토큰 `shelterSeq`.
- **오류 [초안]** 404 `ANIMAL_NOT_FOUND`(`animalSeq`가 다른 보호소 소속 포함, Q434).

### 12.10 POST /adoption-applications/{id}/review

**APPROVE 요청 [초안]** `{ "decision":"APPROVE" }`.

**REJECT 요청 [초안]** `{ "decision":"REJECT", "rejectionReason":"현재 보호 환경이 입양 조건에 부합하지 않습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"APPROVED", "reviewedDttm":"2026-10-29T17:50:00+09:00", "reviewedUsrSeq":55 }`.

- **[확정 정책]** `SUBMITTED → APPROVED/REJECTED`; REJECTED면 공백 아닌 **자연어 설명** 필수. `reviewedUsrSeq`에는 수동 처리자, `reviewedDttm`에는 심사 시각. 상태 이력/거절 단계/거절 코드 생성 금지.
- 여러 사용자 신청서의 APPROVED가 동시에 존재할 수 있습니다. **서류 승인만으로 보호동물을 ADOPTED로 만들지 않습니다.**
- 화면: "심사 대기"에서 서류 승인/서류 거절(사유 입력) → 확인 팝업 → 이 API. 서류 승인 상태의 거절은 12.13, 최종 승인은 12.11입니다.
- 심사 결과는 신청자에게 `ADOPTION_APPROVED` 또는 `ADOPTION_REJECTED` 알림(17.8, Q426·Q447).
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
- **[확정 정책] 11.6과의 분담(Q479):** 신청서 연결(`completed_application_seq`)은 **이 API만** 설정합니다. 보호동물 관리 API 11.6의 `status=ADOPTED`는 신청서 없이 동물 상태·사유·비고만 바꾸며(v10 12.12 외부 입양 대체) 이 값을 건드리지 않습니다. 12.11은 동물이 이미 ADOPTED(11.6으로 먼저 처리)여도 `completed_application_seq`가 NULL이면 채택을 허용하고 동물 상태는 그대로 둡니다(`ADOPTION_ANIMAL_ALREADY_FINISHED`는 **이미 다른 신청서가 연결된 경우**에만). 반환·종료 상태의 동물에 호출하면 활성 신청이 이미 자동 거절돼 `ADOPTION_APPLICATION_NOT_APPROVED`입니다.
- 자동 거절 `rejectionReason`은 실제 사유를 설명하는 한국어 문장; `reviewedDttm`은 자동 거절 시각, `reviewedUsrSeq=null`. 이미 승인된 다른 신청의 예전 심사시각은 별도로 남지 않습니다.
- 화면의 "최종 승인" 확인 팝업에 같은 동물의 다른 활성 신청 수(자동 거절 대상)를 안내하는 것을 디자인에 제안했습니다. 결과 수는 응답 `otherApplicationsAutoRejected`.
- 채택 신청자에게 `ADOPTION_COMPLETED` 알림, 자동 거절된 신청자들에게 `ADOPTION_REJECTED` 알림(17.8, Q426·Q447).
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 포함); 409 `ADOPTION_APPLICATION_NOT_APPROVED`, `ADOPTION_ANIMAL_ALREADY_FINISHED`.

### 12.12 (삭제, Q479)

- v10의 `POST /shelter-animals/{animalSeq}/external-adoption`(외부 입양 완료, 보류 상태)은 **11.6 관리 API의 `status=ADOPTED`**로 흡수되어 삭제했습니다. 신청서 없이 입양 완료 처리하면 `completed_application_seq`는 NULL로 남고 `adoption_reason`·`adoption_memo`에 처리 사유·비고를 기록합니다. 외부 입양자의 신상은 받지 않습니다(범위 외, 비고에 자유 기재).

### 12.13 POST /adoption-applications/{id}/fail

**요청 [초안]** `{ "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

**성공 200 `data` [초안]** `{ "applicationSeq":901, "status":"REJECTED", "rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다." }`.

- **[확정 정책]** 승인 후 실제 입양 진행 실패 시 해당 APPROVED 신청서를 REJECTED로 변경하고 `reviewedDttm`을 실패 처리 시각으로 갱신. 별도 입양 프로세스/실패 이력 테이블 생성 금지. 호출 주체는 담당 보호소. 신청자에게 `ADOPTION_REJECTED` 알림(Q447).
- **오류 [초안]** 404 `ADOPTION_APPLICATION_NOT_FOUND`(타 보호소 포함); 409 `ADOPTION_APPLICATION_NOT_APPROVED`; 400 `ADOPTION_REJECTION_REASON_REQUIRED`.

### 12.14 자동 상태 변경과 입양 알림 (클라이언트 이벤트)

| 발생 사건 | 신청서 변화 | 보호동물 변화 | 비고 |
|---|---|---|---|
| DRAFT 저장 | DRAFT 유지 | 없음 | 빈 답변 허용 |
| 제출(12.6) | DRAFT→SUBMITTED 또는 새 행 SUBMITTED | 없음 | 현재 문항·고정 필드 검증 |
| 서류 승인 | SUBMITTED→APPROVED | 없음 | 다수 승인 허용 |
| 서류 거절 | SUBMITTED→REJECTED | 없음 | 거절 사유 필수 |
| 직접 취소 | SUBMITTED→CANCELED | 없음 | 회원 직접 처리만 |
| 입양 완료(12.11) | 채택 APPROVED→ADOPTED; 다른 활성→REJECTED | ADOPTED/ADOPTED, 완료 FK 연결 | 동일 트랜잭션 |
| 입양 완료(11.6 `ADOPTED`, 신청서 미연결) | 활성→REJECTED | ADOPTED/ADOPTED, 완료 FK **그대로**(없으면 NULL) | 사유·비고 저장, 동일 트랜잭션(Q479) |
| 반환 완료(11.6 `RETURNED`) | 활성→REJECTED | RETURNED/UNAVAILABLE | 반환 기록 생성·덮어쓰기(Q479) |
| 보호 종료(11.6 `ENDED`) | 활성→REJECTED | ENDED/UNAVAILABLE | 자연어 자동 거절 |
| 종료 상태→PROTECTING 복귀(11.6) | **없음**(거절된 신청은 되살리지 않음) | PROTECTING + 요청 `adoptionStatus` | 종료 컬럼 NULL, 완료 FK·반환 행 유지(Q479) |
| AVAILABLE→UNAVAILABLE(11.6) | **기존 활성 상태 유지**(DRAFT 행도 유지하되 제출 불가) | PROTECTING+UNAVAILABLE | 새 신청(12.4)과 DRAFT 제출(12.6) 차단(Q437) |
| 회원 탈퇴 | 본인 활성→CANCELED | 없음 | ADOPTED 이력은 보존 |

**[확정 정책] 입양 알림 이벤트(Q426 A, 유형명 Q447):** 모든 행의 `targetType=ADOPTION`, `targetSeq=applicationSeq`. title·body는 17.1 표.

| 전이 | notificationType | 수신자 | 비고 |
|---|---|---|---|
| 제출(DRAFT→SUBMITTED) | `ADOPTION_SUBMITTED` | 담당 보호소 SHELTER 계정 | |
| 서류 승인 | `ADOPTION_APPROVED` | 신청자 | |
| 서류 거절 | `ADOPTION_REJECTED` | 신청자 | 거절 사유는 알림 `message`에 포함하지 않음(상세에서 조회) |
| 입양 완료(채택) | `ADOPTION_COMPLETED` | 채택 신청자 | |
| 자동 거절(타인 입양 완료·11.6 종료 상태 전환) | `ADOPTION_REJECTED` | 거절된 각 신청자 | 1건씩 |
| 승인 후 실패(12.13) | `ADOPTION_REJECTED` | 신청자 | |
| 직접 취소·DRAFT 저장·회원 탈퇴 | 없음 | 없음 | |

알림은 core-api가 트랜잭션 커밋 후 RabbitMQ 이벤트로 발행하고 notification 서비스가 `notifications` 저장 + FCM을 수행합니다(17.8).

---

## 13. 댓글·답글 (COMMENT)

> **공통 다형성 API [초안]**: 두 댓글 테이블을 하나의 REST 계약으로 표현하되 실제 저장은 `missing_report_comments` / `found_report_comments`로 각각 분리합니다. **보호동물 댓글은 v11(Q477)에서 제거**되어 `SHELTER_ANIMAL`은 댓글 대상이 아닙니다(`shelter_animal_comments` 테이블 삭제).

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 13.1 | GET | `/comments` | 로그인 | 대상 게시물의 루트 댓글 조회 |
| 13.2 | GET | `/comments/{commentType}/{commentSeq}/replies` | 로그인 | 해당 루트의 답글 조회 |
| 13.3 | POST | `/comments` | USER/SHELTER | 댓글·답글 생성 |
| 13.4 | PATCH | `/comments/{commentType}/{commentSeq}` | 작성자 | 댓글·답글 수정 |
| 13.5 | DELETE | `/comments/{commentType}/{commentSeq}` | 작성자 | 댓글 소프트 삭제 |
| 13.6 | GET | `/comments/{commentType}/{commentSeq}` | 로그인 | 댓글·답글 단건 조회(알림 진입용, Q446) |

**타입 매핑 — 반드시 구분**

| 게시물 `targetType` | 실제 게시물 PK | `commentType` | 댓글 테이블 |
|---|---|---|---|
| `MISSING` | `missing_report_seq` | `MISSING_COMMENT` | `missing_report_comments` |
| `FOUND` | `found_report_seq` | `FOUND_COMMENT` | `found_report_comments` |
| ~~`SHELTER_ANIMAL`~~ | — | — | (삭제, Q477) `targetType=SHELTER_ANIMAL`·`commentType=SHELTER_ANIMAL_COMMENT`는 400 `COMMON_INVALID_INPUT` |

### 13.1 GET /comments · 13.2 GET /.../replies

**루트 댓글 요청 [확정 정책]** `GET /comments?targetType=MISSING&targetSeq=101`. **페이징 없이 전량 반환**(Q480): 파라미터는 `targetType`·`targetSeq` 둘뿐이고 `page`·`size`·커서는 받지 않습니다(명시되지 않은 파라미터는 1.1 규칙대로 무시). **성공 200 `data`** = 댓글 DTO **배열**, **최신순(`commentSeq desc`) 고정**(입력창이 목록 위에 있는 디자인과 목격 제보의 최신성에 맞춤). 삭제된 루트도 `isDeleted:true`로 포함합니다(자식 답글 자리 유지). 게시물당 댓글 수가 적어 7.3·8.3과 같은 배열 응답으로 단순화했고, v10까지의 `beforeCommentSeq` 커서(Q456)는 폐기합니다.

**답글 요청 [확정 정책]** `GET /comments/MISSING_COMMENT/44/replies`. 파라미터 없음. **성공 200 `data`** = 댓글 DTO **배열**, **오래된 순(`regDttm asc`, 동률 `commentSeq asc`) 고정**(대화 순서대로 읽힘). 루트 하나의 답글 전량이며 v10까지의 `page`/`size` offset `Page` 응답(Q456)은 폐기합니다(Q480).

- **오류 [초안]** 400 `COMMON_INVALID_INPUT`(`targetType`·`targetSeq` 누락 또는 2종 밖, `commentType` 2종 밖); 404 `COMMENT_TARGET_NOT_FOUND`(게시물 없음·비노출); 404 `COMMENT_NOT_FOUND`(13.2의 루트가 없거나 답글을 루트로 지정).

**댓글 DTO [초안]**
```json
{
  "commentType": "MISSING_COMMENT",
  "commentSeq": 44,
  "parentCommentSeq": null,
  "targetType": "MISSING",
  "targetSeq": 101,
  "author": { "userSeq":17, "displayName":"반려가족", "role":"USER", "profileImage": { "imageSeq":1201, "imageUrl":"https://...", "displayOrder":1 } },
  "body": "근처에서 본 것 같아요.",
  "image": null,
  "isDeleted": false,
  "replyCount": 2,
  "regDttm": "2026-10-29T17:50:00+09:00",
  "modDttm": null
}
```
- **[확정 정책]** 답글 깊이 최대 1, 부모와 동일 게시물 필수. 삭제된 루트는 **삭제 안내로 표시하고 기존 자식 답글은 유지**합니다. 탈퇴한 루트 작성자의 답글은 보존하되 **새 답글 작성은 차단**합니다.
- **[가안]** 삭제된 댓글은 `body:null`·`image:null`·`isDeleted:true`로 반환합니다.
- **[확정 정책] 개수(Q456):** 게시물 상세(7.2·8.2)의 `commentCount`(화면 "N개의 댓글")는 **루트 + 답글 중 비삭제 행 수**, `replyCount`(화면 "답글 N개 더 보기")는 비삭제 답글 수입니다(11.2의 `commentCount`는 Q477로 삭제).
- **[확정 정책] 작성자 프로필(Q456):** `author.profileImage`는 회원이면 `users.profile_image_seq`, 보호소 담당자 댓글이면 보호소 프로필 사진, 탈퇴 작성자는 `null`(`displayName`은 `(탈퇴한 사용자)`).
- **[확정 정책] 앱 처리(Q456·Q480):** 깊이 1이므로 답글 아래에는 "답글달기"를 표시하지 않습니다. 목록이 전량 응답이므로 "더 보기" 요청은 없고, 내가 쓴 루트 댓글은 13.3 응답을 목록 맨 위에, 내가 쓴 답글은 펼친 답글 목록 맨 끝에 붙이거나(펼치지 않았으면 `replyCount`만 +1) 13.1·13.2를 다시 호출해 갱신합니다. 알림에서 들어와 13.6으로 맨 위에 고정한 댓글과 목록이 겹치면 `commentSeq`로 중복을 제거합니다. 사진 첨부는 4.1의 업로드 순서(보내기 시 4.2)를 따릅니다.

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
- 알림(17.8, Q426·Q436·Q446·Q447): **루트 댓글이면 게시물 작성자에게 `COMMENT`**, **답글이면 루트 댓글 작성자에게만 `COMMENT_REPLY`**(게시물 작성자에게는 보내지 않음). 어느 경우든 본인에게는 보내지 않습니다. 알림의 `targetType`/`targetSeq`는 **게시물**(`MISSING`/`FOUND`와 PK)이고, `commentSeq`는 새로 만든 댓글·답글 PK, `parentCommentSeq`는 답글일 때 루트 댓글 PK입니다. body(`message`)는 댓글 본문 최대 100자 [가안].
- **오류 [초안]** 400 `COMMENT_EMPTY`, `COMMENT_DEPTH_EXCEEDED`; 403 `IMAGE_NOT_OWNED`; 404 `COMMENT_TARGET_NOT_FOUND`; 409 `COMMENT_PARENT_UNAVAILABLE`.

### 13.4 PATCH /comments/{type}/{id} · 13.5 DELETE /comments/{type}/{id}

- `13.4` **요청 [초안]** `{ "body":"수정된 설명", "imageSeq":null }` → 성공 200 수정 댓글. `null`은 해제, 미전달은 유지. `body`·`imageSeq` 둘 다 빈 상태가 되면 400 `COMMENT_EMPTY`. 작성자 본인만(Q423), 아니면 404 `COMMENT_NOT_FOUND`.
- `13.5` 성공 **204**(본문 없음, Q450). 작성자 본인만(Q423). **루트 삭제 시 자식 답글은 삭제하지 않음**; 삭제 표시를 유지. 원래 이미지 참조가 마지막이면 공통 이미지 원본을 커밋 후 best-effort 삭제(Q427). 삭제 커밋 후 `CommentDeleted { targetType, commentSeq }` 이벤트(`targetType`은 게시물 코드)를 발행해 관련 `COMMENT`/`COMMENT_REPLY` 알림의 body를 "삭제된 댓글입니다"로 바꿉니다(Q448, 21.6).
- 댓글에 신고 기능을 적용할 때 신고 API는 **§15**를 사용합니다(`targetType=MISSING_COMMENT/FOUND_COMMENT`, 사유는 사용자·댓글용 세트, Q449).

### 13.6 GET /comments/{commentType}/{commentSeq}

**[확정 정책] 용도(Q446):** `COMMENT`·`COMMENT_REPLY` 알림(17.1)을 탭했을 때 게시물 상세를 열면서 해당 댓글을 댓글 영역 **최상단에 고정**해 보여주기 위한 단건 조회입니다. 알림에 게시물(`targetType`/`targetSeq`)과 `commentSeq`·`parentCommentSeq`가 있으므로 앱은 게시물 상세(7.2·8.2)와 이 API를 **동시에** 호출할 수 있습니다. 고정 아래의 일반 목록은 13.1(전량 배열)을 그대로 씁니다.

**요청 [초안]** `GET /comments/MISSING_COMMENT/45`. `commentType`은 알림의 `targetType`에서 정합니다(`MISSING`→`MISSING_COMMENT` 등, 13장 타입 매핑 표).

**성공 200 `data` [초안]** 13.1의 댓글 DTO와 같습니다(`targetType`·`targetSeq`·`parentCommentSeq`·`replyCount` 포함).

- 답글을 고정할 때 루트 댓글을 함께 보여주려면 `parentCommentSeq`로 이 API를 한 번 더 호출합니다(표시 방식은 앱이 정합니다).
- **[가안] 삭제된 댓글:** 404가 아니라 200으로 `isDeleted:true`, `body:null`, `image:null`을 반환합니다(13.1과 같은 규칙, R7 자리 유지). 탈퇴 작성자는 `(탈퇴한 사용자)`로 표시합니다.
- 고정한 댓글은 아래 13.1 목록에도 다시 나올 수 있으며 중복 표시 처리는 앱이 정합니다.
- **오류 [초안]** 404 `COMMENT_NOT_FOUND`(없음, 게시물 삭제·비노출·작성자 탈퇴 게시물 포함, Q434); 400 `COMMON_INVALID_INPUT`(`commentType`이 2종 밖, Q477).

---

## 14. 채팅 (CHAT) — 서비스 `chat`, 접두어 `/api/chat/v1`

> **[확정 정책] 서비스·저장소:** chat 서비스(8081)가 담당하며 `core` 스키마의 `chat_rooms`·`chat_participants`·`chat_messages`를 **쓰고**, `users`·`shelter_members`·`shelters`·`images`는 읽기 전용 JOIN으로만 참조합니다(Q420 D). 상대 표시명·프로필 URL·탈퇴 여부는 이 JOIN으로 구합니다.
>
> **[확정 정책] 실시간(S5 B):** WebSocket + STOMP, 엔드포인트 **`/api/chat/v1/ws`**(핸드셰이크는 `security.permit-paths`로 열어 둠). 브로커는 **RabbitMQ STOMP relay**(`enableStompBrokerRelay`)이며 chat 인스턴스를 여러 개 띄워도 같은 방을 공유합니다.
> - **CONNECT 인증:** CONNECT 프레임의 `Authorization: Bearer {accessToken}` 헤더를 `StompAuthChannelInterceptor`가 검증합니다. 없거나 틀리면 연결 거부(`AUTH_UNAUTHORIZED`/`AUTH_TOKEN_*`).
> - **SUBSCRIBE 인가:** 구독 목적지 `/topic/rooms/{chatRoomSeq}`. common의 `StompAuthChannelInterceptor`는 CONNECT 인증만 담당하고 DB에 접근하지 않으므로, **chat 모듈의 별도 `ChannelInterceptor`**가 SUBSCRIBE 프레임에서 요청자가 `chat_participants`에 있는지 확인하고 아니면 구독을 거부합니다(`CHAT_ROOM_NOT_PARTICIPANT`). `/user/queue/errors`와 `/user/queue/room-updates` 구독은 본인 큐이므로 검사 없이 허용합니다. 참여자가 아닌 방을 엿들을 수 없습니다.
> - **전송(R16 B):** 클라이언트가 STOMP **SEND** 프레임으로 `/app/rooms/{chatRoomSeq}/messages`에 보냅니다(14.5). 서버 `@MessageMapping`이 참여자·조합·탈퇴 상대·첨부 검증 → `chat_messages` 저장·커밋 → `/topic/rooms/{chatRoomSeq}`로 저장된 메시지 DTO를 브로드캐스트합니다(RabbitMQ relay 경유). 쓰기 경로는 이 하나뿐이며 **REST 메시지 전송은 없습니다.** 방 목록·생성·상세·과거 메시지 조회·읽음·나가기만 REST입니다.
> - **오류 응답(STOMP):** 검증 실패는 `@MessageExceptionHandler`가 송신자의 `/user/queue/errors`로 보냅니다. 본문은 HTTP와 **같은 `ApiResponse` 봉투**(`status`·`code`·`message`·`data:null`·`errors`·`traceId`)이고 코드도 19장과 같습니다(`CHAT_ROOM_NOT_PARTICIPANT`, `CHAT_INVALID_PAYLOAD`, `CHAT_COUNTERPART_DELETED`, `CHAT_SHARED_POST_NOT_FOUND`, `IMAGE_NOT_OWNED`, `IMAGE_NOT_FOUND`). 클라이언트는 연결 직후 `/user/queue/errors`를 구독합니다.
> - **푸시(R9 C):** 서버는 구독 여부를 판단하지 않습니다. 저장·커밋 후 chat이 `ChatMessageSent` 이벤트를 내고, notification이 참여자 중 **보낸 사람을 제외한 전원**에게 FCM **데이터 메시지**(`roomSeq`, `messageSeq`, `senderDisplayName`, `preview`)를 보냅니다. 표시 여부는 앱이 "현재 열려 있는 방" 기준으로 판단합니다(같은 방이면 무시, 아니면 로컬 알림). DB 알림 기록은 없습니다(17장).
> - **[확정 정책] 목록 실시간 갱신(Q454):** 채팅 목록 화면은 방마다 `/topic`을 구독하지 않고, 개인 채널 **`/user/queue/room-updates`** 하나로 내 모든 방의 변화를 받습니다(Spring user destination, 세션별 큐로 변환되어 본인만 수신, 같은 계정의 모든 기기로 배달). 서버는 `convertAndSendToUser(userSeq, "/queue/room-updates", payload)`로 보냅니다. 페이로드 **[초안]** `{ "type":"MESSAGE|READ|LEFT", "room":ChatRoomItem(14.1과 같은 형태), "totalUnreadMessageCount":123 }`. `MESSAGE`는 14.5 저장 후 **방의 참여자 각각**(보낸 사람 포함)에게, `READ`·`LEFT`는 14.6·14.7 후 **요청자 본인**에게 보냅니다. `unreadCount`·`totalUnreadMessageCount`는 **받는 사람 기준**으로 계산합니다. `LEFT`의 `room`은 `chatRoomSeq`만 있어도 됩니다. 앱은 받은 방을 목록 맨 위로 올리거나(없으면 새로 넣음) 갱신하고, `LEFT`면 목록에서 빼며, 자기가 가진 `lastMessageSeq`보다 오래된 갱신은 무시합니다. 인스턴스가 여러 개일 때를 대비해 `setUserDestinationBroadcast`·`setUserRegistryBroadcast`를 설정합니다.
> - **[확정 정책] 연결 생명주기(Q454):** 앱은 로그인 후 **포그라운드에 있는 동안** STOMP 연결 하나를 유지하고 `/user/queue/errors`·`/user/queue/room-updates`를 구독합니다. **구독을 먼저 하고** 14.1 첫 페이지·14.8을 조회하며, 조회 응답 전 도착한 갱신은 모았다가 적용합니다. 채팅방 화면에 들어가면 `/topic/rooms/{id}`를 SUBSCRIBE, 나오면 UNSUBSCRIBE(연결은 유지). 백그라운드로 가면 **유예 시간 [가안 10~30초]** 뒤 DISCONNECT(갤러리 사진 선택처럼 잠깐 나갔다 오는 경우 재연결 방지)하고, 이후 알림은 FCM이 맡습니다. 로그아웃 시 즉시 DISCONNECT. STOMP heart-beat로 끊김을 감지해 지수 백오프로 자동 재연결하며, `/user/queue`는 놓친 갱신을 보존하지 않으므로 **재연결·포그라운드 복귀 시 14.1·14.8을 다시 조회**합니다.
> - **[확정 정책] 토큰 만료(Q454):** STOMP는 CONNECT 때만 인증하므로 서버는 CONNECT 시 토큰 만료 시각을 세션에 저장하고, **SEND 때 만료됐으면 처리하지 않고** `/user/queue/errors`로 `AUTH_TOKEN_EXPIRED`를 보냅니다(Q433의 쓰기 재확인과 같은 위치). 앱은 2.3으로 토큰을 갱신하고 새 토큰으로 다시 CONNECT합니다. 구독(수신)만 하는 동안은 만료로 끊지 않습니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 14.1 | GET | `/chats/rooms` | USER/SHELTER | 내 대화방 목록·방별 미읽음(커서, Q454) |
| 14.2 | POST | `/chats/rooms` | USER/SHELTER | 상대와 대화방 생성 또는 기존 방 재입장 |
| 14.3 | GET | `/chats/rooms/{chatRoomSeq}` | 참여자 | 방 상세·상대방 정보 |
| 14.4 | GET | `/chats/rooms/{chatRoomSeq}/messages` | 참여자 | 메시지 내역 조회 |
| 14.5 | STOMP SEND | `/app/rooms/{chatRoomSeq}/messages` | 참여자 | TEXT/IMAGE/SHARED_POST 발송 (R16 B, REST 아님) |
| 14.6 | PATCH | `/chats/rooms/{chatRoomSeq}/read` | 참여자 | 마지막 읽은 메시지 갱신 |
| 14.7 | POST | `/chats/rooms/{chatRoomSeq}/leave` | 참여자 | 대화방 나가기/기존 기록 숨김 |
| 14.8 | GET | `/chats/unread-count` | USER/SHELTER | 전체 미읽음 메시지 수(탭 배지, Q454) |

### 14.1 GET /chats/rooms

**요청 [확정 정책]** `?unreadOnly=false&beforeLastMessageSeq=4000&size=20`. 최초는 `beforeLastMessageSeq` 생략, `size` 최대 100. **커서 방식**(Q454, Q417의 커서 예외): 정렬은 방의 **마지막 메시지 `chat_message_seq` 내림차순 고정**(IDENTITY라 최신순과 같고 방 사이에서도 유일해 동률 처리 불필요). 필터 `unreadOnly`(true면 미읽음이 1 이상인 방만, 화면 "안 읽음" 탭).

**성공 200 `data` [초안]** `{ "content":[ChatRoomItem], "nextBeforeLastMessageSeq":3870, "hasNext":true }`

**`ChatRoomItem` [초안]**
```json
{
  "chatRoomSeq": 501,
  "roomType": "SHELTER",
  "counterpart": {
    "type": "SHELTER",
    "userSeq": null,
    "shelterSeq": 7,
    "displayName": "행복동물보호센터",
    "profileImage": { "imageSeq": 1201, "imageUrl": "https://...", "displayOrder": 1 }
  },
  "lastMessage": { "chatMessageSeq": 4002, "messageType": "TEXT", "body": "확인했습니다." },
  "lastMessageSeq": 4002,
  "lastMessageDttm": "2026-10-29T17:50:00+09:00",
  "unreadCount": 2
}
```
- **[확정 정책] 노출 기준(Q454):** 내 `lastHiddenMessageSeq` 이후 메시지가 **하나라도 있는 방만** 반환합니다. 방만 만들고 메시지가 없는 방, 나간 뒤 새 메시지가 없는 방은 목록에 나오지 않습니다.
- **[확정 정책] 미읽음(Q454):** `unreadCount`는 **방별** 값으로, 그 방 메시지 중 `chat_message_seq`가 내 `lastReadMessageSeq`·`lastHiddenMessageSeq`보다 모두 크고 **보낸 사람이 내가 아닌** 메시지 수입니다. DB 카운터 컬럼이 아니라 **조회 시 계산**합니다(카운터 방식은 읽음 경쟁·이중 원천 문제로 미채택). 전체 합계는 14.8의 `totalUnreadMessageCount`.
- **[확정 정책] 상대 프로필(Q454):** `counterpart.profileImage`는 상대가 회원이면 `users.profile_image_seq`, 보호소면 `shelters.profile_image_seq`에서 읽습니다(chat의 읽기 전용 JOIN). 탈퇴한 상대는 `profileImage:null`, `displayName:"(탈퇴한 사용자)"`이며 앱이 기본 아바타를 씁니다.
- **[확정 정책] 미리보기:** 서버는 `messageType`과 `body`만 주고, 앱이 `IMAGE`는 "사진을 보냈어요"(본문이 있으면 본문), `SHARED_POST`는 "게시글을 공유했어요"로 표시합니다. 시각 표시는 `lastMessageDttm`.
- **[확정 정책] 페이징과 실시간 갱신:** 다음 페이지는 커서보다 오래된 방만 오므로, 그 사이 `room-updates`로 맨 위로 올라간 방이 다시 나오지 않습니다. 앱은 `chatRoomSeq` 기준 중복 제거를 방어적으로 둡니다.

### 14.2 POST /chats/rooms

**USER 대상 요청 [초안]** `{ "targetType":"USER", "targetUserSeq":18 }`.

**보호소 대상 요청 [초안]** `{ "targetType":"SHELTER", "targetShelterSeq":7 }`.

**성공 200 또는 최초 생성 201 `data` [초안]** `{ "chatRoomSeq":501, "roomType":"SHELTER", "reused":true }`.

- **[확정 정책]** USER↔USER, USER↔SHELTER 1:1만 지원; SHELTER↔SHELTER 불가. 상대 조합당 방 하나. USER형 `user_a_seq/user_b_seq`는 오름차순 기준. 보호소당 계정 하나. 기존 방 재사용 시 예전 나가기 이전 메시지는 계속 숨김.
- **[가안]** SHELTER도 먼저 USER에게 방을 만들 수 있습니다(발견 신고·입양 신청 화면의 작성자에게 연락). `targetShelterSeq`와 `targetUserSeq`는 분기별로 하나만 허용.
- **오류 [초안]** 409 `CHAT_UNSUPPORTED_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED`; 404 `CHAT_TARGET_NOT_FOUND`.

### 14.3 GET /chats/rooms/{id} · 14.4 GET /chats/rooms/{id}/messages

- `14.3` 성공 200 `data:{chatRoomSeq,roomType,counterpart,lastReadMessageSeq,lastHiddenMessageSeq,unreadCount}`. `counterpart`는 14.1과 같은 형태(`profileImage` 포함, Q454), `unreadCount`도 같은 정의입니다.
- `14.4` 요청 **[확정 정책]** `?beforeMessageSeq=4000&size=30` (**채팅 메시지는 offset 대신 seq 기준 역방향 커서**, Q417 커서 예외 중 하나). 최초는 `beforeMessageSeq` 생략. `size` 최대 100. **응답 [초안]** `{ "content":[ChatMessage], "nextBeforeMessageSeq":3990, "hasNext":true }`.
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

- **[확정 정책] 처리 순서:** `@MessageMapping`이 (1) 송신자가 `chat_participants`에 있는지, (2) 상대가 탈퇴하지 않았는지, (3) 본문 종류별 필수값을 검증 → `chat_messages` INSERT·커밋 → `/topic/rooms/{chatRoomSeq}`로 위 DTO 브로드캐스트(RabbitMQ relay) → **참여자 각각에게 `/user/queue/room-updates`(`MESSAGE`) 전송**(Q454, 받는 사람 기준 미읽음 수 포함) → `ChatMessageSent` 이벤트 발행(푸시는 notification, 14장 머리말). 인증 주체는 CONNECT 시 검증된 `AuthUser`이며 `senderUserSeq`는 그 `userSeq`(보호소 담당자도 users PK)입니다.
- **[확정 정책] 전송 확인·중복 제거:** 송신자는 브로드캐스트에서 자기가 보낸 `clientMessageId`를 받으면 "전송 완료"로 표시하고 `chatMessageSeq`를 붙입니다. `clientMessageId`는 클라이언트가 생성하는 임의 문자열(최대 64자)이며 서버는 저장하지 않고 **되돌려 주기만** 합니다. 재연결 후 재전송은 클라이언트 책임이며 서버 측 멱등 처리는 없습니다 **[가안]**.
- **[확정 정책] 본문 규칙:** TEXT는 비공백 `content` 필수. IMAGE는 `imageSeq` **정확히 하나 필수**(송신자가 업로드한 이미지만, 아니면 `IMAGE_NOT_OWNED`), `content`는 선택. SHARED_POST는 `sharedTargetType/sharedTargetSeq` 필수이며 코드 `MISSING/FOUND/SHELTER_ANIMAL`만. 다형성 대상 존재·접근 권한은 BE 검증. `imageUrl`은 서버가 `images`를 읽어 채운 접근 URL입니다.
- 상대가 탈퇴했다면 신규 메시지 거부. **차단 기능이 없으므로 차단 상태 검증은 작성하지 않습니다.** 메시지 개별 삭제·수정 기능 없음(Q423의 "수정·삭제"는 채팅에는 해당 없음).
- DB `notifications`에 채팅 메시지 알림을 저장하지 않으며 FCM 데이터 메시지만 발송합니다(R9 C). 발송 실패 시 재시도 없음.
- **오류(STOMP `/user/queue/errors`, 봉투는 HTTP와 동일):** `CHAT_INVALID_PAYLOAD`(400), `CHAT_ROOM_NOT_PARTICIPANT`(403), `IMAGE_NOT_OWNED`(403), `IMAGE_NOT_FOUND`(404), `CHAT_COUNTERPART_DELETED`(409), `CHAT_SHARED_POST_NOT_FOUND`(404). 오류 본문에 원래 `clientMessageId`를 `data`에 실어 어떤 전송이 실패했는지 알립니다 **[초안]**: `"data": { "clientMessageId": "c-20261102-0002" }`.

### 14.6 PATCH /chats/rooms/{id}/read · 14.7 POST /.../leave

- `14.6` 요청 [초안] `{ "lastReadMessageSeq":4003 }` → 성공 200 `data:{lastReadMessageSeq:4003, unreadCount:0, totalUnreadMessageCount:121}`. 메시지 ID는 **해당 방 소속**이어야 하며(아니면 400 `CHAT_MESSAGE_NOT_IN_ROOM`, BE가 복합 FK 위반 전에 검증) 읽음 포인터를 과거로 되돌리는 요청은 무시하고 현재 값을 반환합니다 [초안]. 커밋 후 **요청자 본인에게** `room-updates`(`READ`)를 보내 다른 기기·화면의 목록과 배지도 맞춥니다(Q454).
- `14.7` 요청 [초안] `{}` → 성공 200 `data:{lastHiddenMessageSeq:4003, totalUnreadMessageCount:119}`. 마지막 메시지 기준으로 과거 대화를 **본인에게만** 숨기며 방/메시지는 삭제하지 않습니다. 재입장 시 기존 방을 사용하고 이후 새 메시지만 표시. 나간 방은 **새 메시지가 올 때까지 14.1 목록에서 빠지며**, 커밋 후 본인에게 `room-updates`(`LEFT`)를 보냅니다(Q454).

### 14.8 GET /chats/unread-count

**성공 200 `data` [초안]** `{ "totalUnreadMessageCount": 123 }`.

- **[확정 정책] (Q454)** 내 모든 방의 미읽음 메시지 합계(방별 `unreadCount`를 더한 값과 같음, 14.1과 같은 정의로 조회 시 계산). 채팅 목록의 "안 읽음 123" 탭 배지와, 이후 하단 탭바 배지용입니다. 화면 진입·재연결·포그라운드 복귀 시 14.1 첫 페이지와 함께 조회하고, 그 사이는 `room-updates`의 `totalUnreadMessageCount`로 갱신합니다.

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
  "reasonCode": "HARASSMENT"
}
```
**성공 201 `data` [초안]** `{ "reportSeq":601, "status":"PENDING" }`.

- **[확정 정책] 대상(Q449·Q477):** `USER/MISSING/FOUND/MISSING_COMMENT/FOUND_COMMENT`의 **5종**. 보호동물 게시글(`SHELTER_ANIMAL`)은 신고 대상이 아니고(Q449), 보호동물 댓글은 기능 자체가 없어 `SHELTER_ANIMAL_COMMENT`도 v11에서 제외했습니다(Q477). `USER`는 일반회원만 신고 가능하며 **SHELTER 계정/보호소 자체 신고 불가**. `ADOPTION` 신청서와 채팅 메시지도 직접 신고 대상에서 제외.
- **[확정 정책] 화면 경로별 대상:** 게시글 신고는 `MISSING`/`FOUND`. 댓글 신고는 13장 댓글 DTO의 `commentType`을 그대로 `targetType`에 넣습니다(신고 대상은 작성자가 아니라 **댓글 자체**). 채팅방의 상대 신고는 `USER`이며, 상대가 보호소인 방(`roomType=SHELTER`)에서는 신고 버튼을 노출하지 않습니다.
- **[확정 정책] 사유(Q449):** 자유 입력 없이 **`reasonCode` 선택만** 받습니다(Q308의 `reasonDetail` 필수를 대체, `reasonDetail` 필드 없음). 대상별 허용 코드는 아래 2세트이며 DB CHECK(`ck_report_reason_by_target`)로도 강제합니다. BE가 먼저 검사해 조합이 맞지 않으면 400 `REPORT_REASON_NOT_ALLOWED`를 돌려줍니다.

| 대상 | 허용 `reasonCode` (화면 문구) |
|---|---|
| 게시글 `MISSING`, `FOUND` | `FALSE_INFO`(허위 정보 / 잘못된 동물 정보), `SPAM_AD`(스팸 / 광고), `OFFENSIVE`(욕설 / 불쾌한 내용), `OTHER`(기타) |
| 사용자 `USER`, 댓글 `MISSING_COMMENT`·`FOUND_COMMENT` | `HARASSMENT`(욕설 / 비방 / 괴롭힘), `SPAM_AD`(스팸 / 광고), `OBSCENE`(음란 / 부적절한 내용), `OTHER`(기타) |

- 같은 `(reporterUserSeq,targetType,targetSeq)`는 **전역 UNIQUE**로 단 한 번만 접수 가능; 현재 `PENDING`만 사용. 관리자 심사/처리/제재 API는 **구현 제외**이고 ADMIN 자체가 범위 외이므로 **접수된 신고를 열람하는 주체가 서비스 안에 없습니다**(DB 보관만). 사용자 자동 차단/차단 해제도 없음.
- **오류 [초안]** 409 `REPORT_ALREADY_EXISTS`; 400 `REPORT_TARGET_NOT_ALLOWED`(허용 밖 대상·SHELTER 계정), `REPORT_REASON_REQUIRED`(`reasonCode` 누락), `REPORT_REASON_NOT_ALLOWED`(대상과 사유 조합 불일치), `COMMON_INVALID_INPUT`(정의되지 않은 `reasonCode` 값); 404 `REPORT_TARGET_NOT_FOUND`.

---

## 16. 북마크 (USER)

> **[확정 정책] Q452:** 북마크는 `user` 패키지(R12)이며 경로를 `/users/me/bookmarks`로 통일합니다.

| # | Method | Path | 권한 | 기능 |
|---|---|---|---|---|
| 16.1 | GET | `/users/me/bookmarks` | USER | 내가 저장한 게시물 목록 |
| 16.2 | POST | `/users/me/bookmarks` | USER | 게시물 북마크 등록 |
| 16.3 | DELETE | `/users/me/bookmarks/{targetType}/{targetSeq}` | USER | 게시물 북마크 해제 |

### 16.1 GET /users/me/bookmarks

**요청 [초안]** `?targetType=MISSING&page=0&size=20&sort=regDttm,desc` (`targetType` 생략 시 세 종류 통합). 필터 `targetType`. 정렬 화이트리스트 `regDttm`.

**목록 `data.content[]` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101, "regDttm":"...", "postSummary":{...} }`.

- **[확정 정책]** 원본 게시물 자체가 삭제되거나 작성자가 탈퇴하면 **목록에서 비노출**. 신고가 CLOSED거나 보호동물이 ADOPTED 상태여도 북마크는 유지됩니다.

### 16.2 POST /users/me/bookmarks · 16.3 DELETE /users/me/bookmarks/{targetType}/{targetSeq}

**요청 [초안]** `{ "targetType":"MISSING", "targetSeq":101 }`.

- `16.2` **성공 201 `data` [초안]** `{ "bookmarkSeq":701, "targetType":"MISSING", "targetSeq":101 }`.
- `16.3` **성공 204**(본문 없음, Q450). 경로는 `DELETE /users/me/bookmarks/{targetType}/{targetSeq}`로 확정합니다(본문 없는 DELETE, Q452). 없는 북마크 해제는 404 `USER_BOOKMARK_NOT_FOUND`. 북마크는 `user` 도메인에 속합니다(R12).
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

**요청 [초안]** `?isRead=false&notificationType=AI_MATCH&page=0&size=20&sort=regDttm,desc`. 필터 `isRead`, `notificationType`(아래 9종 중 하나). 정렬 화이트리스트 `regDttm`.

**목록 200 `data.content[]` [초안]** AI 매칭 예시:
```json
{
  "notificationSeq": 805,
  "notificationType": "AI_MATCH",
  "title": "내 아이 관련 제보가 올라왔어요!",
  "message": "대구광역시 중구에서 목격제보가 올라왔어요. 확인해보세요.",
  "targetType": "FOUND",
  "targetSeq": 210,
  "commentSeq": null,
  "parentCommentSeq": null,
  "missingReportSeq": 101,
  "isRead": false,
  "readDttm": null,
  "regDttm": "2026-10-29T17:50:00+09:00"
}
```

답글 알림 예시:
```json
{
  "notificationSeq": 806,
  "notificationType": "COMMENT_REPLY",
  "title": "회원님의 댓글에 답글이 달렸어요",
  "message": "와 정말 다행이네요!",
  "targetType": "MISSING",
  "targetSeq": 101,
  "commentSeq": 45,
  "parentCommentSeq": 44,
  "missingReportSeq": null,
  "isRead": false,
  "readDttm": null,
  "regDttm": "2026-10-30T09:12:00+09:00"
}
```

**[확정 정책] 유형 9종과 title·body(Q447).** title은 DB에 저장하지 않고 서버 enum의 유형별 고정 문구로 응답과 FCM title을 채웁니다(문구를 바꾸면 과거 알림도 새 문구로 표시). `message`는 body이며 **발행 서비스가 생성 시점에 템플릿으로 완성해 저장한 스냅샷**입니다. 문구와 템플릿은 **[가안]**입니다.

| notificationType | 발생 | 수신자 | title | message(body) 템플릿 |
|---|---|---|---|---|
| `COMMENT` | 내 실종·발견 신고에 루트 댓글 | 게시물 작성자(USER) | 회원님의 게시글에 댓글이 달렸어요 | 댓글 본문(최대 100자, 초과 시 `…`). 이미지만 있으면 "사진을 남겼어요" |
| `COMMENT_REPLY` | 내 루트 댓글에 답글 | 루트 댓글 작성자 | 회원님의 댓글에 답글이 달렸어요 | 답글 본문(같은 규칙) |
| `AI_MATCH` | 실종×발견 / 실종×보호동물 매칭 | 실종 신고 작성자 | 내 아이 관련 제보가 올라왔어요! | FOUND: "{시도} {시군구}에서 목격제보가 올라왔어요. 확인해보세요."(`districtCode` NULL이면 "근처에서 목격제보가 올라왔어요. 확인해보세요.") / SHELTER_ANIMAL: "{보호소명}에서 비슷한 아이를 보호하고 있어요. 확인해보세요." |
| `ADOPTION_SUBMITTED` | 입양 신청 제출 | 담당 보호소 SHELTER 계정 | 새 입양 신청이 도착했어요 | "{품종} 입양 신청서가 제출되었어요." |
| `ADOPTION_APPROVED` | 서류 승인 | 신청자 | 입양 신청이 승인되었어요 | "{보호소명} {품종} 입양 신청 결과를 확인해보세요." |
| `ADOPTION_REJECTED` | 수동 거절·자동 거절·승인 후 실패 | 신청자 | 입양 신청이 거절되었어요 | 위와 같음(거절 사유는 넣지 않고 상세에서 조회) |
| `ADOPTION_COMPLETED` | 입양 완료(채택) | 채택 신청자 | 입양이 완료되었어요 | 위와 같음 |
| `SHELTER` | 발견 신고 자동 배정 | 담당 보호소 SHELTER 계정 | 담당 발견 신고가 배정되었어요 | "{시군구}에서 발견 신고가 접수되었어요."(NULL이면 "담당 구역에서 발견 신고가 접수되었어요.") |
| `SYSTEM` | 없음(유형만 예약) | — | (예약) | (예약) |

- `REPORT`·`SIMULATION` 유형은 없습니다. 모의 입양 알림은 이 목록에 **섞이지 않고** 18.22~18.23의 별도 API로 조회합니다(M13·M16). `targetType/targetSeq` 둘 다 NULL인 운영 알림 허용. 연결 대상이 있다면 서버에서 쌍과 접근 권한 검사.
- **[확정 정책] 이동 정보(Q446·Q477):** `COMMENT`/`COMMENT_REPLY`의 `targetType`/`targetSeq`는 **댓글이 달린 게시물**(`MISSING`/`FOUND`와 PK. 보호동물 댓글이 없어 `SHELTER_ANIMAL`은 오지 않음), `commentSeq`는 알림을 발생시킨 댓글·답글 PK, `parentCommentSeq`는 답글일 때 루트 댓글 PK입니다. 다른 유형에서는 두 필드가 `null`입니다. 앱은 알림을 탭하면 게시물 상세(7.2·8.2)와 댓글 단건 조회(13.6)를 **동시에** 호출하고, 그 댓글을 댓글 영역 **최상단에 고정**합니다.
- **[확정 정책] 삭제된 댓글(Q448):** 알림을 보낸 뒤 해당 댓글이 삭제되면 notification이 `message`를 "삭제된 댓글입니다"로 바꿉니다(21.6 `CommentDeleted`). title은 그대로입니다. 댓글 수정은 반영하지 않습니다(스냅샷).
- **행위자:** `actor` 필드는 없습니다(Q447, 화면에서 쓰지 않아 DB 컬럼과 함께 제거).
- `AI_MATCH`에서 `targetType`은 **`FOUND` 또는 `SHELTER_ANIMAL`**, `targetSeq`는 매칭 상대 PK. `missingReportSeq`는 원본 실종 신고. **`matchedTargetType/matchedTargetSeq` 중복 필드 없음**.
- `ADOPTION_*`의 클릭 대상 `targetType=ADOPTION`은 입양 신청 `applicationSeq`를 가리킵니다. `SYSTEM`은 운영 공지용으로 **유형만 예약**하며 이번 범위에서 발송 주체·API가 없습니다(Q426). `SHELTER`는 담당 발견 신고 배정 알림이며 보호소가 받는 모든 알림의 범용 타입이 아닙니다(입양 제출은 `ADOPTION_SUBMITTED`).

### 17.2~17.5 알림 읽음·삭제

- `17.2` 성공 200 `data:{notificationSeq,isRead:true,readDttm:"..."}`; 타인 알림이거나 삭제된 알림이면 404 `NOTIFICATION_NOT_FOUND`(Q434).
- `17.3` 성공 200 `data:{updatedCount:5}` [초안]. 읽은 시간은 서버가 설정.
- `17.4` 및 `17.5` 성공 **204**(본문 없음, Q450). **[확정 정책]** `is_deleted=true`로 소프트 삭제(개별/전체), 별도 숨김 기능 없음. 17.4는 타인·삭제된 알림이면 404 `NOTIFICATION_NOT_FOUND`.
- 삭제된 AI_MATCH 알림도 **과거 알림 존재 확인에 포함**되어 같은 쌍의 알림을 재생성하지 않습니다.

### 17.6 POST /notifications/device-tokens · 17.7 DELETE /.../current

**등록 요청 [초안]** `{ "fcmToken":"fcm-device-token" }`.

**등록 성공 200 `data` [초안]** `{ "active":true }`.

- **[확정 정책]** 계정당 활성 FCM 토큰 최대 1개, 동일 토큰을 여러 계정에서 동시에 활성화 금지. 등록은 **upsert**이며 같은 토큰을 다시 보내도 200(Q415의 유일한 멱등 예외). 다른 토큰이면 기존 토큰을 비활성화하고 새 토큰을 활성화합니다. 다른 계정에서 활성인 토큰이면 그 계정의 토큰을 비활성화하고 요청 계정으로 옮깁니다(기기 양도). 모든 충돌을 서버가 이렇게 해소하므로 **409를 돌려주는 경우가 없고, 종전 `NOTIFICATION_DEVICE_TOKEN_CONFLICT`는 삭제**합니다(Q439). 탈퇴 시 계정의 활성 토큰 전부 비활성화.
- `17.7` **요청 [초안]** `DELETE /notifications/device-tokens/current` 본문 `{ "fcmToken":"fcm-device-token" }`. 성공 **204**(본문 없음, Q450); DB에는 활성 해제 시각 `mod_dttm` 기록. **역할 분담(Q419 A):** 로그아웃(2.4)에 `fcmToken`을 실으면 core-api가 같은 비활성화를 이벤트로 요청하므로 앱은 17.7을 따로 부르지 않아도 됩니다. 17.7은 로그아웃 없이 푸시만 끄는 설정 화면용입니다.
- 모의 입양 푸시(18장)도 **같은 활성 FCM 토큰**으로 발송합니다. 별도 토큰 등록은 없습니다(M16). simulation은 `device_tokens`를 읽지 않고 `SimulationPushRequested` 이벤트만 보내며, 토큰 조회와 FCM 발송은 notification이 합니다.

### 17.8 알림 생성·AI 매칭 발송 조건 (내부 비공개 동작)

**[확정 정책] 생성 이벤트 최소 집합(Q426 A).** 아래 외의 상태 전이는 알림을 만들지 않습니다.

| 유형 | 이벤트 | 수신자 | 발행 서비스 |
|---|---|---|---|
| `COMMENT` | 내 게시물(실종·발견)에 루트 댓글 | 게시물 작성자(USER). 본인 댓글 제외. target=게시물, `commentSeq`=댓글(Q446). 보호동물 댓글 없음(Q477) | core-api |
| `COMMENT_REPLY` | 내 루트 댓글에 답글 | 루트 댓글 작성자. 본인 답글 제외. 게시물 작성자에게는 중복 발송 안 함. target=게시물, `commentSeq`=답글, `parentCommentSeq`=루트 | core-api |
| `AI_MATCH` | 실종×발견 / 실종×보호동물 매칭 | 실종 작성자 **한 명**, 쌍당 생애 1회 | core-api(직접 INSERT) |
| `ADOPTION_*` (4종) | 12.14 표의 전이 | 표 참조 | core-api |
| `SHELTER` | 발견 신고 자동 배정(8.4) | 담당 보호소 SHELTER 계정 | core-api |
| `SYSTEM` | 없음(유형만 예약) | — | — |
| 채팅 수신 | **FCM 데이터 메시지만**, `notifications` DB 행 저장하지 않음. 표시 여부는 앱이 현재 방 기준으로 판단(R9 C) | 보낸 사람 외 참여자 | chat → `ChatMessageSent` → notification |
| 모의 입양 | `notifications`에 저장하지 않음. `simulation_notifications` 별도 저장 + FCM(18.22) | 세션 소유 USER | simulation |

AI 매칭 규칙: 실종×발견은 ACTIVE·비삭제·비탈퇴 양쪽 검증, 실종×보호동물은 실종 ACTIVE + 보호동물 PROTECTING·비삭제(입양 상태 무관). 발생 계기는 실종/발견/보호동물 신규 등록, CLOSED→ACTIVE 재개, 사진 배열 diff. 같은 쌍은 `notification_type='AI_MATCH'` + `(missing_report_seq,target_type,target_seq)` 생애 1회.

**AI 중복 알림 필수 처리 순서 [확정 정책]**: ① 유효 동물/신고 상태 검사 → ② 해당 `missing_reports` 행에 `SELECT ... FOR UPDATE` 획득 → ③ `is_deleted=true` 알림까지 포함해 동일 쌍 `AI_MATCH` 중복 조회 → ④ 없으면 `message TEXT NOT NULL` 및 `targetType/targetSeq`로 한 행 INSERT(core-api 트랜잭션, 같은 `core` 스키마) → ⑤ 트랜잭션 커밋 → ⑥ `NotificationCreated` 이벤트(이미 저장됨, 발송만 요청)를 RabbitMQ로 발행하면 notification이 DB에 저장된 **똑같은 message 문자열**을 FCM body로 발송. 별도 전용 AI UNIQUE 인덱스는 **없습니다**. FCM 실패 시 DB 알림은 보존하고 재시도하지 않습니다. 그 외 유형은 발행 서비스가 `NotificationRequested` 이벤트(저장 + 발송 요청)에 수신자·유형·완성된 message(body)·target·`commentSeq`·`parentCommentSeq`를 실어 보내고 notification이 INSERT + FCM을 수행합니다. 두 이벤트는 이름으로 구분합니다(21.6).

**[확정 정책] FCM 페이로드(Q446·Q447):** 저장형 알림(`NotificationRequested`·`NotificationCreated`)의 푸시는 notification 메시지의 **title = 유형별 서버 고정 문구**(17.1 표), **body = DB `message`**로 보내고, 앱이 푸시를 탭했을 때 17.1을 거치지 않고 바로 이동할 수 있도록 **data**에 `notificationSeq`, `notificationType`, `targetType`, `targetSeq`, `commentSeq`, `parentCommentSeq`를 싣습니다(값이 없는 필드는 생략). 채팅(`ChatMessageSent`)은 기존대로 data 메시지만 보냅니다(R9 C).

**AI 워커 연동 [확정 정책, Q432]:** 매칭 요청과 결과 모두 RabbitMQ입니다. core-api는 실종·발견·보호동물의 등록·CLOSED→ACTIVE 재개·사진 배열 diff 때 `{ "sourceType": "MISSING|FOUND|SHELTER_ANIMAL", "sourceSeq": 101, "imageKeys": ["images/2026/11/a.jpg"], "speciesCode": "DOG", "occurredAt": "2026-11-02T17:50:00+09:00" }`를 발행합니다. 워커는 DB를 읽지 않고 페이로드와 S3 키만으로 동작하며, 유사하다고 판단한 쌍마다 `AiMatchFound { "missingReportSeq": 101, "targetType": "FOUND|SHELTER_ANIMAL", "targetSeq": 210, "score": 0.91, "modelVersion": "v1" }`를 발행합니다. `score`·`modelVersion`은 저장하지 않는 로그용 선택 필드이고 임계값 판단은 워커 몫입니다. core-api는 수신 시점에 위 ①(ACTIVE·PROTECTING·비삭제·비탈퇴) 재검사부터 ⑥까지 수행합니다. 워커의 임베딩 저장 위치는 POC 범위로 남깁니다.

---

## 18. 모의 입양 (SIMULATION) — 서비스 `simulation`, 접두어 `/api/simulation/v1`

> 이 장은 DB v17의 모의 입양 11개 테이블(`simulation_*`)의 API입니다. 결정 번호 M1~M17은 DB v17 추적표와 같습니다. 18.21(=12.15)만 core-api가 제공합니다.
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
| 18.19 | GET | `/simulations/reports` | USER | 내 리포트 목록 |
| 18.20 | GET | `/simulations/reports/shelter` | SHELTER | 자기 보호동물 세션 리포트 `?animalSeq=` |
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

**도메인 패키지 목록 (R11·R12·Q452·Q453 확정)** — 접두어 = 패키지명 대문자. 기준정보(동물 기준정보·행정구역)는 `catalog`, 반려동물·북마크는 `user`에 속하고, `ANIMAL_`은 보호동물이 단독으로 씁니다. **경로 접두어 = 패키지(Q454 경로, v7 복원):** 각 도메인 자원을 "내 것만" 모아 보는 API(7.3·8.3·8.8·10.1·11.3·12.2·12.9·12.16·18.19·18.20. 10.7은 Q471로 삭제)도 해당 도메인 경로 아래에 두므로 URL 첫 세그먼트가 곧 패키지입니다(1.1 경로 구조). 예외는 소유자 부속 자원인 반려동물·북마크(`/users/me/...`, `user` 패키지)뿐입니다.

| 서비스 | 패키지 | 담당 | 접두어 |
|---|---|---|---|
| core-api | `auth` | 카카오·LOCAL 로그인, 토큰, 가입 컨텍스트 (2장) | `AUTH_` |
| core-api | `user` | 회원 프로필·탈퇴 (3장), **반려동물** (6장, Q452), **북마크** (16장) | `USER_` |
| core-api | `image` | 공통 이미지 업로드·연결·참조 정리 (4장) | `IMAGE_` |
| core-api | `catalog` | 기준정보: 종·품종·색상·조합, 행정구역 **`districts`** (5장, Q453) | `CATALOG_` |
| core-api | `missing` | 실종 신고 (7장) | `MISSING_` |
| core-api | `found` | 발견 신고·자동 배정 (8장) | `FOUND_` |
| core-api | `shelter` | 보호소 정보·배정표 (9장) | `SHELTER_` |
| core-api | `rescue` | 구조 기록 (10장) | `RESCUE_` |
| core-api | `animal` | 보호동물·반환 (11장) | `ANIMAL_` |
| core-api | `adoption` | 입양 문항·신청·심사·완료 (12장, 수료증은 Q460·Q462에서 제외) | `ADOPTION_` |
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
| `CATALOG_` (주소 검색, Q467) | `CATALOG_GEOCODE_UNAVAILABLE` | 503 | 5.6에서 네이버 Geocoding 호출 실패·타임아웃(결과 0건은 오류가 아니라 빈 배열) |
| `USER_` (반려동물, Q452) | `USER_PET_NOT_FOUND` | 404 | 없음·삭제·**타인 소유**(Q434; `PET_NOT_OWNED` 삭제, v4에서 `PET_NOT_FOUND` 개명). 6장 전용이며 7.4에서는 더 이상 발생하지 않음(Q466) |
| `MISSING_` | `MISSING_REPORT_NOT_FOUND` | 404 | 없음·삭제·비노출·**타인 신고**(Q434) |
| `MISSING_` | `MISSING_ACTIVE_ALREADY_EXISTS`, `MISSING_REPORT_CLOSED` | 409 | 회원당 ACTIVE 1건 제약 위반 / CLOSED 본문 수정(Q425) |
| `FOUND_` | `FOUND_REPORT_NOT_FOUND`, `FOUND_REPORT_CLOSED`, `FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE` | 404/409/409 | 없음·삭제·**타인 신고**(8.6은 작성자·담당 보호소 외, Q434) / CLOSED 본문 수정 / 비삭제 보호소가 하나도 없음 |
| `SHELTER_` | `SHELTER_NOT_FOUND` | 404 | 보호소 없음·삭제(`SHELTER_NOT_OWNED`는 토큰 기반이라 발생 경로 없어 삭제, Q442) |
| `RESCUE_` | `RESCUE_ACTIVE_ALREADY_EXISTS` | 409 | 동일 발견 PROCESSING/DONE 1건 제약 |
| `RESCUE_` | `RESCUE_NOT_FOUND`, `RESCUE_INVALID_TRANSITION`, `RESCUE_NOT_DONE` | 404/409/409 | 없음·삭제·**다른 보호소의 구조**(Q434) / 전이 불가 / 11.4에서 DONE 아닌 구조 참조(Q442) |
| `RESCUE_` | `RESCUE_RESULT_REASON_REQUIRED` | 400 | 실패·취소 사유 누락 (`RESCUE_COMPLETION_FIELDS_REQUIRED`는 Q461에서 삭제: 구조 일시·장소는 접수 시 Bean Validation `COMMON_INVALID_INPUT`) |
| `RESCUE_` | `RESCUE_HAS_ANIMALS` | 409 | 참조 보호동물 있는 구조 삭제 시도 |
| `ANIMAL_` (보호동물) | `ANIMAL_NOT_FOUND`, `ANIMAL_INVALID_STATUS_COMBINATION` | 404/409 | 없음·삭제·**다른 보호소 소속**(쓰기 API, Q434) / 보호·입양 상태 조합 CHECK 위반 |
| `ANIMAL_` (보호동물) | `ANIMAL_HAS_ACTIVE_APPLICATIONS` | 409 | 활성 신청이 있는 보호동물 삭제 제한(11.9). `ANIMAL_ALREADY_RETURNED`·`ANIMAL_NOT_PROTECTING`은 11.6 관리 API가 어느 상태에서든 전환을 허용하므로 발생 경로가 없어 삭제(Q479) |
| `ADOPTION_` | `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`, `ADOPTION_ANIMAL_NOT_AVAILABLE` | 409 | 활성 신청 중복 / 새 신청·DRAFT 제출 불가(Q437) |
| `ADOPTION_` | `ADOPTION_APPLICATION_NOT_FOUND` | 404 | 없음·타인 신청서·타 보호소 신청서·SHELTER가 DRAFT 조회(Q434; `ADOPTION_APPLICATION_ACCESS_DENIED` 삭제) |
| `ADOPTION_` | `ADOPTION_APPLICATION_NOT_DRAFT`, `ADOPTION_APPLICATION_NOT_SUBMITTED`, `ADOPTION_APPLICATION_NOT_APPROVED` | 409 | 상태 전이 원천 부적합 |
| `ADOPTION_` | `ADOPTION_REQUIRED_ANSWER_MISSING`, `ADOPTION_INVALID_ANSWER` | 400 | 조건부 필수·보기 응답 오류 |
| `ADOPTION_` | `ADOPTION_REJECTION_REASON_REQUIRED`, `ADOPTION_ANIMAL_ALREADY_FINISHED` | 400/409 | 사유 누락 / 12.11에서 동물에 이미 다른 신청서가 `completed_application_seq`로 연결됨(Q479) |
| `COMMENT_` | `COMMENT_EMPTY`, `COMMENT_DEPTH_EXCEEDED`, `COMMENT_PARENT_UNAVAILABLE` | 400/400/409 | 댓글 비어 있음 / 답글 제한 / 삭제·탈퇴 루트에 답글 |
| `COMMENT_` | `COMMENT_NOT_FOUND`, `COMMENT_TARGET_NOT_FOUND` | 404 | 댓글 없음·타인 댓글 수정·13.6에서 댓글 없음 또는 게시물 삭제·비노출(Q446) / 게시물 없음 |
| `CHAT_` | `CHAT_UNSUPPORTED_PARTICIPANTS`, `CHAT_COUNTERPART_DELETED` | 409 | 채팅 조합 / 탈퇴 상대 |
| `CHAT_` | `CHAT_INVALID_PAYLOAD`, `CHAT_MESSAGE_NOT_IN_ROOM` | 400 | 메시지 종류·필수값 / `lastReadMessageSeq`·`beforeMessageSeq`가 그 방의 메시지가 아님(신규, Q442) |
| `CHAT_` | `CHAT_ROOM_NOT_PARTICIPANT` | 403 | **STOMP SUBSCRIBE·SEND 전용** 참여 권한. REST는 404 `CHAT_ROOM_NOT_FOUND` 사용(Q434) |
| `CHAT_` | `CHAT_ROOM_NOT_FOUND`, `CHAT_TARGET_NOT_FOUND`, `CHAT_SHARED_POST_NOT_FOUND` | 404 | REST에서 방 없음·비참여자(신규, Q434) / 방 생성 대상 없음 / 공유 게시물 없음 |
| `REPORT_` | `REPORT_TARGET_NOT_ALLOWED`, `REPORT_REASON_REQUIRED`, `REPORT_REASON_NOT_ALLOWED`, `REPORT_ALREADY_EXISTS`, `REPORT_TARGET_NOT_FOUND` | 400/400/400/409/404 | 허용 밖 대상(보호동물 게시글·보호동물 댓글·SHELTER 계정 포함, Q449·Q477) / `reasonCode` 누락 / 대상과 사유 조합 불일치(Q449) / 중복 / 대상 없음 |
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

**v3 → v4 변경 대조**

| v3 | v4 |
|---|---|
| `PET_NOT_FOUND` | `USER_PET_NOT_FOUND` (Q452) |
| `REPORT_REASON_REQUIRED` (비공백 `reasonDetail` 누락) | `REPORT_REASON_REQUIRED` (`reasonCode` 누락, Q449) |
| 신규 | `REPORT_REASON_NOT_ALLOWED` (400, 대상과 사유 조합 불일치, Q449) |

**v4 → v5 변경 대조**

| v4 | v5 |
|---|---|
| `ADOPTION_CERTIFICATE_TOO_LARGE`, `ADOPTION_CERTIFICATE_UNSUPPORTED_TYPE`, `ADOPTION_CERTIFICATE_NOT_FOUND` | 삭제(수료증 기능 제외, Q460) |
| `RESCUE_COMPLETION_FIELDS_REQUIRED` | 삭제(Q461, 접수 시 필수값은 `COMMON_INVALID_INPUT`) |
| `ADOPTION_REQUIRED_ANSWER_MISSING`·`ADOPTION_INVALID_ANSWER` | 고정 필드(장소·희망일·이메일) 검증에도 사용(Q458) |
| `AUTH_TOKEN_EXPIRED` | STOMP SEND 시 토큰 만료에도 사용(`/user/queue/errors`, Q454) |

**v10 → v11 변경 대조**

| v10 | v11 |
|---|---|
| `ANIMAL_ALREADY_RETURNED`, `ANIMAL_NOT_PROTECTING` | 삭제(Q479: 11.6 관리 API는 어느 상태에서든 전환 허용, 반환 행은 덮어쓰기). 11.7·11.8·12.12 삭제로 발생 경로 없음 |
| `ANIMAL_INVALID_STATUS_COMBINATION` | 11.6에서 서버 계산 결과가 조합 CHECK에 어긋나는 경합에만 사용(입력 오류는 `COMMON_INVALID_INPUT`) |
| `ADOPTION_ANIMAL_ALREADY_FINISHED` | 의미 축소: 12.11에서 **이미 다른 신청서가 연결된** 동물. 11.6으로 먼저 ADOPTED가 된 동물(FK NULL)에는 채택 허용 |
| `COMMON_INVALID_INPUT` | 11.4 공고 날짜·`adoptionStatus`, 11.6의 날짜·`status`·상태별 추가 필드 검증, 13장 `SHELTER_ANIMAL`/`SHELTER_ANIMAL_COMMENT` 지정(Q477)에도 사용 |
| `REPORT_TARGET_NOT_ALLOWED` | `SHELTER_ANIMAL_COMMENT`도 허용 밖 대상(Q477) |
| `AUTH_FORBIDDEN` | 11.2를 SHELTER가, 11.10을 USER가 호출할 때(`@PreAuthorize`, Q478) |

**v9 → v10 변경 대조**

| v9 | v10 |
|---|---|
| 변경 없음 | Q473~Q475는 필터·DTO 재구성뿐이며 새 코드·삭제 코드가 없습니다. 11.1의 날짜 형식·역전, 11.3의 허용값 밖 `status`·`period`는 기존 400 `COMMON_INVALID_INPUT` |

**v8 → v9 변경 대조**

| v8 | v9 |
|---|---|
| 변경 없음 | Q470~Q472는 DTO 필드 추가·응답 구조 변경·10.7 삭제뿐이며 새 코드·삭제 코드가 없습니다. 10.1의 허용값 밖 `period`·`source`·`status`는 기존 400 `COMMON_INVALID_INPUT` |

**v7 → v8 변경 대조**

| v7 | v8 |
|---|---|
| 변경 없음 | Q468·Q469는 DTO 필드 추가·형식 변경만이며 새 코드·삭제 코드가 없습니다. `weight` 길이 초과(30자)는 기존 400 `COMMON_INVALID_INPUT` |

**v6 → v7 변경 대조**

| v6 | v7 |
|---|---|
| 신규 | `CATALOG_GEOCODE_UNAVAILABLE`(503, 5.6 주소 검색 프록시 외부 호출 실패, Q467) |
| `USER_PET_NOT_FOUND` (7.4 오류 목록) | 7.4에서 제거(실종 신고 `petSeq` 삭제, Q466). 6장에서는 유지 |
| `COMMON_INVALID_INPUT` | 7.1·8.1 날짜 범위 형식·역전, `isNeutered`·`gender` 허용값, 8.8 `period`·`rescueStatus` 허용값에도 사용(Q463·Q464) |

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
| 응답 규약 | **Q415 A** 코드 봉투, 204 미사용(**Q450으로 대체**), 중복 409, 디바이스 토큰만 upsert 200 | 1.2·16.2·17.6 |
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
| R5 공고 종료일 수정 | **A** `noticeEndDate`는 기존 PATCH 11.5 필드로 수정 허용. 전용 API 없음 → **Q479로 대체:** 시작일·종료일 모두 11.6 관리 API에서 수정 | 11.6 |
| R6 보호 종료 단독 처리 | **A** 반환·입양 없이 ENDED 허용, 사유 선택 → **Q479:** 11.6 `status=ENDED`로 이동, 사유 선택(기본 `OTHER`) 유지 | 11.6 |
| R7 삭제된 댓글 | **A** "(삭제된 댓글)"로 자리 유지, 삭제된 루트에 새 답글 불가, 기존 답글 유지 | 13장 |
| R8 채팅방 생성 주체 | **A** USER·SHELTER 모두 먼저 생성 가능 | 14.2 |
| R9 채팅 푸시 | **C** 서버는 구독 여부를 판단하지 않고 보낸 사람 외 전원에게 FCM 데이터 메시지, 표시 여부는 앱이 현재 방 기준으로 판단. Redis 구독 상태 없음 | 14장·17.8·21.6 |
| R10 8.8 보조 필드 | **A** `hasRescue` 선택 필터·`activeRescueSeq` 응답 유지, 기본은 전량(**Q464에서 `hasRescue`를 `rescueStatus` 4값으로 대체**, 기본 전량은 유지) | 8.8 |
| R11·R12 도메인 분류 | 기준정보+행정구역 → `catalog`(`CATALOG_`), 북마크 → `user`(`USER_BOOKMARK_*`), 보호동물은 `animal`(`ANIMAL_`) 단독. core-api 13개 패키지 | 19장 |
| R13 배정 방식 필드 | **A** `assignmentMethod: DISTRICT|NEAREST` 응답 전용, DB 저장 없음 | 8.4 |
| R14 `districts` 번호 | **B** DB 문서에서 07번으로 넣고 이후 재번호 | DB v16 |
| R15 고아 S3 객체 | **A** 수명주기 1일 | 4.1 |
| R16 채팅 전송 방식 | **B** STOMP SEND `/app/rooms/{chatRoomSeq}/messages`, 서버 `@MessageMapping` 검증·저장·브로드캐스트, 오류는 `/user/queue/errors`에 HTTP와 같은 봉투. REST 메시지 전송 삭제, `clientMessageId`로 전송 확인 | 14장·21.4·22장 |
| Q430 감사 시각 매핑 | `mod_dttm` NULL 허용·INSERT 시 NULL·UPDATE 시만 채움(`@PreUpdate`). 베이스 엔티티 `BaseCreatedEntity`/`BaseTimeEntity` 분리 | DB v16 §1 (API 영향 없음) |
| Q431 시드 적재 | 보호소 `district_code`는 오프라인 계산값을 시드 SQL에 삽입, 시드 이미지는 S3에 먼저 올린 키를 SQL에 기록. 세부 절차는 적재 시점에 논의 | 9.1·DB v16 |
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
| Q446 COMMENT 알림 이동 정보 (2026-11-03) | target=게시물 + `commentSeq`·`parentCommentSeq`(DB 컬럼 2개). 13.6 댓글 단건 조회 신설, 알림 진입 시 앱이 게시물 상세와 동시 호출해 해당 댓글을 최상단 고정. FCM data 페이로드 규칙 신설 | 13.3·13.6·17.1·17.8·21.6 |
| Q447 알림 유형·title/body | 9종 세분화, `title`은 서버 enum 고정 문구(저장 없음), `message`=body 스냅샷 템플릿 [가안], `actor` 제거 | 0.2·12장·13.3·17.1·17.8·21.6 |
| Q448 삭제 댓글 알림 | `CommentDeleted` 이벤트 → notification이 `message`를 "삭제된 댓글입니다"로 갱신. title 유지, 발송된 푸시 회수 없음 | 13.5·17.1·21.6 |
| Q449 신고 대상·사유 | 대상 6종(`SHELTER_ANIMAL` 제외), `reasonDetail` 삭제·`reasonCode` 2세트, 조합 불일치 400 `REPORT_REASON_NOT_ALLOWED`. UNIQUE 유지 | 1.5·15.1·19장 |
| Q450 204 | 본문 없는 성공은 204(Q415 대체), 18.2 "없음"은 200 + `data:null` 유지 | 1.2 및 v4 요약 Q450 행의 엔드포인트 |
| Q451 3.1 USER 전용 | `GET /users/me`는 USER만, `shelterSeq` 제거. SHELTER는 2.2 응답·토큰·9.3 사용 | 3.1 |
| Q452 pet→user 통합 | 패키지 `user`, 경로 `/users/me/pets`·`/users/me/bookmarks`, `USER_PET_NOT_FOUND`. 장 번호는 유지하고 제목에 (USER) 표기 | 6·16·19·22장 |
| Q453 5장 개칭 | "기준정보 (CATALOG)": 동물 기준정보(5.1~5.4) + 행정구역(5.5) | 5장·19장 |
| Q454 경로 접두어 = 도메인 (2026-11-03, v5; v7에서 복원) | "내 것만" 목록을 `/users/me/...`·`/shelters/me/...`에서 각 도메인 경로 아래로 이동. 빈 컬렉션 경로 = 호출자가 볼 수 있는 전부, 세그먼트(`/me`·`/assigned`·`/received`·`/shelter`)는 공개 목록과 겹치거나 역할별 형태가 다를 때만. 건수는 목록 경로 아래 `/status-counts`. 반려동물·북마크·`/users/me`·`/shelters/me`·18.2·18.3 유지. **v6의 Q454(채팅)와 번호가 겹치나 별개 결정** | 1.1·7.3·8.3·8.8·10.1·10.7(Q471로 삭제)·11.3·12.2·12.9·12.16·18.19·18.20·19장 |
| Q454 채팅 목록·실시간 (2026-11-04) | 14.1 커서·`unreadOnly`·노출 기준·`profileImage`, 14.8 신설, `/user/queue/room-updates`(MESSAGE/READ/LEFT), 연결 생명주기·토큰 만료, 미읽음 조회 시 계산(카운터 미채택), DB 참여자 인덱스 2개 | 1.1·14장·21.4 |
| Q455 STOMP 목적지 표기 | `.` 구분자 전환 미반영. RabbitMQ relay에서 `/` 동작은 연동 시 테스트 | - |
| Q456 댓글 | `author.profileImage`, `commentCount`=루트+답글·삭제 제외(11.2 추가), 루트 최신순+커서·답글 오래된 순(페이징은 Q480으로 폐기), 깊이 1 유지, 업로드 순서(4.2는 전송 직전) | 1.1·4.1·11.2·13장 |
| Q457 업로드 설계 정리 | 보류(인프라 티켓 296·297과 정렬) | 20.2 |
| Q458 입양 신청서 | `weight`·`registrationNumber`, `AnimalSummary`, 문항 20개·`DATE`·`questionSeq` 1~20 고정, 고정 필드 4개, 12.6 animalSeq 기준 제출, 12.4·12.5 분리 유지, 저장은 임시저장·프로필 편집만 | 11·12장 |
| Q459 신청 내역 | `displayDttm` 정렬 고정, `shelterAddress`, 상세 화면 동작, "제출 전 취소" 화면 없음 | 12.2·12.3·12.8 |
| Q460 보호소 입양 | 12.16 건수 신설, DRAFT·CANCELED 비노출, 신청번호=`applicationSeq`, 수료증 제외(4.3 삭제) | 4장·12장·19장 |
| Q461 구조일지 | 사유 코드 4종, 장소·일시 접수 시 필수, 상세 장소, 10.1 필터·항목, 10.7 신설(Q471로 삭제), 10.2 `modDttm`·`foundReportSummary` | 10장·11.2·19장 |
| Q462 수료증 컬럼 삭제 (2026-11-04) | v6 Q460(기능 제외) 위에 `adoption_applications.certificate_storage_key` 삭제. S3 `certificates/`·Redis `upload:file:*`·`ADOPTION_CERTIFICATE_*` 전부 없음 | 1.4·4.3·12.3·12.9·19장 |
| Q463 신고 검색 파라미터 | 7.1/8.1 필터 = `status`(v10부터 기본값 없음, 생략 시 전체)·`occurredFrom/To`(날짜, `occurredDttm` 기준)·`districtCode`·`speciesSeq`·`gender`·`isNeutered`·`keyword`(`description`만)·`sort`(`regDttm`만). `breedSeq`·`colorSeq`·`shelterSeq` 삭제. 포함 매칭: `MALE/FEMALE`은 `UNKNOWN` 포함, `true/false`는 NULL 포함 | 1.1·7.1·8.1 |
| Q464 내 신고 목록·담당 목록 | 7.3/8.3 파라미터 없음·배열·`regDttm desc` 고정. 8.8은 `status`·`period(RECENT_7D/30D/90D)`·`districtCode`·`speciesSeq`·`rescueStatus(NONE/PROCESSING/DONE/ABORTED)`·`sort`, size 기본 8, `Page<T>` 유지. 앱/웹 기간 형식 이원화는 의도 | 1.1·7.3·8.3·8.8 |
| Q465 실종 `name` 삭제 | 실종 신고에 동물 이름 없음. 7.1 목록·7.4 요청에서 제거, DB 컬럼 삭제 | 7.1·7.4·DB v13 |
| Q466 실종 `petSeq` 삭제 | 반려동물 연결·스냅샷·소유자 검증 없음. 7.2·7.4·7.5·6.5 문구와 7.4의 `USER_PET_NOT_FOUND` 제거, DB 컬럼 삭제. 반려동물 정보 결합은 앱 | 6.5·7장·19장·21.1·DB v13 |
| Q467 주소 검색 프록시 | 5.6 `GET /geocode?query=` 신설(catalog, 네이버 Geocoding 호출, 후보 배열). 앱이 후보를 골라 좌표를 얻고 등록은 `address + latitude/longitude` 유지. 서버 지오코딩·상세 주소 필드 없음. 키는 서버에만. 실패 503 `CATALOG_GEOCODE_UNAVAILABLE` | 5.6·7.4·8.4·19장·21.1 |
| Q468 신고 목록 필드 보강 (2026-11-04) | 7.1 목록에 `age`·`isNeutered`·`weight`, 8.1 목록에 `gender`·`age`·`isNeutered`·`weight`·`regDttm` 추가(7.1과 동일 구성). 7.3·8.3·8.8은 목록 필드를 따라 자동 반영. 목록 카드에서 동물 속성을 바로 표시 | 7.1·7.3·8.1·8.3·8.8 |
| Q469 몸무게 문자열 통일 | `weight`는 전 도메인 **자유 텍스트 `VARCHAR(30)`**. 실종·발견 신고에 `weight` 컬럼 신설(선택). 보호동물 `weight`는 Q458의 `NUMERIC(5,2)` kg 숫자에서 문자열로 변경(CHECK 삭제, 앱 숫자 변환 폐기, 공공데이터 문자열 그대로 적재). 7.4·7.5·8.4·8.5·11.4·11.5 요청, 7.1·8.1 목록, 7.2·8.2·11.2 상세, `AnimalSummary`, 10장 `animal`·`foundReportSummary` | 6·7·8·10·11·12·22장·DB v14 |
| Q470 담당 목록 신고자 정보 (2026-11-04) | 8.8 각 항목에 `reporter:{ name, phone }` 추가. `name`은 `users.name` 실명(보호소 화면은 닉네임이 아닌 실명), v8의 `reporterPhone`은 `reporter.phone`으로 흡수. 나이·성별·중성화·몸무게는 Q468로 이미 포함 | 8.3·8.8 |
| Q471 구조 목록 개편 | 10.1 기간 필터를 `rescuedFrom/To` 날짜 범위에서 `period(RECENT_7D/30D/90D, rescuedDttm 기준)`으로 교체, size 기본 8, 응답을 `data:{ statusCounts, page }`로 바꿔 상태별 건수를 동봉(건수는 `status` 제외 필터만 적용), **10.7 삭제**. 항목 `animal.isNeutered`·`rescueMemo` 추가. 동물 정보 출처(REPORT=발견 신고/DIRECT=보호동물) 현행 유지 | 1.1·1.4·10장·19장·21.2·DB v15 |
| Q472 구조 상세 보강 | 10.2 `foundReportSummary`에 `isNeutered` 추가, `reporter`를 `{ name, phone }` 실명으로 변경(v8 `displayName` 닉네임 대체) | 10.2 |
| Q473 보호동물 목록 통일 (2026-11-04) | 11.1 필터 = `status`(보호+입양 합성 파생 5값, 11.3과 공통 매핑)·`regFrom/To`(`regDttm` 기준 날짜 범위)·`districtCode`(보호소 소재지)·`speciesSeq`·`gender`·`isNeutered`(포함 매칭)·`keyword`(`description`·`healthCheck`)·`sort=regDttm`만. **모든 파라미터 생략 시 전체**(기본값 없음). `shelterSeq`·`breedSeq`·`colorSeq`·`protectionStatus`·`adoptionStatus` 필터, `noticeEndDate` 정렬 삭제. 항목은 7.1 형식 + `protectionStatus`·`adoptionStatus`·공고기간·`shelter{shelterSeq, shelterName, districtCode}` | 1.4·11.1·DB v16 |
| Q474 보호동물 상세 재정리 | 11.2 = 목록 필드 + 상세 전용. `classification` 1.4 전체형, `shelter` 블록, `modDttm` 추가. `rescueInfo`·`simulation` 유지 | 11.2 |
| Q475 우리 보호소 목록 재정의 | 11.3 탭은 보호·입양 상태를 합친 파생 `status` 5값(`PROTECTING_AVAILABLE`/`PROTECTING_UNAVAILABLE`/`RETURNED`/`ADOPTED`/`ENDED`), 필터 `period(RECENT_7D/30D/90D, regDttm)`·`speciesSeq`·`gender`·`isNeutered`·`sort=regDttm`, size 8, 응답 `data:{ statusCounts, page }`(건수는 `status` 제외 필터). 항목은 11.1과 동일 | 1.1·1.4·11.3·DB v16 |
| Q476 보호동물 상세 보호소 블록 (2026-11-05) | 11.2 `shelter` = `{ shelterSeq, shelterName, phone, address }`(`shelterSeq`는 14.2 채팅방 생성용 유지). 목록 11.1·11.3의 `{ shelterSeq, shelterName, districtCode }`는 유지 | 11.2 |
| Q477 보호동물 댓글 제거 | 댓글은 실종·발견 2종만. 13장 `SHELTER_ANIMAL`/`SHELTER_ANIMAL_COMMENT` 삭제, 11.2 `commentCount` 삭제, 신고 대상 5종, `COMMENT`/`COMMENT_REPLY` 알림 대상 `MISSING`/`FOUND`만. DB `shelter_animal_comments` 삭제(44개 테이블) | 0.2·1.5·11.2·13장·15.1·17.1·17.8·19장·22장·DB v17 |
| Q478 보호소 전용 상세 분리 | 11.2 USER 전용(앱, `isBookmarked`·`simulation`), 11.10 `GET /shelter-animals/me/{animalSeq}` SHELTER 전용(웹, `returnInfo`·`adoptionInfo`·`applicationCounts`, `shelter` 블록 없음). 토큰 분기 대신 경로 분리. 타 보호소 404 | 1.5·11장 표·11.2·11.10·12.16 |
| Q479 상태·공고기간 관리 API | 11.6 `PATCH /shelter-animals/{animalSeq}/management`: `noticeStartDate`·`noticeEndDate`·`status`(PROTECTING/RETURNED/ADOPTED/ENDED) + 상태별 입력(`adoptionStatus` / `endReason` / `recipientName`·`recipientPhone` / `reason`·`memo`). **어느 상태에서든 전환 허용**, 복귀 시 종료 컬럼 NULL·FK·반환 행 유지, 반환 행 덮어쓰기, ADOPTED는 `completed_application_seq` 불변(채택은 12.11 전용). 11.7·11.8·12.12 삭제, 11.4에 공고기간·`adoptionStatus` 선택 입력, 11.5에서 `noticeEndDate` 제거, R5·R6 대체. DB `adoption_reason`·`adoption_memo` 추가 | 0.2·1.5·11장·12장·19장·21.3·DB v17 |
| Q480 댓글·답글 전량 조회 (2026-11-05) | 13.1 `GET /comments?targetType&targetSeq`·13.2 `GET /comments/{type}/{seq}/replies`를 **페이징 없는 배열 응답**으로 변경. Q456의 `beforeCommentSeq` 커서·`page`/`size` offset 폐기, 정렬(루트 `commentSeq desc`, 답글 `regDttm asc`)은 유지. 1.1 커서 예외에서 13.1 제거, 배열 예외에 13.1·13.2 추가. 앱의 "더 보기" 없음 | 1.1·13.1·13.2 |

### 20.2 미결 안건

**없음.** 남은 조정 항목은 [가안]으로 표시한 수치뿐입니다: 토큰 수명(1시간/14일), 업로드 컨텍스트 TTL(10분)·고아 객체 정리(24시간), 모의 입양 튜닝값(18.0 표), 채팅 구독 목적지 이름, STOMP 백그라운드 유예 시간(10~30초), 주소 검색 프록시 타임아웃·후보 수(5.6). 모두 코드 설정값이며 명세 버전을 올리지 않고 조정합니다. 시드 적재의 세부 절차(Q431)는 적재 시점에 별도 논의합니다. 새 안건은 Q481부터 번호를 붙입니다.

### 20.3 팀 논의 대기 (v6 시점, 다음 버전에서 결정)

| 영역 | 내용 | 논의 상대 |
|---|---|---|
| ~~11장 상태 변경 방식~~ | **Q479에서 종결.** 11.6 관리 API 하나로 상태·입양 가능 여부·공고기간을 바꾸며 어느 상태에서든 전환 허용. 등록(11.4)은 PROTECTING 고정 + 입양 가능 여부 선택 | — |
| ~~11장 공고기간~~ | **Q479에서 종결.** 11.4 등록 시 시작·종료일 선택 입력(기본 등록일/+10일), 11.6에서 두 날짜 모두 수정 | — |
| 11장 발생장소 | 직접 등록 동물용 `occurred_place` 컬럼 제안(구조 연결 동물은 구조 장소) | FE |
| 11장 공고번호 | "경북-구미-2026-00001" 형식 `notice_number`·채번 규칙 필요 여부 | FE |
| 11장 목록·상세 필드 | 목록 항목에 분류 이름·나이·색상·체중·특징·장소, `classification` 이름, 상태별 건수 API 제안, 피그마와 구현의 "구조정보" 칸 차이 | FE |
| 10장 화면 | 상세의 실패·취소 사유 입력란, 직접 접수 상세의 "연결된 발견 신고" 카드, 동물 정보 등록 화면 진입 시점(진행 중 구조 연결 허용 여부) | FE |
| 8.8 제보 목록 | **Q464에서 확정**(`rescueStatus`·`period`·`districtCode`·`speciesSeq`·`sort`, size 8). 남은 것은 카드 날짜 범위 표시 의미(FE 표시 규칙) | 발견 신고 담당 |
| 16장 북마크 | 보호동물 북마크 여부, 저장 목록 `postSummary` 필드 | 북마크 담당 |
| Q457 업로드 | `public/tmp/` → `public/` 이동, CloudFront URL | 인프라 |
| 5.6 주소 검색 UI | 앱의 위치 입력이 주소 검색(5.6 후보 선택)인지 지도 핀인지, 둘 다인지. 서버는 어느 쪽이든 `address + latitude/longitude`만 받음(Q467) | FE |
| 보류 화면 | 12.15·18장 모의 입양(12.12 외부 입양은 Q479로 11.6에 흡수) | 기획 |

### 아직 스펙에 **없는 것** (의도된 제외)

- 회원 차단·차단 해제·신고 시 자동 차단, 관리자 신고 심사/신고 처리 완료 API, **신고 내역 조회(15.2, Q428)**, **ADMIN 전용 API 전체**(보호소 기준정보·행정구역 배정·입양 문항·SYSTEM 알림 관리 포함).
- 보호동물 보호소 간 이관, 보호소 삭제·정리, 사후관리.
- 모의 입양의 가상 동물(실제 보호동물 없이 시작), 동물병원·진단 전용 API, 상점·시나리오 관리 API, 리포트 수정·삭제.
- AI 이미지 임베딩 저장/벡터 차원·인덱스/모델 버전, AI 매칭 점수 보관, 실종↔발견/보호동물의 확정 동일 개체 FK.
- 입양 질문의 템플릿·버전/변경 이력, 별도 입양 프로세스/상태 이력/서류 심사 이력 API.
- 댓글 좋아요/채팅 메시지 개별 삭제, 채팅 푸시 DB 이력, 발견 신고 시 reporterName 별도 입력, **보호동물 댓글**(Q477).
- 신고 사유 자유 입력(Q449), 알림 행위자(actor) 표시·알림 title 저장(Q447).

---

## 21. 핵심 서버 트랜잭션 흐름 (개발 체크리스트)

### 21.1 신고 등록 → AI 매칭 → 알림

1. 실종 신고 POST 요청의 `userSeq`는 로그인에서 결정(USER만). 유효 조합·사진 1~3장(소유 검증)·필수 속성 검증. 반려동물 연결·이름은 없음(Q465·Q466). 주소·좌표는 앱이 5.6 주소 검색(또는 지도)에서 얻어 보낸 값을 그대로 사용(Q467).
2. 좌표를 네이버 역지오코딩 어댑터(`port/out`)로 보내 법정동 코드 앞 5자리를 `district_code`로 얻음. 실패하면 NULL(Q406-2). 외부 호출은 트랜잭션 밖에서 먼저 수행.
3. 회원의 다른 비삭제 ACTIVE 신고 없음 확인 → DB UNIQUE `WHERE status='ACTIVE' AND is_deleted=false`도 적용 → 신고 및 연결 이미지 저장.
4. 커밋 후 매칭 요청 이벤트 `{sourceType:MISSING, sourceSeq, imageKeys[], speciesCode, occurredAt}`를 RabbitMQ로 발행(Q432). AI 워커가 페이로드와 S3 키만으로 비삭제·비탈퇴 유효 발견 신고/PROTECTING 보호동물과 비교. 매칭 구현 상세는 별도 POC.
5. 워커가 `AiMatchFound {missingReportSeq, targetType, targetSeq, score, modelVersion}`를 RabbitMQ로 발행하면 core-api가 자격을 재검사한 뒤 같은 쌍을 `missing_reports` 행 잠금 하에 확인하고 최초 1회만 `notifications`에 INSERT(17.8). `score`·`modelVersion`은 저장하지 않음.
6. 커밋 후 `NotificationCreated` 이벤트(발송만) → notification이 저장된 `message`와 동일한 문자열을 FCM에 전달. 실패해도 DB 보존/재시도 없음.

### 21.2 발견 신고 → 구조 접수 → 완료 → 보호동물

1. 발견 신고 POST(USER만): 역지오코딩으로 `district_code` 도출(실패 시 NULL) → `shelter_district_assignments` 매핑 → 없으면 하버사인 최근접 → 서버 자동 배정, 발견 신고 ACTIVE. 커밋 후 담당 보호소에 `SHELTER` 알림 이벤트.
2. 보호소가 구조 대상 선택 → 사유 코드·장소·구조 일시를 입력해 rescue PROCESSING 즉시 생성(Q461); 같은 발견 신고의 비삭제 PROCESSING/DONE 합계 최대 1개.
3. DONE 처리: 추가 필수값 없음, 완료시각 기록. 발견 신고 상태는 **그대로**.
4. 보호동물 POST: 구조가 DONE인 경우 `rescueSeq`로 참조 또는 직접 등록 `rescueSeq:null`. DONE 하나에 여러 보호동물 허용. 공고기간·입양 가능 여부는 요청값 또는 기본값(Q479).
5. 보호동물 상세(11.2·11.10)에서 구조 속성 5개(일시·사유 코드·장소·상세 장소·메모)는 **LEFT JOIN**으로 조회, 복제/별도 수정 금지.
6. 보호 상태·공고기간 변경은 11.6 한 트랜잭션: 네 상태 컬럼 동시 설정 → 상태별 부속 처리(반환 행 INSERT/덮어쓰기, 사유·비고 저장, 종료 컬럼 NULL) → 종료 상태면 활성 신청 자동 REJECTED → 커밋 후 `ADOPTION_REJECTED` 알림 이벤트.

### 21.3 입양 DRAFT → 제출 → 심사 → 실제 완료

1. 화면 진입 시 12.2로 DRAFT 확인. "임시저장"·"프로필 편집"을 누를 때만 저장: DRAFT가 없으면 12.4(동물 PROTECTING+AVAILABLE 확인, 같은 USER/동물 활성 신청 하나만), 있으면 12.5.
2. 현재 문항(20개)의 `questionSeq` 키로 answers JSONB + 고정 필드 4개 저장·수정, DRAFT는 빈 값 허용. "다음"은 저장하지 않음.
3. 제출(12.6, animalSeq 기준): 한 트랜잭션에서 활성 신청서 `FOR UPDATE` → DRAFT면 덮어쓰고 SUBMITTED, 없으면 SUBMITTED INSERT → 필수/조건/숨김 답변·고정 필드 검증 + 신청자 인적사항 스냅샷 → 커밋 후 `ADOPTION_SUBMITTED`.
4. 소속 보호소가 수동 승인(APPROVED) 또는 자연어 거절(REJECTED). APPROVED가 여러 개 있어도 아직 입양 완료 아님.
5. 실제 채택한 승인 신청에 완료 호출(12.11) → 신청 ADOPTED + 동물 ADOPTED/ADOPTED + completedApplicationSeq 일치 + 다른 활성 신청 자동 REJECTED **한 트랜잭션**. 동물이 11.6으로 이미 ADOPTED(FK NULL)여도 채택 허용.
6. 신청서 없는 입양 완료·반환·보호 종료는 11.6 관리 API: ADOPTED면 completedApplicationSeq 불변(NULL 유지)·사유·비고 저장, RETURNED면 반환 행 INSERT 또는 덮어쓰기, 어느 경우든 활성 신청 자동 REJECTED(Q479). PROTECTING 복귀 시 거절된 신청은 되살리지 않음.

### 21.4 채팅 전송·나가기와 재입장

1. `roomType=USER`면 두 USER 정렬/단일방 제약, `roomType=SHELTER`면 USER+보호소 제약 확인(14.2).
2. **전송(R16 B):** 클라이언트 SEND `/app/rooms/{chatRoomSeq}/messages` → `@MessageMapping`이 참여자·탈퇴 상대·본문 검증(실패 시 `/user/queue/errors`로 봉투 응답) → `chat_messages` INSERT·커밋 → `/topic/rooms/{chatRoomSeq}`로 `messageSeq`·`clientMessageId`를 실은 DTO 브로드캐스트(RabbitMQ relay) → 참여자 각각에게 `/user/queue/room-updates`(`MESSAGE`, 받는 사람 기준 미읽음 수) → `ChatMessageSent` 이벤트 → notification이 보낸 사람 외 참여자에게 FCM 데이터 메시지(R9 C, 표시는 앱 판단).
3. 나갈 때 참여자의 `lastHiddenMessageSeq`를 해당 방 최신 메시지 기준으로 갱신(기존 메시지 삭제 없음).
4. 같은 상대와 재입장해도 방은 재사용하고, **그 참가자에게만** 과거 메시지 조회 제외.
5. `lastReadMessageSeq`와 `lastHiddenMessageSeq`는 반드시 그 방의 메시지를 참조. 미읽음은 쿼리 계산(포인터 이후 + 본인 발신 제외, Q454). 읽음(14.6)·나가기(14.7) 후 본인에게 `room-updates`(`READ`/`LEFT`).
6. 앱 연결 생명주기: 포그라운드 연결 → 구독 후 조회 → 화면 단위 방 구독 → 백그라운드 유예 후 해제 → 재연결 시 재조회(14장 머리말).

### 21.5 모의 입양 세션 시작 → 돌봄 → 종료 리포트

1. 세션 시작(18.1): 요청자의 `IN_PROGRESS` 세션을 `SELECT ... FOR UPDATE`로 확인(부분 UNIQUE 병행) → `core` 스키마를 읽기 전용 JOIN해 동물 비삭제·보호 중·종 `DOG/CAT` 검증 → 품종에서 성격 스냅샷(NULL이면 랜덤) + 동물의 `shelter_seq`·대표 사진 `image_key`·품종명·색상명 스냅샷(R2) → 세션 INSERT(초기 스탯, `ends_dttm`, `balance=expected_cost`) → `action_logs(SYSTEM, SESSION_START)`. 교차 스키마라 동물 행은 잠그지 않음.
2. 모든 조회·행동·스케줄러 진입 시 **스탯 지연 계산**: `stats_calculated_dttm`부터 경과 tick × (기본 변화 × 성격 배율)을 적용해 0~100으로 클램프하고 `stats_calculated_dttm` 갱신. 계산 결과 `health=0`이면 FAILED, `ends_dttm` 경과면 COMPLETED로 6단계 종료 처리.
3. 돌봄 행동(18.6): 세션 잠금 → 지연 계산 → 기구 보유·소모품 수량 검사 → 수량 -1 → 물품 효과 반영 → `action_logs(CARE)` → `last_action_dttm` 갱신. 돈은 쓰지 않음.
4. 구매(18.9): 세션 잠금 → `simulation_transactions(PURCHASE)` INSERT → `balance -= amount`(음수 허용) → `simulation_inventory_items` UPSERT. 기구는 수량 1·재구매 409.
5. 이벤트: HOME·RANDOM은 스케줄러가 하루 1~2회 추첨, HOME·CONDITION은 지연 계산 시점·스케줄러에서 조건 판정(쿨다운 적용), WALK는 하트비트(18.15)에서 추첨. PENDING 1개 부분 UNIQUE. 응답(18.13): `occurrence FOR UPDATE` → PENDING·미만료 확인 → 효과 반영 → `cost>0`이면 `transactions(EVENT)` + 잔액 차감 → `action_logs(EVENT_ANSWER)` → ANSWERED. 만료는 EXPIRED + `action_logs(EVENT_EXPIRE)`, 효과 없음.
6. 종료(COMPLETED/ABANDONED/FAILED 공통): 스탯 최종 계산 → 진행 중 산책 ENDED·PENDING 이벤트 EXPIRED → 상태·`ended_dttm` 갱신 → `simulation_reports` INSERT(지출 집계·최종 스탯·카운트, `summary=NULL`) → `action_logs(SYSTEM, SESSION_END)` → `simulation_notifications(SESSION_END)` INSERT(같은 트랜잭션, Q440) → 커밋 → `SimulationPushRequested` 이벤트(notification이 FCM) + 커밋 후 비동기 리스너가 LLM 어댑터로 `summary` 생성·UPDATE(실패 시 1회 재시도 후 규칙 문구, Q444). 리포트는 `summary=NULL`로 먼저 저장되며 세션당 1건 UNIQUE, 영구 보존.
7. 알림 스케줄러(S8, 10분 [가안]): simulation의 `@Scheduled` 작업 하나가 진행 중 세션마다 2단계 계산 → 방치(`last_action_dttm` 8시간 경과) `NEGLECT`, 스탯 20 미만 `STAT_{스탯}_WARNING`(같은 세션·같은 유형 24시간 내 1회, Q438), 조건 이벤트 발생 `EVENT`를 `simulation_notifications`에 저장 후 커밋 → `SimulationPushRequested` 이벤트 → notification이 FCM 발송. simulation은 `device_tokens`를 읽지 않고 일반 `notifications`에도 쓰지 않음. 하트비트 10분 끊긴 산책은 자동 종료. core·notification에는 스케줄 작업 없음.

### 21.6 서비스 간 이벤트(RabbitMQ) 목록 [가안]

| 이벤트 | 발행 | 소비 | 용도 |
|---|---|---|---|
| `NotificationRequested` | core-api | notification | `notifications` INSERT + FCM (COMMENT·COMMENT_REPLY·ADOPTION_*·SHELTER). 페이로드: 수신자·유형·완성된 `message`(body)·target·`commentSeq`·`parentCommentSeq`(Q446·Q447). title은 notification이 유형으로 결정. chat은 이 이벤트를 내지 않음(채팅은 `ChatMessageSent`만, R9) |
| `CommentDeleted` | core-api(13.5) | notification | 페이로드 `{targetType, commentSeq}`(`targetType`은 게시물 코드). `COMMENT`/`COMMENT_REPLY` 중 `(target_type, comment_seq)` 일치 행의 `message`를 "삭제된 댓글입니다"로 UPDATE(Q448) |
| `NotificationCreated` | core-api | notification | **FCM 발송만**. core-api가 직접 INSERT한 `AI_MATCH` 행(R1·17.8 ⑥). 페이로드: `notificationSeq`·수신자·message |
| `SimulationPushRequested` | simulation | notification | `simulation_notifications` 저장 완료 건의 FCM 발송만(저장은 simulation, `device_tokens` 조회는 notification) |
| `UserDeleted` | core-api | notification·simulation | 디바이스 토큰 `is_active=false`, 진행 중 세션 ABANDONED + 리포트 |
| `DeviceTokenDeactivateRequested` | core-api(2.4 `fcmToken`) | notification | 로그아웃 기기 토큰 비활성화 |
| 매칭 요청(`MissingReportRegistered`·`FoundReportRegistered`·`ShelterAnimalRegistered`·`ImagesChanged`) | core-api | AI 워커 | 페이로드 `{sourceType, sourceSeq, imageKeys[], speciesCode, occurredAt}`(Q432). 워커는 DB 미접근 |
| `AiMatchFound` | AI 워커 | core-api | 페이로드 `{missingReportSeq, targetType, targetSeq, score, modelVersion}`(Q432). core-api가 자격 재검사 후 17.8 순서로 처리 |
| `ChatMessageSent` | chat | notification | 보낸 사람을 제외한 참여자 전원에게 FCM **데이터 메시지**(`roomSeq, messageSeq, senderDisplayName, preview`). 표시 여부는 앱이 판단, DB 저장 없음(R9 C) |

이벤트 이름·페이로드는 `common`의 `EventPublisher` 포트로 발행하며 MQ 어댑터를 빈으로 등록하면 `LoggingEventAdapter`를 대체합니다.

---

## 22. DB v17 테이블 ↔ API 대응표

| DB 테이블 | 스키마 / 쓰는 서비스 | 주요 API 절 | 주의 |
|---|---|---|---|
| `users`, `auth_providers`, `user_accounts` | core / core-api | 2·3 | USER 필수 개인정보/LOCAL 사전 발급(SHELTER만)/동시 소프트 삭제. `profile_image_seq` FK(Q408). refresh는 Redis(Q409). chat·notification·simulation은 읽기 전용 |
| `districts` | core / core-api(시드만) | 5.5·7·8·9 | 시군구 법정동 코드 5자리 마스터(Q406-1). 필터 값 검증·지역 선택 UI |
| (테이블 없음) 주소 검색 | core-api(`catalog`, 외부 API) | 5.6 | 네이버 Geocoding 프록시(Q467). DB 저장·캐시 없음 |
| `shelters`, `shelter_members`, `shelter_district_assignments` | core / core-api | 8·9 | 보호소당 계정 1개, `district_code` 단일 PK. 기준정보·배정은 Flyway 시드(Q412). 보호소 `district_code`는 오프라인 계산값을 시드에 삽입(Q406·Q431) |
| `animal_species`, `animal_breeds`, `animal_colors`, `animal_combinations` | core / core-api(시드만) | 5·18 | 조합 FK 한 개로 저장. `species_code DOG/CAT/ETC`(M8), `breeds.personality`(M7). simulation 읽기 전용 |
| `pets`, `pet_images` | core / core-api(`user` 패키지, Q452) | 6 | 사진 1~3장. 실종 신고와 FK 연결 없음(Q466). 경로 `/users/me/pets` |
| `images` | core / core-api | 3·4·6~11·13·14 | `reg_usr_seq` 업로더(Q407), 마지막 참조 해제 후 원본 best-effort 삭제(Q427). chat은 `chat_messages.image_seq` 연결 행만 쓰고 `images` 행은 core-api만 생성 |
| `missing_reports`, `missing_report_images` | core / core-api | 7·17 | 회원당 ACTIVE 최대 1건, `district_code`(Q406), CLOSED 읽기 전용(Q425), AI 알림 기준. `name`·`pet_seq` 없음(Q465·Q466). `weight` 자유 텍스트(Q469) |
| `found_reports`, `found_report_images` | core / core-api | 8·10·17 | `district_code` 도출 → 매핑/최근접 자동 배정, 수동 재배정 API 없음, CLOSED 읽기 전용. `weight` 자유 텍스트(Q469) |
| `rescue_records` | core / core-api | 10·11 | DONE 1:N 보호동물, 구조 정보 JOIN. SHELTER 전용 생성. 사유 코드 4종·장소·`rescued_dttm` 접수 시 필수(Q461) |
| `shelter_animals`, `shelter_animal_images` | core / core-api | 11·12·18 | 최초 PROTECTING(입양 가능 여부·공고기간은 등록 시 선택, Q479), 이름 없음. `weight`(Q458 신설, Q469로 자유 텍스트)·`registration_number`(Q458)·`adoption_reason`·`adoption_memo`(Q479). 상태·공고기간은 11.6 관리 API, 어느 상태에서든 전환. 모의 입양 시작 대상(simulation 읽기 전용) |
| `animal_returns` | core / core-api | 11 | 반환 시점=`reg_dttm`(재지정 시 `mod_dttm`), 보호동물당 1건·덮어쓰기(Q479), 소유권 검증 아님 |
| `adoption_form_questions` | core / core-api(시드만) | 12 | 현재 20문항(`question_seq` 1~20 고정, `DATE` 유형), 문항 이력/버전 없음, 관리 API 없음 |
| `adoption_applications` | core / core-api | 12 | JSONB questionSeq 키 + 고정 필드 4개(Q458), 6상태, 별도 프로세스 없음. DRAFT·CANCELED는 보호소 비노출(Q422·Q460). 수료증 컬럼 삭제(Q462). 12.15 리포트 파생 조회 |
| `chat_rooms`, `chat_participants`, `chat_messages` | core / **chat** | 14 | 마지막 읽음/나가기 포인터, 직접 imageSeq, 미읽음 조회 시 계산·참여자 인덱스(Q454). STOMP SUBSCRIBE·SEND 인가 근거, 메시지 쓰기는 SEND 핸들러만(R16 B) |
| `missing_report_comments`, `found_report_comments` | core / core-api | 13·15 | 각각 imageSeq 직접 FK, 삭제 루트의 답글 유지. `shelter_animal_comments`는 Q477로 삭제 |
| `reports` | core / core-api | 15 | 대상 5종·`reason_code` 대상별 조합(Q449·Q477), PENDING만, 신고 심사 없음, 열람 주체·조회 API 없음(Q428) |
| `bookmarks` | core / core-api(`user` 패키지) | 16 | 해제 물리 삭제, 중복 409. 경로 `/users/me/bookmarks`(Q452) |
| `notifications`, `device_tokens` | core / **notification**(AI_MATCH 행만 core-api가 INSERT) | 17·18 | 9종(Q447), title 비저장·`message`=body, COMMENT 계열 `comment_seq`·`parent_comment_seq`(Q446), 삭제 댓글 body 갱신(Q448), AI 최초 1회/FCM 재시도 없음. 모의 입양 푸시도 notification이 같은 토큰으로 발송(`SimulationPushRequested`). 디바이스 토큰 등록 upsert |
| `simulation_sessions` | **simulation** / simulation | 18.1~18.5 | USER당 IN_PROGRESS 1건 부분 UNIQUE, 종·성격·`shelter_seq`·동물 표시 정보 스냅샷, 스탯 7개 0~100, 잔액 음수 허용. `user_seq`·`animal_seq`는 논리 참조 |
| `simulation_shop_items` | simulation / simulation(시드만) | 18.8 | 9분류, 기구 `is_consumable=false`, 사진은 키 문자열(images FK 없음), 관리 API 없음 |
| `simulation_inventory_items` | simulation / simulation | 18.6·18.9·18.10·18.16 | UNIQUE(session,item), 기구 수량 1, 소모품 0 유지 |
| `simulation_transactions` | simulation / simulation | 18.9·18.11·18.13 | PURCHASE/EVENT, `balance_after`, 리포트 집계 근거 |
| `simulation_action_logs` | simulation / simulation | 18.6·18.7·18.13~18.17 | CARE/WALK/EVENT/SYSTEM, `stats_after_jsonb`, 방치 판정 |
| `simulation_walks` | simulation / simulation | 18.14~18.17 | 세션당 IN_PROGRESS 1건, 하트비트 자동 종료(스케줄러) |
| `simulation_event_scenarios`, `simulation_event_choices` | simulation / simulation(시드만) | 18.12·18.13 | HOME/WALK × RANDOM/CONDITION, 선택지 2개·`result_text` |
| `simulation_event_occurrences` | simulation / simulation | 18.12·18.13·18.15 | 세션당 PENDING 1건 부분 UNIQUE, 6시간 만료 |
| `simulation_reports` | simulation / simulation (core-api 읽기 전용) | 18.5·18.18~18.21·12.15 | 세션 1:1, 종료 시 1회 생성, 영구 보존, 보호소 열람만 |
| `simulation_notifications` | simulation / simulation | 18.22·18.23 | 10종(Q438), 일반 알림과 분리, 푸시는 이벤트로 notification에 위임 |

---

**v11 종료.** 이 문서는 DB v17과 백엔드 공통 코드 규약을 기준으로 한 API 계약이며, [가안] 수치와 [초안] DTO 세부는 구현 중 조정할 수 있습니다. v6의 Q454~Q461은 2026-11-04 Figma·미리보기 대조로 확정한 것이고(Q455 미반영, Q457 보류), v7은 그 위에 v5의 Q454(경로 접두어 = 도메인 패키지)를 복원하고 Q462~Q467(수료증 컬럼 삭제·신고 검색 파라미터·내 신고 목록·실종 `name`·`petSeq` 삭제·주소 검색 프록시)을 반영했으며, v8은 Q468~Q469(신고 목록 필드 보강·몸무게 자유 텍스트 통일)를, v9는 Q470~Q472(담당 목록 신고자 실명·구조 목록 기간 프리셋·size 8·건수 동봉·10.7 삭제·중성화·구조 메모·상세 신고자 실명)를, v10은 Q473~Q475(보호동물 목록·상세를 신고 형식과 통일, 우리 보호소 목록을 파생 상태 탭·건수 동봉형으로 재정의)를, v11은 Q476~Q479(상세 보호소 블록·보호동물 댓글 제거·보호소 전용 상세 11.10·상태/공고기간 관리 API 11.6, 11.7·11.8·12.12 삭제)를 반영했습니다. 팀 논의 대기 항목은 20.3에 있습니다. 새 안건은 Q480부터 번호를 붙여 질의응답으로 확정한 뒤 반영합니다. v10은 `animal_service_api_spec_v10.md`로 보존합니다.
