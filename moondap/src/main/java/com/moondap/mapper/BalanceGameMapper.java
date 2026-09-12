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
	// 주의: status='active' 필터가 걸려 있다. "사용자에게 보여줄 최신 게임"을 찾는 용도이며,
	//      신규 ID 채번에 쓰면 안 된다(draft 가 최대값이면 중복 ID 를 발급한다).
	//      채번은 nextBalanceGameSequence() 를 사용할 것.
	public String selectMaxBalanceGameId(@Param("isSpicy") String isSpicy, @Param("category") String category) throws Exception;

	/**
	 * 밸런스 게임 ID 시퀀스를 1 증가시키고 그 값을 돌려준다.
	 *
	 * 결과는 파라미터 맵의 "value" 키에 담긴다(MyBatis selectKey).
	 * 반드시 트랜잭션 안에서 호출해야 한다. LAST_INSERT_ID() 가 커넥션 단위 상태이기 때문이다.
	 */
	public void nextBalanceGameSequence(java.util.Map<String, Object> param);
		
	
	// 이전 / 다음 밸런스 게임 ID 조회
	public String nextOrPrevBalanceGameIdSelect(
			@Param("id") String id, 
			@Param("direction") String direction, 
			@Param("isSpicy") String isSpicy, 
			@Param("category") String category) throws Exception;
	
	// 밸런스 게임 집계 반영.
	// 증감분을 호출부가 정한다. 첫 투표는 (+1,0,+1), 진영 변경은 (-1,+1,0) 처럼 쓴다.
	public int applyVote(@Param("id") String id,
			@Param("option1Delta") int option1Delta,
			@Param("option2Delta") int option2Delta,
			@Param("totalDelta") int totalDelta);

	// 투표 로그 기록. 이미 투표했다면 INSERT IGNORE 로 0 을 반환한다(중복 판정의 기준).
	public int insertVoteLog(@Param("questionId") String questionId, @Param("voterKey") String voterKey,
			@Param("side") String side);

	// 이 사용자가 해당 게임에 투표한 진영. 투표 기록이 없으면 null.
	// 기록은 있으나 진영을 모르는 예전 행이면 빈 값이 아니라 null 이 나온다.
	public String selectVotedSide(@Param("questionId") String questionId, @Param("voterKey") String voterKey);

	// 투표 기록 존재 여부 (진영 미기록 행과 구분하기 위해 따로 둔다)
	public int countVoteLog(@Param("questionId") String questionId, @Param("voterKey") String voterKey);

	// 투표 로그의 진영을 갱신한다(재투표 반영 및 예전 행 보정).
	public int updateVoteLogSide(@Param("questionId") String questionId, @Param("voterKey") String voterKey,
			@Param("side") String side);

	// 게임 삭제 시 관련 투표 로그 정리
	public int deleteVoteLogByQuestionId(@Param("questionId") String questionId);
	
	// 밸런스 게임 댓글 추가.
	// 생성된 PK(no)가 전달한 DTO 에 채워진다(useGeneratedKeys).
	public int insertBalanceGameComment(BalanceGameCommentDTO comment);

	
	// 밸런스 게임 관련 댓글 삭제
	public int deleteBalanceGameComment(String id) throws Exception;

	// 밸런스 게임 단일 댓글 삭제
	public int deleteSingleComment(@Param("no") int no) throws Exception;
	
	/**
	 * 밸런스 게임 댓글 조회.
	 *
	 * @param sort "popular" 면 좋아요순, 그 외에는 최신순
	 */
	public List<BalanceGameCommentDTO> selectBalanceGameComment(@Param("id") String id,
			@Param("sort") String sort,
			@Param("offset") int offset,
			@Param("limit") int limit) throws Exception;
	
	// 밸런스 게임 좋아요 수 증감 (증감분은 서버가 결정한다)
	public int updateBalanceGameCommentLikeCount(@Param("no") int no, @Param("id") String id, @Param("cnt") int cnt) throws Exception;

	// 좋아요 기록. 이미 눌렀다면 INSERT IGNORE 로 0 을 반환한다.
	public int insertCommentLike(@Param("commentNo") int commentNo, @Param("voterKey") String voterKey);

	// 좋아요 취소. 기록이 있었다면 1 을 반환한다.
	public int deleteCommentLike(@Param("commentNo") int commentNo, @Param("voterKey") String voterKey);

	// 특정 사용자가 좋아요를 누른 댓글 번호 목록 (화면의 하트 상태 표시용)
	public java.util.List<Integer> selectLikedCommentNos(@Param("questionId") String questionId,
			@Param("voterKey") String voterKey);

	// 댓글 단건 조회 (변경된 댓글만 응답하기 위함)
	public BalanceGameCommentDTO selectCommentByNo(@Param("no") int no);

	// 밸런스 게임 등록
	public int insertBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 수정
	public int updateBalanceGame(BalanceGameDTO balanceGame) throws Exception;
	
	// 밸런스 게임 삭제
	public int deleteBalanceGame(String id) throws Exception;

	public long selectTotalParticipantCount();
}
