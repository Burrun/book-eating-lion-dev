#!/usr/bin/env bash
# GitHub Actions Variables/Secrets를 AWS에서 직접 조회해서 자동으로 등록한다.
# 콘솔 왔다갔다 하면서 값 복사/붙여넣기 하는 걸 없애기 위한 스크립트.
#
# 전제:
#   - gh CLI 로그인 완료 (gh auth status)
#   - aws CLI 자격 증명 설정 완료 (aws sts get-caller-identity)
#   - 대상 환경(dev/prod)의 terraform 00-base/01-data/02-runtime이 이미 apply돼 있을 것
#     (SSM 파라미터가 없으면 해당 값은 건너뛰고 경고만 출력함)
#
# 사용법:
#   ./scripts/sync-github-config.sh dev
#   ./scripts/sync-github-config.sh prod
#
# 주의: main-cd.yml은 GitHub Environments를 안 쓰고 레포 레벨 Variables/Secrets
# 하나만 쓴다 - 즉 이 스크립트를 dev로 한 번 돌리고 나중에 prod로 다시 돌리면
# 값이 prod 것으로 덮어써진다. dev/prod를 동시에 운영하려면 main-cd.yml에
# GitHub Environments를 도입하는 별도 작업이 필요하다(지금 스코프 밖).

set -euo pipefail

ENV="${1:-}"
if [[ "$ENV" != "dev" && "$ENV" != "prod" ]]; then
  echo "사용법: $0 <dev|prod>" >&2
  exit 1
fi

REPO="Burrun/book-eating-lion-dev"
REGION="ap-northeast-2"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

ssm() {
  aws ssm get-parameter --name "/${ENV}/$1" --region "$REGION" --query 'Parameter.Value' --output text 2>/dev/null || true
}

set_var() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "  ⚠️  SKIP  $name — 값을 못 찾음"
    return
  fi
  gh variable set "$name" --repo "$REPO" --body "$value" >/dev/null
  echo "  ✅ SET   $name"
}

set_secret_from_stdin() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "  ⚠️  SKIP  $name — 값을 못 찾음"
    return
  fi
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO" >/dev/null
  echo "  ✅ SET   $name (값은 출력 안 함)"
}

echo "=== [$ENV] Variables 등록 ==="

set_var "AWS_REGION" "$REGION"
set_var "AWS_ROLE_ARN" "$(ssm ci/github_actions_role_arn)"
set_var "EKS_CLUSTER" "lion-team3-${ENV}"
set_var "AWS_COGNITO_USER_POOL_ID" "$(ssm auth/user_pool_id)"

DOMAIN=$([[ "$ENV" == "dev" ]] && echo "dev.ajttk.com" || echo "book.ajttk.com")
set_var "API_HOST" "$DOMAIN"
set_var "FRONTEND_ORIGIN" "https://${DOMAIN}"

VPC_ID=$(ssm network/vpc_id)
VPC_CIDR=$(aws ec2 describe-vpcs --vpc-ids "$VPC_ID" --region "$REGION" --query 'Vpcs[0].CidrBlock' --output text 2>/dev/null || true)
set_var "VPC_CIDR" "$VPC_CIDR"

set_var "FRONTEND_S3_BUCKET" "$(ssm storage/frontend_bucket_id)"

# S3 Vectors는 provider 미지원으로 Terraform이 아직 안 만듦 (인프라구성명세.md §7.5 참고)
# - §7.5 가이드대로 aws s3vectors create-vector-bucket을 먼저 돌렸다면 아래 이름으로 채워짐
AI_VECTOR_BUCKET="lion-team3-${ENV}-vectors"
if aws s3vectors get-vector-bucket --vector-bucket-name "$AI_VECTOR_BUCKET" --region "$REGION" >/dev/null 2>&1; then
  set_var "AI_VECTOR_BUCKET" "$AI_VECTOR_BUCKET"
