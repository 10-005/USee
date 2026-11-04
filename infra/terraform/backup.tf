# ---------- DB 백업 버킷 ----------

resource "aws_s3_bucket" "backup" {
  bucket = "s15p31d106-backup-887324111755"
}

resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACL을 끄고 버킷 소유자가 모든 객체를 소유
resource "aws_s3_bucket_ownership_controls" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# HTTPS가 아닌 요청은 모두 거부
resource "aws_s3_bucket_policy" "backup" {
  bucket = aws_s3_bucket.backup.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.backup.arn, "${aws_s3_bucket.backup.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  # 퍼블릭 접근 차단이 먼저 적용된 뒤에 정책을 건다
  depends_on = [aws_s3_bucket_public_access_block.backup]
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  # 백업은 30일 보관. 버전 관리 중이라 만료 = 삭제 마커 생성 → 이전 버전은 1일 뒤 삭제
  rule {
    id     = "expire-backups"
    status = "Enabled"
    filter {}

    expiration {
      days = 30
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  # 이전 버전이 모두 지워진 뒤 남는 삭제 마커 정리 (days와 같은 블록에 둘 수 없어 따로 둔다)
  rule {
    id     = "remove-expired-delete-markers"
    status = "Enabled"
    filter {}

    expiration {
      expired_object_delete_marker = true
    }
  }

  depends_on = [aws_s3_bucket_versioning.backup]
}

# ---------- 백업 업로드 전용 IAM 사용자 ----------
# 노드 1이 Lightsail이라 EC2 역할을 쓸 수 없어 액세스 키를 쓴다.
# 액세스 키는 상태 파일에 비밀값이 남지 않도록 Terraform이 아니라 scripts/gen-backup-secrets.sh로 만든다.

resource "aws_iam_user" "backup_writer" {
  name                 = "backup-writer"
  path                 = "/s15p31d106/"
  permissions_boundary = "arn:aws:iam::887324111755:policy/s15p31d106-boundary"
  force_destroy        = true # 스크립트로 만든 액세스 키가 있어도 삭제 가능
}

# 업로드만 허용. 읽기·삭제·목록 권한이 없어 키가 새도 기존 백업을 볼 수도 지울 수도 없다
resource "aws_iam_user_policy" "backup_writer" {
  name = "postgres-backup-put"
  user = aws_iam_user.backup_writer.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "s3:PutObject"
      Resource = "${aws_s3_bucket.backup.arn}/postgres/*"
    }]
  })
}
