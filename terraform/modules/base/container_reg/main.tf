resource "aws_ecr_repository" "this" {
  for_each = toset(var.service_names)

  name                 = "lion-team3/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "최근 ${var.image_tag_keep_count}개 태그만 유지"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.image_tag_keep_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
