resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-sng"
  subnet_ids = var.subnet_ids
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-sng"
  })
}

resource "aws_rds_cluster" "this" {
  cluster_identifier              = var.name_prefix
  engine                          = "aurora-postgresql"
  engine_mode                     = "provisioned" # Standard Aurora
  engine_version                  = var.engine_version
  database_name                   = var.db_name
  master_username                 = var.master_username
  master_password                 = var.master_password
  db_subnet_group_name            = aws_db_subnet_group.this.name
  vpc_security_group_ids          = var.vpc_security_group_ids
  skip_final_snapshot             = var.skip_final_snapshot
  backup_retention_period         = var.backup_retention_period
  preferred_backup_window         = var.preferred_backup_window
  preferred_maintenance_window    = var.preferred_maintenance_window
  storage_encrypted               = var.storage_encrypted
  kms_key_id                      = var.kms_key_id
  apply_immediately               = var.apply_immediately
  deletion_protection             = var.deletion_protection
  allow_major_version_upgrade     = var.allow_major_version_upgrade
  copy_tags_to_snapshot         = var.copy_tags_to_snapshot
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"] # Common useful logs

  tags = merge(var.tags, {
    Name = var.name_prefix
  })
}

resource "aws_rds_cluster_instance" "this" {
  count                           = var.number_of_instances
  identifier                      = "${var.name_prefix}-instance-${count.index + 1}"
  cluster_identifier              = aws_rds_cluster.this.id
  instance_class                  = var.instance_class
  engine                          = "aurora-postgresql" # Must match cluster engine
  engine_version                  = aws_rds_cluster.this.engine_version # Inherit from cluster
  publicly_accessible             = var.publicly_accessible
  db_subnet_group_name            = aws_db_subnet_group.this.name
  preferred_maintenance_window    = var.preferred_maintenance_window # Can be instance-specific or inherit
  apply_immediately               = var.apply_immediately
  monitoring_interval             = var.monitoring_interval
  performance_insights_enabled    = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_retention_period
  performance_insights_kms_key_id = var.storage_encrypted ? var.kms_key_id : null # Use same KMS key if encrypted
  copy_tags_to_snapshot         = var.copy_tags_to_snapshot
  auto_minor_version_upgrade      = var.auto_minor_version_upgrade

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-instance-${count.index + 1}"
  })

  # If you have multiple instances, you might want to set promotion_tier for one of them
  # promotion_tier = count.index == 0 ? 0 : 1 # Example: first instance is primary writer
}
# DB Subnet Group
resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-sng"
  subnet_ids = var.subnet_ids
  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-sng"
    }
  )
}

# Security Group for RDS Cluster
resource "aws_security_group" "this" {
  name        = "${var.name_prefix}-rds-sg"
  description = "Security group for ${var.name_prefix} RDS cluster"
  vpc_id      = var.vpc_id

  # Ingress rule: Allow traffic from specified SGs on the DB port
  # If var.allowed_security_group_ids is empty, this block won't be created.
  dynamic "ingress" {
    for_each = var.allowed_security_group_ids
    content {
      protocol        = "tcp"
      from_port       = var.db_port
      to_port         = var.db_port
      security_groups = [ingress.value]
      description     = "Allow DB traffic from specified SG"
    }
  }

  # Default Egress: Allow all outbound traffic (common for DBs to reach KMS, etc.)
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
    description      = "Allow all outbound traffic"
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-rds-sg"
    }
  )
}

# RDS Cluster
resource "aws_rds_cluster" "this" {
  cluster_identifier              = "${var.name_prefix}-cluster"
  engine                          = var.engine
  engine_version                  = var.engine_version
  engine_mode                     = "provisioned" # Aurora Serverless v2 uses "provisioned" engine_mode
  database_name                   = var.db_name
  master_username                 = var.master_username
  master_password                 = var.master_password
  port                            = var.db_port
  db_subnet_group_name            = aws_db_subnet_group.this.name
  vpc_security_group_ids          = [aws_security_group.this.id]
  skip_final_snapshot             = var.skip_final_snapshot
  backup_retention_period         = var.backup_retention_period
  preferred_backup_window         = var.preferred_backup_window
  preferred_maintenance_window    = var.preferred_maintenance_window
  storage_encrypted               = var.storage_encrypted
  kms_key_id                      = var.kms_key_id
  deletion_protection             = var.deletion_protection
  publicly_accessible             = var.publicly_accessible
  allow_major_version_upgrade     = true # Consider parameterizing
  apply_immediately               = false # Consider parameterizing, especially for production

  serverlessv2_scaling_configuration {
    min_capacity = var.serverless_v2_min_capacity
    max_capacity = var.serverless_v2_max_capacity
  }

  # Enable Performance Insights if specified
  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_id : null # Use same KMS key if PI is enabled
  performance_insights_retention_period = var.performance_insights_enabled ? var.performance_insights_retention_period : null


  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-cluster"
    }
  )

  lifecycle {
    # master_password can be updated in-place for Aurora if managed by AWS Secrets Manager,
    # but Terraform might still see a diff if the value changes in the config.
    # ignore_changes = [master_password] # Uncomment if password is managed externally and causes diffs
  }
}

# RDS Cluster Instance(s)
# For Aurora Serverless v2, you typically define one writer instance.
# Additional reader instances can be added if needed, but scaling is primarily handled by ACUs.
resource "aws_rds_cluster_instance" "this" {
  count              = var.number_of_instances
  identifier         = "${var.name_prefix}-instance-${count.index}"
  cluster_identifier = aws_rds_cluster.this.id
  engine             = aws_rds_cluster.this.engine # Must match cluster engine
  engine_version     = aws_rds_cluster.this.engine_version # Must match cluster engine version
  instance_class     = var.instance_class        # For Serverless v2, this is 'db.serverless'
  publicly_accessible = var.publicly_accessible # Match cluster setting

  # Performance Insights settings for the instance
  # These are often inherited or controlled at the cluster level for Aurora,
  # but can be specified per instance if needed.
  # For Serverless v2, PI is generally managed at the cluster level.
  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_id : null
  performance_insights_retention_period = var.performance_insights_enabled ? var.performance_insights_retention_period : null

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-instance-${count.index}"
    }
  )

  # Note: For Aurora Serverless v2, many instance-specific parameters like
  # storage, IOPS, etc., are managed by the serverless scaling configuration.
}
