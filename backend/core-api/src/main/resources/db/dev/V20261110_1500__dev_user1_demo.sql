-- 시연용 회원 1번 데이터(374). local·dev에서만 적용된다
-- dev의 회원 1번은 실제 카카오 로그인 계정이다. 1번이 USER일 때만 넣는다(로컬 새 DB는 1번이 보호소 계정이라 건너뜀)
-- 보호소 계정은 번호가 환경마다 달라 shelter_members로 찾는다. 사진 파일은 S3 images/seed/dev/user1-*.jpg
-- 이야기: 반려동물 보리(진행 중 실종)·별(예전 실종, 찾음). 보리·별 사진을 실종 신고에 같은 이미지로 재사용(API 4장 Q407)
DO $dev$
DECLARE
    me          CONSTANT INTEGER := 1;
    v_phone     VARCHAR;
    v_name      VARCHAR;
    v_birth     DATE;
    gumi_usr    INTEGER;
    img_profile BIGINT;
    img_a1      BIGINT;
    img_a2      BIGINT;
    img_b1      BIGINT;
    img_b2      BIGINT;
    img_found   BIGINT;
    v_pet       INTEGER;
    v_missing   INTEGER;
    v_found     INTEGER;
    v_root      BIGINT;
    v_my_cmt    BIGINT;
    v_reply     BIGINT;
    v_other_mr  INTEGER;
    v_match     INTEGER;
    v_app_ok    BIGINT;
    v_breed     VARCHAR;
    v_room      BIGINT;
    i           INTEGER;
    lines       TEXT[];
    r           RECORD;
