package com.moondap.controller;

import com.moondap.common.exception.ContentNotFoundException;
import com.moondap.common.exception.UserMessageException;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.SeoMetaDTO;
import com.moondap.service.MdTestCategoryService;
import com.moondap.service.MdTestUserService;
import com.moondap.service.StatService;
import com.moondap.common.SecurityUtil;
import com.moondap.dto.MdTestResultDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/test")
@RequiredArgsConstructor
public class MdTestUserController {

    private final MdTestUserService mdTestUserService;
    private final MdTestCategoryService mdTestCategoryService;
    private final StatService statService;
    private final ObjectMapper objectMapper;

    /**
     * 테스트 목록 페이지
     */
    @GetMapping("/list")
    public String list(@RequestParam(value = "category", required = false, defaultValue = "all") String category,
                       @RequestParam(value = "sort", required = false, defaultValue = "popular") String sort,
                       @RequestParam(value = "type", required = false, defaultValue = "all") String type,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       HttpServletRequest request,
                       Model model) {
        
        int limit = 6;
        int offset = page * limit;
        
        List<MdContentItemDTO> contentList = mdTestUserService.getAllContentList(category, sort, type, offset, limit + 1);
        List<MdTestCategoryDTO> categories = mdTestCategoryService.getActiveCategories();
        
        boolean hasMore = contentList.size() > limit;
        if (hasMore) {
            contentList = contentList.subList(0, limit);
        }
        
        model.addAttribute("contentList", contentList);
        model.addAttribute("categories", categories);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentType", type);
        model.addAttribute("currentPage", page);
        model.addAttribute("hasMore", hasMore);

        // 카테고리·정렬·페이지는 같은 문서를 다르게 보여줄 뿐이므로 canonical 은 항상 /test/list 다.
        // (SeoMetaInterceptor 가 쿼리스트링을 떼고 기본 canonical 을 만든다.)
        model.addAttribute("seo", SeoMetaDTO.of(
                "심리테스트 · 밸런스 게임 전체 목록 - 문답",
                "문답의 모든 심리테스트와 밸런스 게임을 카테고리별로 모았습니다. 인기순·최신순으로 골라 바로 참여해 보세요."));

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            if ("true".equals(request.getHeader("X-Load-More"))) {
                return "test/list :: #content-grid-items";
            }
            return "test/list :: #content-grid";
        }
        
