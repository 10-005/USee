-- 돌발 이벤트 시나리오 초기 데이터 15개(S15P31D106-255, Q412·Q486). 관리 API가 없어 시드로만 넣는다
-- 사용자 결정 2026-11-09: 초안 15개 채택. 해결 효과도 활동한 만큼 대가가 있을 수 있다(예: 놀아 주면 안정감 +, 포만감·활력 -)
-- 효과·확률·쿨다운·비용은 모두 가안. 해결·방치 효과에는 성격 배율을 곱하지 않는다(Q490)
-- description은 정답을 직접 말하지 않는 힌트, neglect_text는 만료 후 정답을 알려 주는 문구
-- 발생 처리: HOME·RANDOM은 256, HOME·CONDITION은 259, WALK는 252(산책), VET_VISIT 진료비는 254
-- scenario_seq는 직접 지정한다. 한 행에 시나리오 하나(EventScenarioSeedTest가 행 단위로 읽어 검사한다)
INSERT INTO simulation_event_scenarios (scenario_seq, title, description, trigger_context, trigger_type, trigger_condition_jsonb, cooldown_hours, trigger_probability, species_scope, personality_scope, weight, resolve_action_code, resolve_cost, resolve_effects_jsonb, result_text, neglect_effects_jsonb, neglect_text) VALUES
-- HOME · RANDOM (가중치 추첨)
(1, '물그릇을 엎었어요', '물그릇이 뒤집혀 바닥이 흥건해요. 목이 마를 텐데 어떡하죠?', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'ALL', NULL, 2, 'WATER', 0, '{"hydration": 10, "calmness": 5}', '새 물을 받아 주니 꿀꺽꿀꺽 시원하게 마셨어요.', '{"hydration": -20, "calmness": -5}', '물을 다시 채워 줬어야 해요. 한참 목이 말랐어요.'),
(2, '흙투성이가 됐어요', '어디서 굴렀는지 털이 흙투성이가 됐어요. 몸에서 냄새도 나요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'ALL', NULL, 1, 'BATHE', 0, '{"cleanliness": 10, "bond": 3, "calmness": -5}', '깨끗하게 씻겨 주었어요. 물이 싫었는지 조금 예민해졌지만 뽀송해졌어요.', '{"cleanliness": -20, "health": -5}', '씻겨 줬어야 해요. 더러운 채로 지내 피부가 가려워졌어요.'),
(3, '화장실 밖에 실수했어요', '화장실이 마음에 안 들었는지 다른 곳에 실수를 했어요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'ALL', NULL, 2, 'CLEAN_LITTER', 0, '{"cleanliness": 10, "calmness": 5}', '화장실을 깨끗하게 치워 주니 다시 제자리에서 볼일을 봐요.', '{"cleanliness": -20, "calmness": -10}', '화장실을 치워 줬어야 해요. 더러운 화장실을 피해 계속 실수했어요.'),
(4, '천둥소리에 숨었어요', '밖에서 천둥이 쳐서 침대 밑으로 숨어 나오지 않아요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'ALL', NULL, 1, 'PLAY', 0, '{"calmness": 15, "bond": 3, "energy": -5}', '곁에서 함께 놀아 주니 긴장이 풀려 밖으로 나왔어요.', '{"calmness": -20}', '곁에서 놀아 주며 안심시켜 줬어야 해요. 오래 불안에 떨었어요.'),
(5, '쿠션을 물어뜯어요', '혼자 심심했는지 쿠션을 물어뜯고 있어요. 솜이 여기저기 날려요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'PLAY', 0, '{"bond": 5, "calmness": 10, "satiety": -5, "energy": -10}', '신나게 놀고 나니 쿠션에는 관심이 없어졌어요. 배가 좀 고파 보여요.', '{"calmness": -15, "bond": -5}', '같이 놀아 줬어야 해요. 심심함이 쌓여 더 예민해졌어요.'),
(6, '앉아! 하고 쳐다봐요', '배운 대로 앉더니 꼬리를 흔들며 빤히 쳐다봐요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'SNACK', 0, '{"bond": 10}', '잘했다고 간식으로 칭찬해 주니 더 신나서 꼬리를 흔들어요.', '{"bond": -5, "calmness": -5}', '간식으로 칭찬해 줬어야 해요. 기다리다 시무룩해졌어요.'),
(7, '헤어볼을 토했어요', '캑캑거리더니 털 뭉치를 토해 냈어요. 털이 많이 빠지는 시기예요.', 'HOME', 'RANDOM', NULL, NULL, 1.00, 'CAT', NULL, 1, 'BATHE', 0, '{"cleanliness": 5, "health": 5, "calmness": -5}', '빗질로 죽은 털을 정리해 주었어요. 조금 귀찮아했지만 한결 편해 보여요.', '{"health": -10}', '빗질로 털을 정리해 줬어야 해요. 헤어볼이 또 생겼어요.'),
-- HOME · CONDITION (스탯 조건을 확률로 판정, 259에서 발생)
(8, '축 처져 있어요', '밥을 잘 안 먹고 구석에서 움직이지 않아요. 평소와 달라 걱정돼요.', 'HOME', 'CONDITION', '{"stat": "health", "op": "<", "value": 40}', 24, 0.50, 'ALL', NULL, 1, 'VET_VISIT', 30000, '{"health": 30, "calmness": -10}', '진단서: 가벼운 장염 소견. 처방식과 휴식을 권장합니다. 병원에 다녀와 조금 지쳤어요.', '{"health": -15}', '병원에 데려갔어야 해요. 건강이 더 나빠졌어요.'),
(9, '배고파서 보채요', '밥그릇 앞을 서성이며 계속 보채요.', 'HOME', 'CONDITION', '{"stat": "satiety", "op": "<", "value": 20}', 12, 0.60, 'ALL', NULL, 1, 'FEED', 0, '{"bond": 3, "calmness": 5}', '밥을 주니 허겁지겁 먹고 편안해졌어요.', '{"health": -5, "calmness": -10}', '밥을 줬어야 해요. 배고픔에 지쳐 기운이 없어졌어요.'),
(10, '몸을 자꾸 긁어요', '몸 여기저기를 자꾸 긁고 털이 푸석해 보여요.', 'HOME', 'CONDITION', '{"stat": "cleanliness", "op": "<", "value": 25}', 24, 0.50, 'ALL', NULL, 1, 'BATHE', 0, '{"health": 5, "calmness": 5}', '깨끗하게 씻기니 가려움이 가라앉았어요.', '{"health": -10}', '씻겨 줬어야 해요. 긁은 자리에 상처가 생겼어요.'),
(11, '가볍게 기침해요', '가끔 콜록콜록 기침을 해요. 열은 없어 보여요.', 'HOME', 'CONDITION', '{"stat": "health", "op": "<", "value": 60}', 24, 0.40, 'ALL', NULL, 1, 'MEDICINE', 0, '{"health": 10, "bond": -2}', '약을 먹이니 기침이 잦아들었어요. 쓴 약이 싫었는지 살짝 토라졌어요.', '{"health": -10}', '상비약을 먹였어야 해요. 기침이 심해졌어요.'),
(12, '불안해서 낑낑대요', '안절부절못하며 낑낑대고 같은 자리를 맴돌아요.', 'HOME', 'CONDITION', '{"stat": "calmness", "op": "<", "value": 30}', 12, 0.50, 'ALL', NULL, 1, 'PLAY', 0, '{"calmness": 15, "bond": 3, "energy": -5}', '함께 놀아 주니 마음이 놓였는지 얌전해졌어요.', '{"calmness": -10, "health": -3}', '놀아 주며 달래 줬어야 해요. 불안이 몸에도 영향을 줬어요.'),
-- WALK (개 산책 중, 252에서 발생)
(13, '헥헥거려요', '한참 뛰었더니 혀를 길게 내밀고 헥헥거려요.', 'WALK', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'GIVE_WATER', 0, '{"hydration": 10, "energy": 5}', '물을 마시고 다시 기운을 차렸어요.', '{"hydration": -15, "health": -3}', '물을 줬어야 해요. 더위에 지쳤어요.'),
(14, '다른 개를 보고 짖어요', '건너편 개를 보더니 몸을 낮추고 짖기 시작했어요.', 'WALK', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'GIVE_SNACK', 0, '{"calmness": 10, "bond": 3, "satiety": 3}', '간식으로 주의를 돌리니 금세 조용해졌어요.', '{"calmness": -15}', '간식으로 주의를 돌려 줬어야 해요. 흥분해서 한참 짖었어요.'),
(15, '길에 앉아 버텨요', '갑자기 길 한가운데 앉아 꿈쩍도 하지 않아요.', 'WALK', 'RANDOM', NULL, NULL, 1.00, 'DOG', NULL, 1, 'GIVE_SNACK', 0, '{"bond": 5, "energy": 5, "satiety": 3}', '간식으로 달래니 다시 신나게 걸어요.', '{"calmness": -5, "energy": -10}', '간식으로 달래 줬어야 해요. 지쳐서 산책이 힘들어졌어요.');

-- 직접 지정한 번호 다음부터 IDENTITY가 이어지도록 맞춘다
SELECT setval(pg_get_serial_sequence('simulation_event_scenarios', 'scenario_seq'), (SELECT MAX(scenario_seq) FROM simulation_event_scenarios));
