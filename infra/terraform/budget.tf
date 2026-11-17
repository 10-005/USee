resource "aws_budgets_budget" "monthly" {
  name         = "s15p31d106-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 크레딧으로 상쇄되기 전의 실제 사용액을 본다.
  # (기본값은 크레딧 차감 후 금액이라, 크레딧이 있으면 알림이 오지 않을 수 있음)
  cost_types {
    include_credit = false
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    notification_type          = "ACTUAL"
    threshold                  = 100
    threshold_type             = "ABSOLUTE_VALUE"
    subscriber_email_addresses = [var.budget_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    notification_type          = "ACTUAL"
    threshold                  = 140
    threshold_type             = "ABSOLUTE_VALUE"
    subscriber_email_addresses = [var.budget_email]
  }

  # 이번 달 예상 사용액이 전체 예산을 넘을 것 같으면 미리 알림
  notification {
    comparison_operator        = "GREATER_THAN"
    notification_type          = "FORECASTED"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    subscriber_email_addresses = [var.budget_email]
  }
}
