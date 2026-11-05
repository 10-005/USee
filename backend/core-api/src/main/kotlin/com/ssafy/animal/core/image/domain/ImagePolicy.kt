package com.ssafy.animal.core.image.domain

import com.ssafy.animal.common.exception.BusinessException
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** 명세 4.1: JPG/JPEG/PNG, 파일당 최대 10MB. 키는 서버가 만든다. */
object ImagePolicy {

    const val MAX_BYTES = 10L * 1024 * 1024

    // 허용 MIME → 확장자. 확장자는 클라이언트 파일명이 아니라 MIME에서 정한다
    private val EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png")

    private val KST = ZoneId.of("Asia/Seoul")

    fun validate(contentType: String, byteSize: Long) {
        if (byteSize > MAX_BYTES) throw BusinessException(ImageErrorCode.IMAGE_TOO_LARGE)
        if (contentType !in EXTENSIONS) throw BusinessException(ImageErrorCode.IMAGE_UNSUPPORTED_TYPE)
    }

    /** images/{yyyy}/{MM}/{uuid}.{ext} */
    fun newKey(contentType: String): String {
        val month = YearMonth.now(KST)
        return "images/%d/%02d/%s.%s".format(month.year, month.monthValue, UUID.randomUUID(), EXTENSIONS.getValue(contentType))
    }
}
