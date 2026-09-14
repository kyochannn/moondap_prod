package com.moondap.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.moondap.common.AnonymousIdentity;
import com.moondap.dto.MdReportDTO;
import com.moondap.dto.request.ReportRequest;
import com.moondap.service.MdReportService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 콘텐츠 신고 접수와 관리.
 *
 * <p>접수(/report)는 비로그인 이용자도 쓸 수 있어야 하므로 공개 경로다.
 * 관리(/admin/report/**)는 SecurityConfig 에서 ADMIN 으로 제한된다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MdReportController {

    private static final int PAGE_SIZE = 20;

    private final MdReportService mdReportService;

    /**
     * 신고 접수.
     *
     * <p>익명 신고자에게는 이 시점에 토큰을 발급한다. 댓글 작성과 같은 쿠키를 쓰므로
     * 이미 댓글을 단 적이 있다면 기존 토큰이 재사용된다.
     *
     * <p>검증 실패는 UserMessageException 으로 올라가 400 과 함께 사유가 전달된다.
     */
    @PostMapping("/report")
    @ResponseBody
    public Map<String, Object> report(@Valid @RequestBody ReportRequest request,
                                      HttpServletResponse response) {

        String anonId = AnonymousIdentity.getOrCreate(response);
        mdReportService.report(request, anonId);

        return Map.of("success", true, "message", "신고가 접수되었습니다. 검토 후 조치하겠습니다.");
    }

    // ── 관리 화면 ──────────────────────────────────────────────

    @GetMapping("/admin/report/list")
    public String reportList(@RequestParam(value = "status", defaultValue = "PENDING") String status,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {

        int safePage = Math.max(page, 0);
        List<MdReportDTO> reports = mdReportService.getReports(status, safePage * PAGE_SIZE, PAGE_SIZE);
        int total = mdReportService.countReports(status);

        model.addAttribute("reports", reports);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalCount", total);
        model.addAttribute("hasMore", (safePage + 1) * PAGE_SIZE < total);
        model.addAttribute("pendingCount", mdReportService.countPending());

        return "admin/report/reportList";
    }

    @PostMapping("/admin/report/{no}/status")
    @ResponseBody
    public Map<String, Object> updateStatus(@PathVariable("no") Long no,
                                            @RequestParam("status") String status) {
        mdReportService.updateStatus(no, status);
        return Map.of("success", true);
    }
}
