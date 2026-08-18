# 장애/배포 주입 명령어 (환경별)

`scenarios/03-pod-failure.js`, `scenarios/04-rolling-deploy.js`는 부하만 만든다. 아래 명령을 **별도 터미널에서, 부하가 시작되고 60~90초 지난 시점에** 직접 실행해야 실제 장애/배포가 재현된다.

## EC2 단일 배포 (docker-compose)

`docker-compose.yml`의 서비스들은 `restart` 정책이 지정되어 있지 않다 — 즉 컨테이너가 죽으면 **자동으로 재시작되지 않는다**. 이 사실 자체가 "EC2/Docker 단독은 자가 치유가 없다"(기획서 §4-5)의 실측 증거가 된다.

```bash
# 장애 유발 (03-pod-failure.js 대상)
docker stop msa-ai            # 또는 msa-catalog

# 관찰: docker ps 로 컨테이너가 다시 안 뜨는지 확인 (자가 치유 부재 증명)
docker ps -a | grep msa-ai

# 복구 (수동 — 이게 핵심이다: 사람이 개입해야 복구된다)
docker start msa-ai

# 무중단 배포 재현 (04-rolling-deploy.js 대상)
# stop → 재시작 사이에 접속이 끊긴다. k6 결과의 5xx/타임아웃 스파이크가 그 증거.
docker compose build catalog
docker compose up -d --no-deps catalog
```

## EKS MSA 배포

```bash
# 장애 유발 (03-pod-failure.js 대상) — Self-Healing 관찰
kubectl delete pod -n lion-app -l app=ai-rag --force
# 또는
kubectl delete pod -n lion-app -l app=catalog-service --force

# 관찰: 새 Pod가 몇 초 만에 재생성되는지
kubectl get pods -n lion-app -l app=ai-rag -w

# 무중단 배포 재현 (04-rolling-deploy.js 대상)
# k8s/catalog/deployment.yaml 의 RollingUpdate(maxSurge:1, maxUnavailable:0) 설정이
# 이론상 다운타임 0초를 보장한다 — k6 결과의 5xx가 0건인지가 그 증거.
kubectl rollout restart deployment/catalog-deployment -n lion-app
kubectl rollout status deployment/catalog-deployment -n lion-app
```

## 기록해야 할 것

두 환경 모두에서 아래를 타임스탬프와 함께 기록해 `tools/merge-results.js` 결과와 나란히 놓고 비교한다:

- 장애 유발 명령 실행 시각
- 서비스가 다시 정상 응답하기 시작한 시각(복구 소요 시간)
- 그 사이 k6가 관찰한 에러율/타임아웃 건수 (스크립트의 JSON 결과에 포함됨)
