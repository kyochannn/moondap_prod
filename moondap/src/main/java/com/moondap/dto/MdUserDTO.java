package com.moondap.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MdUserDTO {

	private Integer no;
	private String username;
	private String password;
	private String role;
	private LocalDateTime createdAt;
	 
}
