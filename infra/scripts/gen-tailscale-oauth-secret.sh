#!/usr/bin/env bash
# Tailscale Operator OAuth 자격 증명 → SealedSecret (infra/platform/secrets/tailscale-operator-oauth.yaml, namespace tailscale)
#   관리 화면 Trust credentials에서 발급: Services·Devices Core·Auth Keys 읽기/쓰기, 태그 tag:k8s-operator
# 값은 화면에 보이지 않게 입력받고 출력하지 않는다.
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-tailscale-oauth-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/tailscale-operator-oauth.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

read -rsp "client_id (입력 내용은 보이지 않음): " CLIENT_ID; echo
read -rsp "client_secret (입력 내용은 보이지 않음): " CLIENT_SECRET; echo
[ -n "$CLIENT_ID" ] && [ -n "$CLIENT_SECRET" ] || { echo "빈 값이 있습니다"; exit 1; }

cat << YAML | kubeseal --cert "$CERT" --format yaml > "$OUT"
apiVersion: v1
kind: Secret
metadata:
  name: operator-oauth
  namespace: tailscale
type: Opaque
stringData:
  client_id: '$CLIENT_ID'
  client_secret: '$CLIENT_SECRET'
YAML

echo "완료: $OUT"
