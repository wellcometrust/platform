module "id_minter" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs_autoscaling_service?ref=v6.4.0"
  name   = "id_minter"

  source_queue_name  = "${module.id_minter_queue.name}"
  source_queue_arn   = "${module.id_minter_queue.arn}"
  ecr_repository_url = "${module.ecr_repository_id_minter.repository_url}"
  release_id         = "${var.release_ids["id_minter"]}"

  env_vars = {
    cluster_url = "${module.identifiers_rds_cluster.host}"
    db_port     = "${module.identifiers_rds_cluster.port}"
    db_username = "${module.identifiers_rds_cluster.username}"
    db_password = "${module.identifiers_rds_cluster.password}"
    queue_url   = "${module.id_minter_queue.id}"
    topic_arn   = "${module.es_ingest_topic.arn}"
  }

  env_vars_length = 6

  cluster_name = "${aws_ecs_cluster.services.name}"
  vpc_id       = "${module.vpc_services.vpc_id}"

  alb_priority = 105

  alb_cloudwatch_id          = "${module.services_alb.cloudwatch_id}"
  alb_listener_https_arn     = "${module.services_alb.listener_https_arn}"
  alb_listener_http_arn      = "${module.services_alb.listener_http_arn}"
  alb_server_error_alarm_arn = "${local.alb_server_error_alarm_arn}"
  alb_client_error_alarm_arn = "${local.alb_client_error_alarm_arn}"
}
