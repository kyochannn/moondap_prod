-- 로컬 개발용 스키마 (테스트 관련 테이블)
--
-- 로컬 moondap DB 에는 밸런스게임 쪽 테이블만 있어서 /test/** 페이지가 전부 500 이 난다.
-- 이 파일은 MyBatis 매퍼(MdTestMapper.xml, MdTestCategoryMapper.xml, EgenTetoMapper.xml)와
-- DTO 에서 도출한 것이며, 화면을 띄워 확인하기 위한 최소 스키마다.
-- 운영 스키마와 타입·인덱스가 정확히 일치한다고 보장하지 않으므로 운영에 쓰지 말 것.
--
-- 적용:  mysql -u root -p moondap < local_dev_schema.sql
--
-- 테이블 이름이 정리되기 전(md_tests, balance_questions, site_statistics ...)에 만든
-- 로컬 DB 가 이미 있다면, 이 파일을 돌리기 전에 table_rename.sql 을 먼저 실행해야 한다.
-- CREATE TABLE IF NOT EXISTS 라서, 구 이름 테이블이 남아 있으면 그걸 건너뛰지 않고
-- 새 이름으로 빈 테이블을 하나 더 만들어 버린다(데이터는 구 이름 쪽에 그대로 남는다).
--
-- 콜레이션은 기존 md_balance_game 와 같은 utf8mb4_unicode_ci 로 맞춘다.
-- 서버 기본값(utf8mb4_0900_ai_ci)으로 만들면 두 테이블을 JOIN 할 때
-- "Illegal mix of collations" 로 조회가 실패한다.

CREATE TABLE IF NOT EXISTS md_content_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    icon          VARCHAR(100)          DEFAULT NULL,
    sort_order    INT                   DEFAULT 0,
    active        TINYINT(1)            DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS md_test (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    test_key        VARCHAR(100) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    thumbnail_image VARCHAR(500)          DEFAULT NULL,
    category        VARCHAR(50)           DEFAULT NULL,
    test_type       VARCHAR(20)           DEFAULT 'TYPE',
    estimated_time  INT                   DEFAULT NULL,
    -- 소문자 'active' 다. 애플리케이션이 "active".equals(...) 로 비교하므로
    -- 'ACTIVE' 로 넣으면 목록에는 뜨지만(MySQL 비교는 대소문자 구분 안 함)
    -- 상세 페이지에서 비공개로 판정돼 오류가 난다.
    status          VARCHAR(20)           DEFAULT 'active',
    created_by      VARCHAR(50)           DEFAULT NULL,
    play_count      INT                   DEFAULT 0,
    created_at      DATETIME              DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_key (test_key),
    KEY idx_category (category),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS md_test_question (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    test_id        BIGINT NOT NULL,
    question_order INT             DEFAULT 0,
    question_text  TEXT   NOT NULL,
    domain         VARCHAR(50)     DEFAULT NULL,
    is_reverse     TINYINT(1)      DEFAULT 0,
    is_active      TINYINT(1)      DEFAULT 1,
    created_at     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_test_id (test_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS md_test_result_type (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    test_id        BIGINT NOT NULL,
    result_title   VARCHAR(255) NOT NULL,
    result_content TEXT,
    result_image   VARCHAR(500) DEFAULT NULL,
    min_score      DOUBLE       DEFAULT NULL,
    max_score      DOUBLE       DEFAULT NULL,
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_test_id (test_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이 테이블만 camelCase 컬럼을 쓴다 (EgenTetoMapper.xml 이 그렇게 조회한다).
CREATE TABLE IF NOT EXISTS md_egenteto_play (
    userNo                   VARCHAR(50) NOT NULL,
    userName                 VARCHAR(100) DEFAULT NULL,
    gender                   VARCHAR(10)  DEFAULT NULL,
    testDate                 DATETIME     DEFAULT CURRENT_TIMESTAMP,
    testResultType           VARCHAR(50)  DEFAULT NULL,
    styleSelfcareResultType  VARCHAR(50)  DEFAULT NULL,
    socialSkillResultType    VARCHAR(50)  DEFAULT NULL,
    innerTendencyResultType  VARCHAR(50)  DEFAULT NULL,
    ambitionResultType       VARCHAR(50)  DEFAULT NULL,
    zScore                   INT          DEFAULT 0,
    topPercent               INT          DEFAULT 0,
    tetoScore                INT          DEFAULT 0,
    egenScore                INT          DEFAULT 0,
    styleSelfcarePoint       INT          DEFAULT 0,
    socialSkillPoint         INT          DEFAULT 0,
    innerTendencyPoint       INT          DEFAULT 0,
    ambitionPoint            INT          DEFAULT 0,
    isTesterMyself           VARCHAR(10)  DEFAULT NULL,
    PRIMARY KEY (userNo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 방문 로그. (날짜, IP) 조합으로 중복 방문을 한 번만 센다.
CREATE TABLE IF NOT EXISTS md_visit_log (
    visit_date DATE        NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    PRIMARY KEY (visit_date, ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일자별 집계. 방문수와 참여수를 같은 행에 쌓는다(SiteStatMapper.xml).
CREATE TABLE IF NOT EXISTS md_visit_daily (
    visit_date          DATE   NOT NULL,
    visit_count         BIGINT DEFAULT 0,
    participation_count BIGINT DEFAULT 0,
    PRIMARY KEY (visit_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 로컬 md_balance_game 가 구버전이라 현재 매퍼가 쓰는 컬럼이 빠져 있다.
-- (MySQL 8 은 ADD COLUMN IF NOT EXISTS 를 지원하지 않아 존재 확인 후 실행한다.)
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='md_balance_game' AND COLUMN_NAME='status') = 0,
  "ALTER TABLE md_balance_game ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active'",
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- md_visit_daily 도 방문수만 있는 구버전이 남아 있을 수 있다.
-- 이 컬럼이 없으면 투표할 때마다 500 이 난다
-- (StandardBalanceGameService.vote -> MdStatService.incrementParticipationCount).
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='md_visit_daily' AND COLUMN_NAME='participation_count') = 0,
  'ALTER TABLE md_visit_daily ADD COLUMN participation_count BIGINT DEFAULT 0',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
