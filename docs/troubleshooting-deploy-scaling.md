# 트러블슈팅 — 배포 · 오토스케일링 (HPA · ResourceQuota · 자동 롤백)

> dev와 prod가 **같은 EKS 클러스터(integrated) · 같은 Karpenter 노드풀**을 네임스페이스로 나눠 쓰면서 겪은 문제들이다.
> 이 영역의 사고는 연쇄된다: **HPA가 폭주 → Quota가 꽉 참 → 롤링 업데이트가 새 파드를 못 만듦 → `rollout status` 타임아웃 → main-cd가 방금 배포한 이미지를 자동 롤백.**
> 코드는 멀쩡한데 배포만 계속 되돌아간다면 이 문서부터 본다.

---

## 1. metrics-server가 없어서 HPA가 전부 `<unknown>`이었다

**상태**: 해결 · PR #122 (`cd3d44f`, 2026-08-31)

### 증상

```text
# (당시 상태를 재구성한 예시)
$ kubectl get hpa -n prod
NAME          TARGETS                  MINPODS   MAXPODS   REPLICAS
catalog-hpa   <unknown>/70%, …         2         20        2
```

`kubectl top`도 실패. 부하를 걸어도 파드가 늘지 않았다.

### 원인

`metrics.k8s.io` API를 제공하는 **metrics-server addon이 `eks_cluster` 모듈에 없었다**(vpc-cni / kube-proxy / coredns / cloudwatch만 관리 중). CPU·메모리 기반 HPA는 전부 목표값을 읽지 못했다.

### 해결

- `aws_eks_addon "metrics_server"` 추가. 시스템 노드그룹의 `CriticalAddonsOnly` taint를 견디도록 coredns와 같은 toleration 부여(같은 taint 때문에 cloudwatch addon이 못 뜬 전례가 있다 — 인프라-트러블슈팅 ㉑).
- CLI로 먼저 addon을 깐 클러스터는 apply 전에 `terraform import`가 1회 필요하다.

### 알아둘 것

`ai-bot` HPA는 **커스텀 메트릭**(`http_server_requests_active`)이라 metrics-server로는 해결되지 않는다. Prometheus Adapter 또는 KEDA가 있어야 하고, 현재 클러스터에는 없다 — 그래서 `ai-bot`의 실제 방어선은 Bulkhead 격리다.

---

## 2. catalog가 트래픽 없이 20/20까지 스케일아웃했다

**상태**: 해결 · `1497555` (2026-09-02)

### 증상

prod `catalog`가 **CPU 4%로 유휴인데** `maxReplicas`(20)까지 늘었고, 이 파드들을 올리려고 Karpenter가 노드 9대를 띄웠다.

### 원인

catalog HPA는 CPU 70% **또는 메모리 80%**로 확장한다. 그런데 Spring Boot 기동 직후 **유휴 메모리가 실측 470~537Mi**로, 메모리 request(512Mi)보다 컸다.

- 트래픽이 없어도 메모리 사용률이 항상 100% 근처 → 목표 80% 초과
- 파드를 늘려도 새 파드 역시 곧장 100% → **스케일업이 멈추지 않음**

메모리는 CPU와 달리 부하가 빠져도 줄지 않는다(JVM은 힙을 잘 반납하지 않는다). request가 유휴 사용량보다 작으면 메모리 기반 HPA는 발산한다.

### 해결

메모리 request를 512Mi → **768Mi**로 올려, 80% 목표(614Mi)가 실측 유휴치 위로 오게 했다.

### 진단이 늦어진 이유

당시 한 팀원 계정만 EKS 관리자 access entry에 있어 다른 계정의 `kubectl`이 전부 401이었다. 같은 커밋에서 `admin_principal_arns`에 추가했다 — **장애 대응 전에 누가 클러스터를 볼 수 있는지부터** 확인해야 한다.

---

## 3. 배포할 때마다 HPA가 튀어서 main-cd가 방금 배포한 걸 되돌렸다

**상태**: 해결 · `3ecd4ca` (2026-09-02)

### 증상

catalog 배포 중 `rollout status`가 600초 타임아웃 → main-cd의 `Rollback on failure`가 **방금 배포한 이미지를 되돌림.** 코드에는 문제가 없었다.

### 원인

HPA 기본 scale-up은 **안정화 창이 0초**라 매 reconcile마다 즉시 반응한다. 롤링 업데이트 중에는 구/신 파드가 섞여 지표가 출렁이고, HPA가 그걸 보고 2 → 8까지 밀어 올렸다. 롤아웃이 따라가야 할 목표가 계속 늘어나 제시간에 끝나지 못했다.

### 해결

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 60
    policies:
      - type: Pods
        value: 2           # 60초당 최대 2 파드
        periodSeconds: 60
