package com.moondap.dto;

import lombok.Data;

/** 유입 출처별 건수. */
@Data
public class ReferrerStatDTO {

    /** 구글 / 네이버 / 직접 유입 … (TrafficSource 가 붙인 이름) */
    private String source;

    private long visitCount;
}
