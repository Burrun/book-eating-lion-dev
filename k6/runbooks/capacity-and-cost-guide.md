# 용량 한계 탐색 + 가성비 비교 가이드

목표: 반복적으로 부하테스트를 돌려가며 "**언제 터지는가**"와 "**증량할 때 어떤 옵션이
가성비가 좋은가**"를 데이터로 쌓아서, 인프라 변경 여부를 결정하는 근거 문서로 계속
갱신한다. `k6/tools/merge-results.js`가 만드는 `comparison.csv`가 이 문서의 원자재다.

---

## 1. "터지는 지점"을 찾는 방법 — 시나리오별로 다르다

### 07-connection-saturation — 이미 계단식으로 설계돼 있다

`ramping-arrival-rate`로 10 → 50 → 150 → 300 → 500 req/s로 단계적으로 올린다.
결과 JSON(`k6_summary.metrics.db_connection_errors`)과 콘솔 로그를 같이 보면서
**어느 stage 구간부터 `db_connection_errors`가 0에서 증가로 바뀌는지**가 곧 한계점이다.
`k8s/order/hpa.yaml` 주석 기준 이론상 300 커넥션(=maxReplicas 30 × pool-size 10) 근처가
후보 — 실제로 그 근처인지 이 스크립트로 확인하는 것.

### 01-traffic-spike — 반복 실행으로 타겟을 올려가며 찾는다

이제 `SPIKE_TARGET_VUS` 환경변수로 목표 VU를 뺐다(기존엔 5000 고정). 5000에서
threshold(`p(95)<500ms`, `에러율<0.1%`)를 통과했다면 8000, 12000... 으로 올려가며
**threshold가 깨지기 시작하는 지점**을 찾는다. `RUN_LABEL`에 값을 반영해서 결과를
구분할 것(`RUN_LABEL=spike-8000`).

```bash
for target in 5000 8000 12000 16000; do
  k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=spike-$target \
    -e SPIKE_TARGET_VUS=$target \
    -e CATALOG_URL=https://<API_HOST> \
    scenarios/01-traffic-spike.js
done
```

### 03/05 — "터지는 지점"이 아니라 "격리/정합성이 깨지는지"가 관찰 대상

이 둘은 원래 목적이 한계 탐색이 아니다(05는 오버셀링 0건 확인, 03은 장애 격리 확인).
용량 한계를 보고 싶으면 07 패턴을 빌려 VU 수를 단계적으로 올리는 변형을 만들어야
한다 — 필요해지면 알려주면 만들어줄 수 있다.

---

## 2. 증량 실험 매트릭스 — 이 프로젝트에서 실제로 바꿀 수 있는 손잡이

`terraform/` 코드에서 확인한, 지금 바로 바꿔서 비교할 수 있는 변수들이다. 값을 바꾸고
`terraform apply` → 같은 k6 시나리오를 `RUN_LABEL`만 바꿔 재실행 → `comparison.csv`에서
비교.

| 손잡이 | 파일 | 현재값 | 비교해볼 후보 | 영향받는 시나리오 |
|---|---|---|---|---|
| Karpenter 워크로드 노드 타입 | `terraform/modules/compute/karpenter/variables.tf` `instance_types` | `[t3.medium, t3.large, m6i.large]` | 후보군에서 빼거나 `c6i.*` 계열 추가 | 01, 전체 |
| catalog HPA 상한 | `k8s/catalog/hpa.yaml` `maxReplicas` | 20 | 30, 40 | 01, 02 |
| order HPA 상한 | `k8s/order/hpa.yaml` `maxReplicas` | 30 | 50 | 05, 07 |
| ElastiCache 노드 타입 | `terraform/environments/dev/01-data/terraform.tfvars` `valkey_node_type` | `cache.t4g.medium` | `cache.t4g.large`, `cache.r6g.large` | 02(캐시 구현 이후) |
| Aurora ACU 상한(prod) | `terraform/modules/data/aurora_pg/main.tf` | `db.serverless` | min/max ACU 값 조정 | 05, 07 |

