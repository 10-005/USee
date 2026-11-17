#!/usr/bin/env bash
# dev_readonly 비밀번호 생성 → postgres-credentials SealedSecret에 DEV_READONLY_PASSWORD 키로 추가 (기존 키는 그대로)
# 평문은 화면·파일에 남기지 않는다. 필요하면 클러스터 Secret data/postgres-credentials의 DEV_READONLY_PASSWORD에서 꺼낸다
# 다시 실행하면 새 값으로 교체된다. push 후 교체했다면 노드 1에서 create-dev-readonly-role.sh를 다시 실행
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-dev-readonly-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/postgres-credentials.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

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

echo "완료: $OUT 의 DEV_READONLY_PASSWORD 갱신(평문은 출력·저장하지 않음)"
