#!/usr/bin/env bash
# Grafana 관리자 계정(admin) 비밀번호 생성 → SealedSecret monitoring/grafana-admin (S15P31D106-423)
# 평문은 화면·파일에 남기지 않는다. 로그인할 때는 노드 1에서 꺼낸다:
#   sudo k3s kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d; echo
# 다시 실행하면 새 값으로 바뀐다(Grafana는 PVC가 없어 재시작 시 새 값이 적용된다)
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-grafana-admin-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/grafana-admin.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

PW=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)
[ ${#PW} -eq 32 ] || { echo "비밀번호 생성 실패"; exit 1; }

cat << YAML | kubeseal --cert "$CERT" --format yaml > "$OUT"
apiVersion: v1
kind: Secret
metadata:
  name: grafana-admin
  namespace: monitoring
type: Opaque
stringData:
  admin-user: admin
  admin-password: '$PW'
YAML

echo "완료: $OUT 생성(평문은 출력·저장하지 않음)"
