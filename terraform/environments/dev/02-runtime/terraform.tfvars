environment     = "dev"
aws_region      = "ap-northeast-2"
cluster_version = "1.34" # 1.30은 표준+연장 지원(26개월) 다 지나서 신규 생성 불가(2026-08-20 확인) - 표준 지원 중 가장 오래된 버전으로 교체

# 00-base와 동일한 값이어야 함
domain_name = "dev.ajttk.com"

bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  "arn:aws:bedrock:us-east-1:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/apac.amazon.nova-micro-v1:0",
]
