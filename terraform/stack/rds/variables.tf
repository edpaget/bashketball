variable "name_prefix" {
  description = "A prefix used for naming resources (e.g., 'prod-app-db')."
  type        = string
}

variable "engine_version" {
  description = "The Aurora PostgreSQL engine version."
  type        = string
  default     = "15.5" # Specify a recent, stable version
}

variable "instance_class" {
  description = "The instance class for the Aurora DB instances."
  type        = string
  default     = "db.t3.medium" # A general-purpose, cost-effective default
}

variable "number_of_instances" {
  description = "Number of DB instances to create in the Aurora cluster. Minimum 1."
  type        = number
  default     = 1
  validation {
    condition     = var.number_of_instances >= 1
    error_message = "The number_of_instances must be at least 1."
  }
}

variable "db_name" {
  description = "The name of the initial database to create in the Aurora cluster."
  type        = string
  default     = "appdb" # A generic default database name
}

variable "master_username" {
  description = "The master username for the Aurora cluster."
  type        = string
  default     = "auroraadmin"
}

variable "master_password" {
  description = "The master password for the Aurora cluster. Must be 8 to 128 printable ASCII characters (excluding /, \", @, and space)."
  type        = string
  sensitive   = true
  # No default for password; it must be provided.
  # Consider using a secrets manager in a real-world scenario.
}

variable "vpc_id" {
  description = "The ID of the VPC where the Aurora cluster will be deployed."
  type        = string
}

variable "subnet_ids" {
  description = "A list of private subnet IDs for the DB subnet group."
  type        = list(string)
  # No default; must be provided by the calling module.
}

variable "vpc_security_group_ids" {
  description = "A list of VPC security group IDs to associate with the Aurora cluster."
  type        = list(string)
  # No default; must be provided. The SGs should allow ingress on PostgreSQL port.
}

variable "backup_retention_period" {
  description = "The number of days to retain automated backups."
  type        = number
  default     = 7
}

variable "preferred_backup_window" {
  description = "The daily time range (in UTC) during which automated backups are created if automated backups are enabled."
  type        = string
  default     = "07:00-09:00"
}

variable "preferred_maintenance_window" {
  description = "The weekly time range (in UTC) during which system maintenance can occur."
  type        = string
  default     = "sun:04:00-sun:06:00" # Example: Sunday 4-6 AM UTC
}

variable "skip_final_snapshot" {
  description = "Determines whether a final DB snapshot is created before the DB cluster is deleted."
  type        = bool
  default     = false # Safer default for production-like environments
}

variable "publicly_accessible" {
  description = "Specifies if the DB instances in the cluster are publicly accessible."
  type        = bool
  default     = false # Databases should generally not be public
}

variable "deletion_protection" {
  description = "If the DB cluster should have deletion protection enabled."
  type        = bool
  default     = true # Safer default for production-like environments
}

variable "storage_encrypted" {
  description = "Specifies whether the DB cluster is encrypted."
  type        = bool
  default     = true
}

variable "kms_key_id" {
  description = "The ARN of the KMS key to use for encryption. If not specified, uses the default KMS key."
  type        = string
  default     = null
}

variable "apply_immediately" {
  description = "Specifies whether any cluster modifications are applied immediately, or during the next maintenance window."
  type        = bool
  default     = false
}

variable "monitoring_interval" {
  description = "The interval, in seconds, between points when Enhanced Monitoring metrics are collected for the DB instance. Valid values are 0, 1, 5, 10, 15, 30, 60."
  type        = number
  default     = 60 # 0 to disable
}

variable "performance_insights_enabled" {
  description = "Specifies whether Performance Insights are enabled."
  type        = bool
  default     = true
}

variable "performance_insights_retention_period" {
  description = "The amount of time, in days, to retain Performance Insights data. Valid values are 7 or 731 (2 years)."
  type        = number
  default     = 7
  validation {
    condition     = contains([7, 731], var.performance_insights_retention_period)
    error_message = "Performance Insights retention period must be 7 or 731 days."
  }
}

variable "copy_tags_to_snapshot" {
  description = "Specifies whether to copy all cluster tags to snapshots."
  type        = bool
  default     = true
}

variable "allow_major_version_upgrade" {
  description = "Enable to allow major version upgrades when changing engine versions. Defaults to true."
  type        = bool
  default     = true
}

variable "auto_minor_version_upgrade" {
  description = "Indicates that minor engine upgrades will be applied automatically to the DB instance during the maintenance window."
  type        = bool
  default     = true
}

variable "tags" {
  description = "A map of tags to assign to all resources."
  type        = map(string)
  default     = {}
}
