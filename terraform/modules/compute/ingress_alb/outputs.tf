# 이름은 alb_dns_name이지만 실제로는 NLB 호스트명이다 - edge_routing이 이 값을
# CloudFront 오리진으로 그대로 쓰므로 인터페이스(출력 이름)를 안 바꿨다.
output "alb_dns_name" {
  value = data.kubernetes_service.ingress_nginx.status[0].load_balancer[0].ingress[0].hostname
}

output "alb_controller_role_arn" {
  value = aws_iam_role.alb_controller.arn
}
