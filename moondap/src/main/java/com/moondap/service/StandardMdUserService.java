package com.moondap.service;

import lombok.RequiredArgsConstructor;

import com.moondap.common.exception.UserMessageException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moondap.dto.MdUserDTO;
import com.moondap.dto.request.ProfileUpdateRequest;
import com.moondap.mapper.MdUserMapper;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StandardMdUserService {

	private final BCryptPasswordEncoder bCryptPasswordEncoder;

	private final MdUserMapper mdUserMapper;

	private final com.moondap.config.auth.ActiveSessionService activeSessionService;

	// 기본값을 두지 않는다. 미설정 시 애플리케이션이 기동 단계에서 실패하도록 하여
	// 약한 기본 키로 조용히 동작하는 상황을 원천 차단한다.
	@Value("${admin.secret-key}")
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
			// isAdminKeyCorrect() 를 재사용한다. 예전에는 여기만 평문 equals 로 비교해서
			// 키 미설정 시 동작과 비교 방식(상수 시간)이 두 경로에서 달랐다.
			if (!isAdminKeyCorrect(user.getAdminKey())) {
				throw new UserMessageException("관리자 인증 코드가 올바르지 않습니다.");
			}
		}
		
		// 1. 아이디 중복 최종 검사
		if (isUsernameDuplicate(user.getUsername())) {
			throw new UserMessageException("이미 사용 중인 아이디입니다.");
		}
		
		// 2. 닉네임 중복 최종 검사
		if (isNicknameDuplicate(user.getNickname())) {
			throw new UserMessageException("이미 사용 중인 닉네임입니다.");
		}
		
		String rawPassword = user.getPassword();
		
		// 3. 비밀번호 유효성 최종 검사 (영문, 숫자, 특수문자 포함 8~20자)
		String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
		if (rawPassword == null || !rawPassword.matches(passwordRegex)) {
			throw new UserMessageException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
		}
		
		String encPassword = bCryptPasswordEncoder.encode(rawPassword);
		user.setPassword(encPassword);
		
		// 4. 이메일 유효성 검사 (입력된 경우에만)
		String email = user.getEmail();
		if (email != null && !email.isEmpty()) {
			String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
			if (!email.matches(emailRegex)) {
				throw new UserMessageException("올바른 이메일 형식이 아닙니다.");
			}
		}
		
		// 전달받은 권한이 없으면 기본값 ROLE_USER 설정
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_USER");
		}
		
		// 프로필 이미지 기본값 설정
		if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
			user.setProfileImage("default-profile-img.svg");
		}
		
		// 기본 상태 설정 (활성)
		user.setStatus("ACTIVE");
		
		mdUserMapper.insertUser(user);
	}

	/**
	 * 관리자 시크릿 키 일치 여부 확인
	 */
	public boolean isAdminKeyCorrect(String adminKey) {
		// 서버 설정 키가 비어있거나 null인 경우 보안을 위해 무조건 실패 처리
		if (adminSecretKey == null || adminSecretKey.trim().isEmpty()) {
			log.error("ADMIN_SECRET_KEY 환경변수가 설정되지 않았습니다. 관리자 가입이 차단됩니다.");
			return false;
		}

		if (adminKey == null) {
			return false;
		}

		// 타이밍 공격 방지를 위해 길이에 무관하게 전체를 비교한다.
		return MessageDigest.isEqual(
				adminSecretKey.trim().getBytes(StandardCharsets.UTF_8),
				adminKey.trim().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 사용자 본인의 프로필 수정.
	 *
	 * <p>DB 에서 읽은 행을 기준으로 <b>허용된 필드만 덮어쓴다.</b>
	 * role · status · point 는 이 경로에서 절대 바뀌지 않는다.
	 *
	 * <p>이전에는 요청에서 바인딩한 MdUserDTO 를 그대로 UPDATE 에 넘겨서,
	 * role=ROLE_ADMIN 을 실어 보내면 관리자로 승격됐고, 반대로 화면이 보내지 않는
	 * status/point 는 null 로 지워져 계정이 로그인 불가 상태가 됐다.
	 *
	 * @param newProfileImage 새로 업로드한 파일명. 변경이 없으면 null.
	 * @return 갱신된 사용자 정보(세션 갱신용)
	 */
	@Transactional
	public MdUserDTO updateProfile(String username, ProfileUpdateRequest request, String newProfileImage) {
		MdUserDTO existingUser = mdUserMapper.selectUserName(username);
		if (existingUser == null) {
			throw new UserMessageException("사용자를 찾을 수 없습니다.");
		}

		// 닉네임 변경 시 중복 검사
		if (request.getNickname() != null && !request.getNickname().equals(existingUser.getNickname())) {
			if (isNicknameDuplicate(request.getNickname())) {
				throw new UserMessageException("이미 사용 중인 닉네임입니다.");
			}
			existingUser.setNickname(request.getNickname());
		}

		existingUser.setEmail(request.getEmail());
		existingUser.setBio(request.getBio());

		if (newProfileImage != null && !newProfileImage.isBlank()) {
			existingUser.setProfileImage(newProfileImage);
		}

		// 비밀번호는 이 경로에서 다루지 않는다(updatePassword 사용).
		// null 로 두면 매퍼의 <if> 가 password 컬럼을 건드리지 않는다.
		existingUser.setPassword(null);

		mdUserMapper.updateUser(existingUser);

		// 호출부가 세션을 갱신할 수 있도록 실제 저장된 상태를 돌려준다.
		return mdUserMapper.selectUserName(username);
	}

	/**
	 * [Admin] 회원 정보 수정. 권한·상태·포인트 변경을 허용한다.
	 *
	 * <p>관리 화면이 보내지 않는 필드는 기존 값을 유지한다.
	 * (이전에는 bio 를 전송하지 않아 관리자가 수정할 때마다 자기소개가 지워졌다)
	 */
	@Transactional
	public void updateUserByAdmin(MdUserDTO user) {
		MdUserDTO existingUser = mdUserMapper.selectUserName(user.getUsername());
		if (existingUser == null) {
			throw new UserMessageException("사용자를 찾을 수 없습니다.");
		}

		// 닉네임 변경 시 중복 검사
		if (user.getNickname() != null && !user.getNickname().equals(existingUser.getNickname())) {
			if (isNicknameDuplicate(user.getNickname())) {
				throw new UserMessageException("이미 사용 중인 닉네임입니다.");
			}
			existingUser.setNickname(user.getNickname());
		}

		// 전달된 값만 반영하고 나머지는 기존 값을 유지한다.
		if (user.getEmail() != null) existingUser.setEmail(user.getEmail());
		if (user.getBio() != null) existingUser.setBio(user.getBio());
		if (user.getRole() != null && !user.getRole().isBlank()) existingUser.setRole(user.getRole());
		if (user.getStatus() != null && !user.getStatus().isBlank()) existingUser.setStatus(user.getStatus());
		if (user.getPoint() != null) existingUser.setPoint(user.getPoint());
		if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
			existingUser.setProfileImage(user.getProfileImage());
		}

		// 비밀번호 변경 시에만 암호화해서 반영
		if (user.getPassword() != null && !user.getPassword().isEmpty()) {
			String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
			if (!user.getPassword().matches(passwordRegex)) {
				throw new UserMessageException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
			}
			existingUser.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
		} else {
			existingUser.setPassword(null); // 매퍼가 password 컬럼을 건드리지 않게 한다
		}

		mdUserMapper.updateUser(existingUser);
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
		if (user == null) throw new UserMessageException("사용자를 찾을 수 없습니다.");
		
		String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
		if (!newPassword.matches(passwordRegex)) {
			throw new UserMessageException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
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
			// 이미 탈퇴(DELETED) 상태라면 진짜로 DB에서 삭제 (Hard Delete)
			if ("DELETED".equals(user.getStatus())) {
				mdUserMapper.deleteUserByUsername(username);
			} else {
				// 그렇지 않으면 탈퇴 상태로만 변경 (Soft Delete)
				user.setStatus("DELETED");
				mdUserMapper.updateUser(user);
			}

			// 상태만 바꾸면 이미 로그인한 세션은 그대로 살아 있다.
			// isEnabled() 는 로그인 시점에만 평가되기 때문이다.
			activeSessionService.expireSessions(username);
		}
	}

	/**
	 * [Admin] 사용자 정지/해제 토글
	 */
	@Transactional
	public void toggleSuspend(String username) {
		MdUserDTO user = mdUserMapper.selectUserName(username);
		if (user != null) {
			if ("SUSPENDED".equals(user.getStatus())) {
				user.setStatus("ACTIVE");
			} else {
				user.setStatus("SUSPENDED");
			}
			mdUserMapper.updateUser(user);

			// 정지된 사용자는 기존 세션도 끊는다. 해제(ACTIVE)일 때도 만료시켜
			// 오래된 세션이 남아 있지 않게 한다.
			activeSessionService.expireSessions(username);
		}
	}

}
