package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.TestHistoryDTO;

@Mapper
public interface TestHistoryMapper {

    /** 테스트를 마친 기록 한 건 저장. */
    int insertHistory(TestHistoryDTO history);

    /**
     * 보관함 조회.
     *
     * <p>로그인 사용자는 userId, 비로그인 사용자는 anonId 로 찾는다.
     * 둘 다 null 이면 빈 목록이어야 하므로 매퍼에서 조건을 강제한다.
     */
    List<TestHistoryDTO> selectHistory(@Param("userId") String userId,
                                       @Param("anonId") String anonId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 익명으로 남긴 기록을 로그인 계정으로 옮긴다.
     *
     * @return 옮겨진 건수
     */
    int claimAnonymousHistory(@Param("userId") String userId, @Param("anonId") String anonId);

    /** 본인 기록 한 건 삭제. 소유자 조건을 함께 걸어 남의 기록을 지울 수 없게 한다. */
    int deleteHistory(@Param("no") long no,
                      @Param("userId") String userId,
                      @Param("anonId") String anonId);
}