`RUN_LABEL` 네이밍 제안: `{손잡이}-{값}` 형태로 통일 — 예: `hpa-order-max50`,
`node-c6ixlarge`, `valkey-r6glarge`. `comparison.csv`에서 `run_label`로 바로 필터링/피벗이
되게 하려는 목적이다.

---

## 3. 단가표 (ap-northeast-2, On-Demand, AWS Pricing API로 2026-08-28 조회)

이 프로젝트가 실제로 쓰는/후보인 리소스만 추렸다. **Aurora Serverless v2 ACU-hr 단가는
API로 정확히 못 뽑아서 비워뒀다** — [AWS Pricing Calculator](https://calculator.aws)에서
`Aurora PostgreSQL Serverless v2`, 리전 `Asia Pacific (Seoul)`로 직접 확인해서 채울 것.

| 리소스 | 단가(USD) | 비고 |
|---|---|---|
| EC2 t3.medium (On-Demand Linux) | $0.052/hr | 현재 EKS 시스템 노드그룹 |
| EC2 t3.large | $0.104/hr | Karpenter 후보 |
| EC2 m6i.large | $0.118/hr | Karpenter 후보 |
| EC2 c6i.large | $0.096/hr | 참고용(현재 후보군엔 없음) |
| EC2 c6i.xlarge | $0.192/hr | 참고용 |
| ElastiCache cache.t4g.medium | $0.075/hr | 현재 Valkey 노드 |
| NAT Gateway | $0.059/hr + $0.059/GB 처리 | AI 서비스의 Bedrock 호출 등 외부 egress가 여기로 나간다 — 03처럼 AI 트래픽이 많은 시나리오는 NAT 데이터 비용도 같이 늘어난다 |
| Aurora Serverless v2 | **확인 필요**(ACU-hr) | prod 전용. dev는 EC2 Postgres라 이 표 대상 아님 |

메모: k6 자체(부하생성기) 비용은 이 표에 안 넣었다 — 로컬에서 돌리면 $0, EC2로 돌리면
`ec2-loadtest-setup.md`의 인스턴스 비용을 별도로 더할 것.

---

## 4. Excel로 정리하기

1. `node k6/tools/merge-results.js` → `k6/results/comparison.csv`.
2. Excel에서 열고, 같은 파일에 **`cost` 시트**를 하나 추가해서 §3 단가표 + §2에서 실제로
   테스트한 `run_label` → 구성 설명 → 시간당 비용을 직접 채운다. 예:

   | run_label | 구성 | 노드 시간당 비용 | 비고 |
   |---|---|---|---|
   | baseline | t3.medium × 평균 4개 | $0.208/hr | HPA 스케일아웃 평균 replica 기준 |
   | node-t3large | t3.large × 평균 3개 | $0.312/hr | replica는 줄지만 단가는 오름 |

3. `comparison.csv` 시트에서 `run_label`을 키로 `cost` 시트를 VLOOKUP/XLOOKUP해서
   "시간당 비용" 열을 만들고, `metric.http_reqs.count`(처리한 요청 수)를 나눠
   **"요청 1,000건당 비용"** 같은 가성비 지표를 계산한다.
4. 피벗 테이블: 행=`scenario`+`target_env`, 열=`run_label`, 값=`metric.http_req_duration.p(95)`,
   `metric.http_req_failed.rate`, 방금 만든 가성비 열. 이러면 "어느 구성이 지연시간 대비
   비용이 제일 좋은가"가 한 표에서 보인다.

## 5. 이 문서 자체를 어떻게 갱신할까

- 인프라를 바꿀 때마다(§2 매트릭스에 손잡이 하나를 건드릴 때마다) 이 표에 행을 추가하고
  실제 실행한 `RUN_LABEL`, 그날 얻은 `comparison.csv`의 핵심 수치(p95, 에러율, 가성비)를
  요약해서 아래 "실험 기록" 섹션에 누적한다.
- `comparison.csv` 원본은 git에 커밋하지 않는다(README 권장사항 — 매번 결과가 다름).
  대신 이 문서의 "실험 기록"에 **요약값**만 남겨서 히스토리를 보존한다.

### 실험 기록 (직접 채워나갈 것)

| 날짜 | 시나리오 | run_label | 핵심 수치 | 결론 |
|---|---|---|---|---|
| | | | | |
