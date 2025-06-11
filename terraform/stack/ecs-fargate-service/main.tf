resource "aws_ecs_task_definition" "this" {
  family                   = var.task_family_name == "" ? "${var.name_prefix}-task" : var.task_family_name
  container_definitions    = var.container_definitions
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  tags = merge(
    var.tags,
    {
      Name = var.task_family_name == "" ? "${var.name_prefix}-task" : var.task_family_name
    }
  )
}

resource "aws_ecs_service" "this" {
  name            = var.service_name == "" ? "${var.name_prefix}-service" : var.service_name
  cluster         = var.cluster_id
  task_definition = var.track_latest_task_definition ? aws_ecs_task_definition.this.family : aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  deployment_maximum_percent         = var.deployment_maximum_percent
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }

  # If track_latest_task_definition is true, we don't want Terraform to detect a diff
  # every time a new task definition revision is created outside of this specific apply.
  # The service will automatically pick up the latest "active" revision of the family.
  lifecycle {
    ignore_changes = [
      task_definition,
    ]
  }

  tags = merge(
    var.tags,
    {
      Name = var.service_name == "" ? "${var.name_prefix}-service" : var.service_name
    }
  )
}
