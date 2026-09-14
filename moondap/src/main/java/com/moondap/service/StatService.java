package com.moondap.service;

import com.moondap.dto.VisitContext;

public interface StatService {
    /**
     * 방문 1건 기록.
     *
     * <p>다섯 가지를 함께 올린다. 한 번의 호출로 묶은 것은 집계 실패를 호출부 한 곳에서만
     * 다루기 위해서다 — 통계 한 건 때문에 화면이 열리지 않으면 안 된다.
     * <ul>
     *   <li>IP 기준 순 방문자(하루 한 IP 당 한 번) — "몇 명이 왔나"</li>
     *   <li>쿠키 기준 순 방문자 — 같은 질문을 다른 기준으로 본다(IP 의 오차 확인용)</li>
     *   <li>시간대별 접속 건수 — "언제 몰리나"</li>
     *   <li>경로별 조회수 — "무엇을 보나"</li>
     *   <li>유입 출처 — "어디서 오나"</li>
     * </ul>
     */
    void recordVisit(VisitContext visit);

    /**
     * 사람으로 셀지 말지를 판정한 결과를 User-Agent 와 함께 남긴다.
     *
     * <p>봇으로 걸러낸 것도 기록한다. 그래야 "멀쩡한 브라우저를 봇으로 거르고 있지 않은가"를
     * 나중에 확인할 수 있다. 실제로 {@code "daum"} 이라는 조각 하나 때문에 다음 앱
     * 인앱 브라우저를 쓰는 방문자가 통째로 누락된 적이 있는데, 그때는 알아챌 방법이 없었다.
     *
     * @param counted 사람으로 셌으면 true, 봇으로 걸렀으면 false
     */
    void recordUserAgent(String userAgent, boolean counted);
    
    /**
     * 오늘 날짜의 콘텐츠 참여 수(투표/테스트완료)를 1 증가시킵니다.
     */
    void incrementParticipationCount();

    /**
     * 오늘 날짜의 방문자 수를 반환합니다.
     */
    long getTodayVisitCount();

    /**
     * 오늘 날짜의 콘텐츠 참여자 수를 반환합니다.
     */
    long getTodayParticipationCount();
}
