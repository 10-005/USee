# Android FCM

- `android/app/google-services.json`: Android Firebase 설정. 패키지명 `com.usee.animal`.
- 루트 Gradle에 Google Services 플러그인 의존성, 앱 Gradle에 플러그인 적용.
- React Native Firebase app/messaging SDK는 자동 링크된다.
- `index.js`에서 백그라운드 핸들러 등록. App 마운트 시 포그라운드·알림 클릭·토큰 갱신 리스너 등록, 언마운트 시 해제.
- Android 13 이상에서 알림 권한 요청. 거부해도 데이터 수신을 위한 토큰 초기화는 수행한다.
- 토큰은 AsyncStorage에 보관하며 `getStoredFcmToken()`으로 조회한다. 토큰을 로그에 출력하지 않는다.
- 서버 토큰 등록 API는 아직 없어 자동 전송하지 않는다. 로그인 사용자별 등록·로그아웃 해제는 API 확정 후 연결해야 한다.
- iOS는 GoogleService-Info.plist 및 APNs 설정이 없어 이번 수신 초기화에서 제외한다.

## 모의입양 메시지 규격

아래는 앱 측 임시 규격이다. 서버 규격 확정 시 어댑터를 수정해야 한다.
`data` 값은 모두 문자열이며 `sessionStartedAt`은 해당 로컬 입양 회차의 시작 시각(Unix 밀리초), `petId`는 로컬 동물 ID와 일치해야 한다.
서버 회차 ID를 로컬 회차로 임의 대체하지 않는다.

```json
{
  "message": {
    "token": "DEVICE_FCM_TOKEN",
    "android": { "priority": "high" },
    "notification": {
      "title": "모의입양 돌발 이벤트",
      "body": "반려동물의 상태를 확인해주세요."
    },
    "data": {
      "type": "mock_adoption_event",
      "eventId": "unique-event-id",
      "petId": "dog-5",
      "sessionStartedAt": "1700000000000",
      "kind": "illness",
      "title": "{petName}가 아픕니다!",
      "body": "{petName}를 동물병원에 데려가주세요!"
    }
  }
}
```

모의입양 이벤트는 기존 저장소에 저장하고 다음 활동 화면 진입 때 팝업으로 표시한다.
알림 수신·클릭이 중복되어도 같은 이벤트는 한 번만 표시한다.
`eventId`가 없으면 FCM messageId, data의 제목·본문이 없으면 notification 값을 사용한다.
식별자·제목·본문·유효한 회차 시각이 없거나 잘못된 이벤트는 무시한다.
그 외 type의 메시지는 현재 모의입양 저장소에 추가하지 않는다.

백그라운드 notification 메시지는 OS가 알림으로 표시한다. 포그라운드 시스템 알림 배너와
data-only 로컬 알림 표시는 구현하지 않았다. data-only 백그라운드 전송은 Android high priority가 필요하다.

## 기기 확인

1. Android 앱을 재빌드·설치하고 한 번 실행하여 알림 권한을 허용한다.
2. 디버거에서 `getStoredFcmToken()`을 호출해 테스트 기기 토큰을 확인한다.
3. 현재 입양 회차 값으로 위 메시지를 HTTP v1 API에서 전송한다.
4. 포그라운드 수신 후 활동 화면 재진입, 백그라운드 수신 후 알림 클릭을 각각 확인한다.
5. 같은 eventId 재전송 시 중복 팝업이 없는지, 질병 이벤트의 병원 빨간 점이 치료 완료까지 유지되는지 확인한다.

실제 발송 검증에는 Firebase 발송 권한과 Google Play services가 있는 기기 또는 에뮬레이터가 필요하다.
앱을 설정에서 강제 종료한 경우 다시 실행해야 수신할 수 있다.

공식 문서: https://rnfirebase.io/messaging/usage
