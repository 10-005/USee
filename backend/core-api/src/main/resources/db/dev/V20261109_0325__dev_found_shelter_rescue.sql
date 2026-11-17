-- 개발·시연용 더미: 발견 신고 10건(보호소별 2건), 구조 5건(DONE 3·PROCESSING 2). local·dev 프로필에서만 적용
-- 기대는 것: S1 더미 회원 user_seq 105~109(V20261109_0010), V2 보호소 5곳·SHELTER 계정, S1 카탈로그 시드
-- 보호소는 district_code로, 조합은 품종 이름으로 찾고(없으면 첫 조합), 새 행 PK는 RETURNING으로 받는다
DO $dev$
DECLARE
    r          RECORD;
    v_shelter  INTEGER;
    v_combo    INTEGER;
    v_found    INTEGER;
    v_member   INTEGER;
    v_rescued  TIMESTAMPTZ;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            -- (순번, 작성자, 시군구, 품종, 성별, 나이, 몸무게, 중성화, 특징, 기준점, 주소, 위도, 경도, 며칠 전, 상태, 구조)
            (1,  105, '47190', '말티즈',       'MALE',    '3살',  '약 4kg',  TRUE,  '파란 목줄, 겁이 많음',       '인동시장 입구',     '경상북도 구미시 인동동',   36.1069, 128.4189, 2,  'ACTIVE', 'DONE'),
            (2,  106, '47190', '코리안 숏헤어', 'UNKNOWN', NULL,   NULL,      NULL,  '흰색 바탕에 회색 얼룩',      '송산도서관 뒤편',   '경상북도 구미시 송정동',   36.1196, 128.3446, 5,  'ACTIVE', NULL),
            (3,  107, '47150', '진돗개',       'FEMALE',  '5살',  '약 15kg', FALSE, '꼬리가 말려 있음',           '김천역 광장',       '경상북도 김천시 평화동',   36.1238, 128.1137, 1,  'ACTIVE', 'PROCESSING'),
            (4,  108, '47150', '푸들',         'MALE',    '2살',  '약 3kg',  NULL,  '갈색, 미용한 지 얼마 안 됨', '직지사 주차장',     '경상북도 김천시 대항면',   36.1186, 128.0010, 12, 'CLOSED', NULL),
            (5,  109, '47850', '믹스견',       'UNKNOWN', '1살',  NULL,      NULL,  '목에 빨간 손수건',           '석적읍 행정복지센터', '경상북도 칠곡군 석적읍',   36.0749, 128.4045, 3,  'ACTIVE', 'DONE'),
            (6,  105, '47850', '코리안 숏헤어', 'FEMALE',  NULL,   '약 3kg',  TRUE,  '치즈색 털',                  '왜관역 앞',         '경상북도 칠곡군 왜관읍',   35.9925, 128.3976, 20, 'ACTIVE', NULL),
            (7,  106, '47250', '시바견',       'MALE',    '4살',  '약 9kg',  TRUE,  '빨간 하네스 착용',           '상주시청 앞',       '경상북도 상주시 남성동',   36.4110, 128.1590, 4,  'ACTIVE', 'DONE'),
            (8,  107, '47250', '말티즈',       'FEMALE',  '7살',  '약 3kg',  NULL,  '눈물 자국이 심함',           '경천섬 공원',       '경상북도 상주시 중동면',   36.4280, 128.2620, 40, 'CLOSED', NULL),
            (9,  108, '47840', '믹스견',       'UNKNOWN', NULL,   '약 10kg', NULL,  '검은색 단모, 다리를 절음',   '성주참외시장 근처', '경상북도 성주군 성주읍',   35.9190, 128.2830, 6,  'ACTIVE', 'PROCESSING'),
            (10, 109, '47840', '포메라니안',   'MALE',    '1살',  '약 2kg',  FALSE, '주황색 털, 사람을 잘 따름',  '성밖숲 입구',       '경상북도 성주군 성주읍',   35.9230, 128.2780, 9,  'ACTIVE', NULL)
        ) AS t (n, user_seq, district_code, breed_name, gender, age, weight, is_neutered, description, landmark,
                address, latitude, longitude, days_ago, status, rescue_status)
        ORDER BY n
    LOOP
        SELECT shelter_seq INTO v_shelter FROM shelters WHERE district_code = r.district_code AND is_deleted = FALSE ORDER BY shelter_seq LIMIT 1;

        SELECT c.combination_seq INTO v_combo
        FROM animal_combinations c JOIN animal_breeds b ON b.breed_seq = c.breed_seq
        WHERE b.breed_name = r.breed_name ORDER BY c.combination_seq LIMIT 1;
        IF v_combo IS NULL THEN
            SELECT min(combination_seq) INTO v_combo FROM animal_combinations;
        END IF;

        INSERT INTO found_reports (
            user_seq, shelter_seq, combination_seq, gender, age, weight, is_neutered, description,
            reporter_phone, is_phone_public, landmark_description, address, latitude, longitude,
            occurred_dttm, district_code, status, closed_reason, closed_dttm
        ) VALUES (
            r.user_seq, v_shelter, v_combo, r.gender, r.age, r.weight, r.is_neutered, r.description,
            '0100000' || r.user_seq, r.n % 2 = 0, r.landmark, r.address, r.latitude, r.longitude,
            now() - make_interval(days => r.days_ago), r.district_code, r.status,
            CASE WHEN r.status = 'CLOSED' THEN '보호자가 직접 찾아감' END,
            CASE WHEN r.status = 'CLOSED' THEN now() - make_interval(days => r.days_ago - 1) END
        ) RETURNING found_report_seq INTO v_found;

        -- 구조는 담당 보호소가 접수(신고 1~2일 뒤). DONE은 완료 시각이 필요하다(ck_rescue_completion_time)
        IF r.rescue_status IS NOT NULL THEN
            SELECT user_seq INTO v_member FROM shelter_members WHERE shelter_seq = v_shelter;
            v_rescued := now() - make_interval(days => greatest(r.days_ago - 1, 0));
            INSERT INTO rescue_records (
                shelter_seq, found_report_seq, status, completed_dttm, rescued_dttm, rescue_reason_code,
                rescue_address, rescue_address_detail, rescue_memo, reg_usr_seq
            ) VALUES (
                v_shelter, v_found, r.rescue_status,
                CASE WHEN r.rescue_status = 'DONE' THEN v_rescued + interval '3 hours' END,
                v_rescued,
                CASE WHEN r.n = 9 THEN 'INJURY_ILLNESS' ELSE 'WANDERING' END,
                r.address, r.landmark,
                CASE WHEN r.rescue_status = 'PROCESSING' THEN '포획틀 설치 예정' END,
                v_member
            );
        END IF;
    END LOOP;
END
$dev$;
