package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.CareConfig
import com.ssafy.animal.simulation.domain.CareKey
import com.ssafy.animal.simulation.domain.DemoConfig
import com.ssafy.animal.simulation.domain.EventConfig
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatRules
import com.ssafy.animal.simulation.domain.WalkConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 모의 입양 코드 설정값(API 명세 18.0 [가안], Q429). 값은 application.yml의 simulation.* 에 두고 플레이 테스트 후 조정한다 */
@ConfigurationProperties("simulation")
data class SimulationProperties(
    /** 스탯 지연 계산 단위(M2) */
    val tick: Duration,
    /** 세션 길이. 예정 종료 = 시작 + 이 값(M2) */
    val sessionDuration: Duration,
    val stats: StatRules,
    val shop: ShopRules = ShopRules(),
    val care: CareConfig = CareConfig(),
    val report: ReportConfig = ReportConfig(),
    val event: EventConfig = EventConfig(),
    val walk: WalkConfig = WalkConfig(),
    val demo: DemoConfig = DemoConfig(),
)

/** 리포트 요약 설정(Q444·Q487 [가안]) */
data class ReportConfig(
    /** 종료 처리 후 이 시간이 지나도 요약이 없으면 스케줄러가 다시 만든다(고아 복구) */
    val summaryRecoveryDelay: Duration = Duration.ofMinutes(10),
    /** 하루 권장 횟수(Q517 [가안]). 18.18 careGuideline·dailyCare 이탈 판정 기준 */
    val careGuideline: Map<CareKey, Int> = mapOf(
        CareKey.FEED to 3, CareKey.WATER to 3, CareKey.CLEAN_LITTER to 2, CareKey.PLAY to 2, CareKey.WALK to 1,
    ),
    /** 종별 최소 예산(원, Q517 [가안]): 필수 기구 + 7일치 소모품 최소 구성. LLM 요약 입력에만 쓴다 */
    val referenceCost: Map<SpeciesType, Int> = mapOf(SpeciesType.DOG to 93_300, SpeciesType.CAT to 88_800),
    val llm: LlmConfig = LlmConfig(),
)

/** LLM 요약(Q517). apiKey가 비어 있으면 규칙 문구 생성기를 쓴다 */
data class LlmConfig(
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val timeout: Duration = Duration.ofSeconds(20),
    val baseUrl: String = "https://api.openai.com",
) {
    // 설정 객체가 로그에 찍혀도 키가 남지 않게 한다
    override fun toString() = "LlmConfig(apiKey=${if (apiKey.isBlank()) "<empty>" else "<set>"}, model=$model, timeout=$timeout, baseUrl=$baseUrl)"
}
