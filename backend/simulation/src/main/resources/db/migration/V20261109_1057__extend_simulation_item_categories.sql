-- 펫샵 물품 분류 9종 → 14종(S15P31D106-240). 참고 물품 목록을 빼지 않고 담기 위해 분류를 추가한다
-- 기구(비소모): FURNITURE(이동장·쉼터·캣타워 등), LEASH(하네스와 리드줄, 산책 기본 조건), GROOMING_KIT(위생 관리 키트)
-- 소모품: POOP_BAG(산책용 배변봉투), CATNIP(캣닢)
ALTER TABLE simulation_shop_items DROP CONSTRAINT ck_sim_item_category;
ALTER TABLE simulation_shop_items ADD CONSTRAINT ck_sim_item_category CHECK (category IN (
    'FOOD_BOWL', 'WATER_BOWL', 'LITTER_BOX', 'TOY', 'FURNITURE', 'LEASH', 'GROOMING_KIT',
    'FOOD', 'SNACK', 'LITTER', 'SHAMPOO', 'MEDICINE', 'POOP_BAG', 'CATNIP'
));

-- M9: 기구 7종만 비소모
ALTER TABLE simulation_shop_items DROP CONSTRAINT ck_sim_item_consumable;
ALTER TABLE simulation_shop_items ADD CONSTRAINT ck_sim_item_consumable CHECK (
    (category IN ('FOOD_BOWL', 'WATER_BOWL', 'LITTER_BOX', 'TOY', 'FURNITURE', 'LEASH', 'GROOMING_KIT')) = (is_consumable = FALSE)
);
