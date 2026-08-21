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
| **`01-data`** | 고정 | 저 (분기 1회) | **Critical** | Aurora PostgreSQL (스토리지 3AZ 6중 복제 + Writer/Reader 인스턴스는 2AZ 배치), RDS Proxy, ElastiCache for Valkey 8.2 (Cluster Mode Disabled, Multi-AZ Failover), Cognito, S3 Vectors(추천용/구매도서 RAG용 인덱스 분리, provider 미지원으로 현재는 출력값 `null`), 신간 등록 이벤트 채널(SQS, 구현 완료) | 영속 데이터 유실 방지 및 독립 보존 |
| **`02-runtime`**| 비고정 | 중~고 (주 단위) | **High** | EKS Control Plane, OIDC, Karpenter, AWS Load Balancer Controller + ingress-nginx(NLB), CloudFront 배포, Route 53 ALIAS 레코드, AI 서비스 Bedrock IRSA | 서비스 배포 주기에 맞춘 반복 Plan/Apply |

> **"3AZ"와 "2AZ"가 둘 다 맞는 이유:** 기획서 텍스트의 "3개 AZ 6중 복제"는 Aurora **스토리지 레이어** 얘기입니다 — Aurora는 인스턴스를 몇 개 띄우든 상관없이 스토리지를 항상 3AZ에 6중 복제합니다(AWS가 자동으로 하는 것이라 Terraform에서 켜고 끄는 옵션이 아님). 반면 다이어그램의 "AZ a/b 2개"는 **컴퓨트 인스턴스**(Writer/Reader) 배치 얘기입니다. 이 프로젝트는 VPC를 2AZ로 설계했으므로 인스턴스는 AZ당 하나씩(Writer→AZ a, Reader→AZ b) 배치합니다. ElastiCache도 같은 방식으로 Primary→AZ a, Replica→AZ b로 짝을 맞춥니다. ElastiCache 엔진은 Redis가 아니라 **Valkey 8.2**입니다(§3.2-3 참고, 기획서 텍스트는 구버전).

---

## 2. 전체 디렉터리 및 파일 상세 구조

