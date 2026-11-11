package com.ssafy.animal.core.found.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.found.application.port.`in`.BreedPrediction
import com.ssafy.animal.core.found.application.port.`in`.BreedPredictionUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 8.9. FoundReportController와 나눈 것은 유스케이스(외부 AI 호출)가 달라서다. 태그는 같은 "발견 신고" */
@Tag(name = "발견 신고")
@RestController
@RequestMapping("/api/core/v1/found-reports")
class BreedPredictionController(
    private val breedPredictionUseCase: BreedPredictionUseCase,
) {

    @Operation(
        summary = "사진으로 종·품종 추천",
        description = "- USER만. 발견 신고 작성 화면에서 사진을 올린 직후 호출해 종·품종 입력란을 미리 채운다(품종을 모르는 제보자용).\n" +
            "- `imageKeys`는 업로드 주소 발급(4.1)으로 받은 키 1~3개. **S3 업로드(PUT)를 마친 뒤, 이미지 확정(4.2) 전에** 호출한다. " +
            "본인이 발급받은 키만, 중복 불가. 사진이 바뀌면 전체 배열로 다시 호출한다.\n" +
            "- 키는 발급 후 10분 동안만 쓸 수 있다. 지났거나 이미 확정(4.2)한 키는 404이며, 그때는 추천 없이 수동 입력으로 진행한다.\n" +
            "- 저장하는 것이 없다. 결과는 추천일 뿐이고, 사용자가 확인·수정한 뒤 `colors`에서 색상을 골라 그 `combinationSeq`로 발견 신고 등록(8.4)을 호출한다.\n" +
            "- `colors`는 추천 품종의 색상 선택지다. `breedSeq`가 null이거나 사용자가 품종을 바꾸면 `GET /api/core/v1/animal-combinations?breedSeq=`로 다시 받는다.\n" +
            "- 사진이 여러 장이면 개·고양이가 보이는 사진만 쓰고, 종은 다수결(동률이면 앞 사진), 품종은 사진별 점수의 평균으로 정한다.\n" +
            "- `speciesSeq`가 null이면 개·고양이를 찾지 못한 것, `breedSeq`만 null이면 품종 확신이 낮거나 우리 품종표에 없는 품종이다. null인 칸은 채우지 않는다.\n" +
            "- 모델은 개 15품종·고양이 15품종만 안다. 믹스나 그 밖의 품종도 아는 품종 중 하나로 답할 수 있으니 반드시 사용자가 확인하게 한다.\n" +
            "- 사진 3장이면 2~3초 걸릴 수 있다. 503이면 추천 없이 수동 입력으로 진행한다(재시도 불필요).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "추천 결과(찾지 못한 값은 null)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BreedPredictionSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "종·품종 추천", value = SUCCESS),
                        ExampleObject(name = "종만 추천(품종 확신 낮음)", value = SPECIES_ONLY),
                        ExampleObject(name = "개·고양이를 찾지 못함", value = NONE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "imageKeys 누락·중복, 또는 1~3개가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "IMAGE_INVALID_COUNT", value = FoundReportController.IMAGE_INVALID_COUNT),
                        ExampleObject(name = "사진 중복", value = FoundReportController.IMAGE_DUPLICATED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "SHELTER 계정",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "쓸 수 없는 키: 발급받은 적 없음, 남이 발급받음, 10분 경과, 이미 확정(4.2)함. 추천 없이 수동 입력으로 진행한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_NOT_FOUND", value = FoundReportController.IMAGE_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "503",
            description = "AI 추천 서버를 쓸 수 없음. 추천 없이 수동 입력으로 진행한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_BREED_PREDICTION_UNAVAILABLE", value = UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/breed-prediction")
    @PreAuthorize("hasRole('USER')")
    fun predict(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: BreedPredictionRequest,
    ): BreedPrediction = breedPredictionUseCase.predict(user.userSeq, request.imageKeys!!)

    companion object {
        const val SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"speciesSeq":1,"speciesCode":"DOG","breedSeq":21,"breedName":"말티즈","candidates":[{"breedSeq":21,"breedName":"말티즈","score":0.69},{"breedSeq":63,"breedName":"믹스견","score":0.29},{"breedSeq":5,"breedName":"비숑 프리제","score":0.005}],"colors":[{"combinationSeq":88,"colorSeq":3,"colorName":"흰색"},{"combinationSeq":89,"colorSeq":12,"colorName":"크림색"}]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SPECIES_ONLY =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"speciesSeq":1,"speciesCode":"DOG","breedSeq":null,"breedName":null,"candidates":[{"breedSeq":48,"breedName":"시츄","score":0.41},{"breedSeq":21,"breedName":"말티즈","score":0.38}],"colors":[]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NONE =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"speciesSeq":null,"speciesCode":null,"breedSeq":null,"breedName":null,"candidates":[],"colors":[]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UNAVAILABLE =
            """{"status":503,"code":"FOUND_BREED_PREDICTION_UNAVAILABLE","message":"품종 추천을 일시적으로 사용할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class BreedPredictionRequest(
    @field:Schema(
        description = "업로드 주소 발급(4.1)으로 받은 imageKey 1~3개. S3 업로드를 마친 뒤, 확정(4.2) 전의 키. 본인이 발급받은 키만, 중복 불가",
        example = "[\"images/2026/11/3f1c2a9e.jpg\"]",
    )
    @field:NotNull val imageKeys: List<String>? = null,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class BreedPredictionSuccessSchema : SuccessResponseSchema<BreedPrediction>()
