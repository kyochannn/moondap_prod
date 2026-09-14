package com.moondap.dto;

import lombok.Data;

/** 일별 접속 통계 한 줄. */
@Data
public class DailyStatDTO {

    /** yyyy-MM-dd */
    private String visitDate;

    /** 그날의 순 방문자 수(IP 기준). */
    private long visitCount;

    /**
     * 그날의 순 방문자 수(쿠키 기준).
     *
     * <p>IP 기준을 대체하지 않고 나란히 둔다. 공유 IP 는 여러 명을 1명으로 합치고
     * 모바일 IP 변동은 1명을 여러 명으로 갈라 놓는데, 기준이 하나뿐이면 어느 방향으로
     * 얼마나 틀리는지 알 수가 없다.
     */
    private long cookieVisitorCount;

    /** 그날의 콘텐츠 참여 수. */
    private long participationCount;
}
