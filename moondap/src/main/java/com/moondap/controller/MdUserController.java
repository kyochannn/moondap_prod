package com.moondap.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.moondap.dto.MdUserDTO;
import com.moondap.service.StandardMdUserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class MdUserController {

	@Autowired
	private StandardMdUserService standardMdUserService; 
	
	/**
	 * 로그인 화면 접근
	 * 
	 * @return
	 */
	@GetMapping("/loginView")
	public String loginView() {
		
		return "/user/login";
	}
	
	/**
	 * 회원가입
	 * 
	 * @return
	 */
	@GetMapping("/joinView")
	public String joinView() {
		
		return "/user/join";
	}
	
	@PostMapping("/joinProc")
	public String joinProc(MdUserDTO user) {
		
		System.out.println(user.getUsername());
		standardMdUserService.joinProc(user);
		
		return "redirect:/loginView";
	}
	
	
}
