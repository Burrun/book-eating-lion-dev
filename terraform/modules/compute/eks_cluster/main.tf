locals {
  cluster_name = coalesce(var.cluster_name, "lion-team3-${var.environment}")

  # k8s taint effect 표기 -> aws_eks_node_group이 요구하는 AWS API 표기.
  eks_taint_effect_map = {
    NoSchedule       = "NO_SCHEDULE"
    NoExecute        = "NO_EXECUTE"
    PreferNoSchedule = "PREFER_NO_SCHEDULE"
  }

  # 시스템 노드그룹 taint를 견디는 toleration - coredns/cloudwatch_observability
  # 등 이 노드그룹에서 떠야 하는 addon들이 공유해서 쓴다.
  system_pool_toleration = {
    key      = var.system_pool_taint_key
    operator = "Exists"
    effect   = var.system_pool_taint_effect
  }
}

# ── Cluster IAM Role ─────────────────────────────────────────────
resource "aws_iam_role" "cluster" {
  name = "${local.cluster_name}-eks-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_security_group" "cluster" {
  name_prefix = "${local.cluster_name}-cluster-"
  description = "EKS control plane additional SG"
  vpc_id      = var.vpc_id

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${local.cluster_name}-cluster-sg"
  }
}

resource "aws_eks_cluster" "this" {
  name     = local.cluster_name
  role_arn = aws_iam_role.cluster.arn
  version  = var.cluster_version

  vpc_config {
    subnet_ids              = var.app_subnet_ids
    security_group_ids      = [aws_security_group.cluster.id]
    endpoint_private_access = true
    endpoint_public_access  = true # CI/개발자 kubectl 접근용. 허용 대역은 var.public_access_cidrs로 제한
    public_access_cidrs     = var.public_access_cidrs
  }

  access_config {
    authentication_mode                         = "API" # 레거시 aws-auth ConfigMap 대신 EKS Access Entries API 사용
    bootstrap_cluster_creator_admin_permissions = true  # 이걸 안 켜면 클러스터를 만든 사람조차 kubectl 권한이 없어서
    # Karpenter/ALB Controller 등 kubernetes_manifest/helm_release 리소스가 전부
    # 401 Unauthorized로 실패한다(2026-08-20 실제로 겪음). 단, 이 값은 클러스터
    # "생성 시점"에만 평가되는 부트스트랩 옵션이라 이미 만들어진 클러스터에는
    # 재적용해도 소급 적용이 안 된다 - 그래서 아래 admin_principal_arns로
    # 언제든 추가/변경 가능한 Access Entry도 별도로 만든다.
  }

  depends_on = [aws_iam_role_policy_attachment.cluster_policy]
}

# ── OIDC Provider (IRSA의 기반) ──────────────────────────────────
data "tls_certificate" "eks" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
}

# ── EKS Access Entry — GitHub Actions가 kubectl로 배포 ───────────
resource "aws_eks_access_entry" "github_actions" {
  count         = var.github_actions_role_arn != null ? 1 : 0
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = var.github_actions_role_arn
}

resource "aws_eks_access_policy_association" "github_actions" {
  count         = var.github_actions_role_arn != null ? 1 : 0
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = var.github_actions_role_arn
  # AmazonEKSEditPolicy는 Secret 적용을 막고(k8s/base/03-secret.yaml 필요),
  # AmazonEKSAdminPolicy(K8s "admin" 롤)는 Namespace 같은 cluster-scoped 리소스를
  # 못 만든다(k8s/base/01-namespace.yaml 필요) - 그래서 admin_principal_arns와
  # 같은 ClusterAdminPolicy를 쓴다. 최소권한화하려면 전용 K8s ClusterRole을 따로
  # 만들어야 하지만 이번 스코프 밖.
  policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  # 이 리소스는 principal_arn/cluster_name 값만 공유할 뿐 access_entry를 속성으로
  # 참조하지 않아서, 명시하지 않으면 Terraform이 둘의 생성 순서를 보장 못 한다.
  # Access Entry 없이 Association만 먼저 만들려고 하면 API가 거부한다.
  depends_on = [aws_eks_access_entry.github_actions]
}

