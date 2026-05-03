package com.moondap.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moondap.mapper.SiteStatMapper;

@Service
public class MdStatService implements StatService {

    @Autowired
    private SiteStatMapper siteStatMapper;

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
