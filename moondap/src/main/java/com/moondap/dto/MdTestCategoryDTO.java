package com.moondap.dto;

import lombok.Data;

@Data
public class MdTestCategoryDTO {
    private Long id;
    private String categoryName;    // 실제 값 (예: PSYCHOLOGY)
    private String displayName;     // 화면 표시 이름 (예: 💖 연애/결혼)
    private String icon;            // 아이콘 클래스 (예: bi-heart-fill)
    private Integer sortOrder;      // 정렬 순서
    private Boolean active;         // 활성화 여부
}
