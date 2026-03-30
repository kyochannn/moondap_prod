package com.moondap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SiteStatMapper {
    /**
     * 방문자 수 증가 (오늘 날짜 데이터가 없으면 삽입, 있으면 업데이트)
     */
    int upsertVisitCount(@Param("visitDate") String visitDate);

    /**
     * 특정 날짜의 방문자 수 조회
     */
    long selectVisitCount(@Param("visitDate") String visitDate);
}
