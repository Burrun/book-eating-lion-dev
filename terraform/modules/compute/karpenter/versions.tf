# 클러스터가 없는 상태에서 helm/kubernetes provider가 인증에 실패할 수 있으므로,
# 최초 apply는 `terraform apply -target=module.eks_cluster`로 클러스터부터 만든 뒤
# 나머지를 apply하는 2단계 절차를 쓴다 (TERRAFORM_STRUCTURE.md §5.1 참고).
terraform {
  required_providers {
    helm = {
      source                = "hashicorp/helm"
      configuration_aliases = [helm]
    }
    kubernetes = {
      source                = "hashicorp/kubernetes"
      configuration_aliases = [kubernetes]
    }
  }
}
