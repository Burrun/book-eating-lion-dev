# 🏛️ Terraform Layered State Architecture Specification

> **Project:** 책 먹는 사자 (Book Eating Lion) MSA E-Commerce Platform  
> **Target Cloud:** AWS (ap-northeast-2, Seoul)  
> **IaC Tool:** Terraform v1.8+ / OpenTofu  
> **Author:** Infrastructure Team

---

## 1. 아키텍처 개요 및 상태 격리 원칙

이 프로젝트는 팀 대부분이 테라폼을 처음 다루기 때문에, "최대한 잘게 쪼갠 구조"보다 **관리하기 쉽고 문서화가 잘 되는 구조**를 우선합니다. 그래서 자원을 딱 두 그룹으로만 나눕니다.

* **고정자원 그룹 — 한 번 만들면 거의 바꾸지 않는 토대**
  VPC 대역, DB 엔진, 인증 체계처럼 바뀌는 순간 그 위에 얹힌 모든 것에 영향을 주는 자원. `00-base`, `01-data` 두 계층이 여기 속합니다. 변경 시 팀 전체 확인을 거치고, `terraform.tfvars` 값 자체가 "이 인프라가 어떻게 생겼는지"를 보여주는 문서 역할도 겸합니다.
* **비고정자원 그룹 — 서비스 배포 주기에 맞춰 자주 바뀌는 런타임**
  노드 스펙, 라우팅 규칙처럼 마이크로서비스가 늘어날 때마다 반복해서 apply하는 자원. `02-runtime` 한 계층에 EKS/Karpenter/ALB Controller를 모두 묶습니다.

각 그룹은 서로 다른 `terraform.tfstate`로 물리 격리되어 있어, 비고정자원을 자주 apply해도 고정자원(VPC, DB)의 상태 파일에는 손이 닿지 않습니다.

```

[ 00-base ] ──(네트워크/보안 불변 계층, 고정자원)──► S3 Key: environments/{env}/00-base.tfstate
│ (VPC ID, Subnets, ACM ARN)
▼
[ 01-data ] ──(영속성 DB & 캐시 계층, 고정자원)────► S3 Key: environments/{env}/01-data.tfstate
│ (DB Endpoints, Valkey Host)
▼
[ 02-runtime ] ─(K8s 클러스터 + 라우팅, 비고정자원)─► S3 Key: environments/{env}/02-runtime.tfstate

```

> **왜 3계층인가 (compute와 ingress를 합친 이유)**
> 원래는 EKS(`02-compute`)와 ALB 라우팅(`03-ingress`)을 별도 state로 나누는 안도 검토했습니다. 그렇게 하면 라우팅 규칙 하나 바꿀 때 EKS 리소스 전체를 refresh하지 않아도 되어 plan이 더 빠르고, state lock 경합도 줄고, 실수로 클러스터 쪽 diff까지 apply하는 사고도 방지됩니다.
> 다만 이 프로젝트는 처음 배우는 팀이 다루는 구조라, state 파일·backend.tf·SSM 참조 체인이 하나라도 줄어드는 게 더 큰 이득이라 판단해 **`02-runtime`으로 합쳤습니다.** 나중에 팀원이 늘고 동시 작업(노드 스펙 변경 vs 라우트 추가)이 잦아져 위 세 가지 제약이 실제로 발목을 잡으면, 그때 `02-runtime`을 `02-compute`/`03-ingress`로 다시 쪼개면 됩니다. (모듈은 이미 `eks_cluster`/`karpenter`/`ingress_alb`로 나뉘어 있어 분리 자체는 어렵지 않습니다.)

### 계층별 위험도 및 변경 주기 매트릭스

| 계층 (Layer) | 자원 유형 | 변경 주기 | 위험 등급 | 관리 대상 리소스 | 상태 격리 목적 |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **`00-base`** | 고정 | 극저 (연 1~2회) | **Critical** | VPC, Multi-AZ Subnets, IGW/NAT, Route 53 Hosted Zone, ACM 인증서, WAF WebACL, ECR, S3 | 네트워크 토대 봉인 (파괴 위험 차단) |
| **`01-data`** | 고정 | 저 (분기 1회) | **Critical** | Aurora PostgreSQL (2AZ Multi-AZ), RDS Proxy, ElastiCache for Valkey 8.2, Cognito | 영속 데이터 유실 방지 및 독립 보존 |
| **`02-runtime`**| 비고정 | 중~고 (주 단위) | **High** | EKS Control Plane, OIDC, Karpenter, ALB Controller, Target Group/Listener, CloudFront 배포, Route 53 ALIAS 레코드 | 서비스 배포 주기에 맞춘 반복 Plan/Apply |

