output "tfstate_bucket_names" {
  value = { for env, b in aws_s3_bucket.tfstate : env => b.id }
}

output "tflock_table_names" {
  value = { for env, t in aws_dynamodb_table.tflock : env => t.name }
}
