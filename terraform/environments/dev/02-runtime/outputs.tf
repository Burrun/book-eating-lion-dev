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
  value = var.enable_edge_routing ? module.edge_routing[0].cloudfront_distribution_id : null
}

output "public_domain_url" {
  value = var.enable_edge_routing ? module.edge_routing[0].public_domain_url : null
}

output "ai_service_irsa_arn" {
  value = module.ai_service_iam.ai_service_irsa_arn
}

output "member_service_irsa_arn" {
  value = module.member_service_iam.member_service_irsa_arn
}
