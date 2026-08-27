data "aws_caller_identity" "current" {}

locals {
  environments = {
    dev = {
      infra_environment = "dev"
      namespace         = "lion-app"
      role_arn          = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/github-actions-lion-team3-dev"
    }
    prod = {
      infra_environment = "prod"
      namespace         = "lion-app"
      role_arn          = var.split_prod_aws_role_arn
    }
    integrated-dev = {
      infra_environment = "integrated"
      namespace         = "dev"
      role_arn          = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/github-actions-lion-team3-integrated"
    }
    integrated-prod = {
      infra_environment = "integrated"
      namespace         = "prod"
      role_arn          = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/github-actions-lion-team3-integrated"
    }
  }

  environment_variables = merge([
    for environment, config in local.environments : {
      for name, value in {
        AWS_REGION    = var.aws_region
        AWS_ROLE_ARN  = config.role_arn
        K8S_NAMESPACE = config.namespace
        } : "${environment}:${name}" => {
        environment = environment
        name        = name
        value       = value
      }
      if value != null
    }
  ]...)
}

resource "github_repository_environment" "this" {
  for_each = local.environments

  repository  = var.github_repository
  environment = each.key
}

resource "github_actions_environment_variable" "this" {
  for_each = local.environment_variables

  repository    = var.github_repository
  environment   = github_repository_environment.this[each.value.environment].environment
  variable_name = each.value.name
  value         = each.value.value
}
