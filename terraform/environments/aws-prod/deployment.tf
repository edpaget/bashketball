locals {
  provider_url = "token.actions.githubusercontent.com"
  audience     = "sts.amazonaws.com"
  subject      = "repo:edpaget/blood-basket:*"

  account_id = data.aws_caller_identity.current.account_id
}

data "aws_iam_policy_document" "blood_basket_github_oidc_assume_policy" {
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

resource "aws_iam_role" "blood_basket_github_oidc_deployment_role" {
  name        = "blood-basket-github-oidc-deployment-role"
  description = "role for github oidc ecr/ecs deployment actions for blood-basket"

  assume_role_policy = data.aws_iam_policy_document.blood_basket_github_oidc_assume_policy.json
}

resource "aws_iam_role_policy_attachment" "blood_basket_ecr_deployment_policy_attachment" {
  role       = aws_iam_role.blood_basket_github_oidc_deployment_role.name
  policy_arn = aws_iam_policy.blood_basket_ecr_deployment_policy.arn
}

resource "aws_iam_policy" "blood_basket_ecr_deployment_policy" {
  name        = "blood-basket-ecr-deployment-policy"
  description = "allow ecr deployment by github oidc"

  policy = data.aws_iam_policy_document.blood_basket_ecr_deployment_policy_doc.json

}

data "aws_iam_policy_document" "blood_basket_ecr_deployment_policy_doc" {
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
    resources = [aws_ecr_repository.blood_basket.arn]
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

resource "aws_iam_role_policy_attachment" "blood_basket_ecs_deployment_policy_attachment" {
  role       = aws_iam_role.blood_basket_github_oidc_deployment_role.name
  policy_arn = aws_iam_policy.blood_basket_ecs_deployment_policy.arn
}

resource "aws_iam_policy" "blood_basket_ecs_deployment_policy" {
  name        = "blood-basket-ecs-deployment-policy"
  description = "allow ecs deployment by github oidc"

  policy = data.aws_iam_policy_document.blood_basket_ecs_deployment_policy_doc.json

}

data "aws_iam_policy_document" "blood_basket_ecs_deployment_policy_doc" {
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
      aws_iam_role.blood_basket_ecs_execution_role.arn,
      aws_iam_role.blood_basket_ecs_task_role.arn,
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
  #     aws_ecs_service.blood_basket.id
  #   ]
  # }
}
