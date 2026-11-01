-- 하나의 DB에 서비스별 스키마. 각 서비스는 currentSchema로 자기 스키마만 사용한다.
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS chat;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS simulation;
