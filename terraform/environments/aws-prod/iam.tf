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
      "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/prod.discord_bot_token"
    ]
  }
}

data "aws_iam_policy_document" "bashketball_ecs_task_policy_doc" {
  statement {
  }
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
