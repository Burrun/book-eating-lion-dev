#!/usr/bin/env python3
"""
terraform/**/*.tf 의 aws_* 리소스  ->  terraform role 이 실제로 가진 권한 대조

terraform apply/destroy 를 돌리기 전에, 이 코드베이스가 만드는 모든 AWS 리소스
타입이 role 정책에 들어있는지 본다. 없으면 terraform 을 돌리기 전에 죽는다.

이게 있는 이유는 실패 방식 때문이다. 권한 하나가 빠지면 apply/destroy 가 시작은
되고, 10~20분 리소스를 만들거나 지운 뒤 중간에 AccessDenied 로 멈춘다. 그때마다
콘솔에서 그 action 하나만 붙이고 다시 돌리면 다음 리소스에서 또 같은 일이 난다
(cloudwatch:DescribeAlarms 사건). 정책 코드와 배포된 role 이 어긋나 있어도
마찬가지다 - 그래서 코드가 아니라 '배포된 role' 을 읽어서 대조한다.

    python3 scripts/check-terraform-iam-coverage.py github-actions-lion-team3-integrated-terraform

검사 범위는 서비스 접두어(prefix) 단위다. `iam:PassRole` 이 어떤 ARN 으로
좁혀져 있는지 같은 리소스 수준 스코프는 보지 않는다 - 그건 정책 본문에서
직접 관리한다.

terraform/bootstrap 과 terraform/github 는 제외한다. 둘 다 role 이 아니라 사람이
로컬에서 돌리는 계층이고, bootstrap 이 만드는 state 버킷/락 테이블은 role 이
만들면 안 되는 리소스다.
"""

import json
import re
import subprocess
import sys
from pathlib import Path

TERRAFORM_DIR = Path(__file__).resolve().parent.parent / "terraform"
EXCLUDED_DIRS = {"bootstrap", "github"}

RESOURCE_RE = re.compile(r'^resource\s+"([a-z0-9_]+)"', re.MULTILINE)

# 접두어 -> IAM 서비스. 가장 긴 접두어가 이긴다 (aws_cloudwatch_event_ 가
# aws_cloudwatch_ 보다 우선). aws_* 인데 여기 없으면 실패시킨다 - 새 리소스
# 타입을 넣을 때 권한을 같이 생각하게 만드는 게 이 표의 목적이다.
SERVICE_BY_PREFIX = {
    "aws_acm_": "acm",
    "aws_autoscaling_": "autoscaling",
    "aws_cloudfront_": "cloudfront",
    "aws_cloudwatch_event_": "events",
    "aws_cloudwatch_log_": "logs",
    "aws_cloudwatch_": "cloudwatch",
    "aws_cognito_": "cognito-idp",
    "aws_db_": "rds",
    "aws_dynamodb_": "dynamodb",
    "aws_ec2_": "ec2",
    "aws_ecr_": "ecr",
    "aws_eip": "ec2",
    "aws_eks_": "eks",
    "aws_elasticache_": "elasticache",
    "aws_iam_": "iam",
    "aws_instance": "ec2",
    "aws_internet_gateway": "ec2",
    "aws_kms_": "kms",
    "aws_lb": "elasticloadbalancing",
    "aws_nat_gateway": "ec2",
    "aws_network_": "ec2",
    "aws_rds_": "rds",
    "aws_route53_": "route53",
    "aws_route_table": "ec2",
    "aws_route": "ec2",
    "aws_s3_": "s3",
    "aws_secretsmanager_": "secretsmanager",
    "aws_security_group": "ec2",
    "aws_sns_": "sns",
    "aws_sqs_": "sqs",
    "aws_ssm_": "ssm",
    "aws_subnet": "ec2",
    "aws_vpc": "ec2",
    "aws_wafv2_": "wafv2",
}


def collect_resource_types():
    """aws_* 리소스 타입 -> 그 타입이 나온 파일들."""
    found = {}

    for path in sorted(TERRAFORM_DIR.rglob("*.tf")):
        if EXCLUDED_DIRS & set(path.relative_to(TERRAFORM_DIR).parts):
            continue
        for resource_type in RESOURCE_RE.findall(path.read_text(encoding="utf-8")):
            if resource_type.startswith("aws_"):
                found.setdefault(resource_type, set()).add(
                    str(path.relative_to(TERRAFORM_DIR.parent))
                )

    return found


def service_of(resource_type):
    matches = [p for p in SERVICE_BY_PREFIX if resource_type.startswith(p)]
    return SERVICE_BY_PREFIX[max(matches, key=len)] if matches else None


def aws(*args):
    result = subprocess.run(
        ["aws", *args], capture_output=True, text=True, check=True
    )
    return json.loads(result.stdout)


def granted_services(role_name):
    """배포된 role 의 인라인 + 연결 정책에서 Allow 된 서비스 접두어 집합."""
    services = set()
    documents = []

    for name in aws("iam", "list-role-policies", "--role-name", role_name)["PolicyNames"]:
        documents.append(
            aws("iam", "get-role-policy", "--role-name", role_name, "--policy-name", name)[
                "PolicyDocument"
            ]
        )

    for attached in aws("iam", "list-attached-role-policies", "--role-name", role_name)[
        "AttachedPolicies"
    ]:
        arn = attached["PolicyArn"]
        version = aws("iam", "get-policy", "--policy-arn", arn)["Policy"]["DefaultVersionId"]
        documents.append(
            aws("iam", "get-policy-version", "--policy-arn", arn, "--version-id", version)[
                "PolicyVersion"
            ]["Document"]
        )

    for document in documents:
        statements = document["Statement"]
        if isinstance(statements, dict):
            statements = [statements]
        for statement in statements:
            if statement.get("Effect") != "Allow":
                continue
            actions = statement.get("Action", [])
            if isinstance(actions, str):
                actions = [actions]
            for action in actions:
                services.add("*" if action == "*" else action.split(":", 1)[0])

    return services


def main():
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    role_name = sys.argv[1].rsplit("/", 1)[-1]  # ARN 을 그대로 넘겨도 받는다
    resources = collect_resource_types()
    services = granted_services(role_name)

    unmapped, uncovered = [], {}

    for resource_type, files in sorted(resources.items()):
        service = service_of(resource_type)
        if service is None:
            unmapped.append((resource_type, files))
        elif "*" not in services and service not in services:
            uncovered.setdefault(service, []).append(resource_type)

    for resource_type, files in unmapped:
        print(
            f"[매핑 없음] {resource_type} — SERVICE_BY_PREFIX 에 추가하고 "
            f"role 정책에도 그 서비스를 넣어라 ({sorted(files)[0]})",
            file=sys.stderr,
        )

    for service, types in sorted(uncovered.items()):
        print(
            f"[권한 없음] {role_name} 에 {service}:* 가 없다 — 필요한 리소스: "
            f"{', '.join(sorted(types))}",
            file=sys.stderr,
        )

    if unmapped or uncovered:
        print(
            "\nterraform/modules/base/github_oidc/main.tf 의 terraform_permissions 를 "
            "고치고 integrated/00-base 를 apply 해서 role 을 먼저 동기화해라.",
            file=sys.stderr,
        )
        return 1

    print(f"{role_name}: aws_* 리소스 {len(resources)}종의 서비스 권한 모두 확인")
    return 0


if __name__ == "__main__":
    sys.exit(main())
