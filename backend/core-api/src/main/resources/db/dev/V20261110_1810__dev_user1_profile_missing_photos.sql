-- 시연 데이터 보정(local·dev 프로필에서만)
-- 1) 회원 1번 시연 계정 이름·닉네임, 이미 낸 입양 신청서의 신청자 스냅샷(이름·이메일). 전화·생년월일·프로필 사진은 그대로
-- 2) 테스트 문구로 남은 실종 신고 2건("S5 더미 실종 1/2")에 품종·내용·사진. 사진은 S3 images/seed/dev/missing-11·12.jpg
DO $dev$
DECLARE
    r     RECORD;
    v_id  INTEGER;
    v_img BIGINT;
BEGIN
    UPDATE users SET nickname = 'justin', name = '신지환', mod_dttm = now()
    WHERE user_seq = 1 AND role = 'USER';

    UPDATE adoption_applications SET applicant_name = '신지환', email = 'justin@naver.com'
    WHERE user_seq = 1 AND applicant_name IS NOT NULL;

    FOR r IN SELECT * FROM (VALUES
        ('S5 더미 실종 1', '믹스묘', '갈색', 'MALE', '3살', '약 5kg', TRUE, '410100000800011',
         '고등어 무늬에 가슴과 네 발이 흰색인 고양이예요. 창문 방충망이 열린 사이 나갔고, 이름을 부르면 대답하듯 울어요.',
         'missing-11.jpg'),
        ('S5 더미 실종 2', '비글', '흰색&갈색&탄', 'FEMALE', '5살', '약 10kg', TRUE, '410100000800012',
         '검은 목줄을 한 비글이에요. 산책 중 목줄이 풀려 공원 쪽으로 뛰어갔어요. 냄새를 따라 돌아다니는 버릇이 있어요.',
         'missing-12.jpg')
    ) AS t(old_desc, breed, color, gender, age, weight, neutered, chip, descr, file)
    LOOP
        UPDATE missing_reports m
        SET combination_seq = (SELECT c.combination_seq FROM animal_combinations c
                               JOIN animal_breeds b ON b.breed_seq = c.breed_seq
                               JOIN animal_colors co ON co.color_seq = c.color_seq
                               WHERE b.breed_name = r.breed AND co.color_name = r.color
                               ORDER BY c.combination_seq LIMIT 1),
            gender = r.gender, age = r.age, weight = r.weight, is_neutered = r.neutered,
            microchip_number = r.chip, description = r.descr
        WHERE m.missing_report_seq = (SELECT missing_report_seq FROM missing_reports WHERE description = r.old_desc
                                      ORDER BY missing_report_seq LIMIT 1)
        RETURNING missing_report_seq INTO v_id;

        CONTINUE WHEN v_id IS NULL OR EXISTS (SELECT 1 FROM missing_report_images WHERE missing_report_seq = v_id);
        SELECT image_seq INTO v_img FROM images WHERE image_key = 'images/seed/dev/' || r.file;
        IF v_img IS NULL THEN
            INSERT INTO images (image_key, mime_type, reg_usr_seq)
            VALUES ('images/seed/dev/' || r.file, 'image/jpeg', (SELECT user_seq FROM missing_reports WHERE missing_report_seq = v_id))
            RETURNING image_seq INTO v_img;
        END IF;
        INSERT INTO missing_report_images (missing_report_seq, image_seq, display_order) VALUES (v_id, v_img, 1);
    END LOOP;
END
$dev$;
