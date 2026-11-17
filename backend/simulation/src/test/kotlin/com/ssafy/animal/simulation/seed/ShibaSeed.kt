package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Personality
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
 * 시연용 모의 입양 완료 세션 시드. 개 시바견 "모찌" 7일 완료를 시연 회원 10명에게 똑같이 넣는다.
 * 시연에서 7일차 리포트를 바로 보여 주려는 것이라 지출이 7일 중 6일(1~6일차)에 나뉘어 있다.
 * 시각표는 보리 시드(BoriSeed)와 같은 뼈대에 구매를 여러 날로 나누고, 겁 많은 성격(안정감이 1.5배 빨리 줄어듦)에 맞춰
 * 간식·산책을 늘리고 돌발 이벤트를 모두 해결하게 했다.
 */
object ShibaSeed : SeedStory() {
    const val ANIMAL_NAME = "모찌"

    /** 배포 DB의 시바견(breed_seq 99, TIMID). SQL은 이름으로 찾으므로 번호는 생성기 안에서만 쓴다 */
    val SHIBA = BreedInfo(99, "시바견", CatalogSpeciesCode.DOG, Personality.TIMID, null)

    override val userSeq = 1L
    override val userSeqs = listOf(1L, 140L, 141L, 142L, 143L, 144L, 145L, 146L, 147L, 148L)
    override val breed = SHIBA
    override val animalName = ANIMAL_NAME
    override val expectedCost = 150_000
    override val start: OffsetDateTime = OffsetDateTime.of(2026, 11, 9, 20, 0, 0, 0, ZoneOffset.ofHours(9))

    override val sql = SeedSql.Header(
        path = "src/main/resources/db/dev/V20261117_0200__dev_shiba_demo_simulation_reports.sql",
        title = "시연 회원 10명의 모의 입양 완료 세션(7일차 리포트 시연용)",
        story = "개 시바견 \"모찌\" 7일 완료. 지출은 1~6일차에 나뉨. 돌발 이벤트 8건 모두 해결: #16·#6(1일차), #14·#4(3일차), #3(4일차), #15(5일차), #16·#5(6일차)",
        generator = "ShibaSeed",
    )

    override val plan: Map<Int, String> = mapOf(
        1 to "20:00 BUY1, 20:10 F, 21:00 WALK30, 21:40 W, 21:50 T, 22:30 C, 22:40 S, 07:30 W, 07:40 F, 07:45 S, 09:00 WALK60/e16@15/p@16, 10:10 W, 11:00 E6, 11:05 S, 13:00 F, 13:10 T, 16:00 W, 17:00 T, 18:30 C, 19:40 F",
        2 to "20:20 BUY2, 20:30 T, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:20 W, 07:30 F, 07:45 S, 08:00 C, 09:00 WALK30, 11:00 T, 13:00 F, 13:10 T, 14:30 W, 16:00 W, 17:00 F, 17:10 T, 18:00 C, 18:30 WALK60, 19:35 S, 19:40 T, 19:45 S, 19:50 F, 19:55 T",
        3 to "20:05 BUY3, 20:10 W, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:30 W, 07:40 F, 07:45 S, 08:00 C, 09:00 WALK60/e14@20/s@21, 10:10 W, 13:00 F, 14:00 E4, 14:05 T, 16:00 W, 17:00 F, 17:10 S, 18:00 C, 18:30 WALK30, 19:20 T, 19:40 F, 19:45 W, 19:50 S",
        4 to "07:10 BUY4, 07:20 W, 07:30 F, 07:35 S, 08:30 E3, 08:35 C, 09:00 WALK60, 13:00 F, 13:10 W, 13:15 S, 15:00 WALK30, 16:00 W, 17:00 S, 19:00 C, 19:30 T",
        5 to "20:05 BUY5, 20:06 C, 20:07 F, 20:10 W, 20:15 S, 20:20 T, 21:00 WALK60, 22:10 W, 22:20 F, 22:30 B, 22:40 S, 22:50 C, 07:30 W, 07:40 F, 07:45 S, 07:50 C, 09:00 WALK60/e15@20/s@21, 10:10 W, 13:00 F, 13:10 T, 16:00 W, 17:00 S, 17:10 F, 18:30 C, 19:40 F, 19:50 T",
        6 to "20:30 BUY6, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 22:50 F, 07:30 W, 07:40 F, 07:45 S, 08:00 C, 09:00 WALK60/e16@30/p@31, 10:10 W, 11:00 E5, 11:03 T, 13:00 F, 13:10 S, 16:00 W, 17:00 T, 17:10 F, 18:30 C, 19:30 F, 19:40 S",
        7 to "21:00 WALK20, 21:40 W, 21:50 T, 22:40 S, 22:50 F, 07:30 W, 07:40 F, 07:45 S, 08:00 C, 09:40 W, 10:20 T, 12:30 F, 13:10 T, 15:00 T, 16:00 W, 17:00 F, 17:10 S, 17:50 C, 18:00 WALK60, 19:10 B, 19:20 F, 19:30 W, 19:40 T",
    )

    override val purchases: Map<Int, List<Pair<Long, Int>>> = mapOf(
        1 to listOf(FOOD_BOWL to 1, WATER_BOWL to 1, PAD_BOARD to 1, LEASH to 1, GROOMING_KIT to 1, TOY to 1, FOOD to 1, PAD to 1, SNACK to 2, POOP_BAG to 1),
        2 to listOf(SNACK to 2),
        3 to listOf(FOOD to 1),
        4 to listOf(SNACK to 2, PAD to 1),
        5 to listOf(FOOD to 1, SNACK to 2),
        6 to listOf(SNACK to 2),
    )
}
