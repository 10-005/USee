package com.ssafy.animal.common.exception

import org.springframework.http.HttpStatus

/**
 * 에러 코드 규약. 공통 코드는 CommonErrorCode, 도메인 코드는 각 도메인 패키지에서
 * 이 인터페이스를 구현하는 enum으로 만든다. 응답의 code에는 enum 상수 이름이 들어간다.
 */
interface ErrorCode {
    val name: String
    val status: HttpStatus
    val message: String
}
