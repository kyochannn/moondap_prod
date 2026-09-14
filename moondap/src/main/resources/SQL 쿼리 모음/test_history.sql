-- 내 결과 보관함
--
-- 테스트 결과가 어디에도 남지 않아서, 한 달 전에 한 테스트 결과를 다시 볼 방법이
-- 없었다. 마이페이지는 콘텐츠 제작자·관리자용 화면이라 일반 사용자에게는 로그인할
-- 이유 자체가 없었다.
--
-- 적용:  mysql -u <user> -p moondap < test_history.sql
--
-- 로그인 사용자는 user_id, 비로그인 사용자는 익명 쿠키(md_anon)의 anon_id 로 남긴다.
-- 둘 중 하나는 반드시 채워진다.

CREATE TABLE IF NOT EXISTS md_test_play (
    no          BIGINT       NOT NULL AUTO_INCREMENT,

    test_key    VARCHAR(100) NOT NULL COMMENT '결과 URL 재구성용',
    test_id     BIGINT                DEFAULT NULL,

    -- 결과 화면을 그대로 되살리기 위한 값들. 결과가 지워졌을 때를 대비해
    -- 제목과 이미지를 그 시점 그대로 복사해 둔다(스냅샷).
    result_id      BIGINT             DEFAULT NULL,
    result_code    VARCHAR(100)       DEFAULT NULL,
    result_title   VARCHAR(255)       DEFAULT NULL,
    result_image   VARCHAR(500)       DEFAULT NULL,
    test_title     VARCHAR(255)       DEFAULT NULL,
    score          INT                DEFAULT NULL,

    user_id     VARCHAR(50)           DEFAULT NULL COMMENT '로그인 사용자',
    anon_id     VARCHAR(64)           DEFAULT NULL COMMENT '비로그인 사용자 (md_anon 쿠키)',

    created_at  DATETIME              DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (no),
    KEY idx_history_user (user_id, created_at),
    KEY idx_history_anon (anon_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
