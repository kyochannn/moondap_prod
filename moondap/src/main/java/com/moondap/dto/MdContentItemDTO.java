package com.moondap.dto;

import lombok.Data;

@Data
public class MdContentItemDTO {
    private String type;         // 'NORMAL' (심리테스트), 'BALANCE' (밸런스게임)
    private String id;
    private String key;          // 이동 시 사용할 Key (id 또는 testKey)
    private String title;        // 제목
    private String description;  // 한 줄 설명
    private String thumbnail;    // 썸네일 이미지
    private String thumbnail2;   // 썸네일 이미지2
    private String categoryName; // 카테고리 표시용 이름
    private Integer playCount;   // 참여수 (조회수)
    private String createdAt;    // 생성일
    private boolean isNew;       // 신규 콘텐츠 여부 (2주 이내)
    private boolean isSpicy;     // 매운맛 여부 (밸런스 게임 전용)
}