# ── EKS Access Entry — 사람(운영자)이 kubectl/terraform으로 접근 ──
# bootstrap_cluster_creator_admin_permissions는 클러스터 "생성 시점"에만
# 평가되는 일회성 옵션이라 기존 클러스터엔 소급 적용이 안 된다. 그래서
# 실제로 Terraform apply를 돌리는 사람(들)은 여기 명시적으로 등록해야
# kubernetes_manifest/helm_release 리소스가 401 Unauthorized 없이 동작한다.
resource "aws_eks_access_entry" "admin" {
  for_each      = toset(var.admin_principal_arns)
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
}

resource "aws_eks_access_policy_association" "admin" {
  for_each      = toset(var.admin_principal_arns)
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.admin]
}

# ── 시스템 노드그룹 (CoreDNS, Karpenter 컨트롤러 기동용) ─────────
resource "aws_iam_role" "node" {
  name = "${local.cluster_name}-eks-node"

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

resource "aws_eks_node_group" "system" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "system"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.app_subnet_ids
  instance_types  = [var.system_node_instance_type]
  # instance_types(위)의 아키텍처와 반드시 맞춰야 한다 - ami_type만 안 바꾸고
  # instance_type만 amd64로 바꾸면 노드 자체가 뜨지 못한다.
  ami_type = "AL2023_x86_64_STANDARD"

  scaling_config {
    desired_size = var.system_node_desired_size
    min_size     = var.system_node_desired_size
    max_size     = var.system_node_desired_size + 2
  }

  update_config {
    max_unavailable = 1
  }

  labels = {
    "book-eating-lion.io/pool" = "system"
  }

  # 클러스터 애드온(CoreDNS, Karpenter 컨트롤러) 전용 노드로 격리한다 - 이 taint가
  # 없으면 앱 워크로드가 스케줄러에 의해 이 노드에도 자유롭게 앉을 수 있는데,
  # 이 노드그룹엔 app_security_group_id가 안 붙어 있어서(Karpenter EC2NodeClass만
  # 붙임) 그렇게 뜬 파드는 DB/Redis에 연결하지 못한다(2026-08-21 실제로 겪음 -
  # ai-bot 등 nodeSelector/toleration 없는 Deployment가 이 노드에 앉아 계속
  # CrashLoopBackOff). taint를 견디는 toleration이 없는 파드는 스케줄러가
  # 자동으로 Karpenter 노드로 보낸다 - 앱 매니페스트 쪽은 손댈 필요 없음.
  taint {
    key    = var.system_pool_taint_key
    value  = var.system_pool_taint_value
    effect = local.eks_taint_effect_map[var.system_pool_taint_effect]
  }

  depends_on = [aws_iam_role_policy_attachment.node]
}

# ── Addons ─────────────────────────────────────────────────────────
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "vpc-cni"
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "kube-proxy"
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "coredns"
  depends_on   = [aws_eks_node_group.system] # CoreDNS Pod가 뜨려면 노드가 먼저 있어야 함

  # 시스템 노드그룹의 CriticalAddonsOnly taint를 견디게 한다 - 이게 없으면
  # CoreDNS Pod가 어디에도 스케줄 못 되고(taint 있는 시스템 노드는 못 견디고,
  # Karpenter 노드는 애초에 CoreDNS가 있어야 쓸 수 있는 DNS로 자기 자신을
  # 찾아야 하는 부트스트랩 문제가 생길 수 있음) 클러스터 DNS 전체가 멎는다.
  configuration_values = jsonencode({
    tolerations = [local.system_pool_toleration]
  })
}

