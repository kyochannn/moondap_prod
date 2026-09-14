package com.moondap.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 한 접속자가 연 화면 한 건.
 *
 * <p>집계가 아니라 개인별 열람 기록이다. 화면에 뿌릴 때는 열람 사실을 로그로 남긴다.
 */
@Data
public class VisitTrailDTO {

    private String path;

    private LocalDateTime viewedAt;
}
