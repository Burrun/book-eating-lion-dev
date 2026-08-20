resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "lion-team3-${var.environment}-vpc"
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "lion-team3-${var.environment}-igw"
  }
}

# ── Public Subnets (ALB, NAT Gateway) ─────────────────────────────
resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "lion-team3-${var.environment}-public-${var.availability_zones[count.index]}"
    "kubernetes.io/role/elb" = "1"
  }
}

# ── Private App Subnets (EKS 노드, Karpenter Provisioned Nodes) ──
resource "aws_subnet" "app" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.app_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name                              = "lion-team3-${var.environment}-app-${var.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# ── Private Data Subnets (Aurora, Valkey) ─────────────────────────
resource "aws_subnet" "data" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.data_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name = "lion-team3-${var.environment}-data-${var.availability_zones[count.index]}"
  }
}

# ── NAT Gateway — 기본은 AZ당 1개, 그 AZ의 App Subnet만 담당 ───────
# 두 AZ가 같은 NAT를 공유하면 그 AZ가 죽을 때 다른 AZ까지 아웃바운드를 잃는다.
# var.single_nat_gateway=true면 이 격리를 포기하고 NAT 1개(첫 AZ)로 비용을 아낀다 -
# NAT Gateway가 시간당 고정 과금이라(2개 기준 월 ~$86) dev처럼 격리보다 비용이
# 중요한 환경에서만 켤 것. prod는 기본값(false, AZ별 NAT) 유지.
locals {
  nat_gateway_count = var.single_nat_gateway ? 1 : length(var.availability_zones)
}

resource "aws_eip" "nat" {
  count  = local.nat_gateway_count
  domain = "vpc"

  tags = {
    Name = "lion-team3-${var.environment}-nat-eip-${var.availability_zones[count.index]}"
  }

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count         = local.nat_gateway_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "lion-team3-${var.environment}-nat-${var.availability_zones[count.index]}"
  }

  depends_on = [aws_internet_gateway.this]
}

# ── Route Tables ───────────────────────────────────────────────────

# Public: 인터넷 게이트웨이 하나로 두 AZ 공유 (실패해도 아웃바운드만 영향, IGW는 리전 리소스라 SPOF 아님)
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name = "lion-team3-${var.environment}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# App: AZ별 라우팅 테이블 — 반드시 그 AZ의 NAT로만 나간다 (AZ 장애 격리)
resource "aws_route_table" "app" {
  count  = length(var.availability_zones)
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = var.single_nat_gateway ? aws_nat_gateway.this[0].id : aws_nat_gateway.this[count.index].id
  }

  tags = {
    Name = "lion-team3-${var.environment}-app-rt-${var.availability_zones[count.index]}"
  }
}

resource "aws_route_table_association" "app" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.app[count.index].id
  route_table_id = aws_route_table.app[count.index].id
}

# Data: 기본 경로 없음 — 완전 격리(아웃바운드 인터넷 불필요, Aurora/Valkey는 VPC 내부에서만 접근)
resource "aws_route_table" "data" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "lion-team3-${var.environment}-data-rt"
  }
}

resource "aws_route_table_association" "data" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.data.id
}

# ── App Security Group ────────────────────────────────────────────
# EKS 노드/Pod(02-runtime)에 붙는 보안그룹. 01-data의 Aurora/RDS Proxy/Valkey
# 보안그룹이 "이 SG에서 오는 트래픽만 허용"하는 식으로 이 SG를 참조한다.
# 그 자체엔 인바운드 규칙이 없다 — 필요한 포트별 허용은 각 데이터 리소스 쪽에서 건다
# (예: aurora_pg가 자기 보안그룹에 "5432 from app_security_group_id" 규칙을 추가).
resource "aws_security_group" "app" {
  name_prefix = "lion-team3-${var.environment}-app-"
  # AWS SG description은 ASCII만 허용한다(정규식 제약) - 한글 설명은 위 주석으로 남긴다.
  description = "EKS node/Pod shared SG - data-layer SGs allow inbound from this SG"
  vpc_id      = aws_vpc.this.id

  egress {
    description = "Allow all outbound (AWS APIs, PG gateway, LLM APIs, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "lion-team3-${var.environment}-app-sg"
  }
}
