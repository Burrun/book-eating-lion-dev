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
  # apac. 크로스리전 프로필은 호출마다 APAC 내 리전으로 라우팅된다(dev에서 ap-southeast-2로
  # 라우팅되어 AccessDenied 발생 - 2026-08-25). inference-profile ARN만으론 부족하고
  # 라우팅 대상 리전들의 foundation-model ARN도 같이 허용해야 한다.
  "arn:aws:bedrock:*::foundation-model/amazon.nova-micro-v1:0",
]

# 클러스터를 실제로 만든 사람(b-student-02)은 access_config의
# bootstrap_cluster_creator_admin_permissions=true로 AWS가 자동으로 Access
# Entry를 만들어줘서 여기 또 넣으면 EKS API가 ResourceInUseException(409)로
# 거부한다(2026-08-20 dev에서 실제로 겪음). 여기(admin_principal_arns)는
# 클러스터를 "만들지 않은" 다른 팀원/역할한테 나중에 kubectl 권한을 추가로
# 줄 때만 쓸 것.
admin_principal_arns = []
