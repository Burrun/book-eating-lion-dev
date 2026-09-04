# Q&A

## 1. AWS 비용 절감

### Q1. AWS 비용을 줄이기 위해 어떤 시도들을 했나요?

크게 네 방향으로 접근했습니다.

1. **Terraform 온오프 자동화** — 안 쓰는 시간엔 값비싼 계층(EKS/Karpenter/ALB)만 골라서
   지웠다가 필요할 때 한 번에 복구할 수 있는 apply/destroy 파이프라인을 만들었습니다.
2. **클러스터 통합** — dev/prod를 각각 별도 EKS로 띄우던 걸 EKS 클러스터 1개(네임스페이스로
   분리)로 합쳤고, DB도 별도 클러스터로 쪼갤 계획이었던 걸 스키마·계정 분리로 대체했습니다.
3. **EC2 자체 설치 DB vs RDS vs Aurora 비교** — "관리형이 편하지만 비싸다"를 감으로 정하지
   않고, 같은 스펙(t4g.micro/db.t4g.micro, gp3 30GB)으로 맞춘 두 모듈을 나란히 만들어
   실제로 갈아타 보며 판단했습니다.
4. **base(상시 유지비) 최소화** — Karpenter spot 활용, 유휴 노드 즉시 축소, 시스템
   노드그룹 최소 고정, reader/replica 기본값 0 등 "평소에 얼마가 나가는가"를 계속 깎았습니다.

아래 항목에서 각각을 PR/커밋 근거와 함께 풀어서 설명합니다.

---

### Q2. Terraform으로 인프라를 켜고 끄는 자동화는 어떻게 만들었나요?

**계층을 "거의 안 바뀌는 것"과 "자주 바뀌는 것"으로 쪼갠 게 전제**입니다
(`docs/개발 문서/TERRAFORM_STRUCTURE.md` §1).

```
00-base   → VPC/NAT/ACM/ECR 등 네트워크 토대       (고정자원, 거의 안 지움)
01-data   → Aurora/RDS/EC2-Postgres, Valkey, Cognito (고정자원, 데이터 유실 위험)
02-runtime → EKS, Karpenter, ALB/Ingress            (비고정자원, 자주 만들고 지움)
03-deploy → CloudFront (prod 전용)
```

