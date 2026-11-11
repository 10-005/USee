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

/** 1.4 `ImageRef`(imageSeq·imageUrl·displayOrder). 작성자 프로필·댓글 사진처럼 한 장짜리도 이 형태로 내보낸다 */
@Schema(name = "ImageRef", description = "이미지 참조(1.4)")
data class OrderedImage(
    @field:Schema(description = "이미지 번호", example = "1301")
    val imageSeq: Long,
    @field:Schema(description = "공개 이미지 URL(서명·만료 없는 고정 주소)", example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val imageUrl: String,
    @field:Schema(description = "표시 순서. 한 장짜리는 1", example = "1")
    val displayOrder: Int,
)

fun ImageSummary.ordered(displayOrder: Int = 1) = OrderedImage(imageSeq, imageUrl, displayOrder)

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

/** 4.2 확정 전의 업로드 사진을 쓰는 기능(8.9 품종 추천)용. ImageUseCase와 따로 둔 것은 그쪽 가짜 구현(테스트)을 건드리지 않기 위해서다 */
interface UploadedImageUseCase {
    /**
     * 4.1로 발급받은 imageKey의 공개 URL. 발급 컨텍스트(Redis, TTL 10분)의 발급자가 요청자와 같을 때만 준다.
     * 없거나(만료·이미 확정) 남의 키면 4.2와 같이 404 IMAGE_NOT_FOUND. 객체가 실제로 올라왔는지는 확인하지 않는다
     */
    fun requireUploadedUrl(userSeq: Long, imageKey: String): String
}
