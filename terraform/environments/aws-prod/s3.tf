module "s3_bucket" {
  source = "../../stack/s3"

  # Construct a globally unique bucket name
  bucket_name = "prod-bashketball-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.name}"

  # Add any specific configurations for the S3 bucket module if needed,
  # e.g., versioning, logging, lifecycle rules, ACLs.
  # Assuming your S3 module has sensible defaults for a private bucket.

  tags = {
    Environment = "prod"
    Project     = "Bashketball"
    Service     = "Storage"
  }
}
