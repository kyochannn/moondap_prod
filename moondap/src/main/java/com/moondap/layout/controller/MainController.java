package com.moondap.layout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.moondap.service.BalanceGameService;
import com.moondap.service.EgenTetoService;
import com.moondap.service.StatService;

import jakarta.servlet.http.HttpServletRequest;

import com.moondap.service.MdTestCategoryService;
import com.moondap.service.MdTestUserService;
import com.moondap.service.MdTestAdminService;
import com.moondap.dto.MdContentItemDTO;
import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.common.CommonUtil;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {
	
	private final BalanceGameService balanceGameService;
	private final StatService statService;
	private final EgenTetoService egenTetoService;
	private final MdTestCategoryService categoryService;
	private final MdTestUserService mdTestUserService;
	private final MdTestAdminService mdTestAdminService;

	@GetMapping("/")
	public String index(@org.springframework.web.bind.annotation.RequestParam(value = "allSort", defaultValue = "popular") String allSort,
	                    @org.springframework.web.bind.annotation.RequestParam(value = "normalSort", defaultValue = "popular") String normalSort,
	                    @org.springframework.web.bind.annotation.RequestParam(value = "balanceSort", defaultValue = "popular") String balanceSort,
	                    HttpServletRequest request,
	                    Model model) {

		// 방문자 수 증가
		String ip = CommonUtil.getClientIp(request);
		statService.incrementVisitCount(ip);

		// 각 콘텐츠별 전체 참여자 수 조회
		long balanceGameTotalCount = balanceGameService.getTotalParticipantCount();
		long normalTestTotalCount = mdTestAdminService.getTotalPlayCount();
		
		// 에겐 테토 참여자 수 조회
		java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
		long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();

		// 전체 통합 참여자 수 계산
		long totalParticipantCount = balanceGameTotalCount + normalTestTotalCount + egenTetoTotalCount;
		long todayVisitCount = statService.getTodayVisitCount();

		// 전체 통합 상위 6개 조회
		java.util.List<MdContentItemDTO> popularAllTests = mdTestUserService.getAllContentList("all", allSort, "all", 0, 6);

		// 심리테스트 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularNormalTests = mdTestUserService.getAllContentList("all", normalSort, "NORMAL", 0, 6);

		// 밸런스 게임 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularBalanceTests = mdTestUserService.getAllContentList("all", balanceSort, "BALANCE", 0, 6);
		
		// 활성 카테고리 조회
		java.util.List<MdTestCategoryDTO> categories = categoryService.getActiveCategories();

		model.addAttribute("totalParticipantCount", totalParticipantCount);
		model.addAttribute("todayVisitCount", todayVisitCount);
		model.addAttribute("egenTetoTotalCount", egenTetoTotalCount);
		model.addAttribute("popularAllTests", popularAllTests);
		model.addAttribute("popularNormalTests", popularNormalTests);
		model.addAttribute("popularBalanceTests", popularBalanceTests);
		model.addAttribute("categories", categories);
		model.addAttribute("currentAllSort", allSort);
		model.addAttribute("currentNormalSort", normalSort);
		model.addAttribute("currentBalanceSort", balanceSort);

		if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
			return "index :: #main-content";
		}

		return "index";
	}

	@GetMapping("/privacy")
	public String privacy() {
		return "legal/privacy";
	}

	@GetMapping("/terms")
	public String terms() {
		return "legal/terms";
	}
	
}
