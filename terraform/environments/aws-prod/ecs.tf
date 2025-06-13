
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
    portMappings = [
      {
        containerPort = 3000
        protocol      = "http"
      }
    ]
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
        name      = "DATABASE_URL"
        valueFrom = aws_secretsmanager_secret.db_jdbc_url_secret.arn # Referencing the Secrets Manager secret ARN
      }
    ]
  },
  {
    name      = "bashketball-migrate-container"
    essential = false
    image     = "${aws_ecr_repository.bashketball.repository_url}:main"
    command    = ["migrate"]
    logConfiguration = { # Same log configuration as the main app
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/prod/bashketball"
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = "ecs-migrate" # Different prefix for migration logs
      }
    }
    secrets = [ # Same secrets as the main app for DB access
      {
        name      = "DATABASE_URL"
        valueFrom = aws_secretsmanager_secret.db_jdbc_url_secret.arn
      }
    ]
    # No portMappings needed for a migration task
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
  security_group_ids = [module.load_balancer.target_security_group_id]

  assign_public_ip = false

  load_balancers = [{
    target_group_arn = module.load_balancer.target_group_arn
    container_name   = "bashketball-container"
    container_port   = 3000
  }]

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
