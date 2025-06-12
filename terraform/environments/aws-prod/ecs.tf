
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
        containerPort = 3000 # Port the container listens on
        protocol      = "tcp"
        # hostPort is not specified for Fargate with awsvpc network mode when using ALB
        # appProtocol can be set to "http", "http2", or "grpc" if needed by ALB features
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
  # The Fargate service will use the target security group created by the load balancer module.
  # This SG allows traffic from the LB to the tasks on the target port (3000).
  security_group_ids = [module.load_balancer.target_security_group_id]

  assign_public_ip = false # Tasks are in private subnets, accessed via LB

  load_balancers = [{
    target_group_arn = module.load_balancer.target_group_arn
    container_name   = "bashketball-container" # Must match the name in container_definitions
    container_port   = 3000                    # Must match a portMapping in container_definitions
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
