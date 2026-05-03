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

    /**
     * 클라이언트의 실제 IP 주소를 가져옵니다.
     * 프록시 환경(X-Forwarded-For 등)을 고려합니다.
     */
    public static String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 여러 개의 IP가 넘어올 경우 첫 번째 IP가 실제 클라이언트 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
