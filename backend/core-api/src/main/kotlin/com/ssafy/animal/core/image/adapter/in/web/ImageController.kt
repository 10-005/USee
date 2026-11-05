package com.ssafy.animal.core.image.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/core/v1/images")
class ImageController(
    private val imageUseCase: ImageUseCase,
) {

    /** 4.1 presigned PUT 주소 발급 */
    @PostMapping("/upload-urls")
    fun issueUploadUrl(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: UploadUrlRequest) =
        UploadUrlResponse.from(imageUseCase.issueUploadUrl(request.toCommand(user.userSeq)))

    /** 4.2 업로드한 이미지 확정 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("이미지가 등록되었습니다.")
    fun confirm(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ConfirmImageRequest) =
        ImageResponse.from(imageUseCase.confirm(user.userSeq, request.imageKey!!))
}