---

## 2. 전체 디렉터리 및 파일 상세 구조

```text
terraform/
├── modules/                                      # [재사용 모듈 원형] 환경 독립적 HCL
│   ├── base/                                      # 모듈 이름은 맡고 있는 역할 그대로 (기능 혼합 금지)
│   │   ├── vpc/                                  # VPC, 서브넷(Public/App/Data), NAT GW, 라우팅 테이블
│   │   ├── dns_zone/                             # Route 53 Hosted Zone
│   │   ├── acm_cert/                             # ACM 인증서 발급 + DNS 검증 레코드
│   │   ├── waf/                                  # AWS WAF v2 WebACL 정의 (AWSManagedRulesCommonRuleSet, SQLi 방어) — 아직 아무 것에도 연결 안 함
│   │   ├── storage/                               # S3 (React 프론트엔드 정적 호스팅, 도서 미디어 에셋 버킷)
│   │   └── container_reg/                        # Amazon ECR 레포지토리 (서비스별 1개씩)
│   ├── data/
│   │   ├── aurora_pg/                            # Aurora PostgreSQL Serverless v2/Provisioned (2AZ Multi-AZ: Writer/Reader)
│   │   ├── rds_proxy/                            # K8s 워크로드 커넥션 풀링용 AWS RDS Proxy 및 IAM 인증
│   │   ├── cache_valkey/                         # ElastiCache for Valkey 8.2 (1 Primary + 1 Replica, Multi-AZ)
│   │   └── auth/                                 # AWS Cognito User Pool, Resource Server, App Client
│   ├── compute/                                  # 02-runtime 계층이 사용하는 모듈 (K8s 클러스터 + 라우팅 + 공개 진입점)
│   │   ├── eks_cluster/                          # EKS v1.30+ Control Plane, Managed NodeGroup(시스템용), OIDC Provider
│   │   ├── karpenter/                            # Karpenter Controller용 IAM/SQS, NodePool, EC2NodeClass 매니페스트
│   │   ├── ingress_alb/                          # AWS Load Balancer Controller, Target Group, ALB Listener
│   │   └── edge_routing/                         # CloudFront 배포(S3+ALB 오리진, WAF 연결), 도메인→CloudFront Route 53 ALIAS 레코드
│   └── dev_tools/
│       └── ec2_postgres/                         # Dev 환경 전용, Aurora 대신 쓰는 비용 절감형 단일 EC2 PostgreSQL
│
└── environments/                                 # [환경별 실행 계층] 실제 프로비저닝 엔트리포인트
    ├── dev/                                      # 개발(Dev/Staging) 환경
    │   ├── 00-base/
    │   ├── 01-data/                               # aurora_pg 대신 modules/dev_tools/ec2_postgres 호출 (§6.3 참고)
    │   └── 02-runtime/
    └── prod/                                     # 운영(Production) 환경
        ├── 00-base/
        │   ├── backend.tf                        # S3 tfstate Key: prod/00-base.tfstate
        │   ├── provider.tf                       # AWS Provider(ap-northeast-2) 및 기본 태그 선언 + us-east-1 alias (acm_cert/waf가 CloudFront용으로 사용)
        │   ├── main.tf                           # modules/base/* 호출 및 파라미터 전달
        │   ├── variables.tf                      # VPC CIDR, 서브넷 대역, 도메인 변수 선언
        │   ├── outputs.tf                        # vpc_id, subnet_ids, acm_certificate_arn, route53_zone_id, waf_web_acl_arn, frontend_bucket_id/arn/domain_name → SSM 등록
        │   └── terraform.tfvars                  # 운영 VPC CIDR ("10.0.0.0/16") 등 실제 값
        ├── 01-data/
        │   ├── backend.tf                        # S3 tfstate Key: prod/01-data.tfstate
        │   ├── provider.tf
        │   ├── main.tf                           # 00-base SSM 참조 + modules/data/* 호출
        │   ├── variables.tf
        │   ├── outputs.tf                        # db_endpoint, rds_proxy_endpoint, valkey_endpoint → SSM 등록
        │   └── terraform.tfvars
        └── 02-runtime/
            ├── backend.tf                        # S3 tfstate Key: prod/02-runtime.tfstate
            ├── provider.tf                       # AWS + Helm + Kubernetes Provider 설정
            ├── main.tf                           # 00-base/01-data SSM 참조 + modules/compute/* 호출 (eks_cluster → karpenter → ingress_alb → edge_routing 순)
            ├── variables.tf
            ├── outputs.tf                        # cluster_name, cluster_endpoint, alb_dns_name, cloudfront_distribution_id
            └── terraform.tfvars

```

