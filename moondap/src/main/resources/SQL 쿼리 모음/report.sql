-- 콘텐츠 신고
--
-- 댓글과 사용자 제작 콘텐츠(밸런스 게임 · 심리테스트)에 문제가 있어도 이용자가
-- 알릴 방법이 없었다. 발견 경로가 운영자가 직접 훑는 것뿐이라, 광고가 붙은
-- 페이지에 혐오·성적 표현이나 저작권 침해물이 오래 남을 수 있었다.
-- 금칙어 필터(ProfanityUtil)는 1차 방어일 뿐이고, 통과한 것은 여기서 잡는다.
--
-- 적용:  mysql -u <user> -p moondap < report.sql
--
-- 로그인 사용자는 user_id, 비로그인 사용자는 익명 쿠키(md_anon)의 anon_id 로 남긴다.
-- 둘 중 하나는 반드시 채워지므로, 같은 사람이 같은 대상을 반복 신고하는 것을 막을 수 있다.

CREATE TABLE IF NOT EXISTS md_content_report (
    no            BIGINT       NOT NULL AUTO_INCREMENT,

    -- 신고 대상. target_type 에 따라 target_id 의 의미가 달라진다.
    --   COMMENT : md_balance_comment.no
    --   BALANCE : md_balance_game.id  (예: BG000012)
    --   TEST    : md_test.test_key
    target_type   VARCHAR(20)  NOT NULL COMMENT 'COMMENT / BALANCE / TEST',
    target_id     VARCHAR(100) NOT NULL,

    -- 사유 코드. 화면의 선택지와 1:1로 대응한다.
    --   ABUSE(욕설·비방) / SEXUAL(음란) / HATE(혐오) / COPYRIGHT(저작권) /
    --   SPAM(도배·광고) / ETC(기타)
    reason_code   VARCHAR(20)  NOT NULL,
    detail        VARCHAR(500)          DEFAULT NULL COMMENT '신고자가 적은 추가 설명',

    -- 신고 시점의 대상 내용 스냅샷. 신고 후 원본이 지워져도 판단할 수 있어야 한다.
    target_snapshot VARCHAR(1000)       DEFAULT NULL,

    reporter_user_id VARCHAR(50)        DEFAULT NULL COMMENT '로그인 신고자',
    reporter_anon_id VARCHAR(64)        DEFAULT NULL COMMENT '비로그인 신고자 (md_anon 쿠키)',

    -- PENDING(접수) / RESOLVED(조치완료) / REJECTED(반려)
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    handled_by    VARCHAR(50)           DEFAULT NULL,
    handled_at    DATETIME              DEFAULT NULL,

    created_at    DATETIME              DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (no),

    -- 관리 화면은 미처리 건을 최신순으로 본다.
    KEY idx_report_status (status, created_at),
    -- 같은 대상에 신고가 몇 건 쌓였는지 집계한다.
    KEY idx_report_target (target_type, target_id),

    -- 같은 사람이 같은 대상을 여러 번 신고해 건수를 부풀리지 못하게 막는다.
    -- 로그인/익명 각각에 대해 하나씩 둔다(한쪽은 항상 NULL 이고, MySQL 은
    -- UNIQUE 인덱스에서 NULL 을 중복으로 보지 않는다).
    UNIQUE KEY uk_report_user (target_type, target_id, reporter_user_id),
    UNIQUE KEY uk_report_anon (target_type, target_id, reporter_anon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
