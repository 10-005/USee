variable "aws_profile" {
  description = "~/.aws/credentials 의 프로필 이름"
  type        = string
  default     = "s15p31d106"
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "availability_zone" {
  description = "노드 2를 둘 가용 영역 (기본 VPC의 이 AZ 서브넷을 사용)"
  type        = string
  default     = "ap-northeast-2a"
}

# ---------- 노드 2 ----------

variable "node2_instance_type" {
  type    = string
  default = "t3.large"
}

variable "node2_volume_size" {
  description = "루트 EBS 크기 (GB)"
  type        = number
  default     = 30
}

variable "ssh_allowed_cidrs" {
  description = "노드 2 SSH(비상용)를 허용할 공인 IP 목록. 예: [\"1.2.3.4/32\"]"
  type        = list(string)
}

variable "tailscale_auth_key" {
  description = "Tailscale 인증 키 (재사용 불가, tag:k3s). 최초 부팅 때 한 번만 쓰임"
  type        = string
  sensitive   = true
}

variable "k3s_url" {
  description = "노드 1 k3s API 주소 (Tailscale IP)"
  type        = string
  default     = "https://100.121.219.58:6443"
}

variable "k3s_token" {
  description = "노드 1에서 'k3s token create'로 만든 부트스트랩 토큰 (만료 있음)"
  type        = string
  sensitive   = true
}

variable "k3s_version" {
  description = "노드 1과 같은 버전이어야 한다"
  type        = string
  default     = "v1.36.5+k3s1"
}

# ---------- 예산 ----------

variable "budget_email" {
  description = "예산 알림을 받을 이메일"
  type        = string
}

variable "budget_limit_usd" {
  description = "전체 예산 (USD)"
  type        = number
  default     = 161
}
