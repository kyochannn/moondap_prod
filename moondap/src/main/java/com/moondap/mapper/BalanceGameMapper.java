package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;

@Mapper
public interface BalanceGameMapper {

	// 밸런스 게임 목록 조회
	public List<BalanceGameDTO> selectBalanceGameList(String isSpicy, String category) throws Exception;
	
	// 밸런스 게임 개별 조회
	public BalanceGameDTO selectBalanceGame(String id, String isSpicy, String category) throws Exception;
	
	// 밸런스 게임 ID 가장 큰 값 조회
	public String selectMaxBalanceGameId(String isSpicy, String category) throws Exception;
		
	// 밸런스 게임 ID 가장 작은 값 조회
	public String selectMinBalanceGameId() throws Exception;
	
	// 이전 / 다음 밸런스 게임 ID 조회
	public String nextOrPrevBalanceGameIdSelect(String id, String direction, String isSpicy, String category) throws Exception;
	
	// 밸런스 게임 투표
	public int vote(String id, int option1Count, int option2Count);
	
	// 밸런스 게임 댓글 추가
	public int insertBalanceGameComment(String id, String nickname, String side, String content);

	// 밸런스 게임 댓글 개수
	public int selectBalanceGameCount(String id, String isSpicy, String category);
	
	// 밸런스 게임 관련 댓글 삭제
	public int deleteBalanceGameComment(String id) throws Exception;
	
	// 밸런스 게임 댓글 조회
	public List<BalanceGameCommentDTO> selectBalanceGameComment(String id) throws Exception;
	
	// 밸런스 게임 좋아요 수 수정
	public int updateBalanceGameCommentLikeCount(int no, String id, int cnt) throws Exception;

	// 밸런스 게임 등록
	public int insertBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 수정
	public int updateBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 삭제
	public int deleteBalanceGame(String id) throws Exception;

	public long selectTotalParticipantCount();
}
