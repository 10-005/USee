#!/usr/bin/env bash
# core-api-secrets(SealedSecret)에 NCP Maps 키(NAVER_MAP_CLIENT_ID·NAVER_MAP_CLIENT_SECRET)를 더하거나 바꾼다
# (5.6 주소 검색, 좌표→시군구). 기존 항목(DB_PASSWORD·JWT_*·REDIS_PASSWORD)은 건드리지 않는다(kubeseal --merge-into)
# 서버 접속이 필요 없다. 키는 화면·파일·셸 이력에 남기지 않는다(입력을 숨기고 표준 입력으로만 kubeseal 에 넘긴다)
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/add-naver-map-keys.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/core-api-secrets.yaml

[ -f "$CERT" ] && [ -f "$OUT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }

read -rsp "NCP Maps Client ID: " ID; echo
read -rsp "NCP Maps Client Secret: " SECRET; echo
[[ "$ID" =~ ^[A-Za-z0-9]+$ && "$SECRET" =~ ^[A-Za-z0-9]+$ ]] || { unset ID SECRET; echo "키 형식이 아닙니다(영문·숫자만). 아무것도 바꾸지 않았습니다"; exit 1; }

printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: core-api-secrets\n  namespace: app\ntype: Opaque\nstringData:\n  NAVER_MAP_CLIENT_ID: %s\n  NAVER_MAP_CLIENT_SECRET: %s\n' "$ID" "$SECRET" \
  | kubeseal --cert "$CERT" --format yaml --merge-into "$OUT"
unset ID SECRET
echo "완료: $OUT 에 NAVER_MAP_CLIENT_ID·NAVER_MAP_CLIENT_SECRET 를 암호화해 합쳤습니다"
