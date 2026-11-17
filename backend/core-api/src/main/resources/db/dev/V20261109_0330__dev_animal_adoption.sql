-- S4(보호동물·입양) 개발용 더미. local·dev 프로필에서만 적용(db/dev)
-- 보호동물 15건(PROTECTING 10[입양 가능 6·불가 4]·ADOPTED 3·RETURNED 2, DONE 구조 연계 3건), 반환 2건, 입양 신청 8건
-- 기대는 것: 보호소 1~5·담당 SHELTER 계정(V2), 동물 조합 시드, 더미 USER 110~114(S1). 다른 세션의 더미 행은 참조하지 않는다
-- 조합은 품종 이름에 묶이지 않게 animal_combinations 순번으로 고른다. PK는 RETURNING으로 받는다
DO $$
DECLARE
    v_combo_count INTEGER;
    v_rescue      INTEGER[] := '{}';
    v_animal      INTEGER[] := '{}';
    v_id          INTEGER;
    v_app         BIGINT;
    v_ended       TIMESTAMPTZ;
    v_shelter_usr INTEGER;
    r             RECORD;
BEGIN
    SELECT count(*) INTO v_combo_count FROM animal_combinations;

    -- 완료(DONE) 구조 3건: 보호소 1~3
    FOR r IN SELECT * FROM (VALUES
        (1, 'WANDERING',       '대구광역시 중구 동성로 12',   '2층 계단 아래'),
        (2, 'INJURY_ILLNESS',  '경상북도 구미시 인동 45',     NULL),
        (3, 'DANGEROUS_PLACE', '대구광역시 북구 침산로 101', '도로 중앙 분리대')
    ) AS t(shelter, reason, address, detail)
    LOOP
        INSERT INTO rescue_records (shelter_seq, status, completed_dttm, rescued_dttm, rescue_reason_code, rescue_address,
                                    rescue_address_detail, rescue_memo, reg_usr_seq, reg_dttm)
        VALUES (r.shelter, 'DONE', now() - make_interval(days => 20 - r.shelter), now() - make_interval(days => 21 - r.shelter),
                r.reason, r.address, r.detail, '더미 구조 기록', (SELECT user_seq FROM shelter_members WHERE shelter_seq = r.shelter),
                now() - make_interval(days => 21 - r.shelter))
        RETURNING rescue_seq INTO v_id;
        v_rescue := v_rescue || v_id;
    END LOOP;

    -- 보호동물 15건. n은 배열 순번(아래 신청서가 v_animal[n]으로 참조)
    FOR r IN SELECT * FROM (VALUES
        (1,  1, 'MALE',    '2살 추정', TRUE,  '4kg',   'PROTECTING', 'AVAILABLE',   1,    3,  '사람을 잘 따르고 산책을 좋아함'),
        (2,  1, 'FEMALE',  '1살 추정', NULL,  '3kg',   'PROTECTING', 'AVAILABLE',   NULL, 5,  '겁이 많지만 간식에 금방 마음을 엶'),
        (3,  2, 'UNKNOWN', '3개월',    NULL,  '1.2kg', 'PROTECTING', 'AVAILABLE',   2,    7,  '다리 치료 후 회복 중'),
        (4,  2, 'FEMALE',  '5살 추정', TRUE,  '6kg',   'PROTECTING', 'AVAILABLE',   NULL, 9,  '조용하고 얌전함'),
        (5,  3, 'MALE',    '3살 추정', FALSE, '12kg',  'PROTECTING', 'AVAILABLE',   3,    11, '활동량이 많아 넓은 공간 필요'),
        (6,  3, 'FEMALE',  '2살 추정', TRUE,  '3.5kg', 'PROTECTING', 'AVAILABLE',   NULL, 13, '다른 동물과 잘 지냄'),
        (7,  4, 'MALE',    '7살 추정', TRUE,  '8kg',   'PROTECTING', 'UNAVAILABLE', NULL, 15, '노령으로 정기 검진 필요'),
        (8,  4, 'UNKNOWN', '1살 추정', NULL,  NULL,    'PROTECTING', 'UNAVAILABLE', NULL, 2,  '입소 직후 기초 검진 대기'),
        (9,  5, 'FEMALE',  '4살 추정', FALSE, '5kg',   'PROTECTING', 'UNAVAILABLE', NULL, 4,  '피부 치료 중'),
        (10, 5, 'MALE',    '6개월',    NULL,  '2kg',   'PROTECTING', 'UNAVAILABLE', NULL, 6,  '예방접종 진행 중'),
        (11, 1, 'FEMALE',  '2살 추정', TRUE,  '4kg',   'ADOPTED',    'ADOPTED',     NULL, 30, '신청서로 입양 완료'),
        (12, 2, 'MALE',    '3살 추정', TRUE,  '7kg',   'ADOPTED',    'ADOPTED',     NULL, 35, '보호소 방문 입양'),
        (13, 3, 'FEMALE',  '1살 추정', TRUE,  '3kg',   'ADOPTED',    'ADOPTED',     NULL, 40, '보호소 방문 입양'),
        (14, 4, 'MALE',    '4살 추정', TRUE,  '9kg',   'RETURNED',   'UNAVAILABLE', NULL, 25, '보호자에게 반환'),
        (15, 5, 'UNKNOWN', '2살 추정', NULL,  '5kg',   'RETURNED',   'UNAVAILABLE', NULL, 28, '보호자에게 반환')
    ) AS t(n, shelter, gender, age, neutered, weight, pstatus, astatus, rescue_idx, days_ago, descr)
    LOOP
        v_shelter_usr := (SELECT user_seq FROM shelter_members WHERE shelter_seq = r.shelter);
        v_ended := CASE WHEN r.pstatus = 'PROTECTING' THEN NULL ELSE now() - make_interval(days => r.days_ago - 7) END;

        INSERT INTO shelter_animals (shelter_seq, rescue_seq, combination_seq, gender, age, is_neutered, weight, description,
                                     health_check, vaccination_status, notice_start_date, notice_end_date,
                                     protection_status, adoption_status, end_reason, protection_ended_dttm,
                                     adoption_reason, reg_usr_seq, reg_dttm)
        VALUES (r.shelter,
                CASE WHEN r.rescue_idx IS NULL THEN NULL ELSE v_rescue[r.rescue_idx] END,
                (SELECT combination_seq FROM animal_combinations ORDER BY combination_seq
                 OFFSET (r.n * 7) % GREATEST(v_combo_count, 1) LIMIT 1),
                r.gender, r.age, r.neutered, r.weight, r.descr, '기초 검진 완료', '1차 예방접종 완료',
                (now() - make_interval(days => r.days_ago))::date, (now() - make_interval(days => r.days_ago))::date + 10,
                r.pstatus, r.astatus,
                CASE WHEN r.pstatus = 'PROTECTING' THEN NULL ELSE r.pstatus END,
                v_ended,
                CASE WHEN r.n IN (12, 13) THEN '보호소 방문 상담 후 직접 인계' END,
                v_shelter_usr, now() - make_interval(days => r.days_ago))
        RETURNING animal_seq INTO v_id;
        v_animal := v_animal || v_id;

        IF r.pstatus = 'RETURNED' THEN
            INSERT INTO animal_returns (animal_seq, recipient_name, recipient_phone, reg_usr_seq, reg_dttm)
            VALUES (v_id, '반환보호자' || r.n, '0109000' || lpad(r.n::text, 4, '0'), v_shelter_usr, v_ended);
        END IF;
    END LOOP;

    -- 입양 신청 8건(회원 110~114). 활성(DRAFT/SUBMITTED/APPROVED)은 회원·동물당 1건
    -- (user, animal 순번, status, 심사 여부)
    FOR r IN SELECT * FROM (VALUES
        (1, 110, 1,  'SUBMITTED'),
        (2, 111, 1,  'APPROVED'),
        (3, 112, 1,  'DRAFT'),
        (4, 113, 3,  'SUBMITTED'),
        (5, 114, 3,  'REJECTED'),
        (6, 110, 2,  'CANCELED'),
        (7, 111, 11, 'ADOPTED'),
        (8, 112, 11, 'REJECTED')
    ) AS t(k, usr, animal_idx, status)
    LOOP
        INSERT INTO adoption_applications (user_seq, animal_seq, shelter_seq, answers_jsonb, care_address, care_address_detail,
                                           email, visit_hope_date, applicant_name, applicant_birth_date, applicant_phone,
                                           status, submitted_dttm, reviewed_dttm, reviewed_usr_seq, rejection_reason, reg_dttm)
        SELECT r.usr, a.animal_seq, a.shelter_seq,
               CASE WHEN r.status = 'DRAFT' THEN '{"1": "아파트"}'::jsonb
                    ELSE '{"1": "아파트", "2": "가족 모두 동의했습니다."}'::jsonb END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE '대구광역시 수성구 달구벌대로 ' || (100 + r.k) END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE r.k || '0' || r.k || '호' END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE 'dummy' || r.usr || '@example.com' END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE current_date + 7 END,
               -- 제출 시점 스냅샷(DRAFT는 비움)
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE u.name END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE u.birth_date END,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE u.phone END,
               r.status,
               CASE WHEN r.status = 'DRAFT' THEN NULL ELSE a.reg_dttm + interval '1 day' END,
               CASE WHEN r.status IN ('APPROVED', 'REJECTED', 'ADOPTED') THEN a.reg_dttm + interval '2 day' END,
               -- 자동 거절(k=8)은 처리자 없음
               CASE WHEN r.status IN ('APPROVED', 'ADOPTED') OR r.k = 5 THEN a.reg_usr_seq END,
               CASE WHEN r.k = 5 THEN '현재 보호 환경이 입양 조건에 부합하지 않습니다.'
                    WHEN r.k = 8 THEN '다른 신청자의 입양이 확정되어 입양 신청이 자동으로 거절되었습니다.' END,
               a.reg_dttm + interval '1 day'
        FROM shelter_animals a, users u
        WHERE a.animal_seq = v_animal[r.animal_idx] AND u.user_seq = r.usr
        RETURNING application_seq INTO v_app;

        -- 12.11로 채택한 신청서를 동물에 연결
        IF r.status = 'ADOPTED' THEN
            UPDATE shelter_animals SET completed_application_seq = v_app WHERE animal_seq = v_animal[r.animal_idx];
        END IF;
    END LOOP;
END $$;
