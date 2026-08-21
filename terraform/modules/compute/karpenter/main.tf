# ── 노드 IAM Role (Karpenter가 띄우는 EC2가 assume) ──────────────
resource "aws_iam_role" "node" {
  name = "lion-team3-${var.environment}-karpenter-node"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
    "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore",
  ])
  role       = aws_iam_role.node.name
  policy_arn = each.value
}

# ── Controller IRSA Role ─────────────────────────────────────────
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

data "aws_iam_policy_document" "controller_trust" {
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
      values   = ["system:serviceaccount:karpenter:karpenter"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "controller" {
  name               = "lion-team3-${var.environment}-karpenter-controller"
  assume_role_policy = data.aws_iam_policy_document.controller_trust.json
}

# Karpenter 공식 문서가 요구하는 최소 권한 (v1.0 기준). 실제 apply 전 karpenter
# 릴리스 노트에서 최신 정책과 대조할 것 - 버전마다 조금씩 늘어난다.
data "aws_iam_policy_document" "controller" {
  statement {
    sid    = "AllowScopedEC2InstanceActions"
    effect = "Allow"
    actions = [
      "ec2:RunInstances",
      "ec2:CreateFleet",
      "ec2:CreateLaunchTemplate",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowScopedInstanceManagement"
    effect = "Allow"
    actions = [
      "ec2:TerminateInstances",
      "ec2:CreateTags",
      "ec2:DeleteLaunchTemplate",
    ]
    resources = ["*"]
    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/karpenter.sh/nodepool"
      values   = ["*"]
    }
  }

  statement {
    sid    = "AllowDescribeActions"
    effect = "Allow"
    actions = [
      "ec2:DescribeInstances",
      "ec2:DescribeInstanceTypes",
      "ec2:DescribeInstanceTypeOfferings",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeSubnets",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeLaunchTemplates",
      "ec2:DescribeSpotPriceHistory",
      "ec2:DescribeImages",
      "pricing:GetProducts",
      "ssm:GetParameter",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "AllowPassingInstanceRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.node.arn]
  }

  statement {
    sid    = "AllowInstanceProfileManagement"
    effect = "Allow"
    actions = [
      "iam:CreateInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "AllowEksDescribe"
    effect    = "Allow"
    actions   = ["eks:DescribeCluster"]
    resources = ["arn:aws:eks:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:cluster/${var.cluster_name}"]
  }

  statement {
    sid       = "AllowInterruptionQueueActions"
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:GetQueueUrl", "sqs:ReceiveMessage"]
    resources = [aws_sqs_queue.interruption.arn]
  }
}

resource "aws_iam_role_policy" "controller" {
  name   = "karpenter-controller"
  role   = aws_iam_role.controller.id
  policy = data.aws_iam_policy_document.controller.json
}

# ── Spot Interruption / 인스턴스 상태 변경 알림 ───────────────────
resource "aws_sqs_queue" "interruption" {
  name                      = "lion-team3-${var.environment}-karpenter-interruption"
  message_retention_seconds = 300 # 인터럽션 통지는 신선할 때만 의미 있음
}

resource "aws_sqs_queue_policy" "interruption" {
  queue_url = aws_sqs_queue.interruption.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" } # 실제 발신자는 EventBridge뿐
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.interruption.arn
    }]
  })
}

resource "aws_cloudwatch_event_rule" "spot_interruption" {
  name = "lion-team3-${var.environment}-karpenter-spot-interruption"
  event_pattern = jsonencode({
    source      = ["aws.ec2"]
    detail-type = ["EC2 Spot Instance Interruption Warning"]
  })
}

resource "aws_cloudwatch_event_rule" "rebalance" {
  name = "lion-team3-${var.environment}-karpenter-rebalance"
  event_pattern = jsonencode({
    source      = ["aws.ec2"]
    detail-type = ["EC2 Instance Rebalance Recommendation"]
  })
}

resource "aws_cloudwatch_event_rule" "instance_state_change" {
  name = "lion-team3-${var.environment}-karpenter-state-change"
  event_pattern = jsonencode({
    source      = ["aws.ec2"]
    detail-type = ["EC2 Instance State-change Notification"]
  })
}

resource "aws_cloudwatch_event_target" "spot_interruption" {
  rule = aws_cloudwatch_event_rule.spot_interruption.name
  arn  = aws_sqs_queue.interruption.arn
}

