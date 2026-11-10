# Web

React + TypeScript 웹 프론트엔드입니다. 서버는 저장소 루트의 `backend/`에서 관리합니다.
보호소 로그인, 입양 신청 목록·상세·심사, 보호동물 목록·상세·등록·수정·삭제는 Core API를 사용합니다. 보호동물 사진은 발급받은 업로드 주소로 전송한 뒤 이미지 번호를 확정합니다. 모의 입양 리포트는 데모 모드에서 확인할 수 있습니다.

## 시작하기

Node.js 24와 npm을 사용합니다. 버전 선택에는 `.nvmrc`, 의존성 재현에는 `package-lock.json`을 사용합니다.

```sh
cd web
npm ci
# 필요한 경우 .env.example을 .env.local로 복사하고 API 주소 설정
npm run dev
```

기본 API 주소는 `/api`이며 Vite 개발 서버가 `https://k15d106.p.ssafy.io`로 프록시합니다. 배포 환경에서는 `/api`를 백엔드로 라우팅하거나 `VITE_API_BASE_URL`에 백엔드의 `/api` 주소를 설정하고 CORS를 허용하세요.
로컬 예시 계정과 데이터로 확인하려면 `.env.local`에 `VITE_DEMO_MODE=true`를 설정하고 개발 서버를 재시작하세요.
인증 방식은 미확정이므로 토큰 저장, 자동 갱신, 교차 출처 쿠키 전송을 추가하지 않았습니다.

## 명령

| 명령                   | 용도                            |
| ---------------------- | ------------------------------- |
| `npm run dev`          | 개발 서버                       |
| `npm run build`        | TypeScript 검사 및 `dist/` 빌드 |
| `npm run preview`      | 빌드 결과 로컬 확인             |
| `npm run typecheck`    | 앱·설정 파일 타입 검사          |
| `npm run lint`         | ESLint 검사, 경고도 실패 처리   |
| `npm run format`       | Prettier 자동 정리              |
| `npm run format:check` | 서식 검사                       |
| `npm test`             | 라우팅·API·환경 변수 테스트     |
| `npm run check`        | 서식 → 린트 → 테스트 → 빌드     |

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

## 상태 및 화면 규칙

- 서버 데이터: TanStack Query. query key에 리소스와 조회 조건을 포함하고 `queryFn`의 signal을 HTTP 클라이언트에 전달합니다.
- 화면 내부 상태: React `useState`. 여러 화면에서 공유하는 UI 상태만 Zustand에 둡니다.
- 서버 응답을 Zustand로 복제하지 않습니다. 비밀값이나 인증 토큰을 영속 저장하지 않습니다.
- Router는 `/`와 `*`(404)를 제공합니다. 운영 서버에서 API·정적 파일을 제외한 화면 경로를 `index.html`로 fallback해야 새로고침이 동작합니다.
- CSS 토큰과 Button은 초기 기반이며 최종 Figma 디자인 시스템을 반영한 결과가 아닙니다.

## API 사용

```ts
import { requestJson } from '@/shared/api/client';
import type { ApiSuccess } from '@/shared/api/contracts';

// 실제 엔드포인트가 확정된 뒤 사용합니다. 아래는 형태를 보여 주는 예시입니다.
const result = await requestJson<ApiSuccess<{ id: string }>>('/items/1', { signal });
// 204/빈 응답은 undefined이므로 명시적으로 처리합니다.
```

JSON 응답은 원문 그대로 반환하며 `data`를 자동으로 추출하지 않습니다.
HTTP 실패는 `ApiError`의 `status`와 `body`로 확인합니다. 네트워크·취소·타임아웃 오류는 원래 오류를 유지합니다.
성공 응답이 JSON이 아니면 명확한 오류를 발생시킵니다. 제네릭 타입은 런타임 검증이 아니므로
외부 데이터 검증이 필요한 기능에서는 스키마 검증을 추가하세요.
공통 계약은 [API 제안안](docs/API_CONTRACT.md)을 참고하세요.

## 환경 변수

| 변수                | 기본값 | 용도                            |
| ------------------- | ------ | ------------------------------- |
| `VITE_API_BASE_URL` | `/api` | API 기본 경로 또는 HTTP(S) 주소 |

`.env.example`만 버전 관리합니다. 개인 설정은 `.env.local`에 두고 운영 값은 CI 환경 변수로 주입합니다.
Vite의 `VITE_` 변수는 브라우저 번들에 공개되고 빌드 시 결정됩니다. 비밀키·서버 토큰·비밀번호를 넣지 마세요.
변경 후 개발 서버를 재시작하고, 운영 환경 값 변경은 다시 빌드합니다.

## 코드 컨벤션

