-- 보호소 시연 데이터 보강(local·dev 프로필에서만). 앞선 dev 시드(0325·0330·1500)가 비워 둔 값을 채우고 행을 추가한다
-- 기존 행은 PK 대신 앞선 시드의 문구·조합으로 찾는다(환경마다 PK가 다를 수 있음). 못 찾으면 그 행은 건너뛴다
-- 사진: S3 images/seed/dev/{shelter-1-*.png, animal-NN.jpg, found-NN.jpg}(2026-11-10 업로드)
-- 보호소 운영 시간은 국가동물보호정보시스템 보호센터 안내 기준. 상주·성주 전화는 공개 정보가 없어 그대로 둔다

-- 품종·색 이름으로 조합을 찾는다. 카탈로그에 없으면 실패시켜 잘못된 품종이 들어가지 않게 한다
CREATE FUNCTION pg_temp.seed_combo(p_breed TEXT, p_color TEXT) RETURNS INTEGER LANGUAGE plpgsql AS $f$
DECLARE v INTEGER;
BEGIN
    SELECT c.combination_seq INTO v
    FROM animal_combinations c
    JOIN animal_breeds b ON b.breed_seq = c.breed_seq
    JOIN animal_colors co ON co.color_seq = c.color_seq
    WHERE b.breed_name = p_breed AND co.color_name = p_color
    ORDER BY c.combination_seq LIMIT 1;
    IF v IS NULL THEN
        RAISE EXCEPTION 'dev seed: 조합 없음 % / %', p_breed, p_color;
    END IF;
    RETURN v;
END
$f$;

-- 같은 키가 이미 있으면 그 행을 쓴다
CREATE FUNCTION pg_temp.seed_image(p_key TEXT, p_mime TEXT, p_usr INTEGER) RETURNS BIGINT LANGUAGE plpgsql AS $f$
DECLARE v BIGINT;
BEGIN
    SELECT image_seq INTO v FROM images WHERE image_key = p_key;
    IF v IS NULL THEN
        INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES (p_key, p_mime, p_usr) RETURNING image_seq INTO v;
    END IF;
    RETURN v;
END
$f$;

