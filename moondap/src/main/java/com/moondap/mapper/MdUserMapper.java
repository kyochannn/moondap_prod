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

	// 사용자 정보 수정
	public int updateUser(@Param("user") MdUserDTO user);

	// [Admin] 모든 사용자 조회
	public java.util.List<MdUserDTO> selectAllUsers();

	// [Admin] 사용자 삭제
	public int deleteUserByUsername(@Param("username") String username);
}
