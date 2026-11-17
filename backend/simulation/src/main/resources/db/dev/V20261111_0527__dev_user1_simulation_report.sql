-- dev 회원 1번의 모의 입양 완료 세션(S15P31D106-254). local·dev에서만 적용된다
-- 생성기가 만든 파일이다. 손으로 고치지 않는다: SEED_GEN=true ./gradlew :simulation:test --tests '*User1SeedGeneratorTest*' --rerun
-- (생성기 User1Seed가 실제 서비스를 고정 시계로 7일 돌린 결과. 스탯·지출·리포트 값은 그 결과 그대로)
-- 이야기: 개 말티즈 "콩이" 7일 완료. 3일차 바쁜 날(물 1번, 쿠션 물어뜯기 #5 만료), 5일차 산책 중 헥헥(#13), 6일차 병원(#8)
-- 리포트 summary는 NULL로 둔다. 배포 뒤 요약 복구 스케줄러가 채운다(LLM 키가 없으면 규칙 문구)
-- 회원 1번이 USER가 아니거나(로컬 새 DB는 보호소 계정), 말티즈가 없거나 성격이 다르거나, 이미 넣었으면 건너뛴다
DO $dev$
DECLARE
    me            CONSTANT INTEGER := 1;
    v_breed       INTEGER;
    v_personality VARCHAR;
    v_session     BIGINT;
    v_id          BIGINT;
    w             BIGINT[] := '{}';
    o             BIGINT[] := '{}';
BEGIN
    IF to_regclass('core.users') IS NULL OR to_regclass('core.animal_breeds') IS NULL THEN
        RAISE NOTICE 'core 테이블이 없어 모의 입양 시드를 건너뜀';
        RETURN;
    END IF;
    PERFORM 1 FROM core.users WHERE user_seq = me AND role = 'USER' AND is_deleted = FALSE;
    IF NOT FOUND THEN
        RAISE NOTICE '회원 1번이 USER가 아니어서 모의 입양 시드를 건너뜀';
        RETURN;
    END IF;
    SELECT b.breed_seq, b.personality INTO v_breed, v_personality
    FROM core.animal_breeds b JOIN core.animal_species s ON s.species_seq = b.species_seq
    WHERE b.breed_name = '말티즈' AND s.species_code = 'DOG'
    ORDER BY b.breed_seq LIMIT 1;
    IF NOT FOUND THEN
        RAISE NOTICE '말티즈 품종이 없어 모의 입양 시드를 건너뜀';
        RETURN;
    END IF;
    -- 스탯은 이 성격의 감소 배율로 계산했다
    IF v_personality <> 'FRIENDLY' THEN
        RAISE NOTICE '말티즈 성격이 %(기대 FRIENDLY)라 모의 입양 시드를 건너뜀', v_personality;
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM simulation_sessions WHERE user_seq = me AND started_dttm = '2026-11-03 20:00:00+09') THEN
        RAISE NOTICE '모의 입양 시드가 이미 있어 건너뜀';
        RETURN;
    END IF;

    INSERT INTO simulation_sessions (user_seq, breed_seq, animal_name, species_type, personality, status, expected_cost, balance,
                                     started_dttm, ends_dttm, ended_dttm, satiety, hydration, cleanliness, bond, calmness, health, energy,
                                     stats_calculated_dttm, last_action_dttm, reg_dttm, mod_dttm)
    VALUES (me, v_breed, '콩이', 'DOG', v_personality, 'COMPLETED', 120000, -15300,
            '2026-11-03 20:00:00+09', '2026-11-10 20:00:00+09', '2026-11-10 20:00:00+09', 96, 95, 98, 60, 73, 66, 39,
            '2026-11-10 20:00:00+09', '2026-11-10 19:40:00+09', '2026-11-03 20:00:00+09', '2026-11-10 20:00:00+09')
    RETURNING session_seq INTO v_session;

    -- 산책 14회
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-03 21:00:00+09', '2026-11-03 21:30:00+09', '2026-11-03 21:30:00+09', '2026-11-03 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-04 09:00:00+09', '2026-11-04 10:00:00+09', '2026-11-04 10:00:00+09', '2026-11-04 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-04 21:00:00+09', '2026-11-04 21:30:00+09', '2026-11-04 21:30:00+09', '2026-11-04 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-05 09:00:00+09', '2026-11-05 10:00:00+09', '2026-11-05 10:00:00+09', '2026-11-05 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-05 18:30:00+09', '2026-11-05 19:30:00+09', '2026-11-05 19:30:00+09', '2026-11-05 18:30:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-06 15:00:00+09', '2026-11-06 16:00:00+09', '2026-11-06 16:00:00+09', '2026-11-06 15:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-06 21:00:00+09', '2026-11-06 22:00:00+09', '2026-11-06 22:00:00+09', '2026-11-06 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-07 09:00:00+09', '2026-11-07 10:00:00+09', '2026-11-07 10:00:00+09', '2026-11-07 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-07 21:00:00+09', '2026-11-07 21:20:00+09', '2026-11-07 21:20:00+09', '2026-11-07 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-08 09:00:00+09', '2026-11-08 10:00:00+09', '2026-11-08 10:00:00+09', '2026-11-08 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-08 21:00:00+09', '2026-11-08 21:20:00+09', '2026-11-08 21:20:00+09', '2026-11-08 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-09 10:00:00+09', '2026-11-09 10:40:00+09', '2026-11-09 10:40:00+09', '2026-11-09 10:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-09 21:00:00+09', '2026-11-09 21:20:00+09', '2026-11-09 21:20:00+09', '2026-11-09 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-10 18:00:00+09', '2026-11-10 19:00:00+09', '2026-11-10 19:00:00+09', '2026-11-10 18:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;

    -- 행동 로그 146건과 돌발 이벤트 4건(시간 순)
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'SYSTEM', 'SESSION_START', NULL, NULL, NULL, NULL, '{"satiety": 70, "hydration": 70, "cleanliness": 80, "bond": 30, "calmness": 50, "health": 90, "energy": 60}', '2026-11-03 20:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 100, "hydration": 70, "cleanliness": 80, "bond": 31, "calmness": 50, "health": 90, "energy": 60}', '2026-11-03 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[1], NULL, NULL, '{"satiety": 96, "hydration": 65, "cleanliness": 78, "bond": 30, "calmness": 48, "health": 90, "energy": 57}', '2026-11-03 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[1], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 90, "hydration": 56, "cleanliness": 72, "bond": 33, "calmness": 60, "health": 90, "energy": 78}', '2026-11-03 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 44}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 33, "calmness": 60, "health": 90, "energy": 78}', '2026-11-03 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 38, "calmness": 62, "health": 90, "energy": 78}', '2026-11-03 21:50:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 86, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 59, "health": 90, "energy": 75}', '2026-11-03 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 91, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 67, "health": 90, "energy": 75}', '2026-11-03 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 55, "hydration": 100, "cleanliness": 82, "bond": 28, "calmness": 44, "health": 90, "energy": 48}', '2026-11-04 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 85, "hydration": 100, "cleanliness": 82, "bond": 29, "calmness": 44, "health": 90, "energy": 48}', '2026-11-04 07:40:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[2], NULL, NULL, '{"satiety": 77, "hydration": 90, "cleanliness": 78, "bond": 27, "calmness": 39, "health": 90, "energy": 42}', '2026-11-04 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[2], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 61, "hydration": 67, "cleanliness": 64, "bond": 32, "calmness": 60, "health": 90, "energy": 81}', '2026-11-04 10:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 1, NULL, 'PENDING', '2026-11-04 10:10:00+09', '2026-11-04 16:10:00+09', '2026-11-04 10:10:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'WATER', NULL, NULL, o[1], '{"hydration": 33, "calmness": 5}', '{"satiety": 61, "hydration": 100, "cleanliness": 64, "bond": 32, "calmness": 65, "health": 90, "energy": 81}', '2026-11-04 10:50:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-04 10:50:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[1];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 79, "hydration": 85, "cleanliness": 58, "bond": 30, "calmness": 57, "health": 90, "energy": 72}', '2026-11-04 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 79, "hydration": 85, "cleanliness": 58, "bond": 35, "calmness": 59, "health": 90, "energy": 72}', '2026-11-04 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 67, "hydration": 100, "cleanliness": 52, "bond": 32, "calmness": 51, "health": 90, "energy": 63}', '2026-11-04 16:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 63, "hydration": 95, "cleanliness": 50, "bond": 36, "calmness": 51, "health": 90, "energy": 60}', '2026-11-04 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 59, "hydration": 90, "cleanliness": 78, "bond": 35, "calmness": 48, "health": 90, "energy": 57}', '2026-11-04 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 85, "hydration": 85, "cleanliness": 76, "bond": 35, "calmness": 46, "health": 90, "energy": 54}', '2026-11-04 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 81, "hydration": 80, "cleanliness": 74, "bond": 39, "calmness": 45, "health": 90, "energy": 51}', '2026-11-04 20:30:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[3], NULL, NULL, '{"satiety": 77, "hydration": 75, "cleanliness": 72, "bond": 38, "calmness": 42, "health": 90, "energy": 48}', '2026-11-04 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[3], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 71, "hydration": 66, "cleanliness": 66, "bond": 41, "calmness": 54, "health": 90, "energy": 69}', '2026-11-04 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 34}', '{"satiety": 71, "hydration": 100, "cleanliness": 66, "bond": 41, "calmness": 54, "health": 90, "energy": 69}', '2026-11-04 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 71, "hydration": 100, "cleanliness": 66, "bond": 46, "calmness": 56, "health": 90, "energy": 69}', '2026-11-04 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 72, "hydration": 95, "cleanliness": 64, "bond": 45, "calmness": 62, "health": 90, "energy": 66}', '2026-11-04 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 36, "hydration": 100, "cleanliness": 46, "bond": 36, "calmness": 38, "health": 90, "energy": 39}', '2026-11-05 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 66, "hydration": 100, "cleanliness": 46, "bond": 37, "calmness": 38, "health": 90, "energy": 39}', '2026-11-05 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 62, "hydration": 95, "cleanliness": 74, "bond": 36, "calmness": 36, "health": 90, "energy": 36}', '2026-11-05 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[4], NULL, NULL, '{"satiety": 58, "hydration": 90, "cleanliness": 72, "bond": 35, "calmness": 33, "health": 90, "energy": 33}', '2026-11-05 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[4], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 42, "hydration": 67, "cleanliness": 58, "bond": 40, "calmness": 55, "health": 90, "energy": 72}', '2026-11-05 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 42, "hydration": 100, "cleanliness": 58, "bond": 40, "calmness": 55, "health": 90, "energy": 72}', '2026-11-05 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 60, "hydration": 85, "cleanliness": 52, "bond": 38, "calmness": 47, "health": 90, "energy": 63}', '2026-11-05 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 60, "hydration": 85, "cleanliness": 52, "bond": 43, "calmness": 49, "health": 90, "energy": 63}', '2026-11-05 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 15}', '{"satiety": 60, "hydration": 100, "cleanliness": 52, "bond": 43, "calmness": 49, "health": 90, "energy": 63}', '2026-11-05 13:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 74, "hydration": 80, "cleanliness": 44, "bond": 40, "calmness": 38, "health": 90, "energy": 51}', '2026-11-05 17:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 74, "hydration": 80, "cleanliness": 44, "bond": 45, "calmness": 40, "health": 90, "energy": 51}', '2026-11-05 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 70, "hydration": 75, "cleanliness": 72, "bond": 44, "calmness": 38, "health": 90, "energy": 48}', '2026-11-05 18:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[5], NULL, NULL, '{"satiety": 70, "hydration": 75, "cleanliness": 72, "bond": 44, "calmness": 38, "health": 90, "energy": 48}', '2026-11-05 18:30:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[5], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 54, "hydration": 52, "cleanliness": 58, "bond": 49, "calmness": 59, "health": 90, "energy": 87}', '2026-11-05 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 59, "hydration": 52, "cleanliness": 58, "bond": 49, "calmness": 67, "health": 90, "energy": 87}', '2026-11-05 19:35:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 59, "hydration": 52, "cleanliness": 58, "bond": 54, "calmness": 69, "health": 90, "energy": 87}', '2026-11-05 19:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 64, "hydration": 52, "cleanliness": 58, "bond": 54, "calmness": 77, "health": 90, "energy": 87}', '2026-11-05 19:45:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 52, "cleanliness": 58, "bond": 55, "calmness": 77, "health": 90, "energy": 87}', '2026-11-05 19:50:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 94, "hydration": 52, "cleanliness": 58, "bond": 60, "calmness": 79, "health": 90, "energy": 87}', '2026-11-05 19:55:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 76, "hydration": 0, "cleanliness": 34, "bond": 49, "calmness": 48, "health": 72, "energy": 51}', '2026-11-06 07:30:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 76, "hydration": 0, "cleanliness": 64, "bond": 49, "calmness": 48, "health": 72, "energy": 51}', '2026-11-06 07:40:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 5, NULL, 'PENDING', '2026-11-06 11:00:00+09', '2026-11-06 17:00:00+09', '2026-11-06 11:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 82, "hydration": 0, "cleanliness": 52, "bond": 44, "calmness": 32, "health": 54, "energy": 33}', '2026-11-06 13:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[6], NULL, NULL, '{"satiety": 74, "hydration": 0, "cleanliness": 48, "bond": 42, "calmness": 27, "health": 48, "energy": 27}', '2026-11-06 15:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[6], NULL, '{"satiety": -12, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 58, "hydration": 0, "cleanliness": 34, "bond": 47, "calmness": 49, "health": 45, "energy": 66}', '2026-11-06 16:00:00+09'),
        (v_session, 'EVENT', 'EVENT_EXPIRE', NULL, NULL, o[2], '{"bond": -5, "calmness": -15}', '{"satiety": 54, "hydration": 0, "cleanliness": 32, "bond": 41, "calmness": 31, "health": 42, "energy": 63}', '2026-11-06 17:00:00+09');
    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = o[2];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 46, "hydration": 50, "cleanliness": 28, "bond": 39, "calmness": 26, "health": 36, "energy": 57}', '2026-11-06 19:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 42, "hydration": 95, "cleanliness": 26, "bond": 38, "calmness": 23, "health": 36, "energy": 54}', '2026-11-06 20:06:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 47, "hydration": 95, "cleanliness": 26, "bond": 38, "calmness": 31, "health": 36, "energy": 54}', '2026-11-06 20:07:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 47, "hydration": 95, "cleanliness": 26, "bond": 43, "calmness": 33, "health": 36, "energy": 54}', '2026-11-06 20:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 47, "hydration": 95, "cleanliness": 56, "bond": 43, "calmness": 33, "health": 36, "energy": 54}', '2026-11-06 20:20:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[7], NULL, NULL, '{"satiety": 43, "hydration": 90, "cleanliness": 54, "bond": 42, "calmness": 31, "health": 36, "energy": 51}', '2026-11-06 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[7], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 27, "hydration": 67, "cleanliness": 40, "bond": 47, "calmness": 52, "health": 36, "energy": 90}', '2026-11-06 22:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 27, "hydration": 100, "cleanliness": 40, "bond": 47, "calmness": 52, "health": 36, "energy": 90}', '2026-11-06 22:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 57, "hydration": 100, "cleanliness": 40, "bond": 48, "calmness": 52, "health": 36, "energy": 90}', '2026-11-06 22:20:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 40, "calmness": -5}', '{"satiety": 57, "hydration": 100, "cleanliness": 80, "bond": 48, "calmness": 47, "health": 36, "energy": 90}', '2026-11-06 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 62, "hydration": 100, "cleanliness": 80, "bond": 48, "calmness": 55, "health": 36, "energy": 90}', '2026-11-06 22:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 67, "hydration": 100, "cleanliness": 80, "bond": 48, "calmness": 63, "health": 36, "energy": 90}', '2026-11-06 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 45}', '{"satiety": 31, "hydration": 100, "cleanliness": 62, "bond": 39, "calmness": 40, "health": 36, "energy": 63}', '2026-11-07 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 61, "hydration": 100, "cleanliness": 62, "bond": 40, "calmness": 40, "health": 36, "energy": 63}', '2026-11-07 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 61, "hydration": 100, "cleanliness": 92, "bond": 40, "calmness": 40, "health": 36, "energy": 63}', '2026-11-07 07:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[8], NULL, NULL, '{"satiety": 53, "hydration": 90, "cleanliness": 88, "bond": 38, "calmness": 34, "health": 36, "energy": 57}', '2026-11-07 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[8], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 37, "hydration": 67, "cleanliness": 74, "bond": 43, "calmness": 56, "health": 36, "energy": 96}', '2026-11-07 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 37, "hydration": 100, "cleanliness": 74, "bond": 43, "calmness": 56, "health": 36, "energy": 96}', '2026-11-07 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 55, "hydration": 85, "cleanliness": 68, "bond": 41, "calmness": 48, "health": 36, "energy": 87}', '2026-11-07 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 55, "hydration": 85, "cleanliness": 68, "bond": 46, "calmness": 50, "health": 36, "energy": 87}', '2026-11-07 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 43, "hydration": 100, "cleanliness": 62, "bond": 43, "calmness": 42, "health": 36, "energy": 78}', '2026-11-07 16:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 44, "hydration": 95, "cleanliness": 60, "bond": 42, "calmness": 48, "health": 36, "energy": 75}', '2026-11-07 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 40, "hydration": 90, "cleanliness": 88, "bond": 41, "calmness": 45, "health": 36, "energy": 72}', '2026-11-07 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 66, "hydration": 85, "cleanliness": 86, "bond": 41, "calmness": 42, "health": 36, "energy": 69}', '2026-11-07 19:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 71, "hydration": 85, "cleanliness": 86, "bond": 41, "calmness": 50, "health": 36, "energy": 69}', '2026-11-07 19:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 15}', '{"satiety": 71, "hydration": 100, "cleanliness": 86, "bond": 41, "calmness": 50, "health": 36, "energy": 69}', '2026-11-07 19:55:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[9], NULL, NULL, '{"satiety": 63, "hydration": 90, "cleanliness": 82, "bond": 39, "calmness": 45, "health": 36, "energy": 63}', '2026-11-07 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[9], NULL, '{"satiety": -4, "hydration": -6, "cleanliness": -4, "bond": 2, "calmness": 8, "energy": 14}', '{"satiety": 59, "hydration": 84, "cleanliness": 78, "bond": 41, "calmness": 53, "health": 36, "energy": 77}', '2026-11-07 21:20:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 59, "hydration": 84, "cleanliness": 78, "bond": 46, "calmness": 55, "health": 36, "energy": 77}', '2026-11-07 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 60, "hydration": 79, "cleanliness": 76, "bond": 45, "calmness": 61, "health": 36, "energy": 74}', '2026-11-07 22:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 65, "hydration": 79, "cleanliness": 76, "bond": 45, "calmness": 69, "health": 36, "energy": 74}', '2026-11-07 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 29, "hydration": 84, "cleanliness": 58, "bond": 36, "calmness": 45, "health": 36, "energy": 47}', '2026-11-08 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 59, "hydration": 84, "cleanliness": 58, "bond": 37, "calmness": 45, "health": 36, "energy": 47}', '2026-11-08 07:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 59, "hydration": 84, "cleanliness": 58, "bond": 42, "calmness": 47, "health": 36, "energy": 47}', '2026-11-08 07:50:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 55, "hydration": 79, "cleanliness": 86, "bond": 41, "calmness": 45, "health": 36, "energy": 44}', '2026-11-08 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[10], NULL, NULL, '{"satiety": 51, "hydration": 74, "cleanliness": 84, "bond": 40, "calmness": 42, "health": 36, "energy": 41}', '2026-11-08 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 13, w[10], 'PENDING', '2026-11-08 09:20:00+09', '2026-11-08 09:25:00+09', '2026-11-08 09:20:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'GIVE_WATER', NULL, w[10], o[3], '{"hydration": 26, "energy": 5}', '{"satiety": 51, "hydration": 100, "cleanliness": 84, "bond": 40, "calmness": 42, "health": 36, "energy": 46}', '2026-11-08 09:23:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-08 09:23:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[3];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[10], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 35, "hydration": 77, "cleanliness": 70, "bond": 45, "calmness": 63, "health": 36, "energy": 85}', '2026-11-08 10:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 40, "hydration": 77, "cleanliness": 70, "bond": 45, "calmness": 71, "health": 36, "energy": 85}', '2026-11-08 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 58, "hydration": 62, "cleanliness": 64, "bond": 43, "calmness": 64, "health": 36, "energy": 76}', '2026-11-08 13:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 63, "hydration": 62, "cleanliness": 64, "bond": 43, "calmness": 72, "health": 36, "energy": 76}', '2026-11-08 13:10:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 47, "hydration": 42, "cleanliness": 56, "bond": 44, "calmness": 63, "health": 36, "energy": 64}', '2026-11-08 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 43, "hydration": 37, "cleanliness": 84, "bond": 43, "calmness": 61, "health": 36, "energy": 61}', '2026-11-08 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 69, "hydration": 32, "cleanliness": 82, "bond": 43, "calmness": 58, "health": 36, "energy": 58}', '2026-11-08 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 69, "hydration": 32, "cleanliness": 82, "bond": 48, "calmness": 60, "health": 36, "energy": 58}', '2026-11-08 19:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 65, "hydration": 77, "cleanliness": 80, "bond": 47, "calmness": 57, "health": 36, "energy": 55}', '2026-11-08 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[11], NULL, NULL, '{"satiety": 61, "hydration": 72, "cleanliness": 78, "bond": 46, "calmness": 55, "health": 36, "energy": 52}', '2026-11-08 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[11], NULL, '{"satiety": -4, "hydration": -6, "cleanliness": -4, "bond": 2, "calmness": 8, "energy": 14}', '{"satiety": 57, "hydration": 66, "cleanliness": 74, "bond": 48, "calmness": 63, "health": 36, "energy": 66}', '2026-11-08 21:20:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 57, "hydration": 66, "cleanliness": 74, "bond": 53, "calmness": 65, "health": 36, "energy": 66}', '2026-11-08 21:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 39}', '{"satiety": 53, "hydration": 100, "cleanliness": 72, "bond": 52, "calmness": 62, "health": 36, "energy": 63}', '2026-11-08 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 58, "hydration": 100, "cleanliness": 72, "bond": 52, "calmness": 70, "health": 36, "energy": 63}', '2026-11-08 22:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 63, "hydration": 100, "cleanliness": 72, "bond": 52, "calmness": 78, "health": 36, "energy": 63}', '2026-11-08 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 45}', '{"satiety": 27, "hydration": 100, "cleanliness": 54, "bond": 43, "calmness": 55, "health": 36, "energy": 36}', '2026-11-09 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 57, "hydration": 100, "cleanliness": 54, "bond": 44, "calmness": 55, "health": 36, "energy": 36}', '2026-11-09 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 62, "hydration": 100, "cleanliness": 54, "bond": 44, "calmness": 63, "health": 36, "energy": 36}', '2026-11-09 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 62, "hydration": 100, "cleanliness": 84, "bond": 44, "calmness": 63, "health": 36, "energy": 36}', '2026-11-09 07:50:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 8, NULL, 'PENDING', '2026-11-09 08:10:00+09', '2026-11-09 14:10:00+09', '2026-11-09 08:10:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'VET_VISIT', NULL, NULL, o[4], '{"calmness": -10, "health": 30}', '{"satiety": 54, "hydration": 90, "cleanliness": 80, "bond": 42, "calmness": 48, "health": 66, "energy": 30}', '2026-11-09 09:10:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-09 09:10:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[4];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 59, "hydration": 90, "cleanliness": 80, "bond": 42, "calmness": 56, "health": 66, "energy": 30}', '2026-11-09 09:20:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[12], NULL, NULL, '{"satiety": 55, "hydration": 85, "cleanliness": 78, "bond": 41, "calmness": 53, "health": 66, "energy": 27}', '2026-11-09 10:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[12], NULL, '{"satiety": -8, "hydration": -12, "cleanliness": -8, "bond": 4, "calmness": 16, "energy": 28}', '{"satiety": 47, "hydration": 73, "cleanliness": 70, "bond": 45, "calmness": 69, "health": 66, "energy": 55}', '2026-11-09 10:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 32}', '{"satiety": 43, "hydration": 100, "cleanliness": 68, "bond": 44, "calmness": 66, "health": 66, "energy": 52}', '2026-11-09 11:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 43, "hydration": 100, "cleanliness": 68, "bond": 49, "calmness": 68, "health": 66, "energy": 52}', '2026-11-09 11:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 65, "hydration": 90, "cleanliness": 64, "bond": 48, "calmness": 63, "health": 66, "energy": 46}', '2026-11-09 13:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 70, "hydration": 90, "cleanliness": 64, "bond": 48, "calmness": 71, "health": 66, "energy": 46}', '2026-11-09 13:10:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 40, "calmness": -5}', '{"satiety": 62, "hydration": 80, "cleanliness": 100, "bond": 46, "calmness": 61, "health": 66, "energy": 40}', '2026-11-09 15:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 25}', '{"satiety": 58, "hydration": 100, "cleanliness": 98, "bond": 45, "calmness": 58, "health": 66, "energy": 37}', '2026-11-09 16:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 63, "hydration": 100, "cleanliness": 98, "bond": 45, "calmness": 66, "health": 66, "energy": 37}', '2026-11-09 16:10:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 59, "hydration": 95, "cleanliness": 96, "bond": 49, "calmness": 66, "health": 66, "energy": 34}', '2026-11-09 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 60, "hydration": 90, "cleanliness": 94, "bond": 48, "calmness": 71, "health": 66, "energy": 31}', '2026-11-09 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 86, "hydration": 85, "cleanliness": 92, "bond": 48, "calmness": 69, "health": 66, "energy": 28}', '2026-11-09 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 91, "hydration": 85, "cleanliness": 92, "bond": 48, "calmness": 77, "health": 66, "energy": 28}', '2026-11-09 19:40:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[13], NULL, NULL, '{"satiety": 83, "hydration": 75, "cleanliness": 88, "bond": 46, "calmness": 71, "health": 66, "energy": 22}', '2026-11-09 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[13], NULL, '{"satiety": -4, "hydration": -6, "cleanliness": -4, "bond": 2, "calmness": 8, "energy": 14}', '{"satiety": 79, "hydration": 69, "cleanliness": 84, "bond": 48, "calmness": 79, "health": 66, "energy": 36}', '2026-11-09 21:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 31}', '{"satiety": 79, "hydration": 100, "cleanliness": 84, "bond": 48, "calmness": 79, "health": 66, "energy": 36}', '2026-11-09 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 79, "hydration": 100, "cleanliness": 84, "bond": 53, "calmness": 81, "health": 66, "energy": 36}', '2026-11-09 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 80, "hydration": 95, "cleanliness": 82, "bond": 52, "calmness": 87, "health": 66, "energy": 33}', '2026-11-09 22:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 85, "hydration": 95, "cleanliness": 82, "bond": 52, "calmness": 95, "health": 66, "energy": 33}', '2026-11-09 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 49, "hydration": 100, "cleanliness": 64, "bond": 43, "calmness": 71, "health": 66, "energy": 6}', '2026-11-10 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 79, "hydration": 100, "cleanliness": 64, "bond": 44, "calmness": 71, "health": 66, "energy": 6}', '2026-11-10 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 75, "hydration": 95, "cleanliness": 92, "bond": 43, "calmness": 69, "health": 66, "energy": 3}', '2026-11-10 08:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 10}', '{"satiety": 71, "hydration": 100, "cleanliness": 90, "bond": 42, "calmness": 66, "health": 66, "energy": 0}', '2026-11-10 09:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 67, "hydration": 95, "cleanliness": 88, "bond": 46, "calmness": 66, "health": 66, "energy": 0}', '2026-11-10 10:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 89, "hydration": 85, "cleanliness": 84, "bond": 45, "calmness": 60, "health": 66, "energy": 0}', '2026-11-10 12:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 85, "hydration": 80, "cleanliness": 82, "bond": 49, "calmness": 60, "health": 66, "energy": 0}', '2026-11-10 13:10:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 77, "hydration": 70, "cleanliness": 78, "bond": 52, "calmness": 57, "health": 66, "energy": 0}', '2026-11-10 15:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 35}', '{"satiety": 73, "hydration": 100, "cleanliness": 76, "bond": 51, "calmness": 54, "health": 66, "energy": 0}', '2026-11-10 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 99, "hydration": 95, "cleanliness": 74, "bond": 51, "calmness": 51, "health": 66, "energy": 0}', '2026-11-10 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 1, "calmness": 8}', '{"satiety": 100, "hydration": 95, "cleanliness": 74, "bond": 51, "calmness": 59, "health": 66, "energy": 0}', '2026-11-10 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 26}', '{"satiety": 100, "hydration": 95, "cleanliness": 100, "bond": 51, "calmness": 59, "health": 66, "energy": 0}', '2026-11-10 17:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[14], NULL, NULL, '{"satiety": 96, "hydration": 90, "cleanliness": 98, "bond": 50, "calmness": 57, "health": 66, "energy": 0}', '2026-11-10 18:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[14], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 80, "hydration": 67, "cleanliness": 84, "bond": 55, "calmness": 78, "health": 66, "energy": 42}', '2026-11-10 19:00:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 16, "calmness": -5}', '{"satiety": 80, "hydration": 67, "cleanliness": 100, "bond": 55, "calmness": 73, "health": 66, "energy": 42}', '2026-11-10 19:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 20, "bond": 1}', '{"satiety": 100, "hydration": 67, "cleanliness": 100, "bond": 56, "calmness": 73, "health": 66, "energy": 42}', '2026-11-10 19:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 56, "calmness": 73, "health": 66, "energy": 42}', '2026-11-10 19:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 61, "calmness": 75, "health": 66, "energy": 42}', '2026-11-10 19:40:00+09'),
        (v_session, 'SYSTEM', 'SESSION_END', NULL, NULL, NULL, NULL, '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 60, "calmness": 73, "health": 66, "energy": 39}', '2026-11-10 20:00:00+09');

    -- 지출 12건(balance_after는 누적)
    INSERT INTO simulation_transactions (session_seq, transaction_type, item_seq, occurrence_seq, quantity, amount, balance_after, description, reg_dttm) VALUES
        (v_session, 'PURCHASE', 1, NULL, 1, 10000, 110000, '밥그릇 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 2, NULL, 1, 10000, 100000, '물그릇 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 4, NULL, 1, 15000, 85000, '강아지 배변판 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 9, NULL, 1, 14900, 70100, '하네스와 리드줄 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 10, NULL, 1, 13800, 56300, '위생 관리 키트 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 17, NULL, 1, 2700, 53600, '매듭 터그 로프 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 2, 21200, 32400, '균형 잡힌 사료 × 2', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 30, NULL, 2, 5200, 27200, '배변패드 × 2', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 1, 2000, 25200, '씹는 간식 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 32, NULL, 1, 500, 24700, '배변봉투 × 1', '2026-11-03 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 5, 10000, 14700, '씹는 간식 × 5', '2026-11-06 20:05:00+09'),
        (v_session, 'EVENT', NULL, o[4], 1, 30000, -15300, '동물병원 진료비 · 축 처져 있어요', '2026-11-09 09:10:00+09');

    -- 보유 물품(묶음 물품은 구매 수 × simulation.shop.uses-per-purchase에서 쓴 만큼 뺀 값)
    INSERT INTO simulation_inventory_items (session_seq, item_seq, quantity, reg_dttm, mod_dttm) VALUES
        (v_session, 1, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 2, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 4, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 9, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 10, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 17, 1, '2026-11-03 20:00:00+09', NULL),
        (v_session, 22, 0, '2026-11-03 20:00:00+09', '2026-11-10 19:20:00+09'),
        (v_session, 30, 7, '2026-11-03 20:00:00+09', '2026-11-10 17:50:00+09'),
        (v_session, 26, 0, '2026-11-03 20:00:00+09', '2026-11-10 17:10:00+09'),
        (v_session, 32, 10, '2026-11-03 20:00:00+09', NULL);

    -- 종료 리포트(summary NULL → 요약 복구 스케줄러가 채움)
    INSERT INTO simulation_reports (session_seq, expected_cost, total_spent, spent_by_category_jsonb, daily_spent_jsonb, final_stats_jsonb, action_count, event_count, event_resolved_count, walk_count, summary, reg_dttm)
    VALUES (v_session, 120000, 135300, '{"FOOD_BOWL": 10000, "WATER_BOWL": 10000, "LITTER_BOX": 15000, "TOY": 2700, "FURNITURE": 0, "LEASH": 14900, "GROOMING_KIT": 13800, "FOOD": 21200, "SNACK": 12000, "LITTER": 5200, "SHAMPOO": 0, "MEDICINE": 0, "POOP_BAG": 500, "CATNIP": 0, "EVENT": 30000}', '{"1": 95300, "2": 0, "3": 0, "4": 10000, "5": 0, "6": 30000, "7": 0}', '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 60, "calmness": 73, "health": 66, "energy": 39}', 143, 4, 3, 14, NULL, '2026-11-10 20:00:00+09');
END
$dev$;