-- 입양 신청 답변(문항 1~20, V20261109_0004). 조건부 문항(5·7·18~20)은 앞 문항에 맞을 때만 넣는다
CREATE FUNCTION pg_temp.seed_answers(p INTEGER) RETURNS JSONB LANGUAGE sql AS $f$
    SELECT CASE p
    WHEN 1 THEN '{"1": "아파트", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "없음", "8": "있음",
        "9": "4~8시간", "10": "본인과 배우자", "11": "없음", "12": "장기 출장 시 근처에 사는 부모님이 돌봐 주시기로 했습니다.",
        "13": "어릴 때 키우던 강아지를 떠나보낸 뒤, 이번에는 보호소 아이에게 가족이 되어 주고 싶습니다.",
        "14": "10~30만 원", "15": "행동 문제가 생기면 훈련사 상담을 받고, 끝까지 함께하겠습니다.", "16": "동의",
        "17": "이수", "18": "농림축산식품부 동물사랑배움터", "19": "반려견 입양 전 기본 교육", "20": "2026-10-02"}'
    WHEN 2 THEN '{"1": "단독주택", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "있음",
        "7": "7살 진도 믹스(누리), 중성화 완료, 다른 개와 잘 지냅니다.", "8": "있음", "9": "4시간 미만",
        "10": "재택근무하는 본인", "11": "없음", "12": "이사 계획은 없고, 여행 시에는 반려견 동반 숙소를 이용합니다.",
        "13": "마당이 있어 활동량 많은 아이도 충분히 뛰놀 수 있고, 누리에게 친구를 만들어 주고 싶습니다.",
        "14": "10~30만 원", "15": "가족 모두 책임지기로 했고, 어려우면 보호소와 먼저 상의하겠습니다.", "16": "동의",
        "17": "이수", "18": "경상북도 동물위생시험소", "19": "반려동물 입양자 교육", "20": "2026-09-18"}'
    WHEN 3 THEN '{"1": "오피스텔", "2": "월세", "3": "확인 필요", "4": "없음", "6": "없음"}'
    WHEN 4 THEN '{"1": "다세대", "2": "전세", "3": "허용", "4": "있음", "5": "모두 동의", "6": "있음",
        "7": "3살 코리안 숏헤어(콩이) 1마리, 중성화 완료", "8": "있음", "9": "4~8시간", "10": "본인",
        "11": "없음", "12": "이사할 때도 반려동물 허용 집만 구하고, 부재 시에는 동생이 돌봐 줍니다.",
        "13": "콩이가 낮에 혼자 있는 시간이 길어 함께 지낼 고양이 친구를 찾고 있습니다.",
        "14": "10만 원 미만", "15": "합사가 어렵다면 공간을 분리해 천천히 적응시키겠습니다.", "16": "동의", "17": "미이수"}'
    WHEN 5 THEN '{"1": "오피스텔", "2": "월세", "3": "확인 필요", "4": "없음", "6": "없음", "8": "없음",
        "9": "8시간 이상", "10": "본인", "11": "모름", "12": "아직 정하지 못했습니다.",
        "13": "혼자 지내는 시간이 많아 반려동물과 함께하고 싶습니다.", "14": "10만 원 미만",
        "15": "주변 지인에게 부탁하겠습니다.", "16": "동의", "17": "미이수"}'
    WHEN 6 THEN '{"1": "아파트", "2": "전세", "3": "허용", "4": "있음", "5": "일부 동의", "6": "없음", "8": "있음",
        "9": "4~8시간", "10": "본인", "11": "없음", "12": "부재 시 반려동물 호텔을 이용하겠습니다.",
        "13": "아이들이 커서 함께 돌볼 수 있게 되어 입양을 결심했습니다.", "14": "10~30만 원",
        "15": "가족회의 후 보호소와 상의하겠습니다.", "16": "동의", "17": "미이수"}'
    WHEN 7 THEN '{"1": "아파트", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "없음", "8": "있음",
        "9": "4시간 미만", "10": "본인과 어머니", "11": "없음", "12": "가족이 함께 살아 늘 돌볼 사람이 있습니다.",
        "13": "어머니가 퇴직하신 뒤 함께 산책할 반려견을 찾고 있습니다.", "14": "30만 원 이상",
        "15": "건강 문제가 생기면 치료비를 감당할 수 있도록 적금을 들어 두었습니다.", "16": "동의",
        "17": "이수", "18": "구미시 동물보호센터", "19": "입양 전 방문 교육", "20": "2026-10-10"}'
    WHEN 8 THEN '{"1": "다세대", "2": "월세", "3": "허용되지 않음", "4": "없음", "6": "없음", "8": "없음",
        "9": "8시간 이상", "10": "본인", "11": "없음", "12": "본가에 맡길 생각입니다.",
        "13": "사진을 보고 한눈에 반했습니다.", "14": "10만 원 미만", "15": "본가에 보내겠습니다.",
        "16": "동의하지 않음", "17": "미이수"}'
    WHEN 9 THEN '{"1": "단독주택", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "없음", "8": "있음",
        "9": "4시간 미만", "10": "본인과 아내", "11": "없음", "12": "시골집이라 이사 계획이 없고, 부재 시 이웃 친척이 돌봐 줍니다.",
        "13": "진돗개를 키워 본 경험이 있어 활동량 많은 아이를 잘 돌볼 수 있습니다.", "14": "10~30만 원",
        "15": "넓은 마당과 산책로가 있어 끝까지 함께하겠습니다.", "16": "동의",
        "17": "이수", "18": "칠곡유기동물보호센터", "19": "대형견 입양 상담 교육", "20": "2026-10-20"}'
    WHEN 10 THEN '{"1": "아파트", "2": "전세", "3": "허용", "4": "있음", "5": "모두 동의", "6": "없음", "8": "있음",
        "9": "4~8시간", "10": "본인과 남편", "11": "없음", "12": "부재 시 반려견 유치원을 이용합니다.",
        "13": "웰시 코기를 키우던 친구 집에서 함께 지내 본 뒤 입양을 결심했습니다.", "14": "10~30만 원",
        "15": "관절 관리와 체중 조절을 꾸준히 하겠습니다.", "16": "동의",
        "17": "이수", "18": "농림축산식품부 동물사랑배움터", "19": "반려견 입양 전 기본 교육", "20": "2026-10-15"}'
    WHEN 11 THEN '{"1": "단독주택", "2": "전세", "3": "허용", "4": "있음", "5": "모두 동의", "6": "있음",
        "7": "5살 믹스견(복실), 중성화 완료", "8": "있음", "9": "4~8시간", "10": "본인",
        "11": "없음", "12": "부모님과 함께 살아 부재 시에도 돌볼 수 있습니다.",
        "13": "복실이와 함께 뛰어놀 활발한 친구를 찾고 있습니다.", "14": "10~30만 원",
        "15": "훈련 수업을 함께 들으며 적응시키겠습니다.", "16": "동의", "17": "미이수"}'
    WHEN 12 THEN '{"1": "오피스텔", "2": "월세", "3": "확인 필요", "4": "없음", "6": "없음", "8": "없음",
        "9": "8시간 이상", "10": "본인", "11": "모름", "12": "아직 생각해 보지 않았습니다.",
        "13": "작고 귀여운 고양이를 키우고 싶습니다.", "14": "10만 원 미만", "15": "다른 사람에게 보내겠습니다.",
        "16": "동의", "17": "미이수"}'
    WHEN 13 THEN '{"1": "아파트", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "없음", "8": "있음",
        "9": "4~8시간", "10": "본인과 아들", "11": "없음", "12": "부재 시 근처 동생 집에 맡깁니다.",
        "13": "비글을 키워 본 적이 있어 성격과 활동량을 잘 알고 있습니다.", "14": "10~30만 원",
        "15": "분리불안이 생기면 전문가 상담을 받겠습니다.", "16": "동의",
        "17": "이수", "18": "성주군동물보호센터", "19": "입양 전 방문 교육", "20": "2026-10-25"}'
    ELSE '{"1": "아파트", "2": "전세", "3": "허용", "4": "있음", "5": "모두 동의", "6": "있음",
        "7": "4살 페르시안(보리) 1마리, 중성화 완료", "8": "있음", "9": "4~8시간", "10": "본인",
        "11": "없음", "12": "부재 시 펫시터를 부릅니다.",
        "13": "보리와 성격이 잘 맞을 차분한 고양이를 찾고 있습니다.", "14": "10~30만 원",
        "15": "건강 검진을 정기적으로 받게 하겠습니다.", "16": "동의", "17": "미이수"}'
    END::jsonb
