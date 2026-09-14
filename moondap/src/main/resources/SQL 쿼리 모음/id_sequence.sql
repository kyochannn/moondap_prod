-- ============================================================
-- 서비스용 ID 채번 테이블
--
-- ⚠️ 애플리케이션 배포 "전에" 반드시 실행할 것.
--    이 테이블이 없으면 밸런스 게임 신규 등록이 실패한다.
--
-- [배경]
-- 기존 채번은 SELECT MAX(id) 결과에 +1 을 하는 방식이었고, 그 조회에
-- status = 'active' 필터가 걸려 있었다. 그래서 가장 큰 ID 를 가진 게임이
-- draft/inactive 이면 이미 존재하는 ID 를 다시 발급해 UNIQUE 제약에 걸렸다.
--
-- 실제로 운영 데이터가 이 상태였다:
--   전체 MAX  = BG00010 (draft)
--   active MAX = BG00009  →  다음 발급 BG00010  →  중복
--
-- 동시 등록 시의 경쟁 조건도 함께 있었다(둘 다 같은 MAX 를 읽음).
-- ============================================================

CREATE TABLE IF NOT EXISTS moondap.md_id_sequence (
    seq_name   VARCHAR(50) NOT NULL PRIMARY KEY COMMENT '시퀀스 이름',
    next_val   BIGINT      NOT NULL             COMMENT '다음에 발급할 번호',
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- 현재 존재하는 가장 큰 번호 + 1 로 초기화한다.
--   - status 를 따지지 않는다. 삭제되지 않은 모든 행이 대상이다.
--   - 'BG' + 숫자 형태만 센다. 다른 형식의 레거시 ID 가 있어도 안전하다.
--   - 이미 행이 있으면 건드리지 않는다(재실행해도 값이 되돌아가지 않음).
INSERT INTO moondap.md_id_sequence (seq_name, next_val)
SELECT 'balance_game',
       COALESCE(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1
  FROM moondap.md_balance_game
 WHERE id REGEXP '^BG[0-9]+$'
ON DUPLICATE KEY UPDATE next_val = next_val;


-- 초기화 결과 확인용 (실행 후 값이 기대와 맞는지 볼 것)
-- SELECT * FROM moondap.md_id_sequence;
--
-- 기대값: md_balance_game 의 가장 큰 BG 번호 + 1
-- SELECT COALESCE(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1 AS expected
--   FROM moondap.md_balance_game WHERE id REGEXP '^BG[0-9]+$';
