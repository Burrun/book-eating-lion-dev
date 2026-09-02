#!/usr/bin/env bash
# GitHub Actions Secrets만 AWS에서 조회해 등록한다. Environment와 일반
# Variables는 terraform/github, AWS 동적 설정은 SSM + load-deployment-config.sh가 관리한다.
#
# 전제:
#   - gh CLI 로그인 완료 (gh auth status)
#   - aws CLI 자격 증명 설정 완료 (aws sts get-caller-identity)
#   - 대상 환경(dev/prod)의 terraform 00-base/01-data/02-runtime이 이미 apply돼 있을 것
#     (SSM 파라미터가 없으면 해당 값은 건너뛰고 경고만 출력함)
#
# 사용법:
#   ./scripts/sync-github-config.sh dev split
#   ./scripts/sync-github-config.sh prod split
#   ./scripts/sync-github-config.sh dev integrated
#   ./scripts/sync-github-config.sh prod integrated
#
# split은 GitHub Environments dev/prod, integrated는 integrated-dev/
# integrated-prod에 등록한다. 운영 모드별 설정이 서로 덮어쓰이지 않는다.

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "사용법: $0 <dev|prod> [split|integrated]" >&2
  exit 1
fi

DEPLOY_ENV="$1"
MODE="${2:-split}"
if [[ "$DEPLOY_ENV" != "dev" && "$DEPLOY_ENV" != "prod" ]] ||
   [[ "$MODE" != "split" && "$MODE" != "integrated" ]]; then
  echo "사용법: $0 <dev|prod> [split|integrated]" >&2
  exit 1
fi

if [[ "$MODE" == "integrated" ]]; then
  GH_ENV="integrated-${DEPLOY_ENV}"
  INFRA_ENV="integrated"
  if [[ "$DEPLOY_ENV" == "dev" ]]; then
    DATA_ENV="dev"
  else
    DATA_ENV="integrated"
  fi
else
  GH_ENV="$DEPLOY_ENV"
  INFRA_ENV="$DEPLOY_ENV"
  DATA_ENV="$DEPLOY_ENV"
fi

REPO="Burrun/book-eating-lion-dev"
REGION="ap-northeast-2"
echo "GitHub Environment '${GH_ENV}' Secrets 동기화 (${MODE} mode, deploy=${DEPLOY_ENV}, data=${DATA_ENV})"

ssm() {
  local prefix="$1" key="$2"
  aws ssm get-parameter --name "/${prefix}/${key}" --region "$REGION" --query 'Parameter.Value' --output text 2>/dev/null || true
}

set_secret_from_stdin() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "  ⚠️  SKIP  $name — 값을 못 찾음"
    return
  fi
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO" --env "$GH_ENV" >/dev/null
  echo "  ✅ SET   $name (값은 출력 안 함)"
}

echo "=== [$GH_ENV] Secrets 등록 ==="

set_secret_from_stdin "AWS_COGNITO_CLIENT_ID" "$(ssm "$DATA_ENV" auth/user_pool_client_id)"

echo "  ⚠️  SKIP  AWS_COGNITO_CLIENT_SECRET — Cognito 클라이언트가 generate_secret=false(공개 클라이언트)라 시크릿 자체가 없음"

# 서비스별 DB 계정 시크릿. 01-data의 db_service_accounts 모듈이 만들고
# /{env}/data/db_{catalog,order,member,ai}_secret_arn 으로 등록한다.
#
# 2026-09-02 이전에는 아래 마스터 시크릿 하나를 8개 값에 그대로 복제했다.
# 그래서 배포 환경의 4개 서비스가 전부 bookadmin으로 DB에 붙었고, README와
# k8s/base/03-secret.yaml이 주장하는 "서비스별 계정 권한이 경계를 만든다"가
# 실제로는 성립하지 않았다(라이브 DB의 테이블 owner가 전부 bookadmin이었다).
#
# 계정별 시크릿이 없는 환경(dev의 EC2 Postgres)은 마스터 폴백으로 간다.
read_secret_json() {
  aws secretsmanager get-secret-value \
    --secret-id "$1" \
    --region "$REGION" --query SecretString --output text 2>/dev/null || true
}

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)[sys.argv[1]])" "$1"
}

DB_ACCOUNTS_SYNCED=0

for svc in catalog order member ai; do
  SVC_UPPER=$(echo "$svc" | tr '[:lower:]' '[:upper:]')
  SVC_SECRET_ARN=$(ssm "$DATA_ENV" "data/db_${svc}_secret_arn")
  [[ -z "$SVC_SECRET_ARN" ]] && continue

  SVC_SECRET_JSON=$(read_secret_json "$SVC_SECRET_ARN")
  if [[ -z "$SVC_SECRET_JSON" ]]; then
    echo "  ⚠️  SKIP  ${SVC_UPPER}_DB_USERNAME/PASSWORD — Secrets Manager에서 $SVC_SECRET_ARN 조회 실패"
    continue
  fi

  set_secret_from_stdin "${SVC_UPPER}_DB_USERNAME" "$(echo "$SVC_SECRET_JSON" | json_field username)"
  set_secret_from_stdin "${SVC_UPPER}_DB_PASSWORD" "$(echo "$SVC_SECRET_JSON" | json_field password)"
  DB_ACCOUNTS_SYNCED=$((DB_ACCOUNTS_SYNCED + 1))
done

# 4개 중 일부만 성공하면 나머지엔 이전 값(= 마스터 계정)이 남는다. 그 상태로
# 배포하면 일부 서비스만 계정 분리가 적용돼 원인 파악이 어려워진다.
if [[ "$DB_ACCOUNTS_SYNCED" -ne 0 && "$DB_ACCOUNTS_SYNCED" -ne 4 ]]; then
  echo "  ⚠️  계정 4개 중 $DB_ACCOUNTS_SYNCED 개만 동기화됨 — 나머지는 이전 값이 남아 파드가 인증 실패할 수 있습니다"
fi

if [[ "$DB_ACCOUNTS_SYNCED" -eq 0 ]]; then
echo "  ℹ️  계정별 시크릿이 없어 마스터 계정으로 폴백합니다 (계정 분리 전 환경)"


# dev(ec2_postgres)/prod(aurora_pg) 둘 다 같은 이름으로 SSM에 등록돼 있다
# (terraform/environments/{env}/01-data/main.tf의 db_master_secret_arn 참고) -
# 환경별로 시크릿 이름 자체가 다르므로(EC2용 vs Aurora 자동 발급) 하드코딩 대신
# 이 ARN으로 조회한다.
DB_MASTER_SECRET_ARN=$(ssm "$DATA_ENV" data/db_master_secret_arn)

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
fi

echo "  ⚠️  SKIP  KAKAOPAY_SECRET_KEY — AWS에 없는 외부(카카오 개발자센터) 값, 직접 등록할 것: gh secret set KAKAOPAY_SECRET_KEY --repo $REPO --env $GH_ENV"

echo ""
echo "=== 완료 ==="
echo "SKIP 표시된 항목은 위 안내대로 별도로 채워야 합니다."
