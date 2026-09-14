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

		// 방문 집계는 VisitLogInterceptor 가 전 페이지에서 담당한다.
		// 여기서 또 세면 메인 방문만 두 번 집계된다.

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
		java.util.List<MdContentItemDTO> popularAllTests = mdTestUserService.getAllContentList("all", allSort, "all", 0, 6, false);

		// 심리테스트 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularNormalTests = mdTestUserService.getAllContentList("all", normalSort, "NORMAL", 0, 6, false);

		// 밸런스 게임 상위 6개 조회 (독립 정렬)
		java.util.List<MdContentItemDTO> popularBalanceTests = mdTestUserService.getAllContentList("all", balanceSort, "BALANCE", 0, 6, false);
		
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
		model.addAttribute("totalParticipantCount", totalParticipantCount());
		model.addAttribute("seo", SeoMetaDTO.of(
				"광고 · 제휴 문의 - 문답",
				"문답(moondap)의 배너 광고, 브랜드 테스트 제작, 제휴 마케팅 문의를 받고 있습니다. 매체 소개와 참여자 지표를 확인해 보세요."));
		return "partnership";
	}

	/**
	 * 서비스 소개.
	 *
	 * <p>운영 주체와 서비스 성격을 밝히는 페이지다. 애드센스 심사는 개인정보처리방침·이용약관과
	 * 함께 소개·문의 경로를 갖췄는지를 관행적으로 확인한다.
	 */
	@GetMapping("/about")
	public String about(Model model) {
		model.addAttribute("totalParticipantCount", totalParticipantCount());
		// 공개 콘텐츠 수. 정확한 총계 쿼리는 없고 이 화면은 어림수면 충분하므로,
		// 통합 목록을 충분히 큰 상한으로 한 번 읽어 센다(이 조회는 캐시된다).
		model.addAttribute("contentCount",
				mdTestUserService.getAllContentList("all", "latest", "all", 0, 1000, false).size());
		model.addAttribute("seo", SeoMetaDTO.of(
				"문답 소개 - 어떤 서비스인가요?",
				"문답(moondap)은 심리테스트, 밸런스 게임, 에겐테토 성격 검사를 한곳에 모은 서비스입니다. 서비스 소개와 운영 원칙을 안내합니다."));
		return "legal/about";
	}

	/**
	 * 문의 안내.
	 *
	 * <p>메일 발송 인프라가 없어 폼 대신 연락 채널을 안내한다. 폼을 두려면
	 * 메일 발송이나 문의 저장 테이블이 필요하다.
	 */
	@GetMapping("/contact")
	public String contact(Model model) {
		model.addAttribute("seo", SeoMetaDTO.of(
				"문의하기 - 문답",
				"문답(moondap)에 오류 제보, 콘텐츠 신고, 기능 제안, 개인정보 관련 요청을 보내는 방법을 안내합니다."));
		return "legal/contact";
	}

	/** 심리테스트·밸런스게임·에겐테토 누적 참여 수 합계 */
	private long totalParticipantCount() {
		long balanceGameTotalCount = balanceGameService.getTotalParticipantCount();
		long normalTestTotalCount = mdTestAdminService.getTotalPlayCount();
		java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
		long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();

		return balanceGameTotalCount + normalTestTotalCount + egenTetoTotalCount;
	}

}
