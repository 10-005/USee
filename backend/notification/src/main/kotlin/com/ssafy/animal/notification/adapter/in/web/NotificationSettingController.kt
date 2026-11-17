package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.notification.application.port.`in`.NotificationSettingResult
import com.ssafy.animal.notification.application.port.`in`.NotificationSettingUseCase
import com.ssafy.animal.notification.domain.NotificationSettingChange
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalTime

/** 17.9~17.10 알림 설정(Q510~Q515). USER만, 본인 설정만 */
@Tag(name = "알림 설정", description = "알림 종류별 푸시 켜기·끄기와 모의 입양 방해금지 시간. USER만. 끈 알림도 알림함에는 저장된다")
@RestController
@RequestMapping("/api/notification/v1/notifications/settings")
class NotificationSettingController(
    private val settingUseCase: NotificationSettingUseCase,
) {

    /** 17.9 행이 없으면 기본값 */
    @Operation(
        summary = "내 알림 설정 조회",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`. 보호소 알림은 항상 발송)\n" +
            "- 한 번도 설정하지 않았으면 기본값을 준다: 5종 모두 `true`, `quietEnabled=false`, 방해금지 22:00~08:00.\n" +
            "- 시각은 서울 기준 `HH:mm`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationSettingSuccessSchema::class),
                    examples = [ExampleObject(name = "기본값", value = DEFAULT_SETTING)],
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
        ApiResponse(
            responseCode = "403",
            description = "보호소 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    fun get(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) = settingUseCase.get(user.userSeq)

    /** 17.10 행이 없으면 만들고 보낸 값만 바꾼다 */
    @Operation(
        summary = "내 알림 설정 변경",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 보낸 항목만 바꾼다. 생략하거나 `null`이면 그대로 둔다. 빈 본문 `{}`도 200.\n" +
            "- 처음 바꾸는 회원이면 기본값(5종 `true`, 방해금지 꺼짐 22:00~08:00) 위에 보낸 항목을 반영해 만든다.\n" +
            "- 응답은 바뀐 뒤의 전체 설정.\n" +
            "- 끈 종류는 푸시(FCM)만 보내지 않는다. 댓글·AI 매칭·입양 알림은 알림함에 그대로 쌓인다.\n" +
            "- 방해금지(`quietEnabled`·`quietStart`·`quietEnd`)는 **모의 입양 알림에만** 적용한다. 그 시간에 생긴 모의 입양 푸시는 나중에 보내지 않고 버린다.\n" +
            "- 시각은 서울 기준 `HH:mm`(초 불가). 시작이 끝보다 늦으면 자정을 넘기는 구간(예: 22:00~08:00). 시작 시각부터 끝 시각 직전까지 막는다.\n" +
            "- 바꾼 결과 시작과 끝이 같으면 400.\n" +
            "- 앱 설치 후 OS 알림 권한을 물은 직후, 권한 결과에 맞춰 5종을 한 번에 보낸다(재설치하면 다시 덮어씀).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "변경 완료. 바뀐 뒤의 전체 설정",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NotificationSettingSuccessSchema::class),
                    examples = [ExampleObject(name = "채팅 끔·방해금지 켬", value = CHANGED_SETTING)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "방해금지 시작·끝이 같음, 초 단위 시각, 또는 본문을 읽을 수 없음(시각 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "시작·끝 같음", value = SAME_QUIET_TIME),
                        ExampleObject(name = "초 단위 시각", value = SECOND_QUIET_TIME),
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
            description = "보호소 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
    )
    @PatchMapping
    @PreAuthorize("hasRole('USER')")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @RequestBody request: NotificationSettingRequest,
    ) = settingUseCase.update(user.userSeq, request.toChange())

    private companion object {
        // 문서 예시 전용
        const val DEFAULT_SETTING =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"simulationEnabled":true,"chatEnabled":true,"commentEnabled":true,"aiMatchEnabled":true,"adoptionEnabled":true,"quietEnabled":false,"quietStart":"22:00","quietEnd":"08:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHANGED_SETTING =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"simulationEnabled":true,"chatEnabled":false,"commentEnabled":true,"aiMatchEnabled":true,"adoptionEnabled":true,"quietEnabled":true,"quietStart":"23:00","quietEnd":"07:30"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SAME_QUIET_TIME =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"quietEnd","reason":"방해금지 시작과 끝 시각이 같을 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SECOND_QUIET_TIME =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"quietStart","reason":"시각은 HH:mm 형식으로 분 단위까지만 입력해 주세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

/** 17.10 본문. 모두 선택, null이면 그대로 */
data class NotificationSettingRequest(
    @field:Schema(description = "모의 입양 알림", nullable = true, example = "true")
    val simulationEnabled: Boolean? = null,
    @field:Schema(description = "채팅 알림", nullable = true, example = "false")
    val chatEnabled: Boolean? = null,
    @field:Schema(description = "댓글 알림", nullable = true, example = "true")
    val commentEnabled: Boolean? = null,
    @field:Schema(description = "AI 매칭 알림", nullable = true, example = "true")
    val aiMatchEnabled: Boolean? = null,
    @field:Schema(description = "입양 관련 알림", nullable = true, example = "true")
    val adoptionEnabled: Boolean? = null,
    @field:Schema(description = "방해금지 사용(모의 입양 알림에만 적용)", nullable = true, example = "true")
    val quietEnabled: Boolean? = null,
    @field:Schema(description = "방해금지 시작(서울, HH:mm)", type = "string", nullable = true, example = "23:00")
    val quietStart: LocalTime? = null,
    @field:Schema(description = "방해금지 끝(서울, HH:mm)", type = "string", nullable = true, example = "07:30")
    val quietEnd: LocalTime? = null,
) {
    fun toChange() = NotificationSettingChange(
        simulationEnabled, chatEnabled, commentEnabled, aiMatchEnabled, adoptionEnabled, quietEnabled, quietStart, quietEnd,
    )
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class NotificationSettingSuccessSchema : SuccessResponseSchema<NotificationSettingResult>()
