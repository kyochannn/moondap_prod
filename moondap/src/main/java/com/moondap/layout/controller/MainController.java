package com.moondap.layout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.moondap.service.BalanceGameService;
import com.moondap.service.EgenTetoService;
import com.moondap.service.StatService;
import com.moondap.service.MdTestCategoryService;
import com.moondap.service.MdTestUserService;
import com.moondap.dto.MdContentItemDTO;
import com.moondap.dto.MdTestCategoryDTO;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {
	
	private final BalanceGameService balanceGameService;
	private final StatService statService;
	private final EgenTetoService egenTetoService;
	private final MdTestCategoryService categoryService;
	private final MdTestUserService mdTestUserService;

	@GetMapping("/")
	public String index(@org.springframework.web.bind.annotation.RequestParam(value = "normalSort", defaultValue = "popular") String normalSort,
	                    @org.springframework.web.bind.annotation.RequestParam(value = "balanceSort", defaultValue = "popular") String balanceSort,
	                    jakarta.servlet.http.HttpServletRequest request,
	                    Model model) {
		// 방문자 수 1 증가
		statService.incrementVisitCount();

		// 밸런스 게임 전체 참여자 수 및 오늘 방문자 수 조회
		long totalParticipantCount = balanceGameService.getTotalParticipantCount();
		long todayVisitCount = statService.getTodayVisitCount();

		// 에겐 테토 참여자 수 조회
		java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
		long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();

		// 심리테스트 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularNormalTests = mdTestUserService.getAllContentList("all", normalSort, "NORMAL", 0, 6);

		// 밸런스 게임 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularBalanceTests = mdTestUserService.getAllContentList("all", balanceSort, "BALANCE", 0, 6);
		
		// 활성 카테고리 조회
		java.util.List<MdTestCategoryDTO> categories = categoryService.getActiveCategories();

		model.addAttribute("totalParticipantCount", totalParticipantCount);
		model.addAttribute("todayVisitCount", todayVisitCount);
		model.addAttribute("egenTetoTotalCount", egenTetoTotalCount);
		model.addAttribute("popularNormalTests", popularNormalTests);
		model.addAttribute("popularBalanceTests", popularBalanceTests);
		model.addAttribute("categories", categories);
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
