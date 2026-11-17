-- 품종표를 254개에서 234개(개 196, 고양이 37, 기타 1)로 줄인다(S15P31D106-103, DB 명세 v23, Q520)
-- 입력: AI 팀과 정리한 animal-options-final.json. 같은 품종의 중복·별칭·변종 17건과 웰시코기 2건을 대표 한 행으로 합치고,
--       AI 모델 전용 품종 2개(우리 코드 X00001·X00002)를 넣고, AI 팀 한글 표시 이름에 맞춰 이름을 고친다
-- 대표 행의 breed_seq·breed_code와 기존 combination_seq는 유지한다. 지우는 품종을 가리키던 반려동물·신고·보호동물은
-- 대표 품종의 같은 색상 조합으로 옮긴다(지우는 것은 기준정보뿐, 사용자 데이터는 지우지 않는다)
-- 색상은 이름으로 맞춘다. 공식 코드표에는 이름이 같은 색상 코드가 4쌍 있고 V20261109_2240이 이름당 한 행으로 합쳤다
-- 작업용 표는 임시 테이블 대신 core 스키마의 일반 테이블로 만들고 끝에서 지운다(V20261109_2240과 같은 이유)

-- 지우는 품종(old_code) → 남는 대표 품종(new_code)
CREATE TABLE tmp_breed_merge (old_code VARCHAR(60), new_code VARCHAR(60));
INSERT INTO tmp_breed_merge VALUES
    ('000146', '000007'),
    ('000236', '000010'),
    ('000238', '000031'),
    ('000076', '000038'),
    ('000093', '000038'),
    ('000153', '000042'),
    ('000161', '000043'),
    ('000241', '000064'),
    ('000088', '000085'),
    ('000205', '000108'),
    ('000092', '000118'),
    ('000094', '000121'),
    ('000074', '000128'),
    ('000078', '000128'),
    ('000105', '000128'),
    ('000107', '000128'),
    ('000250', '000129'),
    ('000226', '000152'),
    ('000199', '000188'),
    ('00216', '000204'),
    ('000148', '000227'),
    ('000197', '00214');

-- 남는 품종의 최종 이름(합친 이름, 웰시코기, AI 팀 표시 이름)
CREATE TABLE tmp_breed_rename (code VARCHAR(60), name VARCHAR(100));
INSERT INTO tmp_breed_rename VALUES
    ('000007', '알라스칸 말라뮤트'),
    ('000010', '오스트레일리안 캐틀 독'),
    ('000031', '센트럴 아시안 셰퍼드'),
    ('000038', '닥스훈트'),
    ('000042', '도고 아르젠티노'),
    ('000043', '프레사 카나리오'),
    ('000050', '프렌치불독'),
    ('000054', '골든리트리버'),
    ('000064', '케리 블루 테리어'),
    ('000067', '진돗개'),
    ('000085', '컨티넨탈 토이 스파니엘(파피용·팔렌)'),
    ('000100', '시바견'),
    ('000101', '시추'),
    ('000108', '웰시코기'),
    ('000113', '요크셔테리어'),
    ('000118', '그레이트 피레니즈'),
    ('000121', '로트와일러'),
    ('000128', '푸들'),
    ('000129', '재패니즈 친'),
    ('000152', '벨지안 셰퍼드 독(말리노이즈)'),
    ('000170', '노르웨이숲'),
    ('000188', '스코티시 폴드'),
    ('000200', '코리안 숏헤어'),
    ('000204', '노르포크 테리어'),
    ('000227', '벨지안 셰퍼드 독(테르뷰렌)'),
    ('00213', '랙돌'),
    ('00214', '페르시안');

