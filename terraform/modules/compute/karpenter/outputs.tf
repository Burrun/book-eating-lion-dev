# 인스턴스 프로파일 이름이 아니라 Role 이름이다 - EC2NodeClass가 role 필드를 쓰면
# Karpenter 컨트롤러가 인스턴스 프로파일을 직접 만들고 관리해서, Terraform이 만든
# 프로파일이 따로 없다(main.tf 주석 참고). 출력 이름은 원래 설계와 맞춰 유지.
output "karpenter_node_instance_profile_name" {
  value = aws_iam_role.node.name
}

output "karpenter_irsa_arn" {
  value = aws_iam_role.controller.arn
}
