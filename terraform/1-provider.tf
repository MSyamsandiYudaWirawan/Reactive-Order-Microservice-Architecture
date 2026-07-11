provider "aws" {
  region = "ap-southeast-3"
}

terraform {

  # Uncomment after running: terraform apply -target=module.tf_state
  # Then run: terraform init -migrate-state
  #
  # backend "s3" {
  #   bucket         = "reactive-order-tf-state"
  #   key            = "reactive-order/terraform.tfstate"
  #   region         = "ap-southeast-3"
  #   encrypt        = true
  #   use_lockfile   = true
  # }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.99.1"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.7.2"
    }
  }
  required_version = "~> 1.0"
}

module "tf_state" {
  source      = "./module/tf_state"
  bucket_name = "reactive-order-tf-state"
}
