provider "aws" {
  region  = "${var.aws_region}"
  version = "1.23.0"
}

provider "aws" {
  region = "us-east-1"
  alias  = "us_east_1"
}
