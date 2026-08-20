# Zone만 만든다. 도메인이 실제로 무언가(CloudFront)를 가리키는 레코드는
# 02-runtime의 edge_routing이 만든다 — CloudFront가 아직 없는 00-base 시점엔
# 만들 수 없기 때문이다 (TERRAFORM_STRUCTURE.md §3.1-2 참고).
resource "aws_route53_zone" "this" {
  name = var.domain_name
}
