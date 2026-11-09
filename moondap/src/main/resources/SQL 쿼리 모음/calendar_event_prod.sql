-- 관리자 공유 캘린더 일정 — 운영 DB 최초 반영용 (깔끔한 최종 스키마만)
--
-- calendar_event.sql 의 압축본이다. 그쪽은 로컬에서 기능을 하나씩 추가하며 쌓인
-- CREATE TABLE + 단계별 ALTER(색상 → 완료 → 날짜 전용 → 공통 일정) 이력을 그대로
-- 보존해서 "왜 이 컬럼이 생겼는지"를 알 수 있게 남겨둔 것이고, 이 파일은 그 이력을
-- 걷어내고 지금 시점의 최종 스키마만 담았다.
--
-- 운영 DB에 이 테이블이 아직 없다면(최초 반영) 이 파일 하나로 충분하다.
-- 이미 테이블이 있고 일부 컬럼만 빠진 상태라면(예: 과거에 다른 버전으로 한 번
-- 만든 적이 있다면) 이 파일은 ALTER 를 하지 않으므로 소용없다 — 그 경우엔
-- calendar_event.sql(단계별 ALTER 포함)을 실행할 것.
--
-- 적용:  mysql -u <user> -p moondap < calendar_event_prod.sql

CREATE TABLE IF NOT EXISTS md_calendar_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '일정 ID',
    title           VARCHAR(200) NOT NULL COMMENT '제목',
    description     TEXT                  DEFAULT NULL COMMENT '설명',
    start_at        DATE         NOT NULL COMMENT '시작일',
    end_at          DATE         NOT NULL COMMENT '종료일',
    author_username VARCHAR(50)  NOT NULL COMMENT '작성한 관리자 계정 (md_user.username)',
    color           VARCHAR(7)            DEFAULT NULL COMMENT '일정별 지정 색상(#rrggbb). NULL 이면 작성자 기준 자동 색상',
    completed       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '완료 여부',
    shared          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '공통 일정 여부 (true 면 작성자 색상 대신 중립 스타일로 표시)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),

    -- 캘린더 화면은 항상 기간(월) 단위로 조회하므로 시작 시각 기준 정렬/검색에 쓴다.
    KEY idx_start_at (start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 공유 캘린더 일정';
