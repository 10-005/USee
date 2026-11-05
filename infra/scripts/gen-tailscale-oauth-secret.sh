#!/usr/bin/env bash
# Tailscale Operator OAuth 자격 증명 → SealedSecret (infra/platform/secrets/tailscale-operator-oauth.yaml, namespace tailscale)
#   관리 화면 Trust credentials에서 발급: Services·Devices Core·Auth Keys 읽기/쓰기, 태그 tag:k8s-operator
# 값은 화면에 보이지 않게 입력받고 출력하지 않는다. kubectl 없이 Secret YAML을 직접 만들어 kubeseal로 봉인
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-tailscale-oauth-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/tailscale-operator-oauth.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

# 붙여넣기 때 터미널이 끼워 넣는 ESC[200~ / ESC[201~ 와 CR·공백 등 제어문자 제거
clean() {
  local v="$1"
  v="${v//$'\e[200~'/}"; v="${v//$'\e[201~'/}"
  printf '%s' "$v" | tr -d '[:cntrl:][:space:]'
}

read -rsp "client_id (입력 내용은 보이지 않음): " CLIENT_ID; echo
read -rsp "client_secret (입력 내용은 보이지 않음): " CLIENT_SECRET; echo
CLIENT_ID=$(clean "$CLIENT_ID")
CLIENT_SECRET=$(clean "$CLIENT_SECRET")

[[ -n "$CLIENT_ID" && "$CLIENT_ID" != *"'"* ]] || { echo "client_id가 비었거나 작은따옴표가 들어 있습니다"; exit 1; }
[[ "$CLIENT_SECRET" == tskey-* && "$CLIENT_SECRET" != *"'"* ]] || { echo "client_secret 형식이 아닙니다(tskey-로 시작, 작은따옴표 없음)"; exit 1; }

# 봉인에 성공했을 때만 결과 파일로 옮김 (실패 시 빈 파일이 남지 않게)
TMP=$(mktemp "${OUT}.XXXXXX")
trap 'rm -f "$TMP"' EXIT
cat << YAML | kubeseal --cert "$CERT" --format yaml > "$TMP"
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
mv "$TMP" "$OUT"

echo "완료: $OUT"
