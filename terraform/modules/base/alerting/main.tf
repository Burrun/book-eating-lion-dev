# Topic만 여기서 만든다. 개별 CloudWatch 알람(Aurora 커넥션, Valkey 메모리, Pod CPU 등)은
# 그 리소스를 실제로 만드는 모듈이 각자 만들고 이 Topic ARN을 SSM으로 받아서 붙인다
# (TERRAFORM_STRUCTURE.md §3.1-7 참고).
resource "aws_sns_topic" "this" {
  name = var.name
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.this.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
