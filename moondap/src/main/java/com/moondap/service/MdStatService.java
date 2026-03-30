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
    public void incrementVisitCount() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        siteStatMapper.upsertVisitCount(today);
    }

    @Override
    public long getTodayVisitCount() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return siteStatMapper.selectVisitCount(today);
    }
}
