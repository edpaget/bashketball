variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-app')."
  type        = string
}

variable "vpc_id" {
  description = "The ID of the VPC where the RDS cluster will be deployed."
  type        = string
}

variable "subnet_ids" {
  description = "A list of private subnet IDs for the DB subnet group. RDS instances will be launched in these subnets."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "A list of security group IDs that are allowed to connect to the RDS cluster on the database port."
  type        = list(string)
  default     = []
}

variable "engine" {
  description = "The database engine to use. For Aurora PostgreSQL, use 'aurora-postgresql'."
  type        = string
  default     = "aurora-postgresql"
}

variable "engine_version" {
  description = "The version number of the database engine to use."
  type        = string
  default     = "15.5" # Specify a recent, supported Aurora PostgreSQL version
}

variable "db_name" {
  description = "The name of the initial database to create in the cluster. If omitted, no database is created."
  type        = string
  default     = null
}

variable "master_username" {
  description = "The username for the master database user."
  type        = string
}

variable "master_password" {
  description = "The password for the master database user. If you're managing this with a secrets manager, you might pass a dummy value and manage the actual secret outside this module or via a data source."
  type        = string
  sensitive   = true
}

variable "db_port" {
  description = "The port on which the DB accepts connections."
  type        = number
  default     = 5432
}

variable "backup_retention_period" {
  description = "The number of days to retain backups for."
  type        = number
  default     = 7
}

variable "preferred_backup_window" {
  description = "The daily time range (in UTC) during which automated backups are created if they are enabled. Eg: '07:00-09:00'"
  type        = string
  default     = "07:00-09:00"
}

variable "preferred_maintenance_window" {
  description = "The weekly time range during which system maintenance can occur, in Universal Coordinated Time (UTC). Eg: 'sun:04:00-sun:06:00'"
  type        = string
  default     = "sun:04:00-sun:06:00"
}

variable "skip_final_snapshot" {
  description = "Determines whether a final DB snapshot is created before the DB cluster is deleted."
  type        = bool
  default     = true # Set to false for production
}

variable "deletion_protection" {
  description = "If the DB instance should have deletion protection enabled."
  type        = bool
  default     = false # Set to true for production
}

variable "storage_encrypted" {
  description = "Specifies whether the DB cluster is encrypted."
  type        = bool
  default     = true
}

variable "kms_key_id" {
  description = "The ARN for the KMS encryption key. If encrypted is true and this is not specified, the default KMS key for RDS is used."
  type        = string
  default     = null
}

variable "performance_insights_enabled" {
  description = "Specifies whether Performance Insights are enabled."
  type        = bool
  default     = true
}

variable "performance_insights_retention_period" {
  description = "The amount of time in days to retain Performance Insights data. Valid values are 7 or 731 (2 years)."
  type        = number
  default     = 7
}

# Aurora Serverless v2 specific variables
variable "serverless_v2_min_capacity" {
  description = "The minimum number of Aurora Capacity Units (ACUs) for a DB instance in an Aurora Serverless v2 cluster. Valid values are 0.5-128 in 0.5 ACU increments."
  type        = number
  default     = 0.5
}

variable "serverless_v2_max_capacity" {
  description = "The maximum number of Aurora Capacity Units (ACUs) for a DB instance in an Aurora Serverless v2 cluster. Valid values are 0.5-128 in 0.5 ACU increments."
  type        = number
  default     = 2.0
}

variable "instance_class" {
  description = "The instance class to use for DB instances in the cluster. For Aurora Serverless v2, this should be a 'db.serverless' type."
  type        = string
  default     = "db.serverless" # Required for Aurora Serverless v2
}

variable "number_of_instances" {
  description = "Number of DB instances to create in the cluster. For Serverless v2, typically 1 or 2 for writer/reader."
  type        = number
  default     = 1
  validation {
    condition     = var.number_of_instances >= 1 && var.number_of_instances <= 16
    error_message = "The number_of_instances must be between 1 and 16."
  }
}

variable "tags" {
  description = "A map of tags to assign to all resources."
  type        = map(string)
  default     = {}
}
