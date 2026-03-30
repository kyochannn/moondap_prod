package com.moondap.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.moondap.dto.MdUserDTO;
import com.moondap.service.StandardMdUserService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
public class MdUserController {

	@Autowired
	private StandardMdUserService standardMdUserService; 
	
	/**
	 * 로그인 화면 접근
	 */
	@GetMapping("/loginView")
	public String loginView() {
		return "/user/login";
	}

	/**
	 * 회원가입 권한 선택 화면
	 */
	@GetMapping("/joinSelectView")
	public String joinSelectView() {
		return "/user/joinSelect";
	}
	
	/**
	 * 회원가입 화면 (권한 정보를 POST로 받음)
	 */
	@PostMapping("/joinView")
	public String joinView(@RequestParam("role") String role, Model model) {
		model.addAttribute("role", role);
		return "/user/join";
	}
	
	@PostMapping("/joinProc")
	public String joinProc(MdUserDTO user) {
		
		System.out.println(user.getUsername());
		standardMdUserService.joinProc(user);
		
		return "redirect:/loginView";
	}
	
	
}
