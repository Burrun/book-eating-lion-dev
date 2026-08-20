locals {
  cluster_name = coalesce(var.cluster_name, "lion-team3-${var.environment}")
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
  # AmazonEKSEditPolicy로 좁혀봤지만(2026-08-20) main-cd.yml이 이 Role로
  # `kubectl apply -f rendered/`를 실행할 때 k8s/base/03-secret.yaml(Secret
  # 객체 4개)도 같이 적용한다 - EditPolicy는 secrets 리소스 권한을 의도적으로
  # 뺀 정책(edit 롤의 시크릿 읽기를 통한 권한 상승을 막으려는 AWS 설계)이라
  # 그 순간부터 Secret apply가 Forbidden으로 깨진다. 그래서 AdminPolicy로
  # 되돌림. 진짜 최소권한화하려면 AWS 관리형 access policy 대신 이 Role
  # 전용 K8s ClusterRole(딱 필요한 리소스/네임스페이스만)을 따로 만들어
  # access_scope를 그걸로 바꿔야 하는데, 이번 스코프 밖이라 보류.
  policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSAdminPolicy"

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
  ami_type        = "AL2023_ARM_64_STANDARD"

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
}

# Pod/Node CPU·메모리를 CloudWatch Container Insights로 수집
resource "aws_eks_addon" "cloudwatch_observability" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "amazon-cloudwatch-observability"
  depends_on   = [aws_eks_node_group.system]
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
