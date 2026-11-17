package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.notification.application.port.`in`.DeviceTokenResult
import com.ssafy.animal.notification.application.port.`in`.NotificationItem
import com.ssafy.animal.notification.application.port.`in`.NotificationUseCase
import com.ssafy.animal.notification.application.port.`in`.ReadAllResult
import com.ssafy.animal.notification.application.port.`in`.ReadResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 17장 알림·FCM. 모두 로그인(USER·SHELTER), 수신자 본인 것만 */
@Tag(name = "알림", description = "내 알림 목록·읽음·삭제와 FCM 기기 토큰 등록·해제. 모두 본인 알림만 다룬다. 채팅·모의 입양 푸시는 저장하지 않아 목록에 없다")
@RestController
@RequestMapping("/api/notification/v1/notifications")
class NotificationController(
    private val notificationUseCase: NotificationUseCase,
) {

    /** 17.1 필터 isRead·notificationType, 정렬 regDttm만 */
    @Operation(
        summary = "내 알림 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 삭제하지 않은 내 알림만 준다.\n" +
            "- 필터 `isRead`·`notificationType`은 각각 선택. 생략하면 전체.\n" +
            "- `sort` 허용 필드: `regDttm`. 기본 `regDttm,desc`(최신순). `size` 기본 20.\n" +
            "- 탭했을 때: `targetType`·`targetSeq`로 상세 화면을 연다. COMMENT·COMMENT_REPLY는 게시글 상세와 댓글 단건(`commentSeq`)을 함께 조회한다.\n" +
            "- 읽음 처리는 `PATCH /api/notification/v1/notifications/{notificationSeq}/read`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 알림이 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "알림 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"notificationSeq":806,"notificationType":"COMMENT_REPLY","title":"회원님의 댓글에 답글이 달렸어요","message":"와 정말 다행이네요!","targetType":"MISSING","targetSeq":101,"commentSeq":45,"parentCommentSeq":44,"missingReportSeq":null,"isRead":false,"readDttm":null,"regDttm":"2026-10-30T09:12:00+09:00"},{"notificationSeq":805,"notificationType":"AI_MATCH","title":"내 아이 관련 제보가 올라왔어요!","message":"대구광역시 중구에서 목격제보가 올라왔어요. 확인해보세요.","targetType":"FOUND","targetSeq":210,"commentSeq":null,"parentCommentSeq":null,"missingReportSeq":101,"isRead":true,"readDttm":"2026-10-29T18:00:00+09:00","regDttm":"2026-10-29T17:50:00+09:00"}],"page":0,"size":20,"totalElements":2,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용하지 않는 sort 필드, 또는 isRead·notificationType 값 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "정렬 필드 오류", value = INVALID_SORT),
                        ExampleObject(name = "notificationType 값 오류", value = INVALID_NOTIFICATION_TYPE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "읽음 여부 필터(선택). 생략하면 전체", example = "false")
        @RequestParam(required = false) isRead: Boolean?,
        @Parameter(description = "알림 유형 필터(선택). 생략하면 전체", example = "AI_MATCH")
        @RequestParam(required = false) notificationType: NotificationType?,
        pageable: Pageable,
    ) = notificationUseCase.list(user.userSeq, isRead, notificationType, pageable)

    /** 17.2 */
    @Operation(
        summary = "알림 읽음 처리",
        description = "- 로그인 필요. 받은 사람 본인만.\n" +
            "- 멱등: 이미 읽은 알림이면 처음 읽은 시각을 그대로 두고 200.\n" +
            "- 다른 사람의 알림이거나 삭제한 알림이면 404.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "처리 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationReadSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "읽음 처리",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"notificationSeq":805,"isRead":true,"readDttm":"2026-10-29T18:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "notificationSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "notificationSeq 타입 오류", value = INVALID_NOTIFICATION_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 알림, 다른 사람의 알림, 또는 삭제한 알림",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "NOTIFICATION_NOT_FOUND", value = NOTIFICATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PatchMapping("/{notificationSeq}/read")
    fun read(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "알림 번호", example = "805") @PathVariable notificationSeq: Long,
    ) = notificationUseCase.read(user.userSeq, notificationSeq)

    /** 17.3 */
    @Operation(
        summary = "전체 읽음 처리",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 삭제하지 않은 내 안 읽은 알림을 모두 읽음으로 바꾼다. 읽은 시각은 서버 시각.\n" +
            "- 멱등: 바꿀 알림이 없으면 `updatedCount=0`으로 200.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "처리 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationReadAllSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "전체 읽음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"updatedCount":5},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PatchMapping("/read-all")
    fun readAll(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) = notificationUseCase.readAll(user.userSeq)

    /** 17.4 */
    @Operation(
        summary = "알림 삭제",
        description = "- 로그인 필요. 받은 사람 본인만.\n" +
            "- 소프트 삭제. 삭제한 알림은 목록에서 빠진다.\n" +
            "- 다른 사람의 알림이거나 이미 삭제한 알림이면 404.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "notificationSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "notificationSeq 타입 오류", value = INVALID_NOTIFICATION_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 알림, 다른 사람의 알림, 또는 이미 삭제한 알림",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "NOTIFICATION_NOT_FOUND", value = NOTIFICATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{notificationSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "알림 번호", example = "805") @PathVariable notificationSeq: Long,
    ) {
        notificationUseCase.delete(user.userSeq, notificationSeq)
    }

    /** 17.5 */
    @Operation(
        summary = "전체 알림 삭제",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 내 알림을 모두 소프트 삭제한다.\n" +
            "- 멱등: 지울 알림이 없어도 204.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAll(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) {
        notificationUseCase.deleteAll(user.userSeq)
    }

    /** 17.6 upsert라 201이 아니라 200(Q415 예외) */
    @Operation(
        summary = "FCM 기기 토큰 등록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- upsert라 201이 아니라 200. 409는 없다.\n" +
            "- 계정당 활성 토큰은 하나. 다른 토큰을 보내면 기존 토큰을 끄고 새 토큰을 켠다.\n" +
            "- 같은 토큰을 다시 보내면 그대로 200(멱등).\n" +
            "- 다른 계정에서 켜져 있던 토큰이면 그 계정에서 끄고 이 계정으로 옮긴다(기기 양도).\n" +
            "- 로그인 직후와 FCM 토큰이 바뀔 때 호출한다. 모의 입양 푸시도 이 토큰으로 간다.\n" +
            "- 같은 토큰으로 동시에 등록하면 드물게 500이 날 수 있다. 다시 호출하면 된다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationDeviceTokenSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "등록 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"active":true},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "fcmToken 누락·공백, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "fcmToken 누락", value = INVALID_FCM_TOKEN),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/device-tokens")
    fun registerDeviceToken(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: DeviceTokenRequest,
    ) = notificationUseCase.registerDeviceToken(user.userSeq, request.fcmToken!!)

    /** 17.7 */
    @Operation(
        summary = "FCM 기기 토큰 해제",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- DELETE지만 본문 `{\"fcmToken\": ...}`이 필요하다. 본문이 없으면 400 `COMMON_INVALID_JSON`.\n" +
            "- 이 계정에 등록된 그 토큰만 끈다. 다른 계정의 토큰은 건드리지 않는다.\n" +
            "- 멱등: 등록돼 있지 않거나 이미 꺼진 토큰이어도 204.\n" +
            "- 로그아웃 없이 푸시만 끄는 설정 화면용. 로그아웃(`POST /api/core/v1/auth/logout`)에 `fcmToken`을 실으면 같은 처리를 하므로 따로 부르지 않아도 된다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "해제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "fcmToken 누락·공백, 또는 본문 없음·형식 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "fcmToken 누락", value = INVALID_FCM_TOKEN),
                        ExampleObject(name = "본문 없음·형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @DeleteMapping("/device-tokens/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateDeviceToken(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: DeviceTokenRequest,
    ) {
        notificationUseCase.deactivateDeviceToken(user.userSeq, request.fcmToken!!)
    }

    private companion object {
        // 문서 예시 전용
        const val INVALID_SORT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: title (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_NOTIFICATION_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"notificationType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_NOTIFICATION_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"notificationSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_FCM_TOKEN =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"fcmToken","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NOTIFICATION_NOT_FOUND =
            """{"status":404,"code":"NOTIFICATION_NOT_FOUND","message":"알림을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class DeviceTokenRequest(
    @field:Schema(description = "이 기기의 FCM 토큰(필수, 공백 불가)", example = "fcm-device-token")
    @field:NotBlank
    val fcmToken: String?,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class NotificationPageSuccessSchema : SuccessResponseSchema<PageResponse<NotificationItem>>()
abstract class NotificationReadSuccessSchema : SuccessResponseSchema<ReadResult>()
abstract class NotificationReadAllSuccessSchema : SuccessResponseSchema<ReadAllResult>()
abstract class NotificationDeviceTokenSuccessSchema : SuccessResponseSchema<DeviceTokenResult>()