$f$;

DO $dev$
DECLARE
    r          RECORD;
    v_id       INTEGER;
    v_img      BIGINT;
    v_member   INTEGER;
    v_rescue   INTEGER;
    v_animal   INTEGER;
    v_app      BIGINT;
    v_animals  INTEGER[] := '{}';
BEGIN
    -- ===== 1. 보호소 정보 =====
    FOR r IN SELECT * FROM (VALUES
        (1, '경상북도수의사회 구미시분회가 운영하는 구미시 지정 동물보호센터입니다. 옥성면 애니멀 케어 센터에서 구조된 개와 고양이를 보호하며, 보호 기간이 끝난 아이들의 새 가족을 찾고 있습니다.',
            '평일 09:00~21:00'),
        (2, '김천시가 지정한 동물보호센터로 개와 고양이, 그 밖의 동물을 구조해 보호합니다. 공고 기간이 지난 동물은 입양 상담을 거쳐 새 가족에게 보냅니다.',
            '평일 09:00~18:00'),
        (3, '칠곡군 지정 유기동물보호센터로 민간 단체가 운영합니다. 석적읍에서 개와 고양이를 보호하며, 입양 전 방문 상담으로 동물과 가족의 궁합을 함께 살핍니다.',
            '평일 09:00~18:00'),
        (4, '상주시가 동물병원에 위탁해 운영하는 동물보호센터입니다. 개와 고양이를 보호하며 진료가 필요한 아이는 센터에서 바로 치료합니다.',
            '평일 10:00~17:00'),
        (5, '성주군 지정 동물보호센터로 선남면에서 개와 고양이를 보호합니다. 입양 문의는 운영 시간 중 전화로 받고 있습니다.',
            '평일 09:00~18:00')
    ) AS t(shelter_seq, description, hours)
    LOOP
        UPDATE shelters SET description = r.description, operating_hours = r.hours,
                            closed_days = '주말·공휴일 (입양 상담은 전화 예약 후 방문)'
        WHERE shelter_seq = r.shelter_seq;
    END LOOP;

    v_member := (SELECT user_seq FROM shelter_members WHERE shelter_seq = 1);
    UPDATE shelters SET profile_image_seq    = pg_temp.seed_image('images/seed/dev/shelter-1-profile.png', 'image/png', v_member),
                        background_image_seq = pg_temp.seed_image('images/seed/dev/shelter-1-bg.png', 'image/png', v_member)
    WHERE shelter_seq = 1;

    -- 보호소 계정: 담당자 이름·전화(보호소 번호). 닉네임은 보호소 이름
    UPDATE users u SET name = s.shelter_name || ' 담당자', nickname = s.shelter_name, phone = s.phone
    FROM shelter_members m JOIN shelters s ON s.shelter_seq = m.shelter_seq
    WHERE u.user_seq = m.user_seq AND u.role = 'SHELTER';

    -- ===== 2. 보호동물 =====
    -- n 1~16: 기존 행(보호소 + 앞선 시드 문구로 찾음), 17~30: 새 행. 사진은 animal-NN.jpg
    FOR r IN SELECT * FROM (VALUES
        (1,  1, '사람을 잘 따르고 산책을 좋아함', '말티즈', '흰색', 'MALE', '2살 추정', '4kg', TRUE,
             '사람을 잘 따르고 산책을 좋아함. 낯선 사람에게도 꼬리를 흔들어요.', '기초 검진 완료, 슬개골 정상', '종합백신 3차·광견병 접종 완료',
             '입양 상담 시 산책 가능 시간 확인', 'PROTECTING', 'AVAILABLE', NULL, 3),
        (2,  1, '겁이 많지만 간식에 금방 마음을 엶', '믹스묘', '크림색', 'FEMALE', '1살 추정', '3kg', TRUE,
             '겁이 많지만 간식에 금방 마음을 엶. 사냥 놀이를 좋아해요.', '기초 검진 완료, 귀 진드기 치료 완료', '종합백신 2차 접종 완료',
             '실내 양육 가정 우선, 방묘창 설치 확인', 'PROTECTING', 'AVAILABLE', NULL, 5),
        (3,  2, '다리 치료 후 회복 중', '믹스묘', '갈색', 'UNKNOWN', '3개월', '1.2kg', FALSE,
             '뒷다리 골절 치료 후 회복 중. 잘 먹고 활발해요.', '뒷다리 골절 수술 후 회복 중, 2주 뒤 재검', '종합백신 1차 접종 완료',
             '회복 경과 공유 후 입양 진행', 'PROTECTING', 'AVAILABLE', NULL, 7),
        (4,  2, '조용하고 얌전함', '시츄', '갈색&흰색', 'FEMALE', '5살 추정', '6kg', TRUE,
             '조용하고 얌전함. 무릎 위에서 자는 걸 좋아해요.', '기초 검진 완료, 치석 제거 필요', '종합백신·광견병 접종 완료',
             '치석 제거 후 입양 가능', 'PROTECTING', 'AVAILABLE', NULL, 9),
        (5,  3, '활동량이 많아 넓은 공간 필요', '진도견', '흰색', 'MALE', '3살 추정', '17kg', FALSE,
             '활동량이 많아 넓은 공간 필요. 보호자에게 충성심이 강해요.', '기초 검진 완료, 심장사상충 음성', '종합백신·광견병 접종 완료',
             '마당 있는 가정 우선, 중성화 후 입양', 'PROTECTING', 'AVAILABLE', NULL, 11),
        (6,  3, '다른 동물과 잘 지냄', '러시안 블루', '회색', 'FEMALE', '2살 추정', '3.5kg', TRUE,
             '다른 동물과 잘 지냄. 조용하고 애교가 많아요.', '기초 검진 완료', '종합백신 3차 접종 완료',
             '다묘 가정 입양 가능', 'PROTECTING', 'AVAILABLE', NULL, 13),
        (7,  4, '노령으로 정기 검진 필요', '비글', '흰색&갈색&탄', 'MALE', '7살 추정', '11kg', TRUE,
             '노령으로 정기 검진 필요. 냄새 맡으며 천천히 걷는 산책을 좋아해요.', '노령견 정기 검진 중, 백내장 초기', '종합백신·광견병 접종 완료',
             '검진 결과 확인 후 입양 공고 예정', 'PROTECTING', 'UNAVAILABLE', NULL, 15),
        (8,  4, '입소 직후 기초 검진 대기', '믹스견', '검정색', 'UNKNOWN', '1살 추정', '8kg', FALSE,
             '입소 직후 기초 검진 대기. 사람 손을 조금 무서워해요.', '기초 검진 대기', '접종 이력 확인 중',
             '공고 기간 중 보호자 확인 대기', 'PROTECTING', 'UNAVAILABLE', NULL, 2),
        (9,  5, '피부 치료 중', '푸들', '갈색', 'FEMALE', '4살 추정', '5kg', FALSE,
             '피부 치료 중. 미용을 마치면 곱슬털이 예뻐질 거예요.', '피부염 치료 중(약욕 주 2회)', '종합백신 접종 완료',
             '피부 치료 완료 후 입양 가능', 'PROTECTING', 'UNAVAILABLE', NULL, 4),
        (10, 5, '예방접종 진행 중', '포메라니안', '크림색', 'MALE', '6개월', '2kg', FALSE,
             '예방접종 진행 중. 호기심이 많고 장난을 좋아해요.', '기초 검진 완료', '종합백신 2차 접종 완료(3차 예정)',
             '접종 완료 후 입양 공고 예정', 'PROTECTING', 'UNAVAILABLE', NULL, 6),
        (11, 1, '신청서로 입양 완료', '비숑 프리제', '흰색', 'FEMALE', '2살 추정', '4kg', TRUE,
             '하얀 곱슬털, 사람 품에 안기는 걸 좋아해요.', '기초 검진 완료', '종합백신·광견병 접종 완료',
             '입양 후 1개월 사후 연락 완료', 'ADOPTED', 'ADOPTED', NULL, 30),
        (12, 2, '보호소 방문 입양', '시바', '적갈색', 'MALE', '3살 추정', '9kg', TRUE,
             '독립적이지만 산책을 무척 좋아해요.', '기초 검진 완료', '종합백신·광견병 접종 완료',
             '방문 상담 후 직접 인계', 'ADOPTED', 'ADOPTED', NULL, 35),
        (13, 3, '보호소 방문 입양', '페르시안-페르시안 친칠라', '흰색', 'FEMALE', '1살 추정', '3kg', TRUE,
             '긴 털, 빗질을 좋아하는 차분한 고양이예요.', '기초 검진 완료', '종합백신 3차 접종 완료',
             '방문 상담 후 직접 인계', 'ADOPTED', 'ADOPTED', NULL, 40),
        (14, 4, '보호자에게 반환', '믹스견', '갈색&흰색', 'MALE', '4살 추정', '9kg', TRUE,
             '낡은 목걸이를 하고 있었고, 내장칩으로 보호자를 찾았어요.', '기초 검진 완료', '종합백신 접종 완료',
             '내장칩 조회로 보호자 연락', 'RETURNED', 'UNAVAILABLE', NULL, 25),
        (15, 5, '보호자에게 반환', '스코티시폴드', '흰색', 'UNKNOWN', '2살 추정', '4kg', TRUE,
             '접힌 귀, 실내에서 키우던 고양이로 보였어요.', '기초 검진 완료', '종합백신 접종 완료',
             '실종 신고와 대조해 반환', 'RETURNED', 'UNAVAILABLE', NULL, 28),
        (16, 1, 'S5 더미 보호동물 1', '말티즈', '흰색', 'MALE', '2살', '3.5kg', FALSE,
             '입소 3일째, 새 환경에 적응하는 중이에요.', '기초 검진 완료', '접종 이력 확인 중',
             '공고 기간 중 보호자 확인 대기', 'PROTECTING', 'UNAVAILABLE', NULL, 3),
        (17, 1, NULL, '골든 리트리버', '금색', 'FEMALE', '3살 추정', '25kg', TRUE,
             '온순하고 아이들을 좋아해요. 공놀이 최고!', '기초 검진 완료, 고관절 정상', '종합백신·광견병 접종 완료',
             '대형견 양육 경험 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 8),
        (18, 1, NULL, '터키시 앙고라', '흰색', 'FEMALE', '1살 추정', '3kg', FALSE,
             '파란 눈의 하얀 고양이, 조용히 곁에 앉아 있어요.', '청력 검사 진행 중', '종합백신 1차 접종 완료',
             '청력 검사 결과 확인 후 공고', 'PROTECTING', 'UNAVAILABLE', NULL, 2),
        (19, 2, NULL, '진도견', '붉고 엷은 황갈색', 'MALE', '4살 추정', '18kg', TRUE,
             '듬직하고 경계심이 있지만 친해지면 애교가 많아요.', '기초 검진 완료, 심장사상충 음성', '종합백신·광견병 접종 완료',
             '진도견 양육 경험 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 10),
        (20, 2, NULL, '브리티시 쇼트헤어', '청회색', 'MALE', '3살 추정', '5kg', TRUE,
             '느긋하고 사람 손길을 좋아하는 고양이예요.', '기초 검진 완료', '종합백신 3차 접종 완료',
             '실내 양육 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 12),
        (21, 2, NULL, '믹스견', '흰색', 'FEMALE', '10살 추정', '12kg', TRUE,
             '노령으로 보호 중 무지개다리를 건넜어요.', '신부전 치료 중이었음', '종합백신 접종 완료',
             '보호 중 자연사, 화장 처리', 'ENDED', 'UNAVAILABLE', 'NATURAL_DEATH', 60),
        (22, 3, NULL, '웰시 코기 펨브로크', '황갈색', 'MALE', '2살 추정', '11kg', TRUE,
             '짧은 다리로 열심히 뛰어다니는 에너지 넘치는 아이예요.', '기초 검진 완료, 척추 정상', '종합백신·광견병 접종 완료',
             '체중 관리 안내 후 입양', 'PROTECTING', 'AVAILABLE', NULL, 9),
        (23, 3, NULL, '믹스묘', '기타', 'MALE', '6개월', '2kg', FALSE,
             '치즈색 아기 고양이, 장난감만 보면 달려들어요.', '기초 검진 완료', '종합백신 2차 접종 완료',
             '중성화 시기 안내 후 입양', 'PROTECTING', 'AVAILABLE', NULL, 6),
        (24, 3, NULL, '요크셔 테리어', '갈색&검정', 'FEMALE', '8살 추정', '3kg', TRUE,
             '노령이지만 활발하고 사람을 무척 좋아해요.', '치과 치료 예정', '종합백신 접종 완료',
             '치과 치료 후 입양 공고 예정', 'PROTECTING', 'UNAVAILABLE', NULL, 14),
        (25, 4, NULL, '보더 콜리', '검정&흰색', 'FEMALE', '2살 추정', '15kg', TRUE,
             '똑똑하고 배우는 걸 좋아해요. 산책과 놀이가 많이 필요해요.', '기초 검진 완료', '종합백신·광견병 접종 완료',
             '활동량 많은 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 7),
        (26, 4, NULL, '먼치킨', '흰색', 'FEMALE', '1살 추정', '3kg', TRUE,
             '짧은 다리로 아장아장 걷는 애교쟁이예요.', '기초 검진 완료, 관절 정상', '종합백신 3차 접종 완료',
             '실내 양육 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 5),
        (27, 4, NULL, '믹스견', '황갈색', 'MALE', '5살 추정', '13kg', TRUE,
             '다른 지역 보호센터로 이관되었어요.', '기초 검진 완료', '종합백신 접종 완료',
             '관할 변경으로 타 보호센터 이관', 'ENDED', 'UNAVAILABLE', 'OTHER', 45),
        (28, 5, NULL, '비글', '흰색&갈색&탄', 'MALE', '3살 추정', '11kg', TRUE,
             '호기심 많고 냄새 맡기를 좋아해요. 사람을 잘 따라요.', '기초 검진 완료', '종합백신·광견병 접종 완료',
             '분리불안 교육 안내 후 입양', 'PROTECTING', 'AVAILABLE', NULL, 8),
        (29, 5, NULL, '샴', '크림색/암갈색 말단', 'FEMALE', '2살 추정', '4kg', TRUE,
             '파란 눈, 말이 많고 사람과 대화하듯 울어요.', '기초 검진 완료', '종합백신 3차 접종 완료',
             '실내 양육 가정 우선', 'PROTECTING', 'AVAILABLE', NULL, 6),
        (30, 5, NULL, '미니어쳐 닥스훈트', '크림색', 'MALE', '4살 추정', '6kg', TRUE,
             '허리 디스크 치료 중, 계단 사용을 피해야 해요.', '허리 디스크 치료 중', '종합백신 접종 완료',
             '치료 경과 확인 후 공고', 'PROTECTING', 'UNAVAILABLE', NULL, 4)
    ) AS t(n, shelter, old_desc, breed, color, gender, age, weight, neutered, descr, health, vaccine,
           memo, pstatus, astatus, end_reason, days_ago)
    ORDER BY n
    LOOP
        v_member := (SELECT user_seq FROM shelter_members WHERE shelter_seq = r.shelter);
        v_id := NULL;
        IF r.old_desc IS NOT NULL THEN
            UPDATE shelter_animals
            SET combination_seq = pg_temp.seed_combo(r.breed, r.color), gender = r.gender, age = r.age, weight = r.weight,
                is_neutered = r.neutered, description = r.descr, health_check = r.health, vaccination_status = r.vaccine,
                microchip_number = '410100000' || lpad(r.n::text, 6, '0'),
                registration_number = '410190000' || lpad(r.n::text, 6, '0'), adoption_memo = r.memo
            WHERE animal_seq = (SELECT animal_seq FROM shelter_animals
                                WHERE shelter_seq = r.shelter AND description = r.old_desc ORDER BY animal_seq LIMIT 1)
            RETURNING animal_seq INTO v_id;
        ELSE
            INSERT INTO shelter_animals (shelter_seq, combination_seq, gender, age, is_neutered, weight, description,
                                         health_check, vaccination_status, microchip_number, registration_number,
                                         notice_start_date, notice_end_date, protection_status, adoption_status,
                                         end_reason, protection_ended_dttm, adoption_memo, reg_usr_seq, reg_dttm)
            VALUES (r.shelter, pg_temp.seed_combo(r.breed, r.color), r.gender, r.age, r.neutered, r.weight, r.descr,
                    r.health, r.vaccine, '410100000' || lpad(r.n::text, 6, '0'), '410190000' || lpad(r.n::text, 6, '0'),
                    (now() - make_interval(days => r.days_ago))::date, (now() - make_interval(days => r.days_ago))::date + 10,
                    r.pstatus, r.astatus, r.end_reason,
                    CASE WHEN r.end_reason IS NOT NULL THEN now() - make_interval(days => r.days_ago - 20) END,
                    r.memo, v_member, now() - make_interval(days => r.days_ago))
            RETURNING animal_seq INTO v_id;
        END IF;

        v_animals := v_animals || v_id;   -- v_animals[n]. 못 찾은 기존 행은 NULL
        CONTINUE WHEN v_id IS NULL OR EXISTS (SELECT 1 FROM shelter_animal_images WHERE animal_seq = v_id);
        v_img := pg_temp.seed_image('images/seed/dev/animal-' || lpad(r.n::text, 2, '0') || '.jpg', 'image/jpeg', v_member);
        INSERT INTO shelter_animal_images (animal_seq, image_seq, display_order) VALUES (v_id, v_img, 1);
    END LOOP;

    -- ===== 3. 구조 =====
    -- 0330 시드의 직접 구조 3건은 대구 주소라 관할(경북 보호소)에 맞춘다
    FOR r IN SELECT * FROM (VALUES
        (1, '경상북도 구미시 송정동 송정공원',     '공원 정자 아래',     '공원에서 배회하던 개, 산책객 제보로 출동'),
        (2, '경상북도 김천시 평화동 김천역 앞',     NULL,                 '다리 부상, 이동장에 넣어 병원 이송'),
        (3, '경상북도 칠곡군 왜관읍 국도 4호선',   '갓길 배수로',        '도로 갓길에 고립, 교통 통제 후 구조')
    ) AS t(shelter, address, detail, memo)
    LOOP
        UPDATE rescue_records SET rescue_address = r.address, rescue_address_detail = r.detail, rescue_memo = r.memo
        WHERE shelter_seq = r.shelter AND rescue_memo = '더미 구조 기록';
    END LOOP;

    -- 메모가 빈 기존 구조
    UPDATE rescue_records SET rescue_memo = CASE status
            WHEN 'DONE' THEN '현장 도착 후 30분 내 포획, 보호소 입소 완료'
            ELSE '현장 확인 중, 주민 제보로 위치 추적' END
    WHERE rescue_memo IS NULL;

    -- 보호소마다 2~3건 추가. DONE은 새 보호동물(17·19·22·25·28)과 연결
    FOR r IN SELECT * FROM (VALUES
        (1, 'DONE',       'WANDERING',       '경상북도 구미시 인동동 인동시장 앞',        '시장 입구 횡단보도 근처', '목줄 없이 배회, 간식으로 유인해 포획', NULL, 17, 9),
        (1, 'FAILED',     'WANDERING',       '경상북도 구미시 옥계동 산책로',             '금오산 방향 둘레길 입구', '포획 시도 중 산 쪽으로 도주', '포획 시도 중 산으로 도주해 수색 종료', NULL, 6),
        (2, 'DONE',       'DANGEROUS_PLACE', '경상북도 김천시 율곡동 혁신도시 사거리',    '중앙 분리대',             '차도에 갇혀 있어 교통 통제 후 구조', NULL, 19, 11),
        (2, 'CANCELED',   'WANDERING',       '경상북도 김천시 부곡동 아파트 단지',        '놀이터 옆 화단',          '신고자와 통화 후 출동 대기', '신고자가 보호자를 찾아 직접 인계', NULL, 4),
        (3, 'DONE',       'WANDERING',       '경상북도 칠곡군 왜관읍 왜관역 광장',        '역 대합실 출입구',        '역무원 협조로 대합실에서 포획', NULL, 22, 10),
        (3, 'PROCESSING', 'INJURY_ILLNESS',  '경상북도 칠곡군 석적읍 중리 공장 단지',     '창고 뒤편',               '앞다리 부상, 포획틀 설치 후 매일 오전 확인', NULL, NULL, 1),
        (4, 'DONE',       'WANDERING',       '경상북도 상주시 남성동 상주시청 앞',        '주차장 출입구',           '사람을 잘 따라 리드줄로 바로 확보', NULL, 25, 8),
        (4, 'FAILED',     'INJURY_ILLNESS',  '경상북도 상주시 중동면 경천섬 공원',        '자전거길 옆 풀숲',        '다친 채 이동 중이라는 제보', '현장 도착 전 이동, 주변 수색했으나 발견 못함', NULL, 5),
        (5, 'DONE',       'WANDERING',       '경상북도 성주군 성주읍 성밖숲',             '숲 입구 정자 근처',       '산책객 제보로 출동, 간식으로 유인', NULL, 28, 9),
        (5, 'CANCELED',   'OTHER',           '경상북도 성주군 선남면 도성리',             NULL,                      '같은 날 접수된 다른 신고와 같은 개체로 확인', '중복 접수(이미 구조된 개체와 동일)', NULL, 3),
        (5, 'PROCESSING', 'DANGEROUS_PLACE', '경상북도 성주군 월항면 지방리 하천변',      '다리 아래 둔치',          '하천 둔치에 고립, 수위 낮아지면 진입 예정', NULL, NULL, 1)
    ) AS t(shelter, status, reason, address, detail, memo, result_reason, animal_n, days_ago)
    LOOP
        v_member := (SELECT user_seq FROM shelter_members WHERE shelter_seq = r.shelter);
        INSERT INTO rescue_records (shelter_seq, status, completed_dttm, rescued_dttm, rescue_reason_code, rescue_address,
                                    rescue_address_detail, rescue_memo, result_reason, reg_usr_seq, reg_dttm)
        VALUES (r.shelter, r.status,
                CASE WHEN r.status <> 'PROCESSING' THEN now() - make_interval(days => r.days_ago) + interval '4 hours' END,
                now() - make_interval(days => r.days_ago), r.reason, r.address, r.detail, r.memo, r.result_reason,
                v_member, now() - make_interval(days => r.days_ago))
        RETURNING rescue_seq INTO v_rescue;
        IF r.animal_n IS NOT NULL THEN
            UPDATE shelter_animals SET rescue_seq = v_rescue WHERE animal_seq = v_animals[r.animal_n];
        END IF;
    END LOOP;

    -- ===== 4. 반환: 상주(14번)는 회원 113에게 반환 =====
    UPDATE animal_returns ar SET returned_user_seq = 113, recipient_name = u.name, recipient_phone = u.phone
    FROM users u
    WHERE u.user_seq = 113 AND ar.animal_seq = v_animals[14];

    -- ===== 5. 입양 신청 =====
    -- 0330 시드 8건: (회원, 상태, 동물 순번)으로 찾아 답변을 20문항 기준으로 바꾼다
    FOR r IN SELECT * FROM (VALUES
        (1, 110, 'SUBMITTED', 1), (2, 111, 'APPROVED', 1), (3, 112, 'DRAFT', 1), (4, 113, 'SUBMITTED', 3),
        (5, 114, 'REJECTED', 3), (6, 110, 'CANCELED', 2), (7, 111, 'ADOPTED', 11), (8, 112, 'REJECTED', 11)
    ) AS t(k, usr, status, animal_n)
    LOOP
        UPDATE adoption_applications SET answers_jsonb = pg_temp.seed_answers(r.k)
        WHERE user_seq = r.usr AND status = r.status AND animal_seq = v_animals[r.animal_n];
    END LOOP;

    -- 신청이 없던 칠곡·상주·성주에 2건씩
    FOR r IN SELECT * FROM (VALUES
        (9,  113, 5,  'SUBMITTED', NULL::TEXT, 4),
        (10, 114, 22, 'APPROVED',  NULL,       6),
        (11, 110, 25, 'SUBMITTED', NULL,       3),
        (12, 111, 26, 'REJECTED',  '현재 주거지에서 반려동물 양육 허용 여부가 확인되지 않아 입양이 어렵습니다.', 5),
        (13, 112, 28, 'APPROVED',  NULL,       5),
        (14, 113, 29, 'SUBMITTED', NULL,       2)
    ) AS t(k, usr, animal_n, status, rejection, days_ago)
    LOOP
        v_animal := v_animals[r.animal_n];
        CONTINUE WHEN v_animal IS NULL;
        INSERT INTO adoption_applications (user_seq, animal_seq, shelter_seq, answers_jsonb, care_address, care_address_detail,
                                           email, visit_hope_date, applicant_name, applicant_birth_date, applicant_phone,
                                           status, submitted_dttm, reviewed_dttm, reviewed_usr_seq, rejection_reason, reg_dttm)
        SELECT r.usr, a.animal_seq, a.shelter_seq, pg_temp.seed_answers(r.k),
               '경상북도 ' || CASE a.shelter_seq WHEN 3 THEN '칠곡군 왜관읍 중앙로 ' WHEN 4 THEN '상주시 무양동 ' ELSE '성주군 성주읍 경산리 ' END
                   || (200 + r.k),
               (r.k % 5 + 1) || '0' || r.k || '호', 'dummy' || r.usr || '@example.com',
               (now() - make_interval(days => r.days_ago))::date + 7,
               u.name, u.birth_date, u.phone,
               r.status, now() - make_interval(days => r.days_ago),
               CASE WHEN r.status <> 'SUBMITTED' THEN now() - make_interval(days => r.days_ago) + interval '1 day' END,
               CASE WHEN r.status <> 'SUBMITTED' THEN m.user_seq END,
               r.rejection, now() - make_interval(days => r.days_ago)
        FROM shelter_animals a
        JOIN users u ON u.user_seq = r.usr
        JOIN shelter_members m ON m.shelter_seq = a.shelter_seq
        WHERE a.animal_seq = v_animal
        RETURNING application_seq INTO v_app;
    END LOOP;

    -- ===== 6. 발견 신고: 앞선 시드 문구로 찾아 품종·빈 값·사진 보강 =====
    FOR r IN SELECT * FROM (VALUES
        (1,  '파란 목줄, 겁이 많음',        '말티즈',     '흰색',     'MALE',    '3살',      '약 4kg',  TRUE,  NULL::TEXT,                   '410100000900001', NULL::TEXT),
        (2,  '흰색 바탕에 회색 얼룩',       '믹스묘',     '기타',     'UNKNOWN', '1살 추정', '약 3kg',  TRUE,  '흰색 바탕에 회색 얼룩, 왼쪽 귀 끝이 잘려 있어요(TNR).', NULL, NULL),
        (3,  '꼬리가 말려 있음',            '진도견',     '흰색',     'FEMALE',  '5살',      '약 15kg', FALSE, NULL,                         NULL, NULL),
        (4,  '갈색, 미용한 지 얼마 안 됨',  '푸들',       '갈색',     'MALE',    '2살',      '약 3kg',  TRUE,  NULL,                         '410100000900004', NULL),
        (5,  '목에 빨간 손수건',            '믹스견',     '흰색',     'UNKNOWN', '1살',      '약 7kg',  FALSE, NULL,                         NULL, NULL),
        (6,  '치즈색 털',                   '믹스묘',     '기타',     'FEMALE',  '2살 추정', '약 3kg',  TRUE,  NULL,                         NULL, NULL),
        (7,  '빨간 하네스 착용',            '시바',       '적갈색',   'MALE',    '4살',      '약 9kg',  TRUE,  NULL,                         '410100000900007', NULL),
        (8,  '눈물 자국이 심함',            '말티즈',     '흰색',     'FEMALE',  '7살',      '약 3kg',  TRUE,  NULL,                         NULL, NULL),
        (9,  '검은색 단모, 다리를 절음',    '믹스견',     '검정색',   'UNKNOWN', '3살 추정', '약 10kg', FALSE, NULL,                         NULL, NULL),
        (10, '주황색 털, 사람을 잘 따름',   '포메라니안', '주황색',   'MALE',    '1살',      '약 2kg',  FALSE, NULL,                         '410100000900010', NULL),
        (11, 'S5 더미 발견 1',              '치와와',     '갈색',     'MALE',    '5살 추정', '약 2kg',  TRUE,
             '마트 주차장 차 밑에 숨어 있었어요. 갈색 단모이고 몸을 떨고 있어요.', NULL, '이마트 구미점 지하 주차장'),
        (12, 'S5 더미 발견 2',              '믹스묘',     '갈색',     'UNKNOWN', '3개월 추정', '약 1kg', FALSE,
             '아파트 화단에서 울고 있던 갈색 줄무늬 새끼 고양이예요. 어미는 보이지 않았어요.', NULL, '도량동 아파트 단지 화단'),
        -- 회원 1번 시연 신고(V20261110_1500). 사진은 이미 있다
        (13, '목줄 없이 역 앞을 돌아다니고 있었어요. 사람을 피하지는 않아요.', '믹스견', '흰색', 'UNKNOWN', '2살 추정', '약 8kg', FALSE,
             NULL, NULL, NULL)
    ) AS t(n, old_desc, breed, color, gender, age, weight, neutered, new_desc, chip, landmark)
    LOOP
        UPDATE found_reports
        SET combination_seq = pg_temp.seed_combo(r.breed, r.color), gender = r.gender, age = r.age, weight = r.weight,
            is_neutered = r.neutered, description = coalesce(r.new_desc, description), microchip_number = r.chip,
            landmark_description = coalesce(landmark_description, r.landmark, '신고 위치 주변')
        WHERE found_report_seq = (SELECT found_report_seq FROM found_reports WHERE description = r.old_desc
                                  ORDER BY found_report_seq LIMIT 1)
        RETURNING found_report_seq INTO v_id;

        CONTINUE WHEN v_id IS NULL OR EXISTS (SELECT 1 FROM found_report_images WHERE found_report_seq = v_id);
        v_img := pg_temp.seed_image('images/seed/dev/found-' || lpad(r.n::text, 2, '0') || '.jpg', 'image/jpeg',
                                    (SELECT user_seq FROM found_reports WHERE found_report_seq = v_id));
        INSERT INTO found_report_images (found_report_seq, image_seq, display_order) VALUES (v_id, v_img, 1);
    END LOOP;
END
$dev$;
