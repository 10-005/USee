-- Flyway 콜백: 매 migrate 뒤 실행(V 파일이 아니라 이력에 남지 않음). 8.9 품종 추천(Q519)은 AI 서버가 주는 한글 품종 이름으로
-- 품종표를 찾으므로(Q520), 이후 마이그레이션이 품종 이름을 바꾸거나 지워 연결이 조용히 끊기면 여기서 기동을 멈춘다.
-- 목록은 AI 팀 display_names.json(개 15·고양이 15). AI 모델이 바뀌면 이 목록도 함께 고친다.
DO $$
DECLARE
    v_text TEXT;
BEGIN
    SELECT string_agg(v.species_code || ':' || v.name, ', ') INTO v_text
    FROM (VALUES
        ('DOG', '말티즈'), ('DOG', '포메라니안'), ('DOG', '비숑 프리제'), ('DOG', '말티푸'), ('DOG', '시베리안 허스키'),
        ('DOG', '치와와'), ('DOG', '시추'), ('DOG', '시바견'), ('DOG', '웰시코기'), ('DOG', '골든리트리버'),
        ('DOG', '프렌치불독'), ('DOG', '닥스훈트'), ('DOG', '재패니즈 스피츠'), ('DOG', '요크셔테리어'), ('DOG', '진돗개'),
        ('CAT', '코리안 숏헤어'), ('CAT', '스코티시 폴드'), ('CAT', '러시안 블루'), ('CAT', '먼치킨'), ('CAT', '브리티시 쇼트헤어'),
        ('CAT', '페르시안'), ('CAT', '샴'), ('CAT', '아비시니안'), ('CAT', '터키시 앙고라'), ('CAT', '스코티시 스트레이트'),
        ('CAT', '아메리칸 쇼트헤어'), ('CAT', '랙돌'), ('CAT', '벵갈'), ('CAT', '노르웨이숲'), ('CAT', '메인쿤')
    ) AS v(species_code, name)
    WHERE NOT EXISTS (
        SELECT 1 FROM animal_breeds b JOIN animal_species s ON s.species_seq = b.species_seq
        WHERE s.species_code = v.species_code AND b.breed_name = v.name);
    IF v_text IS NOT NULL THEN
        RAISE EXCEPTION 'AI 품종 모델 이름이 품종표에 없습니다(8.9 연결 끊김): %', v_text;
    END IF;
END
$$;
