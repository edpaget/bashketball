locals {
  provider_url = "token.actions.githubusercontent.com"
  audience     = "sts.amazonaws.com"
  subject      = "repo:edpaget/bashketball:*"

  account_id = data.aws_caller_identity.current.account_id
}

data "aws_iam_policy_document" "bashketball_github_oidc_assume_policy" {
  statement {
    sid    = "GithubOidcAuth"
    effect = "Allow"
    actions = [
      "sts:TagSession",
      "sts:AssumeRoleWithWebIdentity"
    ]

    principals {
      type        = "Federated"
      identifiers = ["arn:aws:iam::${local.account_id}:oidc-provider/${local.provider_url}"]
    }

    condition {
      test     = "ForAllValues:StringEquals"
      variable = "${local.provider_url}:iss"
      values   = ["https://${local.provider_url}"]
    }

    condition {
      test     = "ForAllValues:StringEquals"
      variable = "${local.provider_url}:aud"
      values   = [local.audience]
    }

    condition {
      test     = "StringLike"
      variable = "${local.provider_url}:sub"
      values   = [local.subject]
    }
  }
}

resource "aws_iam_role" "bashketball_github_oidc_deployment_role" {
  name        = "bashketball-github-oidc-deployment-role"
  description = "role for github oidc ecr/ecs deployment actions for bashketball"

  assume_role_policy = data.aws_iam_policy_document.bashketball_github_oidc_assume_policy.json
}

resource "aws_iam_role_policy_attachment" "bashketball_ecr_deployment_policy_attachment" {
  role       = aws_iam_role.bashketball_github_oidc_deployment_role.name
  policy_arn = aws_iam_policy.bashketball_ecr_deployment_policy.arn
}

resource "aws_iam_policy" "bashketball_ecr_deployment_policy" {
  name        = "bashketball-ecr-deployment-policy"
  description = "allow ecr deployment by github oidc"

  policy = data.aws_iam_policy_document.bashketball_ecr_deployment_policy_doc.json

}

data "aws_iam_policy_document" "bashketball_ecr_deployment_policy_doc" {
  statement {
    sid    = "ECRImagePush"
    effect = "Allow"
    actions = [
      "ecr:CompleteLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:InitiateLayerUpload",
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
    ]
    resources = [aws_ecr_repository.bashketball.arn]
  }

  statement {
    sid    = "AllowECRLogin"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy_attachment" "bashketball_ecs_deployment_policy_attachment" {
  role       = aws_iam_role.bashketball_github_oidc_deployment_role.name
  policy_arn = aws_iam_policy.bashketball_ecs_deployment_policy.arn
}

resource "aws_iam_policy" "bashketball_ecs_deployment_policy" {
  name        = "bashketball-ecs-deployment-policy"
  description = "allow ecs deployment by github oidc"

  policy = data.aws_iam_policy_document.bashketball_ecs_deployment_policy_doc.json

}

data "aws_iam_policy_document" "bashketball_ecs_deployment_policy_doc" {
  statement {
    sid    = "RegisterTaskDefinition"
    effect = "Allow"
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition"
    ]
    resources = ["*"]
  }

  statement {
    sid    = "PassRolesInTaskDefinition"
    effect = "Allow"
    actions = [
      "iam:PassRole"
    ]
    resources = [
      aws_iam_role.bashketball_ecs_execution_role.arn,
      aws_iam_role.bashketball_ecs_task_role.arn,
    ]
  }

  # statement {
  #   sid    = "DeployService"
  #   effect = "Allow"
  #   actions = [
  #     "ecs:UpdateService",
  #     "ecs:DescribeServices"
  #   ]
  #   resources = [
  #     module.bashketball_fargate_service.service_id
  #   ]
  # }
}
