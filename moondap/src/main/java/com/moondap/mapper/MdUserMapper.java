package com.moondap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.MdUserDTO;

@Mapper
public interface MdUserMapper {

	// 사용자 조회
	public MdUserDTO selectUserName(@Param("username") String username);

	// 사용자 등록
	public int insertUser(@Param("user") MdUserDTO user);

	// 아이디 중복 확인 (이미 존재하면 1, 없으면 0 반환)
	public int countByUsername(@Param("username") String username);

	// 닉네임 중복 확인 (이미 존재하면 1, 없으면 0 반환)
	public int countByNickname(@Param("nickname") String nickname);
	
}
