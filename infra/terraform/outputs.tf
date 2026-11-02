output "node2_instance_id" {
  value = aws_instance.node2.id
}

output "node2_public_ip" {
  description = "재시작하면 바뀐다"
  value       = aws_instance.node2.public_ip
}

output "node2_ssh" {
  description = "비상용 SSH 접속 명령"
  value       = "ssh -i ~/.ssh/k15-node2 ubuntu@${aws_instance.node2.public_ip}"
}
