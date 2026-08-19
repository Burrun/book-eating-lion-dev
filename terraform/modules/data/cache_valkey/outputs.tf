output "valkey_primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "valkey_reader_endpoint" {
  value = aws_elasticache_replication_group.this.reader_endpoint_address
}

output "security_group_id" {
  value = aws_security_group.valkey.id
}
