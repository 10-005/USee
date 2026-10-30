# 프로젝트 디렉터리 구조

## 기본 구조

```text
mobile/
├── android/                 # Android 네이티브 프로젝트
├── ios/                     # iOS 네이티브 프로젝트
├── src/
│   ├── app/                 # 앱 최상위 구성과 Provider 조합
│   ├── assets/
│   │   ├── fonts/           # 폰트 파일
│   │   ├── icons/           # 아이콘 이미지
│   │   └── images/          # 일반 이미지
│   ├── components/          # 여러 화면에서 재사용하는 UI
│   ├── constants/           # 앱 전역 상수
│   ├── hooks/               # 재사용 가능한 커스텀 훅
│   ├── platform/            # 플랫폼 기능을 감싸는 공통 인터페이스
│   ├── screens/             # 내비게이션 단위 화면
│   ├── services/            # API와 외부 서비스 연동
│   ├── types/               # 여러 영역에서 공유하는 타입
│   └── utils/               # 상태를 갖지 않는 공통 유틸리티
├── App.tsx                  # React Native 루트 진입점
└── index.js                 # 네이티브 앱 등록 진입점
```

`App.tsx`와 `index.js`는 React Native 도구가 찾는 루트 진입점이므로 유지합니다.
실제 앱 구성은 `src/app/App.tsx`에서 관리합니다.

## 폴더 사용 기준

### app

- 앱 전체에서 한 번만 구성하는 Provider와 전역 초기화 코드를 둡니다.
- 화면이나 도메인 로직은 직접 구현하지 않습니다.

### screens

- 내비게이션에서 하나의 경로로 등록되는 화면을 둡니다.
- 화면별 하위 폴더는 화면 컴포넌트와 해당 화면에서만 사용하는 요소를 함께 관리합니다.
- 두 개 이상의 화면에서 사용하는 UI는 `components`로 이동합니다.

```text
screens/
└── Home/
    ├── HomeScreen.tsx
    ├── HomeScreen.styles.ts
    └── components/
```

### components

- 여러 화면에서 재사용되는 표현 중심 UI를 둡니다.
- 서버 통신이나 내비게이션 같은 앱 흐름을 직접 제어하지 않습니다.
- 컴포넌트별 폴더를 만들고 공개 진입점은 `index.ts`로 제공합니다.

```text
components/
└── AnimalCard/
    ├── AnimalCard.tsx
    ├── AnimalCard.styles.ts
    ├── AnimalCard.types.ts
    └── index.ts
```

### hooks

- 여러 컴포넌트나 화면에서 재사용하는 상태 및 생명주기 로직을 둡니다.
- 특정 화면에서만 사용하는 훅은 해당 화면 폴더 안에 둡니다.

### services

- HTTP 요청, 저장소, 알림 등 외부 시스템과 통신하는 코드를 둡니다.
- 화면 컴포넌트가 통신 구현 세부 사항에 직접 의존하지 않도록 합니다.

### types

- 여러 폴더에서 공유하는 도메인 타입과 공통 타입을 둡니다.
- 한 컴포넌트나 서비스에서만 사용하는 타입은 해당 구현 파일 가까이에 둡니다.

### assets

- 이미지, 아이콘, 폰트처럼 번들에 포함되는 정적 파일을 종류별로 구분합니다.
- 파일 이름은 영문 소문자와 하이픈을 사용합니다.

### constants와 utils

- `constants`에는 여러 영역에서 공유하는 변경되지 않는 값을 둡니다.
- `utils`에는 React 상태를 사용하지 않는 순수 함수를 둡니다.

## 플랫폼 파일 분리 기준

플랫폼 공통 구현을 기본 파일에 작성하고, 실제 동작이 다를 때만 React Native의
플랫폼 확장자를 사용합니다.

```text
BiometricAuth.ts            # 플랫폼 공통 타입 또는 기본 구현
BiometricAuth.android.ts    # Android 구현
BiometricAuth.ios.ts        # iOS 구현
```

- 조건문으로 분기하는 코드가 작으면 `Platform.select` 또는 `Platform.OS`를 사용합니다.
- 네이티브 API, 권한 처리, 화면 구조가 다르면 `.android.ts(x)`와 `.ios.ts(x)`로 분리합니다.
- 호출 측에서는 확장자를 제외한 경로로 가져옵니다.
- 플랫폼별 구현이 공통 인터페이스를 따르도록 `platform`에서 경계를 정의합니다.
- Android 또는 iOS 네이티브 코드는 각각 `android`, `ios` 디렉터리에 둡니다.

## 명명 기준

| 대상           | 기준                               | 예시                     |
| -------------- | ---------------------------------- | ------------------------ |
| React 컴포넌트 | PascalCase                         | `AnimalCard.tsx`         |
| 화면           | `이름Screen.tsx`                   | `HomeScreen.tsx`         |
| 커스텀 훅      | `use` + PascalCase                 | `useAnimalList.ts`       |
| 서비스         | camelCase + 역할                   | `animalApi.ts`           |
| 타입 파일      | camelCase + `.types`               | `animal.types.ts`        |
| 상수 파일      | camelCase                          | `storageKeys.ts`         |
| 유틸리티       | camelCase                          | `formatDate.ts`          |
| 테스트         | 대상 이름 + `.test`                | `AnimalCard.test.tsx`    |
| 스타일         | 대상 이름 + `.styles`              | `AnimalCard.styles.ts`   |
| 에셋           | kebab-case                         | `empty-animal-list.png`  |
| 폴더           | 역할에 따라 PascalCase 또는 소문자 | `AnimalCard`, `services` |

## import와 공개 범위

- 같은 기능 폴더 안에서는 상대 경로를 사용합니다.
- 다른 폴더에서 사용할 항목만 `index.ts`를 통해 공개합니다.
- 순환 참조를 만들지 않도록 하위 계층에서 `screens`나 `app`을 가져오지 않습니다.
- 사용하지 않는 전체 폴더 단위 배럴 파일은 만들지 않습니다.