```

prod `CATALOG_HPA_MAX_REPLICAS`도 20 → 10으로 낮췄다.

### 관련: 롤아웃 시간은 replica 수에 비례한다

모든 서비스가 `maxSurge: 1 / maxUnavailable: 0`(무중단, 완전 직렬 교체)이다. catalog 파드가 Ready까지 실측 **~78초**라, HPA가 8개로 올려둔 상태면 `8 × 78 = 624초`로 600초 타임아웃을 넘긴다(2026-09-02 prod). 파드는 전부 정상이었고 느렸을 뿐이다. 타임아웃을 replica 수에 맞게 조정하는 수정(`c0de686`)은 현재 `dev` 브랜치에만 있다.

---

## 4. ResourceQuota가 꽉 차서 롤링 업데이트가 새 파드를 하나도 못 만들었다

**상태**: 해결 · `aa0cc0b` (2026-08-31), `a0cca58` (2026-09-02)

### 배경

dev와 prod는 같은 노드풀을 쓴다. dev에서 부하 테스트를 하다 prod가 스케줄될 자리를 잃지 않도록 네임스페이스별 **ResourceQuota**(총량 상한)와 **PriorityClass**(`dev-workload` 100 / `prod-workload` 1000)를 추가했다(`01ade46`).

### 증상

dev에서 네임스페이스 경합 부하 테스트(`k6/scenarios/08-namespace-contention.js`)를 돌리자 catalog가 20/20까지 차서 quota를 다 먹었다. 그 상태에서 main-cd가 배포를 시도하자:

```text
FailedCreate: exceeded quota
```

롤링 업데이트는 새 파드 1개가 **먼저** 떠야 시작되는데(`maxSurge: 1`), 그 1개가 admission에서 거절됐다. 두 번 연속 progress deadline 초과로 실패하고 자동 롤백됐다(2026-08-31).

### 원인

**Quota < HPA가 요구할 수 있는 양.** HPA가 천장까지 차면 배포 자체가 불가능해지는 상태였다. 게다가 dev와 prod의 `maxReplicas`가 같았다.

### 해결

1. HPA 상한을 환경별로 분리(`scripts/load-deployment-config.sh` → `envsubst`).

   | | catalog | order |
   | --- | --- | --- |
   | dev | 6 | 8 |
   | prod | 10 | 30 |

2. Quota도 환경별 값으로 분리하고, 불변식을 두었다.

   ```text
   quota ≥ Σ(maxReplicas × 파드 요청량) + 롤링 서지 여유
   ```

### 재발 방지

HPA `maxReplicas`나 파드 `resources`를 바꿀 때는 **같은 PR에서 quota(`QUOTA_*`)를 다시 계산한다.**

---

## 5. 배포 파이프라인이 "아무 일도 안 일어나는" 형태로 고장 났다

**상태**: 해결 · `ec8e1c6`, `ae7ab44`, `4ca65e5` 외 (2026-08-31 ~ 09-02)

실패가 아니라 **조용히 건너뛰는** 고장이라 늦게 발견됐다.

| 증상 | 원인 | 대응 |
| --- | --- | --- |
| 워크플로 파일만 고친 push에 order가 배포 대상으로 잡힘 | `paths-filter`의 `base`를 안 주면 기본 브랜치(main)와 비교 → dev가 main보다 훨씬 앞서 있어 거의 항상 4개 서비스 전부 "변경됨" | **이번 push 직전 커밋 SHA**를 `base`로 명시(`fetch-depth: 2`) |
| `git fetch origin HEAD~1` → invalid refspec (exit 128) | `paths-filter`의 base/head는 refspec으로도 쓰여서 `HEAD~1` 같은 revision 표현식을 못 받음 | 앞 스텝에서 `git rev-parse`로 SHA를 풀어서 넘김 |
| 배포 스크립트를 고쳤는데 CD가 안 돎 | main-cd는 스스로 트리거되지 않고 **CI 성공의 `workflow_run`**으로만 깨어난다. CI의 `paths`에 `scripts/**`와 `main-cd.yml`이 없었다 | Backend/Frontend CI의 `paths`에 배포 경로 추가 |
| 롤백 워크플로의 git revert가 초록불인데 안 됨 | `inputs.revert_git == true`(boolean 비교) — `workflow_dispatch` 입력은 boolean 타입이어도 문자열이라 항상 false | `== 'true'`로 문자열 비교 |
| 롤백 워크플로가 머지 커밋에서 죽음 | `git revert`가 머지 커밋에 `-m` 없이 실패 | `git revert -m 1`, 충돌 시 abort 후 배포 롤백은 계속 |

---

## 부록 — 롤아웃이 계속 실패할 때 확인 순서

```bash
NS=prod   # 또는 dev
kubectl -n $NS get hpa                                   # TARGETS 가 <unknown> 인가 (§1), max 에 붙어 있나 (§2)
kubectl -n $NS describe resourcequota                    # used 가 hard 에 닿았나 (§4)
kubectl -n $NS get events --sort-by=.lastTimestamp | grep -E "FailedCreate|exceeded quota|FailedScheduling"
kubectl -n $NS rollout status deployment/catalog-deployment --timeout=60s
kubectl -n $NS top pods                                  # 유휴 메모리가 request 에 가까운가 (§2)
```

그 밖의 인프라 배포 이력(OIDC, Karpenter, IRSA, CloudFront, Cognito 등)은 [`인프라-트러블슈팅.md`](개발%20문서/인프라-트러블슈팅.md).
