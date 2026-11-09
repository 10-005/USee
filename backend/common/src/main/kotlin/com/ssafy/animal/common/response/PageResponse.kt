package com.ssafy.animal.common.response

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

data class PageResponse<T>(
    @field:Schema(description = "현재 페이지 항목")
    val content: List<T>,
    @field:Schema(description = "현재 페이지 번호(0부터)", example = "0")
    val page: Int,
    @field:Schema(description = "페이지 크기", example = "20")
    val size: Int,
    @field:Schema(description = "전체 항목 수", example = "1")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수", example = "1")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지가 있는지", example = "false")
    val hasNext: Boolean,
) {
    companion object {
        fun <T : Any> of(page: Page<T>) = PageResponse(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
        )
    }
}
