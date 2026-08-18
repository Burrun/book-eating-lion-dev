# 🏛️ Terraform Layered State Architecture Specification

> **Project:** 책 먹는 사자 (Book Eating Lion) MSA E-Commerce Platform  
> **Target Cloud:** AWS (ap-northeast-2, Seoul)  
> **IaC Tool:** Terraform v1.8+ / OpenTofu  
> **Author:** Infrastructure Team

---

## 1. 아키텍처 개요 및 상태 격리 원칙

본 인프라는 변경 주기(Velocity), 장애 파괴 반경(Blast Radius), 데이터 유실 위험도(Risk Level)를 기준으로 테라폼 상태 파일(`terraform.tfstate`)을 4단계로 물리 격리합니다.


```

[ 00-base ] ──(네트워크/보안 불변 계층)──► S3 Key: environments/{env}/00-base.tfstate
│ (VPC ID, Subnets, ACM ARN)
▼
[ 01-data ] ──(영속성 DB & 캐시 계층)────► S3 Key: environments/{env}/01-data.tfstate
│ (DB Endpoints, Redis Host)
▼
[ 02-compute ] ──(K8s 클러스터 런타임)──► S3 Key: environments/{env}/02-compute.tfstate
│ (OIDC ARN, Cluster Name)
▼
[ 03-ingress ] ──(라우팅 & ALB 제어 계층)─► S3 Key: environments/{env}/03-ingress.tfstate

```

### 계층별 위험도 및 변경 주기 매트릭스

| 계층 (Layer) | 변경 주기 | 위험 등급 | 관리 대상 리소스 | 상태 격리 목적 |
| :--- | :---: | :---: | :--- | :--- |
| **`00-base`** | 극저 (연 1~2회) | **Critical** | VPC, Multi-AZ Subnets, IGW/NAT, Route 53, ACM, WAF, ECR | 네트워크 토대 봉인 (파괴 위험 차단) |
| **`01-data`** | 저 (분기 1회) | **Critical** | Aurora PostgreSQL (3AZ), RDS Proxy, ElastiCache Redis, Cognito | 영속 데이터 유실 방지 및 독립 보존 |
| **`02-compute`**| 중 (월 1~2회) | **High** | EKS Control Plane, OIDC, IRSA Base, Karpenter Controller | K8s 버전 업그레이드 및 노드 사양 독립 제어 |
| **`03-ingress`**| 고 (수시) | **Medium** | AWS Load Balancer Controller, Target Group, ALB Listeners | API 라우트 변경 시 초고속 Plan/Apply |

---

## 2. 전체 디렉터리 및 파일 상세 구조

```text
terraform/
├── modules/                                      # [재사용 모듈 원형] 환경 독립적 HCL
│   ├── base/
│   │   ├── vpc/                                  # VPC, 서브넷(Public/App/Data), NAT GW, 라우팅 테이블
│   │   ├── edge_security/                        # AWS WAF v2, CloudFront 배포, Route 53 호스팅 영역, ACM 인증서
│   │   ├── storage/                              # S3 (React 프론트엔드 정적 호스팅, 도서 미디어 에셋 버킷)
│   │   └── container_reg/                        # Amazon ECR 레포지토리 4종 (catalog, order, member, ai)
│   ├── data/
│   │   ├── aurora_pg/                            # Aurora PostgreSQL Serverless v2/Provisioned (3AZ Multi-AZ)
│   │   ├── rds_proxy/                            # Lambda/K8s 커넥션 풀링용 AWS RDS Proxy 및 IAM 인증
│   │   ├── cache_redis/                          # ElastiCache for Redis (1 Primary + 1 Replica, Multi-AZ)
│   │   └── auth/                                 # AWS Cognito User Pool, Resource Server, App Client
│   ├── compute/
│   │   ├── eks_cluster/                          # EKS v1.30+ Control Plane, Managed NodeGroup(시스템용), OIDC Provider
│   │   ├── karpenter/                            # Karpenter Controller용 IAM/SQS, NodePool, EC2NodeClass 매니페스트
│   │   └── ingress_alb/                          # AWS Load Balancer Controller용 IRSA Role 및 파라미터
│   └── dev_tools/
│       └── ec2_postgres/                         # Dev 환경 비용 절감용 단일 EC2 PostgreSQL 인스턴스
│
└── environments/                                 # [환경별 실행 계층] 실제 프로비저닝 엔트리포인트
    ├── dev/                                      # 개발(Dev/Staging) 환경
    │   ├── 00-base/
    │   ├── 01-data/
    │   ├── 02-compute/
    │   └── 03-ingress/
    └── prod/                                     # 운영(Production) 환경
        ├── 00-base/
        │   ├── backend.tf                        # S3 tfstate Key: prod/00-base.tfstate
        │   ├── provider.tf                       # AWS Provider 버전 및 기본 태그 선언
        │   ├── main.tf                           # modules/base/* 호출 및 파라미터 전달
        │   ├── variables.tf                      # VPC CIDR, 서브넷 대역, 도메인 변수 선언
        │   ├── outputs.tf                        # vpc_id, subnet_ids, acm_arn 내보내기 (SSM/RemoteState)
        │   └── terraform.tfvars                  # 운영 VPC CIDR ("10.0.0.0/16") 등 실제 값
        ├── 01-data/
        │   ├── backend.tf                        # S3 tfstate Key: prod/01-data.tfstate
        │   ├── provider.tf
        │   ├── main.tf                           # 00-base 참조 + modules/data/* 호출
        │   ├── variables.tf
        │   ├── outputs.tf                        # db_endpoint, rds_proxy_endpoint, redis_endpoint
        │   └── terraform.tfvars
        ├── 02-compute/
        │   ├── backend.tf                        # S3 tfstate Key: prod/02-compute.tfstate
        │   ├── provider.tf
        │   ├── main.tf                           # 00-base 참조 + modules/compute/* 호출
        │   ├── variables.tf
        │   ├── outputs.tf                        # cluster_name, cluster_endpoint, oidc_arn
        │   └── terraform.tfvars
        └── 03-ingress/
            ├── backend.tf                        # S3 tfstate Key: prod/03-ingress.tfstate
            ├── provider.tf                       # AWS + Helm + Kubernetes Provider 설정
            ├── main.tf                           # 00-base/02-compute 참조 + ALB Controller 배포
            ├── variables.tf
            ├── outputs.tf                        # alb_dns_name, alb_arn
            └── terraform.tfvars

```

