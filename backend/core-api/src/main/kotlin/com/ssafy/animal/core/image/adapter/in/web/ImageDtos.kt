package com.ssafy.animal.core.image.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class UploadUrlRequest(
    @field:Schema(description = "원본 파일 이름. 선택, 최대 255자. 저장 키에는 쓰지 않고 기록만 한다", nullable = true, example = "dog.jpg")
    @field:Size(max = 255)
    val fileName: String?,
    @field:Schema(description = "MIME 타입. image/jpeg 또는 image/png. S3 PUT 요청의 Content-Type도 이 값과 같아야 한다", example = "image/jpeg")
    @field:NotBlank
    val contentType: String?,
    @field:Schema(description = "파일 크기(바이트). 1 이상 10485760(10MB) 이하. S3 PUT 요청의 Content-Length도 이 값과 같아야 한다", example = "1234567")
    @field:NotNull
    @field:Positive
    val byteSize: Long?,
) {
    fun toCommand(userSeq: Long) = IssueUploadUrlCommand(userSeq, fileName, contentType!!, byteSize!!)
}

data class UploadUrlResponse(
    @field:Schema(description = "S3 presigned PUT 주소. 이 주소로 파일 본문을 PUT한다", example = "https://animal-bucket.s3.ap-northeast-2.amazonaws.com/images/2026/11/3f1c2a9e.jpg?X-Amz-Signature=...")
    val uploadUrl: String,
    @field:Schema(description = "서버가 만든 저장 키. 업로드 후 이미지 확정(POST /api/core/v1/images)에 보낸다", example = "images/2026/11/3f1c2a9e.jpg")
    val imageKey: String,
    @field:Schema(description = "uploadUrl과 확정 대기 정보의 유효 시간(초)", example = "600")
    val expiresInSeconds: Long,
) {
    companion object {
        fun from(uploadUrl: UploadUrl) = UploadUrlResponse(uploadUrl.uploadUrl, uploadUrl.imageKey, uploadUrl.expiresInSeconds)
    }
}

data class ConfirmImageRequest(
    @field:Schema(description = "업로드 주소 발급에서 받은 imageKey. 최대 512자", example = "images/2026/11/3f1c2a9e.jpg")
    @field:NotBlank
    @field:Size(max = 512)
    val imageKey: String?,
)

data class ImageResponse(
    @field:Schema(description = "확정된 이미지 번호. 게시물·반려동물·프로필 등 연결 API에 보낸다", example = "1001")
    val imageSeq: Long,
    @field:Schema(description = "공개 이미지 URL(서명·만료 없는 고정 주소)", example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val imageUrl: String,
    @field:Schema(description = "S3에 실제로 저장된 MIME 타입", example = "image/jpeg")
    val mimeType: String,
    @field:Schema(description = "S3에 실제로 저장된 크기(바이트)", example = "1234567")
    val byteSize: Long?,
) {
    companion object {
        fun from(confirmed: ConfirmedImage) = ImageResponse(
            imageSeq = confirmed.image.imageSeq!!,
            imageUrl = confirmed.imageUrl,
            mimeType = confirmed.image.mimeType,
            byteSize = confirmed.image.byteSize,
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class UploadUrlSuccessSchema : SuccessResponseSchema<UploadUrlResponse>()
abstract class ImageConfirmSuccessSchema : SuccessResponseSchema<ImageResponse>()
