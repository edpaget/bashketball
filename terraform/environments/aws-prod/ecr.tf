data "aws_ecr_repository" "blood_basket" {
  name = "blood-basket"
  image_table_mutability = "MUTABLE"
}
