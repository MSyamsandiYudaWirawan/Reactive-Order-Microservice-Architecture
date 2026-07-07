provider "aws" {
  region = "ap-southeast-3"
}

terraform {

  backend "s3" {
    bucket         = "reactive-order-tf-state"
    key            = "reactive-order/terraform.tfstate"  # File path in bucket
    region         = "ap-southeast-3"
    encrypt        = true
    dynamodb_table = "terraform-state-locking"
  }okay

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.99.1"
    }
  }
  required_version = "~> 1.0"
}

module "tf-state" {
  source = "./modules/tf-state"
  bucket_name = "reactive-order-tf-state"
}