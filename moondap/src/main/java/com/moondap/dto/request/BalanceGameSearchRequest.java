package com.moondap.dto.request;

import lombok.Data;

/**
 * 밸런스 게임 목록 조회 조건.
 *
 * <p>이전에는 {@code Map<String, String>} 이었고, 실제로 키를 잘못 쓴 곳이 있었다.
 * {@code BalanceGameController.selectBalanceGameListView} 가 {@code put("isSpicy", "0")}
 * 을 넣었는데 서비스는 {@code get("spicyFilter")} 를 읽어서 값이 전달되지 않았다.
 * 컴파일도 통과하고 실행도 되지만 조건만 조용히 빠지는 형태였다.
 */
@Data
public class BalanceGameSearchRequest {

    /** 기본 페이지 크기 */
    private static final int DEFAULT_LIMIT = 10;

    /**
     * 매운맛 필터.
     * "0" 이면 순한맛만 조회하고, 그 밖의 값이면 필터를 걸지 않는다(전체 조회).
     * 값이 없으면 "0"(순한맛만)으로 본다.
     */
    private String spicyFilter;

    /** 카테고리. null/빈값/"all" 이면 전체 */
    private String category;

    /**
     * 상태(active/draft/inactive). null 이거나 빈값이면 상태를 가리지 않는다.
     *
     * <p>공개 화면에서는 반드시 "active" 를 넣어야 한다. 비워두면 draft 가 함께 노출된다.
     */
    private String status;

    /** 작성자. 마이페이지·관리 화면에서 본인 글만 볼 때 사용 */
    private String userId;

    private Integer offset;
    private Integer limit;

    /** 값이 없으면 순한맛만 조회한다(기존 서비스 기본 동작 유지). */
    public String getSpicyFilterOrDefault() {
        return (spicyFilter == null || spicyFilter.isBlank()) ? "0" : spicyFilter;
    }

    public int getOffsetOrDefault() {
        return (offset == null || offset < 0) ? 0 : offset;
    }

    public int getLimitOrDefault() {
        return (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
    }

    /** 공개 목록용 조건: active 만, 순한맛만 */
    public static BalanceGameSearchRequest publicList(int limit) {
        BalanceGameSearchRequest request = new BalanceGameSearchRequest();
        request.setStatus("active");
        request.setSpicyFilter("0");
        request.setLimit(limit);
        return request;
    }

    /** 관리 화면용 조건: 상태·매운맛 가리지 않고 전부 */
    public static BalanceGameSearchRequest manageList(String userId, int limit) {
        BalanceGameSearchRequest request = new BalanceGameSearchRequest();
        request.setStatus(null);      // 상태 무관
        request.setSpicyFilter("1");  // 매운맛 필터 해제
        request.setUserId(userId);
        request.setLimit(limit);
        return request;
    }
}
