package com.moondap.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moondap.dto.VisitLogDTO;
import com.moondap.mapper.SiteStatMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 접속 기록 보유기간 관리.
 *
 * <p>site_visit_log 에는 IP 주소가 남는다. 개인정보처리방침 제3조에 "수집일로부터 90일"
 * 로 고지했으므로 그 기간이 지난 기록은 파기해야 한다. 예전에는 지우는 코드가 아예 없어
 * 2026-05-03 부터의 기록이 그대로 쌓여 있었다.
 *
 * <p>파기 대상은 IP 가 담긴 site_visit_log 하나뿐이다. 일자별 집계(site_statistics)와
 * 시간대별 집계(site_visit_hourly)는 날짜와 숫자만 있어 개인을 식별할 수 없으므로 남긴다.
 * 그래서 파기해도 과거 추이 그래프는 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitLogRetentionService {

    /** 처리방침에 고지한 보유기간. 이 값을 바꾸면 privacy.html 도 함께 고쳐야 한다. */
    public static final int RETENTION_DAYS = 90;

    /**
     * 하나의 화면에 보여줄 접속 기록 수.
     *
     * <p>하루 방문자가 100~150명대라 300 이면 대부분의 날은 한 화면에 다 들어온다.
     * 페이지를 넘기며 찾는 것보다 브라우저 검색(Ctrl+F)으로 훑는 쪽이 빠르다.
     */
    public static final int PAGE_SIZE = 300;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SiteStatMapper siteStatMapper;

    /** 보관 중인 접속 기록 수. */
    public long storedCount() {
        return siteStatMapper.countVisitLogs();
    }

    /** 보유기간이 지나 파기 대상인 기록 수. */
    public long expiredCount() {
        return siteStatMapper.countVisitLogsBefore(cutoff().format(DAY));
    }

    /** 가장 오래된 기록의 날짜. 기록이 없으면 null. */
    public String oldestDate() {
        return siteStatMapper.selectOldestVisitLogDate();
    }

    /**
     * 보유기간이 지난 접속 기록을 파기한다.
     *
     * @return 지워진 행 수
     */
    public int purgeExpired() {
        String cutoffDate = cutoff().format(DAY);
        int deleted = siteStatMapper.deleteVisitLogsBefore(cutoffDate);

        // 개인정보 파기는 되돌릴 수 없다. 언제 몇 건을 지웠는지 로그로 남겨 둔다.
        log.info("접속 기록 파기: 기준일 {} 이전 {}건 삭제", cutoffDate, deleted);
        return deleted;
    }

    /**
     * 특정 날짜의 접속 기록.
     *
     * <p>개인정보 열람이므로 호출한 쪽(컨트롤러)에서 누가 언제 열었는지 로그를 남긴다.
     */
    public List<VisitLogDTO> logsOn(String visitDate, int page) {
        int offset = Math.max(0, page) * PAGE_SIZE;
        return siteStatMapper.selectVisitLogs(visitDate, offset, PAGE_SIZE);
    }

    /** 특정 날짜의 접속 IP 수. */
    public long countOn(String visitDate) {
        return siteStatMapper.countVisitLogsOn(visitDate);
    }

    /** 이 날짜보다 앞선 기록이 파기 대상이다. */
    private LocalDate cutoff() {
        return LocalDate.now().minusDays(RETENTION_DAYS);
    }
}
