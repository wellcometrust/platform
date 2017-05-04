module "id_minter_topic" {
  source = "./sns"
  name   = "id_minter"
}

module "es_ingest_topic" {
  source = "./sns"
  name   = "es_ingest"
}

module "service_scheduler_topic" {
  source = "./sns"
  name   = "service_scheduler"
}