> dev 환경도 파일 구성은 prod와 동일합니다 (`backend.tf`/`provider.tf`/`main.tf`/`variables.tf`/`outputs.tf`/`terraform.tfvars`). 표에는 계층 폴더만 표시했습니다.

---

## 3. 모듈별 상세 규격 및 I/O 명세

### 3.1 Base Modules (`modules/base/`)

#### 1) `vpc`

* **대상 리소스:** `aws_vpc`, `aws_subnet` (Public 2, Private App 2, Private Data 2), `aws_internet_gateway`, `aws_nat_gateway` (Multi-AZ 2개), `aws_route_table`, `aws_route_table_association`
* **필수 입력(Inputs):** `vpc_cidr`, `availability_zones` (`["ap-northeast-2a", "ap-northeast-2c"]`), `public_subnet_cidrs`, `app_subnet_cidrs`, `data_subnet_cidrs`
* **출력값(Outputs):** `vpc_id`, `public_subnet_ids`, `app_subnet_ids`, `data_subnet_ids`

#### 2) `dns_zone`

* **대상 리소스:** `aws_route53_zone`
* **필수 입력(Inputs):** `domain_name`
* **출력값(Outputs):** `route53_zone_id`, `route53_name_servers`
* **참고:** 이 모듈은 Zone만 만듭니다. 실제 도메인이 CloudFront를 가리키는 레코드는 CloudFront가 존재하는 계층(`02-runtime`의 `edge_routing`)에서 만듭니다 — ALB가 아직 없는 `00-base` 시점엔 CloudFront도 만들 수 없기 때문입니다.

#### 3) `acm_cert`

* **대상 리소스:** `aws_acm_certificate`, `aws_acm_certificate_validation`, `aws_route53_record` (DNS 검증용) — **`us-east-1` 리전 provider alias로 생성**
* **필수 입력(Inputs):** `domain_name`, `route53_zone_id` (같은 00-base 내 `dns_zone` 모듈 출력을 바로 참조 — 계층 간 SSM 필요 없음)
* **출력값(Outputs):** `acm_certificate_arn` (CloudFront 전용, us-east-1)
* **주의 (AWS 고정 제약):** CloudFront는 **반드시 `us-east-1`에서 발급된 ACM 인증서만** 붙일 수 있습니다. 프로젝트 리전은 `ap-northeast-2`이므로 `provider.tf`에 `provider "aws" { alias = "us_east_1", region = "us-east-1" }`를 추가하고, 이 모듈만 `providers = { aws = aws.us_east_1 }`로 호출합니다. (§6.1 백엔드 템플릿에 반영)

#### 4) `waf`

* **대상 리소스:** `aws_wafv2_web_acl` (AWSManagedRulesCommonRuleSet, SQLi 방어) — **`us-east-1` 리전 provider alias로 생성**
* **필수 입력(Inputs):** `scope` (`CLOUDFRONT` — 트래픽이 전부 CloudFront를 거치므로 ALB용 `REGIONAL`이 아니라 `CLOUDFRONT` 스코프로 만듭니다)
* **출력값(Outputs):** `waf_web_acl_arn`
* **참고:** `CLOUDFRONT` 스코프 WebACL은 `acm_cert`와 같은 이유로 **`us-east-1`에서만 생성 가능**합니다. WebACL 정의만 여기서 만들고, CloudFront에 실제로 붙이는 건 `02-runtime`의 `edge_routing`이 `aws_cloudfront_distribution.web_acl_id`에 이 ARN을 SSM으로 읽어와 넣는 방식입니다 (REGIONAL 스코프에서 쓰는 `aws_wafv2_web_acl_association` 리소스는 CLOUDFRONT 스코프엔 쓰지 않음).

