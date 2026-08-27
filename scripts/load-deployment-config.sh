#!/usr/bin/env bash
# AWS OIDC 인증 후 실행한다. AWS가 생성하거나 결정하는 배포 설정은 GitHub
# Variables에 복제하지 않고 SSM을 단일 원본으로 사용한다.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <dev|prod> <split|integrated> <github-env-file>" >&2
  exit 1
fi

DEPLOY_ENV="$1"
MODE="$2"
ENV_FILE="$3"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

if [[ "$DEPLOY_ENV" != "dev" && "$DEPLOY_ENV" != "prod" ]] ||
   [[ "$MODE" != "split" && "$MODE" != "integrated" ]]; then
  echo "unsupported deployment target: environment=$DEPLOY_ENV mode=$MODE" >&2
  exit 1
fi

INFRA_ENV="$DEPLOY_ENV"
DATA_ENV="$DEPLOY_ENV"
EDGE_PREFIX="/$DEPLOY_ENV"
if [[ "$MODE" == "integrated" ]]; then
  INFRA_ENV="integrated"
  if [[ "$DEPLOY_ENV" == "prod" ]]; then
    DATA_ENV="integrated"
    EDGE_PREFIX="/integrated"
  else
    DATA_ENV="dev"
    EDGE_PREFIX="/integrated/dev"
  fi
fi

ssm() {
  aws ssm get-parameter \
    --name "/$1/$2" \
    --region "$AWS_REGION" \
    --query 'Parameter.Value' \
    --output text
}

ssm_optional() {
  aws ssm get-parameter \
    --name "$1" \
    --region "$AWS_REGION" \
    --query 'Parameter.Value' \
    --output text 2>/dev/null || true
}

put() {
  printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
}

DOMAIN=$([[ "$DEPLOY_ENV" == "dev" ]] && printf dev.ajttk.com || printf book.ajttk.com)
VPC_ID="$(ssm "$INFRA_ENV" network/vpc_id)"
VPC_CIDR="$(aws ec2 describe-vpcs --vpc-ids "$VPC_ID" --region "$AWS_REGION" --query 'Vpcs[0].CidrBlock' --output text)"

put DEPLOY_ENV "$DEPLOY_ENV"
put INFRA_ENV "$INFRA_ENV"
put DATA_ENV "$DATA_ENV"
put EKS_CLUSTER "lion-team3-${INFRA_ENV}"
put API_HOST "$DOMAIN"
put FRONTEND_ORIGIN "https://${DOMAIN}"
put VPC_CIDR "$VPC_CIDR"
put FRONTEND_S3_BUCKET "$(ssm "$DATA_ENV" storage/frontend_bucket_id)"
put EBOOK_S3_BUCKET "$(ssm "$DATA_ENV" storage/media_bucket_id)"
put DB_WRITER_ENDPOINT "$(ssm "$DATA_ENV" data/db_endpoint)"
put DB_READER_ENDPOINT "$(ssm "$DATA_ENV" data/db_reader_endpoint)"
put DB_NAME "bookdb_${DEPLOY_ENV}"
put AI_DB_NAME "bookdb_${DEPLOY_ENV}"
DB_SSL_MODE="disable"
if [[ "$DEPLOY_ENV" == "prod" && "$MODE" == "split" ]]; then DB_SSL_MODE="require"; fi
put DB_SSL_MODE "$DB_SSL_MODE"
put SPRING_PROFILE "prod"
put REDIS_HOST "$(ssm "$DATA_ENV" data/valkey_endpoint)"
put AWS_COGNITO_USER_POOL_ID "$(ssm "$DATA_ENV" auth/user_pool_id)"
put SQS_PURCHASE_QUEUE_URL "$(ssm "$DATA_ENV" ai/purchase_channel_url)"
put SQS_INGEST_QUEUE_URL "$(ssm "$DATA_ENV" ai/ingest_channel_url)"
put AI_VECTOR_BUCKET "lion-team3-${DEPLOY_ENV}-vectors"
put ECR_CATALOG_REPO "lion-team3-${INFRA_ENV}/catalog"
put ECR_ORDER_REPO "lion-team3-${INFRA_ENV}/order"
put ECR_MEMBER_REPO "lion-team3-${INFRA_ENV}/member"
put ECR_AI_REPO "lion-team3-${INFRA_ENV}/ai"
put CLOUDFRONT_DIST_ID "$(ssm_optional "${EDGE_PREFIX}/edge/cloudfront_distribution_id")"

if [[ "$MODE" == "integrated" && "$DEPLOY_ENV" == "dev" ]]; then
  put AI_SERVICE_IRSA_ARN "$(ssm integrated dev/ai/service_irsa_arn)"
  put MEMBER_SERVICE_IRSA_ARN "$(ssm integrated dev/member/service_irsa_arn)"
else
  put AI_SERVICE_IRSA_ARN "$(ssm "$DATA_ENV" ai/service_irsa_arn)"
  put MEMBER_SERVICE_IRSA_ARN "$(ssm "$DATA_ENV" member/service_irsa_arn)"
fi
