package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.moondap.dto.MdBoard;

@Mapper
public interface MdBoardMapper {

    // 게시글 목록
    public List<MdBoard> list() throws Exception;
    // 게시글 조회
    public MdBoard select(int no) throws Exception;
    // 게시글 등록
    public int insert(MdBoard board) throws Exception;
    // 게시글 수정
    public int update(MdBoard board) throws Exception;
    // 게시글 삭제
    public int delete(int no) throws Exception;
    
    
}
