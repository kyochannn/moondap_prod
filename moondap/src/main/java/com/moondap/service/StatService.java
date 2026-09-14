package com.moondap.service;

public interface StatService {
    /**
     * 오늘 날짜의 방문자 수를 1 증가시킵니다. (IP 기반 중복 체크 적용)
     */
    void incrementVisitCount(String ipAddress);

    /**
     * 방문 1건 기록.
     *
     * <p>순 방문자(하루 한 IP 당 한 번)와 시간대별 접속 건수를 함께 올린다.
     * 전자는 "몇 명이 왔나", 후자는 "언제 몰리나"를 본다.
     */
    void recordVisit(String ipAddress);
    
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
