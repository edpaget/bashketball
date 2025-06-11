
module "ecs_cluster" {
  source      = "../../stack/ecs-cluster"
  name_prefix = "prod-bashketball"
  tags = {
    Environment = "prod"
    Project     = "Bashketball"
  }
}

locals {
  bashketball_container_definitions = jsonencode([{
    name      = "bashketball-container"
    essential = true
    image     = "${aws_ecr_repository.bashketball.repository_url}:main"
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/prod/bashketball"
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = "ecs"
      }
    }
    secrets = [
      {
        name      = "DISCORD_TOKEN"
        valueFrom = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/prod.discord_bot_token"
      }
    ]
    # Add portMappings if your container exposes ports
    # portMappings = [
    #   { containerPort = 8080, hostPort = 8080, protocol = "tcp" } # Example
    # ]
  }])
}

module "bashketball_fargate_service" {
  source      = "../../stack/ecs-fargate-service"
  name_prefix = "prod-bashketball"

  cluster_id = module.ecs_cluster.cluster_id

  container_definitions = local.bashketball_container_definitions
  task_cpu              = 512
  task_memory           = 1024

  execution_role_arn = aws_iam_role.bashketball_ecs_execution_role.arn
  task_role_arn      = aws_iam_role.bashketball_ecs_task_role.arn

  desired_count = 1

  subnet_ids         = [module.vpc.private_subnet_ids[0], module.vpc.private_subnet_ids[1]]
  security_group_ids = [aws_security_group.bashketball.id] # Assuming this SG is or will be named bashketball

  assign_public_ip = false

  # Using defaults for deployment percentages, but can be overridden
  # deployment_maximum_percent         = 100
  # deployment_minimum_healthy_percent = 0

  track_latest_task_definition = true # As per original config

  tags = {
    Environment = "prod"
    Project     = "Bashketball"
  }

  depends_on = [
    # Add dependencies if ECR repo or IAM roles are created in the same apply
    # aws_ecr_repository.bashketball, # Example, if ECR repo is named bashketball
    aws_iam_role.bashketball_ecs_execution_role,
    aws_iam_role.bashketball_ecs_task_role
  ]
}
