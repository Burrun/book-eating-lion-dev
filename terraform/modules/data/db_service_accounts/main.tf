# 서비스별 DB 계정의 비밀번호 생성 + Secrets Manager 보관.
#
# 왜 필요한가 - 2026-09-02 이전까지 배포 환경의 4개 서비스는 전부 마스터 계정
# (bookadmin)으로 DB에 붙고 있었다. scripts/sync-github-config.sh가 마스터 시크릿
# 하나를 GitHub Secrets 8개(CATALOG/ORDER/MEMBER/AI × USERNAME/PASSWORD)에 그대로
# 복제했기 때문이다. 그래서 README와 k8s/base/03-secret.yaml 주석이 주장하는
# "스키마 4분할 + 서비스별 계정 권한이 경계를 만든다"가 실제로는 성립하지 않았고,
# 라이브 DB의 모든 테이블 owner가 bookadmin이었다.
#
# 이 모듈이 만든 시크릿은 두 곳이 읽는다:
#   1) RDS Proxy - auth 블록에 계정별로 등록해야 그 계정의 접속을 통과시킨다
#   2) scripts/sync-github-config.sh - GitHub Secrets에 주입
#
# 비밀번호 값 자체는 DB에도 반영돼야 한다(db/postgres/00-init.sql의 CREATE ROLE).
# Terraform은 DB 안에 롤을 만들지 않는다 - RDS가 프라이빗 서브넷에 있어 CI 러너가
# 접속할 수 없기 때문이다. 그 단계는 VPC 안(dev EC2 또는 k8s Job)에서 수행한다.

resource "random_password" "this" {
  for_each = var.accounts

  length = 24
  # 접속 문자열·psql 변수·envsubst를 거치므로 특수문자 이스케이프 사고를 원천 차단한다
  # (modules/dev_tools/ec2_postgres의 random_password.master와 같은 판단).
  special = false
}

resource "aws_secretsmanager_secret" "this" {
  for_each = var.accounts

  name        = "lion-team3-${var.environment}-db-${each.key}"
  description = "DB service account for ${each.key} (${each.value})"

  # 이관 중 시크릿을 지웠다 다시 만드는 일이 잦다. 기본 30일 복구 대기가 걸려 있으면
  # 같은 이름으로 재생성이 막힌다.
  recovery_window_in_days = var.recovery_window_in_days
}

# RDS Proxy는 secret이 username/password 두 키를 가진 JSON일 것을 요구한다.
resource "aws_secretsmanager_secret_version" "this" {
  for_each = var.accounts

  secret_id = aws_secretsmanager_secret.this[each.key].id
  secret_string = jsonencode({
    username = each.value
    password = random_password.this[each.key].result
  })
}
