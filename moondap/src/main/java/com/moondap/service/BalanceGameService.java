package com.moondap.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.request.AdjacentGameRequest;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.dto.request.BalanceGameSearchRequest;
import com.moondap.dto.request.CommentLikeRequest;
import com.moondap.dto.request.CommentRequest;
import com.moondap.dto.request.VoteRequest;

public interface BalanceGameService {

	public List<BalanceGameDTO> selectBalanceGameList(BalanceGameSearchRequest request);
	
	public List<BalanceGameDTO> selectBalanceGameListByUser(String userId);
	
	public BalanceGameDTO selectBalanceGame(String id, String spicyFilter, String category) throws Exception;
	
	public String nextOrPrevBalanceGameIdSelect(AdjacentGameRequest request) throws Exception;
	
	/**
	 * 밸런스 게임 투표.
	 *
	 * @param voterKey 투표자 식별자. 중복 투표 판정에 쓰인다.
	 *                 로그인 사용자는 "u:username", 비로그인은 "ip:주소" 형태다.
	 * @return 투표 반영 후(또는 중복이라 반영하지 않은) 최신 집계
	 */
	public BalanceGameDTO vote(VoteRequest request, String voterKey) throws Exception;
	
	/**
	 * 댓글 목록 조회.
	 *
	 * @param voterKey 요청자 식별자. likedByMe 표시에 사용한다. null 이면 전부 false.
	 */
	public List<BalanceGameCommentDTO> selectBalanceGameComment(String id, String voterKey) throws Exception;

	/**
	 * 댓글 등록.
	 *
	 * @param voterKey 투표자 식별자. 어느 진영에 투표했는지 서버가 확인하는 데 쓴다.
	 * @return 방금 만들어진 댓글 하나 (전체 목록이 아니다)
	 */
	public BalanceGameCommentDTO insertBalanceGameComment(CommentRequest request, String voterKey) throws Exception;
	
	public String deleteBalanceGameComment(String id) throws Exception;

	public String deleteSingleComment(int no) throws Exception;

	/**
	 * 댓글 좋아요 토글.
	 *
	 * <p>누를지 취소할지는 요청이 아니라 서버의 기록이 결정한다.
	 *
	 * @return 반영 후의 해당 댓글 하나
	 */
	public BalanceGameCommentDTO toggleCommentLike(CommentLikeRequest request, String voterKey) throws Exception;
	
	public String insertBalanceGame(BalanceGameForm form, MultipartFile option1Image, MultipartFile option2Image) throws Exception;

	public String updateBalanceGame(BalanceGameForm form, MultipartFile option1Image, MultipartFile option2Image) throws Exception;

	public String deleteBalanceGame(BalanceGameForm form) throws Exception;

	public long getTotalParticipantCount();

	public boolean CheckMyTest(String id);
}