-- 합치거나 새로 넣은 품종의 색상 선택지(JSON colors. 웰시코기는 세 항목의 합집합)
CREATE TABLE tmp_breed_colors (code VARCHAR(60), color_name VARCHAR(100));
INSERT INTO tmp_breed_colors VALUES
    ('000038', '갈색'),
    ('000038', '갈색&검정'),
    ('000038', '검은털'),
    ('000038', '검정&황갈색'),
    ('000038', '검정색'),
    ('000038', '기타'),
    ('000038', '노란색'),
    ('000038', '빨간색'),
    ('000038', '얼룩무늬'),
    ('000038', '적갈색'),
    ('000038', '쵸콜릿색'),
    ('000038', '크림색'),
    ('000038', '황갈색'),
    ('000038', '흑갈색'),
    ('X00001', '갈색'),
    ('X00001', '갈색&흰색'),
    ('X00001', '검정&흰색'),
    ('X00001', '검정색'),
    ('X00001', '금색'),
    ('X00001', '기타'),
    ('X00001', '살구색'),
    ('X00001', '크림색'),
    ('X00001', '회색'),
    ('X00001', '흰색'),
    ('000118', '갈색&흰색'),
    ('000118', '기타'),
    ('000118', '엷은 황갈색&흰색'),
    ('000118', '적갈&검정&흰색'),
    ('000118', '호반색&흰색'),
    ('000118', '흰색'),
    ('000204', 'GRIZZLE'),
    ('000204', '갈색 얼룩무늬'),
    ('000204', '검은색&황갈색'),
    ('000204', '검정 황갈색&흰색'),
    ('000204', '빨간색'),
    ('000204', '옅은 황색'),
    ('000204', '적갈색'),
    ('000204', '크림색'),
    ('000204', '회색'),
    ('000042', '기타'),
    ('000042', '밝은 금색'),
    ('000042', '살구색'),
    ('000042', '은색&흰색'),
    ('000042', '크림색'),
    ('000042', '흰색'),
    ('000121', 'WHITE BLACK&TAN'),
    ('000121', '갈색&흰색'),
    ('000121', '검은색&황갈색'),
    ('000121', '검은털'),
    ('000121', '검정&은색'),
    ('000121', '검정&황갈색'),
    ('000121', '검정색'),
    ('000121', '기타'),
    ('000121', '밤색'),
    ('000121', '어두운검정색'),
    ('000121', '어두운청색'),
    ('000121', '적갈색'),
    ('000121', '하얀바탕에 한가지색 또는 두가지색의 명확한 반점'),
    ('000121', '황갈색'),
    ('000121', '흑갈색'),
    ('000152', '기타'),
    ('000152', '적갈색'),
    ('000152', '황갈색'),
    ('000152', '흑갈색'),
    ('000227', '갈색&검정'),
    ('000227', '기타'),
    ('000227', '빨간색'),
    ('000227', '엷은 황갈색'),
    ('000227', '울프그레이'),
    ('000227', '적갈색'),
    ('000227', '적갈색&밝은갈색'),
    ('000227', '적갈색&황갈색&흰색'),
    ('000227', '황갈색'),
    ('000227', '황갈색&적갈색'),
    ('000227', '흑갈색'),
    ('000031', '갈색&흰색'),
    ('000031', '검정&흰색'),
    ('000031', '기타'),
    ('000031', '얼룩무늬'),
    ('000031', '적갈&검정&흰색'),
    ('000031', '하얀바탕에 한가지색 또는 두가지색의 명확한 반점'),
    ('000031', '호반색&흰색'),
    ('000031', '흰색&갈색&탄'),
    ('000007', 'GRAY&BLACK'),
    ('000007', '갈색&회색'),
    ('000007', '갈색&흰색'),
    ('000007', '검정&흰색'),
    ('000007', '검정색'),
    ('000007', '기타'),
    ('000007', '빨간색'),
    ('000007', '울프그레이'),
    ('000007', '회색'),
    ('000007', '회색&흰색'),
    ('000007', '흰색'),
    ('000010', '검정색'),
    ('000010', '기타'),
    ('000010', '빨간색'),
    ('000010', '엷은 황갈색&흰색'),
    ('000010', '적갈색'),
    ('000010', '청색'),
    ('000129', '갈색&흰색'),
    ('000129', '검정&흰색'),
    ('000129', '기타'),
    ('000129', '붉고 엷은 황갈색'),
    ('000129', '주황&흰색'),
    ('000129', '황갈색'),
    ('000085', '갈색'),
    ('000085', '갈색&흰색'),
    ('000085', '검정&흰색'),
    ('000085', '기타'),
    ('000085', '빨간색&흰색'),
    ('000085', '엷은 황갈색&흰색'),
    ('000085', '적갈&흰색'),
    ('000085', '털끝검정'),
    ('000085', '호반색&흰색'),
    ('000085', '흰색'),
    ('000085', '흰색&갈색&탄'),
    ('000064', '검정색'),
    ('000064', '기타'),
    ('000064', '은청색'),
    ('000064', '청색'),
    ('000064', '청회색'),
    ('000128', 'APRICOT'),
    ('000128', 'RED FAWN'),
    ('000128', '갈색'),
    ('000128', '검정&흰색'),
    ('000128', '검정색'),
    ('000128', '기타'),
    ('000128', '붉고 엷은 황갈색'),
    ('000128', '살구색'),
    ('000128', '어두운회색'),
    ('000128', '엷은 황갈색&흰색'),
    ('000128', '주황&흰색'),
    ('000128', '청색'),
    ('000128', '쵸콜릿색'),
    ('000128', '크림색'),
    ('000128', '호반색&흰색'),
    ('000128', '회색'),
    ('000128', '흑갈색'),
    ('000128', '흰색'),
    ('000043', '갈색&검정'),
    ('000043', '검은색&황갈색'),
    ('000043', '검정&황갈색'),
    ('000043', '검정색'),
    ('000043', '기타'),
    ('000043', '적갈색&밝은갈색'),
    ('000043', '호반색(호랑이무늬)'),
    ('000043', '황갈색'),
    ('000043', '황갈색&적갈색'),
    ('000043', '흑갈색'),
    ('X00002', '갈색'),
    ('X00002', '갈색 얼룩무늬'),
    ('X00002', '검정색'),
    ('X00002', '기타'),
    ('X00002', '라일락색'),
    ('X00002', '빨간색'),
    ('X00002', '은색'),
    ('X00002', '크림색'),
    ('X00002', '회색'),
    ('X00002', '흰색'),
    ('X00002', '흰색/줄무늬'),
    ('000188', '갈색'),
    ('000188', '검정색'),
    ('000188', '기타'),
    ('000188', '크림색'),
    ('000188', '회색'),
    ('000188', '흰색'),
    ('00214', '검정색'),
    ('00214', '기타'),
    ('00214', '붉고 엷은 황갈색'),
    ('00214', '청색'),
    ('00214', '청황색'),
    ('00214', '회색'),
    ('00214', '흰색'),
    ('000108', 'SABLE&WHITE'),
    ('000108', '갈색'),
    ('000108', '갈색&흰색'),
    ('000108', '검정 황갈색&흰색'),
    ('000108', '검정&갈색&흰색'),
    ('000108', '검정&흰색'),
    ('000108', '검정색'),
    ('000108', '기타'),
    ('000108', '블루멀'),
    ('000108', '빨간색'),
    ('000108', '살구색'),
    ('000108', '얼룩무늬'),
    ('000108', '황갈색'),
    ('000108', '흑갈색');

