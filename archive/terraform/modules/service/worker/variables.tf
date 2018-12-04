variable "env_vars" {
  type = "map"
}

variable "env_vars_length" {}

variable "vpc_id" {}

variable "subnets" {
  type = "list"
}

variable "container_image" {}

variable "namespace_id" {}

variable "cluster_name" {}
variable "cluster_id" {}

variable "service_name" {}

variable "service_egress_security_group_id" {}

variable "metric_namespace" {
  default = "storage"
}
variable "high_metric_name" {
  default = "empty"
}

variable "min_capacity" {}
variable "max_capacity" {}