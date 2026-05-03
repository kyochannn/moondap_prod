package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;

@Mapper
public interface BalanceGameMapper {

	// 밸런스 게임 목록 조회
	public List<BalanceGameDTO> selectBalanceGameList(@Param("isSpicy") String isSpicy, @Param("category") String category, @Param("status") String status, @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit) throws Exception;
	
	// 밸런스 게임 개별 조회
	public BalanceGameDTO selectBalanceGame(@Param("id") String id, @Param("isSpicy") String isSpicy, @Param("category") String category) throws Exception;
	
	// 밸런스 게임 ID 가장 큰 값 조회
	public String selectMaxBalanceGameId(@Param("isSpicy") String isSpicy, @Param("category") String category) throws Exception;
		
	// 밸런스 게임 ID 가장 작은 값 조회
	public String selectMinBalanceGameId() throws Exception;
	
	// 이전 / 다음 밸런스 게임 ID 조회
	public String nextOrPrevBalanceGameIdSelect(
			@Param("id") String id, 
			@Param("direction") String direction, 
			@Param("isSpicy") String isSpicy, 
			@Param("category") String category) throws Exception;
	
	// 밸런스 게임 투표
	public int vote(@Param("id") String id, @Param("option1Count") int option1Count, @Param("option2Count") int option2Count);
	
	// 밸런스 게임 댓글 추가
	public int insertBalanceGameComment(@Param("id") String id, @Param("nickname") String nickname, @Param("side") String side, @Param("content") String content, @Param("userId") String userId);

	// 밸런스 게임 댓글 개수
	public int selectBalanceGameCount(@Param("id") String id, @Param("isSpicy") String isSpicy, @Param("category") String category);
	
	// 밸런스 게임 관련 댓글 삭제
	public int deleteBalanceGameComment(String id) throws Exception;

	// 밸런스 게임 단일 댓글 삭제
	public int deleteSingleComment(@Param("no") int no) throws Exception;
	
	// 밸런스 게임 댓글 조회
	public List<BalanceGameCommentDTO> selectBalanceGameComment(String id) throws Exception;
	
	// 밸런스 게임 좋아요 수 수정
	public int updateBalanceGameCommentLikeCount(@Param("no") int no, @Param("id") String id, @Param("cnt") int cnt) throws Exception;

	// 밸런스 게임 등록
	public int insertBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 수정
	public int updateBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 삭제
	public int deleteBalanceGame(String id) throws Exception;

	public long selectTotalParticipantCount();
}