        return "test/list";
    }

    /**
     * 테스트 랜딩 페이지 (Intro)
     */
    @GetMapping("/{testKey}")
    public String testIntro(@PathVariable("testKey") String testKey, 
                            @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                            Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        
        if (test == null) {
            throw new ContentNotFoundException("요청하신 테스트를 찾을 수 없습니다.");
        }
        
        // 비공개/초안 상태일 경우 관리자나 작성자만 접근 가능
        if (!isPublished(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new UserMessageException("해당 테스트에 접근할 권한이 없습니다.");
            }
        }

        model.addAttribute("test", test);
        // preview 는 요청 파라미터라 누구나 붙일 수 있다. 권한이 있을 때만 인정한다.
        model.addAttribute("isPreview", preview && isAdminOrAuthor(test.getCreatedBy()));

        // 테스트마다 제목·설명·썸네일이 달라야 검색 결과와 SNS 공유 카드가 구분된다.
        SeoMetaDTO seo = SeoMetaDTO.of(test.getTitle() + " - 문답", test.getDescription());
        seo.setImage(thumbnailUrl(test.getThumbnailImage()));
        seo.setOgType("article");
        model.addAttribute("seo", seo);

        return "test/intro";
    }

    /**
     * 공개 상태인지 여부.
     *
     * <p>대소문자를 구분하지 않는다. 매퍼의 {@code WHERE status = 'active'} 는 MySQL
     * 콜레이션상 'ACTIVE' 도 통과시키므로, 여기서만 구분하면 목록에는 보이는데 상세만
     * 막히는 상태가 된다. 실제로 status 가 'ACTIVE' 로 들어간 행 때문에 목록의 카드를
     * 눌렀을 때 오류 페이지가 떴다.
     */
    private boolean isPublished(String status) {
        return "active".equalsIgnoreCase(status);
    }

    /** 업로드 썸네일의 공개 경로. 없거나 기본 이미지면 공통 대체 이미지를 쓴다. */
    private String thumbnailUrl(String thumbnailImage) {
        if (thumbnailImage == null || thumbnailImage.isBlank()
                || "default-content-img.png".equals(thumbnailImage)) {
            return "/assets/img/default-img/default-content-img.png";
        }
        return "/uploads/" + thumbnailImage;
    }

    /**
     * 질문지 페이지
     */
    @GetMapping("/{testKey}/questions")
    public String testQuestions(@PathVariable("testKey") String testKey, 
                                @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                                Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        
        if (test == null) {
            throw new ContentNotFoundException("요청하신 테스트를 찾을 수 없습니다.");
        }

        // 비공개/초안 상태일 경우 관리자나 작성자만 접근 가능
        if (!isPublished(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new UserMessageException("해당 테스트에 접근할 권한이 없습니다.");
            }
        }
        
        model.addAttribute("test", test);
        // 이 값이 결과 폼의 preview 파라미터로 전달되므로 여기서도 권한을 확인한다.
        model.addAttribute("isPreview", preview && isAdminOrAuthor(test.getCreatedBy()));
        return "test/questions";
    }

    /**
     * 결과 페이지 (POST: 테스트 직후 / GET: 공유 링크)
     */
    @RequestMapping(value = "/{testKey}/result", method = {RequestMethod.GET, RequestMethod.POST})
    public String testResult(@PathVariable("testKey") String testKey,
                             @RequestParam(value = "resultCode", required = false) String resultCode,
                             @RequestParam(value = "score", required = false) Integer score,
                             @RequestParam(value = "answers", required = false) String answersJson,
                             @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                             HttpServletRequest request,
                             jakarta.servlet.http.HttpSession session,
                             Model model) throws Exception {
        
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        if (test == null) {
            throw new ContentNotFoundException("요청하신 테스트를 찾을 수 없습니다.");
        }

        // [보안] preview 는 요청 파라미터라 누구나 붙일 수 있다.
        // 관리자·작성자가 아니면 무시한다. 이전에는 ?preview=true 만 붙이면
        // 광고 단계 검증과 참여수 집계를 모두 건너뛸 수 있었다.
        preview = preview && isAdminOrAuthor(test.getCreatedBy());

        // [광고 검증] POST 요청(테스트 완료) 시 세션 체크
        if ("POST".equalsIgnoreCase(request.getMethod()) && !preview) {
            Boolean adVerified = (Boolean) session.getAttribute("AD_VERIFIED");
            if (adVerified == null || !adVerified) {
                log.warn("광고 단계를 거치지 않은 비정상적 접근 차단: {}", testKey);
                return "redirect:/test/" + testKey;
            }
            // 검증 완료 후 세션에서 제거 (일회성)
            session.removeAttribute("AD_VERIFIED");
        }

        MdTestResultDTO matchedResult = null;

        // 1. POST 방식: 방금 테스트를 마친 경우 (계산 수행)
        if ("POST".equalsIgnoreCase(request.getMethod()) && answersJson != null) {
            try {
                List<Integer> answers = objectMapper.readValue(answersJson, new TypeReference<List<Integer>>() {});
                matchedResult = mdTestUserService.calculateResult(test.getId(), answers);
                
                if (!preview) {
                    mdTestUserService.incrementPlayCount(test.getId());
                    // 오늘 콘텐츠 참여 수 증가
                    statService.incrementParticipationCount();
                }
                
                // resultCode 세팅 (공유 URL용)
                if (matchedResult != null) {
                    resultCode = String.valueOf(matchedResult.getId());
                    score = matchedResult.getCalculatedScore();
                }
            } catch (Exception e) {
                log.error("결과 계산 오류", e);
            }
        } 
        
        // 2. GET 방식 또는 POST 계산 실패: 파라미터로 결과 찾기 (공유 링크 등)
        if (matchedResult == null && resultCode != null) {
            if (test.getResults() != null) {
                String finalResultCode = resultCode;
                matchedResult = test.getResults().stream()
                        // 결과의 id/제목이 null 인 데이터가 하나만 있어도
                        // 예전에는 결과 페이지 전체가 500 으로 떨어졌다.
                        .filter(r -> {
                            try {
                                Long id = Long.parseLong(finalResultCode);
                                if (java.util.Objects.equals(r.getId(), id)) return true;
                            } catch (NumberFormatException e) {}
                            return java.util.Objects.equals(r.getResultTitle(), finalResultCode);
                        })
                        .findFirst()
                        .orElse(test.getResults().isEmpty() ? null : test.getResults().get(0));
                
                if (matchedResult != null && score != null) {
                    matchedResult.setCalculatedScore(score);
                }
            }
        }

        if (matchedResult == null) return "redirect:/test/" + testKey;

        // 결과 조회 시에도 권한 체크 (공유된 링크 등을 통한 우회 방지)
        if (!isPublished(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new UserMessageException("해당 테스트의 결과에 접근할 권한이 없습니다.");
            }
        }

        model.addAttribute("test", test);
        model.addAttribute("result", matchedResult);
        model.addAttribute("resultCode", resultCode);
        model.addAttribute("isPreview", preview);

        // 공유용 URL 생성
        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
        String shareUrl = baseUrl + "/test/" + testKey + "/result?resultCode=" + java.net.URLEncoder.encode(resultCode, "UTF-8");
        if (score != null) {
            shareUrl += "&score=" + score;
        }
        model.addAttribute("shareUrl", shareUrl);

        // 추천 콘텐츠 데이터 추가 (인기 테스트 3개, 인기 밸런스 게임 3개)
        List<MdContentItemDTO> popularNormalTests = mdTestUserService.getAllContentList("all", "popular", "NORMAL", 0, 3);
        List<MdContentItemDTO> popularBalanceTests = mdTestUserService.getAllContentList("all", "popular", "BALANCE", 0, 3);
        model.addAttribute("popularNormalTests", popularNormalTests);
        model.addAttribute("popularBalanceTests", popularBalanceTests);

        return "test/result";
    }

    /**
     * 광고 노출 단계를 거쳤음을 세션에 기록
     */
    @PostMapping("/verify-analysis")
    @ResponseBody
    public void verifyAd(jakarta.servlet.http.HttpSession session) {
        log.info("광고 단계 확인 완료 (세션 기록)");
        session.setAttribute("AD_VERIFIED", true);
    }

    private boolean isAdminOrAuthor(String createdBy) {
        return SecurityUtil.isAdminOrOwner(createdBy);
    }
}
