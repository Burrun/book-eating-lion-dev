output "zone_id" {
  value = aws_route53_zone.database.zone_id
}

output "writer_fqdn" {
  value = trimsuffix(aws_route53_record.writer.fqdn, ".")
}

output "reader_fqdn" {
  value = trimsuffix(aws_route53_record.reader.fqdn, ".")
}
