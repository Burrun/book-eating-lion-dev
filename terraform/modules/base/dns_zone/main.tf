# Zone만 만든다. 도메인이 실제로 무언가(CloudFront)를 가리키는 레코드는
# 02-runtime의 edge_routing이 만든다 — CloudFront가 아직 없는 00-base 시점엔
# 만들 수 없기 때문이다 (TERRAFORM_STRUCTURE.md §3.1-2 참고).
resource "aws_route53_zone" "this" {
  name = var.domain_name
}

# var.domain_name이 이미 등록된 도메인(var.parent_zone_domain)의 서브도메인일 때,
# 그 부모 Zone(같은 계정에 이미 있어야 함)에 NS 위임 레코드를 자동으로 추가한다 -
# 콘솔에서 수동으로 안 해도 apply 한 번으로 델리게이션까지 끝나게 하기 위함.
# parent_zone_domain이 null이면(순수 apex 도메인이거나 위임을 여기서 관리하지
# 않을 때) 이 리소스는 아예 안 만든다. dev/00-base, prod/00-base가 공통으로
# 쓰는 로직이라 각 환경 main.tf에 복붙하는 대신 이 모듈에 한 곳만 둔다.
data "aws_route53_zone" "parent" {
  count        = var.parent_zone_domain != null ? 1 : 0
  name         = var.parent_zone_domain
  private_zone = false
}

resource "aws_route53_record" "delegation" {
  count   = var.parent_zone_domain != null ? 1 : 0
  zone_id = data.aws_route53_zone.parent[0].zone_id
  name    = var.domain_name
  type    = "NS"
  ttl     = 300 # 초기 구축/테스트 단계라 변경 시 빠르게 전파·롤백되도록 짧게 유지 (기본 NS TTL 172800은 안정화된 뒤 고려)
  records = aws_route53_zone.this.name_servers
}
