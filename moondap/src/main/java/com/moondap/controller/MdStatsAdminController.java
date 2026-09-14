package com.moondap.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;
import com.moondap.service.SiteStatsQueryService;
import com.moondap.service.VisitLogRetentionService;

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

    /**
     * 접속 기록을 파기할 수 있는 계정.
     *
     * <p>ADMIN 권한만으로는 부족하다고 보고 계정 하나로 좁혔다. 개인정보 파기는
     * 되돌릴 수 없는데, 관리자 계정은 앞으로 늘어날 수 있기 때문이다.
     * 계정명을 바꾸면 이 상수도 함께 바꿔야 한다.
     */
    private static final String PURGE_ALLOWED_USER = "mdadmin";

    private final SiteStatsQueryService siteStatsQueryService;
    private final VisitLogRetentionService visitLogRetentionService;

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

        // 접속 기록 보유 현황
        model.addAttribute("logStored", visitLogRetentionService.storedCount());
        model.addAttribute("logExpired", visitLogRetentionService.expiredCount());
        model.addAttribute("logOldest", visitLogRetentionService.oldestDate());
        model.addAttribute("retentionDays", VisitLogRetentionService.RETENTION_DAYS);
        model.addAttribute("canPurge", PURGE_ALLOWED_USER.equals(currentUsername()));

        return "admin/stats/visitStats";
    }

    /**
     * 보유기간이 지난 접속 기록 파기.
     *
     * <p>되돌릴 수 없는 작업이라 {@link #PURGE_ALLOWED_USER} 계정으로 제한한다.
     * 화면에서 버튼을 감추는 것만으로는 부족하다 — 주소를 알면 그대로 호출할 수 있으므로
     * 서버에서 막는다.
     */
    @PostMapping("/purge-logs")
    @PreAuthorize("hasRole('ADMIN') and authentication.name == '" + PURGE_ALLOWED_USER + "'")
    public String purgeLogs(RedirectAttributes redirectAttributes) {
        int deleted = visitLogRetentionService.purgeExpired();
        redirectAttributes.addFlashAttribute("purgeResult",
                VisitLogRetentionService.RETENTION_DAYS + "일이 지난 접속 기록 " + deleted + "건을 파기했습니다.");
        return "redirect:/admin/stats";
    }

    private String currentUsername() {
        return com.moondap.common.SecurityUtil.getCurrentUsername();
    }
}
