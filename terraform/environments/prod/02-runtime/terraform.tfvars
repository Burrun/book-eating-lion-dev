environment     = "prod"
aws_region      = "ap-northeast-2"
cluster_version = "1.30"

# 00-base와 동일한 값이어야 함
domain_name = "book.ajttk.com"

# k8s-명세.md ConfigMap 기준 (AI_LLM_MODEL_RAG/AI_LLM_MODEL_BOT) + Titan Embeddings V2.
# global./apac. 접두사가 붙은 모델은 Cross-Region Inference Profile이라 ARN 형태가
# foundation-model이 아니라 inference-profile이다 - 실제 배포 전 콘솔에서 정확한 ARN 재확인.
bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  "arn:aws:bedrock:us-east-1:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/apac.amazon.nova-micro-v1:0",
]
