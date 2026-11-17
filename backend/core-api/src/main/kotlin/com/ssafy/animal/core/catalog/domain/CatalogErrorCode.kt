package com.ssafy.animal.core.catalog.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장 CATALOG_ */
enum class CatalogErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    CATALOG_COMBINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 종·품종·색상 조합입니다."),
    CATALOG_INVALID_DISTRICT_CODE(HttpStatus.BAD_REQUEST, "존재하지 않는 행정구역 코드입니다."),
    CATALOG_GEOCODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "주소 검색을 일시적으로 사용할 수 없습니다."),
}
