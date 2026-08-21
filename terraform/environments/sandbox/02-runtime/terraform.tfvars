environment      = "sandbox"
base_environment = "dev" # dev의 00-base/01-data(VPC/DB/Redis 등)를 그대로 재사용
aws_region       = "ap-northeast-2"
cluster_version  = "1.34"

bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  "arn:aws:bedrock:us-east-1:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/apac.amazon.nova-micro-v1:0",
]

# dev가 §7.5 가이드대로 만든 버킷/인덱스를 그대로 재사용한다 (읽기/쓰기 IAM 권한만
# 부여하는 것이라 여러 IAM Role이 같은 인덱스를 공유해도 안전함).
recommendation_index_arn     = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/recommendation-books-v1"
purchased_book_rag_index_arn = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/wiki-v1"

# 클러스터를 실제로 만드는 사람은 access_config의
# bootstrap_cluster_creator_admin_permissions=true로 자동으로 admin 권한을 받는다
# (dev/02-runtime과 동일한 이유로 비워둠 - 인프라구성명세.md §7.3 참고).
admin_principal_arns = []
