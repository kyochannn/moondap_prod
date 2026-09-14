-- 접속자별 열람 경로 (이동 동선)
--
-- 적용:  mysql -u <user> -p moondap < visit_trail.sql
--
-- 왜 만드는가 — 방문자당 평균 6.2 페이지를 보는데 참여율은 1~5% 다. 즉 "메인만 보고
-- 나간다"가 아니라 여러 화면을 둘러보고도 끝까지 가지 않는다는 뜻인데, 집계 수치만으로는
-- 어느 화면에서 멈추는지 알 수 없다. md_pageview_path 는 화면별 합계라 '한 사람이 어떤
-- 순서로 이동했는가'가 사라지기 때문이다.
--
-- ⚠ 이 표는 지금까지의 통계 테이블과 성격이 다르다.
--
--   다른 표들은 날짜와 숫자만 담아서 개인을 지목할 수 없었다. 여기에는 IP 와 열람한
--   화면과 시각이 한 줄에 모인다. 이것은 집계가 아니라 개인별 열람 기록이다.
--
--   그래서 다음이 함께 따라온다. 하나라도 빠지면 고지와 실제가 어긋난다.
--     · 개인정보처리방침 제2조에 수집 항목·목적을 명시  (완료)
--     · 제3조 보유기간 90일, 파기 대상에 포함            (VisitLogRetentionService)
--     · 관리자가 열람할 때마다 누가 언제 누구 것을 봤는지 로그  (MdStatsAdminController)
--
-- 규모: 하루 약 1,100행, 90일 보관 시 약 10만 행.

CREATE TABLE IF NOT EXISTS md_visit_trail (
    -- 같은 초에 여러 화면이 열리는 일이 있어 시각만으로는 순서가 정해지지 않는다.
    -- 순번을 PK 로 두어 기록된 차례가 그대로 남게 한다.
    no         BIGINT       NOT NULL AUTO_INCREMENT,

    visit_date DATE         NOT NULL,
    ip_address VARCHAR(45)  NOT NULL,

    -- md_pageview_path.path 와 같은 규칙(쿼리스트링 제외, 뒤 슬래시 제거, 191자).
    path       VARCHAR(191) NOT NULL,

    viewed_at  DATETIME     NOT NULL,

    PRIMARY KEY (no),

    -- 조회는 늘 "이 날짜의 이 IP" 다. 파기도 visit_date 로 지운다.
    KEY idx_trail_lookup (visit_date, ip_address, no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
