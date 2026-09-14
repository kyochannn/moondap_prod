package com.moondap.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;
import com.moondap.mapper.SiteStatMapper;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 통계 조회.
 *
 * <p>집계(쓰기)는 {@link MdStatService} 가, 조회(읽기)는 여기가 맡는다. 한 클래스에 두면
 * 요청마다 도는 집계 코드와 관리자만 쓰는 조회 코드가 섞여 어느 쪽을 고쳐도 다른 쪽을
 * 신경 써야 한다.
 */
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

    /** 특정 날짜의 순 방문자 수. */
    public long visitorsOn(LocalDate date) {
        return siteStatMapper.selectVisitCount(date.format(DAY));
    }

    /** 오늘을 포함한 최근 {@code days} 일의 순 방문자 합계. */
    public long visitorsInLastDays(int days) {
        LocalDate today = LocalDate.now();
        return siteStatMapper.selectVisitSum(today.minusDays(days - 1L).format(DAY), today.format(DAY));
    }

    private DailyStatDTO emptyDay(String date) {
        DailyStatDTO dto = new DailyStatDTO();
        dto.setVisitDate(date);
        dto.setVisitCount(0);
        dto.setParticipationCount(0);
        return dto;
    }
}
