# USee Mobile

React Native와 TypeScript로 개발하는 USee 모바일 애플리케이션입니다.

## 지원 플랫폼

- Android 7.0(API 24) 이상
- iOS 15.1 이상

## 시작하기

개발 도구 설치, 에뮬레이터·시뮬레이터 및 실기기 연결, 빌드 방법은
[개발 환경 가이드](docs/DEVELOPMENT.md)를 참고합니다.

코드 배치와 파일 이름은 [프로젝트 구조 가이드](docs/STRUCTURE.md)를 따릅니다.

```bash
npm ci
npm start
```

Metro 실행 후 별도 터미널에서 대상 플랫폼을 실행합니다.

```bash
npm run android
npm run ios
```

## 코드 검증

```bash
npm run lint
npm run typecheck
npm test -- --runInBand
```
