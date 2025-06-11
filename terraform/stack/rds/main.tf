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

  # Ensure password is set if not using manage_master_user_password
  lifecycle {
    ignore_changes = [
      # If manage_master_user_password is set to true in the future, this might be needed.
      # For now, password changes are handled by Terraform.
    ]
  }
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
