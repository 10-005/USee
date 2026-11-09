package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.ReportListItem
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SpeciesType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/** 18.18 리포트(18.5 포기 응답에도 담긴다). 저장된 종료 리포트는 isFinal=true */
data class SimulationReportResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = SESSION_STATUS_DESC, example = "ABANDONED")
    val status: SessionStatus,
    @get:Schema(name = "isFinal", description = "저장된 종료 리포트면 true, 진행 중 세션의 중간 리포트(계산만 하고 저장하지 않음)면 false", example = "true")
    val isFinal: Boolean,
    @field:Schema(description = "가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종")
    val breed: BreedResponse,
    @field:Schema(description = SPECIES_DESC, example = "DOG")
    val speciesType: SpeciesType,
    @field:Schema(description = PERSONALITY_DESC, example = "FRIENDLY")
    val personality: Personality,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "종료 시각. 중간 리포트면 null", nullable = true, example = "2026-11-03T10:20:00+09:00")
    val endedDttm: OffsetDateTime?,
    @field:Schema(description = "일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "총지출(원) = 펫샵 구매 + 진료비", example = "52800")
    val totalSpent: Int,
    @field:Schema(description = "예상 비용 - 총지출(원). 음수면 예상보다 더 썼다", example = "97200")
    val difference: Int,
    @field:Schema(
        description = "분류별 지출(원). 키는 물품 분류 14종과 진료비 EVENT, 지출 없는 분류도 0",
        example = """{"FOOD_BOWL":10000,"FOOD":21200,"EVENT":0}""",
    )
    val spentByCategory: Map<String, Int>,
    @field:Schema(description = "일차별 지출(원). 키는 \"1\"~\"7\"(시작 기준 n일차)", example = """{"1":31200,"2":21600,"3":0}""")
    val dailySpent: Map<String, Int>,
    @field:Schema(
        description = "종료 시점 스탯 7종(중간 리포트면 조회 시점 스탯)",
        example = """{"satiety":55,"hydration":60,"cleanliness":70,"bond":62,"calmness":72,"health":84,"energy":58}""",
    )
    val finalStats: Map<String, Int>,
    @field:Schema(description = "돌봄·산책 행동 수", example = "12")
    val actionCount: Int,
    @field:Schema(description = "발생한 돌발 이벤트 수(해결 + 만료)", example = "0")
    val eventCount: Int,
    @field:Schema(description = "정답 행동으로 해결한 이벤트 수", example = "0")
    val eventResolvedCount: Int,
    @field:Schema(description = "산책 수. 고양이 세션은 항상 0", example = "0")
    val walkCount: Int,
    @field:Schema(
        description = "요약 문구. 종료 직후에는 null(비동기로 생성, 앱은 \"요약 준비 중\" 표시 후 다시 조회). 중간 리포트는 항상 null",
        nullable = true,
        example = "null",
    )
    val summary: String?,
) {
    companion object {
        fun from(view: SessionReportView) = with(view.report) {
            SimulationReportResponse(
                sessionSeq = sessionSeq,
                status = view.session.status,
                isFinal = view.isFinal,
                animalName = view.session.animalName,
                breed = BreedResponse.from(view.breed),
                speciesType = view.session.speciesType,
                personality = view.session.personality,
                startedDttm = view.session.startedDttm,
                endedDttm = view.session.endedDttm,
                expectedCost = expectedCost,
                totalSpent = totalSpent,
                difference = difference,
                spentByCategory = spentByCategoryJsonb,
                dailySpent = dailySpentJsonb,
                finalStats = finalStatsJsonb,
                actionCount = actionCount,
                eventCount = eventCount,
                eventResolvedCount = eventResolvedCount,
                walkCount = walkCount,
                summary = summary,
            )
        }
    }
}

/** 18.5 포기 응답 */
data class AbandonResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "항상 ABANDONED", example = "ABANDONED")
    val status: SessionStatus,
    @field:Schema(description = "포기 시각", example = "2026-11-03T10:20:00+09:00")
    val endedDttm: OffsetDateTime,
    @field:Schema(description = "종료 리포트(summary는 null)")
    val report: SimulationReportResponse,
) {
    companion object {
        fun from(view: SessionReportView) = AbandonResponse(
            sessionSeq = checkNotNull(view.session.sessionSeq),
            status = view.session.status,
            endedDttm = checkNotNull(view.session.endedDttm),
            report = SimulationReportResponse.from(view),
        )
    }
}

