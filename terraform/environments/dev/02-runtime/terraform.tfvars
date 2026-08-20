environment     = "dev"
aws_region      = "ap-northeast-2"
cluster_version = "1.30"

# TODO: 00-base와 동일한 서브도메인으로 교체
domain_name = "dev.book-eating-lion.com"

bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  "arn:aws:bedrock:us-east-1:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/apac.amazon.nova-micro-v1:0",
]
