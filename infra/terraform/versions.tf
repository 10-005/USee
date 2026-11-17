terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # 상태 파일은 S3에 두어 맥·윈도우 어느 쪽에서든 같은 상태를 쓴다.
  # backend 블록에는 변수를 쓸 수 없어 값을 직접 적는다.
  backend "s3" {
    bucket       = "s15p31d106-tfstate-887324111755-ap-northeast-2-an"
    key          = "aws/terraform.tfstate"
    region       = "ap-northeast-2"
    profile      = "s15p31d106"
    encrypt      = true
    use_lockfile = true # S3 객체(.tflock)로 잠금. DynamoDB 불필요
  }
}
