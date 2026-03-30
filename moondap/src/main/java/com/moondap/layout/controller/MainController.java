package com.moondap.layout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.moondap.service.BalanceGameService;
import com.moondap.service.StatService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {
	
	private final BalanceGameService balanceGameService;
	private final StatService statService;

	@GetMapping("/")
	public String index(Model model) {
		// 방문자 수 1 증가
		statService.incrementVisitCount();

		// 전체 참여자 수 및 오늘 방문자 수 조회
		long totalParticipantCount = balanceGameService.getTotalParticipantCount();
		long todayVisitCount = statService.getTodayVisitCount();

		model.addAttribute("totalParticipantCount", totalParticipantCount);
		model.addAttribute("todayVisitCount", todayVisitCount);

		return "index";
	}
	
}
