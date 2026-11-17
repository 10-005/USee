-- local·dev 전용 더미(db/dev): 반려동물 5건, 실종 신고 10건(ACTIVE 6·CLOSED 4, 경북 좌표·districtCode 47xxx)
-- 기대는 것: S1의 dev_base(USER 100~139), 카탈로그 시드(품종·색상 이름), 시군구 시드(47xxx)
-- 회원: S2 몫 100~104. 회원당 ACTIVE 1건(uq_missing_user_active)이라 6번째 ACTIVE는 어느 세션에도 배정되지 않은 135를 쓴다
-- 사진: images 행만 만든다. S3 객체 images/seed/dev/{pet|missing}-*.jpg는 따로 올려야 URL이 열린다

-- 반려동물
WITH v (user_seq, name, breed, color, gender, age, weight, is_neutered, microchip_number, description) AS (
    VALUES
        (100, '초코', '말티즈', '흰색', 'MALE', '3살', '4.5kg', TRUE, NULL, '파란 목줄, 겁이 많음'),
        (101, '보리', '진도견', '황색', 'FEMALE', '5살', '18kg', TRUE, '410100012345678', '왼쪽 귀가 살짝 접힘'),
        (102, '두부', '포메라니안', '크림색', 'MALE', '2살', '3kg', FALSE, NULL, '분홍 하네스'),
        (103, '나비', '러시안블루', '회색', 'FEMALE', '4살', '4kg', TRUE, NULL, '초록 눈, 방울 목걸이'),
        (104, '콩이', '믹스견', '흰색·갈색', 'UNKNOWN', '1살 추정', NULL, NULL, NULL, '꼬리 끝이 흰색')
),
img AS (
    INSERT INTO images (image_key, mime_type, reg_usr_seq)
    SELECT 'images/seed/dev/pet-' || user_seq || '.jpg', 'image/jpeg', user_seq FROM v
    RETURNING image_seq, image_key
),
pet AS (
    INSERT INTO pets (user_seq, name, combination_seq, gender, age, weight, is_neutered, microchip_number, description)
    SELECT v.user_seq, v.name, c.combination_seq, v.gender, v.age, v.weight, v.is_neutered, v.microchip_number, v.description
    FROM v
    JOIN animal_breeds b ON b.breed_name = v.breed
    JOIN animal_colors co ON co.color_name = v.color
    JOIN animal_combinations c ON c.breed_seq = b.breed_seq AND c.color_seq = co.color_seq
    RETURNING pet_seq, user_seq
)
INSERT INTO pet_images (pet_seq, image_seq, display_order)
SELECT pet.pet_seq, img.image_seq, 1
FROM pet JOIN img ON img.image_key = 'images/seed/dev/pet-' || pet.user_seq || '.jpg';

