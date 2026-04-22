package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class MdTestQuestionDTO {

    private Long id;
    private Long testId;
    private Integer questionOrder;
    private String questionText;
    private String domain;
    private Boolean reverse;   // DB: is_reverse
    private Boolean active;    // DB: is_active

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
