package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 플레이한 메모리 행을 dev 시드 SQL(DO 블록 하나)로 옮긴다. 번호는 IDENTITY가 만들고 RETURNING으로 잇는다.
 * 서버 계정은 임시 테이블·함수를 만들 수 없어 변수·배열만 쓴다.
 * 발생 이벤트는 세션당 PENDING 1건(부분 UNIQUE)이라 시간 순으로 PENDING INSERT → 해결 로그 INSERT → UPDATE를 한 건씩 한다.
 */
object SeedSql {

    /**
     * 시드 파일 위치(simulation 모듈 기준)와 머리말. title은 첫 줄, story는 "이야기:" 줄, generator는 시각표 객체 이름
     * (재생성 명령의 테스트 이름이 "{generator}GeneratorTest"다)
     */
    data class Header(val path: String, val title: String, val story: String, val generator: String)

    fun render(game: SeedGame, header: Header): String {
        val store = game.store
        val session = game.session
        val report = store.reports.single()
        val walkVar = store.walks.mapIndexed { i, walk -> walk.walkSeq to "w[${i + 1}]" }.toMap()
        val occurrenceVar = store.occurrences.mapIndexed { i, occurrence -> occurrence.occurrenceSeq to "o[${i + 1}]" }.toMap()
        val out = StringBuilder()
        fun line(text: String = "") = out.append(text).append('\n')

        line("-- ${header.title}. local·dev에서만 적용된다")
        line("-- 생성기가 만든 파일이다. 손으로 고치지 않는다: SEED_GEN=true ./gradlew :simulation:test --tests '*${header.generator}GeneratorTest*' --rerun")
        line("-- (생성기 ${header.generator}가 실제 서비스를 고정 시계로 7일 돌린 결과. 스탯·지출·리포트 값은 그 결과 그대로)")
        line("-- 이야기: ${header.story}")
        line("-- 리포트 summary는 NULL로 둔다. 배포 뒤 요약 복구 스케줄러가 채운다(LLM 키가 없으면 규칙 문구)")
        line("-- 회원 1번이 USER가 아니거나(로컬 새 DB는 보호소 계정), 말티즈가 없거나 성격이 다르거나, 이미 넣었으면 건너뛴다")
        line("DO \$dev\$")
        line("DECLARE")
        line("    me            CONSTANT INTEGER := ${session.userSeq};")
        line("    v_breed       INTEGER;")
        line("    v_personality VARCHAR;")
        line("    v_session     BIGINT;")
        line("    v_id          BIGINT;")
        line("    w             BIGINT[] := '{}';")
        line("    o             BIGINT[] := '{}';")
        line("BEGIN")
        line("    IF to_regclass('core.users') IS NULL OR to_regclass('core.animal_breeds') IS NULL THEN")
        line("        RAISE NOTICE 'core 테이블이 없어 모의 입양 시드를 건너뜀';")
        line("        RETURN;")
        line("    END IF;")
        line("    PERFORM 1 FROM core.users WHERE user_seq = me AND role = 'USER' AND is_deleted = FALSE;")
        line("    IF NOT FOUND THEN")
        line("        RAISE NOTICE '회원 1번이 USER가 아니어서 모의 입양 시드를 건너뜀';")
        line("        RETURN;")
        line("    END IF;")
        line("    SELECT b.breed_seq, b.personality INTO v_breed, v_personality")
        line("    FROM core.animal_breeds b JOIN core.animal_species s ON s.species_seq = b.species_seq")
        line("    WHERE b.breed_name = ${q(SeedGame.MALTESE.breedName)} AND s.species_code = ${q(session.speciesType.name)}")
        line("    ORDER BY b.breed_seq LIMIT 1;")
        line("    IF NOT FOUND THEN")
        line("        RAISE NOTICE '말티즈 품종이 없어 모의 입양 시드를 건너뜀';")
        line("        RETURN;")
        line("    END IF;")
        line("    -- 스탯은 이 성격의 감소 배율로 계산했다")
        line("    IF v_personality <> ${q(session.personality.name)} THEN")
        line("        RAISE NOTICE '말티즈 성격이 %(기대 ${session.personality.name})라 모의 입양 시드를 건너뜀', v_personality;")
        line("        RETURN;")
        line("    END IF;")
        line("    IF EXISTS (SELECT 1 FROM simulation_sessions WHERE user_seq = me AND started_dttm = ${ts(session.startedDttm)}) THEN")
        line("        RAISE NOTICE '모의 입양 시드가 이미 있어 건너뜀';")
        line("        RETURN;")
        line("    END IF;")
        line()
        val s = session.stats()
        line("    INSERT INTO simulation_sessions (user_seq, breed_seq, animal_name, species_type, personality, status, expected_cost, balance,")
        line("                                     started_dttm, ends_dttm, ended_dttm, satiety, hydration, cleanliness, bond, calmness, health, energy,")
        line("                                     stats_calculated_dttm, last_action_dttm, reg_dttm, mod_dttm)")
        line(
            "    VALUES (me, v_breed, ${q(session.animalName)}, ${q(session.speciesType.name)}, v_personality, ${q(session.status.name)}, " +
                "${session.expectedCost}, ${session.balance},",
        )
        line(
            "            ${ts(session.startedDttm)}, ${ts(session.endsDttm)}, ${ts(session.endedDttm)}, " +
                "${s.satiety}, ${s.hydration}, ${s.cleanliness}, ${s.bond}, ${s.calmness}, ${s.health}, ${s.energy},",
        )
        line("            ${ts(session.statsCalculatedDttm)}, ${ts(session.lastActionDttm)}, ${ts(session.startedDttm)}, ${ts(session.endedDttm)})")
        line("    RETURNING session_seq INTO v_session;")
        line()

        line("    -- 산책 ${store.walks.size}회")
        store.walks.forEach { walk ->
            line(
                "    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) " +
                    "VALUES (v_session, ${q(walk.status.name)}, ${ts(walk.startedDttm)}, ${ts(walk.lastHeartbeatDttm)}, ${ts(walk.endedDttm)}, ${ts(walk.startedDttm)}) " +
                    "RETURNING walk_seq INTO v_id; w := w || v_id;",
            )
        }
        line()

        line("    -- 행동 로그 ${store.logs.size}건과 돌발 이벤트 ${store.occurrences.size}건(시간 순)")
        val batch = mutableListOf<String>()
        fun flush() {
            if (batch.isEmpty()) return
            line("    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES")
            line(batch.joinToString(",\n") { "        $it" } + ";")
            batch.clear()
        }
        fun values(log: SimulationActionLog) =
            "(v_session, ${q(log.actionType.name)}, ${q(log.actionCode.name)}, ${log.itemSeq ?: "NULL"}, ${log.walkSeq?.let(walkVar::getValue) ?: "NULL"}, " +
                "${log.occurrenceSeq?.let(occurrenceVar::getValue) ?: "NULL"}, ${json(log.statDeltaJsonb)}, ${json(log.statsAfterJsonb)}, ${ts(log.regDttm)})"

        val pending = ArrayDeque(store.occurrences)
        store.logs.forEach { log ->
            while (pending.isNotEmpty() && !pending.first().occurredDttm.isAfter(checkNotNull(log.regDttm))) {
                flush()
                val occurrence = pending.removeFirst()
                line(
                    "    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) " +
                        "VALUES (v_session, ${occurrence.scenarioSeq}, ${occurrence.walkSeq?.let(walkVar::getValue) ?: "NULL"}, 'PENDING', " +
                        "${ts(occurrence.occurredDttm)}, ${ts(occurrence.expiresDttm)}, ${ts(occurrence.occurredDttm)}) " +
                        "RETURNING occurrence_seq INTO v_id; o := o || v_id;",
                )
            }
            val resolved = store.occurrences.firstOrNull { it.resolvedActionLogSeq == log.actionLogSeq }
            when {
                resolved != null -> {
                    flush()
                    line("    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)")
                    line("    VALUES ${values(log)} RETURNING action_log_seq INTO v_id;")
                    line(
                        "    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = ${ts(resolved.resolvedDttm)}, resolved_action_log_seq = v_id " +
                            "WHERE occurrence_seq = ${occurrenceVar.getValue(resolved.occurrenceSeq)};",
                    )
                }
                log.actionCode == ActionCode.EVENT_EXPIRE -> {
                    batch += values(log)
                    flush()
                    line("    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = ${occurrenceVar.getValue(log.occurrenceSeq)};")
                }
                else -> batch += values(log)
            }
        }
        flush()
        check(pending.isEmpty()) { "로그 뒤에 생긴 이벤트가 있습니다: $pending" }
        // 만료 로그 없이 끝난 이벤트(세션 종료 시 PENDING → EXPIRED)는 이 데이터에 없다
        check(store.occurrences.all { o -> o.status == OccurrenceStatus.RESOLVED || store.logs.any { it.occurrenceSeq == o.occurrenceSeq && it.actionCode == ActionCode.EVENT_EXPIRE } })
        line()

        line("    -- 지출 ${store.transactions.size}건(balance_after는 누적)")
        line("    INSERT INTO simulation_transactions (session_seq, transaction_type, item_seq, occurrence_seq, quantity, amount, balance_after, description, reg_dttm) VALUES")
        line(
            store.transactions.joinToString(",\n") { t ->
                "        (v_session, ${q(t.transactionType.name)}, ${t.itemSeq ?: "NULL"}, ${t.occurrenceSeq?.let(occurrenceVar::getValue) ?: "NULL"}, " +
                    "${t.quantity}, ${t.amount}, ${t.balanceAfter}, ${q(t.description)}, ${ts(t.regDttm)})"
            } + ";",
        )
        line()

        line("    -- 보유 물품(묶음 물품은 구매 수 × simulation.shop.uses-per-purchase에서 쓴 만큼 뺀 값)")
        line("    INSERT INTO simulation_inventory_items (session_seq, item_seq, quantity, reg_dttm, mod_dttm) VALUES")
        line(
            store.inventory.joinToString(",\n") { item ->
                "        (v_session, ${item.itemSeq}, ${item.quantity}, ${ts(game.inventoryCreated.getValue(item.itemSeq))}, ${ts(game.inventoryModified[item.itemSeq])})"
            } + ";",
        )
        line()

        line("    -- 종료 리포트(summary NULL → 요약 복구 스케줄러가 채움)")
        line(
            "    INSERT INTO simulation_reports (session_seq, expected_cost, total_spent, spent_by_category_jsonb, daily_spent_jsonb, final_stats_jsonb, " +
                "action_count, event_count, event_resolved_count, walk_count, summary, reg_dttm)",
        )
        line(
            "    VALUES (v_session, ${report.expectedCost}, ${report.totalSpent}, ${json(report.spentByCategoryJsonb)}, ${json(report.dailySpentJsonb)}, " +
                "${json(report.finalStatsJsonb)}, ${report.actionCount}, ${report.eventCount}, ${report.eventResolvedCount}, ${report.walkCount}, NULL, ${ts(report.regDttm)});",
        )
        line("END")
        line("\$dev\$;")
        return out.toString()
    }

    private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssx")

    private fun ts(at: OffsetDateTime?) = at?.let { "'${it.atZoneSameInstant(SeedGame.SEOUL).toOffsetDateTime().format(TS)}'" } ?: "NULL"

    private fun q(text: String?) = text?.let { "'${it.replace("'", "''")}'" } ?: "NULL"

    private fun json(map: Map<String, Int>?) = map?.let { m -> q(m.entries.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": $v" }) } ?: "NULL"
}
