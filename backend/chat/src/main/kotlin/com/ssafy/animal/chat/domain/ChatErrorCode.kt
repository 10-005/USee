package com.ssafy.animal.chat.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** 명세 19장 CHAT_ 전부 */
enum class ChatErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    CHAT_UNSUPPORTED_PARTICIPANTS(HttpStatus.CONFLICT, "지원하지 않는 대화 상대입니다."),
    CHAT_COUNTERPART_DELETED(HttpStatus.CONFLICT, "탈퇴한 상대와는 대화할 수 없습니다."),
    CHAT_INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "메시지 형식이 올바르지 않습니다."),
    CHAT_MESSAGE_NOT_IN_ROOM(HttpStatus.BAD_REQUEST, "이 대화방의 메시지가 아닙니다."),
    CHAT_ROOM_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "대화방 참여자가 아닙니다."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "대화방을 찾을 수 없습니다."),
    CHAT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "대화 상대를 찾을 수 없습니다."),
    CHAT_SHARED_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "공유한 게시글을 찾을 수 없습니다."),
}

/**
 * chat이 같이 내는 다른 도메인 코드. core-api의 AuthErrorCode·ImageErrorCode와 이름·상태가 같아야 한다(응답 code = 상수 이름).
 * ponytail: 모듈이 달라 복제했다. common으로 옮기면 이 enum을 지운다(코디네이터 요청)
 */
enum class SharedErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 올린 이미지만 사용할 수 있습니다."),
}
