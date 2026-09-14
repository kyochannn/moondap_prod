package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.MdReportDTO;

@Mapper
public interface MdReportMapper {

    /** 신고 접수. 중복 신고는 UNIQUE 제약으로 막히므로 서비스가 미리 확인한다. */
    void insertReport(MdReportDTO report);

    /** 같은 사람이 같은 대상을 이미 신고했는지 */
    int countByReporter(@Param("targetType") String targetType,
                        @Param("targetId") String targetId,
                        @Param("userId") String userId,
                        @Param("anonId") String anonId);

    /** 관리 화면 목록. status 가 null/빈값이면 전체 */
    List<MdReportDTO> selectReports(@Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countReports(@Param("status") String status);

    /** 미처리 건수. 관리 메뉴의 배지에 쓴다. */
    int countPending();

    void updateStatus(@Param("no") Long no,
                      @Param("status") String status,
                      @Param("handledBy") String handledBy);

    MdReportDTO selectReport(@Param("no") Long no);
}