/** 18.19 내 리포트 목록 항목 */
data class ReportListItemResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종")
    val breed: BreedResponse,
    @field:Schema(description = "종료 상태. COMPLETED: 7일 완료, ABANDONED: 포기, FAILED: 돌봄 부족(건강 0)", example = "COMPLETED")
    val status: SessionStatus,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "종료 시각", example = "2026-11-08T14:00:00+09:00")
    val endedDttm: OffsetDateTime,
    @field:Schema(description = "일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "총지출(원)", example = "182000")
    val totalSpent: Int,
    @field:Schema(description = "예상 비용 - 총지출(원). 음수면 예상보다 더 썼다", example = "-32000")
    val difference: Int,
) {
    companion object {
        fun from(item: ReportListItem) = ReportListItemResponse(
            sessionSeq = checkNotNull(item.session.sessionSeq),
            animalName = item.session.animalName,
            breed = BreedResponse.from(item.breed),
            status = item.session.status,
            startedDttm = item.session.startedDttm,
            endedDttm = checkNotNull(item.session.endedDttm) { "리포트가 있는 세션은 끝난 세션입니다." },
            expectedCost = item.report.expectedCost,
            totalSpent = item.report.totalSpent,
            difference = item.report.difference,
        )
    }
}

/** 18.20 보호소 리포트 목록 항목 = 18.19 항목 + 회원 */
data class ShelterReportListItemResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "회원(모의 입양 체험자)")
    val user: SimulationActorSummary,
    @field:Schema(description = "가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종")
    val breed: BreedResponse,
    @field:Schema(description = "종료 상태. COMPLETED: 7일 완료, ABANDONED: 포기, FAILED: 돌봄 부족(건강 0)", example = "COMPLETED")
    val status: SessionStatus,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "종료 시각", example = "2026-11-08T14:00:00+09:00")
    val endedDttm: OffsetDateTime,
    @field:Schema(description = "일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "총지출(원)", example = "182000")
    val totalSpent: Int,
    @field:Schema(description = "예상 비용 - 총지출(원). 음수면 예상보다 더 썼다", example = "-32000")
    val difference: Int,
) {
    companion object {
        fun from(item: ReportListItem) = ReportListItemResponse.from(item).let { base ->
            ShelterReportListItemResponse(
                sessionSeq = base.sessionSeq,
                user = SimulationActorSummary.of(item.session.userSeq, item.user),
                animalName = base.animalName,
                breed = base.breed,
                status = base.status,
                startedDttm = base.startedDttm,
                endedDttm = base.endedDttm,
                expectedCost = base.expectedCost,
                totalSpent = base.totalSpent,
                difference = base.difference,
            )
        }
    }
}

/** 1.4 ActorSummary. 모의 입양 체험자는 USER라 표시 이름은 닉네임 */
@Schema(name = "SimulationActorSummary", description = "회원 표시 정보")
data class SimulationActorSummary(
    @field:Schema(description = "회원 번호", example = "17")
    val userSeq: Long,
    @field:Schema(description = "표시 이름(닉네임). 탈퇴한 회원이면 (탈퇴한 사용자)", nullable = true, example = "반려가족")
    val displayName: String?,
    @field:Schema(description = "역할. 모의 입양 체험자는 항상 USER", example = "USER")
    val role: String,
    @field:Schema(description = "프로필 사진. 없거나 탈퇴한 회원이면 null", nullable = true)
    val profileImage: SimulationProfileImage?,
) {
    companion object {
        const val WITHDRAWN_NAME = "(탈퇴한 사용자)"

        /** 회원 행이 없거나 탈퇴했으면 표시 이름을 바꾸고 사진을 숨긴다 */
        fun of(userSeq: Int, user: UserInfo?): SimulationActorSummary {
            if (user == null || user.isDeleted) return SimulationActorSummary(userSeq.toLong(), WITHDRAWN_NAME, user?.role ?: "USER", null)
            val image = user.profileImageSeq?.let { seq -> user.profileImageUrl?.let { SimulationProfileImage(seq, it) } }
            return SimulationActorSummary(userSeq.toLong(), user.nickname, user.role, image)
        }
    }
}

@Schema(name = "SimulationProfileImage", description = "프로필 사진")
data class SimulationProfileImage(
    @field:Schema(description = "이미지 번호", example = "1301")
    val imageSeq: Long,
    @field:Schema(description = "공개 이미지 URL", example = "https://storage.example.com/images/2026/11/3f1c2a9e.jpg")
    val imageUrl: String,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class AbandonSuccessSchema : SuccessResponseSchema<AbandonResponse>()

abstract class SimulationReportSuccessSchema : SuccessResponseSchema<SimulationReportResponse>()

abstract class ReportListPageSuccessSchema : SuccessResponseSchema<PageResponse<ReportListItemResponse>>()

abstract class ShelterReportListPageSuccessSchema : SuccessResponseSchema<PageResponse<ShelterReportListItemResponse>>()
