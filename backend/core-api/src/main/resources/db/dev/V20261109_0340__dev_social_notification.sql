-- local·dev 전용 더미(S5: 댓글·북마크·알림·FCM 토큰). 더미 회원 115~119(S1 dev_base)와 시드 보호소 1·2만 기댄다
-- 댓글이 달릴 실종·발견 신고와 북마크할 보호동물은 다른 세션 더미를 참조하지 않고 여기서 직접 만든다(description 'S5 더미 …'로 찾는다)

-- ===== 부모 게시물: 실종 2(115·116), 발견 2(117·118), 보호동물 1(보호소 1) =====
INSERT INTO missing_reports (user_seq, combination_seq, gender, age, description, address, latitude, longitude, occurred_dttm,
                             district_code, contact_phone)
SELECT v.user_seq,
       (SELECT c.combination_seq FROM animal_combinations c JOIN animal_breeds b ON b.breed_seq = c.breed_seq
        WHERE b.breed_name = '말티즈' LIMIT 1),
       v.gender, v.age, v.description, '경상북도 김천시 시청1길 1', 36.1398, 128.1136, now() - v.ago, '47150', '0100000' || v.user_seq
FROM (VALUES (115, 'MALE', '3살', 'S5 더미 실종 1', INTERVAL '3 days'),
             (116, 'FEMALE', '5살', 'S5 더미 실종 2', INTERVAL '1 day')) AS v(user_seq, gender, age, description, ago);

INSERT INTO found_reports (user_seq, shelter_seq, combination_seq, gender, description, reporter_phone, address, latitude, longitude,
                           occurred_dttm, district_code)
SELECT v.user_seq, 1,
       (SELECT c.combination_seq FROM animal_combinations c JOIN animal_breeds b ON b.breed_seq = c.breed_seq
        WHERE b.breed_name = '말티즈' LIMIT 1),
       'UNKNOWN', v.description, '0100000' || v.user_seq, '경상북도 김천시 평화동 100', 36.1205, 128.1130, now() - v.ago, '47150'
FROM (VALUES (117, 'S5 더미 발견 1', INTERVAL '2 days'),
             (118, 'S5 더미 발견 2', INTERVAL '12 hours')) AS v(user_seq, description, ago);

INSERT INTO shelter_animals (shelter_seq, combination_seq, gender, age, description, notice_start_date, notice_end_date, reg_usr_seq)
SELECT 1,
       (SELECT c.combination_seq FROM animal_combinations c JOIN animal_breeds b ON b.breed_seq = c.breed_seq
        WHERE b.breed_name = '말티즈' LIMIT 1),
       'MALE', '2살', 'S5 더미 보호동물 1', CURRENT_DATE, CURRENT_DATE + 10,
       (SELECT user_seq FROM shelter_members WHERE shelter_seq = 1);

-- ===== 댓글 20건: 루트는 본문으로 찾는다(본문이 겹치지 않게). 답글은 같은 게시물의 루트에만(복합 FK). user_seq NULL = 보호소 담당자 =====
INSERT INTO missing_report_comments (missing_report_seq, user_seq, body, is_deleted, reg_dttm)
SELECT r.missing_report_seq, coalesce(v.user_seq, (SELECT user_seq FROM shelter_members WHERE shelter_seq = 1)), v.body, v.deleted, now() - v.ago
FROM (VALUES ('S5 더미 실종 1', 116, '근처 공원에서 비슷한 아이를 봤어요.', FALSE, INTERVAL '50 hours'),
             ('S5 더미 실종 1', 117, '전단지 붙이는 거 도와드릴게요.', FALSE, INTERVAL '40 hours'),
             ('S5 더미 실종 1', NULL, '보호소에도 같은 아이가 있는지 확인해 보세요.', FALSE, INTERVAL '30 hours'),
             ('S5 더미 실종 1', 118, '(삭제된 루트 댓글)', TRUE, INTERVAL '20 hours'),
             ('S5 더미 실종 2', 119, '어제 저녁 시장 앞에서 봤어요.', FALSE, INTERVAL '20 hours'),
             ('S5 더미 실종 2', 115, '빨리 찾으시길 바랄게요.', FALSE, INTERVAL '10 hours'),
             ('S5 더미 실종 2', 117, '목줄 색이 무엇인가요?', FALSE, INTERVAL '5 hours')) AS v(description, user_seq, body, deleted, ago)
