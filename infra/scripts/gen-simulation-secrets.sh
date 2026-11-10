#!/usr/bin/env bash
# simulation 비밀값 → SealedSecret simulation-secrets (namespace app, S15P31D106-278·340)
#   DB_PASSWORD    : data/postgres-credentials SIMULATION_APP_PASSWORD
#   JWT_PUBLIC_KEY : app/chat-secrets 의 JWT_PUBLIC_KEY(core-api 와 같은 공개 키). 개인 키 없이 만들 수 있다
# RabbitMQ 계정은 기존 공용 rabbitmq-app 을 그대로 쓴다
# 주의: 이 스크립트는 파일을 새로 만들어 OPENAI_API_KEY 가 빠진다. 다시 만든 뒤 add-openai-key.sh 를 실행한다
# 사용법: infra 작업 폴더 최상위에서  PEM=<노드1 pem 경로> bash infra/scripts/gen-simulation-secrets.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
NODE1=ubuntu@k15d106.p.ssafy.io
OUT=infra/platform/secrets/simulation-secrets.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
[ -n "${PEM:-}" ] && [ -f "$PEM" ] || { echo "PEM 환경 변수에 노드 1 pem 경로를 지정하세요"; exit 1; }

# 클러스터의 기존 Secret에서 값을 꺼내 바로 변수로 (화면 출력 없음)
get() {  # $1=namespace $2=Secret $3=key
  ssh -i "$PEM" "$NODE1" "sudo k3s kubectl get secret -n $1 $2 -o jsonpath='{.data.$3}'" | base64 -d
}
DB_PW=$(get data postgres-credentials SIMULATION_APP_PASSWORD)
JWT_PUB=$(get app chat-secrets JWT_PUBLIC_KEY)
for v in "$DB_PW" "$JWT_PUB"; do
  [ -n "$v" ] || { echo "기존 Secret에서 값을 읽지 못함"; exit 1; }
done

{
  printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: simulation-secrets\n  namespace: app\ntype: Opaque\nstringData:\n'
  printf "  DB_PASSWORD: '%s'\n" "$DB_PW"
  printf "  JWT_PUBLIC_KEY: '%s'\n" "$JWT_PUB"
} | kubeseal --cert "$CERT" --format yaml > "$OUT"
echo "완료: $OUT"
