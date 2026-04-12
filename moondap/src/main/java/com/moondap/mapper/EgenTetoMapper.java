package com.moondap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.moondap.dto.EgenTetoDTO;

/**
 * 에겐/테토 테스트 결과 매퍼
 */
@Mapper
public interface EgenTetoMapper {

    /**
     * 테스트 결과 삽입
     */
    int insertTestResult(@Param("result") EgenTetoDTO result);

    /**
     * 테스트 결과 조회
     */
    EgenTetoDTO selectTestResult(@Param("userNo") String userNo);

    /**
     * 전체 점수 통계 조회 (평균, 표준편차, 총 인원)
     */
    java.util.Map<String, Object> selectScoreStatistics();

    /**
     * 성별별 참여자 수 조회
     */
    java.util.Map<String, Object> selectGenderCounts();
}
