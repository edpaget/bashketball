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

variable "publicly_accessible" {
  description = "Bool to control if instance is publicly accessible. Default false."
  type        = bool
  default     = false
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
