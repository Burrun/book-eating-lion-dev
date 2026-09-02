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
    "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy",
    "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess",
  ])
  role       = aws_iam_role.node.name
  policy_arn = each.value
}

# 클러스터가 authenticationMode=API라 aws-auth ConfigMap이 없다 - 이 노드
# Role로 뜨는 EC2(Karpenter가 만든, EKS Managed NodeGroup이 아닌 셀프 관리
# 노드)는 이 Access Entry가 없으면 kubelet이 부팅은 되지만 클러스터 인증이
# 안 돼서 Node로 영영 등록되지 않는다(Launched=True인데 Registered=Unknown
# 상태로 멈춤 - 2026-08-21 실제로 겪음).
resource "aws_eks_access_entry" "karpenter_node" {
  cluster_name  = var.cluster_name
  principal_arn = aws_iam_role.node.arn
  type          = "EC2_LINUX"
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

  # 위 AllowScopedInstanceManagement의 CreateTags는 "이미 karpenter.sh/nodepool
  # 태그가 붙어있는 리소스"에만 적용된다 - 그런데 CreateLaunchTemplate/RunInstances/
  # CreateFleet로 리소스를 막 만드는 시점엔 그 태그가 아직 없어서(태그를 붙이는
  # 요청 자체가 그 CreateTags 호출이라 닭-달걀 문제) 매번 거부된다(실제로 launch
  # template 생성 직후 CreateTags가 UnauthorizedOperation으로 막혀 노드 프로비저닝
  # 자체가 안 됐다). Karpenter 공식 최소 정책의 AllowScopedResourceCreationTagging과
  # 동일하게, "리소스 생성 액션과 함께 요청된" 태그 요청은 별도로 허용한다.
  statement {
    sid    = "AllowScopedResourceCreationTagging"
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
    ]
    resources = [
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:fleet/*",
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:instance/*",
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:volume/*",
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:network-interface/*",
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:launch-template/*",
      "arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:spot-instances-request/*",
    ]
    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values   = ["RunInstances", "CreateFleet", "CreateLaunchTemplate"]
    }
    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/karpenter.sh/nodepool"
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

  # 시스템 노드그룹(eks_cluster 모듈이 붙인 taint)에 고정한다 - Karpenter
  # 컨트롤러는 자기 자신이 만드는 노드에서 돌면 안 된다(그 노드가
  # consolidation으로 사라지면 컨트롤러도 같이 죽는 닭-달걀 문제).
  # nodeSelector로 강제 배치하고, taint를 견디도록 toleration도 같이 준다.
  #
  # tolerations는 통짜 values(YAML) 블록으로 넣는다 - `set { name =
  # "tolerations[0].key" ... }`처럼 인덱스로 넣으면, 차트가 나중에 기본
  # toleration 목록을 갖게 될 경우 그 목록과 위치 기준으로 뒤섞여 병합되는
  #깨지기 쉬운 동작이 된다(/code-review 지적사항). values 블록은 그 키를
  # 통째로 덮어써서 항상 예측 가능하다.
  values = [
    yamlencode({
      nodeSelector = {
        "book-eating-lion.io/pool" = "system"
      }
      tolerations = [
        {
          key      = var.system_pool_taint_key
          operator = "Exists"
          effect   = var.system_pool_taint_effect
        }
      ]
    })
  ]

  # helm_release는 Role의 ARN만 참조하고 그 옆의 인라인 정책(aws_iam_role_policy)과는
  # 값으로 연결되지 않는다 - 이 depends_on이 없으면 destroy 시 Terraform이 이 정책을
  # helm_release보다 먼저(또는 동시에) 지울 수 있다. 컨트롤러는 아직 살아서
  # EC2NodeClass의 IAM Instance Profile을 정리하려는데 권한만 사라져
  # karpenter.k8s.aws/termination finalizer가 영원히 안 빠지고 destroy 전체가 멎는다
  # (2026-08-23 dev 실배포에서 실제로 겪음 - IAM 정책을 수동으로 임시 재부착해서 복구).
  depends_on = [aws_iam_role_policy.controller]
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
      # cluster SG(노드<->control plane 필수 통신)와 app SG(DB 등 데이터 계층
      # 접근 허용의 기준이 되는 SG)를 둘 다 붙인다 - selectorTerms를 나열하면
      # 매칭되는 보안그룹이 전부 인스턴스에 첨부된다(OR 매칭이지 택1이 아님).
      securityGroupSelectorTerms = [
        { id = var.node_security_group_id },
        { id = var.app_security_group_id },
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
