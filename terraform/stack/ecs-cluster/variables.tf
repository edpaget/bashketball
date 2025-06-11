variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-bashketball')."
  type        = string
}

variable "cluster_name" {
  description = "The name of the ECS cluster. If not provided, it defaults to '{name_prefix}-cluster'."
  type        = string
  default     = ""
}

variable "tags" {
  description = "A map of tags to assign to the cluster."
  type        = map(string)
  default     = {}
}
