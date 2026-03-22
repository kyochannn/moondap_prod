package com.moondap.common;

public class CommonUtil {

    /**
     * 문자열이 null, 빈 문자열, 또는 공백만 있는지 확인합니다. (가장 많이 쓰임)
     * 예: null, "", "  " -> 모두 true 반환
     */
    public static boolean isNull(String str) {
        return str == null || str.trim().isEmpty();
    }
	
    /**
     * 값이 제대로 존재하는지 확인 (nullCheck의 반대)
     * @return 값이 있으면 true, 없으면 false
     */
    public static boolean isNotNull(String str) {
        return !isNull(str);
    }
    
    /**
     * Null 체크 전용
     * Boolean 객체가 null인지 확인합니다.
     */
    public static boolean isNullBool(Boolean bool) {
       return bool == null;
    }
    
    public static boolean isNotNullBool(Boolean bool) {
    	return !isNullBool(bool);
    }
}
