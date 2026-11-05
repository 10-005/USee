#!/usr/bin/env bash
# dev_readonly 비밀번호 생성 → postgres-credentials SealedSecret에 DEV_READONLY_PASSWORD 키로 추가 (기존 키는 그대로)
# 비밀번호는 화면에 출력하지 않고 클립보드로만 복사한다. 비밀번호 관리자에 저장할 것
# 이미 키가 있으면 새 값으로 바뀐다(교체). 교체 후에는 노드 1에서 create-dev-readonly-role.sh를 다시 실행
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-dev-readonly-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/postgres-credentials.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

if command -v clip.exe >/dev/null; then COPY=clip.exe
elif command -v pbcopy >/dev/null; then COPY=pbcopy
else echo "클립보드 도구(clip.exe, pbcopy)가 없습니다"; exit 1; fi

PW=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)
[ ${#PW} -eq 32 ] || { echo "비밀번호 생성 실패"; exit 1; }

cat << YAML | kubeseal --cert "$CERT" --format yaml --merge-into "$OUT"
apiVersion: v1
kind: Secret
metadata:
  name: postgres-credentials
  namespace: data
type: Opaque
stringData:
  DEV_READONLY_PASSWORD: '$PW'
YAML

printf '%s' "$PW" | "$COPY"
echo "완료: $OUT 에 DEV_READONLY_PASSWORD 추가. 비밀번호를 클립보드에 복사했습니다(비밀번호 관리자에 붙여 넣기)"
