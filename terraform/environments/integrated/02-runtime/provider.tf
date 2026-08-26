provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "lion"
      Team        = "Team3"
      Owner       = "likelion-cloud6-team3"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "runtime"
    }
  }
}

# 최초 apply 시 클러스터가 없으면 이 provider 설정 자체가 실패할 수 있으므로,
# `terraform apply -target=module.eks_cluster`로 클러스터부터 만드는 2단계
# 절차를 쓴다 (dev/02-runtime과 동일).
data "aws_eks_cluster_auth" "this" {
  name = module.eks_cluster.cluster_name
}

provider "kubernetes" {
  host                   = module.eks_cluster.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks_cluster.cluster_certificate_authority_data)
  token                  = data.aws_eks_cluster_auth.this.token
}

provider "helm" {
  kubernetes {
    host                   = module.eks_cluster.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks_cluster.cluster_certificate_authority_data)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}
