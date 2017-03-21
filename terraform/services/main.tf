module "service" {
  source              = "./ecs_service"
  service_name        = "${var.service_name}"
  cluster_id          = "${var.cluster_id}"
  task_definition_arn = "${module.task.arn}"
  vpc_id              = "${var.vpc_id}"
  container_name      = "${var.container_name}"
  container_port      = "${var.container_port}"
  listener_arn        = "${var.listener_arn}"
  path_pattern        = "${var.path_pattern}"
}

module "task" {
  source           = "./ecs_tasks"
  task_name        = "${var.task_name}"
  task_role_arn    = "${var.task_role_arn}"
  volume_name      = "${var.volume_name}"
  volume_host_path = "${var.volume_host_path}"
  image_uri        = "${var.image_uri}"
  template_name    = "${var.template_name}"
}
