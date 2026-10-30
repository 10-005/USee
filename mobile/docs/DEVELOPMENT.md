# 모바일 개발 및 빌드 환경

## 1. 지원 범위

| 구분 | 버전 |
|---|---|
| React Native | 0.88.1 |
| Node.js | 24.18.0 권장 (`^22.13.0`, `^24.3.0`, `>=26.0.0` 지원) |
| npm | 11.16.0 |
| Java | JDK 21 |
| Android | Android 7.0(API 24) 이상 |
| Android compile SDK | API 37 |
| Android target SDK | API 36 |
| Android Build Tools | 37.0.0 |
| Android NDK | 27.1.12297006 |
| iOS | iOS 15.1 이상 |
| Xcode | 16.1 이상 |
| Ruby | 2.6.10 이상 |
| CocoaPods | 1.13 이상(1.15.0, 1.15.1 제외) |

Node.js와 Java 버전은 각각 `.nvmrc`, `.java-version`에서 관리합니다.

## 2. 공통 환경 구성

프로젝트 루트(`mobile`)에서 의존성을 설치합니다.

```bash
npm ci
```

개발 서버를 실행합니다.

```bash
npm start
```

## 3. Android 환경

### 필수 도구

1. Android Studio를 설치합니다.
2. SDK Manager에서 다음 항목을 설치합니다.
   - Android SDK Platform 37
   - Android SDK Build-Tools 37.0.0
   - Android SDK Platform-Tools
   - Android Emulator
   - NDK (Side by side) 27.1.12297006
   - CMake
3. `ANDROID_HOME`을 Android SDK 경로로 지정합니다.
4. `platform-tools`와 `emulator`를 `PATH`에 추가합니다.

Windows의 기본 SDK 경로 예시는 다음과 같습니다.

```text
C:\Users\<사용자명>\AppData\Local\Android\Sdk
```

프로젝트 전용 SDK 경로가 필요하면 `android/local.properties`에 다음 내용을 작성합니다.
이 파일은 로컬 경로를 포함하므로 Git에 커밋하지 않습니다.

```properties
sdk.dir=C:\\Users\\<사용자명>\\AppData\\Local\\Android\\Sdk
```

### 에뮬레이터 실행

Android Studio의 Device Manager에서 API 36 이상의 가상 기기를 생성하고 실행합니다.
기기 연결 상태를 확인한 뒤 앱을 실행합니다.

```bash
adb devices
npm run android
```

### Android 실기기 실행

1. 단말에서 개발자 옵션과 USB 디버깅을 활성화합니다.
2. USB 연결 후 단말의 디버깅 허용 요청을 승인합니다.
3. `adb devices` 결과가 `device` 상태인지 확인합니다.
4. `npm run android`를 실행합니다.

여러 기기가 연결된 경우 다음 형식으로 대상을 지정합니다.

```bash
npx react-native run-android --device <device-id>
```

### Android 빌드 및 패키징

Windows에서는 `android` 디렉터리에서 다음 명령을 실행합니다.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat bundleRelease
```

- 개발 APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- 릴리스 AAB: `android/app/build/outputs/bundle/release/app-release.aab`

현재 릴리스 빌드는 템플릿의 디버그 서명을 사용합니다. 배포 전에는 별도 업로드 키를
발급하고 키 정보는 저장소 외부의 보안 저장소에서 관리해야 합니다.

## 4. iOS 환경

iOS 앱 빌드와 Simulator 실행은 macOS에서만 지원합니다.

### 필수 도구

1. macOS에 Xcode 16.1 이상과 Xcode Command Line Tools를 설치합니다.
2. 프로젝트 루트에서 Ruby 의존성을 설치합니다.
3. CocoaPods 의존성을 설치합니다.

```bash
bundle install
cd ios
bundle exec pod install
cd ..
```

### iOS Simulator 실행

Xcode에서 Simulator 런타임을 설치한 뒤 다음 명령을 실행합니다.

```bash
npm run ios
```

특정 Simulator를 사용하려면 이름을 지정합니다.

```bash
npx react-native run-ios --simulator "iPhone 16"
```

### iOS 실기기 실행

1. `ios/USee.xcworkspace`를 Xcode로 엽니다.
2. Signing & Capabilities에서 개발 팀을 선택합니다.
3. 연결된 iPhone에서 개발자 모드를 활성화하고 Mac을 신뢰합니다.
4. 실행 대상을 연결된 iPhone으로 선택해 빌드합니다.

### iOS 패키징

Xcode에서 Generic iOS Device를 선택한 뒤 Product > Archive를 실행합니다.
배포 인증서와 프로비저닝 프로파일은 저장소에 커밋하지 않습니다.

## 5. 기본 검증

코드 품질 검사는 플랫폼 SDK 없이 실행할 수 있습니다.

```bash
npm run lint
npm run typecheck
npm test -- --runInBand
```

플랫폼 개발 환경이 준비된 장비에서는 다음 항목까지 확인합니다.

- Android: 에뮬레이터 또는 실기기에서 앱 실행
- Android: `assembleDebug`로 개발 APK 생성
- Android: `bundleRelease`로 AAB 생성
- iOS: Simulator 또는 실기기에서 앱 실행
- iOS: Xcode Archive 생성

## 6. 환경 점검

React Native 진단 명령으로 누락된 도구를 확인합니다.

```bash
npx react-native doctor
```

Windows에서는 iOS 관련 항목이 실패하는 것이 정상이며, iOS 항목은 macOS에서 확인합니다.
