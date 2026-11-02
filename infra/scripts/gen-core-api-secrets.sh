#!/usr/bin/env bash
# core-api 비밀값 → SealedSecret (infra/platform/secrets/core-api-secrets.yaml, namespace app)
#   DB_PASSWORD     : data/postgres-credentials 의 CORE_APP_PASSWORD 를 그대로 옮김
#   REDIS_PASSWORD  : data/redis-credentials 의 REDIS_PASSWORD 를 그대로 옮김
#   JWT_PUBLIC_KEY  : Base64(X.509 DER) 한 줄 (JwtVerifier 형식)
#   JWT_PRIVATE_KEY : Base64(PKCS#8 DER) 한 줄 (토큰 발급 기능용, 아직 미사용)
# JWT 키 쌍은 ~/.s15p31d106/jwt/ 에 보관 (없으면 생성, 있으면 재사용 → 다른 서비스도 같은 공개 키 사용)
# 사용법: infra 작업 폴더 최상위에서  PEM=<노드1 pem 경로> bash infra/scripts/gen-core-api-secrets.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
NODE1=ubuntu@k15d106.p.ssafy.io
JWT_DIR="$HOME/.s15p31d106/jwt"
OUT=infra/platform/secrets/core-api-secrets.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
[ -n "${PEM:-}" ] && [ -f "$PEM" ] || { echo "PEM 환경 변수에 노드 1 pem 경로를 지정하세요"; exit 1; }

# 클러스터의 기존 Secret에서 값을 꺼내 바로 변수로 (화면 출력 없음)
get() {
  ssh -i "$PEM" "$NODE1" "sudo k3s kubectl get secret -n data $1 -o jsonpath='{.data.$2}'" | base64 -d
}
DB_PW=$(get postgres-credentials CORE_APP_PASSWORD)
REDIS_PW=$(get redis-credentials REDIS_PASSWORD)
[ -n "$DB_PW" ] && [ -n "$REDIS_PW" ] || { echo "기존 Secret에서 값을 읽지 못함"; exit 1; }

# JWT 키 쌍 (RSA 2048)
mkdir -p "$JWT_DIR" && chmod 700 "$JWT_DIR"
if [ ! -f "$JWT_DIR/private.pem" ]; then
  ( umask 077; openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$JWT_DIR/private.pem" 2>/dev/null )
  echo "JWT 키 쌍 생성: $JWT_DIR/private.pem"
else
  echo "기존 JWT 키 재사용: $JWT_DIR/private.pem"
fi
JWT_PUB=$(openssl pkey -in "$JWT_DIR/private.pem" -pubout -outform DER | base64 | tr -d '\n')
JWT_PRIV=$(openssl pkcs8 -topk8 -nocrypt -in "$JWT_DIR/private.pem" -outform DER | base64 | tr -d '\n')
( umask 077; printf '%s\n' "$JWT_PUB" > "$JWT_DIR/public.b64" )

cat << YAML | kubeseal --cert "$CERT" --format yaml > "$OUT"
apiVersion: v1
kind: Secret
metadata:
  name: core-api-secrets
  namespace: app
type: Opaque
stringData:
  DB_PASSWORD: '$DB_PW'
  REDIS_PASSWORD: '$REDIS_PW'
  JWT_PUBLIC_KEY: '$JWT_PUB'
  JWT_PRIVATE_KEY: '$JWT_PRIV'
YAML

echo "완료: $OUT"
echo "  공개 키(Base64 한 줄)는 $JWT_DIR/public.b64 에도 저장 (다른 서비스·로컬 .env 용)"
