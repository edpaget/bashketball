resource "aws_vpc" "prod_blood_basket_vpc" {
  cidr_block = "10.0.0.0/16"
}

resource "aws_subnet" "prod_blood_basket_subnet_a" {
  vpc_id            = aws_vpc.prod_blood_basket_vpc.id
  cidr_block        = "10.0.3.0/24"
  availability_zone = "us-east-2a"
}

resource "aws_subnet" "prod_blood_basket_subnet_b" {
  vpc_id            = aws_vpc.prod_blood_basket_vpc.id
  cidr_block        = "10.0.4.0/24"
  availability_zone = "us-east-2b"
}

resource "aws_internet_gateway" "prod_blood_basket_igw" {
  vpc_id = aws_vpc.prod_blood_basket_vpc.id
}

resource "aws_route_table" "prod_blood_basket_rt" {
  vpc_id = aws_vpc.prod_blood_basket_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.prod_blood_basket_igw.id
  }
}

resource "aws_route_table_association" "prod_blood_basket_subnet_a_rt_association" {
 subnet_id      = aws_subnet.prod_blood_basket_subnet_a.id
 route_table_id = aws_route_table.prod_blood_basket_rt.id
}

resource "aws_route_table_association" "prod_blood_basket_subnet_b_rt_association" {
 subnet_id      = aws_subnet.prod_blood_basket_subnet_b.id
 route_table_id = aws_route_table.prod_blood_basket_rt.id
}

resource "aws_security_group" "blood_basket" {
  name        = "prod_blood_basketsg"
  description = "permit external egress from blood-basket"
  vpc_id      = aws_vpc.prod_blood_basket_vpc.id
}

resource "aws_vpc_security_group_egress_rule" "allow_all_traffic_ipv4" {
  security_group_id = aws_security_group.blood_basket.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "allow_all_traffic_ipv6" {
  security_group_id = aws_security_group.blood_basket.id
  cidr_ipv6         = "::/0"
  ip_protocol       = "-1"
}
