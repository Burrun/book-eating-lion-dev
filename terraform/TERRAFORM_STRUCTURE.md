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
| **`00-base`** | 고정 | 극저 (연 1~2회) | **Critical** | VPC, Multi-AZ Subnets, IGW/NAT, Route 53 Hosted Zone, ACM 인증서, WAF WebACL, ECR, S3, SNS Topic, GitHub OIDC Provider | 네트워크 토대 봉인 (파괴 위험 차단) |
| **`01-data`** | 고정 | 저 (분기 1회) | **Critical** | Aurora PostgreSQL (스토리지 3AZ 6중 복제 + Writer/Reader 인스턴스는 2AZ 배치), RDS Proxy, ElastiCache for Valkey 8.2 (Cluster Mode Disabled, Multi-AZ Failover), Cognito, S3 Vectors(추천용/구매도서 RAG용 인덱스 분리), 신간 등록 이벤트 채널(세부 구현 미확정) | 영속 데이터 유실 방지 및 독립 보존 |
| **`02-runtime`**| 비고정 | 중~고 (주 단위) | **High** | EKS Control Plane, OIDC, Karpenter, ALB Controller, Target Group/Listener, CloudFront 배포, Route 53 ALIAS 레코드, AI 서비스 Bedrock IRSA | 서비스 배포 주기에 맞춘 반복 Plan/Apply |

> **"3AZ"와 "2AZ"가 둘 다 맞는 이유:** 기획서 텍스트의 "3개 AZ 6중 복제"는 Aurora **스토리지 레이어** 얘기입니다 — Aurora는 인스턴스를 몇 개 띄우든 상관없이 스토리지를 항상 3AZ에 6중 복제합니다(AWS가 자동으로 하는 것이라 Terraform에서 켜고 끄는 옵션이 아님). 반면 다이어그램의 "AZ a/b 2개"는 **컴퓨트 인스턴스**(Writer/Reader) 배치 얘기입니다. 이 프로젝트는 VPC를 2AZ로 설계했으므로 인스턴스는 AZ당 하나씩(Writer→AZ a, Reader→AZ b) 배치합니다. ElastiCache도 같은 방식으로 Primary→AZ a, Replica→AZ b로 짝을 맞춥니다. ElastiCache 엔진은 Redis가 아니라 **Valkey 8.2**입니다(§3.2-3 참고, 기획서 텍스트는 구버전).

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
│   │   ├── container_reg/                        # Amazon ECR 레포지토리 (서비스별 1개씩)
│   │   ├── alerting/                             # 장애/이상 알림용 SNS Topic + 이메일 구독 (개별 알람은 그 자원 만드는 모듈에서 이 Topic ARN을 받아 생성)
│   │   └── github_oidc/                          # GitHub Actions가 AWS를 인증하는 OIDC Provider + IAM Role (워크플로우 YAML 자체는 테라폼 범위 아님)
│   ├── data/
│   │   ├── aurora_pg/                            # 스토리지 3AZ 자동 복제 + Writer/Reader 인스턴스(reader_count로 0~2 조절, 기본 1)
│   │   ├── rds_proxy/                            # K8s 워크로드 커넥션 풀링용 AWS RDS Proxy 및 IAM 인증
│   │   ├── cache_valkey/                         # ElastiCache for Valkey 8.2, Cluster Mode Disabled (replica_count로 조절, 기본 1)
│   │   ├── auth/                                 # AWS Cognito User Pool, Resource Server, App Client
│   │   └── ai_pipeline/                          # S3 Vectors 인덱스 2개(추천용 / 구매도서 RAG 인용용) + 신간 등록 이벤트 채널 — 세부 구현 미확정, §3.2-5 참고
│   ├── compute/                                  # 02-runtime 계층이 사용하는 모듈 (K8s 클러스터 + 라우팅 + 공개 진입점)
│   │   ├── eks_cluster/                          # EKS v1.30+ Control Plane, Managed NodeGroup(시스템용), OIDC Provider
│   │   ├── karpenter/                            # Karpenter Controller용 IAM/SQS, NodePool, EC2NodeClass 매니페스트
│   │   ├── ingress_alb/                          # AWS Load Balancer Controller, Target Group, ALB Listener
│   │   ├── edge_routing/                         # CloudFront 배포(S3+ALB 오리진, WAF 연결), 도메인→CloudFront Route 53 ALIAS 레코드
│   │   └── ai_service_iam/                       # AI 서비스 Pod용 IRSA — Bedrock 호출 + 신간 등록 이벤트 소비 + S3 Vectors 읽기/쓰기 — 세부 구현 미확정, §3.3-5 참고
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
        │   ├── outputs.tf                        # vpc_id, subnet_ids, app_security_group_id, acm_certificate_arn, route53_zone_id, waf_web_acl_arn, frontend_bucket_id/arn/domain_name, media_bucket_id/arn, sns_topic_arn, github_actions_role_arn → SSM 등록
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
            ├── main.tf                           # 00-base/01-data SSM 참조 + modules/compute/* 호출 (eks_cluster → karpenter → ingress_alb → edge_routing / ai_service_iam 순)
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
* **출력값(Outputs):** `vpc_id`, `public_subnet_ids`, `app_subnet_ids`, `data_subnet_ids`, `app_security_group_id` (EKS 노드/Pod 공용 보안그룹 — `01-data`의 `aurora_pg`/`rds_proxy`/`cache_valkey`가 이 SG를 소스로 인바운드를 여는 데 씀. 실제 구현 중 발견: 이 출력이 없으면 데이터 계층 보안그룹을 무엇을 기준으로 열지 정할 방법이 없었음)

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

