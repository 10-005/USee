-- 개발·시연용 채팅 더미(API 14장). local·dev에서만 적용된다
-- 기대는 행: 더미 USER 120~129(S1 V20261109_0010__dev_base.sql), 보호소 1·2와 담당 계정(V2 shelter_members)
-- 방 3개(USER↔USER 1, USER↔SHELTER 2) × 메시지 10건 = 30건. PK는 RETURNING으로 받는다
DO $dev$
DECLARE
    room_seq     BIGINT;
    shelter_user INTEGER;
    lines        TEXT[];
    i            INTEGER;
BEGIN
    -- 1) USER 120 ↔ USER 121: 발견 신고를 보고 연락. 121은 마지막까지 읽음, 120은 마지막 2건 미읽음
    lines := ARRAY[
        '안녕하세요, 실종 신고 보고 연락드려요.',
        '네 안녕하세요! 혹시 보셨나요?',
        '어제 저녁 구미역 근처에서 비슷한 말티즈를 봤어요.',
        '정말요? 목줄 색깔 기억나세요?',
        '빨간 목줄이었던 것 같아요.',
        '맞아요, 저희 아이 같아요ㅠㅠ',
        '사진 찍어둔 게 있는데 보내드릴게요.',
        '감사합니다. 그 근처로 가볼게요.',
        '저도 오늘 퇴근길에 한 번 더 둘러볼게요.',
        '정말 감사해요. 소식 있으면 바로 연락드릴게요.'
    ];
    INSERT INTO chat_rooms (room_type, user_a_seq, user_b_seq) VALUES ('USER', 120, 121) RETURNING chat_room_seq INTO room_seq;
    INSERT INTO chat_participants (chat_room_seq, participant_type, participant_user_seq)
    VALUES (room_seq, 'USER', 120), (room_seq, 'USER', 121);
    FOR i IN 1..10 LOOP
        INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
        VALUES (room_seq, CASE WHEN i % 2 = 1 THEN 121 ELSE 120 END, 'TEXT', lines[i],
                TIMESTAMPTZ '2026-11-08 19:00:00+09' + i * INTERVAL '3 minutes');
    END LOOP;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT max(chat_message_seq) FROM chat_messages WHERE chat_room_seq = room_seq)
    WHERE p.chat_room_seq = room_seq AND p.participant_user_seq = 121;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT chat_message_seq FROM chat_messages WHERE chat_room_seq = room_seq ORDER BY chat_message_seq OFFSET 7 LIMIT 1)
    WHERE p.chat_room_seq = room_seq AND p.participant_user_seq = 120;

    -- 2) USER 122 ↔ 보호소 1: 입양 문의. 보호소 쪽 미읽음 1건
    SELECT user_seq INTO shelter_user FROM shelter_members WHERE shelter_seq = 1;
    lines := ARRAY[
        '안녕하세요, 공고 보고 입양 문의드립니다.',
        '안녕하세요. 어떤 아이 보고 연락 주셨나요?',
        '3살 믹스견이요. 아직 입양 가능한가요?',
        '네, 아직 신청 받고 있습니다.',
        '방문해서 먼저 만나볼 수 있을까요?',
        '평일 오전 10시~오후 5시에 방문 가능합니다.',
        '이번 주 목요일 오후 2시에 가도 될까요?',
        '네 괜찮습니다. 신분증 지참해 주세요.',
        '알겠습니다. 감사합니다!',
        '혹시 주차 가능한가요?'
    ];
    INSERT INTO chat_rooms (room_type, user_a_seq, shelter_seq) VALUES ('SHELTER', 122, 1) RETURNING chat_room_seq INTO room_seq;
    INSERT INTO chat_participants (chat_room_seq, participant_type, participant_user_seq, participant_shelter_seq)
    VALUES (room_seq, 'USER', 122, NULL), (room_seq, 'SHELTER', NULL, 1);
    FOR i IN 1..10 LOOP
        INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
        VALUES (room_seq, CASE WHEN i % 2 = 1 OR i = 10 THEN 122 ELSE shelter_user END, 'TEXT', lines[i],
                TIMESTAMPTZ '2026-11-08 10:00:00+09' + i * INTERVAL '10 minutes');
    END LOOP;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT chat_message_seq FROM chat_messages WHERE chat_room_seq = room_seq ORDER BY chat_message_seq OFFSET 8 LIMIT 1)
    WHERE p.chat_room_seq = room_seq AND p.participant_shelter_seq = 1;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT max(chat_message_seq) FROM chat_messages WHERE chat_room_seq = room_seq)
    WHERE p.chat_room_seq = room_seq AND p.participant_user_seq = 122;

    -- 3) 보호소 2 → USER 123: 보호소가 먼저 연락(R8). USER 쪽은 아직 하나도 안 읽음
    SELECT user_seq INTO shelter_user FROM shelter_members WHERE shelter_seq = 2;
    lines := ARRAY[
        '안녕하세요, 발견 신고해 주신 강아지 관련해 연락드립니다.',
        '오늘 오전에 구조해서 보호소로 데려왔습니다.',
        '건강 상태는 양호합니다.',
        '발견 당시 상황을 조금 더 여쭤봐도 될까요?',
        '네, 말씀하세요.',
        '목줄이나 인식표가 있었나요?',
        '아니요, 아무것도 없었어요.',
        '알겠습니다. 보호 공고 올리면 다시 알려드릴게요.',
        '감사합니다. 주인 꼭 찾았으면 좋겠어요.',
        '공고 등록했습니다. 협조 감사합니다.'
    ];
    INSERT INTO chat_rooms (room_type, user_a_seq, shelter_seq) VALUES ('SHELTER', 123, 2) RETURNING chat_room_seq INTO room_seq;
    INSERT INTO chat_participants (chat_room_seq, participant_type, participant_user_seq, participant_shelter_seq)
    VALUES (room_seq, 'USER', 123, NULL), (room_seq, 'SHELTER', NULL, 2);
    FOR i IN 1..10 LOOP
        INSERT INTO chat_messages (chat_room_seq, sender_user_seq, message_type, body, reg_dttm)
        VALUES (room_seq, CASE WHEN i IN (5, 7, 9) THEN 123 ELSE shelter_user END, 'TEXT', lines[i],
                TIMESTAMPTZ '2026-11-07 14:00:00+09' + i * INTERVAL '5 minutes');
    END LOOP;
    UPDATE chat_participants p SET last_read_message_seq = (
        SELECT max(chat_message_seq) FROM chat_messages WHERE chat_room_seq = room_seq)
    WHERE p.chat_room_seq = room_seq AND p.participant_shelter_seq = 2;
END
$dev$;
