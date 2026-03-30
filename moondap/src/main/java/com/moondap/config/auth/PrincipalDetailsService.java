package com.moondap.config.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.moondap.dto.MdUserDTO;
import com.moondap.mapper.MdUserMapper;

@Service
public class PrincipalDetailsService implements UserDetailsService {

	@Autowired
	private MdUserMapper mdUserMapper;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		System.out.println("PrincipalDetailsService 진입 : " + username);
		MdUserDTO userEntity = mdUserMapper.selectUserName(username);

		if (userEntity != null) {
			return new PrincipalDetails(userEntity);
		}

		return null;
	}

}
