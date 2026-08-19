output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_certificate_authority_data" {
  value = aws_eks_cluster.this.certificate_authority[0].data
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.eks.arn
}

output "oidc_provider_url" {
  value = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

output "node_role_arn" {
  description = "karpenter가 만드는 EC2NodeClass의 role도 같은 패턴을 따를 때 참고용"
  value       = aws_iam_role.node.arn
}

output "cluster_security_group_id" {
  description = "EKS가 자동 생성하는 공용 SG - 컨트롤 플레인과 이미 통신 허용돼 있어 karpenter가 띄우는 노드에도 그대로 쓴다"
  value       = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}
