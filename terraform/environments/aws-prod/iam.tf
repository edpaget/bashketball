resource "aws_iam_role" "bashketball_ecs_execution_role" {
  name        = "bashketball-ecs-execution-role"
  description = "ecs execution role for the bashketball application"

  assume_role_policy = data.aws_iam_policy_document.bashketball_ecs_task_assume_role_doc.json
}

resource "aws_iam_role" "bashketball_ecs_task_role" {
  name        = "bashketball-ecs-task-role"
  description = "ecs task role for the bashketball application"

  assume_role_policy = data.aws_iam_policy_document.bashketball_ecs_task_assume_role_doc.json
}

data "aws_iam_policy_document" "bashketball_ecs_task_assume_role_doc" {
  statement {
    sid    = "ECSTaskAssumeRole"
    effect = "Allow"
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "bashketball_ecs_execution_policy_doc" {
  statement {
    sid    = "ECSExecutionRole"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["*"]
  }

  statement {
    sid    = "GetSSMSecrets"
    effect = "Allow"
    actions = [
      "ssm:GetParameters",
    ]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/prod.discord_bot_token" # Keeping existing SSM access if still needed for other params
    ]
  }

  statement {
    sid    = "GetJDBCSecretValue"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue"
    ]
    resources = [
      aws_secretsmanager_secret.db_jdbc_url_secret.arn # ARN of the JDBC URL secret
    ]
  }
}

data "aws_iam_policy_document" "bashketball_ecs_task_policy_doc" {
  statement {
    sid    = "AllowS3BucketAccess"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject"
    ]
    resources = [
      module.s3_bucket.bucket_arn, # ARN for the bucket itself (e.g., for listing if needed, though not strictly for Get/PutObject)
      "${module.s3_bucket.bucket_arn}/*" # ARN for objects within the bucket
    ]
  }
  # Add other statements here if the task role needs more permissions in the future
}

resource "aws_iam_policy" "bashketball_ecs_execution_policy" {
  name        = "bashketball-ecs-execution-policy"
  description = "Allow ecs execution by github oidc" # Consider updating description if it's not related to github oidc for this specific policy

  policy = data.aws_iam_policy_document.bashketball_ecs_execution_policy_doc.json

}

resource "aws_iam_policy" "bashketball_ecs_task_policy" {
  name        = "bashketball-ecs-task-policy"
  description = "Allow ecs task by github oidc" # Consider updating description if it's not related to github oidc for this specific policy

  policy = data.aws_iam_policy_document.bashketball_ecs_task_policy_doc.json

}

resource "aws_iam_role_policy_attachment" "bashketball_ecs_execution_policy_attachment" {
  role       = aws_iam_role.bashketball_ecs_execution_role.name
  policy_arn = aws_iam_policy.bashketball_ecs_execution_policy.arn
}

resource "aws_iam_role_policy_attachment" "bashketball_ecs_task_policy_attachment" {
  role       = aws_iam_role.bashketball_ecs_task_role.name
  policy_arn = aws_iam_policy.bashketball_ecs_task_policy.arn
}
