package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class MdTestDTO {

    private Long id;
    private String testKey;
    private String title;
    private String description;
    private String thumbnailImage;
    private String category;
    private Integer estimatedTime;
    private String status;
    private String createdBy;


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    private Integer questionCount;
    private java.util.List<MdTestQuestionDTO> questions;
    private java.util.List<MdTestResultDTO> results;
}
