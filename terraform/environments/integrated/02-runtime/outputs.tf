output "cluster_name" {
  value = module.eks_cluster.cluster_name
}

output "cluster_endpoint" {
  value = module.eks_cluster.cluster_endpoint
}

output "alb_dns_name" {
  value = module.ingress_alb.alb_dns_name
}

output "cloudfront_distribution_id" {
  value = module.edge_routing.cloudfront_distribution_id
}

output "public_domain_url" {
  value = module.edge_routing.public_domain_url
}

output "public_domain_url_dev" {
  description = "enable_dev_cutover=true일 때만 값 존재"
  value       = var.enable_dev_cutover ? module.edge_routing_dev[0].public_domain_url : null
}

output "ai_service_irsa_arn_prod" {
  value = module.ai_service_iam_prod.ai_service_irsa_arn
}

output "ai_service_irsa_arn_dev" {
  value = module.ai_service_iam_dev.ai_service_irsa_arn
}

output "member_service_irsa_arn_prod" {
  value = module.member_service_iam_prod.member_service_irsa_arn
}

output "member_service_irsa_arn_dev" {
  value = module.member_service_iam_dev.member_service_irsa_arn
}
