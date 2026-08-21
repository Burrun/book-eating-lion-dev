output "cluster_name" {
  value = module.eks_cluster.cluster_name
}

output "cluster_endpoint" {
  value = module.eks_cluster.cluster_endpoint
}

output "alb_dns_name" {
  value = module.ingress_alb.alb_dns_name
}

output "ai_service_irsa_arn" {
  value = module.ai_service_iam.ai_service_irsa_arn
}
