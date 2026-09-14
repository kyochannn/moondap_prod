package com.moondap.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;
import com.moondap.service.SiteStatsQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 접속 통계.
 *
 * <p>URL 이 {@code /admin/**} 이라 SecurityConfig 에서 이미 ADMIN 으로 막히지만,
 * {@code @PreAuthorize} 를 함께 둔다. URL 규칙은 경로를 바꾸는 순간 조용히 풀리는데,
 * 메서드에 붙은 것은 같이 따라온다.
 */
@Controller
@RequestMapping("/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MdStatsAdminController {

    /** 추이 그래프 기간. 30일이면 주 단위 패턴이 눈에 보인다. */
    private static final int TREND_DAYS = 30;

    /** 시간대 분포 기간. 하루치만 보면 그날의 우연에 좌우된다. */
    private static final int HOURLY_DAYS = 7;

    private final SiteStatsQueryService siteStatsQueryService;

    @GetMapping
    public String stats(Model model) {
        LocalDate today = LocalDate.now();

        List<DailyStatDTO> trend = siteStatsQueryService.dailyTrend(TREND_DAYS);
        List<HourlyStatDTO> hourly = siteStatsQueryService.hourlyDistribution(HOURLY_DAYS);

        long todayCount = siteStatsQueryService.visitorsOn(today);
        long yesterdayCount = siteStatsQueryService.visitorsOn(today.minusDays(1));

        model.addAttribute("trend", trend);
        model.addAttribute("hourly", hourly);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("yesterdayCount", yesterdayCount);
        model.addAttribute("diff", todayCount - yesterdayCount);
        model.addAttribute("last7", siteStatsQueryService.visitorsInLastDays(7));
        model.addAttribute("last30", siteStatsQueryService.visitorsInLastDays(TREND_DAYS));
        model.addAttribute("trendDays", TREND_DAYS);
        model.addAttribute("hourlyDays", HOURLY_DAYS);

        return "admin/stats/visitStats";
    }
}
