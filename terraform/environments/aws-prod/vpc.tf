
module "vpc" {
  source = "../../stack/vpc" # Relative path to the module

  name_prefix = "prod-bashketball"
  vpc_cidr_block = "10.0.0.0/16" # Can override defaults
  subnet_configurations = [
    {
      cidr_block        = "10.0.3.0/24"
      availability_zone = "us-east-2a" # Ensure this matches your desired region/AZs
      name_suffix       = "public-a"
    },
    {
      cidr_block        = "10.0.4.0/24"
      availability_zone = "us-east-2b" # Ensure this matches your desired region/AZs
      name_suffix       = "public-b"
    }
    # Add more subnets if needed
  ]
  private_subnet_configurations = [
    {
      cidr_block        = "10.0.1.0/24"
      availability_zone = "us-east-2a"
      name_suffix       = "private-a"
    },
    {
      cidr_block        = "10.0.2.0/24"
      availability_zone = "us-east-2b"
      name_suffix       = "private-b"
    }
  ]
  enable_nat_gateway = true # Assuming NAT Gateway is desired for private subnets
  tags = {
    Environment = "prod"
    Project     = "Bashketball"
  }
}
