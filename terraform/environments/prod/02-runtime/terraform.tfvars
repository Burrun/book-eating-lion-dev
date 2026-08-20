environment     = "prod"
aws_region      = "ap-northeast-2"
cluster_version = "1.34" # 1.30은 표준+연장 지원(26개월) 다 지나서 신규 생성 불가(2026-08-20 확인) - 표준 지원 중 가장 오래된 버전으로 교체

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

# 클러스터를 실제로 만든 사람(b-student-02)은 access_config의
# bootstrap_cluster_creator_admin_permissions=true로 AWS가 자동으로 Access
# Entry를 만들어줘서 여기 또 넣으면 EKS API가 ResourceInUseException(409)로
# 거부한다(2026-08-20 dev에서 실제로 겪음). 여기(admin_principal_arns)는
# 클러스터를 "만들지 않은" 다른 팀원/역할한테 나중에 kubectl 권한을 추가로
# 줄 때만 쓸 것.
admin_principal_arns = []
