package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MdTestResultDTO {
    private Long id;
    private Long testId;
    private String resultTitle;
    private String resultContent;
    private String resultImage;
    private Boolean hasNewImage; // 새 이미지 업로드 여부 [NEW]
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
