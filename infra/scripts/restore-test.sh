#!/usr/bin/env bash
# PostgreSQL 백업 복원 테스트 (S15P31D106-347). S3 백업을 운영과 분리된 임시 Pod에 복원하고 운영 DB와 비교한다.
#
# 사용법 (노드 1, 일반 사용자. kubectl은 스크립트가 sudo -n k3s kubectl로 부른다. 비밀번호 없는 sudo 필요)
#   bash restore-test.sh <백업 TS 예: 20261106-0600>   복원·비교·정리
#   bash restore-test.sh cleanup                       중단돼 남은 임시 리소스 정리
#
# 백업 파일은 맥에서 만든 사전서명 URL로 받는다(파일이 맥·노드 1 디스크에 남지 않음). 스크립트가 물을 때마다 맥에서:
#   aws s3 presign "s3://s15p31d106-backup-887324111755/postgres/globals-<TS>.sql" --expires-in 600 --profile s15p31d106 | pbcopy
#   aws s3 presign "s3://s15p31d106-backup-887324111755/postgres/animal-<TS>.dump" --expires-in 600 --profile s15p31d106 | pbcopy
# 붙여 넣은 URL은 화면·셸 이력·프로세스 인자에 남지 않는다.
#
# 안전장치
#   - 쓰기는 모두 kt()로 restore-test 네임스페이스에만 한다. 운영 쪽 명령은 prod_ro()의 두 가지(get, dev_readonly psql)뿐
#   - 시작할 때 restore-test 네임스페이스가 있으면 중단. 삭제는 purpose=restore-test 라벨을 확인한 뒤에만
#   - 임시 Pod는 Secret·PVC·Service를 쓰지 않고, 소켓으로만 접속된다(listen_addresses 비움)
#   - 종료·중단·ssh 끊김 때 trap이 네임스페이스와 작업 폴더를 지운다. Pod는 1시간 뒤 강제 종료
#
# 결과: 차이가 없으면 exit 0, 있으면 exit 1. 계정·권한 쪽 차이와 테이블·행 차이를 나눠 보여 준다.
set -euo pipefail

readonly NS=restore-test
readonly POD=pg-restore-test
readonly NODE=ip-172-26-15-33
readonly IMAGE=pgvector/pgvector:pg17
readonly BUCKET=s15p31d106-backup-887324111755
readonly K=(sudo -n k3s kubectl)

kt() { "${K[@]}" -n "$NS" "$@"; }

# 운영 DB에 대한 명령은 여기서만 한다. 읽기만 허용
prod_ro() {
  case "$1" in
    check) "${K[@]}" -n data get pod postgres-0 -o name ;;
    query) "${K[@]}" -n data exec -i postgres-0 -- psql -X -A -t -q -v ON_ERROR_STOP=1 -U dev_readonly -d animal ;;
    *) echo "prod_ro: 허용되지 않은 동작: $1" >&2; return 1 ;;
  esac
}

ns_is_ours() {
  [ "$("${K[@]}" get ns "$NS" -o jsonpath='{.metadata.labels.purpose}' 2>/dev/null)" = restore-test ]
}

delete_ns() {
  if ns_is_ours; then
    echo "정리: 네임스페이스 $NS 삭제"
    "${K[@]}" delete ns "$NS" --wait --timeout=120s
  elif "${K[@]}" get ns "$NS" >/dev/null 2>&1; then
    echo "경고: $NS 에 purpose=restore-test 라벨이 없어 지우지 않습니다. 직접 확인하세요" >&2
  fi
}

sudo -n true || { echo "비밀번호 없는 sudo가 필요"; exit 1; }

if [ "${1:-}" = cleanup ]; then
  delete_ns
  exit 0
fi

