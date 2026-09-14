package com.moondap.dto;

import lombok.Data;

/** 일별 접속 통계 한 줄. */
@Data
public class DailyStatDTO {

    /** yyyy-MM-dd */
    private String visitDate;

    /** 그날의 순 방문자 수(IP 기준). */
    private long visitCount;

    /** 그날의 콘텐츠 참여 수. */
    private long participationCount;
}