TypeScript strict 및 `noUncheckedIndexedAccess`를 사용합니다. `any` 대신 `unknown`과 타입 좁히기를 사용합니다.
컴포넌트/타입은 PascalCase, 변수/함수는 camelCase, hook은 `use` 접두사를 사용합니다.
컴포넌트 파일은 PascalCase, 일반 모듈·폴더는 kebab-case를 사용합니다.
타입은 `import type`, 컴포넌트는 named export를 사용합니다.
Prettier 규칙은 2칸 들여쓰기, 작은따옴표, 세미콜론, LF입니다.
웹·모바일 공통 적용은 모바일 담당자 검토가 필요하며 이 변경은 `web/`에만 적용됩니다.
Git 협업은 루트의 `docs/CONVENTION.md`를 따릅니다.

## CI 연결

`web/.gitlab-ci.yml`은 서식·린트·테스트·빌드 job을 제공합니다.
현재 루트 CI 설정이 없으므로 **자동 실행은 아직 활성화되지 않았습니다**.
저장소 담당자가 루트 `.gitlab-ci.yml`에 아래 내용을 추가하면 연결됩니다.
기존 stages를 쓰는 경우 `test` stage가 있어야 합니다.

```yaml
include:
  - local: '/web/.gitlab-ci.yml'
```

## 이슈 대응 및 남은 확인

| 이슈           | 구현 내용                                           |
| -------------- | --------------------------------------------------- |
| S15P31D106-308 | React·TypeScript·Vite, npm lockfile                 |
| S15P31D106-309 | app/pages/shared 구조 및 의존 규칙                  |
| S15P31D106-310 | ESLint·Prettier 및 검사 명령                        |
| S15P31D106-311 | 홈·404 라우팅, 공통 레이아웃                        |
| S15P31D106-312 | JSON HTTP 클라이언트, timeout·취소·오류 처리        |
| S15P31D106-313 | TanStack Query provider, Zustand UI store           |
| S15P31D106-314 | CSS 토큰·기본 스타일·Button                         |
| S15P31D106-324 | 웹 TypeScript·코드 컨벤션, 모바일 합의 필요         |
| S15P31D106-325 | 플랫폼 독립 응답 타입 제안, 백엔드·모바일 합의 필요 |
| S15P31D106-326 | 환경 변수 예제·검증·운영 규칙                       |
| S15P31D106-327 | 통합 check 명령·GitLab CI job, 루트 연결 필요       |

이슈 제목과 사용자 승인 스택을 기준으로 구현했습니다. Jira 본문의 완료 조건은 아직 확인하지 못했습니다.
이슈별 브랜치와 커밋으로 분리하며, 최신 develop-web 기준으로 검사한 뒤 순서대로 푸시와 MR 생성을 진행합니다. 병합은 리뷰 후 담당자가 수행합니다.

## 참고

- [Vite 시작하기](https://vite.dev/guide/)
- [React Router 모드](https://reactrouter.com/start/modes)
- [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview)
- [Zustand 시작하기](https://zustand.docs.pmnd.rs/learn/getting-started/introduction)

## 이슈별 로컬 브랜치

최초 로컬 브랜치는 선행 커밋을 포함하도록 구성했습니다. MR 생성 시에는 이미 병합된 선행 커밋을 제외하고 해당 이슈의 변경만 최신 develop-web 위에 재적용합니다.

| 순서 | 이슈           | 브랜치                                            |
| ---- | -------------- | ------------------------------------------------- |
| 1    | S15P31D106-308 | `build/S15P31D106-308-web-foundation`             |
| 2    | S15P31D106-309 | `refactor/S15P31D106-309-web-directory-structure` |
| 3    | S15P31D106-310 | `build/S15P31D106-310-eslint-prettier`            |
| 4    | S15P31D106-314 | `design/S15P31D106-314-shared-ui-styles`          |
| 5    | S15P31D106-313 | `build/S15P31D106-313-state-management`           |
| 6    | S15P31D106-311 | `build/S15P31D106-311-routing`                    |
| 7    | S15P31D106-326 | `build/S15P31D106-326-environment-config`         |
| 8    | S15P31D106-312 | `build/S15P31D106-312-api-client`                 |
| 9    | S15P31D106-324 | `chore/S15P31D106-324-typescript-conventions`     |
| 10   | S15P31D106-325 | `docs/S15P31D106-325-api-contract`                |
| 11   | S15P31D106-327 | `ci/S15P31D106-327-web-checks`                    |

병합 순서는 표의 순서입니다. 각 MR의 최종 대상은 develop-web입니다. 선행 이슈를 Squash 병합한 뒤 후속 브랜치에서 이미 반영된 선행 커밋을 제외하고 해당 이슈 커밋을 새 develop-web 위에 재적용해야 합니다.
선행 커밋을 포함한 브랜치로 동시에 MR을 생성하면 이전 이슈의 diff도 포함되므로, 순서대로 재정리 후 MR을 생성합니다.
마지막 브랜치는 전체 구현을 포함합니다. API 규격 합의와 루트 CI 연결은 여전히 별도 확인이 필요합니다.
