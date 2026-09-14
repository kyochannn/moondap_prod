package com.moondap.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 접속 기록 한 줄.
 *
 * <p>하루에 같은 IP 는 한 번만 기록되므로, 시각은 "그날 처음 들어온 시각"이다.
 * first_seen_at 컬럼을 추가하기 전에 쌓인 기록에는 시각이 없어 null 이다.
 */
@Data
public class VisitLogDTO {

    private String ipAddress;

    /** 그날 처음 접속한 시각. 컬럼 추가 이전 기록은 null. */
    private LocalDateTime firstSeenAt;
}
