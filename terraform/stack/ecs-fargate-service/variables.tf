variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-bashketball')."
  type        = string
}

variable "service_name" {
  description = "The name of the ECS service. If not provided, it defaults to '{name_prefix}-service'."
  type        = string
  default     = ""
}

variable "task_family_name" {
  description = "The family name for the ECS task definition. If not provided, it defaults to '{name_prefix}-task'."
  type        = string
  default     = ""
}

variable "cluster_id" {
  description = "The ID of the ECS cluster where the service will be deployed."
  type        = string
}

variable "container_definitions" {
  description = "A JSON string describing the container definitions for the task. See AWS documentation for format."
  type        = string
  # Example:
  # jsonencode([{
  #   name      = "my-container"
  #   image     = "nginx:latest"
  #   essential = true
  #   portMappings = [{ containerPort = 80, hostPort = 80 }]
  #   logConfiguration = {
  #     logDriver = "awslogs"
  #     options = {
  #       awslogs-group         = "/ecs/my-app"
  #       awslogs-region        = "us-east-1"
  #       awslogs-stream-prefix = "ecs"
  #     }
  #   }
  #   secrets = [
  #     { name = "MY_SECRET", valueFrom = "arn:aws:ssm:us-east-1:123456789012:parameter/my_secret" }
  #   ]
  # }])
}

variable "task_cpu" {
  description = "The number of CPU units used by the task."
  type        = number
  default     = 256
}

variable "task_memory" {
  description = "The amount of memory (in MiB) used by the task."
  type        = number
  default     = 512
}

variable "execution_role_arn" {
  description = "The ARN of the task execution IAM role that the Amazon ECS container agent and the Docker daemon can assume."
  type        = string
}

variable "task_role_arn" {
  description = "The ARN of the IAM role that containers in this task can assume. Can be empty."
  type        = string
  default     = null
}

variable "desired_count" {
  description = "The number of instantiations of the specified task definition to place and keep running."
  type        = number
  default     = 1
}

variable "subnet_ids" {
  description = "A list of VPC subnet IDs for the task's ENIs."
  type        = list(string)
}

variable "security_group_ids" {
  description = "A list of security group IDs to associate with the task."
  type        = list(string)
}

variable "assign_public_ip" {
  description = "Specifies whether the task's ENI receives a public IP address."
  type        = bool
  default     = true
}

variable "deployment_maximum_percent" {
  description = "The upper limit (as a percentage of the service's desiredCount) of the number of tasks that are allowed in the RUNNING or PENDING state during a deployment."
  type        = number
  default     = 200 # Default for Fargate is 200
}

variable "deployment_minimum_healthy_percent" {
  description = "The lower limit (as a percentage of the service's desiredCount) of the number of tasks that must remain running and healthy in a service during a deployment."
  type        = number
  default     = 100 # Default for Fargate is 100
}

variable "track_latest_task_definition" {
  description = "If true, the service will always use the latest ACTIVE revision of the task definition."
  type        = bool
  default     = true # Matches the original ecs.tf behavior
}

variable "load_balancers" {
  description = "A list of load balancer configurations to associate with the service. Each object in the list should contain 'target_group_arn', 'container_name', and 'container_port'."
  type = list(object({
    target_group_arn = string
    container_name   = string
    container_port   = number
  }))
  default = [] # Default to no load balancers
}

variable "tags" {
  description = "A map of tags to assign to the resources."
  type        = map(string)
  default     = {}
}