```text
terraform/
├── bootstrap/                                     # [최초 1회] dev/prod 공용 S3 tfstate 버킷 + DynamoDB lock 테이블 생성. local state로 관리(§6.1 후반부 참고)
├── modules/                                      # [재사용 모듈 원형] 환경 독립적 HCL
│   ├── base/                                      # 모듈 이름은 맡고 있는 역할 그대로 (기능 혼합 금지)
│   │   ├── vpc/                                  # VPC, 서브넷(Public/App/Data), NAT GW, 라우팅 테이블
│   │   ├── dns_zone/                             # Route 53 Hosted Zone
│   │   ├── acm_cert/                             # ACM 인증서 발급 + DNS 검증 레코드
│   │   ├── waf/                                  # AWS WAF v2 WebACL 정의 (AWSManagedRulesCommonRuleSet, SQLi 방어) — `02-runtime`의 `edge_routing`이 CloudFront에 연결
│   │   ├── storage/                               # S3 (React 프론트엔드 정적 호스팅, 도서 미디어 에셋 버킷)
│   │   ├── container_reg/                        # Amazon ECR 레포지토리 (서비스별 1개씩)
│   │   ├── alerting/                             # 장애/이상 알림용 SNS Topic + 이메일 구독 (개별 알람은 그 자원 만드는 모듈에서 이 Topic ARN을 받아 생성)
│   │   └── github_oidc/                          # GitHub Actions가 AWS를 인증하는 OIDC Provider + IAM Role (워크플로우 YAML 자체는 테라폼 범위 아님)
│   ├── data/
│   │   ├── aurora_pg/                            # 스토리지 3AZ 자동 복제 + Writer/Reader 인스턴스(reader_count로 0~2 조절, 기본 1)
│   │   ├── rds_proxy/                            # K8s 워크로드 커넥션 풀링용 AWS RDS Proxy 및 IAM 인증
│   │   ├── cache_valkey/                         # ElastiCache for Valkey 8.2, Cluster Mode Disabled (replica_count로 조절, 기본 1)
│   │   ├── auth/                                 # AWS Cognito User Pool, Resource Server, App Client
│   │   └── ai_pipeline/                          # 신간 등록 이벤트 SQS 큐(+DLQ) 구현 완료. S3 Vectors 인덱스 2개(추천용/구매도서 RAG 인용용)는 provider 미지원으로 출력값 `null`, §3.2-5 참고
│   ├── compute/                                  # 02-runtime 계층이 사용하는 모듈 (K8s 클러스터 + 라우팅 + 공개 진입점)
│   │   ├── eks_cluster/                          # EKS v1.30+ Control Plane, Managed NodeGroup(시스템용), OIDC Provider
│   │   ├── karpenter/                            # Karpenter Controller용 IAM/SQS, NodePool, EC2NodeClass 매니페스트
│   │   ├── ingress_alb/                          # AWS Load Balancer Controller(NLB 프로비저닝) + ingress-nginx(실제 L7 라우팅, k8s/base/08-ingress.yaml이 대상)
│   │   ├── edge_routing/                         # CloudFront 배포(S3+ALB 오리진, WAF 연결), 도메인→CloudFront Route 53 ALIAS 레코드
│   │   └── ai_service_iam/                       # AI 서비스 Pod용 IRSA — Bedrock 호출 + 신간 등록 이벤트 소비 구현 완료, S3 Vectors 권한은 인덱스 ARN이 생기면 자동으로 붙는 조건부 statement로 구현, §3.3-5 참고
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
        │   ├── outputs.tf                        # db_endpoint, rds_proxy_endpoint, valkey_endpoint, cognito_user_pool_id → SSM 등록
        │   └── terraform.tfvars
        └── 02-runtime/
            ├── backend.tf                        # S3 tfstate Key: prod/02-runtime.tfstate
            ├── provider.tf                       # AWS + Helm + Kubernetes Provider 설정
            ├── main.tf                           # 00-base/01-data SSM 참조 + modules/compute/* 호출 (eks_cluster → karpenter → ingress_alb → edge_routing / ai_service_iam 순)
            ├── variables.tf
            ├── outputs.tf                        # cluster_name, cluster_endpoint, alb_dns_name, cloudfront_distribution_id, public_domain_url, ai_service_irsa_arn
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
* **안전장치 (Critical 등급):** `deletion_protection = true`, `skip_final_snapshot = false` 를 기본값으로 두고, 환경별로 낮추고 싶으면 `terraform.tfvars`에서 명시적으로 override. **정정:** 원래는 `prevent_destroy` lifecycle을 prod에서만 적용한다고 적었으나, Terraform의 `lifecycle.prevent_destroy`는 리터럴 값만 받고 변수를 못 받아서 환경별 조건부 적용이 애초에 불가능합니다(실제 구현 중 확인). 대신 `deletion_protection`을 실제 안전장치로 씁니다 — 이건 AWS API 레벨 보호라 Terraform이 아니라 콘솔/CLI로 지워도 막힙니다.

#### 2) `rds_proxy`

* **대상 리소스:** `aws_db_proxy` (Secrets Manager 인증 모드), `aws_db_proxy_default_target_group`, `aws_db_proxy_target`, `aws_iam_role` (Proxy용), `aws_security_group`(Proxy 전용) + `aws_security_group_rule`(Aurora 클러스터 SG에 "Proxy SG에서 5432" 인바운드 추가)
* **필수 입력(Inputs):** `vpc_id`, `data_subnet_ids`, `app_security_group_id`, `cluster_security_group_id`(**실제 구현 중 발견 — 원래 설계엔 없었음**: Proxy가 Aurora 클러스터 SG로 나가려면 Proxy 자신의 SG를 Aurora 클러스터 SG의 인바운드 소스로 추가해야 하는데, 그러려면 그 클러스터 SG ID가 필요함), `aurora_cluster_identifier`, `secrets_manager_arn` — 전부 같은 `01-data` 계층 안에서 `aurora_pg` 모듈 출력을 바로 참조 (계층 간 SSM 필요 없음)
* **인증 방식 정정:** 원래 "IAM 인증 모드"로 적었으나, 실제 백엔드 코드(k8s ConfigMap의 `DB_HOST`/`DB_PORT`/`DB_NAME` + Secret의 `DB_USERNAME`/`DB_PASSWORD`)를 보면 앱은 IAM 토큰이 아니라 **아이디/비밀번호로 접속**합니다. 그래서 `aws_db_proxy.auth`는 `auth_scheme = "SECRETS"`, `iam_auth = "DISABLED"`로 구현합니다 — `aurora_pg`가 만든 `master_user_secret_arn`을 그대로 씁니다.
* **출력값(Outputs):** `proxy_endpoint`, `proxy_arn`

#### 3) `cache_valkey`

* **대상 리소스:** `aws_elasticache_replication_group` (`engine = "valkey"`, `engine_version = "8.2"`, `num_cache_clusters = 1 + var.replica_count`, `noeviction` 파라미터 그룹 적용), `aws_elasticache_subnet_group`, `aws_elasticache_parameter_group`, `aws_cloudwatch_metric_alarm` (메모리 사용률 — 알람 액션은 `sns_topic_arn`)
* **정정 (실제 구현 중 발견):** `cluster_mode_enabled`라는 인자는 없습니다 — `num_cache_clusters`를 쓰는 것 자체가 Cluster Mode Disabled 구성입니다(`num_node_groups`를 쓰면 Cluster Mode Enabled가 되어 상호 배타적). 그리고 `automatic_failover_enabled`/`multi_az_enabled`는 Replica가 최소 1개 있어야 켤 수 있어서(`replica_count > 0`), dev처럼 `replica_count = 0`으로 비용을 아낄 때는 두 값 다 자동으로 꺼집니다.
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
* **대상 리소스:** `aws_sqs_queue`(신간 등록 이벤트 큐) + DLQ(`maxReceiveCount = 3`) — **구현 완료**. S3 Vectors 버킷 + 인덱스 2개(추천용, 구매도서 RAG용)는 **아직 구현 못 함**: `hashicorp/aws` provider 5.100.0 바이너리를 직접 확인한 결과 `s3vectors_*` 리소스 타입이 없습니다(2025년 말 출시된 신규 서비스라 provider가 아직 못 따라감). 지원되는 provider 버전이 나오면 추가하고, 그때까지 이 두 인덱스의 출력은 `null`입니다.
* **필수 입력(Inputs):** `environment` — **정정 (실제 구현 중 발견):** 애초에 이 모듈은 `media_bucket_id`/`media_bucket_arn`을 받아서 S3 이벤트 알림을 붙이려 했으나, 실제 백엔드 코드(이벤트-메시징-명세.md)를 보면 신간 등록 이벤트는 S3 업로드 트리거가 아니라 catalog-api가 SQS로 직접 발행하는 구조입니다. 그래서 S3 버킷 관련 입력 자체가 필요 없어졌습니다 — 앱이 `ingest_channel_arn` 큐에 바로 `SendMessage`합니다.
* **출력값(Outputs):** `vector_bucket_arn`, `recommendation_index_arn`, `purchased_book_rag_index_arn`, `ingest_channel_arn` (신간 등록 이벤트 채널 — SQS면 큐 ARN, 다른 채널이면 그에 맞는 식별자)

---

### 3.3 Compute Modules (`modules/compute/`) — `02-runtime` 계층이 사용

**실제 구현 완료 (2026-08)** — 아래 5개 모듈 전부 코드가 있고 6개 환경 조합(00-base/01-data/02-runtime × dev/prod) `terraform validate` 통과 상태입니다. 구현하면서 원래 설계가 실제와 안 맞아 크게 고친 부분이 있어 각 모듈에 표시해뒀습니다.

#### 1) `eks_cluster`

* **대상 리소스:** `aws_eks_cluster` (v1.30, `access_config.authentication_mode = "API"` — 레거시 aws-auth ConfigMap 대신 EKS Access Entries API 사용), `aws_eks_node_group`(시스템 노드그룹, t4g.medium 2노드, CoreDNS/Karpenter 컨트롤러 기동용), `aws_iam_openid_connect_provider`(클러스터 자체 OIDC, `tls_certificate`로 지문 조회), `aws_eks_addon`(vpc-cni, kube-proxy, coredns, **amazon-cloudwatch-observability**), `aws_cloudwatch_metric_alarm`(Pod CPU), `aws_eks_access_entry`+`aws_eks_access_policy_association`(**신규** — GitHub Actions 역할에 `AmazonEKSAdminPolicy` 부여, `github_oidc`가 만든 IAM Role이 실제로 kubectl 배포할 수 있게 완성하는 마지막 연결고리)
* **재검토 중 발견 — `aws_eks_access_policy_association`은 `access_entry`를 속성으로 참조하지 않습니다.** Association이 principal_arn/cluster_name 값만 공유할 뿐 Entry 리소스를 attribute로 안 가리켜서, Terraform이 둘의 생성 순서를 자동으로 보장하지 못합니다(Entry 없이 Association만 먼저 만들려 하면 API가 거부). `depends_on = [aws_eks_access_entry.github_actions]`를 명시로 추가했습니다. 반대로 시스템 노드그룹에 붙어 있던 `depends_on = [aws_eks_access_policy_association...]`는 기술적 근거가 없는(노드그룹과 GitHub Actions 접근권한은 서로 무관) 리소스라 제거했습니다.
* **필수 입력(Inputs):** `vpc_id`, `app_subnet_ids`, `cluster_name`, `cluster_version`, `sns_topic_arn`, `github_actions_role_arn`(**신규** — `00-base`의 `github_oidc` 출력을 SSM으로 조회, `null`이면 Access Entry 생략)
* **출력값(Outputs):** `cluster_name`, `cluster_endpoint`, `cluster_certificate_authority_data`, `oidc_provider_arn`, `oidc_provider_url`, `cluster_security_group_id`(**신규** — EKS가 자동 생성하는 공용 SG. `karpenter`가 노드에 붙일 SG로 이걸 그대로 씀 — 원래 설계엔 이 출력이 빠져 있어서 karpenter가 노드 SG를 어디서 받을지 정의가 없었음)

#### 2) `karpenter`

* **대상 리소스:** `aws_iam_role`(Controller IRSA + Node Role), `aws_sqs_queue`(Spot Interruption Queue), `aws_cloudwatch_event_rule`/`aws_cloudwatch_event_target` ×3(Spot Interruption, Rebalance Recommendation, Instance State-change), `helm_release`(`oci://public.ecr.aws/karpenter/karpenter`), `kubernetes_manifest`(EC2NodeClass, NodePool)
* **필수 입력(Inputs):** `cluster_name`, `cluster_endpoint`, `oidc_provider_arn`, `oidc_provider_url`, `vpc_id`, `app_subnet_ids`, `node_security_group_id`(**신규** — `eks_cluster`의 `cluster_security_group_id` 출력을 그대로 참조. 원래 입력 목록에 없었음)
* **출력값(Outputs):** `karpenter_node_instance_profile_name`(이름은 그대로지만 실제 값은 인스턴스 프로파일이 아니라 **IAM Role 이름** — 바로 아래 참고), `karpenter_irsa_arn`
* **재검토 중 발견 — `aws_iam_instance_profile`은 죽은 리소스였습니다.** EC2NodeClass가 `instanceProfile` 대신 `role`(IAM Role 이름) 필드를 쓰면 Karpenter 컨트롤러가 인스턴스 프로파일을 자기가 직접 만들고 관리합니다 — Controller 정책에 이미 `iam:CreateInstanceProfile`류 권한을 준 이유가 이것입니다. Terraform이 따로 인스턴스 프로파일을 만들어봐야 아무도 참조하지 않는 리소스라 제거했습니다. 같이 발견한 것들: SQS 큐 정책 Principal에 실제로는 보내지 않는 `sqs.amazonaws.com`이 잘못 들어가 있어 제거(진짜 발신자는 EventBridge뿐), NodePool의 인스턴스 패밀리 목록(`t4g.medium`/`t4g.large` → `t4g`)에 `distinct()`가 빠져 있어 중복 값이 생기던 것 수정.
* **주의:** NodePool/EC2NodeClass는 Kubernetes 커스텀 리소스라 `eks_cluster` 모듈이 만든 클러스터가 존재해야 apply 가능합니다. 최초 apply 시 클러스터가 없는 상태에서 kubernetes/helm provider가 인증에 실패할 수 있으므로 `terraform apply -target=module.eks_cluster` 로 클러스터부터 만든 뒤 나머지를 apply하는 2단계 절차를 씁니다 (§5.1 참고). Controller IAM 정책은 AWS 공식 문서 요약본이라 실제 apply 전 karpenter 릴리스 노트의 최신 정책과 대조가 필요합니다(버전마다 조금씩 늘어남).

