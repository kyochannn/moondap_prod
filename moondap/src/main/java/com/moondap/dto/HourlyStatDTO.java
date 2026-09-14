package com.moondap.dto;

import lombok.Data;

/** 시간대별 접속 건수. */
@Data
public class HourlyStatDTO {

    /** 0~23 */
    private int visitHour;

    /** 해당 시각의 접속 건수(페이지뷰). 순방문자가 아니다. */
    private long viewCount;
}
