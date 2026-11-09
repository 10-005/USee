-- 펫샵 물품 초기 데이터 35개(S15P31D106-240, Q412). 관리 API가 없어 시드로만 넣는다
-- 참고 물품 목록 32개 + 행동에 필요한 가안 3개(강아지 배변판·순한 샴푸·기본 상비약). 효과값은 모두 가안
-- 사진(image_key)은 아직 없어 NULL
-- item_seq는 직접 지정한다: 묶음 수 설정(simulation.shop.uses-per-purchase)이 이 번호로 물품을 찾는다
-- 가격은 1개(묶음 물품은 1묶음) 가격. 묶음 물품은 1개를 사면 설정의 횟수만큼 인벤토리 수량이 늘어난다
-- 한 행에 물품 하나(ShopItemSeedTest가 행 단위로 읽어 분류·효과 키·설정 짝을 검사한다)
INSERT INTO simulation_shop_items (item_seq, item_name, category, is_consumable, price, description, image_key, species_scope, effects_jsonb, display_order) VALUES
-- 기구: 그릇·화장실
(1, '밥그릇', 'FOOD_BOWL', FALSE, 10000, '사료를 담는 그릇입니다. 있어야 밥을 줄 수 있어요.', NULL, 'ALL', NULL, 1),
(2, '물그릇', 'WATER_BOWL', FALSE, 10000, '물을 담는 그릇입니다. 물은 무료예요.', NULL, 'ALL', NULL, 2),
(3, '고양이 화장실', 'LITTER_BOX', FALSE, 17400, '모래를 깔아 쓰는 고양이 화장실입니다.', NULL, 'CAT', NULL, 3),
(4, '강아지 배변판', 'LITTER_BOX', FALSE, 15000, '배변패드를 고정해 쓰는 강아지 배변판입니다.', NULL, 'DOG', NULL, 4),
-- 기구: 가구·산책·위생
(5, '안전한 이동장', 'FURNITURE', FALSE, 16200, '병원이나 외출할 때 쓰는 이동장입니다.', NULL, 'ALL', NULL, 5),
(6, '포근한 쉼터', 'FURNITURE', FALSE, 19900, '편히 쉴 수 있는 방석형 쉼터입니다.', NULL, 'ALL', NULL, 6),
(7, '아늑한 숨숨 하우스', 'FURNITURE', FALSE, 36000, '몸을 숨기고 쉴 수 있는 집입니다.', NULL, 'ALL', NULL, 7),
(8, '구름 캣타워', 'FURNITURE', FALSE, 64800, '높은 곳을 좋아하는 고양이를 위한 캣타워입니다.', NULL, 'CAT', NULL, 8),
(9, '하네스와 리드줄', 'LEASH', FALSE, 14900, '산책에 꼭 필요한 하네스와 리드줄 세트입니다.', NULL, 'DOG', NULL, 9),
(10, '위생 관리 키트', 'GROOMING_KIT', FALSE, 13800, '빗·발톱깎이 등이 든 위생 관리 세트입니다.', NULL, 'ALL', NULL, 10),
-- 기구: 장난감(놀아 주기에 사용)
(11, '탐색 장난감', 'TOY', FALSE, 4100, '이리저리 굴리며 노는 장난감입니다.', NULL, 'ALL', '{"bond": 3, "calmness": 3}', 11),
(12, '통통 놀이공', 'TOY', FALSE, 8900, '잘 튀는 놀이공입니다.', NULL, 'ALL', '{"bond": 4, "calmness": 3}', 12),
(13, '탐색 퍼즐 장난감', 'TOY', FALSE, 15400, '간식을 숨겨 찾게 하는 퍼즐 장난감입니다.', NULL, 'ALL', '{"bond": 3, "calmness": 6}', 13),
(14, '삑삑이 장난감', 'TOY', FALSE, 6900, '누르면 소리가 나는 장난감입니다.', NULL, 'ALL', '{"bond": 4, "calmness": 2}', 14),
(15, '포옹 인형', 'TOY', FALSE, 7500, '껴안고 쉴 수 있는 인형입니다.', NULL, 'ALL', '{"bond": 2, "calmness": 5}', 15),
(16, '킁킁 노즈워크 매트', 'TOY', FALSE, 39000, '냄새로 간식을 찾는 노즈워크 매트입니다.', NULL, 'DOG', '{"bond": 4, "calmness": 8}', 16),
(17, '매듭 터그 로프', 'TOY', FALSE, 2700, '함께 당기며 노는 터그 로프입니다.', NULL, 'DOG', '{"bond": 5, "calmness": 2}', 17),
(18, '스크래처', 'TOY', FALSE, 10000, '발톱을 긁으며 스트레스를 푸는 스크래처입니다.', NULL, 'CAT', '{"bond": 2, "calmness": 6}', 18),
(19, '낚싯대 장난감', 'TOY', FALSE, 2200, '함께 흔들며 노는 낚싯대 장난감입니다.', NULL, 'CAT', '{"bond": 5, "calmness": 3}', 19),
(20, '꼬마 쥐 장난감', 'TOY', FALSE, 2000, '쫓아다니며 노는 작은 쥐 장난감입니다.', NULL, 'CAT', '{"bond": 3, "calmness": 2}', 20),
(21, '바스락 숨숨 터널', 'TOY', FALSE, 15000, '숨고 뛰어다니는 바스락 터널입니다.', NULL, 'CAT', '{"bond": 3, "calmness": 6}', 21),
-- 소모품: 사료·간식
(22, '균형 잡힌 사료', 'FOOD', TRUE, 10600, '1개에 12끼입니다. 밥을 줄 때마다 1끼씩 줄어요.', NULL, 'ALL', '{"satiety": 30, "bond": 1}', 22),
(23, '바삭한 미니 비스킷', 'SNACK', TRUE, 4500, '1개에 5회입니다.', NULL, 'ALL', '{"satiety": 5, "bond": 3, "calmness": 3}', 23),
(24, '동결건조 닭고기', 'SNACK', TRUE, 6900, '1개에 6회입니다.', NULL, 'ALL', '{"satiety": 10, "bond": 4, "calmness": 5}', 24),
(25, '한입 훈련 간식', 'SNACK', TRUE, 9000, '1개에 8회입니다. 훈련 보상으로 좋아요.', NULL, 'DOG', '{"satiety": 5, "bond": 5}', 25),
(26, '씹는 간식', 'SNACK', TRUE, 2000, '1개에 4회입니다. 오래 씹으며 안정을 찾아요.', NULL, 'DOG', '{"satiety": 5, "calmness": 8}', 26),
(27, '동결건조 흰살생선', 'SNACK', TRUE, 13000, '1개에 6회입니다.', NULL, 'CAT', '{"satiety": 10, "bond": 4, "calmness": 5}', 27),
(28, '촉촉한 스틱 간식', 'SNACK', TRUE, 1900, '1개에 5회입니다. 수분도 함께 채워요.', NULL, 'CAT', '{"satiety": 5, "hydration": 5, "bond": 3}', 28),
-- 소모품: 배변·위생·약
(29, '배변 청소용품', 'LITTER', TRUE, 5500, '1개에 10회입니다. 배변을 치울 때 써요.', NULL, 'ALL', '{"cleanliness": 20}', 29),
(30, '배변패드', 'LITTER', TRUE, 2600, '1개에 10장입니다. 배변판에 깔아 써요.', NULL, 'DOG', '{"cleanliness": 30}', 30),
(31, '화장실 모래', 'LITTER', TRUE, 7200, '1개에 10회입니다. 고양이 화장실에 깔아 써요.', NULL, 'CAT', '{"cleanliness": 30}', 31),
(32, '배변봉투', 'POOP_BAG', TRUE, 500, '1개에 10장입니다. 산책 중 배변을 치울 때 써요.', NULL, 'DOG', NULL, 32),
(33, '향긋한 캣닢', 'CATNIP', TRUE, 9900, '1개에 4회입니다. 기분 전환에 좋아요.', NULL, 'CAT', '{"calmness": 10, "bond": 2}', 33),
(34, '순한 샴푸', 'SHAMPOO', TRUE, 8000, '1개에 10회입니다. 목욕할 때 써요.', NULL, 'ALL', '{"cleanliness": 40, "calmness": -5}', 34),
(35, '기본 상비약', 'MEDICINE', TRUE, 15000, '1개에 3회입니다. 몸이 안 좋을 때 먹여요.', NULL, 'ALL', '{"health": 20}', 35);

-- 직접 지정한 번호 다음부터 IDENTITY가 이어지도록 맞춘다
SELECT setval(pg_get_serial_sequence('simulation_shop_items', 'item_seq'), (SELECT MAX(item_seq) FROM simulation_shop_items));
