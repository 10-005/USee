# 강아지 모델

- 원본: [Cartoon Dog - Shiba Inu / billl90](https://www.cgtrader.com/3d-models/animal/mammal/cartoon-dog-shiba-inu)
- 파일: `dog.glb` — 모바일용 메시 4,584 삼각형, 본 57개, 텍스처 1024×1024 내장
- 방향: +Y 위, +Z 정면
- 애니메이션: 이동·회전·대기·생활·점프 등 74개 클립 (`dog.animations.json`에 원본 이름·길이 기록)
- 제자리 애니메이션을 사용하며 월드 이동은 `DogLeader`에서 처리
- 구매 원본과 라이선스는 별도 관리

## 재변환

Blender 4.5 LTS에서 프로젝트 루트 기준으로 실행합니다.

```powershell
blender --background --factory-startup --python-exit-code 1 --python mobile/scripts/convert-dog.py -- "<구매 파일 폴더>" "mobile/android/app/src/main/assets/models/dog.glb"
```

모바일 FBX의 메시·스킨을 원본 BLEND의 애니메이션 뼈대에 연결하고,
텍스처 및 베이크한 애니메이션을 하나의 GLB에 포함합니다.
