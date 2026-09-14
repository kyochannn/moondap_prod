package com.moondap.dto;

import lombok.Data;

/** User-Agent 별 요청 수. 봇 판정이 맞는지 눈으로 확인하기 위한 것. */
@Data
public class UserAgentStatDTO {

    private String userAgent;

    /** 사람으로 셌으면 true, 봇으로 걸러냈으면 false. */
    private boolean counted;

    private long hitCount;
}
