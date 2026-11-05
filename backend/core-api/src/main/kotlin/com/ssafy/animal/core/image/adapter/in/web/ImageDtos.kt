package com.ssafy.animal.core.image.adapter.`in`.web

import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class UploadUrlRequest(
    @field:Size(max = 255)
    val fileName: String?,
    @field:NotBlank
    val contentType: String?,
    @field:NotNull
    @field:Positive
    val byteSize: Long?,
) {
    fun toCommand(userSeq: Long) = IssueUploadUrlCommand(userSeq, fileName, contentType!!, byteSize!!)
}

data class UploadUrlResponse(
    val uploadUrl: String,
    val imageKey: String,
    val expiresInSeconds: Long,
) {
    companion object {
        fun from(uploadUrl: UploadUrl) = UploadUrlResponse(uploadUrl.uploadUrl, uploadUrl.imageKey, uploadUrl.expiresInSeconds)
    }
}

data class ConfirmImageRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val imageKey: String?,
)

data class ImageResponse(
    val imageSeq: Long,
    val imageUrl: String,
    val mimeType: String,
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
