# aurora_pg 출력값 이름과 최대한 맞춤 (TERRAFORM_STRUCTURE.md §6.3).
# 단일 인스턴스라 reader_endpoint도 같은 주소를 가리킨다.
# rds_proxy는 Aurora 전용 기능이라 dev에서는 아예 호출하지 않는다 - 그래서
# 01-data/main.tf가 rds_proxy_endpoint SSM 파라미터에도 이 값을 그대로 쓴다.

output "cluster_endpoint" {
  value = "${aws_instance.this.private_ip}:5432"
}

output "reader_endpoint" {
  value = "${aws_instance.this.private_ip}:5432"
}

output "cluster_identifier" {
  value = aws_instance.this.id
}

output "master_user_secret_arn" {
  value = aws_secretsmanager_secret.master.arn
}

output "cluster_security_group_id" {
  value = aws_security_group.this.id
}
