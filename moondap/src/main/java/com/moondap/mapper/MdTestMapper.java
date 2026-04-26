package com.moondap.mapper;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MdTestMapper {

    // 테스트 CRUD
    List<MdTestDTO> selectTestList(@Param("createdBy") String createdBy);
    MdTestDTO selectTest(@Param("id") Long id);
    void insertTest(MdTestDTO dto);
    void updateTest(MdTestDTO dto);
    void deleteTest(Long id);
    int countByTestKey(@Param("testKey") String testKey);
    MdTestDTO selectTestByTestKey(@Param("testKey") String testKey);
    Integer selectMaxSequenceByDatePrefix(@Param("prefix") String prefix);
    void updatePlayCount(@Param("id") Long id);

    // 질문 CRUD
    List<MdTestQuestionDTO> selectQuestions(@Param("testId") Long testId);
    MdTestQuestionDTO selectQuestion(@Param("id") Long id);
    void insertQuestion(MdTestQuestionDTO dto);
    void updateQuestion(MdTestQuestionDTO dto);
    void softDeleteQuestion(@Param("id") Long id);
    void deleteQuestionsByTestId(@Param("testId") Long testId);
    int countQuestionsByTestId(@Param("testId") Long testId);

    // 결과(Result) CRUD
    List<MdTestResultDTO> selectResults(@Param("testId") Long testId);
    void insertResult(MdTestResultDTO dto);
    void deleteResultsByTestId(@Param("testId") Long testId);

    // 통합 조회 (메인/리스트용)
    List<com.moondap.dto.MdContentItemDTO> selectPopularContent(@Param("limit") int limit);
    List<com.moondap.dto.MdContentItemDTO> selectAllContentList(@Param("category") String category, @Param("sort") String sort, @Param("type") String type, @Param("offset") int offset, @Param("limit") int limit);
}
