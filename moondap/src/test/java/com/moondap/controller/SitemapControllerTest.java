package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.service.MdTestUserService;

/**
 * sitemap.xml 생성 검증.
 *
 * <p>이 파일은 크롤러만 읽기 때문에 눈으로 확인할 기회가 거의 없다. URL 이 빠지거나
 * XML 이 깨져도 한참 뒤에야 색인 누락으로 드러나므로 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class SitemapControllerTest {

    @Mock
    private MdTestUserService mdTestUserService;

    @InjectMocks
    private SitemapController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "baseUrl", "https://moondap.com");
    }

    private MdContentItemDTO content(String type, String key, String createdAt) {
        MdContentItemDTO item = new MdContentItemDTO();
        item.setType(type);
        item.setKey(key);
        item.setCreatedAt(createdAt);
        return item;
    }

    @Test
    @DisplayName("개별 심리테스트와 밸런스 게임 URL 이 모두 포함된다")
    void includesEveryContentUrl() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of(
                        content("NORMAL", "love-type", "2026-05-01 10:00:00"),
                        content("BALANCE", "BG000012", "2026-05-02 11:00:00")));

        String xml = controller.sitemap();

        // 콘텐츠 URL 이 빠져 있던 것이 '가치 없는 콘텐츠' 판정의 직접적 원인이었다.
        assertThat(xml).contains("<loc>https://moondap.com/test/love-type</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/balanceGame/selectBalanceGameView?id=BG000012</loc>");
        assertThat(xml).contains("<lastmod>2026-05-01</lastmod>");
    }

    @Test
    @DisplayName("콘텐츠가 없어도 고정 URL 은 내려준다")
    void alwaysIncludesStaticUrls() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        String xml = controller.sitemap();

        assertThat(xml).contains("<loc>https://moondap.com/</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/test/list</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/balanceGame/selectBalanceGameListView</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/egenTeto/selectEgenTetoGame</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/privacy</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/terms</loc>");
        assertThat(xml).endsWith("</urlset>\n");
    }

    @Test
    @DisplayName("콘텐츠 조회가 실패해도 sitemap 자체는 응답한다")
    void survivesContentLookupFailure() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("DB down"));

        String xml = controller.sitemap();

        // 500 을 반환하면 구글이 sitemap 을 오류로 기록한다. 정적 URL 만이라도 내려주는 편이 낫다.
        assertThat(xml).contains("<loc>https://moondap.com/</loc>");
        assertThat(xml).contains("</urlset>");
    }

    @Test
    @DisplayName("EGEN 은 고정 URL 에 이미 있으므로 중복 생성하지 않는다")
    void doesNotDuplicateEgenUrl() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of(content("EGEN", "selectEgenTetoGame", "2024-01-01 00:00:00")));

        String xml = controller.sitemap();

        assertThat(xml.split("<loc>https://moondap\\.com/egenTeto/selectEgenTetoGame</loc>", -1))
                .hasSize(2); // 구분자 1개 = 등장 1회
    }

    @Test
    @DisplayName("매운맛도 색인 대상이므로 includeSpicy=true 로 조회한다")
    void requestsSpicyContentForIndexing() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        controller.sitemap();

        // 매운맛은 화면 목록에서만 뺀다. sitemap 까지 빼면 이미 색인된 페이지가
        // 정리되는 게 아니라 크롤링만 뜸해진다.
        verify(mdTestUserService).getAllContentList("all", "latest", "all", 0, 5000, true);
    }

    @Test
    @DisplayName("key 나 createdAt 이 비어 있어도 XML 이 깨지지 않는다")
    void toleratesIncompleteRows() {
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of(
                        content("NORMAL", null, "2026-05-01 10:00:00"),
                        content("NORMAL", "no-date", null)));

        String xml = controller.sitemap();

        assertThat(xml).doesNotContain("<loc>https://moondap.com/test/null</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/test/no-date</loc>");
        // lastmod 는 선택 항목이다. 빈 태그를 넣으면 sitemap 검증에서 오류가 난다.
        assertThat(xml).doesNotContain("<lastmod></lastmod>");
    }
}
