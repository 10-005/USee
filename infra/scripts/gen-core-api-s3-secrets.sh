#!/usr/bin/env bash
# core-api 이미지 업로드용 액세스 키 → SealedSecret (infra/platform/secrets/core-api-s3.yaml, namespace app)
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY : IAM 사용자 core-api-media(Terraform media.tf)의 새 액세스 키
# 키는 상태 파일에 남지 않도록 Terraform이 아니라 이 스크립트로 만든다. 비밀값은 변수로만 다루고 출력하지 않는다.
# 사용법: infra 작업 폴더 최상위에서  bash infra/scripts/gen-core-api-s3-secrets.sh
set -euo pipefail

CERT=infra/platform/sealed-secrets/pub-cert.pem
PROFILE=s15p31d106
USER_NAME=core-api-media
OUT=infra/platform/secrets/core-api-s3.yaml

[ -f "$CERT" ] || { echo "infra 작업 폴더 최상위에서 실행하세요"; exit 1; }
command -v kubeseal >/dev/null || { echo "kubeseal이 필요합니다"; exit 1; }

# 사용자당 액세스 키는 최대 2개. 이미 2개면 오래된 키를 정리한 뒤 다시 실행
COUNT=$(aws iam list-access-keys --user-name "$USER_NAME" --profile "$PROFILE" \
  --query 'length(AccessKeyMetadata)' --output text)
if [ "$COUNT" -ge 2 ]; then
  echo "$USER_NAME 의 액세스 키가 이미 2개입니다. 쓰지 않는 키를 삭제한 뒤 다시 실행하세요"
  exit 1
fi

read -r KEY_ID KEY_SECRET < <(aws iam create-access-key --user-name "$USER_NAME" --profile "$PROFILE" \
  --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text)
[ -n "$KEY_ID" ] && [ -n "$KEY_SECRET" ] || { echo "액세스 키를 만들지 못함"; exit 1; }

# 봉인에 실패하면 만든 키는 쓸 수 없으므로 삭제 안내 (키 ID는 비밀이 아님)
trap 'echo "봉인 실패: 액세스 키 $KEY_ID 를 콘솔에서 삭제한 뒤 다시 실행하세요"' ERR

cat << YAML | kubeseal --cert "$CERT" --format yaml > "$OUT"
apiVersion: v1
kind: Secret
metadata:
  name: core-api-s3
  namespace: app
type: Opaque
stringData:
  AWS_ACCESS_KEY_ID: '$KEY_ID'
  AWS_SECRET_ACCESS_KEY: '$KEY_SECRET'
YAML

echo "완료: $OUT (액세스 키 ID $KEY_ID)"
[ "$COUNT" -eq 1 ] && echo "  기존 키가 1개 더 있습니다. 새 키로 이미지 업로드(4.1·4.2)가 성공하면 기존 키를 삭제하세요"
exit 0
