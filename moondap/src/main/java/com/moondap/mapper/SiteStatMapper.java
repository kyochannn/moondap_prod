package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;

@Mapper
public interface SiteStatMapper {
    /**
     * 방문자 수 증가 (오늘 날짜 데이터가 없으면 삽입, 있으면 업데이트)
     */
    int upsertVisitCount(@Param("visitDate") String visitDate);
    
    /**
     * 방문 로그 기록 (중복 방지를 위해 INSERT IGNORE 사용)
     */
    int insertVisitLog(@Param("visitDate") String visitDate, @Param("ipAddress") String ipAddress);

    /**
     * 콘텐츠 참여 수 증가 (오늘 날짜 데이터가 없으면 삽입, 있으면 업데이트)
     */
    int upsertParticipationCount(@Param("visitDate") String visitDate);

    /**
     * 특정 날짜의 방문자 수 조회
     */
    long selectVisitCount(@Param("visitDate") String visitDate);

    /**
     * 특정 날짜의 콘텐츠 참여 수 조회
     */
    long selectParticipationCount(@Param("visitDate") String visitDate);

    /** 시간대별 접속 건수 +1. 없으면 해당 (날짜, 시각) 행을 만든다. */
    int upsertHourlyView(@Param("visitDate") String visitDate, @Param("visitHour") int visitHour);

    // ── 관리자 통계 조회 ──────────────────────────────────────

    /**
     * 기간 내 일별 통계. 날짜 오름차순.
     *
     * <p>데이터가 없는 날은 행 자체가 없다. 그래프에서 끊기지 않게 채우는 것은
     * 조회한 쪽(StatsService)의 몫이다 — SQL 로 날짜를 생성하면 MariaDB 버전별로
     * 재귀 CTE 지원이 갈린다.
     */
    List<DailyStatDTO> selectDailyStats(@Param("fromDate") String fromDate,
                                        @Param("toDate") String toDate);

    /** 기간 내 순 방문자 합계. */
    long selectVisitSum(@Param("fromDate") String fromDate, @Param("toDate") String toDate);

    /** 기간 내 시간대별 접속 건수 합계. 0~23 중 값이 있는 시각만 반환한다. */
    List<HourlyStatDTO> selectHourlyStats(@Param("fromDate") String fromDate,
                                          @Param("toDate") String toDate);
}
