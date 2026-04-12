package com.moondap.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class EgenTetoDTO {
    private String userNo;
    private String userName;
    private String gender;
    private String isTesterMyself;
    private String testDate;

    private String testResultType;
    private String styleSelfcareResultType;
    private String socialSkillResultType;
    private String innerTendencyResultType;
    private String ambitionResultType;

    private int tetoScore;
    private int egenScore;
    private int zScore;
    private int topPercent;

    private int styleSelfcarePoint;
    private int socialSkillPoint;
    private int innerTendencyPoint;
    private int ambitionPoint;

    // 편의를 위한 계산된 값 (게터 활용 가능)
    public String getStyleSelfcarePointStr() {
        return (0 == styleSelfcarePoint) ? "스타일 믹스형" : "총 " + styleSelfcarePoint + "점";
    }

    public String getSocialSkillPointStr() {
        return (0 == socialSkillPoint) ? "사회적 믹스형" : "총 " + socialSkillPoint + "점";
    }

    public String getInnerTendencyPointStr() {
        return (0 == innerTendencyPoint) ? "내면 믹스형" : "총 " + innerTendencyPoint + "점";
    }

    public String getAmbitionPointStr() {
        return (0 == ambitionPoint) ? "야망 믹스형" : "총 " + ambitionPoint + "점";
    }

    public boolean isTeto() {
        return testResultType != null && testResultType.startsWith("테토");
    }

    public boolean isMale() {
        return testResultType != null && testResultType.endsWith("남");
    }
}
