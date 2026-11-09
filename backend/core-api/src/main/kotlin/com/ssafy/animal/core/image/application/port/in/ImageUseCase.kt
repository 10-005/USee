package com.ssafy.animal.core.image.application.port.`in`

import com.ssafy.animal.core.image.domain.Image
import io.swagger.v3.oas.annotations.media.Schema

interface ImageUseCase {
    /** 4.1 presigned PUT 주소 발급 */
    fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl

    /** 4.2 업로드된 객체를 검증하고 images 행 생성 */
    fun confirm(userSeq: Long, imageKey: String): ConfirmedImage

    /**
     * 자기 자원에 연결할 이미지 확인(Q407). 없으면 404 IMAGE_NOT_FOUND, 업로더가 본인이 아니면(시드 NULL 포함) 403 IMAGE_NOT_OWNED
     */
    fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary

    /** 응답용 {imageSeq, imageUrl}. 행이 없으면 null */
    fun findSummary(imageSeq: Long): ImageSummary?

    /**
     * 연결을 해제한 쪽이 반영(flush)한 뒤 호출한다. 남은 참조가 없을 때만 행을 지우고(Q383),
     * 커밋되면 S3 객체를 best-effort 삭제(Q427)
     */
    fun deleteIfUnreferenced(imageSeq: Long)
}

data class ImageSummary(
    @field:Schema(description = "이미지 번호", example = "1301")
    val imageSeq: Long,
    @field:Schema(description = "공개 이미지 URL(서명·만료 없는 고정 주소)", example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val imageUrl: String,
)

data class IssueUploadUrlCommand(
    val userSeq: Long,
    val fileName: String?,
    val contentType: String,
    val byteSize: Long,
)

data class UploadUrl(
    val uploadUrl: String,
    val imageKey: String,
    val expiresInSeconds: Long,
)

data class ConfirmedImage(
    val image: Image,
    val imageUrl: String,
)
