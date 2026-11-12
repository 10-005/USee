#!/usr/bin/env bash
# 이미 초기화된 DB에 AI 매칭 워커용 vector 확장·ai_app 롤·ai 스키마를 만들거나, 비밀번호를 Secret 값으로 맞춘다 (여러 번 실행해도 안전, S15P31D106-396)
# 새로 초기화하는 DB는 init-configmap.yaml이 만들므로 실행할 필요 없음
# 비밀번호는 Secret → 파이프 → 컨테이너 환경변수 → psql \getenv 로만 전달 (화면·셸 이력·프로세스 인자에 남지 않음)
# 사용법: gen-ai-app-secret.sh로 만든 SealedSecret이 push·반영된 뒤, 노드 1에서  sudo bash create-ai-schema.sh
set -euo pipefail

NS=data
POD=postgres-0

PW_B64=$(kubectl -n "$NS" get secret postgres-credentials -o jsonpath='{.data.AI_APP_PASSWORD}')
[ -n "$PW_B64" ] || { echo "Secret에 AI_APP_PASSWORD가 없습니다. SealedSecret을 push하고 반영된 뒤 다시 실행하세요"; exit 1; }

REMOTE=$(cat << 'SH'
set -euo pipefail
IFS= read -r PW
export PW
psql -v ON_ERROR_STOP=1 -U postgres -d animal << 'SQL'
\getenv pw PW
CREATE EXTENSION IF NOT EXISTS vector;
SELECT 'CREATE ROLE ai_app' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_app') \gexec
ALTER ROLE ai_app WITH LOGIN PASSWORD :'pw';
GRANT CONNECT ON DATABASE animal TO ai_app;
GRANT USAGE ON SCHEMA public TO ai_app;
CREATE SCHEMA IF NOT EXISTS ai AUTHORIZATION ai_app;
ALTER SCHEMA ai OWNER TO ai_app;
ALTER ROLE ai_app SET search_path = ai, public;
SQL
SH
)

{ printf '%s' "$PW_B64" | base64 -d; echo; } | kubectl -n "$NS" exec -i "$POD" -- bash -c "$REMOTE"
echo "완료: vector 확장, ai_app, ai 스키마"
