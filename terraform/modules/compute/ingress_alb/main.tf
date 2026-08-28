# ⚠️ 실제 구현 중 발견: 이 프로젝트는 AWS Load Balancer Controller가 Ingress를 직접
# 해석하는 방식(ALB annotation 기반)이 아니다. 실제 k8s/base/08-ingress.yaml은
# `ingressClassName: nginx` + `nginx.ingress.kubernetes.io/*` 어노테이션을 쓴다 -
# 즉 라우팅은 ingress-nginx가 하고, AWS Load Balancer Controller는 그 앞단 NLB를
# 만들어주는 역할만 한다. 그래서 이 모듈은:
#   1. AWS Load Balancer Controller 설치 (Service type=LoadBalancer를 NLB로 provisioning)
#   2. ingress-nginx 컨트롤러 설치 (실제 L7 라우팅 - k8s/base/08-ingress.yaml이 이걸 봄)
# service_routes 같은 라우팅 입력은 여기서 받지 않는다 - 라우팅 규칙은 k8s/base/*.yaml에
# git으로 관리되고 CI가 배포한다. Terraform은 그 라우팅을 실행할 컨트롤러만 세운다.

# NLB가 public인 현재 구조에서 CloudFront를 거치지 않은 직접 호출을 차단한다.
# CloudFront origin-facing managed prefix list만 NLB 보안그룹 인바운드로 허용한다.
data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "ingress_nlb" {
  name_prefix = "lion-team3-${var.environment}-ingress-nlb-"
  description = "Allow ingress NLB traffic only from CloudFront origin-facing network"
  vpc_id      = var.vpc_id

  ingress {
    description     = "CloudFront origin-facing traffic only"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id]
  }

  ingress {
    description     = "CloudFront origin-facing traffic only"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "lion-team3-${var.environment}-ingress-nlb-sg"
  }
}

data "aws_iam_policy_document" "alb_controller_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "alb_controller" {
  name               = "lion-team3-${var.environment}-alb-controller"
  assume_role_policy = data.aws_iam_policy_document.alb_controller_trust.json
}