#### 3) `ingress_alb`

> ⚠️ **실제 구현 중 근본적으로 다시 씀.** 원래 설계는 "AWS Load Balancer Controller가 Kubernetes Ingress를 직접 해석해서 서비스별 ALB Target Group을 만드는" 모델(`service_routes` 입력으로 라우팅 규칙을 Terraform에 넣는 방식)이었습니다. 그런데 실제 `k8s/base/08-ingress.yaml`을 다시 열어보니 `ingressClassName: nginx` + `nginx.ingress.kubernetes.io/*` 어노테이션을 씁니다 — 즉 **라우팅은 ingress-nginx가 하고 있었고**, AWS Load Balancer Controller가 관여할 여지가 없는 구조였습니다.

* **역할 (정정):** ① AWS Load Balancer Controller — Service `type=LoadBalancer`를 NLB로 프로비저닝하는 역할만 함. ② ingress-nginx 컨트롤러 — 실제 L7 라우팅, `k8s/base/08-ingress.yaml`이 보는 대상. **`service_routes` 입력은 삭제했습니다** — 라우팅 규칙은 Terraform이 아니라 git으로 관리되는 `k8s/base/*.yaml`이 갖고 CI가 배포합니다. Terraform은 그 라우팅을 실행할 컨트롤러 두 개를 세우는 것까지만 합니다.
* **대상 리소스:** `aws_iam_role`+정책(ALB Controller IRSA, AWS 공식 정책 요약본), `helm_release`(`aws-load-balancer-controller`, `eks-charts`), `helm_release`(`ingress-nginx`, Service를 `aws-load-balancer-type: nlb` 어노테이션으로 NLB 프로비저닝), `time_sleep`+`data "kubernetes_service"`(NLB DNS가 뜰 때까지 대기 후 Service 상태 조회 — 완전한 보장은 아니라 최초 apply 실패 시 재시도 필요할 수 있음)
* **필수 입력(Inputs):** `cluster_name`, `oidc_provider_arn`, `oidc_provider_url`, `vpc_id`, `public_subnet_ids`, `aws_region`
* **출력값(Outputs):** `alb_dns_name`(이름은 그대로 뒀지만 실제로는 **NLB 호스트명** — `edge_routing`이 이 값을 그대로 오리진으로 쓰므로 인터페이스만 유지), `alb_controller_role_arn`
* **의존성:** `eks_cluster` → `karpenter` → `ingress_alb` 순으로 적용됩니다.

