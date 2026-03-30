package com.moondap.service;

public interface StatService {
    /**
     * 오늘 날짜의 방문자 수를 1 증가시킵니다.
     */
    void incrementVisitCount();

    /**
     * 오늘 날짜의 방문자 수를 반환합니다.
     */
    long getTodayVisitCount();
}