-- 실종 신고. k는 사진 키 번호이자 신고-사진 연결용. 2장짜리는 k와 k+100
WITH v (k, user_seq, status, breed, color, gender, age, weight, is_neutered, microchip_number, description,
        address, latitude, longitude, occurred_dttm, district_code, contact_phone, is_phone_public, photos) AS (
    VALUES
        (1, 100, 'ACTIVE', '말티즈', '흰색', 'MALE', '3살', '4.5kg', TRUE, NULL, '파란 목줄, 겁이 많아 부르면 숨어요',
         '경상북도 구미시 인동중앙로 1', 36.1036, 128.4196, TIMESTAMPTZ '2026-11-07 18:30:00+09', '47190', '01000000100', TRUE, 2),
        (2, 101, 'ACTIVE', '진도견', '황색', 'FEMALE', '5살', '18kg', TRUE, '410100012345678', '왼쪽 귀가 살짝 접혀 있어요',
         '경상북도 포항시 남구 대잠동', 36.0137, 129.3459, TIMESTAMPTZ '2026-11-06 07:10:00+09', '47111', '01000000101', FALSE, 1),
        (3, 102, 'ACTIVE', '포메라니안', '크림색', 'MALE', '2살', '3kg', FALSE, NULL, '분홍 하네스 착용',
         '경상북도 경주시 황성동', 35.8562, 129.2247, TIMESTAMPTZ '2026-11-05 21:00:00+09', '47130', '01000000102', TRUE, 1),
        (4, 103, 'ACTIVE', '러시안블루', '회색', 'FEMALE', '4살', '4kg', TRUE, NULL, '초록 눈, 방울 목걸이',
         '경상북도 안동시 옥동', 36.5684, 128.7294, TIMESTAMPTZ '2026-11-04 15:20:00+09', '47170', '01000000103', FALSE, 2),
        (5, 104, 'ACTIVE', '믹스견', '흰색·갈색', 'UNKNOWN', '1살 추정', NULL, NULL, NULL, '꼬리 끝이 흰색, 목줄 없음',
         '경상북도 경산시 중방동', 35.8251, 128.7414, TIMESTAMPTZ '2026-11-03 09:45:00+09', '47290', '01000000104', TRUE, 1),
        (6, 135, 'ACTIVE', '시바', '적갈색', 'MALE', '6살', '9kg', NULL, NULL, '빨간 목줄, 사람을 잘 따름',
         '경상북도 칠곡군 왜관읍', 35.9906, 128.3976, TIMESTAMPTZ '2026-11-02 19:00:00+09', '47850', '01000000135', TRUE, 1),
        (7, 100, 'CLOSED', '푸들', '갈색', 'FEMALE', '7살', '5kg', TRUE, NULL, '귀 끝이 곱슬, 노란 옷',
         '경상북도 김천시 부곡동', 36.1398, 128.1136, TIMESTAMPTZ '2026-10-20 12:00:00+09', '47150', '01000000100', FALSE, 1),
        (8, 101, 'CLOSED', '웰시코기펨브로크', '황색', 'MALE', '3살', '12kg', TRUE, NULL, '짧은 다리, 산책 중 실종',
         '경상북도 상주시 남성동', 36.4109, 128.1590, TIMESTAMPTZ '2026-10-15 17:40:00+09', '47250', '01000000101', TRUE, 2),
        (9, 102, 'CLOSED', '페르시안친칠라', '흰색', 'FEMALE', '8살', NULL, NULL, NULL, '털이 길고 겁이 많음',
         '경상북도 영주시 휴천동', 36.8057, 128.6240, TIMESTAMPTZ '2026-10-10 08:15:00+09', '47210', '01000000102', FALSE, 1),
        (10, 103, 'CLOSED', '믹스견', '검은색', 'UNKNOWN', NULL, '약 10kg', FALSE, NULL, '검은 털, 가슴에 흰 무늬',
         '경상북도 성주군 성주읍', 35.9192, 128.2830, TIMESTAMPTZ '2026-10-01 20:30:00+09', '47840', '01000000103', TRUE, 1)
),
photo AS (
    SELECT v.k, v.user_seq, 1 AS display_order, 'images/seed/dev/missing-' || v.k || '.jpg' AS image_key FROM v
    UNION ALL
    SELECT v.k, v.user_seq, 2, 'images/seed/dev/missing-' || (v.k + 100) || '.jpg' FROM v WHERE v.photos = 2
),
img AS (
    INSERT INTO images (image_key, mime_type, reg_usr_seq)
    SELECT image_key, 'image/jpeg', user_seq FROM photo
    RETURNING image_seq, image_key
),
rep AS (
    INSERT INTO missing_reports (user_seq, combination_seq, gender, age, weight, is_neutered, microchip_number, description,
                                 address, latitude, longitude, occurred_dttm, district_code, status, contact_phone,
                                 is_phone_public, reg_dttm)
    SELECT v.user_seq, c.combination_seq, v.gender, v.age, v.weight, v.is_neutered, v.microchip_number, v.description,
           v.address, v.latitude, v.longitude, v.occurred_dttm, v.district_code, v.status, v.contact_phone,
           v.is_phone_public, v.occurred_dttm + INTERVAL '2 hours'
    FROM v
    JOIN animal_breeds b ON b.breed_name = v.breed
    JOIN animal_colors co ON co.color_name = v.color
    JOIN animal_combinations c ON c.breed_seq = b.breed_seq AND c.color_seq = co.color_seq
    RETURNING missing_report_seq, user_seq, occurred_dttm
)
INSERT INTO missing_report_images (missing_report_seq, image_seq, display_order)
SELECT rep.missing_report_seq, img.image_seq, photo.display_order
FROM v
JOIN rep ON rep.user_seq = v.user_seq AND rep.occurred_dttm = v.occurred_dttm
JOIN photo ON photo.k = v.k
JOIN img ON img.image_key = photo.image_key;
