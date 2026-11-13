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

output "backup_bucket" {
  description = "PostgreSQL 백업 버킷 (CronJob의 BACKUP_BUCKET 값)"
  value       = aws_s3_bucket.backup.bucket
}

output "media_bucket" {
  description = "이미지 버킷 (core-api의 STORAGE_BUCKET 값)"
  value       = aws_s3_bucket.media.bucket
}

output "media_public_base_url" {
  description = "이미지 공개 주소, CloudFront (core-api의 STORAGE_PUBLIC_BASE_URL 값)"
  value       = "https://${aws_cloudfront_distribution.media.domain_name}"
}
