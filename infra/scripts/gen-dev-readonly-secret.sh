#!/usr/bin/env bash
# dev_readonly 비밀번호 생성 → postgres-credentials SealedSecret에 DEV_READONLY_PASSWORD 키로 추가 (기존 키는 그대로)
# 평문은 화면에 출력하지 않고 저장소 밖 ~/.s15p31d106/dev-readonly-password.txt 에만 저장 (본인만 읽기)
# 다시 실행하면 새 값으로 교체된다. push 후 교체했다면 노드 1에서 create-dev-readonly-role.sh를 다시 실행
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-dev-readonly-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/postgres-credentials.yaml
PW_DIR="$HOME/.s15p31d106"
PW_FILE="$PW_DIR/dev-readonly-password.txt"

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

PW=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)
[ ${#PW} -eq 32 ] || { echo "비밀번호 생성 실패"; exit 1; }

umask 077
mkdir -p "$PW_DIR"
chmod 700 "$PW_DIR" 2>/dev/null || true

# 봉인이 성공한 뒤에만 평문 파일을 바꾼다 (봉인 실패 시 이전 값과 SealedSecret이 어긋나지 않게)
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

printf '%s\n' "$PW" > "$PW_FILE"
chmod 600 "$PW_FILE" 2>/dev/null || true
echo "완료: $OUT 의 DEV_READONLY_PASSWORD 갱신. 평문은 $PW_FILE 에 저장(화면 출력 안 함)"
