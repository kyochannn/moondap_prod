package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moondap.mapper.SiteStatMapper;

/**
 * 접속 기록 조회 건수.
 *
 * <p>요청 값을 그대로 LIMIT 에 넣으면 한 번에 전체를 끌어올 수 있다. 이 표에 담긴 것은
 * IP 라, 그렇게 되면 개인정보를 통째로 뽑아가는 통로가 된다. 허용값을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class VisitLogPageSizeTest {

    @Mock
    private SiteStatMapper siteStatMapper;

    @InjectMocks
    private VisitLogRetentionService service;

    @Test
    @DisplayName("허용된 건수는 그대로 쓴다")
    void allowsListedSizes() {
        assertThat(service.resolvePageSize(50)).isEqualTo(50);
        assertThat(service.resolvePageSize(100)).isEqualTo(100);
        assertThat(service.resolvePageSize(1000)).isEqualTo(1000);
    }

    @Test
    @DisplayName("목록에 없는 건수는 기본값으로 떨어진다")
    void fallsBackToDefault() {
        // 주소를 직접 고쳐 큰 값을 넣어도 전체가 나가지 않는다.
        assertThat(service.resolvePageSize(999999)).isEqualTo(100);
        assertThat(service.resolvePageSize(300)).isEqualTo(100);
        assertThat(service.resolvePageSize(0)).isEqualTo(100);
        assertThat(service.resolvePageSize(-1)).isEqualTo(100);
    }

    @Test
    @DisplayName("기본값은 100건이다")
    void defaultIsHundred() {
        assertThat(VisitLogRetentionService.DEFAULT_PAGE_SIZE).isEqualTo(100);
        assertThat(VisitLogRetentionService.PAGE_SIZES).containsExactly(50, 100, 1000);
    }
}
