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

output "ai_service_irsa_arn" {
  value = module.ai_service_iam.ai_service_irsa_arn
}

output "member_service_irsa_arn" {
  value = module.member_service_iam.member_service_irsa_arn
}
