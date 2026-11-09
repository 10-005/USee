-- core-api가 입양 신청자의 모의 입양 리포트(API 12.15 = 18.21, Q483)를 읽을 수 있게 권한을 준다
-- 서버 DB 초기화(infra postgres-init)는 core_app에 simulation 스키마 권한을 주지 않는다. 스키마·테이블 소유자(simulation_app)인 Flyway가 여기서 준다
-- 읽기 전용(SELECT)이며 core-api가 쓰는 두 테이블만. 로컬 DB에는 서비스별 계정이 없으므로 계정이 있을 때만 실행한다
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_app') THEN
        GRANT USAGE ON SCHEMA simulation TO core_app;
        GRANT SELECT ON simulation_sessions, simulation_reports TO core_app;
    END IF;
END
$$;
