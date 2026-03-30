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
	
}