#### 4) `edge_routing`

* **역할:** `00-base`에서 만든 도메인/인증서/WAF를, `02-runtime`에서 방금 만든 ALB(NLB)에 실제로 연결하는 모듈. `edge_security`처럼 여러 역할을 한 모듈에 섞지 않고, "ALB가 준비된 뒤에만 할 수 있는 일"만 여기 모읍니다.
* **트래픽 경로 (아키텍처 다이어그램 기준):** 사용자 요청은 항상 `도메인 → CloudFront → (기본) S3 프론트엔드 / (/api/*) ALB` 경로로만 들어옵니다. 도메인이 ALB를 직접 가리키는 별도 레코드는 만들지 않습니다.
* **대상 리소스:** `aws_cloudfront_distribution`(오리진 2개: S3 프론트엔드 `default_cache_behavior` + ALB `/api/*` `ordered_cache_behavior`, `web_acl_id`로 WAF ARN 직접 연결), `aws_cloudfront_origin_access_control`(OAC), `aws_cloudfront_function`(SPA 라우팅용, 아래 참고), `aws_s3_bucket_policy`(S3 버킷을 이 CloudFront 배포에서만 접근 가능하도록 제한), `aws_route53_record` ×2(apex + www, 도메인 → **CloudFront** ALIAS)
* **재검토 중 발견 — SPA 라우팅을 `custom_error_response`로 처리하면 실제 버그가 됩니다.** React Router 같은 클라이언트 사이드 라우팅을 지원하려면 확장자 없는 경로(예: `/mypage`)를 `index.html`로 돌려줘야 하는데, 처음엔 `custom_error_response`(403/404 → `/index.html`)로 구현했습니다. 그런데 `custom_error_response`는 **오리진과 무관하게 배포 전체에 걸리는 규칙**이라, `/api/*`(ALB 오리진)에서 나는 진짜 404까지 잡아채서 `index.html` 200 응답으로 바꿔버립니다 — 프론트가 API 에러를 영영 못 받게 되는 실제 버그입니다. `cloudfront-js-2.0` 런타임의 `aws_cloudfront_function`으로 바꿔서 `default_cache_behavior`(S3 오리진)에만 `viewer-request` 이벤트로 붙였습니다 — `/api/*` `ordered_cache_behavior`엔 이 함수를 안 붙이므로 API 오리진은 전혀 영향받지 않습니다. 같이 빠져있던 `default_root_object = "index.html"`도 추가했습니다.
* **ALB 오리진은 `origin_protocol_policy = "http-only"`입니다** — ingress-nginx가 내부적으로 TLS를 종단하지 않으므로(공개 HTTPS는 CloudFront가 ACM 인증서로 종단), CloudFront-오리진 구간은 평문 HTTP입니다.
* **필수 입력(Inputs):** `alb_dns_name`(같은 `02-runtime` 내 `ingress_alb` 출력), `route53_zone_id`, `acm_certificate_arn`(us-east-1), `waf_web_acl_arn`, `frontend_bucket_id`, `frontend_bucket_arn`, `frontend_bucket_domain_name`(모두 `00-base` SSM 조회)
* **출력값(Outputs):** `cloudfront_distribution_id`, `cloudfront_domain_name`, `public_domain_url`
* **순서:** `eks_cluster → karpenter → ingress_alb → edge_routing` 순으로 적용됩니다.

