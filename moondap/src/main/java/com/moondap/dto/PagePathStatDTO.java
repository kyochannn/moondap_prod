package com.moondap.dto;

import lombok.Data;

/** 경로별 조회수. */
@Data
public class PagePathStatDTO {

    /** 쿼리스트링을 뺀 경로. 예: {@code /test/love-style} */
    private String path;

    /** 화면이 열린 횟수. 순 방문자가 아니라 같은 사람의 재방문도 센다. */
    private long viewCount;

    /** 그중 바깥(검색·SNS·직접 입력)에서 이 화면으로 바로 들어온 횟수. */
    private long entryCount;
}
