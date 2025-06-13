terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1" # Specify a version for the random provider
    }
  }
}

provider "aws" {
  region     = "us-east-2"
}

module "load_balancer" {
  source = "../../stack/load-balancer"

  name_prefix = "prod-bashketball"
  vpc_id      = module.vpc.vpc_id
  subnet_ids  = module.vpc.public_subnet_ids # For an internet-facing ALB

  internal_lb       = false
  listener_port     = 80 # ALB listens on port 80
  listener_protocol = "HTTP"
  target_port       = 3000 # Fargate tasks listen on port 3000
  target_protocol   = "HTTP"
  target_type       = "ip" # Required for Fargate

  health_check_path     = "/" # Default, adjust if your app has a specific health check endpoint
  health_check_protocol = "HTTP"

  # enable_deletion_protection = true # Consider for production environments

  tags = {
    Environment = "prod"
    Project     = "Bashketball"
  }
}
