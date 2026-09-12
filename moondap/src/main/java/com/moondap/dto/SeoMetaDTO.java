package com.moondap.dto;

import lombok.Data;

/**
 * 페이지별 SEO 메타 정보.
 *
 * <p>이전에는 description·og:* 가 main_layout.html 에 하드코딩돼 있어서, 테스트를 몇 개
 * 만들든 검색엔진과 SNS 가 보는 설명문이 전부 동일했다. 같은 제목·같은 설명을 단
 * 페이지가 수백 개 쌓이면 중복 콘텐츠로 취급돼 색인에서 밀린다.
 *
 * <p>컨트롤러는 필요한 값만 채워 {@code model.addAttribute("seo", ...)} 로 넘기고,
 * 나머지 기본값(canonical·og:image·robots)은 {@code SeoMetaInterceptor} 가 채운다.
 *
 * @see com.moondap.config.SeoMetaInterceptor
 */
@Data
public class SeoMetaDTO {

    /** og:title. 비우면 사이트 기본 제목. &lt;title&gt; 태그는 각 템플릿이 따로 지정한다. */
    private String title;

    /** meta description 및 og:description. 공백 정리 후 160자로 잘린다. */
    private String description;

    /**
     * 정규 URL. 비우면 쿼리스트링을 제외한 현재 경로가 쓰인다.
     * 쿼리 파라미터가 콘텐츠를 결정하는 페이지(밸런스 게임 상세)는 직접 지정해야 한다.
     * "/" 로 시작하는 상대경로를 넣으면 인터셉터가 절대 URL 로 바꾼다.
     */
    private String canonical;

    /** og:image. 상대경로를 넣으면 인터셉터가 절대 URL 로 바꾼다. */
    private String image;

    /** robots 메타. 비우면 경로 규칙에 따라 index/noindex 가 자동 결정된다. */
    private String robots;

    /** og:type. 기본값 website. */
    private String ogType;

    public static SeoMetaDTO of(String title, String description) {
        SeoMetaDTO seo = new SeoMetaDTO();
        seo.setTitle(title);
        seo.setDescription(description);
        return seo;
    }
}
