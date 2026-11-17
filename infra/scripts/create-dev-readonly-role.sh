#!/usr/bin/env bash
# 이미 초기화된 DB에 dev_readonly 롤을 만들거나, 비밀번호를 Secret 값으로 맞춘다 (여러 번 실행해도 안전)
# 새로 초기화하는 DB는 init-configmap.yaml이 만들므로 실행할 필요 없음
# 비밀번호는 Secret → 파이프 → 컨테이너 환경변수 → psql \getenv 로만 전달 (화면·셸 이력·프로세스 인자에 남지 않음)
# 사용법: postgres-credentials에 DEV_READONLY_PASSWORD가 반영된 뒤, 노드 1에서  sudo bash create-dev-readonly-role.sh
set -euo pipefail

NS=data
POD=postgres-0

PW_B64=$(kubectl -n "$NS" get secret postgres-credentials -o jsonpath='{.data.DEV_READONLY_PASSWORD}')
[ -n "$PW_B64" ] || { echo "Secret에 DEV_READONLY_PASSWORD가 없습니다. SealedSecret을 push하고 반영된 뒤 다시 실행하세요"; exit 1; }

REMOTE=$(cat << 'SH'
set -euo pipefail
IFS= read -r PW
export PW
psql -v ON_ERROR_STOP=1 -U postgres -d animal << 'SQL'
\getenv pw PW
SELECT 'CREATE ROLE dev_readonly' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dev_readonly') \gexec
ALTER ROLE dev_readonly WITH LOGIN PASSWORD :'pw' CONNECTION LIMIT 5;
GRANT CONNECT ON DATABASE animal TO dev_readonly;
GRANT pg_read_all_data TO dev_readonly;
ALTER ROLE dev_readonly SET default_transaction_read_only = on;
ALTER ROLE dev_readonly SET statement_timeout = '30s';
SQL
SH
)

{ printf '%s' "$PW_B64" | base64 -d; echo; } | kubectl -n "$NS" exec -i "$POD" -- bash -c "$REMOTE"
echo "완료: dev_readonly"
