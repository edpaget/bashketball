output "service_id" {
  description = "The ID of the ECS service."
  value       = aws_ecs_service.this.id
}

output "service_name" {
  description = "The name of the ECS service."
  value       = aws_ecs_service.this.name
}

output "task_definition_arn" {
  description = "The ARN of the ECS task definition."
  value       = aws_ecs_task_definition.this.arn
}

output "task_definition_family" {
  description = "The family of the ECS task definition."
  value       = aws_ecs_task_definition.this.family
}

output "task_definition_revision" {
  description = "The revision of the ECS task definition."
  value       = aws_ecs_task_definition.this.revision
}
