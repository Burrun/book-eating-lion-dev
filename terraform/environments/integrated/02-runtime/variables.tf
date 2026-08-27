variable "environment" {
  type    = string
  default = "integrated"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "cluster_version" {
  type    = string
  default = "1.34"
}

variable "domain_name" {
  description = "이 클러스터에 새로 연결할 도메인 (prod 도메인). dev.ajttk.com 컷오버는 여기서 안 한다 - main.tf 상단 주석 참고"
  type        = string
}

variable "dev_domain_name" {
  description = "dev 임시 컷오버용 도메인"
  type        = string
  default     = "dev.ajttk.com"
}

variable "enable_dev_cutover" {
  description = <<-EOT
    true로 바꾸면 dev.ajttk.com을 이 integrated 클러스터로 컷오버하는
    module.edge_routing_dev가 생성된다.

    ⚠️ 켜기 전에 반드시 dev/02-runtime 쪽의 edge_routing 모듈을 먼저
    destroy(또는 주석 처리 후 apply)할 것. 안 그러면 두 tfstate가 같은
    dev.ajttk.com Route53 레코드를 각자 "내 것"이라 믿고 있는 상태가 되고,
    나중에 dev/02-runtime을 apply하면 drift로 감지해서 이 레코드를
    dev 쪽 ALB로 되돌려버린다 (컷오버가 조용히 원복됨).
  EOT
  type        = bool
  default     = false
}

variable "bedrock_model_arns" {
  type = list(string)
}

variable "admin_principal_arns" {
  type    = list(string)
  default = []
}

variable "dev_namespace" {
  description = "이 클러스터에서 dev 워크로드가 배포되는 k8s 네임스페이스. k8s/base/01-namespace.yaml + CI의 K8S_NAMESPACE와 반드시 같은 값이어야 한다."
  type        = string
  default     = "dev"
}

variable "prod_namespace" {
  description = "이 클러스터에서 prod 워크로드가 배포되는 k8s 네임스페이스. k8s/base/01-namespace.yaml + CI의 K8S_NAMESPACE와 반드시 같은 값이어야 한다."
  type        = string
  default     = "prod"
}

variable "dev_recommendation_index_arn" {
  type    = string
  default = null
}

variable "dev_purchased_book_rag_index_arn" {
  type    = string
  default = null
}

variable "prod_recommendation_index_arn" {
  type    = string
  default = null
}

variable "prod_purchased_book_rag_index_arn" {
  type    = string
  default = null
}

variable "trigger_github_actions" {
  description = <<-EOT
    true면 apply할 때마다 로컬 gh CLI로 GitHub Actions 워크플로를
    자동 실행한다 (gh workflow run). 로컬 PC에 gh 설치 + `gh auth login`
    필요. terraform apply를 실행하는 사람 PC에서 실행되는 것이니,
    CI 서버가 아니라 지금처럼 로컬 cmd에서 apply하는 워크플로에만 맞다.
  EOT
  type        = bool
  default     = false
}

variable "github_actions_workflow_file" {
  description = "trigger_github_actions=true일 때 실행할 워크플로 파일명"
  type        = string
  default     = "main-cd.yml"
}

variable "github_org" {
  type    = string
  default = "Burrun"
}

variable "github_repo" {
  type    = string
  default = "book-eating-lion-dev"
}