#### 5) `storage`

* **대상 리소스:** `aws_s3_bucket` (React 프론트엔드 정적 호스팅용, 도서 미디어 에셋용 2개), `aws_s3_bucket_public_access_block`, `aws_s3_bucket_versioning`
* **필수 입력(Inputs):** `frontend_bucket_name`, `media_bucket_name`
* **출력값(Outputs):** `frontend_bucket_id`, `frontend_bucket_arn`, `frontend_bucket_domain_name`, `media_bucket_id`, `media_bucket_arn`
* **참고 (버킷 정책은 여기서 안 만듦):** "CloudFront를 통해서만 접근 가능"하게 잠그는 `aws_s3_bucket_policy`는 CloudFront 배포 ARN을 조건으로 걸어야 하는데, 그 배포는 `02-runtime`의 `edge_routing`에서만 존재합니다. 그래서 버킷 정책 자체는 `edge_routing`이 `aws_cloudfront_origin_access_control`(OAC)과 함께 만듭니다 — `dns_zone`/`waf`와 같은 원칙: "그 대상이 존재하는 계층에서만 연결한다."

#### 6) `container_reg`

* **대상 리소스:** `aws_ecr_repository` (마이크로서비스별 1개), `aws_ecr_lifecycle_policy` (최근 30개 태그 유지)
* **필수 입력(Inputs):** `service_names` (`list(string)`) — 기획서 "3. 도메인" 기준 4개 확정: `catalog`(도서/리뷰/위시리스트), `order`(장바구니/쿠폰/주문/결제/배송), `member`(회원/사자 캐릭터), `ai`(RAG/추천/1:1 채팅/벡터 색인). "문의채팅"은 별도 서비스가 아니라 `ai` 도메인 하위 기능이므로 5번째 레포는 만들지 않음.
* **출력값(Outputs):** `repository_urls` (`map(string)`)

---

### 3.2 Data Modules (`modules/data/`)

#### 1) `aurora_pg`

* **대상 리소스:** `aws_rds_cluster`, `aws_rds_cluster_instance` (Serverless v2 0.5~8 ACU 또는 Provisioned Multi-AZ), `aws_db_subnet_group`, `aws_security_group`
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `database_name` (`bookdb`), `master_username`
* **출력값(Outputs):** `cluster_endpoint` (Writer), `reader_endpoint` (Reader), `cluster_security_group_id`, `cluster_identifier`, `master_user_secret_arn` (`manage_master_user_password = true`로 AWS가 자동 발급하는 Secrets Manager 시크릿 ARN — 마스터 비밀번호를 tfvars/state에 평문으로 두지 않기 위함)
* **안전장치 (Critical 등급):** `deletion_protection = true`, `skip_final_snapshot = false` 를 기본값으로 두고, 환경별로 낮추고 싶으면 `terraform.tfvars`에서 명시적으로 override. `prevent_destroy` lifecycle은 prod 환경에서만 적용.

#### 2) `rds_proxy`

* **대상 리소스:** `aws_db_proxy` (IAM 인증 모드), `aws_db_proxy_default_target_group`, `aws_db_proxy_target`, `aws_iam_role` (Proxy용)
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `aurora_cluster_identifier`, `secrets_manager_arn` — 둘 다 같은 `01-data` 계층 안에서 `aurora_pg` 모듈의 `cluster_identifier`/`master_user_secret_arn` 출력을 바로 참조 (계층 간 SSM 필요 없음)
* **출력값(Outputs):** `proxy_endpoint`, `proxy_arn`

#### 3) `cache_valkey`

* **대상 리소스:** `aws_elasticache_replication_group` (Multi-AZ, `engine = "valkey"`, `engine_version = "8.2"`, `noeviction` 파라미터 그룹 적용), `aws_elasticache_subnet_group`
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `node_type` (`cache.t4g.medium`)
* **출력값(Outputs):** `valkey_primary_endpoint`, `valkey_reader_endpoint`
* **참고:** Valkey는 Redis OSS와 프로토콜 호환이라 Redisson 분산 락, Pub/Sub, Streams 등 백엔드 코드에서 쓰는 Redis 클라이언트를 그대로 씁니다. AWS ElastiCache가 새 클러스터부터 Valkey를 기본값으로 미는 추세라 이 프로젝트도 처음부터 Valkey로 시작합니다.

