package com.moondap.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 매운맛 제외 조건이 실제 SQL 에 붙는지 검증한다.
 *
 * <p>{@code <if test="includeSpicy == false">} 는 OGNL 식이라 컴파일러가 검사하지
 * 못하고, 오타가 나도 조건이 조용히 빠질 뿐 예외가 발생하지 않는다. 그 경우 메인
 * 페이지에 매운맛이 그대로 노출되는데 화면만 봐서는 원인을 알기 어렵다.
 *
 * <p>{@code getBoundSql} 은 동적 SQL 을 실제로 평가하지만 DB 에 접속하지는 않으므로,
 * 연결 가능한 DB 가 없는 테스트 환경에서도 이 검증이 가능하다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SpicyFilterSqlTest {

    private static final String STATEMENT = "com.moondap.mapper.MdTestMapper.selectAllContentList";

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private String sqlFor(boolean includeSpicy) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", "all");
        params.put("sort", "popular");
        params.put("type", "all");
        params.put("offset", 0);
        params.put("limit", 6);
        params.put("includeSpicy", includeSpicy);

        MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(STATEMENT);
        BoundSql boundSql = statement.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("includeSpicy=false 면 매운맛 제외 조건이 붙는다")
    void excludesSpicyByDefault() {
        assertThat(sqlFor(false)).contains("bq.is_spicy = 0");
    }

    @Test
    @DisplayName("includeSpicy=true 면 매운맛 제외 조건이 빠진다")
    void includesSpicyWhenRequested() {
        assertThat(sqlFor(true)).doesNotContain("bq.is_spicy = 0");
    }

    @Test
    @DisplayName("매운맛 조건은 밸런스 게임에만 걸리고 심리테스트 조회는 그대로다")
    void doesNotAffectNormalTests() {
        String sql = sqlFor(false);

        // is_spicy 는 md_balance_game 에만 있는 컬럼이다. md_test 쪽 SELECT 에
        // 섞이면 쿼리 자체가 깨진다.
        assertThat(sql).contains("FROM md_test t");
        assertThat(sql).containsOnlyOnce("is_spicy = 0");
    }
}
