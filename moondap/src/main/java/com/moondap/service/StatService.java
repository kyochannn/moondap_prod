package com.moondap.service;

public interface StatService {
    /**
     * 오늘 날짜의 방문자 수를 1 증가시킵니다. (IP 기반 중복 체크 적용)
     */
    void incrementVisitCount(String ipAddress);

    /**
     * 방문 1건 기록.
     *
     * <p>네 가지를 함께 올린다. 한 번의 호출로 묶은 것은 집계 실패를 호출부 한 곳에서만
     * 다루기 위해서다 — 통계 한 건 때문에 화면이 열리지 않으면 안 된다.
     * <ul>
     *   <li>순 방문자(하루 한 IP 당 한 번) — "몇 명이 왔나"</li>
     *   <li>시간대별 접속 건수 — "언제 몰리나"</li>
     *   <li>경로별 조회수 — "무엇을 보나"</li>
     *   <li>유입 출처 — "어디서 오나"</li>
     * </ul>
     *
     * @param path           쿼리스트링을 뺀 경로
     * @param trafficSource  유입 출처 이름. <b>사이트 안에서의 이동이면 {@code null}</b>
     *                       ({@link com.moondap.common.TrafficSource} 참고)
     */
    void recordVisit(String ipAddress, String path, String trafficSource);
    
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
