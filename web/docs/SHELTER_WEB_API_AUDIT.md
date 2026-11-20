# 보호소 웹 API 점검

2026-11-09 배포 명세의 경로·권한·요청·응답을 보호소 웹의 라우트 및 호출 코드와 대조했다. 아래 Core 경로의 공통 접두사는 `/api/core/v1`이다.

## 실제 모드에서 연결된 화면

| 화면                    | 요청                                                                                                            | 확인한 동작                                                                                                                                                                                               |
| ----------------------- | --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 로그인                  | `POST /auth/local/login`, `/auth/refresh`, `/auth/logout`                                                       | 보호소 로그인 후 홈으로 이동한다. 동시 토큰 만료 시 갱신 요청을 공유한다. 로그아웃은 로컬 세션을 먼저 지우므로 서버 토큰 폐기에 실패해도 사용자에게 표시되지 않는다.                                      |
| 보호소 정보             | `GET`·`PATCH /shelters/me`                                                                                      | 마이페이지와 헤더에서 로그인한 보호소를 표시한다. 소개·운영시간·휴무일과 프로필·배경 사진을 저장한다.                                                                                                     |
| 채팅                    | `/api/chat/v1/chats`, `/api/chat/v1/ws`                                                                         | 방 목록·메시지 이력·읽음·나가기와 미읽음 수 조회, STOMP 실시간 전송·수신·목록 갱신을 연결했다. 채팅 상대 신고는 Core의 `POST /reports`를 사용한다.                                                        |
| 입양신청                | `GET /adoption-applications/received`, `/received/status-counts`, `GET /adoption-applications/{id}`             | 소속 보호소의 제출된 신청만 조회한다. 동물 필터는 animalSeq이고, 상세는 제출 당시 신청자 정보와 문항별 답변을 사용한다.                                                                                   |
| 심사                    | `POST /adoption-applications/{id}/review`, `/complete`, `/fail`                                                 | 서류 거절과 승인 후 실패에는 각각 rejectionReason을 보낸다. 최종 승인은 본문 없이 요청하고 같은 동물의 다른 진행 중 신청은 자동 거절될 수 있다.                                                           |
| 보호동물 조회           | `GET /shelter-animals/me`, `GET /shelter-animals/me/{id}`                                                       | 상태 탭 건수는 statusCounts, 상세의 신청 건수는 applicationCounts다. 공고번호 필드가 없어 상세에 보호동물 번호를 표시한다. 보호소 상세에 앱 전용 GET 경로를 쓰면 403이다.                                 |
| 보호동물 등록·수정·삭제 | `POST /shelter-animals`, `PATCH`·`DELETE /shelter-animals/{id}`, `PATCH /shelter-animals/{id}/management`       | 기본정보·사진과 공고·상태를 별도로 저장한다. 반환에는 수령인 이름·전화번호, 외부 입양에는 사유가 필요하다. 진행 중 입양신청이 있으면 삭제는 409다.                                                        |
| 사진·분류               | `POST /images/upload-urls` → 서명 URL에 PUT → `POST /images`. `GET /animal-species`, `GET /animal-combinations` | 사진 1~3장의 최종 imageSeq 배열과 서버 분류의 combinationSeq를 보낸다. 첫 장이 대표다. 브라우저가 File의 Content-Length를 설정하며 Content-Type, x-amz-tagging, If-None-Match를 서명 조건에 맞춘다.       |
| 홈                      | 입양신청·보호동물·구조·담당 발견 신고 목록 응답                                                                 | 입양·보호동물·진행 중인 구조·발견 신고는 실제 데이터다.                                                                                                                                                   |
| 모의 입양 리포트        | `GET /adoption-applications/{id}/simulation-reports`, `GET /api/simulation/v1/simulations/{sessionSeq}/report`  | 11월 10일 반영. 담당 신청자의 종료 리포트 목록에서 선택한 세션의 차트를 조회한다.                                                                                                                         |
| 구조일지                | `GET`·`POST /rescues`, `GET`·`PATCH`·`DELETE /rescues/{id}`, `PATCH /rescues/{id}/status`                       | 직접·신고 연계 접수, 목록·상세·수정·삭제 및 상태 처리를 연결했다. 완료 후 같은 구조 번호로 여러 보호동물을 등록할 수 있다.                                                                                |
| 발견 신고               | `GET /found-reports/assigned`, `GET /found-reports/{id}`, `PATCH /found-reports/{id}/status`, `POST /rescues`   | 담당 신고는 assigned로 조회한다. 신고 상태와 구조 접수 상태(NONE·PROCESSING·DONE·ABORTED)는 별개다. 보호소는 담당 신고를 종료할 수 있고 재개는 작성자 권한이다. 신고와 구조의 연결 키는 foundReportSeq다. |

## 명세가 있고 웹 연결이 남은 화면

| 화면      | 사용 가능한 API                                                                             | 화면과 대조한 결과                                                                  |
| --------- | ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| 알림      | `/api/notification/v1/notifications` 아래 목록, 개별·전체 읽음 및 삭제, 기기 토큰 등록·해제 | 웹 알림함 라우트와 API 호출이 없다. 채팅 미읽음 수는 Chat 서비스에서 따로 조회한다. |
| 지역·주소 | `GET /districts`, `GET /geocode`                                                            | 지역 필터의 실제 값은 districtCode다. 발견 신고에서 시·군·구 코드로 필터링한다.     |

## 백엔드와 확인할 차이

- 11월 10일 확인한 기본정보 PATCH는 isNeutered를 지원한다. 중성화 변경 시 완료·미완료·모름을 true·false·null로 보내고 변경하지 않으면 생략한다.
- 보호 종료 사유 ENDED는 NATURAL_DEATH·EUTHANIZED·OTHER만 저장한다. 기타 설명을 담을 필드가 없다. 웹은 실제 모드에서 기타 선택만 제공한다.
- 구조 목록은 접수 일시(regDttm) 기준으로 정렬한다. 발견 신고 담당 목록은 regDttm 정렬만 지원하고 탭별 건수 일괄 응답이 없다. 각 탭의 정확한 건수에는 상태별 조회나 집계 필드가 필요하다.
- 보호동물의 보호 중 탭은 서버에서 PROTECTING_AVAILABLE과 PROTECTING_UNAVAILABLE로 나뉜다. 현재 웹은 서버 정렬 순서대로 필요한 페이지까지 읽어 합친다. 데이터가 많을 때는 두 상태를 한 번에 필터링하는 서버 옵션이 효율적이다.
- 리포트 상세는 spentByCategory, dailySpent, finalStats, actionCount, walkCount, eventCount, eventResolvedCount를 제공한다. actionCount에는 산책이 포함되며 종료 직후 summary는 null일 수 있다.

공개 명세 조회와 모의 HTTP 응답에 대한 웹 테스트로 점검했다. 발급 계정으로 공유 서버에 데이터를 등록·수정하거나 실제 S3 업로드, 채팅 소켓, 푸시 수신을 시험한 결과는 포함하지 않는다.

원문은 [Core](https://k15d106.p.ssafy.io/api/core/v3/api-docs), [Simulation](https://k15d106.p.ssafy.io/api/simulation/v3/api-docs), [Chat](https://k15d106.p.ssafy.io/api/chat/v3/api-docs), [Notification](https://k15d106.p.ssafy.io/api/notification/v3/api-docs) 명세에서 확인할 수 있다.