resource "aws_cloudwatch_event_target" "rebalance" {
  rule = aws_cloudwatch_event_rule.rebalance.name
  arn  = aws_sqs_queue.interruption.arn
}

resource "aws_cloudwatch_event_target" "instance_state_change" {
  rule = aws_cloudwatch_event_rule.instance_state_change.name
  arn  = aws_sqs_queue.interruption.arn
}

# ── Karpenter Controller (Helm) ───────────────────────────────────
resource "helm_release" "karpenter" {
  provider = helm

  name             = "karpenter"
  namespace        = "karpenter"
  create_namespace = true
  repository       = "oci://public.ecr.aws/karpenter"
  chart            = "karpenter"
  version          = var.karpenter_version

  set {
    name  = "settings.clusterName"
    value = var.cluster_name
  }

  set {
    name  = "settings.clusterEndpoint"
    value = var.cluster_endpoint
  }

  set {
    name  = "settings.interruptionQueue"
    value = aws_sqs_queue.interruption.name
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = aws_iam_role.controller.arn
  }
}

# ── NodePool / EC2NodeClass ────────────────────────────────────────
resource "kubernetes_manifest" "ec2_node_class" {
  provider = kubernetes

  manifest = {
    apiVersion = "karpenter.k8s.aws/v1"
    kind       = "EC2NodeClass"
    metadata = {
      name = "default"
    }
    spec = {
      amiFamily = "AL2023"
      # karpenter.k8s.aws/v1(GA) API부터는 amiSelectorTerms가 필수다 - 예전
      # v1beta1처럼 amiFamily만 있으면 자동 선택되던 게 아니라서 이게 없으면
      # "spec.amiSelectorTerms: Required value"로 EC2NodeClass 생성 자체가
      # 거부된다(2026-08-20 실제로 겪음). alias로 최신 AL2023 AMI를 씀 - CRD
      # 설명에 "latest는 새 AMI 나올 때마다 drift 발생, 운영엔 비권장"이라고
      # 적혀있어서, prod에서 안정적으로 고정하고 싶으면 나중에
      # "al2023@v20240625" 같은 특정 버전으로 바꿀 것.
      amiSelectorTerms = [
        { alias = "al2023@latest" }
      ]
      # role(인스턴스 프로파일 이름이 아니라 IAM Role 이름)을 쓰면 Karpenter 컨트롤러가
      # 인스턴스 프로파일을 자기가 직접 만들고 관리한다 - 그래서 controller 정책에
      # iam:CreateInstanceProfile류 권한을 줬다. 여기서 aws_iam_instance_profile을
      # 따로 만들면 아무도 안 쓰는 죽은 리소스가 된다(예전엔 실수로 만들어져 있었음).
      role = aws_iam_role.node.name
      subnetSelectorTerms = [
        for id in var.app_subnet_ids : { id = id }
      ]
      securityGroupSelectorTerms = [
        { id = var.node_security_group_id }
      ]
      tags = {
        Project     = "lion"
        Team        = "Team3"
        Owner       = "likelion-cloud6-team3"
        Environment = var.environment
        ManagedBy   = "karpenter"
      }
    }
  }

  depends_on = [helm_release.karpenter]
}

resource "kubernetes_manifest" "default_node_pool" {
  provider = kubernetes

  manifest = {
    apiVersion = "karpenter.sh/v1"
    kind       = "NodePool"
    metadata = {
      name = "default"
    }
    spec = {
      template = {
        spec = {
          requirements = [
            { key = "karpenter.k8s.aws/instance-family", operator = "In", values = distinct([for t in var.instance_types : split(".", t)[0]]) },
            # main-cd.yml의 docker build가 amd64 러너에서 --platform 없이 이미지를
            # 만들어서 amd64로 고정 - Graviton(arm64)으로 바꾸려면 CI를 buildx
            # 크로스컴파일로 먼저 바꿔야 한다(인프라구성명세.md §7.7 참고).
            { key = "kubernetes.io/arch", operator = "In", values = ["amd64"] },
            { key = "karpenter.sh/capacity-type", operator = "In", values = ["spot", "on-demand"] },
          ]
          nodeClassRef = {
            group = "karpenter.k8s.aws"
            kind  = "EC2NodeClass"
            name  = "default"
          }
        }
      }
      limits = {
        cpu = "100"
      }
      disruption = {
        consolidationPolicy = "WhenEmptyOrUnderutilized"
        consolidateAfter    = "30s"
      }
    }
  }

  depends_on = [kubernetes_manifest.ec2_node_class]
}