#### 5) `ai_service_iam`

* **역할:** `ai` 마이크로서비스 Pod(`ai-rag`/`ai-bot` 두 Deployment 다 — k8s-명세.md §1.4)가 Bedrock/신간 등록 이벤트 큐/S3 Vectors를 호출할 때 쓰는 IRSA. `edge_routing`과 순서상 나란히(병렬로) 적용 가능합니다.
* **대상 리소스:** `aws_iam_role`(Trust policy: `system:serviceaccount:lion-app:ai-rag`/`ai-bot` 둘 다 허용), `aws_iam_policy`(`bedrock:InvokeModel`, SQS 소비, S3 Vectors 읽기/쓰기)
* **S3 Vectors 권한은 조건부입니다** — `01-data`의 `ai_pipeline`이 아직 `null`을 출력하므로(provider 미지원), 인덱스 ARN이 하나라도 있을 때만 그 statement를 동적으로 추가합니다(`dynamic "statement"` 블록). 지금은 Bedrock + SQS 권한만 실제로 붙습니다.
* **필수 입력(Inputs):** `oidc_provider_arn`, `oidc_provider_url`, `ingest_channel_arn`, `recommendation_index_arn`(nullable), `purchased_book_rag_index_arn`(nullable), `bedrock_model_arns`
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
# 0. State 백엔드 부트스트랩 (dev/prod의 모든 계층이 쓸 S3 tfstate 버킷 +
#    DynamoDB lock 테이블 생성) — 계정에 이미 있으면(2번째 이후 사람) 생략.
#    terraform/bootstrap/README나 §6.1 후반부 참고. 이 모듈 자체는 local
#    state로 관리하므로 -out 확인 절차를 생략하지 않는다(되돌리기 어려운
#    리소스라 §5.1 전체와 같은 원칙 적용).
cd terraform/bootstrap
terraform init
terraform plan -out=tfplan
terraform apply tfplan
cd ../..

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