BEGIN
    SELECT phone, name, birth_date INTO v_phone, v_name, v_birth
    FROM users WHERE user_seq = me AND role = 'USER' AND is_deleted = FALSE;
    IF NOT FOUND THEN
        RAISE NOTICE '회원 1번이 USER가 아니어서 시연 데이터를 건너뜀';
        RETURN;
    END IF;
    gumi_usr := (SELECT user_seq FROM shelter_members WHERE shelter_seq = 1);

    -- ===== 사진(업로더 = 회원 1번이라 앱에서 수정·재사용 가능) =====
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-profile.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_profile;
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-pet-a-1.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_a1;
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-pet-a-2.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_a2;
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-pet-b-1.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_b1;
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-pet-b-2.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_b2;
    INSERT INTO images (image_key, mime_type, reg_usr_seq) VALUES ('images/seed/dev/user1-found-1.jpg', 'image/jpeg', me) RETURNING image_seq INTO img_found;

    UPDATE users SET profile_image_seq = img_profile WHERE user_seq = me;

    -- ===== 반려동물 2 + 실종 신고 2(보리 진행 중, 별 종료). 조합은 품종·색 이름으로, 없으면 그 품종 아무 색 → 아무 조합 =====
    FOR r IN SELECT * FROM (VALUES
        ('A', '보리', '말티즈',  '흰색', '15살', '3.2kg', '나이가 많아 천천히 걷고, 낯선 사람을 조금 경계해요.',
         '경상북도 구미시 인동가산로 9', 36.1043, 128.4199, INTERVAL '2 days', 'ACTIVE',
         '산책 중 목줄이 풀려 인동 쪽으로 달아났어요. 노령견이라 멀리 못 갔을 거예요. 분홍색 옷을 입고 있어요.'),
        ('B', '별',   '토이 푸들', '갈색', '12살', '4.1kg', '사람을 좋아하고 간식을 보면 앉아서 기다려요.',
         '경상북도 구미시 송정대로 55', 36.1195, 128.3446, INTERVAL '60 days', 'CLOSED',
         '현관문이 열린 사이 나갔어요. 이웃분 제보로 이틀 만에 찾았습니다.')
    ) AS t(k, name, breed, color, age, weight, descr, address, lat, lng, ago, status, mdescr)
    LOOP
        INSERT INTO pets (user_seq, name, combination_seq, gender, age, weight, is_neutered, description, reg_dttm)
        VALUES (me, r.name,
                coalesce((SELECT c.combination_seq FROM animal_combinations c
                          JOIN animal_breeds b ON b.breed_seq = c.breed_seq
                          JOIN animal_colors co ON co.color_seq = c.color_seq
                          WHERE b.breed_name = r.breed ORDER BY (co.color_name = r.color) DESC, c.combination_seq LIMIT 1),
                         (SELECT min(combination_seq) FROM animal_combinations)),
                'FEMALE', r.age, r.weight, TRUE, r.descr, now() - INTERVAL '90 days')
        RETURNING pet_seq INTO v_pet;
        INSERT INTO pet_images (pet_seq, image_seq, display_order)
        VALUES (v_pet, CASE r.k WHEN 'A' THEN img_a1 ELSE img_b1 END, 1),
               (v_pet, CASE r.k WHEN 'A' THEN img_a2 ELSE img_b2 END, 2);

        -- 실종 신고는 반려동물과 연결 컬럼이 없다(Q466). 정보를 그대로 옮겨 적는다
        INSERT INTO missing_reports (user_seq, combination_seq, gender, age, weight, is_neutered, description, address,
                                     latitude, longitude, occurred_dttm, district_code, status, contact_phone, is_phone_public,
                                     reg_dttm, mod_usr_seq, mod_dttm)
        SELECT me, p.combination_seq, p.gender, p.age, p.weight, p.is_neutered, r.mdescr, r.address,
               r.lat, r.lng, now() - r.ago, '47190', r.status, v_phone, TRUE,
               now() - r.ago + INTERVAL '1 hour',
               CASE WHEN r.status = 'CLOSED' THEN me END,
               CASE WHEN r.status = 'CLOSED' THEN now() - r.ago + INTERVAL '2 days' END
        FROM pets p WHERE p.pet_seq = v_pet
        RETURNING missing_report_seq INTO i;
        INSERT INTO missing_report_images (missing_report_seq, image_seq, display_order)
        VALUES (i, CASE r.k WHEN 'A' THEN img_a1 ELSE img_b1 END, 1),
               (i, CASE r.k WHEN 'A' THEN img_a2 ELSE img_b2 END, 2);
        IF r.k = 'A' THEN v_missing := i; END IF;
    END LOOP;

    -- ===== 발견 신고 1(구미역, 구미시 보호소 배정) =====
    INSERT INTO found_reports (user_seq, shelter_seq, combination_seq, gender, age, description, reporter_phone, is_phone_public,
                               landmark_description, address, latitude, longitude, occurred_dttm, district_code, reg_dttm)
    VALUES (me, 1,
            coalesce((SELECT c.combination_seq FROM animal_combinations c JOIN animal_breeds b ON b.breed_seq = c.breed_seq
                      WHERE b.breed_name = '믹스견' ORDER BY c.combination_seq LIMIT 1),
                     (SELECT min(combination_seq) FROM animal_combinations)),
            'UNKNOWN', '2살 추정', '목줄 없이 역 앞을 돌아다니고 있었어요. 사람을 피하지는 않아요.', v_phone, FALSE,
            '구미역 1번 출구 앞 버스정류장', '경상북도 구미시 구미중앙로 76', 36.1284, 128.3311,
            now() - INTERVAL '1 day', '47190', now() - INTERVAL '23 hours')
    RETURNING found_report_seq INTO v_found;
    INSERT INTO found_report_images (found_report_seq, image_seq, display_order) VALUES (v_found, img_found, 1);
    INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, reg_dttm)
    VALUES (gumi_usr, 'SHELTER', '구미시에서 발견 신고가 접수되었어요.', 'FOUND', v_found, now() - INTERVAL '23 hours');

    -- ===== 보리 실종 신고에 달린 댓글 → 회원 1번에게 COMMENT 알림 =====
    FOR r IN SELECT * FROM (VALUES
        (1, 101,  '어제 저녁 인동시장 근처에서 분홍 옷 입은 하얀 강아지 봤어요!', INTERVAL '30 hours', TRUE),
        (2, 102,  '전단지 만드시면 저희 아파트 게시판에도 붙여 드릴게요.',        INTERVAL '20 hours', TRUE),
        (3, NULL, '구미시 동물보호센터입니다. 비슷한 아이가 입소하면 바로 연락드리겠습니다.', INTERVAL '8 hours', FALSE),
        (4, 103,  '노령견이라 걱정되네요. 꼭 찾으시길 바랄게요.',                  INTERVAL '2 hours', FALSE)
    ) AS t(k, usr, body, ago, is_read)
    LOOP
        INSERT INTO missing_report_comments (missing_report_seq, user_seq, body, reg_dttm)
        VALUES (v_missing, coalesce(r.usr, gumi_usr), r.body, now() - r.ago)
        RETURNING missing_report_comment_seq INTO v_reply;
        IF r.k = 1 THEN v_root := v_reply; END IF;
        INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, comment_seq,
                                   is_read, read_dttm, reg_dttm)
        VALUES (me, 'COMMENT', r.body, 'MISSING', v_missing, v_reply,
                r.is_read, CASE WHEN r.is_read THEN now() - r.ago + INTERVAL '1 hour' END, now() - r.ago);
    END LOOP;
    -- 첫 댓글에 회원 1번 답글 → 루트 작성자(101)에게 COMMENT_REPLY
    INSERT INTO missing_report_comments (missing_report_seq, user_seq, parent_comment_seq, body, reg_dttm)
    VALUES (v_missing, me, v_root, '감사합니다! 오늘 바로 가 볼게요.', now() - INTERVAL '29 hours')
    RETURNING missing_report_comment_seq INTO v_reply;
    INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, comment_seq, parent_comment_seq, reg_dttm)
    VALUES (101, 'COMMENT_REPLY', '감사합니다! 오늘 바로 가 볼게요.', 'MISSING', v_missing, v_reply, v_root, now() - INTERVAL '29 hours');

    -- ===== 회원 1번이 다른 실종 신고에 쓴 댓글과 받은 답글 =====
    SELECT missing_report_seq, user_seq INTO v_other_mr, i FROM missing_reports
    WHERE description = 'S5 더미 실종 1' ORDER BY missing_report_seq LIMIT 1;
    IF v_other_mr IS NOT NULL THEN
        INSERT INTO missing_report_comments (missing_report_seq, user_seq, body, reg_dttm)
        VALUES (v_other_mr, me, '김천역 근처에서 비슷한 말티즈를 본 것 같아요.', now() - INTERVAL '26 hours')
        RETURNING missing_report_comment_seq INTO v_my_cmt;
        INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, comment_seq, reg_dttm)
        VALUES (i, 'COMMENT', '김천역 근처에서 비슷한 말티즈를 본 것 같아요.', 'MISSING', v_other_mr, v_my_cmt, now() - INTERVAL '26 hours');

        INSERT INTO missing_report_comments (missing_report_seq, user_seq, parent_comment_seq, body, reg_dttm)
        VALUES (v_other_mr, i, v_my_cmt, '정말요? 몇 시쯤 보셨는지 알려주실 수 있나요?', now() - INTERVAL '25 hours')
        RETURNING missing_report_comment_seq INTO v_reply;
        INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, comment_seq, parent_comment_seq,
                                   is_read, read_dttm, reg_dttm)
        VALUES (me, 'COMMENT_REPLY', '정말요? 몇 시쯤 보셨는지 알려주실 수 있나요?', 'MISSING', v_other_mr, v_reply, v_my_cmt,
                TRUE, now() - INTERVAL '24 hours', now() - INTERVAL '25 hours');
    END IF;

    -- ===== AI 매칭 알림(보리 실종 × 말티즈 발견 신고) =====
    v_match := (SELECT found_report_seq FROM found_reports WHERE description = 'S5 더미 발견 1' ORDER BY found_report_seq LIMIT 1);
    IF v_match IS NOT NULL THEN
        INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, missing_report_seq, reg_dttm)
        VALUES (me, 'AI_MATCH', '경상북도 김천시에서 목격제보가 올라왔어요. 확인해보세요.', 'FOUND', v_match, v_missing,
                now() - INTERVAL '5 hours');
    END IF;

    -- ===== 입양 신청 3건(구미시 보호소): 심사 중, 승인, 자동 거절 =====
    FOR r IN SELECT * FROM (VALUES
        ('SUBMITTED', 'AVAILABLE', 0, INTERVAL '3 days'),
        ('APPROVED',  'AVAILABLE', 1, INTERVAL '6 days'),
        ('REJECTED',  'ADOPTED',   0, INTERVAL '12 days')
    ) AS t(status, astatus, nth, ago)
    LOOP
        INSERT INTO adoption_applications (user_seq, animal_seq, shelter_seq, answers_jsonb, care_address, care_address_detail,
                                           email, visit_hope_date, applicant_name, applicant_birth_date, applicant_phone,
                                           status, submitted_dttm, reviewed_dttm, reviewed_usr_seq, rejection_reason, reg_dttm)
        SELECT me, a.animal_seq, a.shelter_seq,
               '{"1": "아파트", "2": "자가", "3": "허용", "4": "있음", "5": "모두 동의", "6": "있음",
                 "7": "15살 말티즈(보리), 12살 토이 푸들(별), 둘 다 중성화 완료", "8": "있음", "9": "4~8시간",
                 "10": "본인", "11": "없음", "12": "부모님 댁에 맡길 수 있습니다.",
                 "13": "노령견을 오래 돌본 경험을 살려 보호소 아이에게도 가족을 만들어 주고 싶습니다.",
                 "14": "10~30만 원", "15": "가족과 상의해 끝까지 책임지겠습니다.", "16": "동의", "17": "미이수"}'::jsonb,
               '경상북도 구미시 인동가산로 9', '101동 1001호', 'demo-user1@example.com', (now() - r.ago)::date + 7,
               v_name, v_birth, v_phone,
               r.status, now() - r.ago,
               CASE WHEN r.status <> 'SUBMITTED' THEN now() - r.ago + INTERVAL '2 days' END,
               CASE WHEN r.status = 'APPROVED' THEN gumi_usr END,
               CASE WHEN r.status = 'REJECTED' THEN '다른 신청자의 입양이 확정되어 입양 신청이 자동으로 거절되었습니다.' END,
               now() - r.ago
        FROM shelter_animals a
        WHERE a.shelter_seq = 1 AND a.adoption_status = r.astatus AND a.is_deleted = FALSE
        ORDER BY a.animal_seq OFFSET r.nth LIMIT 1
        RETURNING application_seq INTO v_app_ok;

        CONTINUE WHEN v_app_ok IS NULL;
        -- 알림 문구는 AdoptionNotices와 같은 형식
        SELECT b.breed_name INTO v_breed FROM adoption_applications ap
        JOIN shelter_animals a ON a.animal_seq = ap.animal_seq
        JOIN animal_combinations c ON c.combination_seq = a.combination_seq
        JOIN animal_breeds b ON b.breed_seq = c.breed_seq
        WHERE ap.application_seq = v_app_ok;
        IF r.status = 'SUBMITTED' THEN
            INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, reg_dttm)
            VALUES (gumi_usr, 'ADOPTION_SUBMITTED', v_breed || ' 입양 신청서가 제출되었어요.', 'ADOPTION', v_app_ok, now() - r.ago);
        ELSE
            INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq,
                                       is_read, read_dttm, reg_dttm)
            VALUES (me, CASE r.status WHEN 'APPROVED' THEN 'ADOPTION_APPROVED' ELSE 'ADOPTION_REJECTED' END,
                    '구미시 동물보호센터 ' || v_breed || ' 입양 신청 결과를 확인해보세요.', 'ADOPTION', v_app_ok,
                    r.status = 'REJECTED', CASE WHEN r.status = 'REJECTED' THEN now() - r.ago + INTERVAL '3 days' END,
                    now() - r.ago + INTERVAL '2 days');
        END IF;
    END LOOP;

    -- ===== 북마크 =====
    INSERT INTO bookmarks (user_seq, target_type, target_seq)
    SELECT me, 'SHELTER_ANIMAL', a.animal_seq FROM shelter_animals a
    WHERE a.shelter_seq = 1 AND a.adoption_status = 'AVAILABLE' AND a.is_deleted = FALSE;
    INSERT INTO bookmarks (user_seq, target_type, target_seq)
    SELECT me, 'MISSING', missing_report_seq FROM missing_reports WHERE description IN ('S5 더미 실종 1', 'S5 더미 실종 2');
    IF v_match IS NOT NULL THEN
        INSERT INTO bookmarks (user_seq, target_type, target_seq) VALUES (me, 'FOUND', v_match);
    END IF;

    -- ===== 채팅 1) 회원 101 ↔ 회원 1: 보리 목격 제보. 회원 1은 마지막 2건 미읽음 =====
    lines := ARRAY[
        '안녕하세요, 보리 실종 신고 보고 연락드려요.',
        '안녕하세요! 혹시 보셨나요?',
        '어제 저녁 7시쯤 인동시장 입구에서 분홍 옷 입은 하얀 강아지를 봤어요.',
        '맞아요, 분홍 옷이요! 어느 방향으로 갔는지 기억나세요?',
        '시장 안쪽 골목으로 들어갔어요. 너무 빨라서 잡지는 못했어요.',
        '정말 감사합니다. 지금 그쪽으로 가 볼게요.',
        '오늘 퇴근길에 저도 한 번 더 둘러볼게요.',
        '혹시 다시 보시면 사진 한 장만 부탁드려도 될까요?'
    ];
    INSERT INTO chat_rooms (room_type, user_a_seq, user_b_seq, reg_dttm)
    VALUES ('USER', me, 101, now() - INTERVAL '28 hours') RETURNING chat_room_seq INTO v_room;
    INSERT INTO chat_participants (chat_room_seq, participant_type, participant_user_seq)
    VALUES (v_room, 'USER', me), (v_room, 'USER', 101);
    INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, shared_target_type, shared_target_seq, reg_dttm)
    VALUES (v_room, 101, 'SHARED_POST', 'MISSING', v_missing, now() - INTERVAL '28 hours');
    FOR i IN 1..array_length(lines, 1) LOOP
        INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
        VALUES (v_room, CASE WHEN i IN (1, 3, 5, 7) THEN 101 ELSE me END, 'TEXT', lines[i],
                now() - INTERVAL '28 hours' + i * INTERVAL '4 minutes');
    END LOOP;
    INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
    VALUES (v_room, 101, 'TEXT', '네, 보이면 바로 찍어서 보내드릴게요!', now() - INTERVAL '3 hours'),
           (v_room, 101, 'TEXT', '방금 시장 뒤편에서 비슷한 아이를 또 봤어요.', now() - INTERVAL '1 hour');
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT max(chat_message_seq) FROM chat_messages WHERE chat_room_seq = v_room)
    WHERE p.chat_room_seq = v_room AND p.participant_user_seq = 101;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT chat_message_seq FROM chat_messages WHERE chat_room_seq = v_room ORDER BY chat_message_seq DESC OFFSET 2 LIMIT 1)
    WHERE p.chat_room_seq = v_room AND p.participant_user_seq = me;

    -- ===== 채팅 2) 회원 1 ↔ 구미시 보호소: 승인된 입양 신청 방문 일정. 모두 읽음 =====
    lines := ARRAY[
        '안녕하세요, 입양 신청 승인 안내 받고 연락드렸어요.',
        '안녕하세요, 구미시 동물보호센터입니다. 방문 상담 날짜를 정하려고 해요.',
        '이번 주 토요일 오전 괜찮을까요?',
        '네, 토요일 오전 10시로 예약해 둘게요. 신분증 지참 부탁드립니다.',
        '감사합니다. 그때 뵙겠습니다!'
    ];
    INSERT INTO chat_rooms (room_type, user_a_seq, shelter_seq, reg_dttm)
    VALUES ('SHELTER', me, 1, now() - INTERVAL '4 days') RETURNING chat_room_seq INTO v_room;
    INSERT INTO chat_participants (chat_room_seq, participant_type, participant_user_seq, participant_shelter_seq)
    VALUES (v_room, 'USER', me, NULL), (v_room, 'SHELTER', NULL, 1);
    FOR i IN 1..array_length(lines, 1) LOOP
        INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
        VALUES (v_room, CASE WHEN i % 2 = 0 THEN gumi_usr ELSE me END, 'TEXT', lines[i],
                now() - INTERVAL '4 days' + i * INTERVAL '10 minutes');
    END LOOP;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT max(chat_message_seq) FROM chat_messages WHERE chat_room_seq = v_room)
    WHERE p.chat_room_seq = v_room;

-- 시드 사이에 회원 1번이 앱에서 같은 행(진행 중 실종 신고·채팅방 등)을 먼저 만들었으면 통째로 건너뛴다.
-- 실패시키면 core-api가 기동하지 못한다(Flyway 실패 → CrashLoop)
EXCEPTION WHEN unique_violation THEN
    RAISE WARNING '회원 1번 시연 데이터를 건너뜀(이미 있는 데이터와 충돌): %', SQLERRM;
END $dev$;