---

## 3. 모듈별 상세 규격 및 I/O 명세

### 3.1 Base Modules (`modules/base/`)

#### 1) `vpc`

* **대상 리소스:** `aws_vpc`, `aws_subnet` (Public 2, Private App 2, Private Data 2), `aws_internet_gateway`, `aws_nat_gateway` (Multi-AZ 2개), `aws_route_table`, `aws_route_table_association`
* **필수 입력(Inputs):** `vpc_cidr`, `availability_zones` (`["ap-northeast-2a", "ap-northeast-2c"]`), `public_subnet_cidrs`, `app_subnet_cidrs`, `data_subnet_cidrs`
* **출력값(Outputs):** `vpc_id`, `public_subnet_ids`, `app_subnet_ids`, `data_subnet_ids`

#### 2) `edge_security`

* **대상 리소스:** `aws_wafv2_web_acl` (AWSManagedRulesCommonRuleSet, SQLi 방어), `aws_cloudfront_distribution`, `aws_route53_zone`, `aws_route53_record`, `aws_acm_certificate`
* **필수 입력(Inputs):** `domain_name`, `alb_dns_name`, `s3_bucket_domain_name`
* **출력값(Outputs):** `acm_certificate_arn`, `cloudfront_distribution_id`, `route53_zone_id`

#### 3) `container_reg`

* **대상 리소스:** `aws_ecr_repository` (`catalog`, `order`, `member`, `ai`), `aws_ecr_lifecycle_policy` (최근 30개 태그 유지)
* **필수 입력(Inputs):** `service_names` (`list(string)`)
* **출력값(Outputs):** `repository_urls` (`map(string)`)

---

### 3.2 Data Modules (`modules/data/`)

#### 1) `aurora_pg`

* **대상 리소스:** `aws_rds_cluster`, `aws_rds_cluster_instance` (Serverless v2 0.5~8 ACU 또는 Provisioned Multi-AZ), `aws_db_subnet_group`, `aws_security_group`
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `database_name` (`bookdb`), `master_username`
* **출력값(Outputs):** `cluster_endpoint` (Writer), `reader_endpoint` (Reader), `cluster_security_group_id`

#### 2) `cache_redis`

* **대상 리소스:** `aws_elasticache_replication_group` (Multi-AZ, `redis7`, `noeviction` 파라미터 그룹 적용), `aws_elasticache_subnet_group`
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `node_type` (`cache.t4g.medium`)
* **출력값(Outputs):** `redis_primary_endpoint`, `redis_reader_endpoint`

#### 3) `auth`

* **대상 리소스:** `aws_cognito_user_pool`, `aws_cognito_user_pool_client` (SRP/PASSWORD Auth), `aws_cognito_user_pool_domain`
* **필수 입력(Inputs):** `user_pool_name`, `custom_domain_name`
* **출력값(Outputs):** `user_pool_id`, `user_pool_arn`, `user_pool_client_id`