#### 7) `alerting`

* **대상 리소스:** `aws_sns_topic`, `aws_sns_topic_subscription` (운영자 이메일/SMS)
* **필수 입력(Inputs):** `alert_email` (또는 `alert_phone_number`)
* **출력값(Outputs):** `sns_topic_arn`
* **참고:** Topic만 여기서 만듭니다. "무엇을 알람으로 볼지"(Aurora 커넥션 수, Valkey 메모리, Pod CPU 등)는 그 리소스를 실제로 만드는 모듈이 각자 `aws_cloudwatch_metric_alarm`을 만들고 이 Topic ARN을 SSM으로 받아 알람 액션으로 연결합니다 — 계층 하나로 몰아넣지 않고 "그 자원을 아는 모듈이 그 자원의 알람도 안다"는 원칙.

#### 8) `github_oidc`

* **대상 리소스:** `aws_iam_openid_connect_provider` (`token.actions.githubusercontent.com`), `aws_iam_role` (GitHub Actions가 assume), `aws_iam_role_policy` (ECR push + EKS 배포에 필요한 최소 권한)
* **필수 입력(Inputs):** `environment` (Role 이름 충돌 방지용), `create_oidc_provider` (`bool`, 기본 `true`), `github_org`, `github_repo` (트러스트 정책의 `sub` 조건을 이 리포지토리로 제한 — 다른 리포/포크가 이 역할을 못 쓰게), `ecr_repository_arns`
* **출력값(Outputs):** `github_actions_role_arn`
* **범위:** 이 모듈은 "GitHub Actions가 AWS를 인증하는 방법"까지만입니다. 실제 배포 워크플로우(`*.github/workflows/*.yml`)는 애플리케이션 리포지토리가 관리하는 영역이라 이 테라폼 문서에서 다루지 않습니다.
* **주의 (실제 구현 중 발견 — dev/prod 동시 호출 시 충돌):** `aws_iam_openid_connect_provider`는 **계정당 URL 하나에 유일한 전역 리소스**입니다. dev/prod가 모두 이 모듈을 호출하므로, 한쪽만 `create_oidc_provider = true`로 실제로 만들고 나머지는 `false`로 둬서 `data "aws_iam_openid_connect_provider"`로 조회만 하게 합니다. 이 프로젝트는 `prod/00-base`가 소유합니다. **결과적으로 `dev/00-base`는 `prod/00-base`가 최소 한 번 apply된 뒤에만 apply할 수 있습니다** — 계층 간 순서(§5.1)와 별개로 생기는, OIDC Provider 하나 때문의 예외적인 환경 간 순서 의존입니다. IAM Role 이름에는 `environment`를 넣어 dev/prod가 서로 다른 이름을 쓰게 해서 그쪽은 충돌하지 않습니다.

