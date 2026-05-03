package com.moondap.service;

public interface StatService {
    /**
     * 오늘 날짜의 방문자 수를 1 증가시킵니다. (IP 기반 중복 체크 적용)
     */
    void incrementVisitCount(String ipAddress);
    
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
