-- 관리자 공유 캘린더 일정
--
-- 관리자 2계정(로그인 가능한 모든 ROLE_ADMIN 계정)이 함께 보는 개인용 캘린더.
-- 콘텐츠와 무관한 내부용 일정이라 별도 캐시나 공개 조회 경로는 두지 않는다.
--
-- 적용:  mysql -u <user> -p moondap < calendar_event.sql
-- (이 스크립트는 여러 번 실행해도 안전하다. color 컬럼 추가분은 information_schema 로
--  존재 여부를 먼저 확인한다 — comment_feature_upgrade.sql 과 같은 방식.)
--
-- 작성자별 기본 색상은 이 테이블이 아니라 프런트(JS)에서 author_username 을 해시해
-- 팔레트를 고르는 방식으로 처리한다. color 컬럼은 그 기본값을 일정 단위로 덮어쓰고
-- 싶을 때만 채운다(NULL 이면 작성자 기준 자동 색상을 그대로 쓴다).

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


-- ────────────────────────────────────────────────────────────
-- color 컬럼 추가 (이미 테이블을 만든 환경을 위한 소급 적용)
--
-- 실제 서버는 MariaDB 10.1.13 이라 ADD COLUMN IF NOT EXISTS 를 쓰지 않는다
-- (MySQL 에는 없는 확장). information_schema 로 직접 확인한다.
-- ────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'md_calendar_event'
       AND COLUMN_NAME  = 'color'
);
SET @stmt := IF(@col_exists = 0,
    'ALTER TABLE moondap.md_calendar_event
        ADD COLUMN color VARCHAR(7) NULL
        COMMENT ''일정별 지정 색상(#rrggbb). NULL 이면 작성자 기준 자동 색상''
        AFTER author_username',
    'SELECT ''[건너뜀] md_calendar_event.color 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


-- ────────────────────────────────────────────────────────────
-- completed 컬럼 추가 (이미 테이블을 만든 환경을 위한 소급 적용)
-- ────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'md_calendar_event'
       AND COLUMN_NAME  = 'completed'
);
SET @stmt := IF(@col_exists = 0,
    'ALTER TABLE moondap.md_calendar_event
        ADD COLUMN completed TINYINT(1) NOT NULL DEFAULT 0
        COMMENT ''완료 여부''
        AFTER color',
    'SELECT ''[건너뜀] md_calendar_event.completed 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


-- ────────────────────────────────────────────────────────────
-- start_at/end_at: DATETIME → DATE (시간은 더 이상 입력받지도, 저장하지도 않는다)
--
-- DATETIME → DATE 변환은 기존 값의 시간 부분을 버린다(돌이킬 수 없음).
-- 이 캘린더는 "몇 시에 했는지"가 아니라 "어느 날인지"만 쓰므로 의도된 손실이다.
-- ────────────────────────────────────────────────────────────
SET @col_type := (
    SELECT DATA_TYPE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'md_calendar_event'
       AND COLUMN_NAME  = 'start_at'
);
SET @stmt := IF(@col_type IS NULL OR @col_type <> 'date',
    'ALTER TABLE moondap.md_calendar_event
        MODIFY COLUMN start_at DATE NOT NULL COMMENT ''시작일'',
        MODIFY COLUMN end_at DATE NOT NULL COMMENT ''종료일''',
    'SELECT ''[건너뜀] md_calendar_event.start_at 이미 DATE'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


-- ────────────────────────────────────────────────────────────
-- shared 컬럼 추가 (이미 테이블을 만든 환경을 위한 소급 적용)
--
-- 개인 작성자와 무관한 "공통 일정"을 구분하는 플래그. author_username 은
-- 그대로 "누가 만들었나" 기록용으로 남기고, shared=true 면 화면에서만
-- 작성자 색상/아바타 대신 중립 스타일(회색 + 공유 아이콘)로 그린다.
-- ────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'md_calendar_event'
       AND COLUMN_NAME  = 'shared'
);
SET @stmt := IF(@col_exists = 0,
    'ALTER TABLE moondap.md_calendar_event
        ADD COLUMN shared TINYINT(1) NOT NULL DEFAULT 0
        COMMENT ''공통 일정 여부 (true 면 작성자 색상 대신 중립 스타일로 표시)''
        AFTER completed',
    'SELECT ''[건너뜀] md_calendar_event.shared 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
