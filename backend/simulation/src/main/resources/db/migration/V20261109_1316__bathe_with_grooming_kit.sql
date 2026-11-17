-- 목욕(BATHE)은 위생 관리 키트만 있으면 무제한으로 한다(S15P31D106-238, 사용자 결정 2026-11-09)
-- 샴푸는 참고 목록에 없던 가안 물품이라 판매를 중단하고(행은 지출 내역 근거로 남긴다), 그 효과를 키트로 옮긴다
UPDATE simulation_shop_items
SET is_active = FALSE, mod_dttm = CURRENT_TIMESTAMP
WHERE item_seq = 34 AND category = 'SHAMPOO';

UPDATE simulation_shop_items
SET effects_jsonb = '{"cleanliness": 40, "calmness": -5}',
    description = '빗·발톱깎이·목욕 용품이 든 위생 관리 세트입니다. 있으면 언제든 목욕시킬 수 있어요.',
    mod_dttm = CURRENT_TIMESTAMP
WHERE item_seq = 10 AND category = 'GROOMING_KIT';
