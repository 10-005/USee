# ---------- 조회 (data): 이미 있는 것을 읽기만 한다 ----------

# Canonical 공식 Ubuntu 24.04 (amd64, gp3) 중 가장 최신 AMI
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnet" "default" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = var.availability_zone
  default_for_az    = true
}

# ---------- SSH 키 ----------

# 공개 키는 비밀이 아니므로 저장소에 둔다 (맥·윈도우 어디서 실행해도 같은 값)
resource "aws_key_pair" "node2" {
  key_name   = "k15d106-node2"
  public_key = file("${path.module}/keys/k15-node2.pub")
}

# ---------- 보안 그룹 ----------

resource "aws_security_group" "node2" {
  name        = "k15d106-node2"
  description = "k3s worker node2: SSH from admin IPs, Tailscale direct UDP"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "k15d106-node2"
  }
}

# 비상용 SSH. 허용 IP 목록마다 규칙 하나씩
resource "aws_vpc_security_group_ingress_rule" "node2_ssh" {
  for_each = toset(var.ssh_allowed_cidrs)

  security_group_id = aws_security_group.node2.id
  description       = "SSH (emergency access)"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

# Tailscale 직접 연결용. 막혀 있으면 릴레이(DERP)를 거쳐 느려진다.
# WireGuard는 인증되지 않은 패킷에 응답하지 않으므로 열어도 안전하다.
resource "aws_vpc_security_group_ingress_rule" "node2_tailscale" {
  security_group_id = aws_security_group.node2.id
  description       = "Tailscale WireGuard direct"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "udp"
  from_port         = 41641
  to_port           = 41641
}

resource "aws_vpc_security_group_egress_rule" "node2_all" {
  security_group_id = aws_security_group.node2.id
  description       = "All outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ---------- EC2 ----------

resource "aws_instance" "node2" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.node2_instance_type
  subnet_id                   = data.aws_subnet.default.id
  vpc_security_group_ids      = [aws_security_group.node2.id]
  key_name                    = aws_key_pair.node2.key_name
  associate_public_ip_address = true # Elastic IP 없이 자동 공인 IP (재시작 시 바뀜, Tailscale이라 무관)

  # t3 크레딧 소진 시 추가 요금 대신 성능 제한
  credit_specification {
    cpu_credits = "standard"
  }

  # 인스턴스 메타데이터는 IMDSv2(토큰 방식)만 허용
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.node2_volume_size
    encrypted             = true
    delete_on_termination = true

    tags = {
      Name = "k15d106-node2-root"
    }
  }

  # 최초 부팅 때 한 번 실행: swap, ufw, Tailscale, k3s agent
  user_data = templatefile("${path.module}/templates/node2-init.sh.tftpl", {
    hostname           = "k15d106-node2"
    tailscale_auth_key = var.tailscale_auth_key
    k3s_url            = var.k3s_url
    k3s_token          = var.k3s_token
    k3s_version        = var.k3s_version
  })

  tags = {
    Name = "k15d106-node2"
  }

  lifecycle {
    # ami: 새 Ubuntu 이미지가 나올 때마다 노드가 교체되는 것을 막는다.
    # user_data: 일회용 키·토큰이 바뀌어도 이미 조인한 노드를 건드리지 않는다.
    ignore_changes = [ami, user_data]
  }
}
