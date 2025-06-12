variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-bashketball')."
  type        = string
  default     = "bashketball"
}

variable "vpc_cidr_block" {
  description = "The CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_configurations" {
  description = "A list of objects, each defining a subnet's CIDR block and availability zone."
  type = list(object({
    cidr_block        = string
    availability_zone = string
    name_suffix       = string # e.g., "public-a", "public-b"
  }))
  default = [
    {
      cidr_block        = "10.0.3.0/24"
      availability_zone = "us-east-2a"
      name_suffix       = "public-a"
    },
    {
      cidr_block        = "10.0.4.0/24"
      availability_zone = "us-east-2b"
      name_suffix       = "public-b"
    }
  ]
  validation {
    condition     = alltrue([for config in var.subnet_configurations : config.cidr_block != null && config.availability_zone != null && config.name_suffix != null])
    error_message = "Each subnet configuration must have cidr_block, availability_zone, and name_suffix defined."
  }
}

variable "private_subnet_configurations" {
  description = "A list of objects, each defining a private subnet's CIDR block, availability zone, and name suffix."
  type = list(object({
    cidr_block        = string
    availability_zone = string
    name_suffix       = string # e.g., "private-a", "private-b"
  }))
  default = [] # No private subnets by default
  validation {
    condition     = alltrue([for config in var.private_subnet_configurations : config.cidr_block != null && config.availability_zone != null && config.name_suffix != null])
    error_message = "Each private subnet configuration must have cidr_block, availability_zone, and name_suffix defined."
  }
}

variable "enable_nat_gateway" {
  description = "Set to true to create a NAT Gateway for each AZ with private subnets, enabling outbound internet access for them. Requires public subnets in the same AZs."
  type        = bool
  default     = true # Default to true, assuming NAT gateway is usually desired for private subnets
}

variable "enable_dns_hostnames" {
  description = "A boolean flag to enable/disable DNS hostnames in the VPC."
  type        = bool
  default     = true
}

variable "enable_dns_support" {
  description = "A boolean flag to enable/disable DNS support in the VPC."
  type        = bool
  default     = true
}

variable "tags" {
  description = "A map of tags to assign to the resources."
  type        = map(string)
  default     = {}
}
