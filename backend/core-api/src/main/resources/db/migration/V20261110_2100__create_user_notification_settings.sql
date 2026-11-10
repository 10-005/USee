-- 회원 알림 설정(DB 명세 v22 46번, Q510~Q515). 쓰기는 notification 서비스만 한다.
-- 행이 없으면 전부 켜짐으로 본다. 회원가입 때 만들지 않고 기존 회원도 채우지 않는다(첫 PATCH 때 생성).
-- USER만 설정할 수 있다(SHELTER는 행이 없어 항상 발송). 방해금지는 모의 입양 푸시에만 적용하고 시각은 서울 기준이다.
CREATE TABLE user_notification_settings (
    user_seq           INTEGER     PRIMARY KEY,
    simulation_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    chat_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    comment_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_match_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    adoption_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    quiet_start        TIME(0)     NOT NULL DEFAULT '22:00',
    quiet_end          TIME(0)     NOT NULL DEFAULT '08:00',
    reg_dttm           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dttm           TIMESTAMPTZ,
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_seq) REFERENCES users (user_seq),
    -- 시작이 끝보다 늦으면 자정을 넘기는 구간. 같으면 구간이 정의되지 않는다
    CONSTRAINT ck_notification_setting_quiet CHECK (quiet_start <> quiet_end)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notification_app') THEN
        -- SELECT는 서버 기본 권한으로도 주어지지만 infra 설정과 상관없이 동작하도록 함께 준다
        GRANT SELECT, INSERT, UPDATE ON user_notification_settings TO notification_app;
    END IF;
END
$$;
