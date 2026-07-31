# Book Eating Lion (책 먹는 사자)

K8s 및 AWS EKS 기반 금융/결제 연동 도서 쇼핑몰 시스템

---

## 📁 폴더 구조

```text
book_eating_lion/
├── .github/                       # CI/CD 자동화 전용 폴더
│   └── workflows/                 # GitHub Actions 파이프라인
│       ├── dev-backend-ci.yml     # [DEV] 백엔드 Spring Boot 빌드 & 테스트
│       ├── dev-frontend-ci.yml    # [DEV] 프론트엔드 React 빌드 & 테스트
│       ├── dev-cd.yml             # [DEV] 개발 서버 자동 배포 (EKS Dev / Docker)
│       ├── main-backend-ci.yml    # [MAIN] 운영 백엔드 검증 파이프라인
│       ├── main-frontend-ci.yml   # [MAIN] 운영 프론트엔드 정적 검사 & 빌드
│       └── main-cd.yml            # [MAIN] AWS EKS 운영 환경 자동 배포
│
├── frontend/                      # React 기반 UI (도서 목록, 장바구니, 결제, 대시보드)
├── backend/                       # Spring Boot API (도서, 주문, 결제, CQRS, S3 연동)
├── k8s/                           # AWS EKS & K8s 매니페스트 (App, Monitoring, Ingress, HPA)
├── k6/                            # k6 성능, 재고 동시성 및 결제 멱등성 부하 테스트
├── db/                            # DDL, DML 및 초기화 SQL 스크립트 (1_demo_data.sql)
├── docs/                          # 기획서, 아키텍처 다이어그램, IAM/SG 명세, ERD, API 컬렉션
│
├── .env.example                   # 환경 변수 설정 템플릿
├── .gitignore                     # Git tracking 제외 대상 목록
└── docker-compose.yml             # 로컬 개발용 통합 컨테이너 환경
```

---

## 🚀 현재 구현된 핵심 기능 목록

---

## 📮 Postman API 테스트 가이드

---

## ⚙️ 환경 변수 관리

- **로컬 개발**: `application.yml` 템플릿 기본값(`localhost:3306`) 활용
- **AWS S3 및 데이터베이스 환경 변수**

---

## 🐳 배포 및 실행 (Docker Compose)

```bash
# 컨테이너 및 볼륨 초기화 재기동
docker compose down -v && docker compose up --build -d
```