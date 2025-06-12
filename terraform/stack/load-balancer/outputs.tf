output "lb_dns_name" {
  description = "The DNS name of the load balancer."
  value       = aws_lb.this.dns_name
}

output "lb_zone_id" {
  description = "The canonical hosted zone ID of the load balancer (to be used in a Route 53 Alias record)."
  value       = aws_lb.this.zone_id
}

output "lb_arn" {
  description = "The ARN of the load balancer."
  value       = aws_lb.this.arn
}

output "lb_security_group_id" {
  description = "The ID of the security group attached to the load balancer."
  value       = aws_security_group.lb.id
}

output "listener_arn" {
  description = "The ARN of the load balancer listener."
  value       = aws_lb_listener.this.arn
}

output "target_group_arn" {
  description = "The ARN of the load balancer target group."
  value       = aws_lb_target_group.this.arn
}

output "target_group_name" {
  description = "The name of the load balancer target group."
  value       = aws_lb_target_group.this.name
}

output "target_security_group_id" {
  description = "The ID of the security group created for the load balancer targets. This SG should be attached to your application instances/containers."
  value       = aws_security_group.target.id
}
