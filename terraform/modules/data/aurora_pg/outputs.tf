output "cluster_endpoint" {
  description = "Writer 엔드포인트"
  value       = aws_rds_cluster.this.endpoint
}

output "reader_endpoint" {
  value = aws_rds_cluster.this.reader_endpoint
}

output "cluster_security_group_id" {
  value = aws_security_group.cluster.id
}

output "cluster_identifier" {
  value = aws_rds_cluster.this.cluster_identifier
}

output "master_user_secret_arn" {
  description = "AWS가 자동 발급한 Secrets Manager 시크릿 ARN"
  value       = aws_rds_cluster.this.master_user_secret[0].secret_arn
}
