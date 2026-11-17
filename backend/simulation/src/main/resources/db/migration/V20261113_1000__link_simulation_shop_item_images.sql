-- 펫샵 물품 사진 연결(S15P31D106-240). S3 객체 키만 저장하고 imageUrl은 {STORAGE_PUBLIC_BASE_URL}/{image_key}(CloudFront)
-- 파일은 S3 images/seed/simulation/items/item-{item_seq}.png 에 먼저 올린다. 판매 중단한 순한 샴푸(34)는 사진 없음
UPDATE simulation_shop_items
SET image_key = 'images/seed/simulation/items/item-' || item_seq || '.png', mod_dttm = CURRENT_TIMESTAMP
WHERE item_seq <> 34 AND image_key IS NULL;
