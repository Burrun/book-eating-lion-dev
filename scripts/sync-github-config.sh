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
# GitHub Environments(dev/prod)에 스코프된 Variables/Secrets로 등록한다 - repo
# 레벨이 아니라서 dev로 돌린 뒤 prod로 다시 돌려도 서로 안 덮어쓴다. 환경이
# 없으면 먼저 만든다(멱등). main-cd.yml은 workflow_dispatch의 `environment`
# 입력값으로 각 job의 `environment:`를 지정해서 이 스코프를 읽는다.

set -euo pipefail

ENV="${1:-}"
if [[ "$ENV" != "dev" && "$ENV" != "prod" ]]; then
  echo "사용법: $0 <dev|prod>" >&2
  exit 1
fi

REPO="Burrun/book-eating-lion-dev"
REGION="ap-northeast-2"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# GitHub Environment가 없으면 생성(있으면 그대로 통과 - PUT은 멱등).
gh api --method PUT "repos/${REPO}/environments/${ENV}" >/dev/null || \
  { echo "Failed to ensure GitHub Environment '${ENV}' exists; check gh authentication/permissions." >&2; exit 1; }
echo "GitHub Environment '${ENV}' 준비됨"

ssm() {
  aws ssm get-parameter --name "/${ENV}/$1" --region "$REGION" --query 'Parameter.Value' --output text 2>/dev/null || true
}

set_var() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "  ⚠️  SKIP  $name — 값을 못 찾음"
    return
  fi
  gh variable set "$name" --repo "$REPO" --env "$ENV" --body "$value" >/dev/null
  echo "  ✅ SET   $name"
}

set_secret_from_stdin() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "  ⚠️  SKIP  $name — 값을 못 찾음"
    return
  fi
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO" --env "$ENV" >/dev/null
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

# ebook 전용 버킷은 따로 없다 - main-cd.yml 주석대로 media 버킷을 재사용한다
# (TERRAFORM_STRUCTURE.md, 인프라구성명세.md §naming).
set_var "EBOOK_S3_BUCKET" "$(ssm storage/media_bucket_id)"

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
  # main-cd.yml이 레지스트리 주소를 "$ECR_REGISTRY/$ECR_${svc}_REPO"로 직접
  # 조합하므로 여기엔 순수 리포 이름만 넣는다(레지스트리 포함하면 중복됨).
  repo_name="lion-team3-${ENV}/${lower}"
  if aws ecr describe-repositories --repository-names "$repo_name" --region "$REGION" >/dev/null 2>&1; then
    set_var "ECR_${svc}_REPO" "$repo_name"
  else
    echo "  ⚠️  SKIP  ECR_${svc}_REPO — ECR 레포가 아직 없음"
  fi
done

set_var "AURORA_ENDPOINT" "$(ssm data/db_endpoint)"
set_var "AURORA_READER_ENDPOINT" "$(ssm data/db_reader_endpoint)"
set_var "DB_NAME" "bookdb"
# ai_db도 스키마만 다를 뿐 같은 bookdb 안에 있다(AI_DB_HOST 옆 주석 참고) - 별도
# AI 전용 DB를 새로 팠다면 이 값을 바꿀 것. 예전엔 이 값을 스킵하고 사람이
# 수동 등록하도록 남겨뒀었는데, 아무도 안 채워서 ai-api가 빈 DB명으로 접속
# 계정명("bookadmin")에 접속을 시도하다 죽는 사고가 났다(인프라구성명세.md §7.7.1 ⑫).
set_var "AI_DB_NAME" "bookdb"

set_var "REDIS_HOST" "$(ssm data/valkey_endpoint)"
# 아래 ssm() 인자("ai/...")는 terraform/environments/{env}/01-data/main.tf의
# locals.ai_channel_ssm_values 키와 정확히 같아야 한다 - 두 값이 각각 따로
# 하드코딩돼 있어서, 한쪽만 바꾸면 여기가 조용히 빈 값을 등록한다(/code-review
# 지적사항). 이 SSM 키를 바꿀 땐 반드시 그 locals도 같이 바꿀 것.
set_var "SQS_PURCHASE_QUEUE_URL" "$(ssm ai/purchase_channel_url)"
set_var "SQS_INGEST_QUEUE_URL" "$(ssm ai/ingest_channel_url)"
set_var "AI_SERVICE_IRSA_ARN" "$(ssm ai/service_irsa_arn)"
set_var "MEMBER_SERVICE_IRSA_ARN" "$(ssm member/service_irsa_arn)"

# backend엔 application-dev.yml이 없다 - application-prod.yml이 ${DB_HOST} 등으로
# 파라미터화돼 있어 재사용 가능하므로, AWS 환경명과 무관하게 Spring 프로필은
# 항상 "prod"로 고정한다.
set_var "SPRING_PROFILE" "prod"
# application-prod.yml의 sslmode=${DB_SSL_MODE:require} 기본값용. dev의 EC2
# Postgres는 SSL 미지원이라 disable, 실제 SSL을 쓰는 prod(Aurora)는 require.
if [[ "$ENV" == "dev" ]]; then
  set_var "DB_SSL_MODE" "disable"
else
  set_var "DB_SSL_MODE" "require"
fi
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

# dev(ec2_postgres)/prod(aurora_pg) 둘 다 같은 이름으로 SSM에 등록돼 있다
# (terraform/environments/{env}/01-data/main.tf의 db_master_secret_arn 참고) -
# 환경별로 시크릿 이름 자체가 다르므로(EC2용 vs Aurora 자동 발급) 하드코딩 대신
# 이 ARN으로 조회한다.
DB_MASTER_SECRET_ARN=$(ssm data/db_master_secret_arn)

if [[ -z "$DB_MASTER_SECRET_ARN" ]]; then
  echo "  ⚠️  SKIP  DB 계정 8개(CATALOG/ORDER/MEMBER/AI × USERNAME/PASSWORD) — SSM에 db_master_secret_arn이 없음 (01-data가 아직 이 파라미터를 안 만든 구버전이거나 apply 전)"
  MASTER_SECRET_JSON=""
else
  MASTER_SECRET_JSON=$(aws secretsmanager get-secret-value \
    --secret-id "$DB_MASTER_SECRET_ARN" \
    --region "$REGION" --query SecretString --output text 2>/dev/null || true)
fi

if [[ -z "$MASTER_SECRET_JSON" ]]; then
  if [[ -n "$DB_MASTER_SECRET_ARN" ]]; then
    echo "  ⚠️  SKIP  DB 계정 8개(CATALOG/ORDER/MEMBER/AI × USERNAME/PASSWORD) — Secrets Manager에서 $DB_MASTER_SECRET_ARN 조회 실패"
  fi
else
  DB_USER=$(echo "$MASTER_SECRET_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['username'])")
  DB_PASS=$(echo "$MASTER_SECRET_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['password'])")
  for svc in CATALOG ORDER MEMBER AI; do
    set_secret_from_stdin "${svc}_DB_USERNAME" "$DB_USER"
    set_secret_from_stdin "${svc}_DB_PASSWORD" "$DB_PASS"
  done
fi

echo "  ⚠️  SKIP  KAKAOPAY_SECRET_KEY — AWS에 없는 외부(카카오 개발자센터) 값, 직접 등록할 것: gh secret set KAKAOPAY_SECRET_KEY --repo $REPO --env $ENV"

echo ""
echo "=== 완료 ==="
echo "SKIP 표시된 항목은 위 안내대로 별도로 채워야 합니다."
