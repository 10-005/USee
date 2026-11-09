-- 산책 중 배변 치우기(S15P31D106-252, 사용자 결정 2026-11-09)
-- 산책 행동 CLEAN_UP_POOP(배변봉투 1개)을 새로 두고, 그 행동이 정답인 산책 시나리오 16번을 넣는다
-- WALK 시나리오의 정답 행동 CHECK에 CLEAN_UP_POOP을 더한다(HOME 쪽은 그대로)
ALTER TABLE simulation_event_scenarios DROP CONSTRAINT ck_sim_scenario_resolve;
ALTER TABLE simulation_event_scenarios ADD CONSTRAINT ck_sim_scenario_resolve CHECK (
    (trigger_context = 'HOME' AND resolve_action_code IN
        ('FEED', 'WATER', 'CLEAN_LITTER', 'BATHE', 'PLAY', 'SNACK', 'MEDICINE', 'VET_VISIT'))
    OR (trigger_context = 'WALK' AND resolve_action_code IN ('GIVE_WATER', 'GIVE_SNACK', 'CLEAN_UP_POOP'))
);

-- 한 행에 시나리오 하나(EventScenarioSeedTest가 행 단위로 읽어 검사한다)
INSERT INTO simulation_event_scenarios (scenario_seq, title, description, trigger_context, trigger_type, trigger_condition_jsonb, cooldown_hours, trigger_probability, species_scope, personality_scope, weight, resolve_action_code, resolve_cost, resolve_effects_jsonb, result_text, neglect_effects_jsonb, neglect_text) VALUES
(16, '길에서 똥을 쌌어요', '산책하다 길 한가운데서 볼일을 봤어요. 그냥 두고 갈 수는 없죠.', 'WALK', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'CLEAN_UP_POOP', 0, '{"bond": 2}', '봉투로 깨끗이 치웠어요. 매너 있는 산책이에요.', '{"calmness": -5, "bond": -3}', '배변봉투로 치웠어야 해요. 주변 눈총에 덩달아 주눅 들었어요.');

SELECT setval(pg_get_serial_sequence('simulation_event_scenarios', 'scenario_seq'), (SELECT MAX(scenario_seq) FROM simulation_event_scenarios));
