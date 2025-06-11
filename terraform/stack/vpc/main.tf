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

resource "aws_subnet" "this" {
  count = length(var.subnet_configurations)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.subnet_configurations[count.index].cidr_block
  availability_zone = var.subnet_configurations[count.index].availability_zone

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

resource "aws_route_table_association" "public" {
  count = length(var.subnet_configurations)

  subnet_id      = aws_subnet.this[count.index].id
  route_table_id = aws_route_table.public.id
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
