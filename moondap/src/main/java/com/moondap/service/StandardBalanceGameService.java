package com.moondap.service;

import lombok.RequiredArgsConstructor;

import com.moondap.common.exception.UserMessageException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataAccessException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.moondap.config.CacheConfig;
import org.springframework.web.multipart.MultipartFile;

import com.moondap.common.AnonymousIdentity;
import com.moondap.common.CommonUtil;
import com.moondap.common.FileService;
import com.moondap.common.ProfanityUtil;
import com.moondap.common.SecurityUtil;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.CommentPageDTO;
import com.moondap.dto.request.AdjacentGameRequest;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.dto.request.BalanceGameSearchRequest;
import com.moondap.dto.request.CommentLikeRequest;
import com.moondap.dto.request.CommentRequest;
import com.moondap.dto.request.VoteRequest;
import com.moondap.mapper.BalanceGameMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StandardBalanceGameService implements BalanceGameService {

    private static final String DEFAULT_IMAGE = "default-content-img.png";

    /**
     * 한 번에 내려주는 댓글 수.
     *
     * 화면에 한 번에 다 보여줄 필요가 없다. 20개면 스크롤 한두 번 분량이라
     * 더보기를 누르기 전까지 읽을 거리가 충분하다.
     */
    public static final int COMMENT_PAGE_SIZE = 20;

	private final BalanceGameMapper balanceGameMapper;
	private final FileService fileService;
	private final StatService statService;
	
    // 밸런스 게임 목록
	@Override
	public List<BalanceGameDTO> selectBalanceGameList(BalanceGameSearchRequest request) {
		log.info("========== 밸런스 게임 리스트 조회 시작 ==========");

		try {
            String isSpicy = request.getSpicyFilterOrDefault();

            List<BalanceGameDTO> balanceGameList = balanceGameMapper.selectBalanceGameList(
                    isSpicy, request.getCategory(), request.getStatus(), request.getUserId(),
                    request.getOffsetOrDefault(), request.getLimitOrDefault());

            if (balanceGameList == null || balanceGameList.isEmpty()) {
                log.info("조회된 데이터가 없습니다. 필터: {}, 카테고리: {}, 상태: {}, 사용자: {}",
                        isSpicy, request.getCategory(), request.getStatus(), request.getUserId());
                return Collections.emptyList();
            }

            return balanceGameList;
        } catch (DataAccessException e) {
            log.error("데이터베이스 접근 중 오류 발생: {}", e.getMessage());
            throw new UserMessageException("데이터베이스 조회를 실패했습니다.", e);
        } catch (Exception e) {
            log.error("서버 내부 에러 발생: {}", e.getMessage(), e);
            throw new UserMessageException("시스템 오류가 발생했습니다.");
        }
	}

    @Override
    public List<BalanceGameDTO> selectBalanceGameListByUser(String userId) {
        log.info("========== 사용자별 밸런스 게임 리스트 조회: {} ==========", userId);
        try {
            // 모든 상태(draft, active, inactive), 모든 카테고리 조회
            return balanceGameMapper.selectBalanceGameList(null, null, null, userId, 0, 1000);
        } catch (Exception e) {
            log.error("사용자별 밸런스 게임 조회 실패", e);
            return Collections.emptyList();
        }
    }    
	// 밸런스 게임 조회
    @Override
    public BalanceGameDTO selectBalanceGame(String id, String spicyFilter, String category) throws Exception {
    	log.info("========== 밸런스 게임 select ==========");
    	
    	if (CommonUtil.isNull(id)) {
    		id = balanceGameMapper.selectMaxBalanceGameId(spicyFilter, category);
    	}
    	
    	BalanceGameDTO balanceGame = balanceGameMapper.selectBalanceGame(id, spicyFilter, category);
    	
    	if (balanceGame == null) {
    		return null;
    	}
    	
    	// Service 계산 로직 - Null safe 처리를 위해 Integer에서 int로 변환 시 null 체크 추가
    	int o1Count = (balanceGame.getOption1Count() == null) ? 0 : balanceGame.getOption1Count();
    	int o2Count = (balanceGame.getOption2Count() == null) ? 0 : balanceGame.getOption2Count();
    	int totalCount = o1Count + o2Count;
    	
    	if (totalCount == 0) {
    	    balanceGame.setOption1Percent(50);
    	    balanceGame.setOption2Percent(50);
    	} else {
    	    int option1Percent = (o1Count * 100) / totalCount;
    	    int option2Percent = 100 - option1Percent;
    	    
    	    balanceGame.setOption1Percent(option1Percent);
    	    balanceGame.setOption2Percent(option2Percent);
    	}
    	
        return balanceGame;
    }
    
    // 다음 밸런스 게임 ID 조회
    @Override
    public String nextOrPrevBalanceGameIdSelect(AdjacentGameRequest request) throws Exception {
    	log.info("========== 다음 밸런스 게임 ID select ==========");

    	return balanceGameMapper.nextOrPrevBalanceGameIdSelect(
    			request.getId(), request.getDirection(), request.getSpicyFilter(), request.getCategory());
    }
    
    // 밸런스 게임 투표
    @Override
    @Transactional
    public BalanceGameDTO vote(VoteRequest request, String voterKey) throws Exception {
    	log.info("========== 밸런스 게임 vote ==========");

    	String id = request.getId();
    	String side = request.getSide();

    	if (CommonUtil.isNull(id) || CommonUtil.isNull(voterKey)) {
    		return null;
    	}

    	// 컨트롤러의 @Valid 가 1차로 막지만 여기서도 확인한다.
    	// 검증 애노테이션에만 의존하면, 이 서비스를 다른 경로에서 호출하거나
    	// @Valid 가 빠졌을 때 잘못된 값이 조용히 집계된다.
    	// (실제로 @Pattern 단독으로는 null 이 통과해 right 투표로 집계됐다)
    	if (!"left".equals(side) && !"right".equals(side)) {
    		log.warn("잘못된 투표 값: {}", side);
    		return null;
    	}

    	boolean left = request.isLeft();

    	// 이전 투표 기록을 먼저 읽는다. INSERT 이후에 읽으면 방금 넣은 값이 보인다.
    	String previous = balanceGameMapper.selectVotedSide(id, voterKey);

    	// UNIQUE(question_id, voter_key) + INSERT IGNORE 이므로
    	// 처음 투표하는 경우에만 1 이 반환된다.
    	boolean firstVote = balanceGameMapper.insertVoteLog(id, voterKey, side) == 1;

    	int updatedRows;
    	if (firstVote) {
    		// 새 표를 더한다. 총 투표수도 함께 증가한다.
    		updatedRows = balanceGameMapper.applyVote(id, left ? 1 : 0, left ? 0 : 1, 1);

    		// 실제로 집계된 투표만 참여수에 반영한다.
    		if (updatedRows == 1) {
    			statService.incrementParticipationCount();
    		}
    	} else if (side.equals(previous)) {
    		// 같은 진영에 다시 투표. 바뀔 것이 없다.
    		return selectBalanceGame(id, null, null);
    	} else if (previous == null) {
    		// selected_side 컬럼 추가 이전에 투표한 행이다.
    		// 그 표가 어느 컬럼에 들어갔는지 알 수 없어 옮길 수가 없다.
    		// 기록만 채우고 집계는 건드리지 않는다.
    		log.info("진영 미기록 투표 보정: id={}, side={}", id, side);
    		balanceGameMapper.updateVoteLogSide(id, voterKey, side);
    		return selectBalanceGame(id, null, null);
    	} else {
    		// [재투표] 마음을 바꾼 경우. 표를 새로 만들지 않고 옮긴다.
    		// 이전 진영에서 빼고 새 진영에 더하므로 총 투표수는 그대로다.
    		// 참여수도 올리지 않는다 — 새로운 참여가 아니라 기존 표의 이동이다.
    		log.info("투표 변경: id={}, {} -> {}", id, previous, side);
    		updatedRows = balanceGameMapper.applyVote(id, left ? 1 : -1, left ? -1 : 1, 0);
    		if (updatedRows == 1) {
    			balanceGameMapper.updateVoteLogSide(id, voterKey, side);
    		}
    	}

    	if (updatedRows == 1) {
    		return selectBalanceGame(id, null, null);
    	}

    	// 집계 대상이 없는데 로그만 남는 상황을 막기 위해 롤백시킨다.
    	throw new IllegalStateException("투표 대상 게임을 찾을 수 없습니다: " + id);
    }
    
	// 밸런스 게임 댓글 조회
    @Override
    public CommentPageDTO selectBalanceGameComment(String id, String voterKey, String sort, int offset)
    		throws Exception {
    	log.info("========== 밸런스 게임 댓글 select ==========");

    	// 한 건 더 요청해서 다음 페이지가 있는지 판단한다.
    	// COUNT 쿼리를 따로 치는 것보다 싸고, 마지막 페이지에서 빈 더보기가 남지 않는다.
    	int safeOffset = Math.max(0, offset);
    	List<BalanceGameCommentDTO> fetched =
    			balanceGameMapper.selectBalanceGameComment(id, sort, safeOffset, COMMENT_PAGE_SIZE + 1);

    	boolean hasMore = fetched.size() > COMMENT_PAGE_SIZE;
    	List<BalanceGameCommentDTO> comments = hasMore
    			? new java.util.ArrayList<>(fetched.subList(0, COMMENT_PAGE_SIZE))
    			: fetched;

    	if (comments.isEmpty()) {
    		return CommentPageDTO.of(comments, false, safeOffset);
    	}

    	// 이 요청자가 좋아요를 누른 댓글 번호들을 한 번에 읽어 표시한다.
    	// 예전에는 화면이 localStorage 로 판단해서 기기를 바꾸면 하트가 전부 풀렸다.
    	java.util.Set<Integer> liked = CommonUtil.isNull(voterKey)
    			? java.util.Collections.emptySet()
    			: new java.util.HashSet<>(balanceGameMapper.selectLikedCommentNos(id, voterKey));

    	for (BalanceGameCommentDTO comment : comments) {
    		comment.setLikedByMe(liked.contains(comment.getNo()));
    		comment.setDeletable(canDeleteComment(comment));
    	}

    	log.info("밸런스 게임 댓글 {}개 (offset={}, sort={}, hasMore={})",
    			comments.size(), safeOffset, sort, hasMore);
        return CommentPageDTO.of(comments, hasMore, safeOffset + comments.size());
    }
    
    // 밸런스 게임 댓글 달기
    @Override
    @Transactional
    public BalanceGameCommentDTO insertBalanceGameComment(CommentRequest request, String voterKey) throws Exception {
    	log.info("========== 밸런스 게임 댓글 추가 ==========");

    	String id = request.getId();
    	String nickname = request.getNickname();
    	String content = request.getContent();

    	// 필수값과 길이 제한은 CommentRequest 의 검증 애노테이션이 처리한다.
    	// 금칙어는 형식이 아니라 도메인 규칙이라 여기 남긴다.
    	if (ProfanityUtil.containsProfanity(nickname) || ProfanityUtil.containsProfanity(content)) {
    		throw new UserMessageException("금칙어가 포함된 내용을 입력할 수 없습니다.");
    	}

    	// [신뢰 경계] 댓글의 진영은 요청이 아니라 서버의 투표 기록이 결정한다.
    	// 예전에는 화면의 localStorage 값을 그대로 저장해서,
    	// 투표하지 않았거나 반대편에 투표한 사람도 원하는 진영으로 댓글을 달 수 있었다.
    	String side = resolveVotedSide(id, voterKey, request.getSide());

    	BalanceGameCommentDTO comment = new BalanceGameCommentDTO();
    	comment.setQuestionId(id);
    	comment.setNickname(nickname);
    	comment.setSelectedSide(side);
    	comment.setContent(content);
    	comment.setUserId(request.getUserId());
    	comment.setAnonId(request.getAnonId());

    	if (balanceGameMapper.insertBalanceGameComment(comment) != 1) {
    		return null;
    	}

    	// useGeneratedKeys 로 no 가 채워졌으므로 방금 만든 댓글만 다시 읽어 돌려준다.
    	// 전체 목록을 반환하면 화면이 통째로 다시 그려져 입력 중이던 내용과 스크롤이 날아간다.
    	BalanceGameCommentDTO created = balanceGameMapper.selectCommentByNo(comment.getNo());
    	if (created != null) {
    		created.setLikedByMe(false);
    		created.setDeletable(true); // 방금 본인이 작성한 댓글
    	}
    	return created;
    }

    /**
     * 댓글에 표시할 진영을 결정한다.
     *
     * <p>원칙은 "서버의 투표 기록을 따른다" 이다. 다만 selected_side 컬럼을 추가하기 전에
     * 투표한 사용자는 기록에 진영이 비어 있다. 그 경우에 한해 화면이 보낸 값을 한 번만
     * 받아들이고 기록을 보정한다(자가 복구). 투표 자체가 없으면 거절한다.
     */
    private String resolveVotedSide(String questionId, String voterKey, String clientSide) {
        String recorded = balanceGameMapper.selectVotedSide(questionId, voterKey);
        if (recorded != null && !recorded.isBlank()) {
            return recorded;
        }

        if (balanceGameMapper.countVoteLog(questionId, voterKey) == 0) {
            throw new UserMessageException("투표 후 댓글을 남길 수 있습니다.");
        }

        // 진영 미기록 행: 화면 값을 받아들이되 형식은 확인하고, 기록을 채워 둔다.
        if (!"left".equals(clientSide) && !"right".equals(clientSide)) {
            throw new UserMessageException("투표 후 댓글을 남길 수 있습니다.");
        }
        balanceGameMapper.updateVoteLogSide(questionId, voterKey, clientSide);
        log.info("진영 미기록 투표 보정: questionId={}, side={}", questionId, clientSide);
        return clientSide;
    }
    
    // 밸런스 게임 관련 댓글 삭제
    @Override
    @Transactional
    public String deleteBalanceGameComment(String id) throws Exception {
    	log.info("========== 밸런스 게임 댓글 삭제 ==========");
    	
    	// 게임 삭제에 딸린 정리 작업이라 권한/표시용 플래그 계산이 필요 없다.
    	balanceGameMapper.deleteBalanceGameComment(id);
    	return id;
    }

    // 밸런스 게임 단일 댓글 삭제 (관리자용)
    @Override
    @Transactional
    public String deleteSingleComment(int no) throws Exception {
        log.info("========== 밸런스 게임 단일 댓글 삭제: {} ==========", no);

        BalanceGameCommentDTO comment = balanceGameMapper.selectCommentByNo(no);
        if (comment == null) {
            return "FAIL";
        }

        // 예전에는 관리자만 지울 수 있어서, 익명 작성자는 자기 오타 하나도 고칠 수 없었다.
        if (!canDeleteComment(comment)) {
            throw new UserMessageException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        return balanceGameMapper.deleteSingleComment(no) == 1 ? "SUCCESS" : "FAIL";
    }
    
    // 밸런스 게임 좋아요 등록
    @Override
    @Transactional
    public BalanceGameCommentDTO toggleCommentLike(CommentLikeRequest request, String voterKey) throws Exception {
    	log.info("========== 댓글 좋아요 토글 ==========");

    	String id = request.getId();
    	int commentNo = request.getNo();

    	if (CommonUtil.isNull(id) || CommonUtil.isNull(voterKey)) {
    		return null;
    	}

    	// [중복 방지] 누를지 취소할지는 요청이 아니라 기록이 결정한다.
    	// 예전에는 화면이 보낸 setting('UP'/'DOWN') 을 그대로 믿고 ±1 해서,
    	// localStorage 를 지우거나 API 를 직접 호출하면 무한히 올릴 수 있었다.
    	boolean liked;
    	if (balanceGameMapper.insertCommentLike(commentNo, voterKey) == 1) {
    		// 처음 누른 경우
    		balanceGameMapper.updateBalanceGameCommentLikeCount(commentNo, id, 1);
    		liked = true;
    	} else {
    		// 이미 눌러둔 경우 → 취소
    		if (balanceGameMapper.deleteCommentLike(commentNo, voterKey) == 1) {
    			balanceGameMapper.updateBalanceGameCommentLikeCount(commentNo, id, -1);
    		}
    		liked = false;
    	}

    	// 전체 목록이 아니라 바뀐 댓글 하나만 돌려준다.
    	BalanceGameCommentDTO updated = balanceGameMapper.selectCommentByNo(commentNo);
    	if (updated == null) {
    		return null;
    	}
    	updated.setLikedByMe(liked);
    	updated.setDeletable(canDeleteComment(updated));
    	return updated;
    }

    /**
     * 이 요청자가 해당 댓글을 지울 수 있는지 판단한다.
     *
     * <p>관리자 / 로그인 작성자 본인 / 익명이라도 작성 당시의 토큰을 가진 브라우저.
     */
    private boolean canDeleteComment(BalanceGameCommentDTO comment) {
        if (SecurityUtil.isAdmin()) {
            return true;
        }
        String username = SecurityUtil.getCurrentUsername();
        if (username != null) {
            return username.equals(comment.getUserId());
        }
        String anonId = AnonymousIdentity.current();
        return anonId != null && anonId.equals(comment.getAnonId());
    }
	
    // 밸런스 게임 등록
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    public String insertBalanceGame(BalanceGameForm form, MultipartFile option1Image, MultipartFile option2Image) throws Exception {

        // 필수값·길이 제한은 BalanceGameForm 의 검증 애노테이션이 처리한다.
        // 금칙어는 형식이 아니라 도메인 규칙이라 여기 남긴다.
        validateProfanity(form);

    	String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);

    	if (CommonUtil.isNull(option1ImagePath) || CommonUtil.isNull(option2ImagePath)) {
            return null;
        }

    	// 검증을 모두 통과한 뒤에 채번한다. 먼저 뽑으면 중간에 반환될 때마다 번호가 버려진다.
    	String lastId = nextBalanceGameId();

    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
    	balanceGameDto.setTitle(form.getTitle());
        balanceGameDto.setIsSpicy(form.isSpicy());
        balanceGameDto.setCategory(form.getCategory());
        balanceGameDto.setStatus(form.getStatusOrDefault());
        balanceGameDto.setOption1Text(form.getOption1Text());
        balanceGameDto.setOption2Text(form.getOption2Text());
        balanceGameDto.setOption1ImagePath(option1ImagePath);
        balanceGameDto.setOption2ImagePath(option2ImagePath);
    	balanceGameDto.setId(lastId);
    	// 등록은 로그인 사용자만 가능하다(컨트롤러 + SecurityConfig 에서 차단).
    	// 여기서 한 번 더 확인해 소유자 없는 레코드가 생기지 않게 한다.
    	balanceGameDto.setUserId(SecurityUtil.requireCurrentUsername());
    	
    	// 신규 등록 시 카운트 초기화 (0) - NULL 방지
    	balanceGameDto.setOption1Count(0);
    	balanceGameDto.setOption2Count(0);
    	balanceGameDto.setTotalCount(0);
    	
    	int updatedRows = balanceGameMapper.insertBalanceGame(balanceGameDto);
    	if (updatedRows == 1) {
    		return lastId;
    	}
    	return null;
    }
    
    // 밸런스 게임 수정
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
	public String updateBalanceGame(BalanceGameForm form, MultipartFile option1Image, MultipartFile option2Image) throws Exception {
    	String id = form.getId();

        if (CommonUtil.isNull(id)) return null;

        // [보안] 권한 확인
        if (!CheckMyTest(id)) {
            throw new UserMessageException("해당 게임을 수정할 권한이 없습니다.");
        }

    	String oldOption1ImagePath = form.getOldOption1ImagePath();
    	String oldOption2ImagePath = form.getOldOption2ImagePath();

        validateProfanity(form);

        String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);

    	if (CommonUtil.isNull(option1ImagePath) || CommonUtil.isNull(option2ImagePath)) {
            return null;
        }

    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
        balanceGameDto.setId(id);
    	balanceGameDto.setTitle(form.getTitle());
        balanceGameDto.setIsSpicy(form.isSpicy());
        balanceGameDto.setCategory(form.getCategory());
        balanceGameDto.setStatus(form.getStatus());
        balanceGameDto.setOption1Text(form.getOption1Text());
        balanceGameDto.setOption2Text(form.getOption2Text());
        // 수정 시 소유자는 바꾸지 않는다. updateBalanceGame 쿼리에 user_id 가 없어
        // 원래도 반영되지 않던 값이라, 관리자가 남의 글을 고쳐도 소유권은 유지된다.

        if (DEFAULT_IMAGE.equals(option1ImagePath)) {
            option1ImagePath = oldOption1ImagePath;
        } 
        balanceGameDto.setOption1ImagePath(option1ImagePath);

        if (DEFAULT_IMAGE.equals(option2ImagePath)) {
            option2ImagePath = oldOption2ImagePath;
        } 
        balanceGameDto.setOption2ImagePath(option2ImagePath);
    	
    	int updatedRows = balanceGameMapper.updateBalanceGame(balanceGameDto);
    	if (updatedRows == 1) {
            // DB 성공 시에만 기존 파일 삭제
            if (oldOption1ImagePath != null && !option1ImagePath.equals(oldOption1ImagePath)) {
                fileService.deleteFile(oldOption1ImagePath);
            }
            if (oldOption2ImagePath != null && !option2ImagePath.equals(oldOption2ImagePath)) {
                fileService.deleteFile(oldOption2ImagePath);
            }
    		return id;
    	}
    	return null;
    }

    // 밸런스 게임 삭제
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
	public String deleteBalanceGame(BalanceGameForm form) throws Exception {
    	String id = form.getId();

    	if (CommonUtil.isNull(id)) return null;

        // [보안] 권한 확인
        if (!CheckMyTest(id)) {
            throw new UserMessageException("해당 게임을 삭제할 권한이 없습니다.");
        }

    	String oldOption1ImagePath = form.getOldOption1ImagePath();
    	String oldOption2ImagePath = form.getOldOption2ImagePath();

        // 관련 댓글 먼저 삭제 (트랜잭션 보장)
        deleteBalanceGameComment(id);

        // 투표 로그도 함께 정리한다. 남겨두면 같은 ID 가 재사용될 때
        // 새 게임에 투표하지 않은 사람이 중복 투표자로 오판된다.
        balanceGameMapper.deleteVoteLogByQuestionId(id);
    	
    	int updatedRows = balanceGameMapper.deleteBalanceGame(id);
    	if (updatedRows == 1) {
            // DB 삭제 성공 시 물리 파일 삭제
            if (CommonUtil.isNotNull(oldOption1ImagePath)) {
                fileService.deleteFile(oldOption1ImagePath);
            }
            if (CommonUtil.isNotNull(oldOption2ImagePath)) {
                fileService.deleteFile(oldOption2ImagePath);
            }
    		return id;
    	}
    	return null;
    }
    
    /**
     * 금칙어 검사.
     *
     * 길이·필수값 검증은 BalanceGameForm 의 애노테이션으로 옮겼다.
     * 금칙어는 서비스가 아는 도메인 규칙이라 여기 남긴다.
     */
    private void validateProfanity(BalanceGameForm form) {
        if (ProfanityUtil.containsProfanity(form.getTitle())
                || ProfanityUtil.containsProfanity(form.getOption1Text())
                || ProfanityUtil.containsProfanity(form.getOption2Text())) {
            throw new UserMessageException("금칙어가 포함된 내용을 입력할 수 없습니다.");
        }
    }

    @Override
    public boolean CheckMyTest(String id) {
        log.info("========== 권한 확인 시작: {} ==========", id);

        // 1. 로그인 상태가 아니면 무조건 권한 없음
        if (!SecurityUtil.isAuthenticated()) {
            log.warn("- 비로그인 사용자 접근 차단");
            return false;
        }

        // 2. 관리자 권한 확인 (ROLE_ADMIN 이면 아이디 상관없이 허용)
        if (SecurityUtil.isAdmin()) {
            log.info("- 관리자 권한 확인됨");
            return true;
        }

        // 3. 작성자 확인
        String currentUserId = SecurityUtil.getCurrentUsername();
        try {
            BalanceGameDTO balanceGame = balanceGameMapper.selectBalanceGame(id, null, null);
            // 작성자가 없는(익명) 콘텐츠는 관리자만 다룰 수 있으므로 여기서 false 다.
            if (balanceGame != null && balanceGame.getUserId() != null
                    && balanceGame.getUserId().equals(currentUserId)) {
                log.info("- 작성자 본인 확인됨: {}", currentUserId);
                return true;
            }
        } catch (Exception e) {
            log.error("권한 확인 중 데이터베이스 조회 오류: {}", e.getMessage());
        }

        log.warn("- 권한 없음: 현재사용자({}), 작성자(알 수 없음 또는 다름)", currentUserId);
        return false;
    }

    /** 서비스용 ID 접두사 */
    private static final String ID_PREFIX = "BG";

    /**
     * 다음 밸런스 게임 ID 를 채번한다.
     *
     * <p>이전 구현은 {@code SELECT MAX(id)} 에 +1 을 했는데, 그 조회에 status='active'
     * 필터가 걸려 있어서 가장 큰 ID 를 가진 게임이 draft 이면 이미 존재하는 ID 를 다시
     * 발급했다(UNIQUE 위반으로 등록 실패). 동시 등록 시 두 요청이 같은 MAX 를 읽는
     * 경쟁 조건도 있었다.
     *
     * <p>이제는 md_id_sequence 테이블의 행 락으로 직렬화되므로 두 문제가 모두 사라진다.
     * 호출부가 @Transactional 이어야 한다(LAST_INSERT_ID 는 커넥션 단위 상태).
     */
    private String nextBalanceGameId() {
        Map<String, Object> param = new java.util.HashMap<>();
        balanceGameMapper.nextBalanceGameSequence(param);

        Object value = param.get("value");
        if (value == null) {
            // md_id_sequence 에 'balance_game' 행이 없으면 UPDATE 가 0건이라 값이 없다.
            // 조용히 1번부터 발급하면 기존 ID 와 충돌하므로 명시적으로 실패시킨다.
            throw new IllegalStateException(
                    "ID 시퀀스가 초기화되지 않았습니다. 'SQL 쿼리 모음/id_sequence.sql' 을 실행하세요.");
        }

        return formatId(((Number) value).longValue());
    }

    /**
     * 번호를 서비스용 ID 문자열로 변환한다.
     *
     * <p>5자리 zero padding 을 유지하는 이유: 이전/다음 게임 조회가 id 문자열 비교
     * (ORDER BY id)로 순서를 정하기 때문에, 자릿수가 같아야 사전순과 숫자순이 일치한다.
     * 99999 를 넘으면 이 성질이 깨지므로 그때는 조회 쿼리도 함께 손봐야 한다.
     */
    static String formatId(long number) {
        return String.format("%s%05d", ID_PREFIX, number);
    }

	@Override
	@Cacheable(cacheNames = CacheConfig.PARTICIPANT_COUNT, key = "'balanceGameParticipantCount'")
	public long getTotalParticipantCount() {
		return balanceGameMapper.selectTotalParticipantCount();
	}
}
