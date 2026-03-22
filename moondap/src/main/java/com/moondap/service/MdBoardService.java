package com.moondap.service;

import java.util.List;

import com.moondap.dto.MdBoard;

public interface MdBoardService {
	
    // 게시글 목록
    public List<MdBoard> list() throws Exception;
    // 게시글 조회
    public MdBoard select(int no) throws Exception;
    // 게시글 등록
    public boolean insert(MdBoard board) throws Exception;
    // 게시글 수정
    public boolean update(MdBoard board) throws Exception;
    // 게시글 삭제
    public boolean delete(int no) throws Exception;
    
}
