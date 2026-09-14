package com.moondap.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 콘텐츠 신고 한 건.
 *
 * @see com.moondap.service.MdReportService
 */
@Data
public class MdReportDTO {

    private Long no;

    /** COMMENT / BALANCE / TEST */
    private String targetType;

    /** targetType 에 따라 댓글 번호, 밸런스 게임 ID, 테스트 키가 들어간다. */
    private String targetId;

    /** ABUSE / SEXUAL / HATE / COPYRIGHT / SPAM / ETC */
    private String reasonCode;

    private String detail;

    /** 신고 시점의 대상 내용. 원본이 지워져도 판단할 수 있도록 복사해 둔다. */
    private String targetSnapshot;

    private String reporterUserId;

    /**
     * 익명 신고자 토큰. 관리 화면 응답에는 내보내지 않는다.
     * 다른 사람의 토큰을 알면 그 사람인 척 행동할 수 있다.
     */
    @JsonIgnore
    private String reporterAnonId;

    /** PENDING / RESOLVED / REJECTED */
    private String status;

    private String handledBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // ── DB 컬럼이 아니라 조회 시 계산해 붙이는 값 ──

    /** 같은 대상에 쌓인 신고 건수. 관리 화면에서 우선순위를 가늠하는 데 쓴다. */
    private Integer reportCount;
}
