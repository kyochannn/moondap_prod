package com.moondap.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 신고 매퍼의 동적 SQL.
 *
 * <p>{@code <choose>} 와 {@code <where>} 안의 OGNL 식은 컴파일러가 검사하지 못하고,
 * 틀려도 예외 없이 조건만 빠진다. 중복 신고 확인에서 조건이 빠지면 모든 신고가
 * "이미 신고함"으로 막히거나 반대로 무제한 신고가 가능해지므로 고정한다.
 *
 * <p>{@code getBoundSql} 은 동적 SQL 을 평가하지만 DB 에 접속하지 않으므로,
 * 연결 가능한 DB 가 없는 테스트 환경에서도 검증할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MdReportMapperSqlTest {

    private static final String NS = "com.moondap.mapper.MdReportMapper.";

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private String sql(String statementId, Map<String, Object> params) {
        MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(NS + statementId);
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }

    private Map<String, Object> reporterParams(String userId, String anonId) {
        Map<String, Object> params = new HashMap<>();
        params.put("targetType", "COMMENT");
        params.put("targetId", "12");
        params.put("userId", userId);
        params.put("anonId", anonId);
        return params;
    }

    @Test
    @DisplayName("로그인 신고자는 계정으로 중복을 확인한다")
    void duplicateCheckUsesAccountWhenLoggedIn() {
        String generated = sql("countByReporter", reporterParams("tester", "anon-token"));

        assertThat(generated).contains("reporter_user_id = ?");
        assertThat(generated).doesNotContain("reporter_anon_id = ?");
    }

    @Test
    @DisplayName("비로그인 신고자는 익명 토큰으로 중복을 확인한다")
    void duplicateCheckUsesAnonWhenNotLoggedIn() {
        String generated = sql("countByReporter", reporterParams(null, "anon-token"));

        assertThat(generated).contains("reporter_anon_id = ?");
        assertThat(generated).doesNotContain("reporter_user_id = ?");
    }

    @Test
    @DisplayName("빈 문자열 계정도 비로그인으로 본다")
    void treatsBlankUserIdAsAnonymous() {
        // userId 가 "" 로 들어오면 로그인으로 착각해 모든 익명 신고가 한 덩어리가 된다.
        String generated = sql("countByReporter", reporterParams("", "anon-token"));

        assertThat(generated).contains("reporter_anon_id = ?");
    }

    @Test
    @DisplayName("상태 필터가 있으면 조건이 붙고 'all' 이면 빠진다")
    void statusFilterIsOptional() {
        Map<String, Object> pending = new HashMap<>();
        pending.put("status", "PENDING");
        pending.put("offset", 0);
        pending.put("limit", 20);
        assertThat(sql("selectReports", pending)).contains("r.status = ?");

        Map<String, Object> all = new HashMap<>();
        all.put("status", "all");
        all.put("offset", 0);
        all.put("limit", 20);
        assertThat(sql("selectReports", all)).doesNotContain("r.status = ?");
    }

    @Test
    @DisplayName("목록은 같은 대상의 누적 신고 건수를 함께 센다")
    void listIncludesReportCount() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "PENDING");
        params.put("offset", 0);
        params.put("limit", 20);

        // 운영자가 우선순위를 가늠하는 값이라 빠지면 화면이 비어 보인다.
        assertThat(sql("selectReports", params)).contains("report_count");
    }
}
