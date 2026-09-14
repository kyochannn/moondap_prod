package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.moondap.mapper.SiteStatMapper;

/**
 * 접속 기록 보유기간 관리.
 *
 * <p>처리방침에 90일이라고 고지해 두고 실제로는 지우지 않아, 넉 달치 5,815건이 남아 있던
 * 적이 있다. 식별자가 담긴 표가 늘어날 때 파기 대상에서 빠뜨리면 같은 일이 반복되므로,
 * 어느 표가 지워지는지를 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitLogRetentionServiceTest {

    @Mock
    private SiteStatMapper siteStatMapper;

    @InjectMocks
    private VisitLogRetentionService service;

    @Test
    @DisplayName("식별자가 담긴 표를 모두 파기한다")
    void purgesEveryIdentifyingTable() {
        when(siteStatMapper.deleteVisitLogsBefore(anyString())).thenReturn(10);
        when(siteStatMapper.deleteVisitCookiesBefore(anyString())).thenReturn(5);
        when(siteStatMapper.deleteVisitTrailsBefore(anyString())).thenReturn(60);

        assertThat(service.purgeExpired()).isEqualTo(75);

        // 셋 중 하나라도 빠지면 고지한 보유기간과 실제가 어긋난다.
        verify(siteStatMapper).deleteVisitLogsBefore(anyString());
        verify(siteStatMapper).deleteVisitCookiesBefore(anyString());
        verify(siteStatMapper).deleteVisitTrailsBefore(anyString());
    }

    @Test
    @DisplayName("[회귀] 나중에 추가된 표가 없어도 IP 파기는 진행된다")
    void purgesIpEvenWhenNewTablesMissing() {
        when(siteStatMapper.deleteVisitLogsBefore(anyString())).thenReturn(10);
        when(siteStatMapper.deleteVisitCookiesBefore(anyString()))
                .thenThrow(SiteStatsQueryServiceTest.tableMissing("md_visit_cookie"));
        when(siteStatMapper.deleteVisitTrailsBefore(anyString()))
                .thenThrow(SiteStatsQueryServiceTest.tableMissing("md_visit_trail"));

        // 여기서 예외가 올라가면 마이그레이션 전 서버는 IP 조차 파기할 수 없게 된다.
        assertThat(service.purgeExpired()).isEqualTo(10);
    }

    @Test
    @DisplayName("[회귀] 열람 경로 표가 없어도 화면은 빈 목록을 보여준다")
    void trailSurvivesMissingTable() {
        when(siteStatMapper.selectVisitTrail(anyString(), anyString(), anyInt()))
                .thenThrow(SiteStatsQueryServiceTest.tableMissing("md_visit_trail"));

        assertThat(service.trailOf("2026-09-15", "203.0.113.7")).isEmpty();
    }

    @Test
    @DisplayName("한 IP 의 열람 경로는 상한을 둔다")
    void limitsTrailRows() {
        service.trailOf("2026-09-15", "203.0.113.7");

        // 크롤러처럼 수천 건을 찍은 IP 하나 때문에 화면이 멈추면 안 된다.
        verify(siteStatMapper).selectVisitTrail("2026-09-15", "203.0.113.7",
                VisitLogRetentionService.TRAIL_LIMIT);
    }
}
