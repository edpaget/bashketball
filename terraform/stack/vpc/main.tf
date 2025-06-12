resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr_block
  enable_dns_hostnames = var.enable_dns_hostnames
  enable_dns_support   = var.enable_dns_support

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-vpc"
    }
  )
}

resource "aws_subnet" "public" {
  count = length(var.subnet_configurations)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.subnet_configurations[count.index].cidr_block
  availability_zone = var.subnet_configurations[count.index].availability_zone
  map_public_ip_on_launch = true # Public subnets usually map public IPs on launch

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-subnet-${var.subnet_configurations[count.index].name_suffix}"
    }
  )
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-igw"
    }
  )
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-public-rt"
    }
  )
}

resource "aws_route_table_association" "public_subnets" {
  count = length(aws_subnet.public) # Changed from var.subnet_configurations to aws_subnet.public

  subnet_id      = aws_subnet.public[count.index].id # Changed from aws_subnet.this
  route_table_id = aws_route_table.public.id
}

# --- Private Subnets, NAT Gateways, and Private Routing ---

resource "aws_subnet" "private" {
  count = length(var.private_subnet_configurations)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_configurations[count.index].cidr_block
  availability_zone = var.private_subnet_configurations[count.index].availability_zone
  map_public_ip_on_launch = false # Private subnets should not auto-assign public IPs

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-subnet-${var.private_subnet_configurations[count.index].name_suffix}"
    }
  )
}

locals {
  # AZs that need a NAT gateway and a private route table.
  # Only compute if NAT is enabled and private subnets are actually defined.
  nat_gateway_azs = var.enable_nat_gateway && length(var.private_subnet_configurations) > 0 ? distinct([
    for config in var.private_subnet_configurations : config.availability_zone
  ]) : []

  # Map AZ to the ID of the first public subnet found in that AZ, for NAT gateway placement.
  # This assumes 'var.subnet_configurations' (public) are correctly defined such that for every AZ
  # in 'nat_gateway_azs', a corresponding public subnet exists.
  az_to_public_subnet_id_for_nat = {
    for az in local.nat_gateway_azs : az => one(
      [
        # Iterate over the created public subnets to find one in the target AZ
        for i, ps_resource in aws_subnet.public : ps_resource.id
        if ps_resource.availability_zone == az
      ]
      # Error message if not exactly one public subnet is found in the AZ for NAT GW.
      # If you expect multiple public subnets per AZ and want to pick the first,
      # you could use `(...)[0]` but ensure the list is not empty. `one()` is stricter.
    )
  }

  # Map AZ to the ID of the private route table created for that AZ.
  az_to_private_route_table_id = {
    for i, az in local.nat_gateway_azs : az => aws_route_table.private[i].id
    if var.enable_nat_gateway # Ensure this map is only populated if NAT GWs are enabled
  }
}

resource "aws_eip" "nat" {
  count  = length(local.nat_gateway_azs)
  domain = "vpc" # Corrected from 'vpc', it should be just 'vpc' for AWS provider
  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-nat-eip-${local.nat_gateway_azs[count.index]}"
    }
  )
}

resource "aws_nat_gateway" "this" {
  count         = length(local.nat_gateway_azs)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = local.az_to_public_subnet_id_for_nat[local.nat_gateway_azs[count.index]]

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-nat-gw-${local.nat_gateway_azs[count.index]}"
    }
  )
  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "private" {
  count  = length(local.nat_gateway_azs)
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this[count.index].id
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-private-rt-${local.nat_gateway_azs[count.index]}"
    }
  )
}

resource "aws_route_table_association" "private_subnets_assoc" {
  # Create an association for each private subnet if NAT is enabled.
  # This assumes that if NAT is enabled, a private route table will exist for the subnet's AZ.
  # If not (e.g., no public subnet in that AZ for NAT GW), the lookup for route_table_id will fail.
  count = var.enable_nat_gateway && length(aws_subnet.private) > 0 ? length(aws_subnet.private) : 0

  subnet_id      = aws_subnet.private[count.index].id
  # Lookup the private route table ID for the AZ of the current private subnet.
  # If enable_nat_gateway is true, and a private subnet is defined, we expect its AZ
  # to be in local.az_to_private_route_table_id. If not, it's a configuration error.
  route_table_id = lookup(local.az_to_private_route_table_id, aws_subnet.private[count.index].availability_zone, null)
  # Terraform will error if route_table_id is null and this resource instance is created,
  # which is the desired behavior for highlighting misconfigurations.
}

resource "aws_security_group" "default_egress" {
  name        = "${var.name_prefix}-default-sg"
  description = "Default security group for ${var.name_prefix} allowing all outbound traffic and controlled inbound."
  vpc_id      = aws_vpc.this.id

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-default-sg"
    }
  )
}

resource "aws_vpc_security_group_egress_rule" "allow_all_traffic_ipv4" {
  security_group_id = aws_security_group.default_egress.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1" # All protocols
  description       = "Allow all outbound IPv4 traffic."
}

resource "aws_vpc_security_group_egress_rule" "allow_all_traffic_ipv6" {
  security_group_id = aws_security_group.default_egress.id
  cidr_ipv6         = "::/0"
  ip_protocol       = "-1" # All protocols
  description       = "Allow all outbound IPv6 traffic."
}
