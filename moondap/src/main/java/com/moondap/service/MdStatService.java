package com.moondap.service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.moondap.mapper.SiteStatMapper;

@Service
@RequiredArgsConstructor
public class MdStatService implements StatService {

    private final SiteStatMapper siteStatMapper;

    @Override
    public void incrementVisitCount(String ipAddress) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 1. 먼저 방문 로그 기록 시도 (INSERT IGNORE)
        int result = siteStatMapper.insertVisitLog(today, ipAddress);
        
        // 2. 신규 방문(affected rows > 0)인 경우에만 통계 숫자 증가
        if (result > 0) {
            siteStatMapper.upsertVisitCount(today);
        }
    }

    @Override
    public void recordVisit(String ipAddress, String path, String trafficSource) {
        LocalDateTime now = LocalDateTime.now();
        String today = now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 1. 순 방문자. PK(visit_date, ip_address) + INSERT IGNORE 라
        //    같은 IP 가 하루에 몇 번을 들어와도 한 번만 센다.
        if (siteStatMapper.insertVisitLog(today, ipAddress) > 0) {
            siteStatMapper.upsertVisitCount(today);
        }

        // 2. 시간대별 접속 건수. 이쪽은 재방문도 센다 — 트래픽이 몰리는 시각을
        //    보는 지표라 같은 사람이 여러 번 들어온 것도 부하이자 관심이다.
        siteStatMapper.upsertHourlyView(today, now.getHour());

        // 3. 경로별 조회수. trafficSource 가 있으면 바깥에서 들어온 것이므로
        //    유입으로도 함께 센다(사이트 안에서의 이동은 null 로 온다).
        boolean entry = trafficSource != null;
        if (path != null && !path.isBlank()) {
            siteStatMapper.upsertPathView(today, path, entry ? 1 : 0);
        }

        // 4. 유입 출처. 내부 이동까지 세면 "직접 유입"이 실제의 몇 배로 부풀어
        //    검색·SNS 의 기여가 묻힌다.
        if (entry) {
            siteStatMapper.upsertReferrerVisit(today, trafficSource);
        }
    }

    @Override
    public void incrementParticipationCount() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        siteStatMapper.upsertParticipationCount(today);
    }

    @Override
    public long getTodayVisitCount() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return siteStatMapper.selectVisitCount(today);
    }

    @Override
    public long getTodayParticipationCount() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return siteStatMapper.selectParticipationCount(today);
    }
}
