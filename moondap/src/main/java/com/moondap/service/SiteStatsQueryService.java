package com.moondap.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;
import com.moondap.dto.PagePathStatDTO;
import com.moondap.dto.ReferrerStatDTO;
import com.moondap.mapper.SiteStatMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 통계 조회.
 *
 * <p>집계(쓰기)는 {@link MdStatService} 가, 조회(읽기)는 여기가 맡는다. 한 클래스에 두면
 * 요청마다 도는 집계 코드와 관리자만 쓰는 조회 코드가 섞여 어느 쪽을 고쳐도 다른 쪽을
 * 신경 써야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteStatsQueryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SiteStatMapper siteStatMapper;

    /**
     * 최근 {@code days} 일의 일별 통계.
     *
     * <p>데이터가 없는 날은 0 으로 채워 반환한다. 비어 있는 날을 그냥 빼면 그래프의
     * 가로축 간격이 들쭉날쭉해져서, 방문이 없던 날과 그냥 하루가 지난 것을 구분할 수 없다.
     */
    public List<DailyStatDTO> dailyTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);

        Map<String, DailyStatDTO> found = new LinkedHashMap<>();
        for (DailyStatDTO row : siteStatMapper.selectDailyStats(from.format(DAY), today.format(DAY))) {
            found.put(row.getVisitDate(), row);
        }

        List<DailyStatDTO> filled = new ArrayList<>(days);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            String key = d.format(DAY);
            filled.add(found.getOrDefault(key, emptyDay(key)));
        }
        return filled;
    }

    /**
     * 시간대별 접속 분포. 0~23 시를 모두 채워 반환한다.
     *
     * <p>값이 있는 시각만 내려주면 새벽처럼 접속이 0 인 구간이 그래프에서 사라져,
     * "조용한 시간대"라는 정보 자체가 보이지 않는다.
     */
    public List<HourlyStatDTO> hourlyDistribution(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);

        Map<Integer, Long> found = new LinkedHashMap<>();
        for (HourlyStatDTO row : siteStatMapper.selectHourlyStats(from.format(DAY), today.format(DAY))) {
            found.put(row.getVisitHour(), row.getViewCount());
        }

        List<HourlyStatDTO> filled = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            HourlyStatDTO dto = new HourlyStatDTO();
            dto.setVisitHour(hour);
            dto.setViewCount(found.getOrDefault(hour, 0L));
            filled.add(dto);
        }
        return filled;
    }

    /** 최근 {@code days} 일 조회수 상위 경로. */
    public List<PagePathStatDTO> topPages(int days, int limit) {
        return orEmpty("경로별 조회수", () -> siteStatMapper.selectTopPaths(from(days), today(), limit));
    }

    /** 최근 {@code days} 일 유입 수 상위 경로. 바깥에서 이 화면으로 바로 들어온 횟수 기준. */
    public List<PagePathStatDTO> topEntryPages(int days, int limit) {
        return orEmpty("유입 경로", () -> siteStatMapper.selectTopEntryPaths(from(days), today(), limit));
    }

    /** 최근 {@code days} 일 유입 출처. */
    public List<ReferrerStatDTO> topReferrers(int days, int limit) {
        return orEmpty("유입 출처", () -> siteStatMapper.selectTopReferrers(from(days), today(), limit));
    }

    /**
     * 조회가 실패하면 빈 목록을 준다.
     *
     * <p>경로·유입 집계는 나중에 추가된 테이블이라, 마이그레이션을 아직 적용하지 않은
     * 서버에서는 없다. 그때 예외가 그대로 올라가면 방문자 수·추이·보관 현황까지
     * 들어 있는 통계 화면 <b>전체</b>가 500 이 된다. 부가 정보 하나 때문에 이미 잘 돌던
     * 화면을 못 쓰게 만들 이유가 없다.
     *
     * <p>집계 쪽({@code VisitLogInterceptor})이 예외를 삼키는 것과 같은 이유다.
     * 쓰기만 막아 두고 읽기를 빠뜨려서 실제로 이 화면이 500 이 났다.
     */
    private <T> List<T> orEmpty(String what, Supplier<List<T>> query) {
        try {
            return query.get();
        } catch (Exception e) {
            // 감싼 예외(BadSqlGrammarException)의 메시지는 비어 있다. 실제 원인까지
            // 풀어야 "Table ... doesn't exist" 가 로그에 남아 조치로 이어진다.
            log.warn("{} 조회 실패 — 빈 목록으로 대체한다. 마이그레이션(pageview_path.sql)을 적용했는지 확인할 것: {}",
                    what, NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            return List.of();
        }
    }

    /** 특정 날짜의 순 방문자 수. */
    public long visitorsOn(LocalDate date) {
        return siteStatMapper.selectVisitCount(date.format(DAY));
    }

    /** 오늘을 포함한 최근 {@code days} 일의 순 방문자 합계. */
    public long visitorsInLastDays(int days) {
        LocalDate today = LocalDate.now();
        return siteStatMapper.selectVisitSum(today.minusDays(days - 1L).format(DAY), today.format(DAY));
    }

    private String today() {
        return LocalDate.now().format(DAY);
    }

    /** 오늘을 포함해 {@code days} 일을 보므로 하루를 뺀다. */
    private String from(int days) {
        return LocalDate.now().minusDays(days - 1L).format(DAY);
    }

    private DailyStatDTO emptyDay(String date) {
        DailyStatDTO dto = new DailyStatDTO();
        dto.setVisitDate(date);
        dto.setVisitCount(0);
        dto.setParticipationCount(0);
        return dto;
    }
}