JOIN missing_reports r ON r.description = v.description;

INSERT INTO missing_report_comments (missing_report_seq, user_seq, parent_comment_seq, body, reg_dttm)
SELECT p.missing_report_seq, v.user_seq, p.missing_report_comment_seq, v.body, now() - v.ago
FROM (VALUES ('근처 공원에서 비슷한 아이를 봤어요.', 115, '언제쯤 보셨나요?', INTERVAL '49 hours'),
             ('근처 공원에서 비슷한 아이를 봤어요.', 116, '오늘 오후 3시쯤이요.', INTERVAL '48 hours'),
             ('전단지 붙이는 거 도와드릴게요.', 115, '정말 감사합니다!', INTERVAL '39 hours'),
             ('(삭제된 루트 댓글)', 119, '아직 못 찾으셨나요?', INTERVAL '19 hours'),
             ('어제 저녁 시장 앞에서 봤어요.', 116, '어느 시장인지 알려주실 수 있나요?', INTERVAL '19 hours')) AS v(root_body, user_seq, body, ago)
JOIN missing_report_comments p ON p.body = v.root_body AND p.parent_comment_seq IS NULL;

INSERT INTO found_report_comments (found_report_seq, user_seq, body, reg_dttm)
SELECT r.found_report_seq, coalesce(v.user_seq, (SELECT user_seq FROM shelter_members WHERE shelter_seq = 2)), v.body, now() - v.ago
FROM (VALUES ('S5 더미 발견 1', 115, '저희 아이랑 많이 닮았어요. 사진 더 있나요?', INTERVAL '40 hours'),
             ('S5 더미 발견 1', 116, '목걸이가 있었나요?', INTERVAL '30 hours'),
             ('S5 더미 발견 1', NULL, '인근 보호소에서도 확인해 보겠습니다.', INTERVAL '20 hours'),
             ('S5 더미 발견 2', 119, '혹시 귀에 점이 있나요?', INTERVAL '10 hours'),
             ('S5 더미 발견 2', 115, '근처에 사는데 저도 찾아볼게요.', INTERVAL '6 hours')) AS v(description, user_seq, body, ago)
JOIN found_reports r ON r.description = v.description;

INSERT INTO found_report_comments (found_report_seq, user_seq, parent_comment_seq, body, reg_dttm)
SELECT p.found_report_seq, v.user_seq, p.found_report_comment_seq, v.body, now() - v.ago
FROM (VALUES ('저희 아이랑 많이 닮았어요. 사진 더 있나요?', 117, '정면 사진은 없고 옆모습만 있어요.', INTERVAL '39 hours'),
             ('저희 아이랑 많이 닮았어요. 사진 더 있나요?', 115, '그래도 한번 보고 싶어요.', INTERVAL '38 hours'),
             ('혹시 귀에 점이 있나요?', 118, '네, 왼쪽 귀에 있어요.', INTERVAL '9 hours')) AS v(root_body, user_seq, body, ago)
JOIN found_report_comments p ON p.body = v.root_body AND p.parent_comment_seq IS NULL;

-- ===== 알림: 위 댓글에서 17.8 규칙대로 만든다(루트 → 게시물 작성자 COMMENT, 답글 → 루트 작성자 COMMENT_REPLY, 본인 제외) =====
-- 루트 COMMENT 12건 + 답글 COMMENT_REPLY 6건 + AI_MATCH 1건 + SHELTER 2건 = 21건. 짝수 PK는 읽음
INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, comment_seq, parent_comment_seq,
                           is_read, read_dttm, reg_dttm)
SELECT x.recipient, x.type, CASE WHEN x.deleted THEN '삭제된 댓글입니다' ELSE left(x.body, 100) END, x.target_type, x.target_seq,
       x.comment_seq, x.parent_seq, x.comment_seq % 2 = 0, CASE WHEN x.comment_seq % 2 = 0 THEN x.reg_dttm + INTERVAL '1 hour' END,
       x.reg_dttm