---

### 3.3 Compute Modules (`modules/compute/`)

#### 1) `eks_cluster`

* **대상 리소스:** `aws_eks_cluster` (v1.30+), `aws_eks_node_group` (CoreDNS/Karpenter 기동용 t4g.medium 2노드), `aws_iam_openid_connect_provider`, `aws_eks_addon` (vpc-cni, kube-proxy, coredns)
* **필수 입력(Inputs):** `vpc_id`, `app_subnet_ids`, `cluster_name`, `cluster_version`
* **출력값(Outputs):** `cluster_name`, `cluster_endpoint`, `cluster_certificate_authority_data`, `oidc_provider_arn`, `oidc_provider_url`

#### 2) `karpenter`

* **대상 리소스:** `aws_iam_role` (Karpenter Controller & Node IRSA), `aws_sqs_queue` (Spot Interruption Queue), `aws_cloudwatch_event_rule` (EC2 State Change 알림)
* **필수 입력(Inputs):** `cluster_name`, `oidc_provider_arn`, `oidc_provider_url`
* **출력값(Outputs):** `karpenter_node_instance_profile_name`, `karpenter_irsa_arn`

---

## 4. 계층 간 데이터 연동 표준 (Decoupling Strategy)

하위 계층이 상위 계층의 리소스(VPC ID, Subnet ID 등)를 참조할 때 **AWS Systems Manager (SSM) Parameter Store**를 활용하여 State 파일 간 결합도를 완전히 제거합니다.

```
[00-base Apply] ────► aws_ssm_parameter.vpc_id 등록 ("/prod/network/vpc_id")
                             │
[01-data Plan]  ◄──── data.aws_ssm_parameter.vpc_id 조회

```

### HCL 구현 예시

**상위 계층 (`environments/prod/00-base/outputs.tf`):**

```hcl
resource "aws_ssm_parameter" "vpc_id" {
  name  = "/${var.environment}/network/vpc_id"
  type  = "String"
  value = module.vpc.vpc_id
}

resource "aws_ssm_parameter" "data_subnets" {
  name  = "/${var.environment}/network/data_subnet_ids"
  type  = "StringList"
  value = join(",", module.vpc.data_subnet_ids)
}

```

**하위 계층 (`environments/prod/01-data/main.tf`):**

```hcl
data "aws_ssm_parameter" "vpc_id" {
  name = "/${var.environment}/network/vpc_id"
}

data "aws_ssm_parameter" "data_subnets" {
  name = "/${var.environment}/network/data_subnet_ids"
}

module "aurora" {
  source          = "../../../modules/data/aurora_pg"
  vpc_id          = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids = split(",", data.aws_ssm_parameter.data_subnets.value)
}

```

---

## 5. 운영 배포 런북 (Deployment Runbook)

### 5.1 최초 프로비저닝 순서

```bash
# 1. Base 계층 배포 (VPC, 서브넷, ECR, Route53, ACM)
cd terraform/environments/prod/00-base
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 2. Data 계층 배포 (Aurora DB, Redis, Cognito)
cd ../01-data
terraform init && terraform apply -auto-approve

# 3. Compute 계층 배포 (EKS 클러스터, Karpenter IAM)
cd ../02-compute
terraform init && terraform apply -auto-approve

# 4. Ingress 계층 배포 (AWS Load Balancer Controller)
cd ../03-ingress
terraform init && terraform apply -auto-approve

```

### 5.2 자원 정리 및 삭제 순서 (역순 삭제)

```bash
cd terraform/environments/prod/03-ingress && terraform destroy -auto-approve
cd ../02-compute && terraform destroy -auto-approve
cd ../01-data    && terraform destroy -auto-approve
cd ../00-base    && terraform destroy -auto-approve

```

---

## 6. 보안, 태깅 및 백엔드 표준 규격

### 6.1 State 백엔드 표준 템플릿 (`backend.tf`)

```hcl
terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }

  backend "s3" {
    bucket         = "book-eating-lion-tfstate-prod"
    key            = "prod/00-base.tfstate" # 디렉터리별 고유 키 지정
    region         = "ap-northeast-2"
    dynamodb_table = "book-eating-lion-tflock-prod"
    encrypt        = true
  }
}

```

### 6.2 공통 태깅 전략 (Unified Tagging)

`provider.tf`의 `default_tags` 블록을 통해 프로비저닝되는 모든 AWS 리소스에 자동 태깅을 적용합니다:

```hcl
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "book-eating-lion"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "base" # base | data | compute | ingress
    }
  }
}

```

