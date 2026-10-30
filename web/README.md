# Web

React + TypeScript 웹 프론트엔드입니다. 서버는 저장소 루트의 `backend/`에서 관리합니다.
현재는 초기 기반이며 실제 서비스 API는 연결되지 않았습니다.

## 시작하기

Node.js 24, npm을 사용합니다.

```sh
cd web
npm ci
npm run dev
npm run build
```

## 디렉터리와 의존 방향

```text
src/
  app/                 # 앱 진입 구성, provider, 라우팅, 공통 레이아웃
  pages/               # URL 단위 화면
  shared/
    api/               # HTTP 클라이언트와 공통 계약 제안 타입
    config/            # 환경 변수 읽기 및 검증
    model/             # 전역 UI 상태
    styles/            # CSS 토큰과 기본 스타일
    ui/                # 공통 UI 구성 요소
  test/                # 테스트 공통 설정
```

기능이 생기면 `src/features/<기능명>/`에 해당 기능의 API·모델·UI를 모읍니다.
의존 방향은 `app → pages → features → shared`로 유지합니다.
`shared`는 상위 계층을 import하지 않습니다. 빈 기능 폴더나 가짜 API는 만들지 않습니다.
루트 기준 import는 `@/` 별칭을 사용합니다. 정적 파일은 필요할 때 `public/`에 추가합니다.

## 코드 검사

`npm run lint`와 `npm run format:check`로 검사하고, `npm run format`으로 서식을 정리합니다.

## 공통 UI

공통 Button 및 CSS 토큰·반응형 기본 스타일을 제공합니다. 최종 Figma 디자인 반영 전의 초기 기반입니다.

## 상태 관리

서버 데이터는 TanStack Query, 공유 UI 상태는 Zustand, 화면 내부 상태는 React useState로 관리합니다.
서버 응답을 Zustand로 복제하거나 인증 토큰을 영속 저장하지 않습니다.

## 라우팅

홈과 404 라우트를 제공합니다. 메뉴 탐색과 홈 복귀 동작은 `npm test`로 검증합니다.
운영 서버는 API 및 정적 파일을 제외한 화면 경로를 index.html로 fallback해야 합니다.
