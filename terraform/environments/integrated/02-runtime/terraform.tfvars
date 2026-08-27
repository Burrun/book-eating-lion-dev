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

# dev의 기존 벡터 인덱스를 그대로 재사용한다 (dev 워크로드 임시 이전이라 별도
# 인덱스 새로 만들 필요 없음 - dev/02-runtime/terraform.tfvars와 동일 값).
# prod 실제 서비스를 이 클러스터에 올릴 땐 prod 전용 인덱스로 바꿔야 함.
dev_recommendation_index_arn     = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/recommendation-books-v1"
dev_purchased_book_rag_index_arn = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-dev-vectors/index/wiki-v1"

# prod는 dev 인덱스를 절대 공유하지 않는다. S3 Vectors는 현재 Terraform provider가
# 직접 생성하지 못하므로 인프라 구성 가이드대로 이 이름으로 먼저 생성해야 한다.
prod_recommendation_index_arn     = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-prod-vectors/index/recommendation-books-v1"
prod_purchased_book_rag_index_arn = "arn:aws:s3vectors:ap-northeast-2:061039804626:bucket/lion-team3-prod-vectors/index/wiki-v1"

admin_principal_arns = []
# split dev가 같은 dev.ajttk.com 레코드를 소유할 수 있으므로 기본은 꺼 둔다.
# 명시적인 컷오버 절차에서만 true로 바꾼다.
enable_dev_cutover = false
