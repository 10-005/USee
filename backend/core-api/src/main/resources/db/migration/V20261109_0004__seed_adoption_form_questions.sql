-- 23. adoption_form_questions 현재 공통 20문항(Q458). question_seq 1~20은 프론트의 섹션·placeholder 매핑 키라 직접 지정한다
-- 구성: 주거 및 동거 환경 1~12(조건부 5·7), 입양 신청서 13~16, 입양 전 교육 17~20(조건부 18~20)
-- 문구·보기는 확정본이 없어 명세 12.1 예시(1번 주거 형태, 20번 이수일)에 맞춘 가안이다. 확정되면 같은 question_seq 행을 UPDATE
-- 조건부 문항은 is_required=TRUE + condition_jsonb.required_when_matched=TRUE: 조건이 맞을 때만 필수(12.1 예시와 같은 표기)
INSERT INTO adoption_form_questions
    (question_seq, question_text, question_type, is_required, display_order, options_jsonb, condition_jsonb)
OVERRIDING SYSTEM VALUE
VALUES
    (1,  '주거 형태', 'SINGLE_CHOICE', TRUE, 1, '["아파트", "단독주택", "다세대", "오피스텔", "기타"]', NULL),
    (2,  '주거 소유 형태', 'SINGLE_CHOICE', TRUE, 2, '["자가", "전세", "월세", "기타"]', NULL),
    (3,  '주거지에서 반려동물 양육이 허용되나요?', 'SINGLE_CHOICE', TRUE, 3, '["허용", "허용되지 않음", "확인 필요"]', NULL),
    (4,  '함께 사는 가족(동거인)이 있나요?', 'SINGLE_CHOICE', TRUE, 4, '["있음", "없음"]', NULL),
    (5,  '동거인 모두 입양에 동의했나요?', 'SINGLE_CHOICE', TRUE, 5, '["모두 동의", "일부 동의", "동의하지 않음"]',
         '{"depends_on": 4, "operator": "EQ", "value": "있음", "required_when_matched": true}'),
    (6,  '현재 함께 사는 다른 동물이 있나요?', 'SINGLE_CHOICE', TRUE, 6, '["있음", "없음"]', NULL),
    (7,  '함께 사는 동물을 소개해 주세요(종류·나이·중성화 여부 등)', 'TEXT', TRUE, 7, NULL,
         '{"depends_on": 6, "operator": "EQ", "value": "있음", "required_when_matched": true}'),
    (8,  '반려동물을 길러 본 경험이 있나요?', 'SINGLE_CHOICE', TRUE, 8, '["있음", "없음"]', NULL),
    (9,  '하루 평균 집을 비우는 시간', 'SINGLE_CHOICE', TRUE, 9, '["4시간 미만", "4~8시간", "8시간 이상"]', NULL),
    (10, '주로 돌볼 사람', 'TEXT', TRUE, 10, NULL, NULL),
    (11, '동물 알레르기가 있는 가족이 있나요?', 'SINGLE_CHOICE', TRUE, 11, '["있음", "없음", "모름"]', NULL),
    (12, '이사·장기 부재 시 돌봄 계획', 'TEXT', TRUE, 12, NULL, NULL),
    (13, '입양을 결심한 이유', 'TEXT', TRUE, 13, NULL, NULL),
    (14, '한 달 예상 양육비', 'SINGLE_CHOICE', TRUE, 14, '["10만 원 미만", "10~30만 원", "30만 원 이상"]', NULL),
    (15, '양육이 어려워질 경우 대처 계획', 'TEXT', TRUE, 15, NULL, NULL),
    (16, '입양 후 중성화·예방접종에 동의하나요?', 'SINGLE_CHOICE', TRUE, 16, '["동의", "동의하지 않음"]', NULL),
    (17, '입양 전 교육 이수 여부', 'SINGLE_CHOICE', TRUE, 17, '["이수", "미이수"]', NULL),
    (18, '교육 기관', 'TEXT', TRUE, 18, NULL,
         '{"depends_on": 17, "operator": "EQ", "value": "이수", "required_when_matched": true}'),
    (19, '교육 과정', 'TEXT', TRUE, 19, NULL,
         '{"depends_on": 17, "operator": "EQ", "value": "이수", "required_when_matched": true}'),
    (20, '이수일', 'DATE', TRUE, 20, NULL,
         '{"depends_on": 17, "operator": "EQ", "value": "이수", "required_when_matched": true}');

-- 고정 PK를 넣었으니 IDENTITY 시퀀스를 맞춘다(안 하면 다음 INSERT가 1부터 시작해 PK 충돌)
SELECT setval(pg_get_serial_sequence('adoption_form_questions', 'question_seq'), (SELECT max(question_seq) FROM adoption_form_questions));
