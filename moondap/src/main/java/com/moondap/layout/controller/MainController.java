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
import com.moondap.dto.SeoMetaDTO;
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

		// 정렬 파라미터는 같은 문서의 다른 표현일 뿐이다.
		// canonical 은 SeoMetaInterceptor 가 쿼리스트링을 떼고 "/" 로 만든다.
		model.addAttribute("seo", SeoMetaDTO.of(
				"문답: 테스트로 만나는 또 다른 나",
				"심리테스트, 밸런스 게임, 에겐테토 테스트까지. 나도 몰랐던 나의 모습을 문답(moondap)에서 무료로 확인해 보세요."));

		if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
			return "index :: #main-content";
		}

		return "index";
	}

	@GetMapping("/privacy")
	public String privacy(Model model) {
		model.addAttribute("seo", SeoMetaDTO.of(
				"개인정보처리방침 - 문답",
				"문답(moondap)이 수집하는 개인정보 항목, 이용 목적, 보유 기간과 제3자 광고 쿠키 사용에 관한 안내입니다."));
		return "legal/privacy";
	}

	@GetMapping("/terms")
	public String terms(Model model) {
		model.addAttribute("seo", SeoMetaDTO.of(
				"이용약관 - 문답",
				"문답(moondap) 서비스 이용에 관한 조건과 절차, 이용자와 운영자의 권리·의무를 정한 약관입니다."));
		return "legal/terms";
	}

	@GetMapping("/partnership")
	public String partnership(Model model) {
		// 전체 참여자 수 집계
		long balanceGameTotalCount = balanceGameService.getTotalParticipantCount();
		long normalTestTotalCount = mdTestAdminService.getTotalPlayCount();
		java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
		long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();
		
		long totalParticipantCount = balanceGameTotalCount + normalTestTotalCount + egenTetoTotalCount;
		
		model.addAttribute("totalParticipantCount", totalParticipantCount);
		model.addAttribute("seo", SeoMetaDTO.of(
				"광고 · 제휴 문의 - 문답",
				"문답(moondap)의 배너 광고, 브랜드 테스트 제작, 제휴 마케팅 문의를 받고 있습니다. 매체 소개와 참여자 지표를 확인해 보세요."));
		return "partnership";
	}
	
}
