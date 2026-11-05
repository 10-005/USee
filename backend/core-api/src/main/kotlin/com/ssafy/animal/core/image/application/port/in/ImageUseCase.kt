package com.ssafy.animal.core.image.application.port.`in`

import com.ssafy.animal.core.image.domain.Image

interface ImageUseCase {
    /** 4.1 presigned PUT 주소 발급 */
    fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl

    /** 4.2 업로드된 객체를 검증하고 images 행 생성 */
    fun confirm(userSeq: Long, imageKey: String): ConfirmedImage

    /** 마지막 참조가 사라진 이미지 삭제. 참조 확인은 호출하는 도메인 몫(Q383). 행 삭제 후 커밋되면 S3 객체를 best-effort 삭제(Q427) */
    fun delete(imageSeq: Long)
}

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