#### 4) `auth`

* **대상 리소스:** `aws_cognito_user_pool`, `aws_cognito_user_pool_client` (SRP/PASSWORD Auth), `aws_cognito_user_pool_domain`
* **필수 입력(Inputs):** `user_pool_name`, `custom_domain_name`
* **출력값(Outputs):** `user_pool_id`, `user_pool_arn`, `user_pool_client_id`

---

### 3.3 Compute Modules (`modules/compute/`) — `02-runtime` 계층이 사용

#### 1) `eks_cluster`

* **대상 리소스:** `aws_eks_cluster` (v1.30+), `aws_eks_node_group` (CoreDNS/Karpenter 기동용 t4g.medium 2노드), `aws_iam_openid_connect_provider`, `aws_eks_addon` (vpc-cni, kube-proxy, coredns)
* **필수 입력(Inputs):** `vpc_id`, `app_subnet_ids`, `cluster_name`, `cluster_version`
* **출력값(Outputs):** `cluster_name`, `cluster_endpoint`, `cluster_certificate_authority_data`, `oidc_provider_arn`, `oidc_provider_url`

#### 2) `karpenter`

* **대상 리소스:** `aws_iam_role` (Karpenter Controller & Node IRSA), `aws_sqs_queue` (Spot Interruption Queue), `aws_cloudwatch_event_rule`/`aws_cloudwatch_event_target` (EC2 State Change 알림), `kubernetes_manifest` (NodePool, EC2NodeClass)
* **필수 입력(Inputs):** `cluster_name`, `oidc_provider_arn`, `oidc_provider_url`, `vpc_id`, `app_subnet_ids` (모두 `00-base`가 SSM에 등록해둔 값을 조회 — Karpenter가 띄우는 노드는 기획서 원칙대로 Private App Subnet에만 생성)
* **출력값(Outputs):** `karpenter_node_instance_profile_name`, `karpenter_irsa_arn`
* **주의:** NodePool/EC2NodeClass는 Kubernetes 커스텀 리소스라 `eks_cluster` 모듈이 만든 클러스터가 존재해야 apply 가능합니다. `main.tf`에서 `module.eks_cluster` → `module.karpenter` 순서로 `depends_on`을 명시하고, 최초 apply 시 클러스터가 없는 상태에서 kubernetes/helm provider가 인증에 실패할 수 있으므로 `terraform apply -target=module.eks_cluster` 로 클러스터부터 만든 뒤 나머지를 apply하는 2단계 절차를 씁니다 (§5.1 참고).

#### 3) `ingress_alb`

* **대상 리소스:** `helm_release` (aws-load-balancer-controller), `aws_iam_role` (Controller IRSA), `kubernetes_manifest` 또는 `aws_lb_target_group`/`aws_lb_listener_rule` (서비스별 라우팅 규칙)
* **필수 입력(Inputs):** `cluster_name`, `oidc_provider_arn`, `oidc_provider_url`, `vpc_id`, `public_subnet_ids`, `service_routes` (`map(object)` — 서비스명 → 경로/포트)
* **출력값(Outputs):** `alb_dns_name`, `alb_arn`, `target_group_arns`
* **의존성:** `eks_cluster`, `karpenter`와 마찬가지로 클러스터가 먼저 있어야 하므로 `main.tf` 내에서 `eks_cluster` → `karpenter` → `ingress_alb` 순으로 적용됩니다. 새 마이크로서비스가 추가될 때 가장 자주 바뀌는 모듈이므로, `service_routes` 변수만 건드리면 되도록 라우팅 규칙을 최대한 데이터 기반으로 설계합니다.

#### 4) `edge_routing`

