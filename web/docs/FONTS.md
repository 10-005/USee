# 웹 글꼴

- 배포본: [Pretendard v1.3.9](https://github.com/orioncactus/pretendard/tree/v1.3.9)
- 저작권: Copyright (c) 2021 Kil Hyung-jin, Reserved Font Name Pretendard
- 라이선스: SIL Open Font License 1.1. 원문은 `public/licenses/Pretendard-OFL.txt`에 보관하며 빌드 결과에도 포함됩니다.
- [폰트 원본](https://raw.githubusercontent.com/orioncactus/pretendard/v1.3.9/packages/pretendard/dist/web/variable/woff2/PretendardVariable.woff2)
- [라이선스 원본](https://raw.githubusercontent.com/orioncactus/pretendard/v1.3.9/LICENSE)

원본 가변 WOFF2를 수정 없이 `src/shared/assets/fonts/PretendardVariable.woff2`에 저장했습니다.
파일 크기는 2,057,688바이트이며, SHA-256은 다음과 같습니다.

```text
9599f12fd42fc0bce1cd50b47a0c022e108d7aa64dd0d1bb0ed44f3282d900b4
```

`src/shared/styles/fonts.css`에서 굵기 45–920을 선언합니다. 기존 UI에서 사용하는
400·500·600·700 굵기를 하나의 파일로 제공합니다. `global.css`가 이를 불러오며
페이지와 공통 UI는 같은 `--ui-font` 토큰을 사용합니다.
사용자 PC의 설치 글꼴이나 외부 CDN에 의존하지 않습니다.
Vite가 빌드 시 폰트에 해시 파일명을 붙이고 배포 경로에 맞게 URL을 처리합니다.

전체 한글을 지원하는 단일 파일이라 첫 사용 시 약 2MB를 내려받습니다.
`font-display: swap`을 사용하므로 다운로드 중에도 시스템 글꼴로 내용을 표시합니다.
향후 전송량 최적화가 필요하면 공식 동적 서브셋 배포본 도입을 검토할 수 있습니다.

업데이트 시 공식 태그의 폰트와 라이선스를 함께 교체하고 버전·크기·해시를 갱신하세요.
라이선스와 저작권 고지를 유지하고, 빌드 결과의 `licenses/Pretendard-OFL.txt`도 함께 배포합니다.
