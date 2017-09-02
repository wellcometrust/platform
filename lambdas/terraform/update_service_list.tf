# Lambda for publishing ECS service status summary to S3

module "lambda_update_service_list" {
  source      = "./lambda"
  name        = "update_service_list"
  description = "Publish ECS service status summary to S3"
  source_dir  = "../src/update_service_list"
  timeout     = 10

  environment_variables = {
    BUCKET_NAME     = "${data.terraform_remote_state.platform.bucket_dashboard_id}"
    OBJECT_KEY      = "data/ecs_status.json"
    ASSUMABLE_ROLES = "${join(",", var.dashboard_assumable_roles)}"
  }

  alarm_topic_arn = "${data.terraform_remote_state.platform.lambda_error_alarm_arn}"
}

module "trigger_update_service_list" {
  source                  = "./lambda/trigger_cloudwatch"
  lambda_function_name    = "${module.lambda_update_service_list.function_name}"
  lambda_function_arn     = "${module.lambda_update_service_list.arn}"
  cloudwatch_trigger_arn  = "${aws_cloudwatch_event_rule.every_minute.arn}"
  cloudwatch_trigger_name = "${aws_cloudwatch_event_rule.every_minute.name}"
}
