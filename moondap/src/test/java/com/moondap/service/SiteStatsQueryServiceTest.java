package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

import com.moondap.mapper.SiteStatMapper;

/**
 * 관리자 통계 조회.
 *
 * <p>경로·유입 집계는 나중에 추가된 테이블이라 마이그레이션을 아직 적용하지 않은
 * 서버에는 없다. 실제로 그 상태의 서버에서 /admin/stats 가 통째로 500 이 났다 —
 * 방문자 수도 추이도 보관 현황도 다 멀쩡했는데, 새로 붙인 표 하나 때문에
 * 화면 전체를 못 쓰게 된 것이다.
 */
@ExtendWith(MockitoExtension.class)
class SiteStatsQueryServiceTest {

    @Mock
    private SiteStatMapper siteStatMapper;

    @InjectMocks
    private SiteStatsQueryService service;

    @Test
    @DisplayName("[회귀] 경로 집계 테이블이 없어도 통계 화면은 살아 있다")
    void survivesMissingPathTable() {
        when(siteStatMapper.selectTopPaths(anyString(), anyString(), anyInt()))
                .thenThrow(missingTable("md_pageview_path"));
        when(siteStatMapper.selectTopEntryPaths(anyString(), anyString(), anyInt()))
                .thenThrow(missingTable("md_pageview_path"));

        assertThat(service.topPages(7, 15)).isEmpty();
        assertThat(service.topEntryPages(7, 15)).isEmpty();
    }

    @Test
    @DisplayName("[회귀] 유입 출처 테이블이 없어도 통계 화면은 살아 있다")
    void survivesMissingReferrerTable() {
        when(siteStatMapper.selectTopReferrers(anyString(), anyString(), anyInt()))
                .thenThrow(missingTable("md_referrer_daily"));

        assertThat(service.topReferrers(7, 15)).isEmpty();
    }

    private BadSqlGrammarException missingTable(String table) {
        return new BadSqlGrammarException("", "SELECT 1",
                new java.sql.SQLSyntaxErrorException("Table 'moondap." + table + "' doesn't exist"));
    }
}
