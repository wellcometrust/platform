terraform {
  required_version = ">= 0.9"

  backend "s3" {
    bucket     = "platform-infra"
    key        = "platform-batch.tfstate"
    lock_table = "terraform-locktable"
    region     = "eu-west-1"
  }
}
