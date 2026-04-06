package com.moondap.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moondap.dto.MdUserDTO;
import com.moondap.mapper.MdUserMapper;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class StandardMdUserService {

	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;

	@Autowired
	private MdUserMapper mdUserMapper;

	@Value("${admin.secret-key:MOONDAP_ADMIN_2026}")
	private String adminSecretKey;

	/**
	 * 아이디 중복 여부 확인
	 */
	public boolean isUsernameDuplicate(String username) {
		return mdUserMapper.countByUsername(username) > 0;
	}

	/**
	 * 닉네임 중복 여부 확인
	 */
	public boolean isNicknameDuplicate(String nickname) {
		return mdUserMapper.countByNickname(nickname) > 0;
	}

	public void joinProc(MdUserDTO user) {
		
		// 0. 관리자 가입 시 시크릿 키 검사 (보안 강화)
		if ("ROLE_ADMIN".equals(user.getRole())) {
			if (user.getAdminKey() == null || !adminSecretKey.equals(user.getAdminKey())) {
				throw new RuntimeException("관리자 인증 코드가 올바르지 않습니다.");
			}
		}
		
		// 1. 아이디 중복 최종 검사
		if (isUsernameDuplicate(user.getUsername())) {
			throw new RuntimeException("이미 사용 중인 아이디입니다.");
		}
		
		// 2. 닉네임 중복 최종 검사
		if (isNicknameDuplicate(user.getNickname())) {
			throw new RuntimeException("이미 사용 중인 닉네임입니다.");
		}
		
		String rawPassword = user.getPassword();
		
		// 3. 비밀번호 유효성 최종 검사 (영문, 숫자, 특수문자 포함 8~20자)
		String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
		if (rawPassword == null || !rawPassword.matches(passwordRegex)) {
			throw new RuntimeException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
		}
		
		String encPassword = bCryptPasswordEncoder.encode(rawPassword);
		user.setPassword(encPassword);
		
		// 4. 이메일 유효성 검사 (입력된 경우에만)
		String email = user.getEmail();
		if (email != null && !email.isEmpty()) {
			String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
			if (!email.matches(emailRegex)) {
				throw new RuntimeException("올바른 이메일 형식이 아닙니다.");
			}
		}
		
		// 전달받은 권한이 없으면 기본값 ROLE_USER 설정
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_USER");
		}
		
		// 프로필 이미지 기본값 설정
		if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
			user.setProfileImage("/profile/default.png");
		}
		
		mdUserMapper.insertUser(user);
	}

	/**
	 * 관리자 시크릿 키 일치 여부 확인
	 */
	public boolean isAdminKeyCorrect(String adminKey) {
		log.info("Checking Admin Key - Server: [{}], Input: [{}]", adminSecretKey, adminKey);
		
		// 서버 설정 키가 비어있거나 null인 경우 보안을 위해 무조건 실패 처리
		if (adminSecretKey == null || adminSecretKey.trim().isEmpty()) {
			log.error("ADMIN_SECRET_KEY is NOT SET in application.properties!");
			return false;
		}
		
		return adminSecretKey.trim().equals(adminKey != null ? adminKey.trim() : "");
	}

}
