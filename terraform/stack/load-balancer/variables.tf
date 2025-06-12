variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-app')."
  type        = string
}

variable "vpc_id" {
  description = "The ID of the VPC where the load balancer will be deployed."
  type        = string
}

variable "subnet_ids" {
  description = "A list of subnet IDs to attach to the load balancer. Typically public subnets for internet-facing LBs or private subnets for internal LBs."
  type        = list(string)
}

variable "internal_lb" {
  description = "Specifies whether the load balancer is internal or internet-facing."
  type        = bool
  default     = false
}

variable "listener_port" {
  description = "The port on which the load balancer listens."
  type        = number
  default     = 80
}

variable "listener_protocol" {
  description = "The protocol for the load balancer listener (e.g., HTTP, HTTPS)." # ALB supports HTTP, HTTPS
  type        = string
  default     = "HTTP"
  validation {
    condition     = contains(["HTTP", "HTTPS"], var.listener_protocol)
    error_message = "Valid listener protocols for Application Load Balancer are HTTP, HTTPS."
  }
}

variable "target_port" {
  description = "The port on which the targets listen."
  type        = number
  default     = 8080 # Common application port
}

variable "target_protocol" {
  description = "The protocol for the target group (e.g., HTTP, HTTPS)." # ALB target groups support HTTP, HTTPS
  type        = string
  default     = "HTTP"
  validation {
    condition     = contains(["HTTP", "HTTPS"], var.target_protocol)
    error_message = "Valid target group protocols for Application Load Balancer are HTTP, HTTPS."
  }
}

variable "target_type" {
  description = "The type of target that you must specify when registering targets with this target group. One of 'instance', 'ip', or 'lambda'."
  type        = string
  default     = "ip" # Suitable for Fargate
  validation {
    condition     = contains(["instance", "ip", "lambda"], var.target_type)
    error_message = "Valid target types are 'instance', 'ip', or 'lambda'."
  }
}

variable "health_check_path" {
  description = "The destination for the health check request."
  type        = string
  default     = "/"
}

variable "health_check_protocol" {
  description = "The protocol the load balancer uses when performing health checks on targets. (HTTP, HTTPS)"
  type        = string
  default     = "HTTP"
  validation {
    condition     = contains(["HTTP", "HTTPS"], var.health_check_protocol)
    error_message = "Valid health check protocols for Application Load Balancer are HTTP, HTTPS."
  }
}

variable "enable_deletion_protection" {
  description = "If true, deletion of the load balancer will be disabled."
  type        = bool
  default     = false
}

variable "tags" {
  description = "A map of tags to assign to the resources."
  type        = map(string)
  default     = {}
}
