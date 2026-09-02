resource "aws_cognito_user_pool" "this" {
  name = var.user_pool_name

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = false
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

# 백엔드는 cognito:groups 클레임으로 ADMIN 권한을 판별한다
# (member_db.members.role CHECK 'USER'|'ADMIN'과 별개로, JWT 인가는 그룹 기준).
resource "aws_cognito_user_group" "admin" {
  name         = "ADMIN"
  user_pool_id = aws_cognito_user_pool.this.id
  description  = "관리자 콘솔 접근 및 1:1 문의 게시판 답변 권한"
}

resource "aws_cognito_user_pool_client" "this" {
  name         = "lion-team3-web"
  user_pool_id = aws_cognito_user_pool.this.id

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    # 백엔드(CognitoAuthClient.login)가 클라이언트 SRP/PASSWORD가 아니라 서버 사이드
    # AdminInitiateAuth(ADMIN_USER_PASSWORD_AUTH)로 로그인시킨다 - 이게 없으면
    # "Auth flow not enabled for this client"로 로그인이 항상 실패한다
    # (2026-08-23 dev 실배포에서 실제로 겪음).
    "ALLOW_ADMIN_USER_PASSWORD_AUTH",
  ]

  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  allowed_oauth_flows_user_pool_client = true
  supported_identity_providers         = ["COGNITO"]

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  generate_secret = false # SPA 프론트엔드는 시크릿을 안전히 못 숨기므로 public client로
}

resource "aws_cognito_user_pool_domain" "this" {
  domain       = var.custom_domain_name
  user_pool_id = aws_cognito_user_pool.this.id
}
