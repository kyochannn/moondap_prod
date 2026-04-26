package com.moondap.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.moondap.common.CommonUtil;
import com.moondap.common.FileService;
import com.moondap.common.ProfanityUtil;
import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.mapper.BalanceGameMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StandardBalanceGameService implements BalanceGameService {

    private static final String DEFAULT_IMAGE = "default-img.png";

	@Autowired
	private BalanceGameMapper balanceGameMapper;
	@Autowired
	private FileService fileService;
	
    // 밸런스 게임 목록
	@Override
	public List<BalanceGameDTO> selectBalanceGameList(Map<String, String> request, int offset, int limit) {
		log.info("========== 밸런스 게임 리스트 조회 시작 ==========");
		
		try {
            String isSpicy = request.get("spicyFilter");
            String category = request.get("category");
            String status = request.get("status");
            String userId = request.get("userId"); // 신규 추가
            
            if (CommonUtil.isNull(isSpicy)) {
            	isSpicy = "0";
            }

            List<BalanceGameDTO> balanceGameList = balanceGameMapper.selectBalanceGameList(isSpicy, category, status, userId, offset, limit);

            if (balanceGameList == null || balanceGameList.isEmpty()) {
                log.info("조회된 데이터가 없습니다. 필터: {}, 카테고리: {}, 사용자: {}", isSpicy, category, userId);
                return Collections.emptyList();
            }

            return balanceGameList;
        } catch (DataAccessException e) {
            log.error("데이터베이스 접근 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("데이터베이스 조회를 실패했습니다.", e);
        } catch (Exception e) {
            log.error("서버 내부 에러 발생: {}", e.getMessage(), e);
            throw new RuntimeException("시스템 오류가 발생했습니다.");
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
    public String nextOrPrevBalanceGameIdSelect(Map<String, String> request) throws Exception {
    	log.info("========== 다음 밸런스 게임 ID select ==========");
    	
    	String id = request.get("id");
    	String direction = request.get("direction");
    	String spicyFilter = request.get("spicyFilter");
    	String category = request.get("category");
    	
    	return balanceGameMapper.nextOrPrevBalanceGameIdSelect(id, direction, spicyFilter, category);
    }
    
    // 밸런스 게임 투표
    @Override
    @Transactional
    public BalanceGameDTO vote(Map<String, String> request) throws Exception {
    	log.info("========== 밸런스 게임 vote ==========");
    	
    	String id = request.get("id");
    	String side = request.get("side");
    	
    	int option1Count = "left".equals(side) ? 1 : 0;
    	int option2Count = "right".equals(side) ? 1 : 0;
    	
    	int updatedRows = balanceGameMapper.vote(id, option1Count, option2Count);

    	if (updatedRows == 1) {
    		return selectBalanceGame(id, null, null);
    	} else {
    		log.warn("업데이트 실패");
    		
    		return null;
    	}	
    }
    
	// 밸런스 게임 댓글 조회
    @Override
    public List<BalanceGameCommentDTO> selectBalanceGameComment(String id) throws Exception {
    	log.info("========== 밸런스 게임 댓글 select ==========");
    	
    	List<BalanceGameCommentDTO> balanceGameCommentList = balanceGameMapper.selectBalanceGameComment(id);
    	
    	if (balanceGameCommentList.isEmpty()) {
    		log.info("밸런스 게임 댓글이 존재하지 않습니다.");
    	} else {
    		log.info("밸런스 게임 댓글이 {}개 존재합니다.", balanceGameCommentList.size());
    	}
    	
        return balanceGameCommentList;
    }
    
    // 밸런스 게임 댓글 달기
    @Override
    @Transactional
    public List<BalanceGameCommentDTO> insertBalanceGameComment(Map<String, String> request) throws Exception {
    	log.info("========== 밸런스 게임 댓글 추가 ==========");
    	
    	String id = request.get("id");
    	String nickname = request.get("nickname");
    	String side = request.get("side");
    	String content = request.get("content");
    	String userId = request.get("userId");
    	
    	if (CommonUtil.isNull(id) || CommonUtil.isNull(nickname) || CommonUtil.isNull(side) || CommonUtil.isNull(content)) {
            return null;
        }

    	if (ProfanityUtil.containsProfanity(nickname) || ProfanityUtil.containsProfanity(content)) {
    		throw new RuntimeException("금칙어가 포함된 내용을 입력할 수 없습니다.");
    	}
    	
    	// 글자 수 체크 (50자 제한)
    	if (content.length() > 50) {
    		throw new RuntimeException("댓글은 50자 이내로 입력 가능합니다.");
    	}
    	
    	int updatedRows = balanceGameMapper.insertBalanceGameComment(id, nickname, side, content, userId);
    	if (updatedRows == 1) {
    		return selectBalanceGameComment(id);
    	}
    	return null;
    }
    
    // 밸런스 게임 관련 댓글 삭제
    @Override
    @Transactional
    public String deleteBalanceGameComment(String id) throws Exception {
    	log.info("========== 밸런스 게임 댓글 삭제 ==========");
    	
    	List<BalanceGameCommentDTO> list = selectBalanceGameComment(id);
    	if (list != null && !list.isEmpty()) {
    		int updatedRows = balanceGameMapper.deleteBalanceGameComment(id);
    		if (updatedRows >= 1) {
    			return id;
    		}
    		return null;
    	} 
    	return id;
    }
    
    // 밸런스 게임 좋아요 등록
    @Override
    @Transactional
    public List<BalanceGameCommentDTO> updateBalanceGameCommentLikeCount(Map<String, String> request) throws Exception {
    	log.info("========== 밸런스 게임 댓글 좋아요 추가 ==========");
    	
    	String id = request.get("id");
    	int no = Integer.parseInt(request.get("no"));
    	String setting = request.get("setting");
    	
    	int updatedRows;
    	if ("UP".equals(setting)) { 		
    		updatedRows = balanceGameMapper.updateBalanceGameCommentLikeCount(no, id, 1);
    	} else {
    		updatedRows = balanceGameMapper.updateBalanceGameCommentLikeCount(no, id, -1);    		
    	}

    	if (updatedRows == 1) {
    		return selectBalanceGameComment(id);
    	}
    	return null;
    }
	
    // 밸런스 게임 등록
    @Override
    @Transactional
    public String insertBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception {
    	String title = params.get("title");
    	String spicyFilter = params.get("spicyFilter");
    	String category = params.get("category");
    	String status = params.getOrDefault("status", "draft");
    	String option1Text = params.get("option1Text");
    	String option2Text = params.get("option2Text");

        // 입력값 검증
        validateBalanceGame(title, option1Text, option2Text);

    	String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);
    	String lastId = generateNextId(balanceGameMapper.selectMaxBalanceGameId(null, null));
    	
    	if (CommonUtil.isNull(title) || CommonUtil.isNull(spicyFilter) || CommonUtil.isNull(category) || 
            CommonUtil.isNull(option1Text) || CommonUtil.isNull(option2Text) || 
            CommonUtil.isNull(option1ImagePath) || CommonUtil.isNull(option2ImagePath)) {
            return null;
        }

    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
    	balanceGameDto.setTitle(title);
        balanceGameDto.setIsSpicy(Boolean.parseBoolean(spicyFilter));
        balanceGameDto.setCategory(category);
        balanceGameDto.setStatus(status);
        balanceGameDto.setOption1Text(option1Text);
        balanceGameDto.setOption2Text(option2Text);
        balanceGameDto.setOption1ImagePath(option1ImagePath);
        balanceGameDto.setOption2ImagePath(option2ImagePath);
    	balanceGameDto.setId(lastId);
    	balanceGameDto.setUserId(getCurrentUserId());
    	
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
	public String updateBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception {
    	String id = params.get("id");
        
        // [보안] 권한 확인
        if (!CheckMyTest(id)) {
            throw new RuntimeException("해당 게임을 수정할 권한이 없습니다.");
        }

    	String title = params.get("title");
    	String spicyFilter = params.get("spicyFilter");
    	String category = params.get("category");
    	String status = params.get("status");
    	String option1Text = params.get("option1Text");
    	String option2Text = params.get("option2Text");
    	String oldOption1ImagePath = params.get("oldOption1ImagePath");
    	String oldOption2ImagePath = params.get("oldOption2ImagePath");
    	
        if (CommonUtil.isNull(id)) return null;

        // 입력값 검증
        validateBalanceGame(title, option1Text, option2Text);

        String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);
    	
    	if (CommonUtil.isNull(title) || CommonUtil.isNull(spicyFilter) || CommonUtil.isNull(category) || 
            CommonUtil.isNull(option1Text) || CommonUtil.isNull(option2Text) || 
            CommonUtil.isNull(option1ImagePath) || CommonUtil.isNull(option2ImagePath)) {
            return null;
        }

    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
        balanceGameDto.setId(id);
    	balanceGameDto.setTitle(title);
        balanceGameDto.setIsSpicy(Boolean.parseBoolean(spicyFilter));
        balanceGameDto.setCategory(category);
        balanceGameDto.setStatus(status);
        balanceGameDto.setOption1Text(option1Text);
        balanceGameDto.setOption2Text(option2Text);
    	balanceGameDto.setUserId(getCurrentUserId());

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
	public String deleteBalanceGame(Map<String, String> params) throws Exception {
    	String id = params.get("id");
        
        // [보안] 권한 확인
        if (!CheckMyTest(id)) {
            throw new RuntimeException("해당 게임을 삭제할 권한이 없습니다.");
        }

    	String oldOption1ImagePath = params.get("oldOption1ImagePath");
    	String oldOption2ImagePath = params.get("oldOption2ImagePath");
    	
    	if (CommonUtil.isNull(id)) return null;

        // 관련 댓글 먼저 삭제 (트랜잭션 보장)
        deleteBalanceGameComment(id);
    	
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
    
    private void validateBalanceGame(String title, String option1Text, String option2Text) {
        if (title != null && title.length() > 30) {
            throw new RuntimeException("제목의 길이가 30자를 초과했습니다.");
        }
        if (option1Text != null && option1Text.length() > 20) {
            throw new RuntimeException("왼쪽 선택지의 길이가 20자를 초과했습니다.");
        }
        if (option2Text != null && option2Text.length() > 20) {
            throw new RuntimeException("오른쪽 선택지의 길이가 20자를 초과했습니다.");
        }

        if (ProfanityUtil.containsProfanity(title) || 
            ProfanityUtil.containsProfanity(option1Text) || 
            ProfanityUtil.containsProfanity(option2Text)) {
            throw new RuntimeException("금칙어가 포함된 내용을 입력할 수 없습니다.");
        }
    }

    private boolean IsLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && 
              !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private String getCurrentUserId() {
        if (!IsLoggedIn()) {
            return "mdadmin"; 
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof PrincipalDetails) {
            return ((PrincipalDetails) principal).getUsername();
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Override
    public boolean CheckMyTest(String id) {
        log.info("========== 권한 확인 시작: {} ==========", id);
        
        // 1. 로그인 상태가 아니면 무조건 권한 없음
        if (!IsLoggedIn()) {
            log.warn("- 비로그인 사용자 접근 차단");
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 2. 관리자 권한 확인 (ROLE_ADMIN 권한이 있다면 아이디 상관없이 허용)
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            log.info("- 관리자 권한 확인됨");
            return true;
        }

        // 3. 작성자 확인
        String currentUserId = getCurrentUserId();
        try {
            BalanceGameDTO balanceGame = balanceGameMapper.selectBalanceGame(id, null, null);
            if (balanceGame != null && currentUserId.equals(balanceGame.getUserId())) {
                log.info("- 작성자 본인 확인됨: {}", currentUserId);
                return true;
            }
        } catch (Exception e) {
            log.error("권한 확인 중 데이터베이스 조회 오류: {}", e.getMessage());
        }

        log.warn("- 권한 없음: 현재사용자({}), 작성자(알 수 없음 또는 다름)", currentUserId);
        return false;
    }

    public static String generateNextId(String lastId) {
        String prefix = "BG";
        int nextNumber = 1;
        if (lastId != null && lastId.startsWith(prefix)) {
            try {
                String numericPart = lastId.substring(2); 
                nextNumber = Integer.parseInt(numericPart) + 1;
            } catch (NumberFormatException e) {
                nextNumber = 1;
            }
        }
        return String.format("%s%05d", prefix, nextNumber);
    }

	@Override
	public long getTotalParticipantCount() {
		return balanceGameMapper.selectTotalParticipantCount();
	}
}
