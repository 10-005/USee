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

## 재생 규칙

- 정지 3초: 여러 대기 자세 또는 귀 긁기 중 하나를 한 번 재생
- 정지 10초: 웅크리기 또는 땅파기를 시작 → 반복 → 종료 순서로 한 번 재생
- 이동 재개: 정지 동작을 중단하고 이동 애니메이션으로 복귀
- 몸의 방향에 대한 실제 이동 방향과 속도에 따라 전진·후진·옆걸음·대각선·속보·달리기 선택
- 배치 후 `동작` 메뉴에서 짖기·앉기·눕기·점프·먹기·물 마시기·배변 등을 선택
- 클립 사이에는 0.15초 블렌딩 적용. 정지 동작을 이동 중 자막으로 표시하지 않음
- 인식된 수직 벽은 진행 방향을 적게 바꾸는 경로부터 확인하고, 벽을 따라 좌우로 우회
- 우회 중 몸은 진행 방향을 유지하고 옆걸음 사용. 실제 다음 걸음의 바닥과 벽 충돌은 계속 검사
- 좌우 진로가 모두 막히거나 바닥을 확인할 수 없으면 정지
