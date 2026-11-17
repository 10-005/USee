#!/usr/bin/env bash
# 레지스트리 비밀값 생성 → SealedSecret 2개 작성
#   infra/platform/secrets/registry-htpasswd.yaml  (platform: 레지스트리 로그인 계정 ci, puller)
#   infra/platform/secrets/registry-pull.yaml      (app: 클러스터가 이미지를 받을 때 쓰는 imagePullSecret)
# 평문 비밀번호는 ~/.s15p31d106/registry.env (권한 600)에만 저장. 저장소에는 봉인된 값만 들어간다.
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-registry-secrets.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
REGISTRY=k15d106.p.ssafy.io
OUT_DIR="$HOME/.s15p31d106"
ENV_FILE="$OUT_DIR/registry.env"

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요 ($CERT 없음)"; exit 1; }
[ -f "$ENV_FILE" ] && { echo "이미 있음: $ENV_FILE  (다시 만들려면 이 파일을 지우고 실행)"; exit 1; }

gen() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32; }
CI_PW=$(gen)
PULL_PW=$(gen)

# 1) 평문은 로컬 파일에만 (본인만 읽기)
mkdir -p "$OUT_DIR" && chmod 700 "$OUT_DIR"
( umask 077
  cat > "$ENV_FILE" << ENV
REGISTRY=$REGISTRY
REGISTRY_CI_USER=ci
REGISTRY_CI_PASSWORD=$CI_PW
REGISTRY_PULL_USER=puller
REGISTRY_PULL_PASSWORD=$PULL_PW
ENV
)

# 2) htpasswd (bcrypt, 레지스트리는 bcrypt만 지원)
LINE_CI=$(htpasswd -nbB ci "$CI_PW" | head -1)
LINE_PULL=$(htpasswd -nbB puller "$PULL_PW" | head -1)

cat << YAML | kubeseal --cert "$CERT" --format yaml > infra/platform/secrets/registry-htpasswd.yaml
apiVersion: v1
kind: Secret
metadata:
  name: registry-htpasswd
  namespace: platform
type: Opaque
stringData:
  htpasswd: |
    $LINE_CI
    $LINE_PULL
YAML

# 3) imagePullSecret (docker config JSON)
AUTH=$(printf '%s' "puller:$PULL_PW" | base64)
DOCKERCFG=$(printf '{"auths":{"%s":{"username":"puller","password":"%s","auth":"%s"}}}' "$REGISTRY" "$PULL_PW" "$AUTH")

cat << YAML | kubeseal --cert "$CERT" --format yaml > infra/platform/secrets/registry-pull.yaml
apiVersion: v1
kind: Secret
metadata:
  name: registry-pull
  namespace: app
type: kubernetes.io/dockerconfigjson
stringData:
  .dockerconfigjson: '$DOCKERCFG'
YAML

echo "완료"
echo "  봉인된 파일: infra/platform/secrets/registry-htpasswd.yaml, registry-pull.yaml"
echo "  평문 비밀번호: $ENV_FILE  (Git에 올리지 말 것, 화면에 출력하지 않음)"
