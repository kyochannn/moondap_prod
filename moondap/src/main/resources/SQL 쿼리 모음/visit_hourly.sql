-- 시간대별 접속 분포
--
-- md_visit_daily 는 하루 한 행이라 "몇 시에 사람이 몰리는가"를 알 수 없었다.
-- md_visit_log 는 PK 가 (visit_date, ip_address) 라 하루 한 사람당 한 행만 남고
-- 시각도 없다. 그래서 별도 집계 테이블을 둔다.
--
-- 기존 두 테이블은 건드리지 않는다. md_visit_log 의 PK 를 바꾸면 하루 1행 제약이
-- 풀려 지난 135일치 데이터의 의미가 달라지고, 순방문자 계산도 전부 다시 해야 한다.
--
-- 적용:  mysql -u <user> -p moondap < visit_hourly.sql
--
-- 여기 쌓이는 값은 순방문자가 아니라 '접속 건수(페이지뷰)'다. 시간대 분포는
-- "언제 트래픽이 몰리나"를 보는 것이므로 같은 사람의 재방문도 세는 편이 맞다.

CREATE TABLE IF NOT EXISTS md_pageview_hourly (
    visit_date DATE     NOT NULL,
    visit_hour TINYINT  NOT NULL COMMENT '0~23',
    view_count BIGINT   NOT NULL DEFAULT 0 COMMENT '해당 시각의 접속 건수',

    PRIMARY KEY (visit_date, visit_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
