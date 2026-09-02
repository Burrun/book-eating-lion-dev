# 출력 이름은 aurora_pg와 의도적으로 맞춰 뒀다 - 01-data가 두 모듈 중 어느 쪽을
# 부르든 rds_proxy/database_private_dns/SSM 배선을 그대로 재사용할 수 있게 하기 위함.

output "endpoint" {
  description = "Writer 엔드포인트 (호스트만, 포트 제외)"
  value       = aws_db_instance.this.address
}

# 리드 리플리카 0번의 주소. read_replica_count = 0 으로 끄면 Writer와 같은 곳을
# 가리킨다(그 경우 k8s의 db-reader-service 분리는 이름만 남는다).
output "reader_endpoint" {
  description = "읽기 엔드포인트. 리플리카가 있으면 그 주소, 없으면 Writer와 동일"
  value       = var.read_replica_count > 0 ? aws_db_instance.replica[0].address : aws_db_instance.this.address
}

output "replica_identifiers" {
  value = aws_db_instance.replica[*].identifier
}

output "security_group_id" {
  value = aws_security_group.this.id
}

output "instance_identifier" {
  value = aws_db_instance.this.identifier
}

output "master_user_secret_arn" {
  description = "AWS가 자동 발급한 Secrets Manager 시크릿 ARN"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "port" {
  value = aws_db_instance.this.port
}
