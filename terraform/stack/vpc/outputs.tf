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
  value       = aws_subnet.this[*].id
}

output "public_subnet_cidrs" {
  description = "List of CIDR blocks of the public subnets."
  value       = aws_subnet.this[*].cidr_block
}

output "public_subnet_availability_zones" {
  description = "List of availability zones for the public subnets."
  value       = aws_subnet.this[*].availability_zone
}

output "internet_gateway_id" {
  description = "The ID of the Internet Gateway."
  value       = aws_internet_gateway.this.id
}

output "public_route_table_id" {
  description = "The ID of the public route table."
  value       = aws_route_table.public.id
}

output "default_security_group_id" {
  description = "The ID of the default security group."
  value       = aws_security_group.default_egress.id
}
