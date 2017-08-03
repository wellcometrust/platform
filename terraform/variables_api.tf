# We can run two versions of the API: a "production" and a "staging" API.
# The idea is that we can run the new version of the API behind a different
# hostname, then promote it to production when it's ready to go.
#
# This file contains all the different bits of config for the two versions,
# followed by heavy abuse of Terraform's ternary operator in services.tf.
#
# https://www.terraform.io/docs/configuration/interpolation.html#conditionals

# These three variables will change fairly regularly, whenever we want to
# swap the staging and production APIs.

variable "production_api" {
  description = "Which version of the API is production? (romulus | remus)"
}

variable "staging_app_version" {
  description = "ECR tag for the staging version of the API"
}

variable "staging_nginx_version" {
  description = "ECR tag for the staging version of nginx"
}

variable "staging_task_count" {
  description = "How many versions of the staging API to run"
  default     = 1
}

# These variables change less frequently -- the service blocks in services.tf
# will choose which variable to use based on the value of `production_api`.

variable "api_host" {
  description = "Hostname to use for the production API"
  default     = "api.wellcomecollection.org"
}

variable "api_host_stage" {
  description = "Hostname to use for the staging version of the API"
  default     = "api-stage.wellcomecollection.org"
}

variable "es_config" {
  description = "ElasticCloud config for the production API"
  type        = "map"
  default     = {}
}

variable "es_config_stage" {
  description = "ElasticCloud config for the staging API"
  type        = "map"
  default     = {}
}