TS=${1:?"사용법: bash $0 <백업 TS 예: 20261106-0600> | cleanup"}
[[ "$TS" =~ ^[0-9]{8}-[0-9]{4}$ ]] || { echo "TS 형식은 YYYYMMDD-HHMM" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl이 필요합니다" >&2; exit 1; }

CREATED=0
WORK=
cleanup() {
  set +e
  [ "$CREATED" = 1 ] && delete_ns
  [ -n "$WORK" ] && rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

prod_ro check >/dev/null
if "${K[@]}" get ns "$NS" >/dev/null 2>&1; then
  echo "$NS 네임스페이스가 이미 있습니다. 이전 실행이 남았다면: bash $0 cleanup" >&2
  exit 1
fi
WORK=$(mktemp -d)   # 비교 결과만 둔다(비밀값 없음). 끝나면 삭제

# 1) 임시 Pod
echo "1) 임시 Pod 생성 ($NS/$POD, 노드 $NODE)"
CREATED=1
"${K[@]}" create -f - <<YAML
apiVersion: v1
kind: Namespace
metadata:
  name: $NS
  labels: { purpose: restore-test }
---
apiVersion: v1
kind: Pod
metadata:
  name: $POD
  namespace: $NS
  labels: { purpose: restore-test }
spec:
  nodeSelector: { kubernetes.io/hostname: $NODE }
  restartPolicy: Never
  activeDeadlineSeconds: 3600
  automountServiceAccountToken: false
  enableServiceLinks: false
  containers:
    - name: postgres
      image: $IMAGE
      imagePullPolicy: IfNotPresent
      # 소켓 전용 접속. 버리는 DB라 fsync를 꺼 운영 DB와 같은 디스크의 I/O를 줄이고, 실패 문장(비밀번호 해시 포함 가능)을 로그에 남기지 않는다
      args: ["-c", "listen_addresses=", "-c", "fsync=off", "-c", "full_page_writes=off", "-c", "log_min_error_statement=panic"]
      env:
        - { name: POSTGRES_HOST_AUTH_METHOD, value: trust }
        - { name: PGDATA, value: /restore/pgdata }
        - { name: TZ, value: Asia/Seoul }
      resources:
        requests: { cpu: 200m, memory: 256Mi }
        limits: { memory: 1Gi }
      # 초기화 중 임시 서버가 아니라 최종 postgres가 PID 1이 된 뒤에 Ready
      readinessProbe:
        exec:
          command: ["sh", "-c", "[ \"\$(cat /proc/1/comm)\" = postgres ] && pg_isready -q -U postgres"]
        periodSeconds: 2
      volumeMounts:
        - { name: restore, mountPath: /restore }
  volumes:
    - name: restore
      emptyDir: { sizeLimit: 1Gi }
YAML
kt wait --for=condition=Ready "pod/$POD" --timeout=180s

# 2) 사전서명 URL로 Pod에 직접 스트리밍
fetch() { # $1 S3 파일 이름, $2 Pod 안 경로
  local url
  IFS= read -rs -p "  $1 사전서명 URL 붙여 넣기: " url; echo
  [[ "$url" == "https://$BUCKET.s3."*"/postgres/$1?"* ]] || { echo "  URL이 $1 을 가리키지 않습니다" >&2; return 1; }
  printf 'url = "%s"\n' "$url" | curl -sS --fail --proto =https --max-time 120 --config - \
    | kt exec -i "$POD" -- sh -c "cat > $2"
  kt exec "$POD" -- test -s "$2"
}
echo "2) 백업 받기 ($TS)"
fetch "globals-$TS.sql" /restore/globals.sql
fetch "animal-$TS.dump" /restore/animal.dump

# 3) globals → pg_restore, 끝나면 백업 파일 즉시 삭제
echo "3) 복원"
GLOBALS_OUT=$(kt exec "$POD" -- psql -X -q -U postgres -d postgres -v VERBOSITY=terse -f /restore/globals.sql 2>&1)
GLOBALS_ERR=$(grep 'ERROR:' <<<"$GLOBALS_OUT" | grep -v 'role "postgres" already exists' \
  | sed -E 's/SCRAM-SHA-256\$[^ '"'"'"]*/SCRAM-SHA-256$***/g' || true)
if [ -n "$GLOBALS_ERR" ]; then
  echo "globals 적용 실패:" >&2; echo "$GLOBALS_ERR" >&2; exit 1
fi
kt exec "$POD" -- pg_restore -U postgres -d postgres -C --exit-on-error /restore/animal.dump
kt exec "$POD" -- rm -f /restore/globals.sql /restore/animal.dump
echo "  복원 성공"

# 4) 비교. 양쪽에 같은 SELECT를 돌린다(비밀번호 해시는 조회하지 않음). ACL은 순서 차이를 없애려 정렬
COMPARE_SQL=$(cat << 'SQL'
select concat_ws(E'\t', 'EXT', extname, extversion) from pg_extension
union all
select concat_ws(E'\t', 'SCHEMA', nspname, pg_get_userbyid(nspowner),
  coalesce((select string_agg(a::text, ',' order by a::text) from unnest(nspacl) a), ''))
from pg_namespace where nspname !~ '^pg_' and nspname <> 'information_schema'
union all
select concat_ws(E'\t', 'ROLE', rolname,
  concat_ws(',', case when rolsuper then 'super' end, case when rolinherit then 'inherit' end,
    case when rolcreaterole then 'createrole' end, case when rolcreatedb then 'createdb' end,
    case when rolcanlogin then 'login' end, case when rolreplication then 'replication' end,
    case when rolbypassrls then 'bypassrls' end),
  rolconnlimit, coalesce(array_to_string(rolconfig, ','), ''))
from pg_roles where rolname !~ '^pg_'
union all
select concat_ws(E'\t', 'MEMBER', r.rolname, m.rolname)
from pg_auth_members am join pg_roles r on r.oid = am.roleid join pg_roles m on m.oid = am.member
union all
select concat_ws(E'\t', 'DB', datname, coalesce((select string_agg(a::text, ',' order by a::text) from unnest(datacl) a), ''))
from pg_database where datname = current_database()
union all
select concat_ws(E'\t', 'DBSET', coalesce(nullif(pg_get_userbyid(setrole), 'unknown (OID=0)'), '(all)'), array_to_string(setconfig, ','))
from pg_db_role_setting where setdatabase = (select oid from pg_database where datname = current_database())
union all
select concat_ws(E'\t', 'DEFACL', pg_get_userbyid(defaclrole), coalesce(n.nspname, ''), defaclobjtype,
  coalesce((select string_agg(a::text, ',' order by a::text) from unnest(defaclacl) a), ''))
from pg_default_acl d left join pg_namespace n on n.oid = d.defaclnamespace
union all
select concat_ws(E'\t', 'TABLE', n.nspname || '.' || c.relname, pg_get_userbyid(c.relowner),
  coalesce((select string_agg(a::text, ',' order by a::text) from unnest(c.relacl) a), ''))
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where c.relkind in ('r', 'p') and n.nspname !~ '^pg_' and n.nspname <> 'information_schema'
union all
select concat_ws(E'\t', 'ROWS', n.nspname || '.' || c.relname,
  (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', n.nspname, c.relname), false, true, '')))[1]::text)
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where c.relkind in ('r', 'p') and n.nspname !~ '^pg_' and n.nspname <> 'information_schema';
SQL
)
echo "4) 비교"
B="$WORK/backup.tsv"; P="$WORK/prod.tsv"
kt exec -i "$POD" -- psql -X -A -t -q -v ON_ERROR_STOP=1 -U postgres -d animal <<<"$COMPARE_SQL" | LC_ALL=C sort > "$B"
prod_ro query <<<"$COMPARE_SQL" | LC_ALL=C sort > "$P"

meta() { grep -vE $'^(TABLE|ROWS)\t' "$1" || true; }
keys() { awk -F'\t' '$1=="TABLE"{print $2}' "$1" | LC_ALL=C sort; }
count() { [ -z "$1" ] && echo 0 || printf '%s\n' "$1" | wc -l | tr -d ' '; }

META_DIFF=$(diff <(meta "$B") <(meta "$P") | sed -n 's/^< /  백업에만: /p; s/^> /  운영에만: /p' || true)
TABLE_PERM=$(awk -F'\t' 'NR==FNR { if ($1=="TABLE") b[$2]=$3" "$4; next }
  $1=="TABLE" && ($2 in b) && b[$2] != $3" "$4 { print "  " $2 ": 소유자·권한 백업 [" b[$2] "] / 운영 [" $3 " " $4 "]" }' "$B" "$P")
ONLY_PROD=$(LC_ALL=C comm -13 <(keys "$B") <(keys "$P") | sed 's/^/  /')
ONLY_BACKUP=$(LC_ALL=C comm -23 <(keys "$B") <(keys "$P") | sed 's/^/  /')
ROWS_DIFF=$(awk -F'\t' 'NR==FNR { if ($1=="ROWS") b[$2]=$3; next }
  $1=="ROWS" && ($2 in b) && b[$2] != $3 { printf "  %s: 운영 %s, 백업 %s (%+d)\n", $2, $3, b[$2], $3 - b[$2] }' "$B" "$P")

PERM_N=$(( $(count "$META_DIFF") + $(count "$TABLE_PERM") ))
echo
echo "== 계정·확장·스키마·권한 차이 (있으면 문제 신호) =="
[ "$PERM_N" -eq 0 ] && echo "  없음" || { [ -n "$META_DIFF" ] && echo "$META_DIFF"; [ -n "$TABLE_PERM" ] && echo "$TABLE_PERM"; }
echo
echo "== 테이블·행 차이 (백업 $TS 이후 운영 변경이면 정상일 수 있음) =="
echo "운영에만 있는 테이블:";   echo "${ONLY_PROD:-  없음}"
echo "백업에만 있는 테이블:";   echo "${ONLY_BACKUP:-  없음}"
echo "행 수가 다른 테이블 (운영−백업):"; echo "${ROWS_DIFF:-  없음}"
echo
echo "== 요약 (비교한 테이블: 백업 $(keys "$B" | wc -l | tr -d ' ')개, 운영 $(keys "$P" | wc -l | tr -d ' ')개) =="
echo "  계정·확장·스키마·권한 차이: ${PERM_N}건"
echo "  테이블·행 차이: 운영에만 $(count "$ONLY_PROD"), 백업에만 $(count "$ONLY_BACKUP"), 행 수 다름 $(count "$ROWS_DIFF")"

[ "$PERM_N" -eq 0 ] && [ -z "$ONLY_PROD$ONLY_BACKUP$ROWS_DIFF" ] && exit 0
exit 1
