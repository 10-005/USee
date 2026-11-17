#!/usr/bin/env bash
# chat·notification 비밀값과 앱용 RabbitMQ 계정 → SealedSecret 3개 (namespace app, S15P31D106-278·340)
#   chat-secrets         : DB_PASSWORD(data/postgres-credentials CHAT_APP_PASSWORD), REDIS_PASSWORD, JWT_PUBLIC_KEY
#   notification-secrets : DB_PASSWORD(data/postgres-credentials NOTIFICATION_APP_PASSWORD), REDIS_PASSWORD, JWT_PUBLIC_KEY
#   rabbitmq-app         : RABBITMQ_USERNAME·RABBITMQ_PASSWORD(data/rabbitmq-credentials 기본 계정). core-api·chat·notification 공용
# JWT_PUBLIC_KEY 는 core-api 와 같은 키여야 한다 → ~/.s15p31d106/jwt/private.pem 에서 꺼낸다(gen-core-api-secrets.sh 가 만든 키)
# 사용법: infra 작업 폴더 최상위에서  PEM=<노드1 pem 경로> bash infra/scripts/gen-chat-notification-secrets.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
NODE1=ubuntu@k15d106.p.ssafy.io
JWT_KEY="$HOME/.s15p31d106/jwt/private.pem"
DIR=infra/platform/secrets

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
[ -n "${PEM:-}" ] && [ -f "$PEM" ] || { echo "PEM 환경 변수에 노드 1 pem 경로를 지정하세요"; exit 1; }
[ -f "$JWT_KEY" ] || { echo "$JWT_KEY 가 없습니다(core-api 와 같은 키가 필요)"; exit 1; }

# 클러스터의 기존 Secret에서 값을 꺼내 바로 변수로 (화면 출력 없음)
get() {
  ssh -i "$PEM" "$NODE1" "sudo k3s kubectl get secret -n data $1 -o jsonpath='{.data.$2}'" | base64 -d
}
CHAT_DB_PW=$(get postgres-credentials CHAT_APP_PASSWORD)
NOTI_DB_PW=$(get postgres-credentials NOTIFICATION_APP_PASSWORD)
REDIS_PW=$(get redis-credentials REDIS_PASSWORD)
MQ_USER=$(get rabbitmq-credentials RABBITMQ_DEFAULT_USER)
MQ_PW=$(get rabbitmq-credentials RABBITMQ_DEFAULT_PASS)
for v in "$CHAT_DB_PW" "$NOTI_DB_PW" "$REDIS_PW" "$MQ_USER" "$MQ_PW"; do
  [ -n "$v" ] || { echo "기존 Secret에서 값을 읽지 못함"; exit 1; }
done
JWT_PUB=$(openssl pkey -in "$JWT_KEY" -pubout -outform DER | base64 | tr -d '\n')

seal() {  # $1=Secret 이름, 나머지=KEY=VALUE
  local name=$1; shift
  {
    printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: %s\n  namespace: app\ntype: Opaque\nstringData:\n' "$name"
    for kv in "$@"; do printf "  %s: '%s'\n" "${kv%%=*}" "${kv#*=}"; done
  } | kubeseal --cert "$CERT" --format yaml > "$DIR/$name.yaml"
  echo "완료: $DIR/$name.yaml"
}
seal chat-secrets "DB_PASSWORD=$CHAT_DB_PW" "REDIS_PASSWORD=$REDIS_PW" "JWT_PUBLIC_KEY=$JWT_PUB"
seal notification-secrets "DB_PASSWORD=$NOTI_DB_PW" "REDIS_PASSWORD=$REDIS_PW" "JWT_PUBLIC_KEY=$JWT_PUB"
seal rabbitmq-app "RABBITMQ_USERNAME=$MQ_USER" "RABBITMQ_PASSWORD=$MQ_PW"
