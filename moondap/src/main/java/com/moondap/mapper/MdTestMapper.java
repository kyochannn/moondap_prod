package com.moondap.mapper;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MdTestMapper {

    // 테스트 CRUD
    List<MdTestDTO> selectTestList();
    MdTestDTO selectTest(Long id);
    void insertTest(MdTestDTO dto);
    void updateTest(MdTestDTO dto);
    void deleteTest(Long id);
    int countByTestKey(@Param("testKey") String testKey);
    Integer selectMaxSequenceByDatePrefix(@Param("prefix") String prefix);

    // 질문 CRUD
    List<MdTestQuestionDTO> selectQuestions(@Param("testId") Long testId);
    MdTestQuestionDTO selectQuestion(@Param("id") Long id);
    void insertQuestion(MdTestQuestionDTO dto);
    void updateQuestion(MdTestQuestionDTO dto);
    void softDeleteQuestion(@Param("id") Long id);
    void deleteQuestionsByTestId(@Param("testId") Long testId);
    int countQuestionsByTestId(@Param("testId") Long testId);
}
