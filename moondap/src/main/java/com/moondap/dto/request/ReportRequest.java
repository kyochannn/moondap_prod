package com.moondap.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 신고 접수 요청.
 *
 * <p>targetType 과 reasonCode 는 화면이 보내는 값이므로 그대로 믿지 않는다.
 * 허용 목록에 없는 값은 서비스에서 거부한다.
 */
@Data
public class ReportRequest {

    /** 허용하는 신고 대상 종류 */
    public static final List<String> TARGET_TYPES = List.of("COMMENT", "BALANCE", "TEST");

    /** 허용하는 신고 사유 */
    public static final List<String> REASON_CODES =
            List.of("ABUSE", "SEXUAL", "HATE", "COPYRIGHT", "SPAM", "ETC");

    @NotBlank(message = "신고 대상이 없습니다.")
    private String targetType;

    @NotBlank(message = "신고 대상이 없습니다.")
    @Size(max = 100)
    private String targetId;

    @NotBlank(message = "신고 사유를 선택해주세요.")
    private String reasonCode;

    @Size(max = 500, message = "상세 내용은 500자를 넘을 수 없습니다.")
    private String detail;

    public boolean hasValidTargetType() {
        return targetType != null && TARGET_TYPES.contains(targetType);
    }

    public boolean hasValidReasonCode() {
        return reasonCode != null && REASON_CODES.contains(reasonCode);
    }
}
