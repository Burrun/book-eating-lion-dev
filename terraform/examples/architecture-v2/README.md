# AWS Architecture v2 Terraform examples

이 디렉터리는 목표 아키텍처 검토용 예시다. `.example` 파일 자체는 자동 실행되지 않는다.

실제로 실행 가능한 GitHub Actions 워크플로는 다음 경로로 승격했다.

- `.github/workflows/terraform-apply.yml`
- `.github/workflows/terraform-destroy.yml`

Apply에서 `deploy_modules=true`를 선택하면 Terraform 적용 완료 후
`.github/workflows/main-cd.yml`을 호출해 Catalog, Order, Member, AI 모듈을 배포한다.

## 현재 코드와 목표 구조 비교

| 항목 | 현재 Terraform | 목표/예시 |
|---|---|---|
| 네트워크 | 2개 AZ, Public/App/Data Subnet, AZ별 NAT Gateway | 일치 |
| 애플리케이션 | EKS, Karpenter, Ingress LB | 대체로 일치. 다이어그램의 ALB와 실제 모듈이 만든 LB 유형은 적용 전 재확인 필요 |
| 데이터베이스 | dev: EC2 PostgreSQL, prod: Aurora Writer/Reader + RDS Proxy | 구조 유지 |
| 캐시 | ElastiCache for Valkey Primary/Replica | 일치 |
| DB 접속 주소 | SSM 값을 GitHub Variables로 복사해 Pod 환경변수로 주입 | Private Route 53 FQDN을 고정 주소로 사용 |
| CloudFront | `02-runtime` 상태에 포함 | 선택적으로 `03-deploy` 별도 상태로 이동 |
| Terraform Actions | 앱 배포 워크플로 중심 | 수동 Apply/Destroy 예시 추가 |

## 권장 내부 DB 주소

- dev writer/reader: `writer.db.dev.lion.internal`, `reader.db.dev.lion.internal`
- prod writer: `writer.db.prod.lion.internal` → RDS Proxy
- prod reader: `reader.db.prod.lion.internal` → Aurora Reader endpoint

인터넷에 공개하지 않는 데이터베이스이므로 Public Hosted Zone이 아니라 VPC에 연결된 **Route 53 Private Hosted Zone**을 사용한다. FQDN은 주소를 안정화하지만 비밀번호를 대신하지 않으므로 DB 사용자명과 비밀번호는 Secrets Manager 또는 Kubernetes Secret에서 계속 관리한다.

## CloudFront 분리 시 주의

`02-runtime`에서 CloudFront 코드를 삭제하고 `03-deploy`에 복사하는 것만으로는 안 된다. 기존 Terraform state의 리소스 주소가 바뀌므로 `terraform state mv` 또는 import 계획이 필요하다. 이를 생략하면 Terraform이 기존 배포를 삭제하고 새로 만들거나 중복 생성을 시도할 수 있다.

분리하면 EKS/Ingress만 변경할 때 CloudFront를 기다리지 않아 runtime 작업은 빨라진다. 다만 전체 인프라를 처음부터 생성하거나 모두 삭제하는 총시간 자체가 크게 줄어드는 것은 아니다.

## 예시 파일

- `database-private-dns.tf.example`: DB용 Private Hosted Zone과 writer/reader 레코드
- `cloudfront-deploy-layer.tf.example`: CloudFront를 별도 deploy state에서 호출하는 구조
- `terraform-apply.yml.example`: 계층 순서대로 적용하는 수동 GitHub Actions
- `terraform-destroy.yml.example`: 역순으로 삭제하는 수동 GitHub Actions

실제 반영 전에는 예시를 그대로 복사하지 말고 기존 state와 리소스 이름을 기준으로 migration plan을 먼저 작성한다.
