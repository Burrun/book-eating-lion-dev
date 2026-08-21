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

# 인프라구성명세.md §7.5대로 AWS CLI로 수동 생성한 값 (lion-team3-dev-vectors 버킷).
# 인덱스명은 앱이 실제로 부르는 이름(application.yml의 index-name/recommendation-index-name
# 기본값, k8s/ai/configmap.yaml의 AI_VECTOR_INDEX/AI_RECOMMENDATION_VECTOR_INDEX)과 반드시
# 같아야 한다 - 예전엔 각각 recommendation/purchased-book-rag로 만들어져 있어서 앱이 호출하는
# wiki-v1/recommendation-books-v1과 이름이 어긋났고, GetIndex가 없는 인덱스를 찾다가 크래시났다.
# (당시엔 두 인덱스 다 벡터 0개였어서 재인제스트 없이 이름만 맞는 새 인덱스로 교체했다.)
recommendation_index_arn     = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/recommendation-books-v1"
purchased_book_rag_index_arn = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/wiki-v1"

# 클러스터를 실제로 만든 사람(b-student-02)은 access_config의
# bootstrap_cluster_creator_admin_permissions=true로 AWS가 자동으로 Access
# Entry를 만들어줘서 여기 또 넣으면 EKS API가 ResourceInUseException(409)로
# 거부한다(2026-08-20 실제로 겪음). 여기(admin_principal_arns)는 클러스터를
# "만들지 않은" 다른 팀원/역할한테 나중에 kubectl 권한을 추가로 줄 때만 쓸 것.
admin_principal_arns = []
