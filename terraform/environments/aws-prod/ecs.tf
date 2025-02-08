resource "aws_ecs_cluster" "prod_blood_basket" {
  name = "prod-blood-basket-ecs-cluster"
}

resource "aws_ecs_task_definition" "blood_basket_task" {
  count = 0
  family       = "blood-basket-ecs-task-definition"
  track_latest = true

  container_definitions = jsonencode([{
    name      = "blood-basket-container"
    essential = true
    image     = "${data.aws_ecr_repository.blood_basket.repository_url}:main"
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/prod/blood-basket"
        awslogs-region        = "us-east-2"
        awslogs-stream-prefix = "ecs"
      }
    }
    secrets = [
      {
        name      = "DISCORD_TOKEN"
        valueFrom = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/prod.discord_bot_token"
      }
    ]
  }])

  cpu          = 256
  memory       = 512
  network_mode = "awsvpc"

  requires_compatibilities = [
    "FARGATE"
  ]

  execution_role_arn = aws_iam_role.blood_basket_ecs_execution_role.arn
  task_role_arn      = aws_iam_role.blood_basket_ecs_task_role.arn
}

resource "aws_ecs_service" "blood_basket" {
  count = 0
  name            = "blood-basket-ecs-service"
  cluster         = aws_ecs_cluster.prod_blood_basket.id
  task_definition = aws_ecs_task_definition.blood_basket_task.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  deployment_maximum_percent         = 100
  deployment_minimum_healthy_percent = 0

  network_configuration {
    subnets          = [aws_subnet.prod_blood_basket_subnet_a.id, aws_subnet.prod_blood_basket_subnet_b.id]
    security_groups  = [aws_security_group.blood_basket.id]
    assign_public_ip = true
  }
}
