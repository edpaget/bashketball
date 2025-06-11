
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
  tags = {
    Environment = "prod"
    Project     = "Bashketball"
  }
}

# You can then use outputs from the module, for example:
# output "vpc_id_from_module" {
#   value = module.vpc.vpc_id
# }
# output "public_subnets_from_module" {
#   value = module.vpc.public_subnet_ids
# }
