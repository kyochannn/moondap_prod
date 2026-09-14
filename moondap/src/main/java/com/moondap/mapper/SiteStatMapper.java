package com.moondap.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.DailyStatDTO;
import com.moondap.dto.HourlyStatDTO;
import com.moondap.dto.PagePathStatDTO;
import com.moondap.dto.ReferrerStatDTO;
import com.moondap.dto.VisitLogDTO;

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

    /**
     * 경로별 조회수 +1.
     *
     * @param entry 바깥에서 이 화면으로 바로 들어왔으면 1, 사이트 안에서의 이동이면 0.
     *              두 숫자를 한 행에 두는 것은 "조회수 대비 유입 비율"이 화면마다
     *              바로 읽혀야 하기 때문이다.
     */
    int upsertPathView(@Param("visitDate") String visitDate,
                       @Param("path") String path,
                       @Param("entry") int entry);

    /** 유입 출처별 건수 +1. */
    int upsertReferrerVisit(@Param("visitDate") String visitDate, @Param("source") String source);

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

    /** 기간 내 조회수 상위 경로. */
    List<PagePathStatDTO> selectTopPaths(@Param("fromDate") String fromDate,
                                         @Param("toDate") String toDate,
                                         @Param("limit") int limit);

    /**
     * 기간 내 유입 수 상위 경로.
     *
     * <p>조회수 상위와 따로 뽑는다. 메인은 조회수가 늘 1위지만 유입은 다른 화면이
     * 더 많을 수 있고, 그 차이가 "검색이 어느 화면으로 사람을 데려오는가"다.
     */
    List<PagePathStatDTO> selectTopEntryPaths(@Param("fromDate") String fromDate,
                                              @Param("toDate") String toDate,
                                              @Param("limit") int limit);

    /** 기간 내 유입 출처별 건수. 많은 순. */
    List<ReferrerStatDTO> selectTopReferrers(@Param("fromDate") String fromDate,
                                             @Param("toDate") String toDate,
                                             @Param("limit") int limit);

    // ── 접속 기록 보유기간 관리 ────────────────────────────────

    /** 보관 중인 접속 기록 행 수. */
    long countVisitLogs();

    /** 기준일보다 오래된 접속 기록 행 수. */
    long countVisitLogsBefore(@Param("cutoffDate") String cutoffDate);

    /** 가장 오래된 접속 기록의 날짜. 기록이 없으면 null. */
    String selectOldestVisitLogDate();

    /**
     * 기준일보다 오래된 접속 기록 파기.
     *
     * <p>지워지는 것은 IP 가 담긴 md_visit_log 뿐이다. 일자별 집계(md_visit_daily)와
     * 시간대별 집계(md_pageview_hourly)에는 개인을 식별할 값이 없으므로 그대로 둔다.
     * 덕분에 과거 추이 그래프는 파기 후에도 끊기지 않는다.
     *
     * @return 지워진 행 수
     */
    int deleteVisitLogsBefore(@Param("cutoffDate") String cutoffDate);

    // ── 접속 기록 열람 ────────────────────────────────────────

    /** 특정 날짜의 접속 기록(IP + 첫 접속 시각). */
    List<VisitLogDTO> selectVisitLogs(@Param("visitDate") String visitDate,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 특정 날짜의 접속 IP 수. */
    long countVisitLogsOn(@Param("visitDate") String visitDate);
}
