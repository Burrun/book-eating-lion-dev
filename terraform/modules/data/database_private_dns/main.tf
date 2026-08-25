resource "aws_route53_zone" "database" {
  name = var.zone_name

  vpc {
    vpc_id = var.vpc_id
  }

  tags = {
    Name        = "lion-team3-${var.environment}-db-private-zone"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_route53_record" "writer" {
  zone_id = aws_route53_zone.database.zone_id
  name    = "writer.${var.zone_name}"
  type    = "CNAME"
  ttl     = 60
  records = [trimsuffix(var.writer_target, ".")]
}

resource "aws_route53_record" "reader" {
  zone_id = aws_route53_zone.database.zone_id
  name    = "reader.${var.zone_name}"
  type    = "CNAME"
  ttl     = 60
  records = [trimsuffix(var.reader_target, ".")]
}
