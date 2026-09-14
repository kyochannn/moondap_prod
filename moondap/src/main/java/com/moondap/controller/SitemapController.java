package com.moondap.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.service.MdTestUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * sitemap.xml 을 DB 내용으로 생성한다.
 *
 * <p>이전에는 static/sitemap.xml 에 URL 5개가 하드코딩돼 있었다. 정작 콘텐츠인 개별
 * 심리테스트({@code /test/{testKey}})와 밸런스 게임 상세는 한 건도 들어 있지 않아서,
 * 크롤러 입장에서 이 사이트는 페이지 5개짜리 사이트였다. 콘텐츠를 아무리 등록해도
 * 색인되는 URL 이 늘지 않으니 '가치 없는 콘텐츠' 판정을 피할 수 없다.
 *
 * <p>정적 파일이 아니라 컨트롤러로 처리하므로 콘텐츠를 등록하는 즉시 반영된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final MdTestUserService mdTestUserService;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * 한 번에 담을 콘텐츠 수 상한. sitemap 규격상 URL 50,000개 / 50MB 가 한계이므로
     * 이 정도면 단일 파일로 충분하다. 넘어가면 sitemap index 로 분할해야 한다.
     */
    private static final int MAX_CONTENT_URLS = 5000;

    /** 콘텐츠와 무관하게 항상 포함되는 경로. priority 는 사이트 내 상대적 중요도다. */
    private static final String[][] STATIC_URLS = {
            {"/", "daily", "1.0"},
            {"/test/list", "daily", "0.9"},
            {"/balanceGame/selectBalanceGameListView", "daily", "0.9"},
            {"/egenTeto/selectEgenTetoGame", "weekly", "0.8"},
            {"/about", "monthly", "0.5"},
            {"/contact", "monthly", "0.4"},
            {"/partnership", "monthly", "0.4"},
            {"/privacy", "yearly", "0.3"},
            {"/terms", "yearly", "0.3"},
    };

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    public String sitemap() {

        String origin = StringUtils.trimTrailingCharacter(baseUrl, '/');

        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String[] entry : STATIC_URLS) {
            appendUrl(xml, origin + entry[0], null, entry[1], entry[2]);
        }

        // status='active' 인 콘텐츠만 반환한다(selectAllContentList 의 WHERE 절).
        // 초안·비공개 글이 sitemap 에 실려 404/403 을 받으면 크롤링 품질 점수가 깎인다.
        //
        // includeSpicy=true: 매운맛은 화면 목록에서만 빼고 색인에는 남긴다.
        // 검색으로 찾아오는 것 자체는 막을 이유가 없고, sitemap 에서 URL 을 빼면
        // 이미 색인된 페이지가 정리되는 게 아니라 크롤링만 뜸해진다.
        List<MdContentItemDTO> contents;
        try {
            contents = mdTestUserService.getAllContentList("all", "latest", "all", 0, MAX_CONTENT_URLS, true);
        } catch (Exception e) {
            // sitemap 은 크롤러만 보는 페이지다. 조회가 실패해도 정적 URL 이라도 내려준다.
            log.error("sitemap 콘텐츠 목록 조회 실패. 정적 URL 만 포함한다.", e);
            contents = List.of();
        }

        for (MdContentItemDTO item : contents) {
            String loc = toLocation(origin, item);
            if (loc == null) {
                continue;
            }
            appendUrl(xml, loc, toLastmod(item.getCreatedAt()), "weekly", "0.7");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    /**
     * 콘텐츠 종류별 공개 URL. EGEN 은 정적 URL 목록에 이미 있으므로 건너뛴다.
     */
    private String toLocation(String origin, MdContentItemDTO item) {
        String key = item.getKey();
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return switch (item.getType()) {
            case "NORMAL" -> origin + "/test/" + UriUtils.encodePathSegment(key, StandardCharsets.UTF_8);
            case "BALANCE" -> origin + "/balanceGame/selectBalanceGameView?id="
                    + UriUtils.encodeQueryParam(key, StandardCharsets.UTF_8);
            default -> null;
        };
    }

    /**
     * createdAt 은 매퍼에서 문자열로 넘어온다("2026-04-21 10:00:00" 형태).
     * sitemap 의 lastmod 는 W3C Datetime 이라 날짜 부분만 쓰면 된다.
     */
    private String toLastmod(String createdAt) {
        if (createdAt == null || createdAt.length() < 10) {
            return null;
        }
        String date = createdAt.substring(0, 10);
        return date.matches("\\d{4}-\\d{2}-\\d{2}") ? date : null;
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    /** loc 은 XML 텍스트 노드다. 인코딩된 URL 이라도 &amp; 등은 반드시 escape 해야 파일이 깨지지 않는다. */
    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
