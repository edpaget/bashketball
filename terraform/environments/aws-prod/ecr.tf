resource "aws_ecr_repository" "bashketball" {
  name = "bashketball"
  image_tag_mutability = "MUTABLE"
}