> **왜 이전엔 이 단계 없이도 `terraform init`이 됐었나:** `terraform/bootstrap`이
> 생기기 전에도(2026-08-20 이전) 이 버킷/테이블은 이미 존재해서 `init`이
> 문제없이 됐다 — 이 저장소의 어떤 terraform 코드로도 만들어진 적이 없고,
> 처음에 누군가 콘솔/AWS CLI로 수동 생성해둔 것이었기 때문이다. `backend.tf`의
> `backend "s3" { bucket = "..." }`는 그 이름의 버킷이 이미 있다고 가정하고
> 거기 연결만 할 뿐, 없으면 만들어주지 않는다. 2026-08-20 저녁 인프라 정리
> 중 이 버킷들까지 콘솔에서 같이 삭제되면서 그다음 `init`부터 `S3 bucket ...
> does not exist` 에러가 났고, 그래서 이 부트스트랩 모듈을 코드로 만들어
> 재생성 가능하게 했다.
>
> **이 버킷/테이블은 어디서 만드나:** `terraform/bootstrap`이 dev/prod 각각의
> `book-eating-lion-tfstate-{env}` S3 버킷(버저닝+암호화+public access
> block)과 `book-eating-lion-tflock-{env}` DynamoDB 테이블(PAY_PER_REQUEST)을
> 만든다. 다른 계층들의 backend가 될 버킷을 만드는 것이라 원격 backend를 쓸
> 수 없어(닭-달걀 문제) 이 모듈만 예외적으로 **local state**로 관리한다 —
> 실행한 사람이 `terraform/bootstrap/terraform.tfstate`를 잃어버리지 않게
> 보관할 것. 계정에 이미 버킷/테이블이 있으면(팀 두 번째 이후 사람) 이 단계는
> 생략한다. §5.1의 0단계 참고.
>
> 2026-08-20에 이 버킷/테이블을 인프라 정리 중 콘솔에서 실수로 같이 지운
> 적이 있다 — 그래서 `bootstrap`의 리소스에는 `lifecycle.prevent_destroy`
> (terraform 레벨)와 DynamoDB `deletion_protection_enabled`(AWS 레벨)를
> 걸어 재사고를 막는다. 만약 이 버킷/테이블이 다시 사라져 있다면(콘솔에서
> 실수로 지웠거나 계정이 바뀌었거나) `terraform/bootstrap`을 다시 apply해
> 재생성하면 된다 — 단, tfstate 버킷이 사라지면 그 안에 있던 각 계층의
> state도 함께 사라지므로, 재생성 후에는 각 계층에서 `terraform import`로
> 실제 AWS 리소스를 다시 끌어오거나 해당 환경을 처음부터 다시 배포해야
> 한다.

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
* **실제 구현 중 발견 — 인스턴스는 `data_subnet`이 아니라 `app_subnet`에 놓습니다.** `data_subnet`은 완전 격리(NAT 없음, §3.1-1 `vpc`)라 패키지 설치(`dnf install postgresql`)조차 안 됩니다. 그래서 이 인스턴스만 예외적으로 App Subnet(NAT 경유 아웃바운드 있음)에 배치하되, 보안그룹은 `aurora_pg`와 똑같이 "`app_security_group_id`에서만 5432 인바운드 허용"으로 제한해서 데이터 계층과 동일한 접근 통제를 유지합니다. SSH 키/포트는 아예 안 열고 SSM Session Manager로만 접속합니다. 마스터 비밀번호는 `random_password` + Secrets Manager로 관리해 Aurora와 동일한 보안 수준(평문 비밀번호를 tfvars/state에 안 둠)을 맞춥니다.
* **재검토 중 발견 — `user_data`에 비밀번호를 직접 심으면 안 됩니다(보안 이슈).** 처음엔 `${random_password.master.result}`를 `user_data` 스크립트(`ALTER USER`/`CREATE ROLE` 문)에 그대로 문자열 보간했습니다. 이러면 평문 비밀번호가 Terraform state(`aws_instance.user_data` 속성)뿐 아니라 **EC2 인스턴스 메타데이터로도 그대로 노출**되어(IMDS로 인스턴스 내부에서 IAM 권한 없이 조회 가능, `ec2:DescribeInstanceAttribute` 권한만 있으면 외부에서도 조회 가능), Secrets Manager를 따로 둔 목적 자체가 무너집니다. 그래서 `user_data`엔 비밀번호를 넣지 않고, 인스턴스가 **부팅 시점에 Secrets Manager에서 직접 읽어오도록** 고쳤습니다 — IAM Role(`aws_iam_role.ssm`)에 `secretsmanager:GetSecretValue`(대상 시크릿 ARN으로 스코프 제한)를 추가하고, 스크립트는 `aws secretsmanager get-secret-value`로 받은 값을 bash 변수(`$DB_PASSWORD`, Terraform state에 안 남는 런타임 값)에 담아 `psql` 명령에 씁니다.
* `rds_proxy`는 Aurora 전용 기능이라 dev에서는 호출하지 않습니다. 대신 `rds_proxy_endpoint` SSM 파라미터에도 `ec2_postgres`의 엔드포인트를 그대로 등록해서, `02-runtime`이 dev/prod 분기 없이 같은 키를 읽게 합니다.
* 스키마 초기화(`db/postgres/00-init.sql`, `01~04-*.sql`)는 이 모듈의 책임이 아닙니다 — PostgreSQL 설치와 마스터 계정 생성까지만 하고, 실제 스키마 적용은 배포 파이프라인/애플리케이션 쪽 몫입니다(로컬 `docker-compose`가 하는 역할과 동일).
