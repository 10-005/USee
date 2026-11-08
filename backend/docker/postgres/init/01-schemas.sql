-- 하나의 DB에 스키마 2개(DB 명세 Q420 D). core는 core-api·chat·notification이 함께 쓰고, simulation은 simulation 전용.
-- 테이블은 core-api(core)·simulation(simulation)의 Flyway가 만든다.
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS simulation;
