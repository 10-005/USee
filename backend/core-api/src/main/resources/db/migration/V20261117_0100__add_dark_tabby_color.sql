-- 색상 '검정·짙은회갈색'을 추가하고 코리안 숏헤어(품종 코드 000200)에서 고를 수 있게 한다(시연용 반려동물 등록)
-- 공식 코드표(animal.go.kr)에 없는 색이라 우리 코드 X0001을 쓴다(품종의 X00001·X00002와 같은 방식)
INSERT INTO animal_colors (color_name, color_code) VALUES ('검정·짙은회갈색', 'X0001');

INSERT INTO animal_combinations (breed_seq, color_seq)
SELECT b.breed_seq, c.color_seq
FROM animal_breeds b
JOIN animal_colors c ON c.color_code = 'X0001'
WHERE b.breed_code = '000200';

-- 품종 코드가 바뀌어 조합이 안 들어가면 조용히 넘어가지 않고 멈춘다
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM animal_combinations m JOIN animal_colors c USING (color_seq) WHERE c.color_code = 'X0001'
    ) THEN
        RAISE EXCEPTION '코리안 숏헤어(000200) × 검정·짙은회갈색 조합을 만들지 못했습니다';
    END IF;
END $$;
