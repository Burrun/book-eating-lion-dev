output "valkey_primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "valkey_reader_endpoint" {
  # replica_count=0(리플리카 없음)이면 AWS가 reader_endpoint_address를 빈 문자열로 둔다.
  # 빈 문자열은 aws_ssm_parameter(type=String)에 쓸 수 없어 apply가 실패하므로,
  # 리플리카가 없을 땐 primary_endpoint_address로 대체한다(어차피 리플리카가 없으면
  # 읽기도 primary가 처리한다).
  value = (
    aws_elasticache_replication_group.this.reader_endpoint_address != "" ?
    aws_elasticache_replication_group.this.reader_endpoint_address :
    aws_elasticache_replication_group.this.primary_endpoint_address
  )
}

output "security_group_id" {
  value = aws_security_group.valkey.id
}
