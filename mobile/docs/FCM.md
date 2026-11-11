# Android FCM

- `android/app/google-services.json`: Android Firebase 설정. 패키지명 `com.usee.animal`.
- 루트 Gradle에 Google Services 플러그인 의존성, 앱 Gradle에 플러그인 적용.
- React Native Firebase app/messaging SDK는 자동 링크된다.
- `index.js`에서 백그라운드 핸들러 등록. App 마운트 시 포그라운드·알림 클릭·토큰 갱신 리스너 등록, 언마운트 시 해제.
- Android 13 이상에서 알림 권한 요청. 거부해도 데이터 수신을 위한 토큰 초기화는 수행한다.
- 토큰은 AsyncStorage에 보관하며 `getStoredFcmToken()`으로 조회한다. 토큰을 로그에 출력하지 않는다.
- 로그인 세션이 있으면 `POST /api/notification/v1/notifications/device-tokens`로 기기 토큰을 등록한다. 세션 복원, 로그인, 회원가입, 토큰 저장·갱신 때 호출한다.
- 기기 알림이 꺼져 있으면 `DELETE /api/notification/v1/notifications/device-tokens/current`로 토큰을 해제한다. 로그아웃은 인증 로그아웃에 `fcmToken`을 실어 같은 처리를 하므로 이 API를 따로 호출하지 않는다.
- iOS는 GoogleService-Info.plist 및 APNs 설정이 없어 이번 수신 초기화에서 제외한다.

## 수신 메시지

`data` 값은 문자열이다. 제목·본문은 `notification`을 사용하고, 없으면 `data`의 같은 키를 사용한다.
`src/services/notifications/pushMessage.ts`가 메시지를 해석하고, `subscribePushMessages`로 전달한다.
구독자가 없으면 다음 구독 때 전달한다. 알 수 없는 타입이나 제목·본문이 없는 메시지는 무시한다.
화면 이동과 목록 저장, 시뮬레이션 API 호출은 이 단계에서 하지 않는다.

서버 알림:

```json
{
  "notification": {
    "title": "회원님의 게시글에 댓글이 달렸어요",
    "body": "dev105님이 댓글을 남겼어요: 인동시장 근처에서 비슷한 강아지를 봤어요"
  },
  "data": {
    "notificationSeq": "1203",
    "notificationType": "COMMENT",
    "targetType": "MISSING",
    "targetSeq": "101",
    "commentSeq": "55"
  }
}
```

`notificationType`은 `COMMENT`, `COMMENT_REPLY`, `AI_MATCH`, `ADOPTION_SUBMITTED`,
`ADOPTION_APPROVED`, `ADOPTION_REJECTED`, `ADOPTION_COMPLETED`, `SHELTER`, `SYSTEM`, `CHAT`이다.
`commentSeq`는 댓글 계열에서만 있을 수 있다.

모의입양 알림은 돌발 이벤트, 7일 종료, 건강 중단이 같은 데이터로 온다.
회차나 이벤트 종류는 포함되지 않으므로 앱은 시뮬레이션 갱신 신호로만 다룬다.

```json
{
  "notification": {
    "title": "초코에게 무슨 일이 생겼어요",
    "body": "물그릇을 엎었어요. 들어와서 살펴봐 주세요."
  },
  "data": { "target": "SIMULATION" }
}
```

목적지는 `resolveNotificationDestination`이 정한다.
`MISSING` 댓글은 실종 상세, 그 외 댓글과 `SHELTER`는 발견 상세, 입양 상태는 신청 이력이다.
`CHAT`은 알림 목록에 넣지 않고 채팅 탭으로 보낸다. `AI_MATCH`와 `SYSTEM`은 목적지를 정하지 않는다.

해석된 메시지는 `usee_push` 채널에 높은 중요도로 배너를 띄운다. 앱이 켜져 있을 때와
data만 있는 백그라운드 메시지는 이 배너로 표시한다. `notification`이 있는 백그라운드
메시지는 운영체제가 같은 채널로 표시한다. data-only 백그라운드 전송은 Android high priority가 필요하다.

## 기기 확인

1. Android 앱을 재빌드·설치하고 한 번 실행하여 알림 권한을 허용한다.
2. 디버거에서 `getStoredFcmToken()`을 호출해 테스트 기기 토큰을 확인한다.
3. 위 메시지 중 하나를 HTTP v1 API에서 전송한다.
4. 포그라운드 수신과 백그라운드 알림 클릭이 같은 해석 경로를 타는지 확인한다.

실제 발송 검증에는 Firebase 발송 권한과 Google Play services가 있는 기기 또는 에뮬레이터가 필요하다.
앱을 설정에서 강제 종료한 경우 다시 실행해야 수신할 수 있다.

공식 문서: https://rnfirebase.io/messaging/usage