* **역할:** `00-base`에서 만든 도메인/인증서/WAF를, `02-runtime`에서 방금 만든 ALB에 실제로 연결하는 모듈. `edge_security`처럼 여러 역할을 한 모듈에 섞지 않고, "ALB가 준비된 뒤에만 할 수 있는 일"만 여기 모읍니다.
* **트래픽 경로 (아키텍처 다이어그램 기준):** 사용자 요청은 항상 `도메인 → CloudFront → ALB` 한 경로로만 들어옵니다. 도메인이 ALB를 직접 가리키는 별도 레코드는 만들지 않습니다 — 정적 자산(S3)이든 API(ALB)든 전부 CloudFront 하나를 거칩니다.
* **대상 리소스:** `aws_cloudfront_distribution` (오리진: ALB + S3 프론트엔드/이미지 버킷, 경로 기반 라우팅, `web_acl_id`로 WAF ARN 직접 연결), `aws_cloudfront_origin_access_control` (OAC), `aws_s3_bucket_policy` (S3 버킷을 이 CloudFront 배포에서만 접근 가능하도록 제한 — 버킷 자체는 `00-base`의 `storage` 소유), `aws_route53_record` (도메인 → **CloudFront** ALIAS)
* **참고:** `CLOUDFRONT` 스코프 WebACL은 별도 연결 리소스(`aws_wafv2_web_acl_association`, REGIONAL 전용)가 아니라 `aws_cloudfront_distribution.web_acl_id` 인자에 ARN을 바로 넣어서 붙입니다.
* **필수 입력(Inputs):** `alb_dns_name` (같은 `02-runtime` 내 `ingress_alb` 모듈 출력을 바로 참조), `route53_zone_id`, `acm_certificate_arn` (us-east-1 인증서, `acm_cert` 참고), `waf_web_acl_arn`, `frontend_bucket_id`, `frontend_bucket_arn`, `frontend_bucket_domain_name` (모두 `00-base`가 SSM에 등록해둔 값을 조회)
* **출력값(Outputs):** `cloudfront_distribution_id`, `cloudfront_domain_name`, `public_domain_url`
* **순서:** `main.tf` 내에서 `ingress_alb` 다음, 즉 `eks_cluster → karpenter → ingress_alb → edge_routing` 순으로 적용됩니다.

---

## 4. 계층 간 데이터 연동 표준 (Decoupling Strategy)

하위 계층이 상위 계층의 리소스(VPC ID, Subnet ID 등)를 참조할 때 **AWS Systems Manager (SSM) Parameter Store**를 활용하여 State 파일 간 결합도를 완전히 제거합니다.

```
[00-base Apply] ────► aws_ssm_parameter.vpc_id 등록 ("/prod/network/vpc_id")
                             │
[01-data Plan]  ◄──── data.aws_ssm_parameter.vpc_id 조회

```

> **트레이드오프:** `terraform_remote_state` data source 대신 SSM을 쓰는 이유는 하위 계층이 상위 계층의 state 파일을 직접 열어보지 않아도 되게 하기 위해서입니다(state 파일 자체가 민감 정보를 담고 있어 접근 권한을 좁히기 쉬움). 대신 SSM 값은 "그 시점에 apply된 값의 스냅샷"이라, 상위 계층에서 값이 바뀌어도 하위 계층 `terraform plan`이 자동으로 drift를 잡아내지 못합니다. 상위 계층을 apply한 뒤에는 그 값을 참조하는 하위 계층도 순서대로 다시 apply해야 한다는 점을 팀 규칙으로 기억해두세요 (§5.1 순서 그대로).

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
# 1. Base 계층 배포 (VPC, 서브넷, ECR, S3, Route53, ACM) — 고정자원
cd terraform/environments/prod/00-base
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 2. Data 계층 배포 (Aurora DB, RDS Proxy, Valkey, Cognito) — 고정자원
cd ../01-data
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 3. Runtime 계층 배포 (EKS → Karpenter → ALB Controller) — 비고정자원
cd ../02-runtime
terraform init
# 3-1. 클러스터부터 먼저 생성 (Karpenter/ALB Controller의 kubernetes provider가 인증할 대상이 필요)
terraform apply -target=module.eks_cluster
# 3-2. 나머지(Karpenter, ALB Controller, 라우팅) 적용
terraform plan -out=tfplan
terraform apply tfplan

