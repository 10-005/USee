-- 시연 회원 10명의 모의 입양 완료 세션(7일차 리포트 시연용). local·dev에서만 적용된다
-- 생성기가 만든 파일이다. 손으로 고치지 않는다: SEED_GEN=true ./gradlew :simulation:test --tests '*ShibaSeedGeneratorTest*' --rerun
-- (생성기 ShibaSeed가 실제 서비스를 고정 시계로 7일 돌린 결과. 스탯·지출·리포트 값은 그 결과 그대로)
-- 이야기: 개 시바견 "모찌" 7일 완료. 지출은 1~6일차에 나뉨. 돌발 이벤트 8건 모두 해결: #16·#6(1일차), #14·#4(3일차), #3(4일차), #15(5일차), #16·#5(6일차)
-- 리포트 summary는 NULL로 둔다. 배포 뒤 요약 복구 스케줄러가 채운다(LLM 키가 없으면 규칙 문구)
-- 대상 회원(1, 140, 141, 142, 143, 144, 145, 146, 147, 148번)마다, 회원이 USER가 아니거나(로컬 새 DB는 보호소 계정), 시바견 품종이 없거나 성격이 다르거나, 이미 넣었으면 건너뛴다
DO $dev$
DECLARE
    me            INTEGER;
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
    FOREACH me IN ARRAY ARRAY[1, 140, 141, 142, 143, 144, 145, 146, 147, 148] LOOP
    w := '{}'; o := '{}';
    PERFORM 1 FROM core.users WHERE user_seq = me AND role = 'USER' AND is_deleted = FALSE;
    IF NOT FOUND THEN
        RAISE NOTICE '회원 %번이 USER가 아니어서 모의 입양 시드를 건너뜀', me;
        CONTINUE;
    END IF;
    SELECT b.breed_seq, b.personality INTO v_breed, v_personality
    FROM core.animal_breeds b JOIN core.animal_species s ON s.species_seq = b.species_seq
    WHERE b.breed_name = '시바견' AND s.species_code = 'DOG'
    ORDER BY b.breed_seq LIMIT 1;
    IF NOT FOUND THEN
        RAISE NOTICE '시바견 품종이 없어 모의 입양 시드를 건너뜀';
        CONTINUE;
    END IF;
    -- 스탯은 이 성격의 감소 배율로 계산했다
    IF v_personality <> 'TIMID' THEN
        RAISE NOTICE '시바견 성격이 %(기대 TIMID)라 모의 입양 시드를 건너뜀', v_personality;
        CONTINUE;
    END IF;
    IF EXISTS (SELECT 1 FROM simulation_sessions WHERE user_seq = me AND started_dttm = '2026-11-09 20:00:00+09') THEN
        RAISE NOTICE '모의 입양 시드가 이미 있어 건너뜀';
        CONTINUE;
    END IF;

    INSERT INTO simulation_sessions (user_seq, breed_seq, animal_name, species_type, personality, status, expected_cost, balance,
                                     started_dttm, ends_dttm, ended_dttm, satiety, hydration, cleanliness, bond, calmness, health, energy,
                                     stats_calculated_dttm, last_action_dttm, reg_dttm, mod_dttm)
    VALUES (me, v_breed, '모찌', 'DOG', v_personality, 'COMPLETED', 150000, 26100,
            '2026-11-09 20:00:00+09', '2026-11-16 20:00:00+09', '2026-11-16 20:00:00+09', 96, 95, 98, 99, 77, 90, 39,
            '2026-11-16 20:00:00+09', '2026-11-16 19:40:00+09', '2026-11-09 20:00:00+09', '2026-11-16 20:00:00+09')
    RETURNING session_seq INTO v_session;

    -- 산책 16회
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-09 21:00:00+09', '2026-11-09 21:30:00+09', '2026-11-09 21:30:00+09', '2026-11-09 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-10 09:00:00+09', '2026-11-10 10:00:00+09', '2026-11-10 10:00:00+09', '2026-11-10 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-10 21:00:00+09', '2026-11-10 21:30:00+09', '2026-11-10 21:30:00+09', '2026-11-10 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-11 09:00:00+09', '2026-11-11 09:30:00+09', '2026-11-11 09:30:00+09', '2026-11-11 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-11 18:30:00+09', '2026-11-11 19:30:00+09', '2026-11-11 19:30:00+09', '2026-11-11 18:30:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-11 21:00:00+09', '2026-11-11 21:30:00+09', '2026-11-11 21:30:00+09', '2026-11-11 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-12 09:00:00+09', '2026-11-12 10:00:00+09', '2026-11-12 10:00:00+09', '2026-11-12 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-12 18:30:00+09', '2026-11-12 19:00:00+09', '2026-11-12 19:00:00+09', '2026-11-12 18:30:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-13 09:00:00+09', '2026-11-13 10:00:00+09', '2026-11-13 10:00:00+09', '2026-11-13 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-13 15:00:00+09', '2026-11-13 15:30:00+09', '2026-11-13 15:30:00+09', '2026-11-13 15:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-13 21:00:00+09', '2026-11-13 22:00:00+09', '2026-11-13 22:00:00+09', '2026-11-13 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-14 09:00:00+09', '2026-11-14 10:00:00+09', '2026-11-14 10:00:00+09', '2026-11-14 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-14 21:00:00+09', '2026-11-14 21:30:00+09', '2026-11-14 21:30:00+09', '2026-11-14 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-15 09:00:00+09', '2026-11-15 10:00:00+09', '2026-11-15 10:00:00+09', '2026-11-15 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-15 21:00:00+09', '2026-11-15 21:20:00+09', '2026-11-15 21:20:00+09', '2026-11-15 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-16 18:00:00+09', '2026-11-16 19:00:00+09', '2026-11-16 19:00:00+09', '2026-11-16 18:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;

    -- 행동 로그 167건과 돌발 이벤트 8건(시간 순)
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'SYSTEM', 'SESSION_START', NULL, NULL, NULL, NULL, '{"satiety": 70, "hydration": 70, "cleanliness": 80, "bond": 30, "calmness": 50, "health": 90, "energy": 60}', '2026-11-09 20:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 100, "hydration": 70, "cleanliness": 80, "bond": 31, "calmness": 50, "health": 90, "energy": 60}', '2026-11-09 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[1], NULL, NULL, '{"satiety": 96, "hydration": 65, "cleanliness": 78, "bond": 30, "calmness": 47, "health": 90, "energy": 57}', '2026-11-09 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[1], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 90, "hydration": 56, "cleanliness": 72, "bond": 33, "calmness": 59, "health": 90, "energy": 78}', '2026-11-09 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 44}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 33, "calmness": 59, "health": 90, "energy": 78}', '2026-11-09 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 38, "calmness": 61, "health": 90, "energy": 78}', '2026-11-09 21:50:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 86, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 58, "health": 90, "energy": 75}', '2026-11-09 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 91, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 66, "health": 90, "energy": 75}', '2026-11-09 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 55, "hydration": 100, "cleanliness": 82, "bond": 28, "calmness": 39, "health": 90, "energy": 48}', '2026-11-10 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 85, "hydration": 100, "cleanliness": 82, "bond": 29, "calmness": 39, "health": 90, "energy": 48}', '2026-11-10 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 90, "hydration": 100, "cleanliness": 82, "bond": 29, "calmness": 47, "health": 90, "energy": 48}', '2026-11-10 07:45:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[2], NULL, NULL, '{"satiety": 82, "hydration": 90, "cleanliness": 78, "bond": 27, "calmness": 41, "health": 90, "energy": 42}', '2026-11-10 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 16, w[2], 'PENDING', '2026-11-10 09:15:00+09', '2026-11-10 09:20:00+09', '2026-11-10 09:15:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'CLEAN_UP_POOP', 32, w[2], o[1], '{"bond": 2}', '{"satiety": 82, "hydration": 90, "cleanliness": 78, "bond": 29, "calmness": 41, "health": 90, "energy": 42}', '2026-11-10 09:16:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-10 09:16:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[1];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[2], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 66, "hydration": 67, "cleanliness": 64, "bond": 34, "calmness": 62, "health": 90, "energy": 81}', '2026-11-10 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 66, "hydration": 100, "cleanliness": 64, "bond": 34, "calmness": 62, "health": 90, "energy": 81}', '2026-11-10 10:10:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 6, NULL, 'PENDING', '2026-11-10 11:00:00+09', '2026-11-10 17:00:00+09', '2026-11-10 11:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'SNACK', 26, NULL, o[2], '{"satiety": 5, "bond": 10, "calmness": 8}', '{"satiety": 67, "hydration": 95, "cleanliness": 62, "bond": 43, "calmness": 67, "health": 90, "energy": 78}', '2026-11-10 11:05:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-10 11:05:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[2];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 89, "hydration": 85, "cleanliness": 58, "bond": 42, "calmness": 61, "health": 90, "energy": 72}', '2026-11-10 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 89, "hydration": 85, "cleanliness": 58, "bond": 47, "calmness": 63, "health": 90, "energy": 72}', '2026-11-10 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 77, "hydration": 100, "cleanliness": 52, "bond": 44, "calmness": 54, "health": 90, "energy": 63}', '2026-11-10 16:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 73, "hydration": 95, "cleanliness": 50, "bond": 48, "calmness": 53, "health": 90, "energy": 60}', '2026-11-10 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 69, "hydration": 90, "cleanliness": 78, "bond": 47, "calmness": 50, "health": 90, "energy": 57}', '2026-11-10 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 95, "hydration": 85, "cleanliness": 76, "bond": 47, "calmness": 47, "health": 90, "energy": 54}', '2026-11-10 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 91, "hydration": 80, "cleanliness": 74, "bond": 51, "calmness": 46, "health": 90, "energy": 51}', '2026-11-10 20:30:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[3], NULL, NULL, '{"satiety": 87, "hydration": 75, "cleanliness": 72, "bond": 50, "calmness": 43, "health": 90, "energy": 48}', '2026-11-10 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[3], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 81, "hydration": 66, "cleanliness": 66, "bond": 53, "calmness": 55, "health": 90, "energy": 69}', '2026-11-10 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 34}', '{"satiety": 81, "hydration": 100, "cleanliness": 66, "bond": 53, "calmness": 55, "health": 90, "energy": 69}', '2026-11-10 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 81, "hydration": 100, "cleanliness": 66, "bond": 58, "calmness": 57, "health": 90, "energy": 69}', '2026-11-10 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 82, "hydration": 95, "cleanliness": 64, "bond": 57, "calmness": 62, "health": 90, "energy": 66}', '2026-11-10 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 46, "hydration": 100, "cleanliness": 46, "bond": 48, "calmness": 35, "health": 90, "energy": 39}', '2026-11-11 07:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 76, "hydration": 100, "cleanliness": 46, "bond": 49, "calmness": 35, "health": 90, "energy": 39}', '2026-11-11 07:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 81, "hydration": 100, "cleanliness": 46, "bond": 49, "calmness": 43, "health": 90, "energy": 39}', '2026-11-11 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 77, "hydration": 95, "cleanliness": 74, "bond": 48, "calmness": 40, "health": 90, "energy": 36}', '2026-11-11 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[4], NULL, NULL, '{"satiety": 73, "hydration": 90, "cleanliness": 72, "bond": 47, "calmness": 37, "health": 90, "energy": 33}', '2026-11-11 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[4], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 67, "hydration": 81, "cleanliness": 66, "bond": 50, "calmness": 49, "health": 90, "energy": 54}', '2026-11-11 09:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 59, "hydration": 71, "cleanliness": 62, "bond": 53, "calmness": 45, "health": 90, "energy": 48}', '2026-11-11 11:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 81, "hydration": 61, "cleanliness": 58, "bond": 52, "calmness": 39, "health": 90, "energy": 42}', '2026-11-11 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 81, "hydration": 61, "cleanliness": 58, "bond": 57, "calmness": 41, "health": 90, "energy": 42}', '2026-11-11 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 44}', '{"satiety": 77, "hydration": 100, "cleanliness": 56, "bond": 56, "calmness": 38, "health": 90, "energy": 39}', '2026-11-11 14:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 10}', '{"satiety": 69, "hydration": 100, "cleanliness": 52, "bond": 54, "calmness": 32, "health": 90, "energy": 33}', '2026-11-11 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 95, "hydration": 95, "cleanliness": 50, "bond": 54, "calmness": 29, "health": 90, "energy": 30}', '2026-11-11 17:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 95, "hydration": 95, "cleanliness": 50, "bond": 59, "calmness": 31, "health": 90, "energy": 30}', '2026-11-11 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 91, "hydration": 90, "cleanliness": 78, "bond": 58, "calmness": 28, "health": 90, "energy": 27}', '2026-11-11 18:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[5], NULL, NULL, '{"satiety": 91, "hydration": 90, "cleanliness": 78, "bond": 58, "calmness": 28, "health": 90, "energy": 27}', '2026-11-11 18:30:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[5], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 75, "hydration": 67, "cleanliness": 64, "bond": 63, "calmness": 49, "health": 90, "energy": 66}', '2026-11-11 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 80, "hydration": 67, "cleanliness": 64, "bond": 63, "calmness": 57, "health": 90, "energy": 66}', '2026-11-11 19:35:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 80, "hydration": 67, "cleanliness": 64, "bond": 68, "calmness": 59, "health": 90, "energy": 66}', '2026-11-11 19:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 85, "hydration": 67, "cleanliness": 64, "bond": 68, "calmness": 67, "health": 90, "energy": 66}', '2026-11-11 19:45:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 15, "bond": 1}', '{"satiety": 100, "hydration": 67, "cleanliness": 64, "bond": 69, "calmness": 67, "health": 90, "energy": 66}', '2026-11-11 19:50:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 67, "cleanliness": 64, "bond": 74, "calmness": 69, "health": 90, "energy": 66}', '2026-11-11 19:55:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 38}', '{"satiety": 96, "hydration": 100, "cleanliness": 62, "bond": 73, "calmness": 66, "health": 90, "energy": 63}', '2026-11-11 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[6], NULL, NULL, '{"satiety": 92, "hydration": 95, "cleanliness": 60, "bond": 72, "calmness": 63, "health": 90, "energy": 60}', '2026-11-11 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[6], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 86, "hydration": 86, "cleanliness": 54, "bond": 75, "calmness": 75, "health": 90, "energy": 81}', '2026-11-11 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 14}', '{"satiety": 86, "hydration": 100, "cleanliness": 54, "bond": 75, "calmness": 75, "health": 90, "energy": 81}', '2026-11-11 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 86, "hydration": 100, "cleanliness": 54, "bond": 80, "calmness": 77, "health": 90, "energy": 81}', '2026-11-11 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 87, "hydration": 95, "cleanliness": 52, "bond": 79, "calmness": 82, "health": 90, "energy": 78}', '2026-11-11 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 51, "hydration": 100, "cleanliness": 34, "bond": 70, "calmness": 55, "health": 90, "energy": 51}', '2026-11-12 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 81, "hydration": 100, "cleanliness": 34, "bond": 71, "calmness": 55, "health": 90, "energy": 51}', '2026-11-12 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 86, "hydration": 100, "cleanliness": 34, "bond": 71, "calmness": 63, "health": 90, "energy": 51}', '2026-11-12 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 82, "hydration": 95, "cleanliness": 62, "bond": 70, "calmness": 60, "health": 90, "energy": 48}', '2026-11-12 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[7], NULL, NULL, '{"satiety": 78, "hydration": 90, "cleanliness": 60, "bond": 69, "calmness": 57, "health": 90, "energy": 45}', '2026-11-12 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 14, w[7], 'PENDING', '2026-11-12 09:20:00+09', '2026-11-12 09:25:00+09', '2026-11-12 09:20:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'GIVE_SNACK', 26, w[7], o[3], '{"satiety": 8, "bond": 3, "calmness": 18}', '{"satiety": 86, "hydration": 90, "cleanliness": 60, "bond": 72, "calmness": 75, "health": 90, "energy": 45}', '2026-11-12 09:21:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-12 09:21:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[3];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[7], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 70, "hydration": 67, "cleanliness": 46, "bond": 77, "calmness": 96, "health": 90, "energy": 84}', '2026-11-12 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 70, "hydration": 100, "cleanliness": 46, "bond": 77, "calmness": 96, "health": 90, "energy": 84}', '2026-11-12 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 88, "hydration": 85, "cleanliness": 40, "bond": 75, "calmness": 87, "health": 90, "energy": 75}', '2026-11-12 13:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 4, NULL, 'PENDING', '2026-11-12 14:00:00+09', '2026-11-12 20:00:00+09', '2026-11-12 14:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'PLAY', 17, NULL, o[4], '{"bond": 8, "calmness": 16, "energy": -5}', '{"satiety": 84, "hydration": 80, "cleanliness": 38, "bond": 82, "calmness": 100, "health": 90, "energy": 67}', '2026-11-12 14:05:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-12 14:05:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[4];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 76, "hydration": 100, "cleanliness": 34, "bond": 80, "calmness": 94, "health": 90, "energy": 61}', '2026-11-12 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 28, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 32, "bond": 80, "calmness": 91, "health": 90, "energy": 58}', '2026-11-12 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 8}', '{"satiety": 100, "hydration": 95, "cleanliness": 32, "bond": 80, "calmness": 99, "health": 90, "energy": 58}', '2026-11-12 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 60, "bond": 79, "calmness": 96, "health": 90, "energy": 55}', '2026-11-12 18:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[8], NULL, NULL, '{"satiety": 96, "hydration": 90, "cleanliness": 60, "bond": 79, "calmness": 96, "health": 90, "energy": 55}', '2026-11-12 18:30:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[8], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 7, "energy": 21}', '{"satiety": 86, "hydration": 76, "cleanliness": 52, "bond": 81, "calmness": 100, "health": 90, "energy": 73}', '2026-11-12 19:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5}', '{"satiety": 86, "hydration": 76, "cleanliness": 52, "bond": 86, "calmness": 100, "health": 90, "energy": 73}', '2026-11-12 19:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 14, "bond": 1}', '{"satiety": 100, "hydration": 76, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-11-12 19:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 24}', '{"satiety": 100, "hydration": 100, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-11-12 19:45:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{}', '{"satiety": 100, "hydration": 100, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-11-12 19:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 52, "hydration": 90, "cleanliness": 28, "bond": 75, "calmness": 64, "health": 90, "energy": 37}', '2026-11-13 07:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 82, "hydration": 90, "cleanliness": 28, "bond": 76, "calmness": 64, "health": 90, "energy": 37}', '2026-11-13 07:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 87, "hydration": 90, "cleanliness": 28, "bond": 76, "calmness": 72, "health": 90, "energy": 37}', '2026-11-13 07:35:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 3, NULL, 'PENDING', '2026-11-13 08:30:00+09', '2026-11-13 14:30:00+09', '2026-11-13 08:30:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, o[5], '{"cleanliness": 40, "calmness": 5}', '{"satiety": 83, "hydration": 85, "cleanliness": 66, "bond": 75, "calmness": 74, "health": 90, "energy": 34}', '2026-11-13 08:35:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-13 08:35:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[5];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_START', NULL, w[9], NULL, NULL, '{"satiety": 79, "hydration": 80, "cleanliness": 64, "bond": 74, "calmness": 71, "health": 90, "energy": 31}', '2026-11-13 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[9], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 63, "hydration": 57, "cleanliness": 50, "bond": 79, "calmness": 92, "health": 90, "energy": 70}', '2026-11-13 10:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 81, "hydration": 42, "cleanliness": 44, "bond": 77, "calmness": 83, "health": 90, "energy": 61}', '2026-11-13 13:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 81, "hydration": 92, "cleanliness": 44, "bond": 77, "calmness": 83, "health": 90, "energy": 61}', '2026-11-13 13:10:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 86, "hydration": 92, "cleanliness": 44, "bond": 77, "calmness": 91, "health": 90, "energy": 61}', '2026-11-13 13:15:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[10], NULL, NULL, '{"satiety": 78, "hydration": 82, "cleanliness": 40, "bond": 75, "calmness": 85, "health": 90, "energy": 55}', '2026-11-13 15:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[10], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 72, "hydration": 73, "cleanliness": 34, "bond": 78, "calmness": 97, "health": 90, "energy": 76}', '2026-11-13 15:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 32}', '{"satiety": 68, "hydration": 100, "cleanliness": 32, "bond": 77, "calmness": 94, "health": 90, "energy": 73}', '2026-11-13 16:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 69, "hydration": 95, "cleanliness": 30, "bond": 76, "calmness": 99, "health": 90, "energy": 70}', '2026-11-13 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 61, "hydration": 85, "cleanliness": 56, "bond": 74, "calmness": 93, "health": 90, "energy": 64}', '2026-11-13 19:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 61, "hydration": 85, "cleanliness": 56, "bond": 79, "calmness": 95, "health": 90, "energy": 64}', '2026-11-13 19:30:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 57, "hydration": 80, "cleanliness": 84, "bond": 78, "calmness": 92, "health": 90, "energy": 61}', '2026-11-13 20:06:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 87, "hydration": 80, "cleanliness": 84, "bond": 79, "calmness": 92, "health": 90, "energy": 61}', '2026-11-13 20:07:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 20}', '{"satiety": 87, "hydration": 100, "cleanliness": 84, "bond": 79, "calmness": 92, "health": 90, "energy": 61}', '2026-11-13 20:10:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 92, "hydration": 100, "cleanliness": 84, "bond": 79, "calmness": 100, "health": 90, "energy": 61}', '2026-11-13 20:15:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5}', '{"satiety": 92, "hydration": 100, "cleanliness": 84, "bond": 84, "calmness": 100, "health": 90, "energy": 61}', '2026-11-13 20:20:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[11], NULL, NULL, '{"satiety": 88, "hydration": 95, "cleanliness": 82, "bond": 83, "calmness": 97, "health": 90, "energy": 58}', '2026-11-13 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[11], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 6, "energy": 42}', '{"satiety": 72, "hydration": 72, "cleanliness": 68, "bond": 88, "calmness": 100, "health": 90, "energy": 97}', '2026-11-13 22:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 28}', '{"satiety": 72, "hydration": 100, "cleanliness": 68, "bond": 88, "calmness": 100, "health": 90, "energy": 97}', '2026-11-13 22:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 28, "bond": 1}', '{"satiety": 100, "hydration": 100, "cleanliness": 68, "bond": 89, "calmness": 100, "health": 90, "energy": 97}', '2026-11-13 22:20:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 32, "calmness": -5}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 89, "calmness": 95, "health": 90, "energy": 97}', '2026-11-13 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 5}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 89, "calmness": 100, "health": 90, "energy": 97}', '2026-11-13 22:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 89, "calmness": 100, "health": 90, "energy": 97}', '2026-11-13 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 45}', '{"satiety": 64, "hydration": 100, "cleanliness": 82, "bond": 80, "calmness": 73, "health": 90, "energy": 70}', '2026-11-14 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 82, "bond": 81, "calmness": 73, "health": 90, "energy": 70}', '2026-11-14 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 99, "hydration": 100, "cleanliness": 82, "bond": 81, "calmness": 81, "health": 90, "energy": 70}', '2026-11-14 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 18}', '{"satiety": 99, "hydration": 100, "cleanliness": 100, "bond": 81, "calmness": 81, "health": 90, "energy": 70}', '2026-11-14 07:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[12], NULL, NULL, '{"satiety": 91, "hydration": 90, "cleanliness": 96, "bond": 79, "calmness": 75, "health": 90, "energy": 64}', '2026-11-14 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 15, w[12], 'PENDING', '2026-11-14 09:20:00+09', '2026-11-14 09:25:00+09', '2026-11-14 09:20:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'GIVE_SNACK', 26, w[12], o[6], '{"satiety": 8, "bond": 5, "calmness": 8, "energy": 5}', '{"satiety": 99, "hydration": 90, "cleanliness": 96, "bond": 84, "calmness": 83, "health": 90, "energy": 69}', '2026-11-14 09:21:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-14 09:21:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[6];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[12], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 20, "energy": 34}', '{"satiety": 83, "hydration": 67, "cleanliness": 82, "bond": 89, "calmness": 100, "health": 90, "energy": 100}', '2026-11-14 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 83, "hydration": 100, "cleanliness": 82, "bond": 89, "calmness": 100, "health": 90, "energy": 100}', '2026-11-14 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 29, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 76, "bond": 87, "calmness": 91, "health": 90, "energy": 91}', '2026-11-14 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 85, "cleanliness": 76, "bond": 92, "calmness": 93, "health": 90, "energy": 91}', '2026-11-14 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 88, "hydration": 100, "cleanliness": 70, "bond": 89, "calmness": 84, "health": 90, "energy": 82}', '2026-11-14 16:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 89, "hydration": 95, "cleanliness": 68, "bond": 88, "calmness": 89, "health": 90, "energy": 79}', '2026-11-14 17:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 11, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 68, "bond": 89, "calmness": 89, "health": 90, "energy": 79}', '2026-11-14 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 96, "bond": 88, "calmness": 86, "health": 90, "energy": 76}', '2026-11-14 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 8, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 94, "bond": 88, "calmness": 83, "health": 90, "energy": 73}', '2026-11-14 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 85, "cleanliness": 94, "bond": 93, "calmness": 85, "health": 90, "energy": 73}', '2026-11-14 19:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[13], NULL, NULL, '{"satiety": 92, "hydration": 75, "cleanliness": 90, "bond": 91, "calmness": 79, "health": 90, "energy": 67}', '2026-11-14 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[13], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 86, "hydration": 66, "cleanliness": 84, "bond": 94, "calmness": 91, "health": 90, "energy": 88}', '2026-11-14 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 34}', '{"satiety": 86, "hydration": 100, "cleanliness": 84, "bond": 94, "calmness": 91, "health": 90, "energy": 88}', '2026-11-14 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 86, "hydration": 100, "cleanliness": 84, "bond": 99, "calmness": 93, "health": 90, "energy": 88}', '2026-11-14 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 87, "hydration": 95, "cleanliness": 82, "bond": 98, "calmness": 98, "health": 90, "energy": 85}', '2026-11-14 22:40:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 13, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 82, "bond": 99, "calmness": 98, "health": 90, "energy": 85}', '2026-11-14 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 64, "hydration": 100, "cleanliness": 64, "bond": 90, "calmness": 71, "health": 90, "energy": 58}', '2026-11-15 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 64, "bond": 91, "calmness": 71, "health": 90, "energy": 58}', '2026-11-15 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 99, "hydration": 100, "cleanliness": 64, "bond": 91, "calmness": 79, "health": 90, "energy": 58}', '2026-11-15 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 95, "hydration": 95, "cleanliness": 92, "bond": 90, "calmness": 76, "health": 90, "energy": 55}', '2026-11-15 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[14], NULL, NULL, '{"satiety": 91, "hydration": 90, "cleanliness": 90, "bond": 89, "calmness": 73, "health": 90, "energy": 52}', '2026-11-15 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 16, w[14], 'PENDING', '2026-11-15 09:30:00+09', '2026-11-15 09:35:00+09', '2026-11-15 09:30:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'CLEAN_UP_POOP', 32, w[14], o[7], '{"bond": 2}', '{"satiety": 91, "hydration": 90, "cleanliness": 90, "bond": 91, "calmness": 73, "health": 90, "energy": 52}', '2026-11-15 09:31:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-15 09:31:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[7];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[14], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 75, "hydration": 67, "cleanliness": 76, "bond": 96, "calmness": 94, "health": 90, "energy": 91}', '2026-11-15 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 75, "hydration": 100, "cleanliness": 76, "bond": 96, "calmness": 94, "health": 90, "energy": 91}', '2026-11-15 10:10:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 5, NULL, 'PENDING', '2026-11-15 11:00:00+09', '2026-11-15 17:00:00+09', '2026-11-15 11:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'PLAY', 17, NULL, o[8], '{"satiety": -5, "bond": 5, "calmness": 9, "energy": -10}', '{"satiety": 66, "hydration": 95, "cleanliness": 74, "bond": 100, "calmness": 100, "health": 90, "energy": 78}', '2026-11-15 11:03:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-15 11:03:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[8];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 88, "hydration": 85, "cleanliness": 70, "bond": 99, "calmness": 94, "health": 90, "energy": 72}', '2026-11-15 13:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 6}', '{"satiety": 93, "hydration": 85, "cleanliness": 70, "bond": 99, "calmness": 100, "health": 90, "energy": 72}', '2026-11-15 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 81, "hydration": 100, "cleanliness": 64, "bond": 96, "calmness": 91, "health": 90, "energy": 63}', '2026-11-15 16:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 77, "hydration": 95, "cleanliness": 62, "bond": 100, "calmness": 90, "health": 90, "energy": 60}', '2026-11-15 17:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 23}', '{"satiety": 100, "hydration": 95, "cleanliness": 62, "bond": 100, "calmness": 90, "health": 90, "energy": 60}', '2026-11-15 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 90, "bond": 99, "calmness": 87, "health": 90, "energy": 57}', '2026-11-15 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 8, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 88, "bond": 99, "calmness": 84, "health": 90, "energy": 54}', '2026-11-15 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 8}', '{"satiety": 100, "hydration": 85, "cleanliness": 88, "bond": 99, "calmness": 92, "health": 90, "energy": 54}', '2026-11-15 19:40:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[15], NULL, NULL, '{"satiety": 92, "hydration": 75, "cleanliness": 84, "bond": 97, "calmness": 86, "health": 90, "energy": 48}', '2026-11-15 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[15], NULL, '{"satiety": -4, "hydration": -6, "cleanliness": -4, "bond": 2, "calmness": 8, "energy": 14}', '{"satiety": 88, "hydration": 69, "cleanliness": 80, "bond": 99, "calmness": 94, "health": 90, "energy": 62}', '2026-11-15 21:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 31}', '{"satiety": 88, "hydration": 100, "cleanliness": 80, "bond": 99, "calmness": 94, "health": 90, "energy": 62}', '2026-11-15 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 1, "calmness": 2}', '{"satiety": 88, "hydration": 100, "cleanliness": 80, "bond": 100, "calmness": 96, "health": 90, "energy": 62}', '2026-11-15 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 7}', '{"satiety": 89, "hydration": 95, "cleanliness": 78, "bond": 99, "calmness": 100, "health": 90, "energy": 59}', '2026-11-15 22:40:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 11, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 78, "bond": 100, "calmness": 100, "health": 90, "energy": 59}', '2026-11-15 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 64, "hydration": 100, "cleanliness": 60, "bond": 91, "calmness": 73, "health": 90, "energy": 32}', '2026-11-16 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 60, "bond": 92, "calmness": 73, "health": 90, "energy": 32}', '2026-11-16 07:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 99, "hydration": 100, "cleanliness": 60, "bond": 92, "calmness": 81, "health": 90, "energy": 32}', '2026-11-16 07:45:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 95, "hydration": 95, "cleanliness": 88, "bond": 91, "calmness": 78, "health": 90, "energy": 29}', '2026-11-16 08:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 10}', '{"satiety": 91, "hydration": 100, "cleanliness": 86, "bond": 90, "calmness": 75, "health": 90, "energy": 26}', '2026-11-16 09:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 87, "hydration": 95, "cleanliness": 84, "bond": 94, "calmness": 74, "health": 90, "energy": 23}', '2026-11-16 10:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 21, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 80, "bond": 93, "calmness": 68, "health": 90, "energy": 17}', '2026-11-16 12:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 96, "hydration": 80, "cleanliness": 78, "bond": 97, "calmness": 67, "health": 90, "energy": 14}', '2026-11-16 13:10:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 88, "hydration": 70, "cleanliness": 74, "bond": 100, "calmness": 63, "health": 90, "energy": 8}', '2026-11-16 15:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 35}', '{"satiety": 84, "hydration": 100, "cleanliness": 72, "bond": 99, "calmness": 60, "health": 90, "energy": 5}', '2026-11-16 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 20, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 70, "bond": 99, "calmness": 57, "health": 90, "energy": 2}', '2026-11-16 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 8}', '{"satiety": 100, "hydration": 95, "cleanliness": 70, "bond": 99, "calmness": 65, "health": 90, "energy": 2}', '2026-11-16 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 100, "hydration": 95, "cleanliness": 100, "bond": 99, "calmness": 65, "health": 90, "energy": 2}', '2026-11-16 17:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[16], NULL, NULL, '{"satiety": 96, "hydration": 90, "cleanliness": 98, "bond": 98, "calmness": 62, "health": 90, "energy": 0}', '2026-11-16 18:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[16], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 3, "calmness": 24, "energy": 42}', '{"satiety": 80, "hydration": 67, "cleanliness": 84, "bond": 100, "calmness": 83, "health": 90, "energy": 42}', '2026-11-16 19:00:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 16, "calmness": -5}', '{"satiety": 80, "hydration": 67, "cleanliness": 100, "bond": 100, "calmness": 78, "health": 90, "energy": 42}', '2026-11-16 19:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 20}', '{"satiety": 100, "hydration": 67, "cleanliness": 100, "bond": 100, "calmness": 78, "health": 90, "energy": 42}', '2026-11-16 19:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 100, "calmness": 78, "health": 90, "energy": 42}', '2026-11-16 19:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"calmness": 2}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 100, "calmness": 80, "health": 90, "energy": 42}', '2026-11-16 19:40:00+09'),
        (v_session, 'SYSTEM', 'SESSION_END', NULL, NULL, NULL, NULL, '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 99, "calmness": 77, "health": 90, "energy": 39}', '2026-11-16 20:00:00+09');

    -- 지출 17건(balance_after는 누적)
    INSERT INTO simulation_transactions (session_seq, transaction_type, item_seq, occurrence_seq, quantity, amount, balance_after, description, reg_dttm) VALUES
        (v_session, 'PURCHASE', 1, NULL, 1, 10000, 140000, '밥그릇 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 2, NULL, 1, 10000, 130000, '물그릇 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 4, NULL, 1, 15000, 115000, '강아지 배변판 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 9, NULL, 1, 14900, 100100, '하네스와 리드줄 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 10, NULL, 1, 13800, 86300, '위생 관리 키트 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 17, NULL, 1, 2700, 83600, '매듭 터그 로프 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 1, 10600, 73000, '균형 잡힌 사료 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 30, NULL, 1, 2600, 70400, '배변패드 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 2, 4000, 66400, '씹는 간식 × 2', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 32, NULL, 1, 500, 65900, '배변봉투 × 1', '2026-11-09 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 2, 4000, 61900, '씹는 간식 × 2', '2026-11-10 20:20:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 1, 10600, 51300, '균형 잡힌 사료 × 1', '2026-11-11 20:05:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 2, 4000, 47300, '씹는 간식 × 2', '2026-11-13 07:10:00+09'),
        (v_session, 'PURCHASE', 30, NULL, 1, 2600, 44700, '배변패드 × 1', '2026-11-13 07:10:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 1, 10600, 34100, '균형 잡힌 사료 × 1', '2026-11-13 20:05:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 2, 4000, 30100, '씹는 간식 × 2', '2026-11-13 20:05:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 2, 4000, 26100, '씹는 간식 × 2', '2026-11-14 20:30:00+09');

    -- 보유 물품(묶음 물품은 구매 수 × simulation.shop.uses-per-purchase에서 쓴 만큼 뺀 값)
    INSERT INTO simulation_inventory_items (session_seq, item_seq, quantity, reg_dttm, mod_dttm) VALUES
        (v_session, 1, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 2, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 4, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 9, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 10, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 17, 1, '2026-11-09 20:00:00+09', NULL),
        (v_session, 22, 6, '2026-11-09 20:00:00+09', '2026-11-16 19:20:00+09'),
        (v_session, 30, 4, '2026-11-09 20:00:00+09', '2026-11-16 17:50:00+09'),
        (v_session, 26, 13, '2026-11-09 20:00:00+09', '2026-11-16 17:10:00+09'),
        (v_session, 32, 8, '2026-11-09 20:00:00+09', '2026-11-15 09:31:00+09');

    -- 종료 리포트(summary NULL → 요약 복구 스케줄러가 채움)
    INSERT INTO simulation_reports (session_seq, expected_cost, total_spent, spent_by_category_jsonb, daily_spent_jsonb, final_stats_jsonb, action_count, event_count, event_resolved_count, walk_count, summary, reg_dttm)
    VALUES (v_session, 150000, 123900, '{"FOOD_BOWL": 10000, "WATER_BOWL": 10000, "LITTER_BOX": 15000, "TOY": 2700, "FURNITURE": 0, "LEASH": 14900, "GROOMING_KIT": 13800, "FOOD": 31800, "SNACK": 20000, "LITTER": 5200, "SHAMPOO": 0, "MEDICINE": 0, "POOP_BAG": 500, "CATNIP": 0, "EVENT": 0}', '{"1": 84100, "2": 4000, "3": 10600, "4": 6600, "5": 14600, "6": 4000, "7": 0}', '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 99, "calmness": 77, "health": 90, "energy": 39}', 165, 8, 8, 16, NULL, '2026-11-16 20:00:00+09');
    END LOOP;
END
$dev$;
