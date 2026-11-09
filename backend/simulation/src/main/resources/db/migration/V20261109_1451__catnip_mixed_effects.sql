-- 캣닢 주기(GIVE_CATNIP) 혼합 효과(S15P31D106-246, 사용자 결정 2026-11-09)
-- 신나게 놀아 활력·친밀도는 오르지만 흥분해서 안정감이 조금 떨어진다. 남용(6시간 안에 또 줌)은 코드 설정값으로 처리
UPDATE simulation_shop_items
SET effects_jsonb = '{"energy": 15, "bond": 3, "calmness": -5}',
    description = '1개에 4회입니다. 신나게 놀지만 흥분해서 안정감이 조금 떨어져요. 6시간 안에 또 주면 지나치게 흥분해요.',
    mod_dttm = CURRENT_TIMESTAMP
WHERE item_seq = 33 AND category = 'CATNIP';
