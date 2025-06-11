resource "aws_ecs_cluster" "this" {
  name = var.cluster_name == "" ? "${var.name_prefix}-cluster" : var.cluster_name

  tags = merge(
    var.tags,
    {
      Name = var.cluster_name == "" ? "${var.name_prefix}-cluster" : var.cluster_name
    }
  )
}