else
  echo "  ⚠️  SKIP  AI_VECTOR_BUCKET — 아직 안 만들어짐 (인프라구성명세.md §7.5 가이드로 먼저 생성할 것)"
fi

for svc in CATALOG ORDER MEMBER AI; do
  lower=$(echo "$svc" | tr '[:upper:]' '[:lower:]')
  repo_uri="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/lion-team3-${ENV}/${lower}"
  if aws ecr describe-repositories --repository-names "lion-team3-${ENV}/${lower}" --region "$REGION" >/dev/null 2>&1; then
    set_var "ECR_${svc}_REPO" "$repo_uri"
  else
    echo "  ⚠️  SKIP  ECR_${svc}_REPO — ECR 레포가 아직 없음"
  fi
done

set_var "AURORA_ENDPOINT" "$(ssm data/db_endpoint)"
set_var "AURORA_READER_ENDPOINT" "$(ssm data/db_reader_endpoint)"
set_var "DB_NAME" "bookdb"
echo "  ⚠️  SKIP  AI_DB_NAME — Terraform엔 DB 하나(bookdb)뿐, AI 전용 DB명이 따로 있으면 직접 등록할 것"

set_var "REDIS_HOST" "$(ssm data/valkey_endpoint)"
set_var "SQS_PURCHASE_QUEUE_URL" "$(ssm ai/purchase_channel_url)"

set_var "SPRING_PROFILE" "$ENV"
set_var "K8S_NAMESPACE" "lion-app"

CF_DIST_ID=$(aws cloudfront list-distributions --query "DistributionList.Items[?Aliases.Items[?@=='${DOMAIN}']].Id | [0]" --output text 2>/dev/null || true)
if [[ -n "$CF_DIST_ID" && "$CF_DIST_ID" != "None" ]]; then
  set_var "CLOUDFRONT_DIST_ID" "$CF_DIST_ID"
else
  echo "  ⚠️  SKIP  CLOUDFRONT_DIST_ID — edge_routing이 아직 안 올라간 것 같음 (선택 항목이라 안 넣어도 배포는 됨)"
fi

echo ""
echo "=== [$ENV] Secrets 등록 ==="

set_secret_from_stdin "AWS_COGNITO_CLIENT_ID" "$(ssm auth/user_pool_client_id)"

echo "  ⚠️  SKIP  AWS_COGNITO_CLIENT_SECRET — Cognito 클라이언트가 generate_secret=false(공개 클라이언트)라 시크릿 자체가 없음"

MASTER_SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "lion-team3-${ENV}-ec2-postgres-master" \
  --region "$REGION" --query SecretString --output text 2>/dev/null || true)

if [[ -z "$MASTER_SECRET_JSON" ]]; then
  echo "  ⚠️  SKIP  DB 계정 8개(CATALOG/ORDER/MEMBER/AI × USERNAME/PASSWORD) — Secrets Manager에서 못 찾음 (Aurora를 쓰는 환경이면 master_user_secret_arn 쪽을 대신 조회해야 함, 이 스크립트는 dev의 ec2_postgres 기준)"
else
  DB_USER=$(echo "$MASTER_SECRET_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['username'])")
  DB_PASS=$(echo "$MASTER_SECRET_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['password'])")
  for svc in CATALOG ORDER MEMBER AI; do
    set_secret_from_stdin "${svc}_DB_USERNAME" "$DB_USER"
    set_secret_from_stdin "${svc}_DB_PASSWORD" "$DB_PASS"
  done
fi

echo "  ⚠️  SKIP  KAKAOPAY_SECRET_KEY — AWS에 없는 외부(카카오 개발자센터) 값, 직접 등록할 것: gh secret set KAKAOPAY_SECRET_KEY --repo $REPO"

echo ""
echo "=== 완료 ==="
echo "SKIP 표시된 항목은 위 안내대로 별도로 채워야 합니다."
