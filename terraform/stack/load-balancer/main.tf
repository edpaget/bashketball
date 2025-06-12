# Security Group for the Load Balancer itself
resource "aws_security_group" "lb" {
  name        = "${var.name_prefix}-lb-sg"
  description = "Security group for ${var.name_prefix} load balancer"
  vpc_id      = var.vpc_id

  ingress {
    protocol         = "tcp" # HTTP/HTTPS listeners use TCP
    from_port        = var.listener_port
    to_port          = var.listener_port
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
    description      = "Allow inbound traffic on listener port"
  }

  # Allow all outbound traffic
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1" # All protocols
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
    description      = "Allow all outbound traffic"
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-lb-sg"
    }
  )
}

# Security Group for the Load Balancer Targets
# This SG allows traffic from the Load Balancer's SG on the target port
resource "aws_security_group" "target" {
  name        = "${var.name_prefix}-target-sg"
  description = "Security group for ${var.name_prefix} load balancer targets"
  vpc_id      = var.vpc_id

  # Ingress rule: Allow traffic from the LB's security group on the target port
  ingress {
    protocol        = "tcp" # HTTP/HTTPS targets use TCP
    from_port       = var.target_port
    to_port         = var.target_port
    security_groups = [aws_security_group.lb.id] # Source is the LB's SG
    description     = "Allow traffic from LB to targets on target port"
  }

  # Allow all outbound traffic (typical for application targets, can be restricted if needed)
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
    description      = "Allow all outbound traffic from targets"
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-target-sg"
    }
  )
}


# Application Load Balancer
resource "aws_lb" "this" {
  name               = "${var.name_prefix}-alb"
  internal           = var.internal_lb
  load_balancer_type = "application"
  security_groups    = [aws_security_group.lb.id]
  subnets            = var.subnet_ids

  enable_deletion_protection = var.enable_deletion_protection

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-alb"
    }
  )
}

# Target Group
resource "aws_lb_target_group" "this" {
  name        = substr("${var.name_prefix}-tg", 0, 32) # Max 32 chars for TG name
  port        = var.target_port
  protocol    = var.target_protocol
  vpc_id      = var.vpc_id
  target_type = var.target_type

  health_check {
    enabled             = true
    path                = var.health_check_path
    protocol            = var.health_check_protocol
    port                = "traffic-port" # Uses the port of a target, can be var.target_port
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5  # seconds
    interval            = 30 # seconds
    matcher             = "200-399" # For HTTP/HTTPS, consider successful responses
  }

  tags = merge(
    var.tags,
    {
      Name = substr("${var.name_prefix}-tg", 0, 32)
    }
  )
}

# Listener
resource "aws_lb_listener" "this" {
  load_balancer_arn = aws_lb.this.arn
  port              = var.listener_port
  protocol          = var.listener_protocol

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  # If HTTPS, you would add certificate_arn here
  # certificate_arn = var.listener_protocol == "HTTPS" ? var.certificate_arn : null

  tags = merge(
    var.tags,
    {
      Name = "${var.name_prefix}-listener-${var.listener_port}"
    }
  )
}
