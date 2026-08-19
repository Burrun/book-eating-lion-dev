locals {
  cluster_name = coalesce(var.cluster_name, "book-eating-lion-${var.environment}")
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
    endpoint_public_access  = true # true로 두되 CI/개발자 kubectl 접근용. 운영에서 IP 제한하려면 public_access_cidrs 추가
  }

  access_config {
    authentication_mode = "API" # 레거시 aws-auth ConfigMap 대신 EKS Access Entries API 사용
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
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSAdminPolicy"

  access_scope {
    type = "cluster"
  }

  # 이 리소스는 principal_arn/cluster_name 값만 공유할 뿐 access_entry를 속성으로
  # 참조하지 않아서, 명시하지 않으면 Terraform이 둘의 생성 순서를 보장 못 한다.
  # Access Entry 없이 Association만 먼저 만들려고 하면 API가 거부한다.
  depends_on = [aws_eks_access_entry.github_actions]
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
