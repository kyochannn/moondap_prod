package com.moondap.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moondap.dto.MdBoard;
import com.moondap.mapper.MdBoardMapper;

@Service    // 서비스 역할의 스프링 빈
public class MdBoardServiceImpl implements MdBoardService {
    
    @Autowired
    private MdBoardMapper mdBoardMapper;

    /**
     * 게시글 목록 조회
     */
    @Override
    public List<MdBoard> list() throws Exception {
        // TODO : mdBoardMapper 로 list() 호출
        /*
         *        ➡ List<Board> boardList 로 받아옴
         *        ➡ return boardList
         */
        List<MdBoard> boardList = mdBoardMapper.list();
        return boardList;
    }

    /**
     * 게시글 조회
     * - no 매개변수로 게시글 번호를 전달받아서
     *   데이터베이스에 조회 요청
     */
    @Override
    public MdBoard select(int no) throws Exception {
        // TODO : mdBoardMapper 로 select(no) 호출
        /*
         *        ➡ Board board 로 받아옴
         *        ➡ return board
         */
    	MdBoard board = mdBoardMapper.select(no);
        return board;        
    }

    /**
     * 게시글 등록
     */
    @Override
    public boolean insert(MdBoard board) throws Exception {
        // TODO : mdBoardMapper 로 insert(Board) 호출
        /*
        *        ➡ int result 로 데이터 처리 행(개수) 받아옴
        *        ➡ return result
        */
        int result = mdBoardMapper.insert(board);
        return result > 0;
    }

    /**
     * 게시글 수정
     */
    @Override
    public boolean update(MdBoard board) throws Exception {
        // TODO : mdBoardMapper 로 update(Board) 호출
        /*
         *        ➡ int result 로 데이터 처리 행(개수) 받아옴
         *        ➡ return result
         */
        int result = mdBoardMapper.update(board);
        return result > 0;
    }

    /**
     * 게시글 삭제
     */
    @Override
    public boolean delete(int no) throws Exception {
        // TODO : mdBoardMapper 로 delete(no) 호출
        /*
         *        ➡ int result 로 데이터 처리 행(개수) 받아옴
         *        ➡ return result
         */
        int result = mdBoardMapper.delete(no);
        return result > 0;
    }


}
