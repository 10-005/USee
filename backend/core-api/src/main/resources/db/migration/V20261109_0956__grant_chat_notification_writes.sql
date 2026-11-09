-- chat·notification 서비스가 core 스키마의 자기 테이블에 쓸 수 있게 권한을 준다(S15P31D106-278).
-- 서버 DB 초기화(infra postgres-init)는 core 테이블 SELECT만 기본 권한으로 준다. 쓰기는 테이블 소유자(core_app)인 Flyway가 여기서 준다.
-- 로컬 DB에는 서비스별 계정이 없으므로 계정이 있을 때만 실행한다.
-- IDENTITY 컬럼은 INSERT 권한만 있으면 되고 시퀀스 권한은 따로 필요 없다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'chat_app') THEN
        GRANT INSERT, UPDATE ON chat_rooms, chat_participants, chat_messages TO chat_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notification_app') THEN
        GRANT INSERT, UPDATE ON notifications, device_tokens TO notification_app;
    END IF;
END
$$;