# AWS 공식 게시 정책 요약본. 실제 apply 전 aws-load-balancer-controller 릴리스 노트의
# iam_policy.json과 대조할 것 - 버전마다 조금씩 늘어난다. 실제로 겪음(2026-08-20):
# helm_release가 chart 버전을 안 고정해서 최신 컨트롤러가 설치됐는데, 그 버전이
# 요구하는 elasticloadbalancing:DescribeListenerAttributes/ModifyListenerAttributes가
# 이 요약본엔 없어서 ingress-nginx Service가 NLB를 못 만들고 FailedDeployModel
# 이벤트만 반복됐다.
data "aws_iam_policy_document" "alb_controller" {
  statement {
    sid    = "AllowReadOnly"
    effect = "Allow"
    actions = [
      "ec2:DescribeAccountAttributes", "ec2:DescribeAddresses", "ec2:DescribeAvailabilityZones",
      "ec2:DescribeInternetGateways", "ec2:DescribeVpcs", "ec2:DescribeVpcPeeringConnections",
      "ec2:DescribeSubnets", "ec2:DescribeSecurityGroups", "ec2:DescribeInstances",
      "ec2:DescribeNetworkInterfaces", "ec2:DescribeTags", "ec2:GetCoipPoolUsage",
      "ec2:DescribeCoipPools", "elasticloadbalancing:DescribeLoadBalancers",
      "elasticloadbalancing:DescribeLoadBalancerAttributes", "elasticloadbalancing:DescribeListeners",
      "elasticloadbalancing:DescribeListenerCertificates", "elasticloadbalancing:DescribeSSLPolicies",
      "elasticloadbalancing:DescribeRules", "elasticloadbalancing:DescribeTargetGroups",
      "elasticloadbalancing:DescribeTargetGroupAttributes", "elasticloadbalancing:DescribeTargetHealth",
      "elasticloadbalancing:DescribeListenerAttributes",
      "elasticloadbalancing:DescribeTags", "acm:ListCertificates", "acm:DescribeCertificate",
      "iam:ListServerCertificates", "iam:GetServerCertificate", "waf-regional:GetWebACL",
      "wafv2:GetWebACL", "wafv2:GetWebACLForResource", "shield:GetSubscriptionState",
      "shield:DescribeProtection",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "AllowCreateServiceLinkedRole"
    effect    = "Allow"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values   = ["elasticloadbalancing.amazonaws.com"]
    }
  }

  statement {
    sid    = "AllowSecurityGroupWrite"
    effect = "Allow"
    actions = [
      "ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress",
      "ec2:CreateSecurityGroup", "ec2:CreateTags", "ec2:DeleteTags",
      "ec2:DeleteSecurityGroup",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowLoadBalancerWrite"
    effect = "Allow"
    actions = [
      "elasticloadbalancing:CreateLoadBalancer", "elasticloadbalancing:CreateTargetGroup",
      "elasticloadbalancing:CreateListener", "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:CreateRule", "elasticloadbalancing:DeleteRule",
      "elasticloadbalancing:ModifyLoadBalancerAttributes", "elasticloadbalancing:SetIpAddressType",
      "elasticloadbalancing:SetSecurityGroups", "elasticloadbalancing:SetSubnets",
      "elasticloadbalancing:DeleteLoadBalancer", "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:ModifyTargetGroupAttributes", "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:RegisterTargets", "elasticloadbalancing:DeregisterTargets",
      "elasticloadbalancing:SetWebAcl", "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:ModifyListenerAttributes",
      "elasticloadbalancing:AddListenerCertificates", "elasticloadbalancing:RemoveListenerCertificates",
      "elasticloadbalancing:ModifyRule", "elasticloadbalancing:AddTags", "elasticloadbalancing:RemoveTags",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "alb_controller" {
  name   = "alb-controller"
  role   = aws_iam_role.alb_controller.id
  policy = data.aws_iam_policy_document.alb_controller.json
}

resource "helm_release" "alb_controller" {
  provider = helm

  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  # 버전을 안 고정하면 업그레이드 때마다 IAM 정책이 안 맞아 배포가 깨질 수 있다
  # (2026-08-20 실제로 겪음) - 지금 IAM 정책/Helm set 값으로 검증된 버전에 고정.
  version = var.alb_controller_chart_version

  set {
    name  = "clusterName"
    value = var.cluster_name
  }

  set {
    name  = "region"
    value = var.aws_region
  }

  set {
    name  = "vpcId"
    value = var.vpc_id
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = aws_iam_role.alb_controller.arn
  }

  # helm_release는 Role의 ARN만 참조하고 그 옆의 인라인 정책(aws_iam_role_policy)과는
  # 값으로 연결되지 않는다 - 이 depends_on이 없으면 destroy 시 Terraform이 이 정책을
  # helm_release보다 먼저(또는 동시에) 지울 수 있다. 컨트롤러는 아직 살아서 NLB/타겟그룹을
  # 정리하려는데 권한만 사라져 Service의 service.k8s.aws/resources finalizer가 영원히
  # 안 빠지고 destroy 전체가 멎는다(2026-08-23 dev 실배포에서 실제로 겪음 - IAM 정책을
  # 수동으로 임시 재부착해서 복구).
  depends_on = [aws_iam_role_policy.alb_controller]
}

# ── ingress-nginx — 실제 L7 라우팅. k8s/base/08-ingress.yaml이 이 클래스를 본다 ──
resource "helm_release" "ingress_nginx" {
  provider = helm

  name             = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = var.ingress_nginx_chart_version
  # wait=true 기본 타임아웃(300초)이 최초 NLB 프로비저닝엔 빠듯하다 - 리소스
  # 자체는 정상 생성됐는데 helm_release만 타임아웃으로 실패 처리된 걸 실제로
  # 겪음(2026-08-20, NLB는 살아있고 helm status도 deployed인데 Terraform만
  # 에러). 여유 있게 늘림.
  timeout = 600

  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }

  # NLB로 provisioning - CloudFront(edge_routing)가 이 뒤에서 L4로 붙는다.
  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-type"
    value = "nlb"
  }

  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-nlb-target-type"
    value = "ip"
  }

  # AWS LB Controller의 scheme 기본값은 internal이라, 이 어노테이션 없이는
  # public 서브넷에 둬도 비공개 NLB가 된다. CloudFront가 공인 인터넷으로
  # 붙어야 하므로 명시적으로 internet-facing을 지정한다.
  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-scheme"
    value = "internet-facing"
  }

  # NLB에 전용 SG를 부착해 CloudFront origin-facing prefix list 외의
  # 직접 접근(NLB DNS 포함)을 차단한다.
  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-security-groups"
    value = aws_security_group.ingress_nlb.id
  }

  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-manage-backend-security-group-rules"
    value = "true"
  }

  set {
    name = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/aws-load-balancer-subnets"
    # Helm의 set 문법은 콤마를 "여러 key=value 쌍의 구분자"로 해석해서, 값
    # 안의 콤마(서브넷 ID 나열)를 그냥 join(",", ...)하면 두 번째 서브넷부터
    # "값 없는 키"로 잘못 파싱돼 apply가 실패한다(2026-08-20 실제로 겪음).
    # \,로 이스케이프해야 Helm이 값 전체를 하나로 본다.
    value = join("\\,", var.public_subnet_ids)
  }

  set {
    name  = "controller.ingressClassResource.name"
    value = "nginx"
  }

  depends_on = [helm_release.alb_controller, aws_security_group.ingress_nlb]
}

# NLB DNS는 헬름 릴리스 직후엔 아직 프로비저닝 중일 수 있어서, Service 상태를 읽기 전에
# 약간 대기한다 - 그래도 완전한 보장은 아니라서 최초 apply 시 실패하면 다시 apply할 것.
resource "time_sleep" "wait_for_nlb" {
  depends_on      = [helm_release.ingress_nginx]
  create_duration = "60s"
}

data "kubernetes_service" "ingress_nginx" {
  provider = kubernetes

  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }

  depends_on = [time_sleep.wait_for_nlb]
}
