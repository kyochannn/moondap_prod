package com.moondap.service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.moondap.dto.VisitContext;
import com.moondap.mapper.SiteStatMapper;

@Service
@RequiredArgsConstructor
public class MdStatService implements StatService {

    private final SiteStatMapper siteStatMapper;

    @Override
    public void recordVisit(VisitContext visit) {
        LocalDateTime now = LocalDateTime.now();
        String today = now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 1. IP 기준 순 방문자. PK(visit_date, ip_address) + INSERT IGNORE 라
        //    같은 IP 가 하루에 몇 번을 들어와도 한 번만 센다.
        if (siteStatMapper.insertVisitLog(today, visit.ipAddress()) > 0) {
            siteStatMapper.upsertVisitCount(today);
        }

        // 2. 쿠키 기준 순 방문자. IP 기준을 대체하는 것이 아니라 나란히 둔다 —
        //    공유 IP(여러 명이 1명으로 합쳐짐)와 모바일 IP 변동(1명이 여러 명으로 갈림)이
        //    어느 방향으로 얼마나 틀리는지는 비교 대상이 있어야 보인다.
        if (visit.anonId() != null && !visit.anonId().isBlank()) {
            siteStatMapper.insertVisitCookie(today, visit.anonId());
        }

        // 3. 시간대별 접속 건수. 이쪽은 재방문도 센다 — 트래픽이 몰리는 시각을
        //    보는 지표라 같은 사람이 여러 번 들어온 것도 부하이자 관심이다.
        siteStatMapper.upsertHourlyView(today, now.getHour());

        // 4. 경로별 조회수. trafficSource 가 있으면 바깥에서 들어온 것이므로
        //    유입으로도 함께 센다(사이트 안에서의 이동은 null 로 온다).
        boolean entry = visit.trafficSource() != null;
        if (visit.path() != null && !visit.path().isBlank()) {
            siteStatMapper.upsertPathView(today, visit.path(), entry ? 1 : 0);

            // 4-1. 열람 경로. 화면별 합계로는 '한 사람이 어떤 순서로 이동했는가'가
            //      사라져서, 어느 화면에서 멈추는지 알 수 없다.
            //      IP 가 함께 남으므로 개인정보이고, 90일 파기 대상이다.
            siteStatMapper.insertVisitTrail(today, visit.ipAddress(), visit.path());
        }

        // 5. 유입 출처. 내부 이동까지 세면 "직접 유입"이 실제의 몇 배로 부풀어
        //    검색·SNS 의 기여가 묻힌다.
        if (entry) {
            siteStatMapper.upsertReferrerVisit(today, visit.trafficSource());
        }
    }

    /** User-Agent 컬럼 길이. */
    private static final int MAX_USER_AGENT = 191;

    /** User-Agent 를 아예 보내지 않는 쪽도 목록에 남아야 "무엇을 걸렀나"가 완성된다. */
    private static final String NO_USER_AGENT = "(User-Agent 없음)";

    @Override
    public void recordUserAgent(String userAgent, boolean counted) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String value = (userAgent == null || userAgent.isBlank()) ? NO_USER_AGENT : userAgent.trim();
        if (value.length() > MAX_USER_AGENT) {
            value = value.substring(0, MAX_USER_AGENT);
        }

        siteStatMapper.upsertUserAgent(today, value, counted ? 1 : 0);
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
