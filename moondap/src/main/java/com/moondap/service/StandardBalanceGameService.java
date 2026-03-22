package com.moondap.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.moondap.common.CommonUtil;
import com.moondap.common.FileService;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.mapper.BalanceGameMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StandardBalanceGameService implements BalanceGameService {

	@Autowired
	private BalanceGameMapper balanceGameMapper;
	@Autowired
	private FileService fileService;
	
    // 밸런스 게임 목록
	public List<BalanceGameDTO> selectBalanceGameList(Map<String, String> request) {
		log.info("========== 밸런스 게임 리스트 조회 시작 ==========");
		
		try {
            String isSpicy = request.get("spicyFilter");
            String category = request.get("category");
            
            if (CommonUtil.isNull(isSpicy)) {
            	isSpicy = "0";
            }

            List<BalanceGameDTO> balanceGameList = balanceGameMapper.selectBalanceGameList(isSpicy, category);

            for (BalanceGameDTO balanceGame : balanceGameList) {
            	balanceGame.setCommentCnt(balanceGameMapper.selectBalanceGameCount(balanceGame.getId(), null, null));
			}
            
            if (balanceGameList == null || balanceGameList.isEmpty()) {
                log.info("조회된 데이터가 없습니다. 필터: {}, 카테고리: {}", isSpicy, category);
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
    
	// 밸런스 게임 조회
    public BalanceGameDTO selectBalanceGame(String id, String spicyFilter, String category) throws Exception {
    	System.out.println("========== 밸런스 게임 select ==========");
    	System.out.println("id: " + id + "	|	spicyFilter: " + spicyFilter + "	|	category: " + category);
    	
    	if (CommonUtil.isNull(id)) {
    		id = balanceGameMapper.selectMaxBalanceGameId(spicyFilter, category);
    		System.out.println("해당 조건문 탐..! : id : " + id);
    	}
    	
    	BalanceGameDTO balanceGame = balanceGameMapper.selectBalanceGame(id, spicyFilter, category);
    	
    	if (balanceGame == null) {
    		System.out.println("밸런스 게임이 존재하지 않습니다.");
    	}
    	
    	// Service 계산 로직
    	int totalCount = balanceGame.getOption1Count() + balanceGame.getOption2Count();
    	
    	if (totalCount == 0) {
    	    // 아무도 투표하지 않은 경우, 0%로 설정하거나 예외를 주지 않도록 처리
    	    balanceGame.setOption1Percent(50);
    	    balanceGame.setOption2Percent(50);
    	} else {
    	    // 투표자가 있을 때만 계산
    	    int option1Percent = (balanceGame.getOption1Count() * 100) / totalCount;
    	    int option2Percent = 100 - option1Percent;
    	    
    	    balanceGame.setOption1Percent(option1Percent);
    	    balanceGame.setOption2Percent(option2Percent);
    	}
    	
        return balanceGame;
    }
    
    // 다음 밸런스 게임 ID 조회
    public String nextOrPrevBalanceGameIdSelect(Map<String, String> request) throws Exception {
    	System.out.println("========== 다음 밸런스 게임 ID select ==========");
    	
    	String id = request.get("id");
    	String direction = request.get("direction");
    	String spicyFilter = request.get("spicyFilter");
    	String category = request.get("category");
    	
    	String balanceGame = balanceGameMapper.nextOrPrevBalanceGameIdSelect(id, direction, spicyFilter, category);
    	
    	if (balanceGame == null || "".equals(balanceGame)) {
    		System.out.println("밸런스 게임 null");
    	}
    	
    	return balanceGame;
    }
    
    // 밸런스 게임 투표
    public BalanceGameDTO vote(Map<String, String> request) throws Exception {
    	System.out.println("========== 밸런스 게임 vote ==========");
    	
    	String id = request.get("id");
    	String side = request.get("side");
    	
    	int option1Count = "left".equals(side) ? 1 : 0;
    	int option2Count = "right".equals(side) ? 1 : 0;
    	
    	int updatedRows = balanceGameMapper.vote(id, option1Count, option2Count);

    	if (updatedRows == 1) {
    		System.out.println("업데이트 성공");
    		BalanceGameDTO balanceGame = selectBalanceGame(id, null, null);
    		
    		return balanceGame;
    	} else {
    		System.out.println("업데이트 실패");
    		
    		return null;
    	}	
    	
    }
    
	// 밸런스 게임 댓글 조회
    public List<BalanceGameCommentDTO> selectBalanceGameComment(String id) throws Exception {
    	System.out.println("========== 밸런스 게임 댓글 select ==========");
    	
    	List<BalanceGameCommentDTO> balanceGameCommentList = balanceGameMapper.selectBalanceGameComment(id);
    	
    	if (balanceGameCommentList.size() == 0 || balanceGameCommentList == null) {
    		System.out.println("밸런스 게임 댓글 null이거나 존재하지 않습니다.");
    	} else {
    		
    		System.out.println("밸런스 게임 댓글이 " + balanceGameCommentList.size() + "개 존재합니다.");
    	}
    	
        return balanceGameCommentList;
    }
    
    // 밸런스 게임 댓글 달기
    public List<BalanceGameCommentDTO> insertBalanceGameComment(Map<String, String> request) throws Exception {
    	System.out.println("========== 밸런스 게임 댓글 추가 ==========");
    	
    	String id = request.get("id");
    	String nickname = request.get("nickname");
    	String side = request.get("side");
    	String content = request.get("content");
    	
    	Boolean flag = true;
    	int updatedRows = 0;
    	
    	if (CommonUtil.isNull(id)) flag = false;
    	if (CommonUtil.isNull(nickname)) flag = false;
    	if (CommonUtil.isNull(side)) flag = false;
    	if (CommonUtil.isNull(content)) flag = false;
    	
    	if (flag) {    		
    		updatedRows = balanceGameMapper.insertBalanceGameComment(id, nickname, side, content);
    		if (updatedRows == 1) {
    			System.out.println("업데이트 성공");
    			List<BalanceGameCommentDTO> balanceGameCommentList = selectBalanceGameComment(id);
    			
    			return balanceGameCommentList;
    		} else {
    			System.out.println("업데이트 실패");
    			
    			return null;
    		}	
    	} else {
    		return null;
    	}
    	
    }
    
    // 밸런스 게임 관련 댓글 삭제
    public String deleteBalanceGameComment(String id) throws Exception {
    	System.out.println("========== 밸런스 게임 댓글 삭제 ==========");
    	
    	List<BalanceGameCommentDTO> list = selectBalanceGameComment(id);
    	
    	// DB
    	// 댓글이 존재할 때만 삭제
    	if (list.size() > 0) {
    		int updatedRows = balanceGameMapper.deleteBalanceGameComment(id);
    		
    		System.out.println("updatedRows ::::::::: del :::::" + updatedRows);
    		
    		if (updatedRows >= 1) {
    			System.out.println("밸런스 게임 관련 댓글 삭제 성공");
    			return id;
    		} else {
    			System.out.println("밸런스 게임 관련 댓글 삭제 실패");
    			return null;
    		}    		
    	} else {
    		System.out.println("밸런스 게임 관련 댓글이 존재하지 않아 댓글 삭제를 생략합니다.");    		
    		return id;
    	}
    	
    }
    
    // 밸런스 게임 좋아요 등록
    public List<BalanceGameCommentDTO> updateBalanceGameCommentLikeCount(Map<String, String> request) throws Exception {
    	System.out.println("========== 밸런스 게임 댓글 좋아요 추가 ==========");
    	
    	String id = request.get("id");
    	int no = Integer.parseInt(request.get("no"));
    	String setting = request.get("setting");
    	
    	System.out.println(id + "//" + no + "//" + setting);
    	
    	int updatedRows;
    	
    	if ("UP".equals(setting) || "UP" == setting) { 		
    		updatedRows = balanceGameMapper.updateBalanceGameCommentLikeCount(no, id, 1);
    	} else {
    		updatedRows = balanceGameMapper.updateBalanceGameCommentLikeCount(no, id, -1);    		
    	}

    	if (updatedRows == 1) {
    		System.out.println("업데이트 성공");
    		List<BalanceGameCommentDTO> balanceGameCommentList = selectBalanceGameComment(id);
    		
    		return balanceGameCommentList;
    	} else {
    		System.out.println("업데이트 실패");
    		
    		return null;
    	}	
    	
    }
	
    // 밸런스 게임 등록
    public String insertBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception {
    	System.out.println("========== 밸런스 게임 등록 ==========");
    	
    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
    	
    	String title = params.get("title");
    	String spicyFilter = params.get("spicyFilter");
    	String category = params.get("category");
    	String option1Text = params.get("option1Text");
    	String option2Text = params.get("option2Text");
    	String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);
    	String lastId = generateNextId(balanceGameMapper.selectMaxBalanceGameId(null, null));
    	
    	// null check
    	if (CommonUtil.isNotNull(title)) balanceGameDto.setTitle(title);
    	else return null;
    	
    	if (CommonUtil.isNotNull(spicyFilter)) balanceGameDto.setIsSpicy(Boolean.parseBoolean(spicyFilter));
    	else return null;
    	
    	if (CommonUtil.isNotNull(category)) balanceGameDto.setCategory(category);	    		
    	else return null;
    	
    	if (CommonUtil.isNotNull(option1Text)) balanceGameDto.setOption1Text(option1Text);
    	else return null;
    	
    	if (CommonUtil.isNotNull(option2Text)) balanceGameDto.setOption2Text(option2Text);
    	else return null;
    	
    	if (CommonUtil.isNotNull(option1ImagePath)) balanceGameDto.setOption1ImagePath(option1ImagePath);
    	else return null;
    	
    	if (CommonUtil.isNotNull(option2ImagePath)) balanceGameDto.setOption2ImagePath(option2ImagePath);
    	else return null;
    		
    	if (CommonUtil.isNotNull(lastId)) balanceGameDto.setId(lastId);
    	else return null;
    	
    	/*if (CommonUtil.isNotNull("UserId"))*/ balanceGameDto.setUserId("mdadmin");
//    	else return null;
    	
    	// DB Insert
    	int updatedRows = balanceGameMapper.insertBalanceGame(balanceGameDto);

    	if (updatedRows == 1) {
    		System.out.println("업데이트 성공");
    		return lastId;
    	} else {
    		System.out.println("업데이트 실패");
    		return null;
    	}
    }
    
    // 밸런스 게임 수정
	public String updateBalanceGame(Map<String, String> params, MultipartFile option1Image, MultipartFile option2Image) throws Exception {
    	System.out.println("========== 밸런스 게임 수정 ==========");
    	
    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
    	
    	String id = params.get("id");
    	String title = params.get("title");
    	String spicyFilter = params.get("spicyFilter");
    	String category = params.get("category");
    	String option1Text = params.get("option1Text");
    	String option2Text = params.get("option2Text");
    	String oldOption1ImagePath = params.get("oldOption1ImagePath");
    	String oldOption2ImagePath = params.get("oldOption2ImagePath");
    	String option1ImagePath = fileService.upload(option1Image);
    	String option2ImagePath = fileService.upload(option2Image);
    	
    	// null check
    	if (CommonUtil.isNotNull(id)) balanceGameDto.setId(id);
    	else return null;
    	
    	if (CommonUtil.isNotNull(title)) balanceGameDto.setTitle(title);
    	else return null;
    	
    	System.out.println("Boolean.parseBoolean(spicyFilter)::: " + Boolean.parseBoolean(spicyFilter));
    	if (CommonUtil.isNotNull(spicyFilter)) balanceGameDto.setIsSpicy(Boolean.parseBoolean(spicyFilter));
    	else return null;
    	
    	if (CommonUtil.isNotNull(category)) balanceGameDto.setCategory(category);	    		
    	else return null;
    	
    	if (CommonUtil.isNotNull(option1Text)) balanceGameDto.setOption1Text(option1Text);
    	else return null;
    	
    	if (CommonUtil.isNotNull(option2Text)) balanceGameDto.setOption2Text(option2Text);
    	else return null;
    	
    	if (CommonUtil.isNotNull(option1ImagePath)) {
        	if ("default-img.png".equals(option1ImagePath)) {
        		option1ImagePath = oldOption1ImagePath;
        	} else {
        		fileService.deleteFile(oldOption1ImagePath);
        	}
    		balanceGameDto.setOption1ImagePath(option1ImagePath);
    	} else {
    		return null;
    	}
    	
    	if (CommonUtil.isNotNull(option2ImagePath)) {
    		if ("default-img.png".equals(option2ImagePath)) {
    			option2ImagePath = oldOption2ImagePath;
    		} else {
        		fileService.deleteFile(oldOption2ImagePath);
        	}
    		balanceGameDto.setOption2ImagePath(option2ImagePath);
    	} else {
    		return null;
    	}
    	
    	/*if (CommonUtil.isNotNull("UserId"))*/ balanceGameDto.setUserId("mdadmin");
//        	else return null;
    	
    	// DB Insert
    	int updatedRows = balanceGameMapper.updateBalanceGame(balanceGameDto);

    	if (updatedRows == 1) {
    		System.out.println("업데이트 성공");
    		return id;
    	} else {
    		System.out.println("업데이트 실패");
    		return null;
    	}
    }

    // 밸런스 게임 삭제
	public String deleteBalanceGame(Map<String, String> params) throws Exception {
    	System.out.println("========== 밸런스 게임 삭제 ==========");
    	
    	BalanceGameDTO balanceGameDto = new BalanceGameDTO();
    	
    	String id = params.get("id");
    	String oldOption1ImagePath = params.get("oldOption1ImagePath");
    	String oldOption2ImagePath = params.get("oldOption2ImagePath");
    	
    	// null check
    	if (CommonUtil.isNotNull(id)) balanceGameDto.setId(id);
    	else return null;
    	
    	if (CommonUtil.isNotNull(oldOption1ImagePath)) {
    		fileService.deleteFile(oldOption1ImagePath);
    	} else {
    		return null;
    	}
    	
    	if (CommonUtil.isNotNull(oldOption2ImagePath)) {
    		fileService.deleteFile(oldOption2ImagePath);
    	} else {
    		return null;
    	}
    	
    	// DB Insert
    	int updatedRows = balanceGameMapper.deleteBalanceGame(id);

    	if (updatedRows == 1) {
    		System.out.println("밸런스 게임 삭제 성공");
    		return id;
    	} else {
    		System.out.println("밸런스 게임 삭제 실패");
    		return null;
    	}
    }
    
    //
    public static String generateNextId(String lastId) {
        String prefix = "BG";
        int nextNumber = 1;
        
        // 1. 기존 ID가 존재하는 경우 숫자 추출
        if (lastId != null && lastId.startsWith(prefix)) {
            try {
                String numericPart = lastId.substring(2); 
                nextNumber = Integer.parseInt(numericPart) + 1;
            } catch (NumberFormatException e) {
                nextNumber = 1;
            }
        }

        // 2. 숫자를 5자리 문자열로 포맷팅 (모자란 자릿수는 0으로 채움)
        // %05d: 5자리 정수이며, 빈 자리는 0으로 채움
        return String.format("%s%05d", prefix, nextNumber);
    }
    
}
