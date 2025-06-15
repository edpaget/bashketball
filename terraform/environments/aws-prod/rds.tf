resource "random_password" "db_master_password" {
  length           = 16
  special          = true
  override_special = "_%@" # Characters to use for special, excluding those problematic for some DBs or connection strings
}

resource "aws_secretsmanager_secret" "db_master_password_secret" {
  name        = "prod/bashketball/db_master_password"
  description = "Master password for the Bashketball production Aurora Serverless database."
  tags = {
    Environment = "prod"
    Project     = "Bashketball"
    Service     = "DatabaseCredentials"
  }
}

resource "aws_secretsmanager_secret_version" "db_master_password_secret_version" {
  secret_id     = aws_secretsmanager_secret.db_master_password_secret.id
  secret_string = random_password.db_master_password.result
}

resource "aws_secretsmanager_secret" "db_jdbc_url_secret" {
  name        = "prod/bashketball/db_jdbc_url"
  description = "JDBC connection URL for the Bashketball production Aurora Serverless database."
  tags = {
    Environment = "prod"
    Project     = "Bashketball"
    Service     = "DatabaseConnection"
  }
}

resource "aws_secretsmanager_secret_version" "db_jdbc_url_secret_version" {
  secret_id = aws_secretsmanager_secret.db_jdbc_url_secret.id
  secret_string = format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",
    module.rds_aurora_serverless.cluster_endpoint,
    module.rds_aurora_serverless.cluster_port,
    module.rds_aurora_serverless.cluster_database_name,
    module.rds_aurora_serverless.cluster_master_username,
    random_password.db_master_password.result
  )
  lifecycle {
    ignore_changes = [secret_string] # To prevent diffs if password changes and RDS module outputs are not yet updated in the same plan
  }
  depends_on = [module.rds_aurora_serverless, aws_secretsmanager_secret_version.db_master_password_secret_version]
}


module "rds_aurora_serverless" {
  source = "../../stack/rds"

  name_prefix     = "prod-bashketball-db"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  # Allow access from the Fargate tasks' security group
  allowed_security_group_ids = [module.load_balancer.target_security_group_id]

  engine         = "aurora-postgresql"
  engine_version = "15.5" # Or your desired version, matching stack default
  db_name        = "bashketballdb" # Initial database name

  master_username = "dbadmin"
  master_password = random_password.db_master_password.result

  db_port = 5432 # Default PostgreSQL port

  # Aurora Serverless v2 specific settings (using defaults from your rds stack variables)
  instance_class             = "db.serverless"
  serverless_v2_min_capacity = 0
  serverless_v2_max_capacity = 2.0 # Adjust as needed
  number_of_instances        = 1   # Typically 1 writer for Serverless v2, can be more

  # General RDS settings (can be adjusted or rely on module defaults)
  backup_retention_period = 7
  skip_final_snapshot     = false # Recommended for prod
  deletion_protection     = true  # Recommended for prod
  storage_encrypted       = true
  publicly_accessible     = false

  performance_insights_enabled = true

  tags = {
    Environment = "prod"
    Project     = "Bashketball"
    Service     = "Database"
  }

  depends_on = [
    module.vpc,
    module.load_balancer # To ensure its security group output is available
  ]
}