FROM (
    SELECT r.user_seq AS recipient, 'COMMENT' AS type, c.body, c.is_deleted AS deleted, 'MISSING' AS target_type,
           r.missing_report_seq::BIGINT AS target_seq, c.missing_report_comment_seq AS comment_seq, NULL::BIGINT AS parent_seq, c.reg_dttm
    FROM missing_report_comments c JOIN missing_reports r ON r.missing_report_seq = c.missing_report_seq
    WHERE r.description LIKE 'S5 더미%' AND c.parent_comment_seq IS NULL AND c.user_seq <> r.user_seq
    UNION ALL
    SELECT p.user_seq, 'COMMENT_REPLY', c.body, c.is_deleted, 'MISSING', c.missing_report_seq, c.missing_report_comment_seq,
           p.missing_report_comment_seq, c.reg_dttm
    FROM missing_report_comments c JOIN missing_report_comments p ON p.missing_report_comment_seq = c.parent_comment_seq
    JOIN missing_reports r ON r.missing_report_seq = c.missing_report_seq
    WHERE r.description LIKE 'S5 더미%' AND c.user_seq <> p.user_seq
    UNION ALL
    SELECT r.user_seq, 'COMMENT', c.body, c.is_deleted, 'FOUND', r.found_report_seq, c.found_report_comment_seq, NULL, c.reg_dttm
    FROM found_report_comments c JOIN found_reports r ON r.found_report_seq = c.found_report_seq
    WHERE r.description LIKE 'S5 더미%' AND c.parent_comment_seq IS NULL AND c.user_seq <> r.user_seq
    UNION ALL
    SELECT p.user_seq, 'COMMENT_REPLY', c.body, c.is_deleted, 'FOUND', c.found_report_seq, c.found_report_comment_seq,
           p.found_report_comment_seq, c.reg_dttm
    FROM found_report_comments c JOIN found_report_comments p ON p.found_report_comment_seq = c.parent_comment_seq
    JOIN found_reports r ON r.found_report_seq = c.found_report_seq
    WHERE r.description LIKE 'S5 더미%' AND c.user_seq <> p.user_seq
) x;

-- AI_MATCH(실종 1 × 발견 1, core-api가 INSERT하는 유형)와 SHELTER(발견 신고 배정 → 보호소 1 계정)
INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, missing_report_seq, reg_dttm)
SELECT 115, 'AI_MATCH', '경상북도 김천시에서 목격제보가 올라왔어요. 확인해보세요.', 'FOUND',
       (SELECT found_report_seq FROM found_reports WHERE description = 'S5 더미 발견 1'),
       (SELECT missing_report_seq FROM missing_reports WHERE description = 'S5 더미 실종 1'), now() - INTERVAL '47 hours';

INSERT INTO notifications (recipient_user_seq, notification_type, message, target_type, target_seq, reg_dttm)
SELECT (SELECT user_seq FROM shelter_members WHERE shelter_seq = 1), 'SHELTER', '김천시에서 발견 신고가 접수되었어요.', 'FOUND',
       r.found_report_seq, r.reg_dttm
FROM found_reports r WHERE r.description LIKE 'S5 더미 발견%';

-- ===== 북마크 10건 =====
INSERT INTO bookmarks (user_seq, target_type, target_seq)
SELECT v.user_seq, v.target_type,
       CASE v.target_type
           WHEN 'MISSING' THEN (SELECT missing_report_seq FROM missing_reports WHERE description = v.description)
           WHEN 'FOUND' THEN (SELECT found_report_seq FROM found_reports WHERE description = v.description)
           ELSE (SELECT animal_seq FROM shelter_animals WHERE description = v.description)
       END
FROM (VALUES (115, 'MISSING', 'S5 더미 실종 2'), (115, 'FOUND', 'S5 더미 발견 1'), (115, 'SHELTER_ANIMAL', 'S5 더미 보호동물 1'),
             (116, 'MISSING', 'S5 더미 실종 1'), (116, 'FOUND', 'S5 더미 발견 2'),
             (117, 'MISSING', 'S5 더미 실종 1'), (117, 'SHELTER_ANIMAL', 'S5 더미 보호동물 1'),
             (118, 'FOUND', 'S5 더미 발견 1'),
             (119, 'MISSING', 'S5 더미 실종 1'), (119, 'SHELTER_ANIMAL', 'S5 더미 보호동물 1')) AS v(user_seq, target_type, description);

-- ===== FCM 토큰(로깅 어댑터 확인용 가짜 값) =====
INSERT INTO device_tokens (user_seq, fcm_token)
SELECT n, 'dev-fcm-token-' || n FROM generate_series(115, 117) AS n;