---

### 3.2 Data Modules (`modules/data/`)

#### 1) `aurora_pg`

* **대상 리소스:** `aws_rds_cluster`, `aws_rds_cluster_instance` (Serverless v2 0.5~8 ACU 또는 Provisioned, `count = 1 + var.reader_count`), `aws_db_subnet_group`, `aws_security_group`, `aws_cloudwatch_metric_alarm` (DB 커넥션 수, CPU — 알람 액션은 `sns_topic_arn`)
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `database_name` (`bookdb`), `master_username`, `sns_topic_arn` (`00-base`의 `alerting` 출력을 SSM으로 조회), `reader_count` (`number`, 기본값 `1`)
* **출력값(Outputs):** `cluster_endpoint` (Writer), `reader_endpoint` (Reader), `cluster_security_group_id`, `cluster_identifier`, `master_user_secret_arn` (`manage_master_user_password = true`로 AWS가 자동 발급하는 Secrets Manager 시크릿 ARN — 마스터 비밀번호를 tfvars/state에 평문으로 두지 않기 위함)
* **스토리지 vs 인스턴스 (중요):** Aurora **스토리지**는 인스턴스 개수와 무관하게 항상 3AZ에 6중 복제됩니다 — AWS가 자동으로 하는 것이라 이 모듈에 옵션이 없습니다. 이 모듈이 실제로 조절하는 건 **컴퓨트 인스턴스**(Writer/Reader) 개수뿐입니다. `reader_count`로 단계별 조절 (아래 세 단계는 전부 `aurora_pg`를 실제로 호출하는 환경 기준입니다 — `dev/01-data`는 §6.3에 따라 `aurora_pg`가 아니라 `ec2_postgres`를 쓰므로 이 변수 자체가 적용되지 않습니다):
  * `reader_count = 0` — 초기 단계 (Writer 1, 비용 최소)
  * `reader_count = 1` — 기본값, 다이어그램 기준 2AZ 운영 시연 (Writer AZ-a / Reader AZ-b)
  * `reader_count = 2` — k6 부하 테스트 등 강한 Multi-AZ 시연용. **VPC가 지금 2AZ로 설계돼 있으므로(§3.1-1 `vpc`), 이 값을 쓰려면 `data_subnet_ids`에 3번째 AZ 서브넷을 먼저 추가해야 합니다.**
* **안전장치 (Critical 등급):** `deletion_protection = true`, `skip_final_snapshot = false` 를 기본값으로 두고, 환경별로 낮추고 싶으면 `terraform.tfvars`에서 명시적으로 override. `prevent_destroy` lifecycle은 prod 환경에서만 적용.

#### 2) `rds_proxy`

* **대상 리소스:** `aws_db_proxy` (IAM 인증 모드), `aws_db_proxy_default_target_group`, `aws_db_proxy_target`, `aws_iam_role` (Proxy용)
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `aurora_cluster_identifier`, `secrets_manager_arn` — 둘 다 같은 `01-data` 계층 안에서 `aurora_pg` 모듈의 `cluster_identifier`/`master_user_secret_arn` 출력을 바로 참조 (계층 간 SSM 필요 없음)
* **출력값(Outputs):** `proxy_endpoint`, `proxy_arn`

