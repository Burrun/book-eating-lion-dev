environment     = "integrated"
aws_region      = "ap-northeast-2"
cluster_version = "1.34"

# 00-base와 동일한 값이어야 함
domain_name = "book.ajttk.com"

bedrock_model_arns = [
  "arn:aws:bedrock:ap-northeast-2::foundation-model/amazon.titan-embed-text-v2:0",
  # global. 프로필은 "어디서 호출했는가"의 리전 ARN으로 권한이 평가된다. ai-service가
  # ap-northeast-2에서 도는데 여기를 us-east-1로 두고 있어 사자 답변이 전부 500이었다
  # (2026-08-31 dev 실배포: bedrock:InvokeModel on
  #  arn:aws:bedrock:ap-northeast-2:...:inference-profile/global.anthropic.claude-haiku-4-5 거부).
  # 임베딩(titan)은 리전이 맞아 통과하므로 검색까지는 되고 LLM 호출만 막힌다.
  "arn:aws:bedrock:ap-northeast-2:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
  # 아래 nova-micro와 같은 이유 — 프로필 ARN만으론 부족하고 라우팅 대상 모델도 허용해야 한다.
  # global.은 라우팅 리전이 고정되지 않아 리전을 비워 둔다.
  "arn:aws:bedrock:*::foundation-model/anthropic.claude-haiku-4-5-20251001-v1:0",
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

admin_principal_arns = [
  "arn:aws:iam::061039804626:user/b-student-02",
]
# 현재 dev.ajttk.com은 integrated CloudFront가 담당한다. 운영 상태와 코드 기본값을
# 일치시켜 옵션 없는 apply가 dev edge 리소스를 제거하지 않게 한다. split dev로
# 되돌릴 때만 별도 PR에서 false로 바꾸고 의도적인 컷백 절차를 수행한다.
enable_dev_cutover = true
