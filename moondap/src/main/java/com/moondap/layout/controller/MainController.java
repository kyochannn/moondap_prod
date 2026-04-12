package com.moondap.layout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.moondap.service.BalanceGameService;
import com.moondap.service.EgenTetoService;
import com.moondap.service.StatService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {
	
	private final BalanceGameService balanceGameService;
	private final StatService statService;
	private final EgenTetoService egenTetoService;

	@GetMapping("/")
	public String index(Model model) {
		// 방문자 수 1 증가
		statService.incrementVisitCount();

		// 밸런스 게임 전체 참여자 수 및 오늘 방문자 수 조회
		long totalParticipantCount = balanceGameService.getTotalParticipantCount();
		long todayVisitCount = statService.getTodayVisitCount();

		// 에겐 테토 참여자 수 조회
		java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
		long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();

		model.addAttribute("totalParticipantCount", totalParticipantCount);
		model.addAttribute("todayVisitCount", todayVisitCount);
		model.addAttribute("egenTetoTotalCount", egenTetoTotalCount);

		return "index";
	}
	
}
