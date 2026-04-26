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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
public class MdUserController {

	@Autowired
	private StandardMdUserService standardMdUserService; 
	
	@Autowired
	private com.moondap.common.FileService fileService;	
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
	public String joinView(@RequestParam("role") String role, 
						  @RequestParam(value = "adminKey", required = false) String adminKey,
						  Model model, RedirectAttributes rttr) {
		
		// 관리자 가입 시 인증 코드 검증
		if ("ROLE_ADMIN".equals(role)) {
			if (!standardMdUserService.isAdminKeyCorrect(adminKey)) {
				rttr.addFlashAttribute("errorMessage", "관리자 인증 코드가 올바르지 않습니다.");
				return "redirect:/joinSelectView";
			}
			model.addAttribute("adminKey", adminKey);
		}
		
		model.addAttribute("role", role);
		return "/user/join";
	}
	
	/**
	 * 아이디 중복 체크 API (AJAX)
	 */
	@GetMapping("/checkUsername")
	@ResponseBody
	public boolean checkUsername(@RequestParam("username") String username) {
		return standardMdUserService.isUsernameDuplicate(username);
	}

	/**
	 * 닉네임 중복 체크 API (AJAX)
	 */
	@GetMapping("/checkNickname")
	@ResponseBody
	public boolean checkNickname(@RequestParam("nickname") String nickname) {
		return standardMdUserService.isNicknameDuplicate(nickname);
	}

	/**
	 * 관리자 인증 코드 일치 여부 확인 (AJAX)
	 * 캐싱 방지를 위해 POST 방식을 사용합니다.
	 */
	@PostMapping("/checkAdminKey")
	@ResponseBody
	public boolean checkAdminKey(@RequestParam("adminKey") String adminKey) {
		return standardMdUserService.isAdminKeyCorrect(adminKey);
	}

	/**
	 * 회원가입 처리 로직
	 */
	@PostMapping("/joinProc")
	public String joinProc(MdUserDTO user, 
						   @RequestParam(value = "profileFile", required = false) org.springframework.web.multipart.MultipartFile profileFile,
						   RedirectAttributes rttr) {
		
		try {
			// 프로필 이미지 처리
			if (profileFile != null && !profileFile.isEmpty()) {
				String savedFilename = fileService.upload(profileFile);
				user.setProfileImage(savedFilename);
			}

			standardMdUserService.joinProc(user);
			
			rttr.addFlashAttribute("nickname", user.getNickname());
			rttr.addFlashAttribute("joinSuccess", true);
			
			return "redirect:/joinCompleteView";
			
		} catch (Exception e) {
			log.error("Join Error: {}", e.getMessage());
			rttr.addFlashAttribute("errorMessage", e.getMessage());
			rttr.addFlashAttribute("user", user);
			rttr.addFlashAttribute("role", user.getRole());
			return "redirect:/joinViewAfterError";
		}
	}
	
	/**
	 * 에러 발생 후 다시 가입 폼으로 리다이렉트되는 경로
	 * (POST 전용인 joinView를 대신하여 가입 폼을 다시 보여줌)
	 */
	@GetMapping("/joinViewAfterError")
	public String joinViewAfterError(Model model) {
		// FlashAttribute로 전달된 정보를 모델에 담음
		if (model.containsAttribute("role")) {
			model.addAttribute("role", model.getAttribute("role"));
		}
		if (model.containsAttribute("user")) {
			model.addAttribute("user", model.getAttribute("user"));
		}
		return "/user/join";
	}
	
	/**
	 * 회원가입 완료 화면
	 * 직접적인 URL 호출(어뷰징) 방지를 위해 joinSuccess 플래그를 체크합니다.
	 */
	@GetMapping("/joinCompleteView")
	public String joinCompleteView(Model model) {
		
		// joinSuccess 속성이 모델(FlashMap)에 없으면 부적절한 직접 접근으로 간주하고 메인으로 보냄
		if (!model.containsAttribute("joinSuccess")) {
			return "redirect:/";
		}
		
		return "/user/joinComplete";
	}
	
}
