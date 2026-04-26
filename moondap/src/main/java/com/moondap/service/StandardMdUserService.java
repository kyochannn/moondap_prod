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
		
		// 기본 상태 설정 (활성)
		user.setStatus("ACTIVE");
		
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

	/**
	 * 회원 정보 수정
	 */
	@Transactional
	public void updateUser(MdUserDTO user) {
		MdUserDTO existingUser = mdUserMapper.selectUserName(user.getUsername());
		if (existingUser == null) {
			throw new RuntimeException("사용자를 찾을 수 없습니다.");
		}
		
		// 닉네임 변경 시 중복 검사
		if (user.getNickname() != null && !user.getNickname().equals(existingUser.getNickname())) {
			if (isNicknameDuplicate(user.getNickname())) {
				throw new RuntimeException("이미 사용 중인 닉네임입니다.");
			}
		}
		
		// 비밀번호 변경 시 암호화
		if (user.getPassword() != null && !user.getPassword().isEmpty()) {
			String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
			if (!user.getPassword().matches(passwordRegex)) {
				throw new RuntimeException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
			}
			user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
		} else {
			// 비밀번호를 입력하지 않은 경우 기존 비밀번호 유지
			user.setPassword(existingUser.getPassword());
		}
		
		// 필수 필드 유지
		if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
			user.setProfileImage(existingUser.getProfileImage());
		}
		
		mdUserMapper.updateUser(user);
	}

	/**
	 * 현재 비밀번호 일치 여부 확인
	 */
	public boolean checkPassword(String username, String rawPassword) {
		MdUserDTO user = mdUserMapper.selectUserName(username);
		if (user == null) return false;
		return bCryptPasswordEncoder.matches(rawPassword, user.getPassword());
	}

	/**
	 * 비밀번호만 변경
	 */
	@Transactional
	public void updatePassword(String username, String newPassword) {
		MdUserDTO user = mdUserMapper.selectUserName(username);
		if (user == null) throw new RuntimeException("사용자를 찾을 수 없습니다.");
		
		String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
		if (!newPassword.matches(passwordRegex)) {
			throw new RuntimeException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
		}
		
		user.setPassword(bCryptPasswordEncoder.encode(newPassword));
		mdUserMapper.updateUser(user);
	}

	/**
	 * [Admin] 모든 사용자 조회
	 */
	public java.util.List<MdUserDTO> getAllUsers() {
		return mdUserMapper.selectAllUsers();
	}

	/**
	 * [Admin] 사용자 삭제
	 */
	@Transactional
	public void deleteUser(String username) {
		MdUserDTO user = mdUserMapper.selectUserName(username);
		if (user != null) {
				user.setStatus("DELETED");
				mdUserMapper.updateUser(user);
		}
	}

}
