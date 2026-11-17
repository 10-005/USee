-- dev 회원 1번의 두 번째 모의 입양 완료 세션(리포트 위키 Q521 확인용). local·dev에서만 적용된다
-- 생성기가 만든 파일이다. 손으로 고치지 않는다: SEED_GEN=true ./gradlew :simulation:test --tests '*BoriSeedGeneratorTest*' --rerun
-- (생성기 BoriSeed가 실제 서비스를 고정 시계로 7일 돌린 결과. 스탯·지출·리포트 값은 그 결과 그대로)
-- 이야기: 개 말티즈 "보리" 7일 완료. 해결 #6·#16(1일차)·#14·#4(3일차)·#5(6일차), 놓침 #1(2일차)·#3(4일차 바쁜 날)·#15(5일차)·#16(6일차)
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
    IF EXISTS (SELECT 1 FROM simulation_sessions WHERE user_seq = me AND started_dttm = '2026-10-27 20:00:00+09') THEN
        RAISE NOTICE '모의 입양 시드가 이미 있어 건너뜀';
        RETURN;
    END IF;

    INSERT INTO simulation_sessions (user_seq, breed_seq, animal_name, species_type, personality, status, expected_cost, balance,
                                     started_dttm, ends_dttm, ended_dttm, satiety, hydration, cleanliness, bond, calmness, health, energy,
                                     stats_calculated_dttm, last_action_dttm, reg_dttm, mod_dttm)
    VALUES (me, v_breed, '보리', 'DOG', v_personality, 'COMPLETED', 110000, -5900,
            '2026-10-27 20:00:00+09', '2026-11-03 20:00:00+09', '2026-11-03 20:00:00+09', 96, 95, 98, 99, 44, 66, 39,
            '2026-11-03 20:00:00+09', '2026-11-03 19:40:00+09', '2026-10-27 20:00:00+09', '2026-11-03 20:00:00+09')
    RETURNING session_seq INTO v_session;

    -- 산책 15회
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-27 21:00:00+09', '2026-10-27 21:30:00+09', '2026-10-27 21:30:00+09', '2026-10-27 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-28 09:00:00+09', '2026-10-28 10:00:00+09', '2026-10-28 10:00:00+09', '2026-10-28 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-28 21:00:00+09', '2026-10-28 21:30:00+09', '2026-10-28 21:30:00+09', '2026-10-28 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-29 09:00:00+09', '2026-10-29 09:30:00+09', '2026-10-29 09:30:00+09', '2026-10-29 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-29 18:30:00+09', '2026-10-29 19:30:00+09', '2026-10-29 19:30:00+09', '2026-10-29 18:30:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-29 21:00:00+09', '2026-10-29 21:30:00+09', '2026-10-29 21:30:00+09', '2026-10-29 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-30 09:00:00+09', '2026-10-30 10:00:00+09', '2026-10-30 10:00:00+09', '2026-10-30 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-30 18:30:00+09', '2026-10-30 19:00:00+09', '2026-10-30 19:00:00+09', '2026-10-30 18:30:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-31 15:00:00+09', '2026-10-31 15:30:00+09', '2026-10-31 15:30:00+09', '2026-10-31 15:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-10-31 21:00:00+09', '2026-10-31 22:00:00+09', '2026-10-31 22:00:00+09', '2026-10-31 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-01 09:00:00+09', '2026-11-01 10:00:00+09', '2026-11-01 10:00:00+09', '2026-11-01 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-01 21:00:00+09', '2026-11-01 21:30:00+09', '2026-11-01 21:30:00+09', '2026-11-01 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-02 09:00:00+09', '2026-11-02 10:00:00+09', '2026-11-02 10:00:00+09', '2026-11-02 09:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-02 21:00:00+09', '2026-11-02 21:20:00+09', '2026-11-02 21:20:00+09', '2026-11-02 21:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;
    INSERT INTO simulation_walks (session_seq, status, started_dttm, last_heartbeat_dttm, ended_dttm, reg_dttm) VALUES (v_session, 'ENDED', '2026-11-03 18:00:00+09', '2026-11-03 19:00:00+09', '2026-11-03 19:00:00+09', '2026-11-03 18:00:00+09') RETURNING walk_seq INTO v_id; w := w || v_id;

    -- 행동 로그 157건과 돌발 이벤트 9건(시간 순)
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'SYSTEM', 'SESSION_START', NULL, NULL, NULL, NULL, '{"satiety": 70, "hydration": 70, "cleanliness": 80, "bond": 30, "calmness": 50, "health": 90, "energy": 60}', '2026-10-27 20:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 100, "hydration": 70, "cleanliness": 80, "bond": 31, "calmness": 50, "health": 90, "energy": 60}', '2026-10-27 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[1], NULL, NULL, '{"satiety": 96, "hydration": 65, "cleanliness": 78, "bond": 30, "calmness": 48, "health": 90, "energy": 57}', '2026-10-27 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[1], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 90, "hydration": 56, "cleanliness": 72, "bond": 33, "calmness": 60, "health": 90, "energy": 78}', '2026-10-27 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 44}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 33, "calmness": 60, "health": 90, "energy": 78}', '2026-10-27 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 90, "hydration": 100, "cleanliness": 72, "bond": 38, "calmness": 62, "health": 90, "energy": 78}', '2026-10-27 21:50:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 86, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 59, "health": 90, "energy": 75}', '2026-10-27 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 91, "hydration": 95, "cleanliness": 100, "bond": 37, "calmness": 67, "health": 90, "energy": 75}', '2026-10-27 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 55, "hydration": 100, "cleanliness": 82, "bond": 28, "calmness": 44, "health": 90, "energy": 48}', '2026-10-28 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 85, "hydration": 100, "cleanliness": 82, "bond": 29, "calmness": 44, "health": 90, "energy": 48}', '2026-10-28 07:40:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[2], NULL, NULL, '{"satiety": 77, "hydration": 90, "cleanliness": 78, "bond": 27, "calmness": 39, "health": 90, "energy": 42}', '2026-10-28 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 16, w[2], 'PENDING', '2026-10-28 09:15:00+09', '2026-10-28 09:20:00+09', '2026-10-28 09:15:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'CLEAN_UP_POOP', 32, w[2], o[1], '{"bond": 2}', '{"satiety": 77, "hydration": 90, "cleanliness": 78, "bond": 29, "calmness": 39, "health": 90, "energy": 42}', '2026-10-28 09:16:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-10-28 09:16:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[1];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[2], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 61, "hydration": 67, "cleanliness": 64, "bond": 34, "calmness": 60, "health": 90, "energy": 81}', '2026-10-28 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 61, "hydration": 100, "cleanliness": 64, "bond": 34, "calmness": 60, "health": 90, "energy": 81}', '2026-10-28 10:10:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 6, NULL, 'PENDING', '2026-10-28 11:00:00+09', '2026-10-28 17:00:00+09', '2026-10-28 11:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'SNACK', 26, NULL, o[2], '{"satiety": 5, "bond": 10, "calmness": 8}', '{"satiety": 62, "hydration": 95, "cleanliness": 62, "bond": 43, "calmness": 65, "health": 90, "energy": 78}', '2026-10-28 11:05:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-10-28 11:05:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[2];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 84, "hydration": 85, "cleanliness": 58, "bond": 42, "calmness": 60, "health": 90, "energy": 72}', '2026-10-28 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 84, "hydration": 85, "cleanliness": 58, "bond": 47, "calmness": 62, "health": 90, "energy": 72}', '2026-10-28 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 72, "hydration": 100, "cleanliness": 52, "bond": 44, "calmness": 54, "health": 90, "energy": 63}', '2026-10-28 16:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 68, "hydration": 95, "cleanliness": 50, "bond": 48, "calmness": 54, "health": 90, "energy": 60}', '2026-10-28 17:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 64, "hydration": 90, "cleanliness": 78, "bond": 47, "calmness": 51, "health": 90, "energy": 57}', '2026-10-28 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 90, "hydration": 85, "cleanliness": 76, "bond": 47, "calmness": 49, "health": 90, "energy": 54}', '2026-10-28 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 86, "hydration": 80, "cleanliness": 74, "bond": 51, "calmness": 48, "health": 90, "energy": 51}', '2026-10-28 20:30:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[3], NULL, NULL, '{"satiety": 82, "hydration": 75, "cleanliness": 72, "bond": 50, "calmness": 45, "health": 90, "energy": 48}', '2026-10-28 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[3], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 76, "hydration": 66, "cleanliness": 66, "bond": 53, "calmness": 57, "health": 90, "energy": 69}', '2026-10-28 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 34}', '{"satiety": 76, "hydration": 100, "cleanliness": 66, "bond": 53, "calmness": 57, "health": 90, "energy": 69}', '2026-10-28 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 76, "hydration": 100, "cleanliness": 66, "bond": 58, "calmness": 59, "health": 90, "energy": 69}', '2026-10-28 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 77, "hydration": 95, "cleanliness": 64, "bond": 57, "calmness": 65, "health": 90, "energy": 66}', '2026-10-28 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 41, "hydration": 100, "cleanliness": 46, "bond": 48, "calmness": 41, "health": 90, "energy": 39}', '2026-10-29 07:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 71, "hydration": 100, "cleanliness": 46, "bond": 49, "calmness": 41, "health": 90, "energy": 39}', '2026-10-29 07:30:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 67, "hydration": 95, "cleanliness": 74, "bond": 48, "calmness": 39, "health": 90, "energy": 36}', '2026-10-29 08:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 1, NULL, 'PENDING', '2026-10-29 08:10:00+09', '2026-10-29 14:10:00+09', '2026-10-29 08:10:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_START', NULL, w[4], NULL, NULL, '{"satiety": 63, "hydration": 90, "cleanliness": 72, "bond": 47, "calmness": 36, "health": 90, "energy": 33}', '2026-10-29 09:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[4], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 57, "hydration": 81, "cleanliness": 66, "bond": 50, "calmness": 48, "health": 90, "energy": 54}', '2026-10-29 09:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 49, "hydration": 71, "cleanliness": 62, "bond": 53, "calmness": 45, "health": 90, "energy": 48}', '2026-10-29 11:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 71, "hydration": 61, "cleanliness": 58, "bond": 52, "calmness": 40, "health": 90, "energy": 42}', '2026-10-29 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 71, "hydration": 61, "cleanliness": 58, "bond": 57, "calmness": 42, "health": 90, "energy": 42}', '2026-10-29 13:10:00+09'),
        (v_session, 'EVENT', 'EVENT_EXPIRE', NULL, NULL, o[3], '{"hydration": -20, "calmness": -5}', '{"satiety": 67, "hydration": 36, "cleanliness": 56, "bond": 56, "calmness": 34, "health": 90, "energy": 39}', '2026-10-29 14:10:00+09');
    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = o[3];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 67, "hydration": 86, "cleanliness": 56, "bond": 56, "calmness": 34, "health": 90, "energy": 39}', '2026-10-29 14:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 24}', '{"satiety": 59, "hydration": 100, "cleanliness": 52, "bond": 54, "calmness": 29, "health": 90, "energy": 33}', '2026-10-29 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 85, "hydration": 95, "cleanliness": 50, "bond": 54, "calmness": 26, "health": 90, "energy": 30}', '2026-10-29 17:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 85, "hydration": 95, "cleanliness": 50, "bond": 59, "calmness": 28, "health": 90, "energy": 30}', '2026-10-29 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 81, "hydration": 90, "cleanliness": 78, "bond": 58, "calmness": 26, "health": 90, "energy": 27}', '2026-10-29 18:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[5], NULL, NULL, '{"satiety": 81, "hydration": 90, "cleanliness": 78, "bond": 58, "calmness": 26, "health": 90, "energy": 27}', '2026-10-29 18:30:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[5], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 65, "hydration": 67, "cleanliness": 64, "bond": 63, "calmness": 47, "health": 90, "energy": 66}', '2026-10-29 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 70, "hydration": 67, "cleanliness": 64, "bond": 63, "calmness": 55, "health": 90, "energy": 66}', '2026-10-29 19:35:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 70, "hydration": 67, "cleanliness": 64, "bond": 68, "calmness": 57, "health": 90, "energy": 66}', '2026-10-29 19:40:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 75, "hydration": 67, "cleanliness": 64, "bond": 68, "calmness": 65, "health": 90, "energy": 66}', '2026-10-29 19:45:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 25, "bond": 1}', '{"satiety": 100, "hydration": 67, "cleanliness": 64, "bond": 69, "calmness": 65, "health": 90, "energy": 66}', '2026-10-29 19:50:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 67, "cleanliness": 64, "bond": 74, "calmness": 67, "health": 90, "energy": 66}', '2026-10-29 19:55:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 38}', '{"satiety": 96, "hydration": 100, "cleanliness": 62, "bond": 73, "calmness": 65, "health": 90, "energy": 63}', '2026-10-29 20:10:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[6], NULL, NULL, '{"satiety": 92, "hydration": 95, "cleanliness": 60, "bond": 72, "calmness": 62, "health": 90, "energy": 60}', '2026-10-29 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[6], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 86, "hydration": 86, "cleanliness": 54, "bond": 75, "calmness": 74, "health": 90, "energy": 81}', '2026-10-29 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 14}', '{"satiety": 86, "hydration": 100, "cleanliness": 54, "bond": 75, "calmness": 74, "health": 90, "energy": 81}', '2026-10-29 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 86, "hydration": 100, "cleanliness": 54, "bond": 80, "calmness": 76, "health": 90, "energy": 81}', '2026-10-29 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 87, "hydration": 95, "cleanliness": 52, "bond": 79, "calmness": 81, "health": 90, "energy": 78}', '2026-10-29 22:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 51, "hydration": 100, "cleanliness": 34, "bond": 70, "calmness": 58, "health": 90, "energy": 51}', '2026-10-30 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 81, "hydration": 100, "cleanliness": 34, "bond": 71, "calmness": 58, "health": 90, "energy": 51}', '2026-10-30 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 77, "hydration": 95, "cleanliness": 62, "bond": 70, "calmness": 55, "health": 90, "energy": 48}', '2026-10-30 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[7], NULL, NULL, '{"satiety": 73, "hydration": 90, "cleanliness": 60, "bond": 69, "calmness": 53, "health": 90, "energy": 45}', '2026-10-30 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 14, w[7], 'PENDING', '2026-10-30 09:20:00+09', '2026-10-30 09:25:00+09', '2026-10-30 09:20:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'WALK', 'GIVE_SNACK', 26, w[7], o[4], '{"satiety": 8, "bond": 3, "calmness": 18}', '{"satiety": 81, "hydration": 90, "cleanliness": 60, "bond": 72, "calmness": 71, "health": 90, "energy": 45}', '2026-10-30 09:21:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-10-30 09:21:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[4];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[7], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 65, "hydration": 67, "cleanliness": 46, "bond": 77, "calmness": 92, "health": 90, "energy": 84}', '2026-10-30 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 65, "hydration": 100, "cleanliness": 46, "bond": 77, "calmness": 92, "health": 90, "energy": 84}', '2026-10-30 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 83, "hydration": 85, "cleanliness": 40, "bond": 75, "calmness": 84, "health": 90, "energy": 75}', '2026-10-30 13:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 4, NULL, 'PENDING', '2026-10-30 14:00:00+09', '2026-10-30 20:00:00+09', '2026-10-30 14:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'PLAY', 17, NULL, o[5], '{"bond": 8, "calmness": 17, "energy": -5}', '{"satiety": 79, "hydration": 80, "cleanliness": 38, "bond": 82, "calmness": 99, "health": 90, "energy": 67}', '2026-10-30 14:05:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-10-30 14:05:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[5];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 71, "hydration": 100, "cleanliness": 34, "bond": 80, "calmness": 94, "health": 90, "energy": 61}', '2026-10-30 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 97, "hydration": 95, "cleanliness": 32, "bond": 80, "calmness": 91, "health": 90, "energy": 58}', '2026-10-30 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 3, "calmness": 8}', '{"satiety": 100, "hydration": 95, "cleanliness": 32, "bond": 80, "calmness": 99, "health": 90, "energy": 58}', '2026-10-30 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 60, "bond": 79, "calmness": 96, "health": 90, "energy": 55}', '2026-10-30 18:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[8], NULL, NULL, '{"satiety": 96, "hydration": 90, "cleanliness": 60, "bond": 79, "calmness": 96, "health": 90, "energy": 55}', '2026-10-30 18:30:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[8], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 6, "energy": 21}', '{"satiety": 86, "hydration": 76, "cleanliness": 52, "bond": 81, "calmness": 100, "health": 90, "energy": 73}', '2026-10-30 19:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5}', '{"satiety": 86, "hydration": 76, "cleanliness": 52, "bond": 86, "calmness": 100, "health": 90, "energy": 73}', '2026-10-30 19:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 14, "bond": 1}', '{"satiety": 100, "hydration": 76, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-10-30 19:40:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 24}', '{"satiety": 100, "hydration": 100, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-10-30 19:45:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{}', '{"satiety": 100, "hydration": 100, "cleanliness": 52, "bond": 87, "calmness": 100, "health": 90, "energy": 73}', '2026-10-30 19:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 52, "hydration": 90, "cleanliness": 28, "bond": 75, "calmness": 69, "health": 90, "energy": 37}', '2026-10-31 07:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 82, "hydration": 90, "cleanliness": 28, "bond": 76, "calmness": 69, "health": 90, "energy": 37}', '2026-10-31 07:30:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 3, NULL, 'PENDING', '2026-10-31 08:30:00+09', '2026-10-31 14:30:00+09', '2026-10-31 08:30:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 88, "hydration": 60, "cleanliness": 16, "bond": 71, "calmness": 53, "health": 84, "energy": 19}', '2026-10-31 13:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 40}', '{"satiety": 88, "hydration": 100, "cleanliness": 16, "bond": 71, "calmness": 53, "health": 84, "energy": 19}', '2026-10-31 13:10:00+09'),
        (v_session, 'EVENT', 'EVENT_EXPIRE', NULL, NULL, o[6], '{"cleanliness": -14, "calmness": -10}', '{"satiety": 84, "hydration": 95, "cleanliness": 0, "bond": 70, "calmness": 40, "health": 81, "energy": 16}', '2026-10-31 14:30:00+09');
    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = o[6];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_START', NULL, w[9], NULL, NULL, '{"satiety": 80, "hydration": 90, "cleanliness": 0, "bond": 69, "calmness": 38, "health": 78, "energy": 13}', '2026-10-31 15:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[9], NULL, '{"satiety": -6, "hydration": -9, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 74, "hydration": 81, "cleanliness": 0, "bond": 72, "calmness": 50, "health": 78, "energy": 34}', '2026-10-31 15:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 24}', '{"satiety": 70, "hydration": 100, "cleanliness": 0, "bond": 71, "calmness": 47, "health": 75, "energy": 31}', '2026-10-31 16:00:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 58, "hydration": 85, "cleanliness": 30, "bond": 68, "calmness": 39, "health": 66, "energy": 22}', '2026-10-31 19:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 58, "hydration": 85, "cleanliness": 30, "bond": 73, "calmness": 41, "health": 66, "energy": 22}', '2026-10-31 19:30:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 54, "hydration": 80, "cleanliness": 58, "bond": 72, "calmness": 39, "health": 66, "energy": 19}', '2026-10-31 20:06:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 84, "hydration": 80, "cleanliness": 58, "bond": 73, "calmness": 39, "health": 66, "energy": 19}', '2026-10-31 20:07:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 20}', '{"satiety": 84, "hydration": 100, "cleanliness": 58, "bond": 73, "calmness": 39, "health": 66, "energy": 19}', '2026-10-31 20:10:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 89, "hydration": 100, "cleanliness": 58, "bond": 73, "calmness": 47, "health": 66, "energy": 19}', '2026-10-31 20:15:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 89, "hydration": 100, "cleanliness": 58, "bond": 78, "calmness": 49, "health": 66, "energy": 19}', '2026-10-31 20:20:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[10], NULL, NULL, '{"satiety": 85, "hydration": 95, "cleanliness": 56, "bond": 77, "calmness": 46, "health": 66, "energy": 16}', '2026-10-31 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[10], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 69, "hydration": 72, "cleanliness": 42, "bond": 82, "calmness": 68, "health": 66, "energy": 55}', '2026-10-31 22:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 28}', '{"satiety": 69, "hydration": 100, "cleanliness": 42, "bond": 82, "calmness": 68, "health": 66, "energy": 55}', '2026-10-31 22:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 99, "hydration": 100, "cleanliness": 42, "bond": 83, "calmness": 68, "health": 66, "energy": 55}', '2026-10-31 22:20:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 40, "calmness": -5}', '{"satiety": 99, "hydration": 100, "cleanliness": 82, "bond": 83, "calmness": 63, "health": 66, "energy": 55}', '2026-10-31 22:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 1, "calmness": 8}', '{"satiety": 100, "hydration": 100, "cleanliness": 82, "bond": 83, "calmness": 71, "health": 66, "energy": 55}', '2026-10-31 22:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 18}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 83, "calmness": 71, "health": 66, "energy": 55}', '2026-10-31 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 45}', '{"satiety": 64, "hydration": 100, "cleanliness": 82, "bond": 74, "calmness": 47, "health": 66, "energy": 28}', '2026-11-01 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 82, "bond": 75, "calmness": 47, "health": 66, "energy": 28}', '2026-11-01 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 18}', '{"satiety": 94, "hydration": 100, "cleanliness": 100, "bond": 75, "calmness": 47, "health": 66, "energy": 28}', '2026-11-01 07:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[11], NULL, NULL, '{"satiety": 86, "hydration": 90, "cleanliness": 96, "bond": 73, "calmness": 42, "health": 66, "energy": 22}', '2026-11-01 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 15, w[11], 'PENDING', '2026-11-01 09:20:00+09', '2026-11-01 09:25:00+09', '2026-11-01 09:20:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'EVENT', 'EVENT_EXPIRE', NULL, w[11], o[7], '{"calmness": -5, "energy": -10}', '{"satiety": 86, "hydration": 90, "cleanliness": 96, "bond": 73, "calmness": 37, "health": 66, "energy": 12}', '2026-11-01 09:25:00+09');
    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = o[7];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[11], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 70, "hydration": 67, "cleanliness": 82, "bond": 78, "calmness": 58, "health": 66, "energy": 51}', '2026-11-01 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 70, "hydration": 100, "cleanliness": 82, "bond": 78, "calmness": 58, "health": 66, "energy": 51}', '2026-11-01 10:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 88, "hydration": 85, "cleanliness": 76, "bond": 76, "calmness": 51, "health": 66, "energy": 42}', '2026-11-01 13:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 88, "hydration": 85, "cleanliness": 76, "bond": 81, "calmness": 53, "health": 66, "energy": 42}', '2026-11-01 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 76, "hydration": 100, "cleanliness": 70, "bond": 78, "calmness": 45, "health": 66, "energy": 33}', '2026-11-01 16:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 77, "hydration": 95, "cleanliness": 68, "bond": 77, "calmness": 50, "health": 66, "energy": 30}', '2026-11-01 17:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 23, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 68, "bond": 78, "calmness": 50, "health": 66, "energy": 30}', '2026-11-01 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 96, "bond": 77, "calmness": 48, "health": 66, "energy": 27}', '2026-11-01 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 8, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 94, "bond": 77, "calmness": 45, "health": 66, "energy": 24}', '2026-11-01 19:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 100, "hydration": 85, "cleanliness": 94, "bond": 82, "calmness": 47, "health": 66, "energy": 24}', '2026-11-01 19:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[12], NULL, NULL, '{"satiety": 92, "hydration": 75, "cleanliness": 90, "bond": 80, "calmness": 42, "health": 66, "energy": 18}', '2026-11-01 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[12], NULL, '{"satiety": -6, "hydration": -9, "cleanliness": -6, "bond": 3, "calmness": 12, "energy": 21}', '{"satiety": 86, "hydration": 66, "cleanliness": 84, "bond": 83, "calmness": 54, "health": 66, "energy": 39}', '2026-11-01 21:30:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 34}', '{"satiety": 86, "hydration": 100, "cleanliness": 84, "bond": 83, "calmness": 54, "health": 66, "energy": 39}', '2026-11-01 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 86, "hydration": 100, "cleanliness": 84, "bond": 88, "calmness": 56, "health": 66, "energy": 39}', '2026-11-01 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 87, "hydration": 95, "cleanliness": 82, "bond": 87, "calmness": 61, "health": 66, "energy": 36}', '2026-11-01 22:40:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 13, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 82, "bond": 88, "calmness": 61, "health": 66, "energy": 36}', '2026-11-01 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 64, "hydration": 100, "cleanliness": 64, "bond": 79, "calmness": 38, "health": 66, "energy": 9}', '2026-11-02 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 64, "bond": 80, "calmness": 38, "health": 66, "energy": 9}', '2026-11-02 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 90, "hydration": 95, "cleanliness": 92, "bond": 79, "calmness": 35, "health": 66, "energy": 6}', '2026-11-02 08:00:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[13], NULL, NULL, '{"satiety": 86, "hydration": 90, "cleanliness": 90, "bond": 78, "calmness": 33, "health": 66, "energy": 3}', '2026-11-02 09:00:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 16, w[13], 'PENDING', '2026-11-02 09:30:00+09', '2026-11-02 09:35:00+09', '2026-11-02 09:30:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'EVENT', 'EVENT_EXPIRE', NULL, w[13], o[8], '{"bond": -3, "calmness": -5}', '{"satiety": 86, "hydration": 90, "cleanliness": 90, "bond": 75, "calmness": 28, "health": 66, "energy": 3}', '2026-11-02 09:35:00+09');
    UPDATE simulation_event_occurrences SET status = 'EXPIRED' WHERE occurrence_seq = o[8];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'WALK', 'WALK_END', NULL, w[13], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 70, "hydration": 67, "cleanliness": 76, "bond": 80, "calmness": 49, "health": 66, "energy": 42}', '2026-11-02 10:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 70, "hydration": 100, "cleanliness": 76, "bond": 80, "calmness": 49, "health": 66, "energy": 42}', '2026-11-02 10:10:00+09');
    INSERT INTO simulation_event_occurrences (session_seq, scenario_seq, walk_seq, status, occurred_dttm, expires_dttm, reg_dttm) VALUES (v_session, 5, NULL, 'PENDING', '2026-11-02 11:00:00+09', '2026-11-02 17:00:00+09', '2026-11-02 11:00:00+09') RETURNING occurrence_seq INTO v_id; o := o || v_id;
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm)
    VALUES (v_session, 'CARE', 'PLAY', 17, NULL, o[9], '{"satiety": -5, "bond": 10, "calmness": 12, "energy": -10}', '{"satiety": 61, "hydration": 95, "cleanliness": 74, "bond": 89, "calmness": 58, "health": 66, "energy": 29}', '2026-11-02 11:03:00+09') RETURNING action_log_seq INTO v_id;
    UPDATE simulation_event_occurrences SET status = 'RESOLVED', resolved_dttm = '2026-11-02 11:03:00+09', resolved_action_log_seq = v_id WHERE occurrence_seq = o[9];
    INSERT INTO simulation_action_logs (session_seq, action_type, action_code, item_seq, walk_seq, occurrence_seq, stat_delta_jsonb, stats_after_jsonb, reg_dttm) VALUES
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 83, "hydration": 85, "cleanliness": 70, "bond": 88, "calmness": 53, "health": 66, "energy": 23}', '2026-11-02 13:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 88, "hydration": 85, "cleanliness": 70, "bond": 88, "calmness": 61, "health": 66, "energy": 23}', '2026-11-02 13:10:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 30}', '{"satiety": 76, "hydration": 100, "cleanliness": 64, "bond": 85, "calmness": 53, "health": 66, "energy": 14}', '2026-11-02 16:00:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 72, "hydration": 95, "cleanliness": 62, "bond": 89, "calmness": 53, "health": 66, "energy": 11}', '2026-11-02 17:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 28, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 62, "bond": 90, "calmness": 53, "health": 66, "energy": 11}', '2026-11-02 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 96, "hydration": 90, "cleanliness": 90, "bond": 89, "calmness": 50, "health": 66, "energy": 8}', '2026-11-02 18:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 8, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 88, "bond": 89, "calmness": 48, "health": 66, "energy": 5}', '2026-11-02 19:30:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 8}', '{"satiety": 100, "hydration": 85, "cleanliness": 88, "bond": 89, "calmness": 56, "health": 66, "energy": 5}', '2026-11-02 19:40:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[14], NULL, NULL, '{"satiety": 92, "hydration": 75, "cleanliness": 84, "bond": 87, "calmness": 50, "health": 66, "energy": 0}', '2026-11-02 21:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[14], NULL, '{"satiety": -4, "hydration": -6, "cleanliness": -4, "bond": 2, "calmness": 8, "energy": 14}', '{"satiety": 88, "hydration": 69, "cleanliness": 80, "bond": 89, "calmness": 58, "health": 66, "energy": 14}', '2026-11-02 21:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 31}', '{"satiety": 88, "hydration": 100, "cleanliness": 80, "bond": 89, "calmness": 58, "health": 66, "energy": 14}', '2026-11-02 21:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 88, "hydration": 100, "cleanliness": 80, "bond": 94, "calmness": 60, "health": 66, "energy": 14}', '2026-11-02 21:50:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"satiety": 5, "calmness": 8}', '{"satiety": 89, "hydration": 95, "cleanliness": 78, "bond": 93, "calmness": 66, "health": 66, "energy": 11}', '2026-11-02 22:40:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 11, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 78, "bond": 94, "calmness": 66, "health": 66, "energy": 11}', '2026-11-02 22:50:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 50}', '{"satiety": 64, "hydration": 100, "cleanliness": 60, "bond": 85, "calmness": 42, "health": 66, "energy": 0}', '2026-11-03 07:30:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 30, "bond": 1}', '{"satiety": 94, "hydration": 100, "cleanliness": 60, "bond": 86, "calmness": 42, "health": 66, "energy": 0}', '2026-11-03 07:40:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 90, "hydration": 95, "cleanliness": 88, "bond": 85, "calmness": 40, "health": 66, "energy": 0}', '2026-11-03 08:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 10}', '{"satiety": 86, "hydration": 100, "cleanliness": 86, "bond": 84, "calmness": 37, "health": 66, "energy": 0}', '2026-11-03 09:40:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 82, "hydration": 95, "cleanliness": 84, "bond": 88, "calmness": 37, "health": 66, "energy": 0}', '2026-11-03 10:20:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 26, "bond": 1}', '{"satiety": 100, "hydration": 85, "cleanliness": 80, "bond": 87, "calmness": 31, "health": 66, "energy": 0}', '2026-11-03 12:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 96, "hydration": 80, "cleanliness": 78, "bond": 91, "calmness": 31, "health": 66, "energy": 0}', '2026-11-03 13:10:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 5, "calmness": 2}', '{"satiety": 88, "hydration": 70, "cleanliness": 74, "bond": 94, "calmness": 28, "health": 66, "energy": 0}', '2026-11-03 15:00:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 35}', '{"satiety": 84, "hydration": 100, "cleanliness": 72, "bond": 93, "calmness": 25, "health": 66, "energy": 0}', '2026-11-03 16:00:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 20, "bond": 1}', '{"satiety": 100, "hydration": 95, "cleanliness": 70, "bond": 93, "calmness": 22, "health": 66, "energy": 0}', '2026-11-03 17:00:00+09'),
        (v_session, 'CARE', 'SNACK', 26, NULL, NULL, '{"calmness": 8}', '{"satiety": 100, "hydration": 95, "cleanliness": 70, "bond": 93, "calmness": 30, "health": 66, "energy": 0}', '2026-11-03 17:10:00+09'),
        (v_session, 'CARE', 'CLEAN_LITTER', 30, NULL, NULL, '{"cleanliness": 30}', '{"satiety": 100, "hydration": 95, "cleanliness": 100, "bond": 93, "calmness": 30, "health": 66, "energy": 0}', '2026-11-03 17:50:00+09'),
        (v_session, 'WALK', 'WALK_START', NULL, w[15], NULL, NULL, '{"satiety": 96, "hydration": 90, "cleanliness": 98, "bond": 92, "calmness": 28, "health": 66, "energy": 0}', '2026-11-03 18:00:00+09'),
        (v_session, 'WALK', 'WALK_END', NULL, w[15], NULL, '{"satiety": -12, "hydration": -18, "cleanliness": -12, "bond": 6, "calmness": 24, "energy": 42}', '{"satiety": 80, "hydration": 67, "cleanliness": 84, "bond": 97, "calmness": 49, "health": 66, "energy": 42}', '2026-11-03 19:00:00+09'),
        (v_session, 'CARE', 'BATHE', NULL, NULL, NULL, '{"cleanliness": 16, "calmness": -5}', '{"satiety": 80, "hydration": 67, "cleanliness": 100, "bond": 97, "calmness": 44, "health": 66, "energy": 42}', '2026-11-03 19:10:00+09'),
        (v_session, 'CARE', 'FEED', 22, NULL, NULL, '{"satiety": 20, "bond": 1}', '{"satiety": 100, "hydration": 67, "cleanliness": 100, "bond": 98, "calmness": 44, "health": 66, "energy": 42}', '2026-11-03 19:20:00+09'),
        (v_session, 'CARE', 'WATER', NULL, NULL, NULL, '{"hydration": 33}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 98, "calmness": 44, "health": 66, "energy": 42}', '2026-11-03 19:30:00+09'),
        (v_session, 'CARE', 'PLAY', 17, NULL, NULL, '{"bond": 2, "calmness": 2}', '{"satiety": 100, "hydration": 100, "cleanliness": 100, "bond": 100, "calmness": 46, "health": 66, "energy": 42}', '2026-11-03 19:40:00+09'),
        (v_session, 'SYSTEM', 'SESSION_END', NULL, NULL, NULL, NULL, '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 99, "calmness": 44, "health": 66, "energy": 39}', '2026-11-03 20:00:00+09');

    -- 지출 12건(balance_after는 누적)
    INSERT INTO simulation_transactions (session_seq, transaction_type, item_seq, occurrence_seq, quantity, amount, balance_after, description, reg_dttm) VALUES
        (v_session, 'PURCHASE', 1, NULL, 1, 10000, 100000, '밥그릇 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 2, NULL, 1, 10000, 90000, '물그릇 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 4, NULL, 1, 15000, 75000, '강아지 배변판 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 9, NULL, 1, 14900, 60100, '하네스와 리드줄 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 10, NULL, 1, 13800, 46300, '위생 관리 키트 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 17, NULL, 1, 2700, 43600, '매듭 터그 로프 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 2, 21200, 22400, '균형 잡힌 사료 × 2', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 30, NULL, 2, 5200, 17200, '배변패드 × 2', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 3, 6000, 11200, '씹는 간식 × 3', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 32, NULL, 1, 500, 10700, '배변봉투 × 1', '2026-10-27 20:00:00+09'),
        (v_session, 'PURCHASE', 26, NULL, 3, 6000, 4700, '씹는 간식 × 3', '2026-10-31 20:05:00+09'),
        (v_session, 'PURCHASE', 22, NULL, 1, 10600, -5900, '균형 잡힌 사료 × 1', '2026-10-31 20:05:00+09');

    -- 보유 물품(묶음 물품은 구매 수 × simulation.shop.uses-per-purchase에서 쓴 만큼 뺀 값)
    INSERT INTO simulation_inventory_items (session_seq, item_seq, quantity, reg_dttm, mod_dttm) VALUES
        (v_session, 1, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 2, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 4, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 9, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 10, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 17, 1, '2026-10-27 20:00:00+09', NULL),
        (v_session, 22, 6, '2026-10-27 20:00:00+09', '2026-11-03 19:20:00+09'),
        (v_session, 30, 5, '2026-10-27 20:00:00+09', '2026-11-03 17:50:00+09'),
        (v_session, 26, 7, '2026-10-27 20:00:00+09', '2026-11-03 17:10:00+09'),
        (v_session, 32, 9, '2026-10-27 20:00:00+09', '2026-10-28 09:16:00+09');

    -- 종료 리포트(summary NULL → 요약 복구 스케줄러가 채움)
    INSERT INTO simulation_reports (session_seq, expected_cost, total_spent, spent_by_category_jsonb, daily_spent_jsonb, final_stats_jsonb, action_count, event_count, event_resolved_count, walk_count, summary, reg_dttm)
    VALUES (v_session, 110000, 115900, '{"FOOD_BOWL": 10000, "WATER_BOWL": 10000, "LITTER_BOX": 15000, "TOY": 2700, "FURNITURE": 0, "LEASH": 14900, "GROOMING_KIT": 13800, "FOOD": 31800, "SNACK": 12000, "LITTER": 5200, "SHAMPOO": 0, "MEDICINE": 0, "POOP_BAG": 500, "CATNIP": 0, "EVENT": 0}', '{"1": 99300, "2": 0, "3": 0, "4": 0, "5": 16600, "6": 0, "7": 0}', '{"satiety": 96, "hydration": 95, "cleanliness": 98, "bond": 99, "calmness": 44, "health": 66, "energy": 39}', 151, 9, 5, 15, NULL, '2026-11-03 20:00:00+09');
END
$dev$;
