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
     * 클라이언트의 IP 주소를 가져옵니다.
     *
     * <p><b>요청 헤더를 직접 읽지 않습니다.</b> 이전 구현은 X-Forwarded-For 등을
     * 검증 없이 신뢰하고 그중 <i>첫 번째</i> 값을 썼습니다. 그 값은 전적으로 클라이언트가
     * 정하는 것이라, 헤더 하나만 바꿔 보내면 매 요청이 다른 IP 로 보였습니다.
     * 투표 중복 방지와 방문자 수 집계가 모두 이 값을 기준으로 하므로 그대로 우회됐습니다.
     *
     * <p>대신 {@code request.getRemoteAddr()} 를 씁니다.
     * {@code server.forward-headers-strategy=framework}(application.properties) 설정으로
     * 스프링의 ForwardedHeaderFilter 가 이미 등록되어 있어, 프록시 뒤에서도 이 값이
     * 실제 클라이언트 IP 로 채워집니다.
     *
     * <p><b>남은 전제</b> — 프록시가 클라이언트가 보낸 X-Forwarded-For 를 그대로
     * 통과시키지 않고 <i>덮어써야</i> 합니다. 그렇지 않으면 여전히 위조가 가능합니다.
     * Cafe24 등 앞단 프록시 설정을 확인하고, 필요하면 Tomcat RemoteIpValve 의
     * internalProxies 로 신뢰 프록시를 명시해야 합니다. (docs/SECRETS.md 참고)
     */
    public static String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }
}
