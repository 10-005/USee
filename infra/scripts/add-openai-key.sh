#!/usr/bin/env bash
# simulation-secrets(SealedSecret)에 OPENAI_API_KEY 를 더하거나 바꾼다 (리포트 LLM 요약, S15P31D106-254)
# 기존 항목(DB_PASSWORD·JWT_PUBLIC_KEY)은 건드리지 않는다(kubeseal --merge-into). 서버 접속이 필요 없다
# 키는 화면·파일·셸 이력에 남기지 않는다(입력을 숨기고 표준 입력으로만 kubeseal 에 넘긴다)
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/add-openai-key.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
OUT=infra/platform/secrets/simulation-secrets.yaml

[ -f "$CERT" ] && [ -f "$OUT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }

read -rsp "OpenAI key: " KEY; echo
[[ "$KEY" =~ ^sk-[A-Za-z0-9_-]+$ ]] || { unset KEY; echo "키 형식이 아닙니다(sk- 로 시작). 아무것도 바꾸지 않았습니다"; exit 1; }

printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: simulation-secrets\n  namespace: app\ntype: Opaque\nstringData:\n  OPENAI_API_KEY: %s\n' "$KEY" \
  | kubeseal --cert "$CERT" --format yaml --merge-into "$OUT"
unset KEY
echo "완료: $OUT 에 OPENAI_API_KEY 를 암호화해 합쳤습니다"
