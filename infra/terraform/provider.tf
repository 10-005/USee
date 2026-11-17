provider "aws" {
  region  = var.region
  profile = var.aws_profile

  # 태그를 지원하는 모든 리소스에 자동으로 붙는다.
  default_tags {
    tags = {
      project    = "s15p31d106"
      managed-by = "terraform"
    }
  }
}
