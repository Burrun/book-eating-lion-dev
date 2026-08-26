environment     = "integrated"
aws_region      = "ap-northeast-2"
cluster_version = "1.34"

# 00-base와 동일한 값이어야 함
domain_name = "book.ajttk.com"

bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  "arn:aws:bedrock:us-east-1:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/apac.amazon.nova-micro-v1:0",
  "arn:aws:bedrock:*::foundation-model/amazon.nova-micro-v1:0",
]

# S3 Vectors는 아직 미생성 - dev와 마찬가지로 AWS CLI로 수동 생성 후 채울 것.
recommendation_index_arn     = null
purchased_book_rag_index_arn = null

admin_principal_arns = []
