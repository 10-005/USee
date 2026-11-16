-- 펫샵 앞쪽 6개 순서: 밥그릇, 균형 잡힌 사료, 탐색 장난감, 위생 관리 키트, 하네스와 리드줄, 배변봉투
-- 나머지 물품은 기존 순서를 지킨 채 그 뒤로 민다
UPDATE simulation_shop_items SET display_order = display_order + 100;

UPDATE simulation_shop_items SET display_order = CASE item_seq
    WHEN 1  THEN 1  -- 밥그릇
    WHEN 22 THEN 2  -- 균형 잡힌 사료
    WHEN 11 THEN 3  -- 탐색 장난감
    WHEN 10 THEN 4  -- 위생 관리 키트
    WHEN 9  THEN 5  -- 하네스와 리드줄
    WHEN 32 THEN 6  -- 배변봉투
    ELSE display_order
END;
