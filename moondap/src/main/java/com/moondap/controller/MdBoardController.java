package com.moondap.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.moondap.dto.MdBoard;
import com.moondap.service.MdBoardService;

import lombok.extern.slf4j.Slf4j;

/**
 *  /board 경로로 요청 왔을 때 처리
 *  [GET]   - /board/list   : 게시글 목록 화면
 *  [GET]   - /board/read   : 게시글 조회 화면
 *  [GET]   - /board/insert : 게시글 등록 화면
 *  [POST]  - /board/insert : 게시글 등록 처리
 *  [GET]   - /board/update : 게시글 수정 화면
 *  [POST]  - /board/update : 게시글 수정 처리
 *  [POST]  - /board/delete : 게시글 삭제 처리
 */
@Slf4j                      // 로그 어노테이션
@Controller                 // 컨트롤러 스프링 빈으로 등록
@RequestMapping("/mdBoard")   // 클레스 레벨 요청 경로 매핑 
                            // - /mdBoard/~ 경로의 요청은 이 컨트롤러에서 처리
public class MdBoardController {
    
    // ⭐데이터 요청과 화면 출력
    // Controller --> Service (데이터 요청)
    // Controller <-- Service (데이터 전달)
    // Controller --> Model   (모델 등록)
    // View <-- Model         (데이터 출력)
    @Autowired                              // 의존성 자동 주입
    private MdBoardService boardService;      // @Service를 --Impl 에 등록

    /**
     * 게시글 목록 조회 화면
     * @return
     * @throws Exception 
     */
    @GetMapping("/list")
    public String list(Model model) throws Exception {
        // 데이터 요청
        List<MdBoard> boardList = boardService.list();
        // 모델 등록
        model.addAttribute("boardList", boardList);
        // 뷰 페이지 지정
        return "mdBoard/list";       // resources/templates/mdBoard/list.html
    }
    
    /**
     * 게시글 조회 화면
     * - /board/read?no=💎
     * @param no
     * @return
     * @throws Exception 
     */
    // @RequestParam("파라미터명") 
    // - 스프링 부트 3.2버전 이하, 생략해도 자동 매핑된다.
    // - 스프링 부트 3.2버전 이상, 필수로 명시해야 매핑된다.
    @GetMapping("/select")
    public String read(@RequestParam("no") int no, Model model) throws Exception {
        // 데이터 요청
    	MdBoard board = boardService.select(no);
        // 모델 등록
        model.addAttribute("board", board);
        // 뷰페이지 지정
        return "/board/read";
    }
    
    /**
     * 게시글 등록 화면
     * @return
     */
    @GetMapping("/insert")
    public String insert() {

        return "/board/insert";
    }

    /**
     * 게시글 등록 처리
     * @param board
     * @return
     * @throws Exception 
     */
    @PostMapping("/insert")
    public String insertPro(MdBoard board) throws Exception {
        // 데이터 요청
        boolean result = boardService.insert(board);
        // 리다이렉트
        // ⭕ 데이터 처리 성공
        if( result ) {
            return "redirect:/board/list";
        }
        // ❌ 데이터 처리 실패
        return "redirect:/board/insert?error";  
    }
    
    /**
     * 게시글 수정 화면
     * @param no
     * @param model
     * @return
     * @throws Exception 
     */
    @GetMapping("/update")
    public String update(@RequestParam("no") int no, Model model) throws Exception {
    	MdBoard board = boardService.select(no);
        model.addAttribute("board", board);
        return "/board/update";
    }

    /**
     * 게시글 수정 처리
     * @param board
     * @return
     * @throws Exception
     */
    @PostMapping("/update")
    public String updatePro(MdBoard board) throws Exception {
        boolean result = boardService.update(board);

        if( result ) {
            return "redirect:/board/list";
        }
        int no = board.getNo();
        return "redirect:/board/update?no="+ no + "&error";
    }
    
    /**
     * 게시글 삭제 처리
     * @param no
     * @return
     * @throws Exception
     */
    @PostMapping("/delete")
    public String delete(@RequestParam("no") int no) throws Exception {
        boolean result = boardService.delete(no);
        if( result ) {
            return "redirect:/board/list";
        }
        return "redirect:/board/update?no=" + no + "&error";
    }
    
    
}
