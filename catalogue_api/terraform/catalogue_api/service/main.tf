module "catalogue_api" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/prebuilt/rest?ref=cfbc6c413003f953768e2ff97f47fad3f1f68ea5"

  vpc_id = "${var.vpc_id}"
  subnets = ["${var.subnets}"]
  cluster_name = "${var.cluster_name}"
  service_name = "${var.namespace}-catalogue-api"
  namespace_id = "${var.namespace_id}"

  container_image = "${var.container_image}"
  container_port  = "${var.container_port}"

  env_vars = {
    api_host    = "api.wellcomecollection.org"
    es_host     = "${data.template_file.es_cluster_host.rendered}"
    es_port     = "${var.es_cluster_credentials["port"]}"
    es_username = "${var.es_cluster_credentials["username"]}"
    es_password = "${var.es_cluster_credentials["password"]}"
    es_protocol = "${var.es_cluster_credentials["protocol"]}"
    es_index_v1 = "${var.es_config["index_v1"]}"
    es_index_v2 = "${var.es_config["index_v2"]}"
    es_doc_type = "${var.es_config["doc_type"]}"
  }

  env_vars_length = "9"

  security_group_ids = ["${var.security_group_ids}"]
  service_egress_security_group_id = "${var.service_egress_security_group_id}"
}