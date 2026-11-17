#!/usr/bin/env bash
# ai_app 비밀번호 생성 → SealedSecret 두 곳에 같은 값으로 넣는다 (S15P31D106-396). 서버 접속이 필요 없다
#   data/postgres-credentials  AI_APP_PASSWORD : DB 초기화·create-ai-schema.sh용 (기존 키는 그대로, --merge-into)
#   app/match-worker-secrets   DB_PASSWORD     : match-worker 접속용 (파일을 새로 만든다)
# 평문은 화면·파일에 남기지 않는다. 필요하면 클러스터 Secret에서 꺼낸다
# 다시 실행하면 두 곳 모두 새 값으로 교체된다. push 후 교체했다면 노드 1에서 create-ai-schema.sh를 다시 실행
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-ai-app-secret.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
PG=infra/platform/secrets/postgres-credentials.yaml
OUT=infra/platform/secrets/match-worker-secrets.yaml

[ -f "$CERT" ] && [ -f "$PG" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

PW=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)
[ ${#PW} -eq 32 ] || { echo "비밀번호 생성 실패"; exit 1; }

printf "apiVersion: v1\nkind: Secret\nmetadata:\n  name: postgres-credentials\n  namespace: data\ntype: Opaque\nstringData:\n  AI_APP_PASSWORD: '%s'\n" "$PW" \
  | kubeseal --cert "$CERT" --format yaml --merge-into "$PG"
printf "apiVersion: v1\nkind: Secret\nmetadata:\n  name: match-worker-secrets\n  namespace: app\ntype: Opaque\nstringData:\n  DB_PASSWORD: '%s'\n" "$PW" \
  | kubeseal --cert "$CERT" --format yaml > "$OUT"
unset PW
echo "완료: $PG 의 AI_APP_PASSWORD, $OUT 의 DB_PASSWORD 갱신(평문은 출력·저장하지 않음)"
