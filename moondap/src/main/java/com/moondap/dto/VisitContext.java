package com.moondap.dto;

/**
 * 방문 1건을 집계하는 데 필요한 값 묶음.
 *
 * <p>인자를 다섯 개 늘어놓지 않기 위해 묶었다. 같은 타입(String)이 줄지어 있으면
 * 순서가 바뀌어도 컴파일이 통과해서, 경로 자리에 출처가 들어가는 식의 사고가 조용히 난다.
 *
 * @param ipAddress     클라이언트 IP
 * @param anonId        요청에 실려 온 익명 쿠키 값. <b>방금 발급한 것은 담지 않는다</b>
 *                      (쿠키를 저장하지 않는 클라이언트가 요청마다 새 방문자로 잡히기 때문)
 * @param path          쿼리스트링을 뺀 경로
 * @param trafficSource 유입 출처 이름. 사이트 안에서의 이동이면 {@code null}
 */
public record VisitContext(String ipAddress, String anonId, String path, String trafficSource) {
}
