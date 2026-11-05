# ---------- 이미지 버킷 (S15P31D106-296) ----------
# core-api가 presigned PUT으로 받는 사진을 둔다(API 명세 4장·Q443).
# images/* 만 익명 GET을 허용하고, 업로드는 presigned PUT만 가능하다(익명 쓰기 없음).

# 정책에서 ARN을 이름으로 조립해 plan에서 정책 본문이 그대로 보이게 한다(local.media_arn은 apply 후에야 알려짐)
locals {
  media_arn = "arn:aws:s3:::${aws_s3_bucket.media.bucket}"
}

resource "aws_s3_bucket" "media" {
  bucket = "s15p31d106-media-887324111755"
  # force_destroy를 켜지 않는다: 사진이 남아 있으면 destroy가 멈춘다(실수로 지우는 것 방지)
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 버전 관리는 선언하지 않는다(새 버킷 기본값 = 꺼짐).
# 켜 두면 태그 교체·삭제가 현재 버전에만 적용되어 이전 버전이 남는다.

# ACL을 끄고 버킷 소유자가 모든 객체를 소유
resource "aws_s3_bucket_ownership_controls" "media" {
  bucket = aws_s3_bucket.media.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# 버킷 정책으로만 공개한다. ACL 공개는 계속 막는다
resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "media" {
  bucket = aws_s3_bucket.media.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [local.media_arn, "${local.media_arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      },
      {
        # imageUrl = {public-base-url}/{image_key} 고정 URL(Q443). images/ 밖은 공개하지 않는다
        Sid       = "AllowPublicReadImages"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${local.media_arn}/images/*"
      },
    ]
  })

  # 퍼블릭 정책 허용(block_public_policy=false)이 먼저 적용돼야 공개 정책을 걸 수 있다
  depends_on = [aws_s3_bucket_public_access_block.media]
}

resource "aws_s3_bucket_lifecycle_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  # 4.1 PUT 때 upload=pending 태그가 붙고 4.2 확정 때 upload=confirmed로 바뀐다.
  # 확정되지 않은 업로드만 1일 뒤 삭제(UTC 자정 기준이라 실제로는 최대 2일 가까이)
  rule {
    id     = "expire-pending-uploads"
    status = "Enabled"

    filter {
      and {
        prefix = "images/"
        tags   = { upload = "pending" }
      }
    }

    expiration {
      days = 1
    }
  }
}

# 보호소 웹이 브라우저에서 presigned PUT으로 올린다. 출처는 core-api CORS_ALLOWED_ORIGINS와 같게 유지
resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  cors_rule {
    allowed_methods = ["PUT", "GET"]
    allowed_origins = [
      "http://localhost:5173",
      "http://127.0.0.1:5173",
      "https://s15p31d106-develop-web.vercel.app",
    ]
    allowed_headers = ["content-type", "x-amz-tagging", "if-none-match"]
    max_age_seconds = 3000
  }
}

# ---------- core-api 이미지 업로드용 IAM 사용자 ----------
# 노드 1이 Lightsail이라 액세스 키를 쓴다. 키는 scripts/gen-core-api-s3-secrets.sh로 만든다(상태 파일에 비밀값 없음).

resource "aws_iam_user" "core_api_media" {
  name                 = "core-api-media"
  path                 = "/s15p31d106/"
  permissions_boundary = "arn:aws:iam::887324111755:policy/s15p31d106-boundary"
  force_destroy        = true # 스크립트로 만든 액세스 키가 있어도 삭제 가능
}

resource "aws_iam_user_policy" "core_api_media" {
  name = "media-images-rw"
  user = aws_iam_user.core_api_media.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # presigned PUT(태그 포함), 4.2 HEAD·태그 교체, 마지막 참조 삭제
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:PutObjectTagging", "s3:GetObject", "s3:DeleteObject"]
        Resource = "${local.media_arn}/images/*"
      },
      {
        # 없는 키 HEAD가 403이 아니라 404로 오게 한다(IMAGE_NOT_FOUND 판별). HEAD에는 s3:prefix가 없어 조건을 걸지 않는다
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = local.media_arn
      },
    ]
  })
}