#### 3) `cache_valkey`

* **대상 리소스:** `aws_elasticache_replication_group` (`engine = "valkey"`, `engine_version = "8.2"`, `cluster_mode_enabled = false`, `multi_az_enabled = true`, `automatic_failover_enabled = true`, `num_cache_clusters = 1 + var.replica_count`, `noeviction` 파라미터 그룹 적용), `aws_elasticache_subnet_group`, `aws_cloudwatch_metric_alarm` (메모리 사용률 — 알람 액션은 `sns_topic_arn`)
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `node_type` (`cache.t4g.medium`), `sns_topic_arn` (`00-base`의 `alerting` 출력을 SSM으로 조회), `replica_count` (`number`, 기본값 `1`)
* **출력값(Outputs):** `valkey_primary_endpoint`, `valkey_reader_endpoint`
* **인스턴스 구성:** `aurora_pg`의 `reader_count`와 같은 방식. 기본값(`replica_count = 1`)은 다이어그램 기준 2AZ(Primary AZ-a / Replica AZ-b)이고, Aurora의 "강한 3AZ 시연"과 짝을 맞추려면 `replica_count = 2` + 3번째 AZ 서브넷이 필요합니다(§3.1-1 `vpc` 참고).
* **용도:** ① 베스트셀러 캐싱 ② Redisson 분산 락(재고 오버셀링 방지) ③ 채팅 Pub/Sub ④ MSA 비동기 이벤트(Streams) ⑤ **AI 임베딩·검색 결과 캐시** (`ai_pipeline`이 S3 Vectors 조회 결과를 여기 캐싱 — 매번 벡터 검색을 반복하지 않도록)
* **참고:** Valkey는 Redis OSS와 프로토콜 호환이라 Redisson 분산 락, Pub/Sub, Streams 등 백엔드 코드에서 쓰는 Redis 클라이언트를 그대로 씁니다. AWS ElastiCache가 새 클러스터부터 Valkey를 기본값으로 미는 추세라 이 프로젝트도 처음부터 Valkey로 시작합니다. Cluster Mode는 Disabled — 샤딩 없이 Primary/Replica 구조 그대로 씁니다.

#### 4) `auth`

* **대상 리소스:** `aws_cognito_user_pool`, `aws_cognito_user_pool_client` (SRP/PASSWORD Auth), `aws_cognito_user_pool_domain`
* **필수 입력(Inputs):** `user_pool_name`, `custom_domain_name`
* **출력값(Outputs):** `user_pool_id`, `user_pool_arn`, `user_pool_client_id`

#### 5) `ai_pipeline`

> **아직 세부 구현이 확정되지 않은 영역입니다.** 기능 자체가 팀 내에서도 정확히 확정되지 않아, 여기서는 기획서 수준의 큰 흐름만 잡아두고 실제 이벤트 트리거 방식(S3 이벤트 vs 앱이 직접 발행, 큐 이름, 인덱스 이름 등)은 백엔드 코드가 정해지는 대로 다시 맞춥니다.

* **역할:** 벡터 저장을 목적이 다른 두 인덱스로 분리합니다 — 용도가 섞이면 검색 품질도 섞이기 때문입니다.
  * **추천 도서 인덱스**: 신간 등록 이벤트를 받아 비동기로 임베딩·색인합니다.
  * **RAG 질의응답 인덱스**: "개인 메모 RAG"라고 부르지만 실제로는 사용자가 **구매한 책의 본문**을 임베딩해서, 질문하면 관련 인용 구절을 찾아 보여주는 기능입니다(사용자가 직접 작성한 메모 텍스트를 임베딩하는 게 아님). 구매 여부로 검색 범위가 제한됩니다.

  기획서가 이 항목을 "3) 데이터베이스 및 인메모리 캐시" 영역에 함께 분류해서, Aurora/Valkey와 같은 `01-data`에 둡니다.
