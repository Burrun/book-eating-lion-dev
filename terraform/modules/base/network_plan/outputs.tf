output "vpc_cidr" {
  value = local.plan.vpc_cidr
}

output "availability_zones" {
  value = local.plan.availability_zones
}

output "public_subnet_cidrs" {
  value = local.plan.public_subnet_cidrs
}

output "app_subnet_cidrs" {
  value = local.plan.app_subnet_cidrs
}

output "data_subnet_cidrs" {
  value = local.plan.data_subnet_cidrs
}
