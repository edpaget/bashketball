output "vpc_id" {
  description = "The ID of the VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "The CIDR block of the VPC."
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "List of IDs of the public subnets."
  value       = aws_subnet.public[*].id # Changed from aws_subnet.this
}

output "public_subnet_cidrs" {
  description = "List of CIDR blocks of the public subnets."
  value       = aws_subnet.public[*].cidr_block # Changed from aws_subnet.this
}

output "public_subnet_availability_zones" {
  description = "List of availability zones for the public subnets."
  value       = aws_subnet.public[*].availability_zone # Changed from aws_subnet.this
}

output "internet_gateway_id" {
  description = "The ID of the Internet Gateway."
  value       = aws_internet_gateway.this.id
}

output "public_route_table_id" {
  description = "The ID of the public route table."
  value       = aws_route_table.public.id
}

output "private_subnet_ids" {
  description = "List of IDs of the private subnets."
  value       = aws_subnet.private[*].id
}

output "private_subnet_cidrs" {
  description = "List of CIDR blocks of the private subnets."
  value       = aws_subnet.private[*].cidr_block
}

output "private_subnet_availability_zones" {
  description = "List of availability zones for the private subnets."
  value       = aws_subnet.private[*].availability_zone
}

output "nat_gateway_public_ips" {
  description = "List of public Elastic IP addresses for the NAT Gateways (one per AZ with private subnets if enabled)."
  value       = var.enable_nat_gateway ? aws_eip.nat[*].public_ip : []
}

output "nat_gateway_ids" {
  description = "List of IDs of the NAT Gateways (one per AZ with private subnets if enabled)."
  value       = var.enable_nat_gateway ? aws_nat_gateway.this[*].id : []
}

output "private_route_table_ids" {
  description = "List of IDs of the private route tables (one per AZ with NAT Gateway if enabled)."
  value       = var.enable_nat_gateway ? aws_route_table.private[*].id : []
}

output "default_security_group_id" {
  description = "The ID of the default security group."
  value       = aws_security_group.default_egress.id
}
