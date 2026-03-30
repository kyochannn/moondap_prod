package com.moondap.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.moondap.dto.MdUserDTO;
import com.moondap.mapper.MdUserMapper;

@Service
public class StandardMdUserService {

	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;

	@Autowired
	private MdUserMapper mdUserMapper;

	public void joinProc(MdUserDTO user) {
		String rawPassword = user.getPassword();
		String encPassword = bCryptPasswordEncoder.encode(rawPassword);
		user.setPassword(encPassword);
		
		// 전달받은 권한이 없으면 기본값 ROLE_USER 설정
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_USER");
		}
		
		mdUserMapper.insertUser(user);
	}

}
