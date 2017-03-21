module "transformer" {
  source         = "./services"
  service_name   = "transformer"
  cluster_id     = "${aws_ecs_cluster.services.id}"
  task_name      = "transformer"
  task_role_arn  = "${module.ecs_services_iam.task_role_arn}"
  container_name = "transformer"
  container_port = "8888"
  vpc_id         = "${module.vpc_services.vpc_id}"
  image_uri      = "${aws_ecr_repository.transformer.repository_url}:${var.release_id}"
  listener_arn   = "${module.services_alb.listener_arn}"
  path_pattern   = "/transformer/*"
}

module "api" {
  source         = "./services"
  service_name   = "api"
  cluster_id     = "${aws_ecs_cluster.api.id}"
  task_name      = "api"
  task_role_arn  = "${module.ecs_api_iam.task_role_arn}"
  container_name = "api"
  container_port = "8888"
  vpc_id         = "${module.vpc_api.vpc_id}"
  image_uri      = "${aws_ecr_repository.api.repository_url}:${var.release_id}"
  listener_arn   = "${module.api_alb.listener_arn}"
}

module "jenkins" {
  source           = "./services"
  service_name     = "jenkins"
  cluster_id       = "${aws_ecs_cluster.tools.id}"
  task_name        = "jenkins"
  task_role_arn    = "${module.ecs_tools_iam.task_role_arn}"
  container_name   = "jenkins"
  container_port   = "8080"
  vpc_id           = "${module.vpc_tools.vpc_id}"
  volume_name      = "jenkins-home"
  volume_host_path = "/mnt/efs"
  template_name    = "jenkins"
  listener_arn     = "${module.tools_alb.listener_arn}"
}
