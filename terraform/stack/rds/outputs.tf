output "cluster_id" {
  description = "The ID of the Aurora cluster."
  value       = aws_rds_cluster.this.id
}

output "cluster_arn" {
  description = "The ARN of the Aurora cluster."
  value       = aws_rds_cluster.this.arn
}

output "cluster_endpoint" {
  description = "The writer endpoint for the Aurora cluster."
  value       = aws_rds_cluster.this.endpoint
}

output "cluster_reader_endpoint" {
  description = "The reader endpoint for the Aurora cluster."
  value       = aws_rds_cluster.this.reader_endpoint
}

output "cluster_port" {
  description = "The port on which the Aurora cluster is listening."
  value       = aws_rds_cluster.this.port
}

output "cluster_master_username" {
  description = "The master username for the Aurora cluster."
  value       = aws_rds_cluster.this.master_username
}

output "cluster_database_name" {
  description = "The initial database name configured for the Aurora cluster."
  value       = aws_rds_cluster.this.database_name
}

output "instance_ids" {
  description = "List of DB instance identifiers created in the Aurora cluster."
  value       = aws_rds_cluster_instance.this[*].id
}

output "instance_endpoints" {
  description = "List of individual instance endpoints. Note: Use cluster_endpoint for writes."
  value       = aws_rds_cluster_instance.this[*].endpoint
}

output "db_subnet_group_name" {
  description = "The name of the DB subnet group created."
  value       = aws_db_subnet_group.this.name
}
