package com.moondap.common;

import java.util.Arrays;
import java.util.List;

/**
 * 금칙어 필터링 유틸리티
 */
public class ProfanityUtil {

    // 금칙어 리스트
    private static final List<String> PROHIBITED_WORDS = Arrays.asList(
        "시발", "씨발", "병신", "개새끼", "존나", "지랄", "등신", "머저리", 
        "바보", "멍청이", "미친", "호로", "쌍놈", "상놈", "놈팽이", 
        "닥쳐", "입닥쳐", "꺼져", "호구", "찐따", "빠가", "성괴", 
        "걸레", "창녀", "보지", "자지", "섹스", "정액", "콘돔", "야동",
        "일베", "메갈", "워마드", "한남", "김치녀"
    );

    /**
     * 입력된 텍스트에 금칙어가 포함되어 있는지 확인
     * 
     * @param text 검사할 텍스트
     * @return 금칙어 포함 여부 (true: 포함, false: 미포함)
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String normalizedText = text.replaceAll("\\s+", "").toLowerCase(); // 공백 제거 및 소문자화

        for (String word : PROHIBITED_WORDS) {
            if (normalizedText.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
