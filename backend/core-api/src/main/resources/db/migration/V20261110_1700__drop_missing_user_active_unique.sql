-- 회원당 ACTIVE 실종 신고 1건 제한을 푼다(DB 명세 Q505, Q399 선행 확인 대체)
-- 등록(7.4)·재개(7.6)에서 다른 ACTIVE 신고가 있어도 허용한다. 회원별 조회는 ix_missing_user(비삭제 user_seq)가 계속 받친다
DROP INDEX IF EXISTS uq_missing_user_active;
