package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.seed.SeedItems.FOOD
import com.ssafy.animal.simulation.seed.SeedItems.FOOD_BOWL
import com.ssafy.animal.simulation.seed.SeedItems.GROOMING_KIT
import com.ssafy.animal.simulation.seed.SeedItems.LEASH
import com.ssafy.animal.simulation.seed.SeedItems.PAD
import com.ssafy.animal.simulation.seed.SeedItems.PAD_BOARD
import com.ssafy.animal.simulation.seed.SeedItems.POOP_BAG
import com.ssafy.animal.simulation.seed.SeedItems.SNACK
import com.ssafy.animal.simulation.seed.SeedItems.TOY
import com.ssafy.animal.simulation.seed.SeedItems.WATER_BOWL
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * dev 회원 1번의 두 번째 모의 입양 완료 세션 시드(리포트 위키 Q521 확인용) 시각표. 개 말티즈 "보리", 콩이보다 앞선 주에 7일 완료.
 * 집·산책 돌발 이벤트를 해결과 놓침으로 고루 넣어 위키의 "해결했을 때"·"놓쳤을 때" 줄이 모두 쓰이게 한다.
 * 같은 시나리오(#16 산책 중 배변)를 한 번은 해결하고 한 번은 놓쳐 한 페이지에 두 줄이 함께 붙는 경우도 만든다.
 *
 * 해결: 1일차 앉아(#6, 간식)·산책 배변(#16, 배변봉투), 3일차 산책 중 짖음(#14, 간식)·천둥(#4, 놀이), 6일차 쿠션(#5, 놀이)
 * 놓침: 2일차 물그릇(#1, 6시간 넘게 물 없음), 4일차 화장실 실수(#3, 바쁜 날 배변 청소 없음), 5일차 산책 중 버팀(#15), 6일차 산책 배변(#16)
 */
object BoriSeed : SeedStory() {
    const val ANIMAL_NAME = "보리"

    override val userSeq = 1L
    override val animalName = ANIMAL_NAME
    override val expectedCost = 110_000
    override val start: OffsetDateTime = OffsetDateTime.of(2026, 10, 27, 20, 0, 0, 0, ZoneOffset.ofHours(9))

    override val sql = SeedSql.Header(
        path = "src/main/resources/db/dev/V20261112_1000__dev_user1_bori_simulation_report.sql",
        title = "dev 회원 1번의 두 번째 모의 입양 완료 세션(리포트 위키 Q521 확인용)",
        story = "개 말티즈 \"보리\" 7일 완료. 해결 #6·#16(1일차)·#14·#4(3일차)·#5(6일차), 놓침 #1(2일차)·#3(4일차 바쁜 날)·#15(5일차)·#16(6일차)",
        generator = "BoriSeed",
    )

    override val plan: Map<Int, String> = mapOf(
        1 to "20:00 BUY1, 20:10 F, 21:00 WALK30, 21:40 W, 21:50 T, 22:30 C, 22:40 S, 07:30 W, 07:40 F, 09:00 WALK60/e16@15/p@16, 10:10 W, 11:00 E6, 11:05 S, 13:00 F, 13:10 T, 16:00 W, 17:00 T, 18:30 C, 19:40 F",
        2 to "20:30 T, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:20 W, 07:30 F, 08:00 C, 08:10 E1, 09:00 WALK30, 11:00 T, 13:00 F, 13:10 T, 14:30 W, 16:00 W, 17:00 F, 17:10 T, 18:00 C, 18:30 WALK60, 19:35 S, 19:40 T, 19:45 S, 19:50 F, 19:55 T",
        3 to "20:10 W, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:30 W, 07:40 F, 08:00 C, 09:00 WALK60/e14@20/s@21, 10:10 W, 13:00 F, 14:00 E4, 14:05 T, 16:00 W, 17:00 F, 17:10 S, 18:00 C, 18:30 WALK30, 19:20 T, 19:40 F, 19:45 W, 19:50 S",
        4 to "07:20 W, 07:30 F, 08:30 E3, 13:00 F, 13:10 W, 15:00 WALK30, 16:00 W, 19:00 C, 19:30 T",
        5 to "20:05 BUY2, 20:06 C, 20:07 F, 20:10 W, 20:15 S, 20:20 T, 21:00 WALK60, 22:10 W, 22:20 F, 22:30 B, 22:40 S, 22:50 C, 07:30 W, 07:40 F, 07:50 C, 09:00 WALK60/e15@20, 10:10 W, 13:00 F, 13:10 T, 16:00 W, 17:00 S, 17:10 F, 18:30 C, 19:40 F, 19:50 T",
        6 to "21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 22:50 F, 07:30 W, 07:40 F, 08:00 C, 09:00 WALK60/e16@30, 10:10 W, 11:00 E5, 11:03 T, 13:00 F, 13:10 S, 16:00 W, 17:00 T, 17:10 F, 18:30 C, 19:30 F, 19:40 S",
        7 to "21:00 WALK20, 21:40 W, 21:50 T, 22:40 S, 22:50 F, 07:30 W, 07:40 F, 08:00 C, 09:40 W, 10:20 T, 12:30 F, 13:10 T, 15:00 T, 16:00 W, 17:00 F, 17:10 S, 17:50 C, 18:00 WALK60, 19:10 B, 19:20 F, 19:30 W, 19:40 T",
    )

    override val purchases: Map<Int, List<Pair<Long, Int>>> = mapOf(
        1 to listOf(FOOD_BOWL to 1, WATER_BOWL to 1, PAD_BOARD to 1, LEASH to 1, GROOMING_KIT to 1, TOY to 1, FOOD to 2, PAD to 2, SNACK to 3, POOP_BAG to 1),
        2 to listOf(SNACK to 3, FOOD to 1),
    )
}
