-- local·dev 전용 더미 뼈대(db/dev, prod에는 적용되지 않음). 다른 세션의 더미가 이 번호를 참조한다
-- USER userSeq 100~139: 카카오 가입 회원. 세션별 사용 범위 S2 100~104, S3 105~109, S4 110~114, S5 115~119, S6 120~129, S7 130~134
-- 닉네임 dev{n}, 전화 0100000{n}(활성 USER 유일), 카카오 식별자 dev-kakao-{n}, 프로필 이미지 NULL
INSERT INTO users (user_seq, role, nickname, name, birth_date, phone)
OVERRIDING SYSTEM VALUE
SELECT n, 'USER', 'dev' || n, '테스트' || n, DATE '1995-01-01' + (n - 100), '0100000' || n
FROM generate_series(100, 139) AS n;

INSERT INTO user_accounts (user_seq, provider_seq, login_identifier)
SELECT n, p.provider_seq, 'dev-kakao-' || n
FROM generate_series(100, 139) AS n
CROSS JOIN auth_providers p
WHERE p.provider_code = 'KAKAO';

-- 고정 PK를 넣었으니 IDENTITY 시퀀스를 맞춘다(안 하면 다음 회원가입이 PK 충돌)
SELECT setval(pg_get_serial_sequence('users', 'user_seq'), (SELECT max(user_seq) FROM users));