state 파일이 계층별로 완전히 분리돼 있어서, 가장 비싼 `02-runtime`(EKS 컨트롤 플레인 +
노드)만 골라서 destroy 해도 VPC나 DB의 state에는 손이 안 닿습니다. 이 구조 위에
GitHub Actions workflow 2개로 온오프를 자동화했습니다(PR #91 `feat: Terraform apply/destroy
and EKS module deployment pipeline`, PR #93, 이후 PR #120/#125로 계속 다듬음).

* **`terraform-apply.yml`**(`.github/workflows/terraform-apply.yml`) — `workflow_dispatch`로
  환경(dev/prod/integrated)과 계층을 선택해서 apply합니다. `confirmation` 입력에
  `APPLY-{환경}`을 정확히 쳐야만 job이 도는 이중 안전장치를 걸었고, `02-runtime`을 처음부터
  다시 세울 때는 EKS 컨트롤 플레인 → Karpenter Helm(CRD 등록) → 전체 리소스 순서로
  3단계 부트스트랩을 자동 수행합니다.
* **`terraform-destroy.yml`** — 반대로 `DESTROY-{환경}`을 입력해야 지워집니다. DB에
  `deletion_protection`이 걸려 있으면 destroy가 중간에 멎기 때문에, `01-data`를 지울 땐
  보호 플래그를 먼저 끄는 apply를 선행하고 나서 destroy합니다.
* 이렇게 만들어두면 실습/부하테스트가 끝난 뒤 EKS+노드만 지워서 그 시간만큼 컴퓨트 비용을
  aggregate로 아낄 수 있고, `terraform-apply.yml`을 한 번 실행하면 원래 상태로 복구됩니다.
  실제로 PR #74에서 이 반복(destroy→apply)을 연습하는 `sandbox/02-runtime` 계층을 따로
  만들어서 검증했습니다.

**DB까지 온오프하려던 시도도 있었습니다.** dev가 아직 EC2 자체 설치 PostgreSQL을 쓰던
시절, 그 인스턴스 하나만 야간에 자동으로 stop/start하는 `db-power.yml`을 위한 전용 IAM
role(`github-actions-lion-team3-db-power`, EC2 인스턴스 하나로만 권한을 좁힌 최소 권한
role)을 Terraform으로 먼저 만들었습니다(PR #125, 커밋 `432a26b`). 그런데 같은 날
integrated 환경의 DB를 EC2에서 관리형 RDS로 옮기면서(Q4 참고, PR #130) "EC2 인스턴스
하나를 stop/start"한다는 전제 자체가 사라졌고, 실제 워크플로 파일은 끝내 커밋되지 않은 채
role 코드만 백지화했습니다(커밋 `59c347e`). 시도했다가 상위 결정(EC2→RDS 전환)에 따라
자연스럽게 폐기된 케이스입니다.

---

### Q3. 클러스터를 하나로 합친 이유와 방법은요? ("단일 클러스터화")

두 층위에서 있었습니다.

**① EKS 클러스터 통합.** 원래 설계(split)는 dev/prod가 VPC부터 EKS까지 완전히 독립이었는데
(`terraform/environments/{dev,prod}/...`), EKS는 클러스터당 컨트롤 플레인 자체가 시간당
과금이라 두 벌을 상시 띄우면 그 비용이 통째로 두 배입니다. 그래서 `lion-team3-integrated`
클러스터 하나 안에 `dev`/`prod` **네임스페이스**로 나누는 integrated 모델로 전환했습니다
(커밋 `5b74b9b` "integrated 단일 클러스터 전환", PR #98 "integrated 테라폼 코드 추가",
정리 문서는 `terraform/인프라구성명세-v2.md` §3.1).

한 클러스터를 공유하면 "dev가 prod 자원을 굶길 수 있다"는 위험이 새로 생기는데, 이건
격리 장치로 따로 막았습니다.

* `ResourceQuota` + `PriorityClass`로 네임스페이스별 자원 상한과 스케줄링 우선순위를
  나눔 (커밋 `01ade46`).
* dev/prod HPA `maxReplicas`를 분리해서 dev 부하가 prod 오토스케일 여력을 잠식하지 않게 함
  (커밋 `aa0cc0b`).
* dev 도메인(`dev.ajttk.com`) 소유권을 `enable_dev_cutover`/`enable_edge_routing`
  변수로 여닫아서, 언제든 split(완전 분리) 구조로 되돌릴 수 있는 퇴로를 남겨둠
  (`terraform-apply.yml`의 `deployment_mode`/`confirm_edge_handoff` 입력, §4).

**② DB 클러스터 통합.** 원래는 `ai_db`(RAG 벡터 저장)만 pgvector가 필요해서 별도 Aurora
Serverless v2 클러스터로 뺄 계획이었습니다. 이후 벡터를 전부 S3 Vectors로 옮기면서
① pgvector가 필요하다는 이유와 ② auto-pause로 그 클러스터만 과금을 따로 줄인다는
이유가 둘 다 없어졌고, "클러스터를 하나 더 띄울 값을 못 한다"는 결론으로 PostgreSQL
클러스터 1개 / 스키마 4개(`catalog_db`, `order_db`, `member_db`, `ai_db`) / 서비스별
계정 4개로 합쳤습니다(`README.md` §핵심 설계 결정 ③). 경계는 클러스터가 아니라 계정
권한(`catalog_svc`로 `order_db` 조회 시 `permission denied for schema`)이 만들어서,
격리 수준을 낮추지 않고도 인스턴스 비용만 줄인 구조입니다.

---

### Q4. EC2 자체 설치 DB와 RDS를 어떻게 비교했나요?

세 가지 DB 구성을 스펙 단계별로 나란히 두고 있습니다.

| 계층 | 모듈 | 구성 | 용도 |
| --- | --- | --- | --- |
| dev (split, 현재 미운영) | `modules/dev_tools/ec2_postgres` | EC2 `t4g.micro` + gp3 30GB, PostgreSQL 직접 설치 | 상시 켜두는 dev를 최저 비용으로 |
| integrated (현재 운영) | `modules/data/rds_postgres` | RDS 단일 인스턴스 `db.t4g.micro` + gp3 30GB | EC2와 "동급 스펙"으로 맞춘 관리형 비교 대상 |
| prod (split, 미구축) | `modules/data/aurora_pg` | Aurora Multi-AZ, 스토리지 3AZ 6중 복제 | 실운영 등급 |

핵심은 `rds_postgres` 모듈을 Aurora가 아니라 **EC2와 같은 인스턴스 등급으로 일부러
맞춰서** 만들었다는 점입니다(`terraform/modules/data/rds_postgres/main.tf` 상단 주석).

> "aurora_pg와 나란히 두는 이유: integrated prod는 'EC2 자체 설치 Postgres → 관리형 RDS'
> 전환의 비교 대상이라 Aurora가 아니라 EC2와 같은 급의 단일 인스턴스여야 한다
> (t4g.micro ↔ db.t4g.micro, gp3 30GB). Aurora로 가면 스토리지 아키텍처부터 달라져
> 그 비교가 성립하지 않는다."

그래서 얻은 비교 결과는 이렇습니다.

* **EC2 자체 설치** — 인스턴스 비용만 내면 되는 최저가지만, 패치/백업/장애 복구를 직접
  운영해야 합니다. `data_subnet`은 완전 격리(NAT 없음)라 패키지 설치조차 안 돼서, 이
  인스턴스만 예외적으로 `app_subnet`에 두되 보안그룹은 데이터 계층과 동일하게 좁혔고,
  SSH는 아예 열지 않고 SSM Session Manager로만 접속하게 했습니다
  (`terraform/인프라구성명세.md`, TERRAFORM_STRUCTURE.md §6.3).
* **RDS (단일 인스턴스)** — 같은 인스턴스 등급인데도 자동 백업/PITR, 마이너 버전 자동
  업그레이드, Secrets Manager 연동 마스터 비밀번호 관리를 관리형으로 받습니다. 그
  대가로 EC2보다 시간당 단가가 조금 더 비쌉니다. dev와 prod를 한 인스턴스에서 같이
  운영하는 integrated 모델에서는 "직접 관리 부담을 지는 것보다 이 정도 웃돈이 낫다"는
  판단으로 EC2에서 RDS로 실제 전환했습니다(PR #130 `test/integrated rds migration`).
* **Aurora Multi-AZ** — 스토리지가 항상 3AZ에 자동 복제되고 Reader 인스턴스를 늘릴 수
  있는 실운영 등급이라 비용이 가장 높습니다. 아직 실사용자가 붙는 prod가 별도로
  구축되지 않아 split 코드로만 존재하고, 실제 운영에는 아직 적용하지 않았습니다.

즉 "무조건 관리형이 좋다/EC2가 싸다"로 정하지 않고, 같은 스펙에서 EC2와 RDS를 실제로
운영 전환해보며 비용과 운영 부담을 저울질했다는 게 이 비교의 핵심입니다.

---

### Q5. 상시 유지비(base cost)를 줄이기 위한 세부 Terraform 설정은요?

"환경을 켜 두기만 해도 나가는 돈"을 계속 낮추는 방향으로 다음을 적용했습니다.

* **Karpenter spot 활용 + 즉시 축소.** NodePool이 `capacity-type`을 `spot`과
  `on-demand` 둘 다 허용해서 가능하면 더 싼 spot을 우선 쓰고, `consolidationPolicy =
  WhenEmptyOrUnderutilized` + `consolidateAfter = 30s`로 유휴 노드를 30초 만에
  회수합니다(`terraform/modules/compute/karpenter/main.tf`). 앱 워크로드가 없으면
  노드 자체가 0대까지 줄어드는 구조라, "미리 사둔 용량"에 돈을 내지 않습니다.
* **arm64(Graviton) 시도, 현재는 amd64로 보류.** 한때 NodePool이 `kubernetes.io/arch:
  arm64`만 요구하도록 해서 Graviton(같은 성능에 더 저렴)으로 비용을 줄이려 했는데,
  `main-cd.yml`의 Docker 빌드가 amd64 러너에서 `--platform` 지정 없이 이미지를 만들어
  아키텍처가 안 맞아 크래시루프가 났습니다(`docs/개발 문서/인프라-트러블슈팅.md` 항목).
  지금은 amd64로 고정해뒀고, CI를 buildx 크로스컴파일로 바꾸면 다시 Graviton으로
  전환할 수 있다는 걸 코드 주석에 남겨 다음 개선 여지로 문서화했습니다.
* **시스템 노드그룹은 최소로 고정.** EKS 시스템 노드그룹(`aws_eks_node_group.system`,
  CoreDNS/Karpenter 컨트롤러용)은 `t3.medium` 2대로 `min=max=desired`를 고정해
  더 늘지 않게 했고, 그 외 실제 워크로드는 전부 Karpenter가 필요한 만큼만 붙였다 뗍니다
  (`terraform/modules/compute/eks_cluster/variables.tf`).
* **Reader/Replica 기본값은 0.** Aurora `reader_count`는 초기 단계 기본 0(Writer 1대만),
  dev의 Valkey `replica_count`도 0으로 시작해서(`terraform/environments/dev/01-data/terraform.tfvars`)
  트래픽이 실제로 필요해질 때만 단계적으로 늘리는 정책을 씁니다
  (TERRAFORM_STRUCTURE.md §3.2-1/§3.2-3).
* **고정자원/비고정자원 계층 분리 자체가 비용 설계.** Q2에서 설명한 00-base·01-data
  vs 02-runtime 분리 덕분에, 네트워크·DB 같은 "계속 켜둘 수밖에 없는" 자원과 EKS 같은
  "필요할 때만 켜는" 자원을 따로 다룰 수 있어서 온오프 자동화(Q2)와 이 base 최적화가
  서로 다른 계층에서 독립적으로 동작합니다.

---

## 2. 구독/쿼터 혜택과 DB 기록 실패 시 Fallback

### Q6. 구독권·읽기 쿼터 같은 혜택을 주는데 DB에 기록이 안 되면 어떻게 되나요?

"혜택을 확인하는 조회"와 "혜택을 실제로 기록하는 쓰기"를 다른 원칙으로 다룹니다.

**① 조회(구독 여부 확인) 실패 → 항상 fail-closed(비구독으로 강등).**
AI 쿼터, eBook 전체 열람, 주문 시 중복 구독 검증까지 모두 `member-service`에
Feign으로 구독 상태를 물어보는데, 이 호출이 실패하면(회로 차단기 fallback) 셋 다
"구독 없음"으로 안전하게 강등시킵니다. 근거 없이 혜택 쪽으로 fail-open 하면 조용히
과다 지급되는 사고가 되기 때문입니다(같은 원칙이 세 서비스에 반복 적용돼 있습니다).

```java
// backend/modules/ai/src/main/java/com/bookeatinglion/ai/client/MemberSubscriptionClientFallback.java
log.warn("member-service 구독 상태 조회 실패 — 1배(비구독)로 안전 강등한다. memberId={}", memberId);
return new SubscriptionStatus(memberId, false);
```

```java
// backend/apps/catalog-api/.../MemberSubscriptionFallback.java
log.warn("member-service 구독 상태 조회 실패 — 비구독으로 안전 강등한다. memberId={}", memberId);
return new SubscriptionStatus(memberId, false);
```

다만 이미 확정된 권리(구매 완료 도서의 리뷰 작성 권한, 구매 확정 기반 eBook 열람 등)는
이 실패로 막히지 않습니다 — "구독처럼 매번 다시 물어야 하는 혜택"과 "이미 이벤트로
넘겨받아 로컬에 저장해 둔 권한"을 구분해서, 후자는 member-service가 죽어 있어도
그대로 동작합니다.

**② 결제 전/후로 리스크가 다르면 fail-closed 방향도 다르게 잡습니다.**
주문에서 "이미 구독 중인데 또 결제되는 사고"를 막는 검증은, 조회 자체가 실패하면
아예 주문을 막아버립니다(`SubscriptionCheckFailedException`) — 잘못 막으면 사용자가
재시도하면 그만이지만, 잘못 통과시키면 돈이 이미 나간 뒤에야 문제를 발견하게 되는
비대칭 때문입니다(`backend/modules/order/.../OrderService.java` `rejectIfAlreadySubscribed`).

**③ 결제는 끝났는데 구독 활성화(쓰기) 자체가 실패하면 — 자동 롤백하지 않고 로그만
남깁니다.** 구독권 결제가 확정된 뒤 `member-service`에 구독을 실제로 활성화하는
호출은 주문 트랜잭션이 커밋된 **후**에만 실행되도록 `TransactionSynchronization
.afterCommit()`으로 묶여 있는데, 이 호출 자체가 실패해도 주문을 되돌리지 않습니다.
이미 결제가 끝난 돈을 취소하려면 별도 환불 로직이 필요한데 그게 없기 때문에, 대신
"결제 전에 이미 구독 중인지부터 막는" ②의 사전 검증으로 애초에 이 경로를 덜 타게 하고,
그래도 활성화 자체가 실패하는 드문 경우는 사람이 보고 수동 복구하도록 명시적으로
로그를 남깁니다.

```java
// backend/modules/order/.../OrderService.java
} catch (RuntimeException e) {
    log.error(
        "구독 활성화 실패 - 결제는 이미 확정됐다. 수동 복구 필요."
            + " orderId={}, memberId={}, planType={}",
        orderId, memberId, subscriptionPlanType, e);
}
```

**④ 쿼터 사용량 자체는 DB가 아니라 Redis에 기록하고, Redis 장애는 반대로
fail-open으로 처리합니다.** 일일 RAG 쿼터(`DailyQuota`)는 접근 제어가 아니라
"과금 방어선"이라서, Redis가 죽어 사용량을 확인/기록하지 못하면 요청을 막지 않고
그냥 통과시키되 WARN 로그를 남깁니다. 쿼터가 조용히 꺼진 채로 아무도 모르는 상태가
더 큰 사고라는 판단이고, 같은 파일 안에서 "구독 여부 조회"는 여전히 위 ①번
fail-closed 규칙을 따르므로 한 클래스 안에서도 두 원칙이 의도적으로 공존합니다
(`backend/modules/ai/src/main/java/com/bookeatinglion/ai/wiki/service/DailyQuota.java`).

정리하면, **읽기(혜택 자격 확인)는 못 믿을 땐 손해를 사용자가 지게 하고(fail-closed),
쓰기(이미 지불된 혜택 기록)는 실패해도 이미 받은 돈을 되돌리지 않는 대신 사람이 보는
로그로 남기며, 순수 사용량 카운터(쿼터)는 막았을 때의 반경이 더 크므로 열어 둔다**는
세 갈래 원칙으로 나눠서 다룹니다.
