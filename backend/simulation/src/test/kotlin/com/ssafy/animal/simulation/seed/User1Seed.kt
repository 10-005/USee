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
 * dev 회원 1번의 모의 입양 완료 세션 시드(S15P31D106-254) 시각표. 개 말티즈 "콩이", 예상 비용 120,000원. 23시~7시에는 행동하지 않는다.
 *
 * 기획 표에서 바꾼 점(규칙상 표 그대로는 3일차에 건강 0으로 FAILED): 안정감이 시간당 2.6(FRIENDLY ×1.3) 줄어 하루 약 62가 필요해
 * 권장을 채운 날은 산책 2번·놀이·간식을 표보다 많이 했다. 이야기의 아쉬운 점(권장 미달)은 3일차(밥 2·물 1·배변 1·놀이 0),
 * 5일차(물 2), 6일차(배변 1)로 표와 같고, 4일차는 표(놀이 1·산책 0)와 달리 회복하는 날이다. 건강 감소는 3일차 수분 부족에서만 난다.
 */
object User1Seed : SeedStory() {
    const val USER_SEQ = 1L
    const val ANIMAL_NAME = "콩이"
    const val EXPECTED_COST = 120_000

    val START: OffsetDateTime = OffsetDateTime.of(2026, 11, 3, 20, 0, 0, 0, ZoneOffset.ofHours(9))
    val END: OffsetDateTime = START.plusDays(7)

    override val userSeq = USER_SEQ
    override val animalName = ANIMAL_NAME
    override val expectedCost = EXPECTED_COST
    override val start = START

    override val sql = SeedSql.Header(
        path = "src/main/resources/db/dev/V20261111_0527__dev_user1_simulation_report.sql",
        title = "dev 회원 1번의 모의 입양 완료 세션(S15P31D106-254)",
        story = "개 말티즈 \"콩이\" 7일 완료. 3일차 바쁜 날(물 1번, 쿠션 물어뜯기 #5 만료), 5일차 산책 중 헥헥(#13), 6일차 병원(#8)",
        generator = "User1Seed",
    )

    override val plan: Map<Int, String> = mapOf(
        1 to "20:00 BUY1, 20:10 F, 21:00 WALK30, 21:40 W, 21:50 T, 22:30 C, 22:40 S, 07:30 W, 07:40 F, 09:00 WALK60, 10:10 E1, 10:50 W, 13:00 F, 13:10 T, 16:00 W, 17:00 T, 18:30 C, 19:40 F",
        2 to "20:30 T, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:30 W, 07:40 F, 08:00 C, 09:00 WALK60, 10:10 W, 13:00 F, 13:10 T, 13:30 W, 17:00 F, 17:10 T, 18:00 C, 18:30 WALK60, 19:35 S, 19:40 T, 19:45 S, 19:50 F, 19:55 T",
        3 to "07:30 F, 07:40 C, 11:00 E5, 13:00 F, 15:00 WALK60, 19:30 W",
        4 to "20:05 BUY4, 20:06 W, 20:07 S, 20:10 T, 20:20 C, 21:00 WALK60, 22:10 W, 22:20 F, 22:30 B, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:50 C, 09:00 WALK60, 10:10 W, 13:00 F, 13:10 T, 16:00 W, 17:00 S, 18:30 C, 19:40 F, 19:50 S, 19:55 W",
        5 to "21:00 WALK20, 21:50 T, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:50 T, 08:00 C, 09:00 WALK60/e13@20/w@23, 10:10 S, 13:00 F, 13:10 S, 17:00 T, 18:30 C, 19:40 F, 19:50 T",
        6 to "20:10 W, 21:00 WALK20, 21:50 T, 22:30 W, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:45 S, 07:50 C, 08:10 E8, 09:10 VET, 09:20 S, 10:00 WALK40, 11:00 W, 11:10 T, 13:00 F, 13:10 S, 15:00 B, 16:00 W, 16:10 S, 17:00 T, 18:30 S, 19:30 F, 19:40 S",
        7 to "21:00 WALK20, 21:40 W, 21:50 T, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 08:00 C, 09:40 W, 10:20 T, 12:30 F, 13:10 T, 15:00 T, 16:00 W, 17:00 F, 17:10 S, 17:50 C, 18:00 WALK60, 19:10 B, 19:20 F, 19:30 W, 19:40 T",
    )

    override val purchases: Map<Int, List<Pair<Long, Int>>> = mapOf(
        1 to listOf(FOOD_BOWL to 1, WATER_BOWL to 1, PAD_BOARD to 1, LEASH to 1, GROOMING_KIT to 1, TOY to 1, FOOD to 2, PAD to 2, SNACK to 1, POOP_BAG to 1),
        4 to listOf(SNACK to 5),
    )
}