* **대상 리소스:** 신간 등록 이벤트를 받을 큐(SQS 또는 다른 이벤트 채널 — 확정 전), S3 Vectors 버킷 + 인덱스 2개(추천용, 구매도서 RAG용) (AWS provider의 최신 리소스 타입 확인 필요 — 이 서비스는 최근 출시라 Terraform AWS provider 버전에 따라 리소스명이 다를 수 있음, 구현 직전에 provider 문서 재확인)
* **필수 입력(Inputs):** `media_bucket_id`, `media_bucket_arn` (`00-base`의 `storage` 출력을 SSM으로 조회 — 버킷은 `storage`가 갖고 있고, 이벤트 알림 설정만 여기서 붙임. `edge_routing`이 CloudFront 배포에 버킷 정책을 붙이는 것과 같은 이유)
* **출력값(Outputs):** `vector_bucket_arn`, `recommendation_index_arn`, `purchased_book_rag_index_arn`, `ingest_channel_arn` (신간 등록 이벤트 채널 — SQS면 큐 ARN, 다른 채널이면 그에 맞는 식별자)

---

### 3.3 Compute Modules (`modules/compute/`) — `02-runtime` 계층이 사용

#### 1) `eks_cluster`

* **대상 리소스:** `aws_eks_cluster` (v1.30+), `aws_eks_node_group` (CoreDNS/Karpenter 기동용 t4g.medium 2노드), `aws_iam_openid_connect_provider`, `aws_eks_addon` (vpc-cni, kube-proxy, coredns, **amazon-cloudwatch-observability** — Pod/Node CPU·메모리를 CloudWatch Container Insights로 수집), `aws_cloudwatch_metric_alarm` (Pod CPU — 알람 액션은 `sns_topic_arn`)
* **필수 입력(Inputs):** `vpc_id`, `app_subnet_ids`, `cluster_name`, `cluster_version`, `sns_topic_arn` (`00-base`의 `alerting` 출력을 SSM으로 조회)
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

#### 5) `ai_service_iam`

> `ai_pipeline`과 마찬가지로 세부 구현이 아직 확정 전입니다 — 큰 틀(어떤 권한이 필요한가)만 잡아둡니다.

* **역할:** `ai` 마이크로서비스 Pod가 Bedrock/S3 Vectors(그리고 신간 등록 이벤트를 받는 채널)를 호출할 때 쓰는 IRSA(IAM Role for Service Account). ALB나 CloudFront와는 무관하게 `eks_cluster`의 OIDC만 있으면 되므로, `edge_routing`과 순서상 나란히(병렬로) 적용 가능합니다.
* **대상 리소스:** `aws_iam_role` (Trust policy: `ai` 서비스 계정만), `aws_iam_policy` (`bedrock:InvokeModel`, 신간 등록 이벤트 소비, S3 Vectors 읽기/쓰기 — 최소 권한으로 이 세 가지만)
* **필수 입력(Inputs):** `oidc_provider_arn`, `oidc_provider_url` (같은 `02-runtime` 내 `eks_cluster` 출력을 바로 참조), `recommendation_index_arn`, `purchased_book_rag_index_arn`, `ingest_channel_arn` (`01-data`의 `ai_pipeline` 출력을 SSM으로 조회), `bedrock_model_arns` (`list(string)` — 실제 사용하는 임베딩/LLM 모델 ARN)
* **출력값(Outputs):** `ai_service_irsa_arn`

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
# 1. Base 계층 배포 (VPC, 서브넷, ECR, S3, Route53, ACM, WAF, SNS, GitHub OIDC) — 고정자원
cd terraform/environments/prod/00-base
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 2. Data 계층 배포 (Aurora DB, RDS Proxy, Valkey, Cognito, S3 Vectors) — 고정자원
cd ../01-data
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 3. Runtime 계층 배포 (EKS → Karpenter → ALB Controller → CloudFront/AI IRSA) — 비고정자원
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
