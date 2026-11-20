# 강아지 모델 편집 원본

`dog-blue-collar.blend`는 파란 목줄을 편집할 수 있는 Blender 원본입니다.
앱에 포함하지 않으며 자동 백업·미리보기·중간 산출물은 관리하지 않습니다.
모델과 애니메이션의 구매 원본 및 라이선스는 별도로 관리합니다.

앱은 `android/app/src/main/assets/models/dog.glb`를 사용합니다.
모바일용 GLB의 기존 74개 애니메이션을 유지해야 합니다.
모델을 수정한 후에는 프로젝트 루트에서 다음 명령으로 목줄 연결점을 갱신합니다.

```powershell
node mobile/scripts/apply-dog-collar.cjs <수정한-모바일용-모델.glb>
```

인수를 생략하면 앱의 현재 모델에서 연결점 3개만 다시 계산합니다.
같은 모델에 반복 실행해도 연결점이 중복 추가되지 않습니다.
