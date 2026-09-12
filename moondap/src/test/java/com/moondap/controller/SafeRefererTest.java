package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Referer 기반 리다이렉트의 안전 검사.
 *
 * <p>삭제 후 "이전 화면으로 돌아가기" 를 위해 Referer 를 리다이렉트 대상으로 쓴다.
 * Referer 는 요청 헤더라 값이 통제되지 않으므로, 그대로 붙이면 외부 사이트로
 * 보내는 오픈 리다이렉트가 된다.
 */
class SafeRefererTest {

    private static final String FALLBACK = "/test/manage/list";

    private String resolve(String referer) {
        MdTestManageController controller = new MdTestManageController(null, null, null);
        return (String) ReflectionTestUtils.invokeMethod(controller, "safeReferer", referer);
    }

    @Test
    @DisplayName("같은 사이트의 상대 경로는 그대로 사용한다")
    void allowsRelativePath() {
        assertThat(resolve("/test/manage/list")).isEqualTo("/test/manage/list");
        assertThat(resolve("/mypage?tab=tests")).isEqualTo("/mypage?tab=tests");
    }

    @Test
    @DisplayName("절대 URL 은 경로만 남긴다")
    void keepsOnlyPathFromAbsoluteUrl() {
        assertThat(resolve("https://moondap.com/mypage")).isEqualTo("/mypage");
        assertThat(resolve("https://moondap.com/test/list?page=2")).isEqualTo("/test/list?page=2");
    }

    @Test
    @DisplayName("[회귀] 외부 도메인으로 보내지 않는다")
    void rejectsExternalHost() {
        // 절대 URL 이면 호스트를 버리고 경로만 쓰므로 외부로 나갈 수 없다.
        assertThat(resolve("https://evil.example.com/phish")).isEqualTo("/phish");
    }

    @Test
    @DisplayName("[회귀] 프로토콜 상대 URL(//evil.com)을 차단한다")
    void rejectsProtocolRelativeUrl() {
        // "redirect://evil.com" 은 브라우저가 외부 사이트로 해석한다.
        assertThat(resolve("//evil.example.com/phish")).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("비어 있거나 경로가 아니면 기본 경로로 보낸다")
    void fallsBackOnUnusableValues() {
        assertThat(resolve(null)).isEqualTo(FALLBACK);
        assertThat(resolve("")).isEqualTo(FALLBACK);
        assertThat(resolve("   ")).isEqualTo(FALLBACK);
        assertThat(resolve("javascript:alert(1)")).isEqualTo(FALLBACK);
    }
}
