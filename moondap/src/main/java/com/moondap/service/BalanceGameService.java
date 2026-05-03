package com.moondap.service;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;

public interface BalanceGameService {

	public List<BalanceGameDTO> selectBalanceGameList(Map<String, String> request, int offset, int limit);
	
	public List<BalanceGameDTO> selectBalanceGameListByUser(String userId);
	
	public BalanceGameDTO selectBalanceGame(String id, String spicyFilter, String category) throws Exception;
	
	public String nextOrPrevBalanceGameIdSelect(Map<String, String> request) throws Exception;
	
	public BalanceGameDTO vote(Map<String, String> request) throws Exception;
	
	public List<BalanceGameCommentDTO> selectBalanceGameComment(String id) throws Exception;

	public List<BalanceGameCommentDTO> insertBalanceGameComment(Map<String, String> request) throws Exception; 
	
	public String deleteBalanceGameComment(String id) throws Exception;

	public String deleteSingleComment(int no) throws Exception;

	public List<BalanceGameCommentDTO> updateBalanceGameCommentLikeCount(Map<String, String> request) throws Exception;
	
	public String insertBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception;

	public String updateBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception;

	public String deleteBalanceGame(Map<String, String> params) throws Exception;

	public long getTotalParticipantCount();

	public boolean CheckMyTest(String id);
}