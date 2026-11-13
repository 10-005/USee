# ---------- 이미지 버킷 (S15P31D106-296) ----------
# core-api가 presigned PUT으로 받는 사진을 둔다(API 명세 4장·Q443).
# 버킷은 비공개. 읽기는 CloudFront(OAC)로만 하고, 업로드는 presigned PUT만 가능하다(S15P31D106-298).

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

# 공개 접근은 모두 막는다. CloudFront 허용은 SourceArn 조건이 있어 공개 정책으로 보지 않는다
resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
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
        # imageUrl = {CDN 주소}/{image_key} 고정 URL(Q443). CDN(OAC)이 이 배포에서 온 요청으로만 images/를 읽는다(S15P31D106-298)
        Sid       = "AllowCloudFrontReadImages"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${local.media_arn}/images/*"
        Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.media.arn } }
      },
    ]
  })

  # 공개 접근 차단 설정을 먼저 적용한다(비공개 정책이라 block_public_policy=true여도 걸 수 있다)
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

# ---------- 이미지 CDN (S15P31D106-298) ----------
# imageUrl = {STORAGE_PUBLIC_BASE_URL}/{image_key}라 환경 변수만 이 주소로 바꾸면 응답이 CDN 주소가 된다(DB에는 키만 저장).
# 같은 값을 쓰는 곳: core-api·chat·simulation의 STORAGE_PUBLIC_BASE_URL, breed-server·match-worker의 IMAGE_URL_PREFIX(+ "/")
# 업로드(presigned PUT)는 계속 S3로 직접 간다. CDN은 읽기만 맡는다.
# 2단계로 전환했다: 1) CDN 추가·주소 변경 2) 버킷 익명 GET 삭제·공개 접근 차단(위 정책·차단 설정)

resource "aws_cloudfront_origin_access_control" "media" {
  name                              = "s15p31d106-media"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 키가 업로드마다 새로 만들어지는 불변 객체라 오래 캐시해도 된다(기본 1일, 최대 1년).
# 지운 이미지도 캐시가 만료될 때까지는 CDN에서 보일 수 있다(고아 객체 허용과 같은 수준, Q427)
locals {
  # AWS 관리형 Managed-CachingOptimized. 모든 계정에서 같은 고정 ID라 조회(cloudfront:ListCachePolicies)하지 않는다
  cache_policy_caching_optimized = "658327ea-f89d-4fab-a63d-7e88639e58f6"
}

resource "aws_cloudfront_distribution" "media" {
  enabled         = true
  comment         = "s15p31d106 media images"
  is_ipv6_enabled = true
  price_class     = "PriceClass_200" # 한국 포함, 남미·오세아니아 제외

  origin {
    origin_id                = "media"
    domain_name              = aws_s3_bucket.media.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.media.id
  }

  default_cache_behavior {
    target_origin_id       = "media"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = local.cache_policy_caching_optimized
    compress               = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # p.ssafy.io DNS는 우리가 관리하지 않아 기본 *.cloudfront.net 도메인을 쓴다
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
