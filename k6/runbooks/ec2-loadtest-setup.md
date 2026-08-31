# k6 부하생성기 EC2 만들기 / 지우기

`README.md`가 말하는 "별도의 테스트용 EC2"용 — k6 스크립트를 실행할 부하생성기다.
**앱(docker-compose)을 올리는 EC2가 아니다** — 그건 별개(README §「지금 안 되는 것」의
"EC2-single 비교 대상"). 이 EC2는 그냥 k6를 설치해서 대상(EKS dev.ajttk.com 등)을
향해 쏘기만 하는 용도라 프로젝트 VPC 안에 있을 필요가 없다.

이 계정(`061039804626`)은 **여러 팀이 공유**한다(`terraform/인프라구성명세.md` §1) —
Terraform이 관리하지 않는 리소스라도 `Team=Team3` 태그는 반드시 붙일 것. Terraform
state에는 안 넣는다 — 매번 새로 만들고 지우는 임시 리소스라 IaC로 관리할 실익이 없다.

## 0. 사전 확인

```bash
aws sts get-caller-identity   # 리전 ap-northeast-2, 올바른 계정인지 확인
```

## 1. 만들기

```bash
set -e
REGION=ap-northeast-2
MY_IP=$(curl -s ifconfig.me)/32
AMI_ID=$(aws ssm get-parameters --region $REGION \
  --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameters[0].Value' --output text)
VPC_ID=$(aws ec2 describe-vpcs --region $REGION \
  --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
SUBNET_ID=$(aws ec2 describe-subnets --region $REGION \
  --filters Name=vpc-id,Values=$VPC_ID Name=map-public-ip-on-launch,Values=true \
  --query 'Subnets[0].SubnetId' --output text)

# 보안그룹 — SSH는 내 IP에서만, 아웃바운드는 전체 허용(대상이 dev.ajttk.com이든
# EKS API_HOST든 아웃바운드로 나가면 그만이라 인바운드를 열 이유가 없다)
SG_ID=$(aws ec2 create-security-group --region $REGION \
  --group-name lion-team3-dev-loadtest-sg \
  --description "k6 loadtest runner - team3" \
  --vpc-id $VPC_ID \
  --tag-specifications "ResourceType=security-group,Tags=[{Key=Name,Value=lion-team3-dev-loadtest-sg},{Key=Team,Value=Team3},{Key=Project,Value=lion},{Key=Environment,Value=dev}]" \
  --query 'GroupId' --output text)

aws ec2 authorize-security-group-ingress --region $REGION \
  --group-id $SG_ID --protocol tcp --port 22 --cidr $MY_IP

# 키페어 — 이 러너 전용으로 새로 만든다(다른 팀원 개인키에 의존하지 않도록).
# .pem은 로컬에만 남는다, 절대 커밋하지 말 것.
aws ec2 create-key-pair --region $REGION \
  --key-name lion-team3-dev-loadtest-key \
  --query 'KeyMaterial' --output text > lion-team3-dev-loadtest-key.pem
chmod 400 lion-team3-dev-loadtest-key.pem

# 인스턴스 — 일반 시나리오는 t3.medium이면 충분하다. 01-traffic-spike.js처럼
# 5,000 VU 스파이크를 돌릴 땐 CPU/네트워크가 부족할 수 있으니 그때만
# --instance-type c6i.xlarge(또는 그 이상)로 올려서 새로 만들 것.
INSTANCE_ID=$(aws ec2 run-instances --region $REGION \
  --image-id $AMI_ID \
  --instance-type t3.medium \
  --key-name lion-team3-dev-loadtest-key \
  --security-group-ids $SG_ID \
  --subnet-id $SUBNET_ID \
  --associate-public-ip-address \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=lion-team3-dev-loadtest},{Key=Team,Value=Team3},{Key=Project,Value=lion},{Key=Owner,Value=likelion-cloud6-team3},{Key=Environment,Value=dev},{Key=ManagedBy,Value=manual}]" \
  --user-data '#!/bin/bash
dnf install -y git
curl -s https://dl.k6.io/rpm/repo.rpm -o /tmp/k6repo.rpm
rpm -i /tmp/k6repo.rpm || (echo -e "[k6]\nname=k6\nbaseurl=https://dl.k6.io/rpm\nrepo_gpgcheck=1\nenabled=1\ngpgcheck=1\ngpgkey=https://dl.k6.io/key.gpg" > /etc/yum.repos.d/k6.repo)
dnf install -y k6
' \
  --query 'Instances[0].InstanceId' --output text)

echo "InstanceId=$INSTANCE_ID"
aws ec2 wait instance-running --region $REGION --instance-ids $INSTANCE_ID
PUBLIC_IP=$(aws ec2 describe-instances --region $REGION --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "PublicIp=$PUBLIC_IP"
echo "ssh -i lion-team3-dev-loadtest-key.pem ec2-user@$PUBLIC_IP"
```

user-data가 부팅 시 k6를 설치해둔다(수 분 걸릴 수 있음 — SSH 접속 직후 `k6 version`으로
확인). 리포는 private이라 user-data에서 자동 clone은 안 했다 — 접속 후 직접
`git clone`(HTTPS+PAT 또는 `scp`로 `k6/` 디렉터리만 복사)할 것.

## 2. 쓰기

```bash
ssh -i lion-team3-dev-loadtest-key.pem ec2-user@$PUBLIC_IP
k6 version   # 설치 안 됐으면 cloud-init 로그(/var/log/cloud-init-output.log) 확인
```

이후 실행 방법은 `k6/README.md` §4 그대로.

## 3. 지우기 — 반드시 실행

공유 계정이라 안 쓸 때 인스턴스를 켜두면 다른 팀 예산에 영향을 준다. 테스트가
끝나면 바로 정리할 것.

```bash
REGION=ap-northeast-2
aws ec2 terminate-instances --region $REGION --instance-ids $INSTANCE_ID
aws ec2 wait instance-terminated --region $REGION --instance-ids $INSTANCE_ID

# 보안그룹은 인스턴스 종료 후에만 삭제 가능(연결돼 있으면 DependencyViolation)
aws ec2 delete-security-group --region $REGION --group-id $SG_ID

# 키페어는 재사용해도 되면 남겨두고, 완전히 끝났으면:
aws ec2 delete-key-pair --region $REGION --key-name lion-team3-dev-loadtest-key
rm -f lion-team3-dev-loadtest-key.pem
```

다음에 뭘 지워야 할지 잊었으면 태그로 찾는다:

```bash
aws ec2 describe-instances --region ap-northeast-2 \
  --filters "Name=tag:Name,Values=lion-team3-dev-loadtest" "Name=instance-state-name,Values=running,stopped" \
  --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name}' --output table
```