-- 1. AI 모델 전용 품종(공식 코드 없음). 성격은 가안, 대표 이미지는 없음
INSERT INTO animal_breeds (species_seq, breed_name, breed_code, personality)
SELECT s.species_seq, v.name, v.code, 'FRIENDLY'
FROM (VALUES ('DOG', '말티푸', 'X00001'), ('CAT', '스코티시 스트레이트', 'X00002')) AS v(species_code, name, code)
JOIN animal_species s ON s.species_code = v.species_code;

-- 2. 남는 품종에 색상 조합을 넣는다
INSERT INTO animal_combinations (breed_seq, color_seq)
SELECT b.breed_seq, c.color_seq
FROM tmp_breed_colors t
JOIN animal_breeds b ON b.breed_code = t.code
JOIN animal_colors c ON c.color_name = t.color_name
ON CONFLICT (breed_seq, color_seq) DO NOTHING;

-- 3. 지우는 품종의 조합을 쓰는 데이터를 대표 품종의 같은 색상 조합으로 옮긴다.
--    대표에 그 색상 조합이 없으면(JSON 밖 색상) 만들어 두고, 아무도 안 쓰면 5에서 지운다
CREATE TABLE tmp_combo_remap AS
SELECT oc.combination_seq AS old_seq, nb.breed_seq AS new_breed_seq, oc.color_seq
FROM tmp_breed_merge m
JOIN animal_breeds ob ON ob.breed_code = m.old_code
JOIN animal_breeds nb ON nb.breed_code = m.new_code
JOIN animal_combinations oc ON oc.breed_seq = ob.breed_seq;
INSERT INTO animal_combinations (breed_seq, color_seq)
SELECT DISTINCT new_breed_seq, color_seq FROM tmp_combo_remap
ON CONFLICT (breed_seq, color_seq) DO NOTHING;
UPDATE pets t SET combination_seq = c.combination_seq
FROM tmp_combo_remap r JOIN animal_combinations c ON c.breed_seq = r.new_breed_seq AND c.color_seq = r.color_seq
WHERE t.combination_seq = r.old_seq;
UPDATE missing_reports t SET combination_seq = c.combination_seq
FROM tmp_combo_remap r JOIN animal_combinations c ON c.breed_seq = r.new_breed_seq AND c.color_seq = r.color_seq
WHERE t.combination_seq = r.old_seq;
UPDATE found_reports t SET combination_seq = c.combination_seq
FROM tmp_combo_remap r JOIN animal_combinations c ON c.breed_seq = r.new_breed_seq AND c.color_seq = r.color_seq
WHERE t.combination_seq = r.old_seq;
UPDATE shelter_animals t SET combination_seq = c.combination_seq
FROM tmp_combo_remap r JOIN animal_combinations c ON c.breed_seq = r.new_breed_seq AND c.color_seq = r.color_seq
WHERE t.combination_seq = r.old_seq;

