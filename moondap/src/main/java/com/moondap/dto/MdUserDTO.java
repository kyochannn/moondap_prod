package com.moondap.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MdUserDTO {

	private Integer no;
	private String username;
	private String password;
	private String nickname;
	private String profileImage;
	private String email;
	private String bio;
	private String role;
	private Integer point;
	private String status;
	private LocalDateTime lastLoginAt;
	private String adminKey; // 가입 시 전송되는 시크릿 키
	private LocalDateTime createdAt;
	 
}