```

> **왜 `00-base`, `01-data`는 `-auto-approve`를 쓰지 않는가:** 두 계층은 위험 등급 **Critical**입니다. `plan -out` → 사람이 diff를 읽고 `apply tfplan`으로 승인하는 2단계를 항상 거칩니다. `02-runtime`(비고정자원)도 신규 인프라를 처음 세우는 순간만큼은 같은 절차를 따르되, 이후 반복 배포(서비스 라우트 추가 등)부터는 팀 판단에 따라 CI에서 자동화해도 됩니다.

### 5.2 자원 정리 및 삭제 순서 (역순 삭제)

```bash
cd terraform/environments/prod/02-runtime && terraform plan -destroy -out=destroy.tfplan && terraform apply destroy.tfplan
cd ../01-data    && terraform plan -destroy -out=destroy.tfplan && terraform apply destroy.tfplan
cd ../00-base    && terraform plan -destroy -out=destroy.tfplan && terraform apply destroy.tfplan

```

> **`-auto-approve`를 쓰지 않는 이유:** `01-data`는 Aurora/Valkey 등 영속 데이터를 담고 있는 Critical 계층입니다. 매트릭스에서 스스로 "위험 등급 Critical"이라 정의해놓고 삭제는 확인 없이 자동 진행하면 모순입니다. 삭제 전 반드시 `plan -destroy`로 무엇이 지워지는지 사람이 확인한 뒤 `apply`합니다. dev 환경처럼 데이터 유실이 문제되지 않는 곳에서만 팀 판단으로 `-auto-approve`를 써도 됩니다.

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
    key            = "prod/00-base.tfstate" # 디렉터리별 고유 키 지정 (00-base / 01-data / 02-runtime)
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
      Layer       = "base" # base | data | runtime
    }
  }
}

# 00-base 전용: CloudFront가 요구하는 acm_cert/waf만 이 alias로 생성
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "book-eating-lion"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "base"
    }
  }
}

```

### 6.3 Dev 환경 비용 절감 규칙 (`modules/dev_tools/ec2_postgres`)

dev 환경의 `01-data` 계층은 Aurora Multi-AZ 대신 **단일 EC2 인스턴스에 PostgreSQL을 직접 설치**하는 `ec2_postgres` 모듈을 호출합니다 (Aurora Serverless v2도 최소 0.5 ACU부터 시간당 과금되어, 상시 켜두는 dev 환경에는 비효율적).

* dev의 `environments/dev/01-data/main.tf`는 `modules/data/aurora_pg` 대신 `modules/dev_tools/ec2_postgres`를 호출합니다.
* 두 모듈의 출력값 이름(`cluster_endpoint` 등)을 동일하게 맞춰서, 상위에서 참조하는 SSM 파라미터 이름(`/${var.environment}/data/db_endpoint`)이 환경에 상관없이 동일한 키를 쓰도록 합니다. 이렇게 하면 `02-runtime`은 dev/prod 어느 쪽이든 같은 코드로 DB endpoint를 읽어올 수 있습니다.
* prod에는 이 모듈을 절대 사용하지 않습니다 (Multi-AZ/자동 백업 없음 — 데이터 유실 위험).

---

## 7. 아직 다루지 않은 것 (알고 있는 범위 밖)

기획서에는 있지만 이 문서(3계층 뼈대)에는 아직 반영하지 않은 인프라입니다. 빠뜨린 게 아니라 의도적으로 나중으로 미룬 것임을 남겨둡니다.

* **AI 벡터 파이프라인**: 신간 등록 → S3 이벤트 → SQS → S3 Vectors 자동 색인 → Bedrock 임베딩. SQS 큐, S3 이벤트 알림, S3 Vectors 버킷/인덱스, Bedrock 호출 IAM 권한 모두 미정.
* **모니터링/알림**: CloudWatch 알람(Pod CPU, DB 커넥션, Valkey 메모리) + SNS 알림. 계층 하나에 속하지 않고 각 계층 리소스마다 걸리는 성격이라 배치 방식부터 다시 정해야 함.
* **CI/CD 배포 권한**: 아키텍처 다이어그램상 GitHub Actions가 직접 EKS에 배포합니다(Developer → GitHub → GitHub Actions → EKS). GitHub Actions가 AWS를 인증하는 방법(OIDC Provider + IAM Role)이 현재 `00-base` 어디에도 없음 — 나중에 `dns_zone`/`acm_cert`처럼 역할이 분명한 모듈(예: `github_oidc`)로 추가 필요.