# 리소스 메트릭(CPU·메모리)으로 스케일하는 HPA는 Resource Metrics API
# (metrics.k8s.io)가 있어야 한다. 이게 없으면 그런 HPA가 TARGETS=<unknown>으로
# 뜨고 절대 스케일하지 않으며 `kubectl top`도 실패한다 (2026-08-31
# lion-team3-integrated에서 실측 - metrics-server addon 자체가 없어서
# catalog/order/member/ai HPA가 전부 죽어 있었다). 커스텀 메트릭
# (예: http_server_requests_active)을 쓰는 HPA는 이 addon과 무관하며, 별도로
# Prometheus Adapter나 KEDA가 있어야 스케일한다. vpc-cni/kube-proxy/coredns와
# 함께 EKS 관리형 addon으로 둔다.
resource "aws_eks_addon" "metrics_server" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "metrics-server"
  depends_on   = [aws_eks_node_group.system] # Pod가 뜨려면 노드가 먼저 있어야 함

  # OVERWRITE는 addon 생성 시 self-managed Kubernetes 리소스(예: 매니페스트로
  # 직접 깔아 둔 metrics-server Deployment)와의 충돌만 덮어쓴다. 이미
  # `aws eks create-addon`으로 만들어 둔 "EKS 관리형 addon"을 terraform 상태로
  # 가져오지는 못하므로, 그런 클러스터(2026-08-31 기준 lion-team3-integrated)에선
  # 이 옵션과 무관하게 CreateAddon이 ResourceInUseException("addon already
  # exists")으로 실패한다. apply 전에 한 번만 수동 import가 필요하다:
  #   terraform import '<이 리소스의 module 경로>.aws_eks_addon.metrics_server' \
  #     lion-team3-integrated:metrics-server
  # sibling addon(vpc-cni/kube-proxy/coredns)들은 처음부터 terraform이 만들어서
  # import도 이 옵션도 필요 없었다.
  resolve_conflicts_on_create = "OVERWRITE"

  # metrics-server는 부트스트랩 의존성이 아니라 toleration이 없으면 Karpenter
  # 노드로 스케줄되지만(coredns 주석 참고), 클러스터를 새로 만들 때 Karpenter
  # 노드가 아직 없는 창을 없애려고 시스템 노드그룹 taint도 견디게 열어둔다.
  configuration_values = jsonencode({
    tolerations = [local.system_pool_toleration]
  })
}

# Pod/Node CPU·메모리를 CloudWatch Container Insights로 수집
resource "aws_eks_addon" "cloudwatch_observability" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "amazon-cloudwatch-observability"
  depends_on   = [aws_eks_node_group.system]

  # coredns와 같은 이유(위 주석 참고) - 이게 없으면 이 addon의 파드(manager,
  # cloudwatch-agent 등)가 시스템 노드그룹 taint를 못 견뎌 전혀 스케줄 못 되고,
  # addon 자체가 DEGRADED 상태로 20분 타임아웃 후 apply가 실패한다. 클러스터를
  # 새로 만들 때만 드러난다 - 이미 떠 있던 파드는 taint가 나중에 추가돼도
  # 영향 없어서(2026-08-22 sandbox 클러스터에서 처음부터 만들어보다가 실제로
  # 겪음 - dev는 이 taint(PR #62)보다 addon이 먼저 떠 있어서 안 드러났었다).
  configuration_values = jsonencode({
    tolerations = [local.system_pool_toleration]

    # 최상위 tolerations만으로는 operator(controller-manager)에 반영되지 않는다.
    # 시스템 노드그룹의 CriticalAddonsOnly taint를 견디도록 manager에도 명시한다.
    manager = {
      tolerations = [local.system_pool_toleration]
    }
  })
}

resource "aws_cloudwatch_metric_alarm" "pod_cpu" {
  alarm_name          = "${local.cluster_name}-pod-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "pod_cpu_utilization"
  namespace           = "ContainerInsights"
  period              = 60
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "EKS Pod CPU utilization high"
  alarm_actions       = [var.sns_topic_arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = aws_eks_cluster.this.name
  }
}