-- 4. 지우는 품종과 그 조합을 지우고(외래 키가 남은 참조를 막는다) 남는 품종의 이름을 고친다
DELETE FROM animal_combinations WHERE combination_seq IN (SELECT old_seq FROM tmp_combo_remap);
DELETE FROM animal_breeds WHERE breed_code IN (SELECT old_code FROM tmp_breed_merge);
UPDATE animal_breeds b SET breed_name = r.name FROM tmp_breed_rename r WHERE b.breed_code = r.code;

-- 5. 합치거나 새로 넣은 품종의 조합을 JSON 색상에 맞춘다. JSON에 없어도 데이터가 쓰는 조합은 남긴다
DELETE FROM animal_combinations ac
USING animal_breeds b
WHERE b.breed_seq = ac.breed_seq
  AND b.breed_code IN (SELECT code FROM tmp_breed_colors)
  AND NOT EXISTS (
        SELECT 1 FROM tmp_breed_colors t JOIN animal_colors c ON c.color_name = t.color_name
        WHERE t.code = b.breed_code AND c.color_seq = ac.color_seq)
  AND NOT EXISTS (SELECT 1 FROM pets t WHERE t.combination_seq = ac.combination_seq)
  AND NOT EXISTS (SELECT 1 FROM missing_reports t WHERE t.combination_seq = ac.combination_seq)
  AND NOT EXISTS (SELECT 1 FROM found_reports t WHERE t.combination_seq = ac.combination_seq)
  AND NOT EXISTS (SELECT 1 FROM shelter_animals t WHERE t.combination_seq = ac.combination_seq);

-- 6. 검증. 하나라도 어긋나면 예외로 이 마이그레이션 전체를 되돌린다
DO $$
DECLARE
    v_text TEXT;
BEGIN
    SELECT string_agg(s.species_code || '=' || n, ',' ORDER BY s.species_code) INTO v_text
    FROM (SELECT species_seq, count(*) AS n FROM animal_breeds GROUP BY species_seq) b
    JOIN animal_species s ON s.species_seq = b.species_seq;
    IF v_text IS DISTINCT FROM 'CAT=37,DOG=196,ETC=1' THEN
        RAISE EXCEPTION '품종 수가 다릅니다: % (기대 CAT=37,DOG=196,ETC=1)', v_text;
    END IF;

    -- AI 품종 모델(개 15·고양이 15)의 한글 표시 이름. core-api는 이 이름으로 품종을 찾는다
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
        RAISE EXCEPTION 'AI 모델 품종이 품종표에 없습니다: %', v_text;
    END IF;

    SELECT string_agg(DISTINCT t.color_name, ', ') INTO v_text
    FROM tmp_breed_colors t WHERE NOT EXISTS (SELECT 1 FROM animal_colors c WHERE c.color_name = t.color_name);
    IF v_text IS NOT NULL THEN
        RAISE EXCEPTION '색상표에 없는 색상입니다: %', v_text;
    END IF;

    SELECT string_agg(b.breed_code, ', ') INTO v_text
    FROM animal_breeds b WHERE NOT EXISTS (SELECT 1 FROM animal_combinations c WHERE c.breed_seq = b.breed_seq);
    IF v_text IS NOT NULL THEN
        RAISE EXCEPTION '색상 조합이 없는 품종입니다: %', v_text;
    END IF;
END
$$;

DROP TABLE tmp_combo_remap, tmp_breed_colors, tmp_breed_rename, tmp_breed_merge;
