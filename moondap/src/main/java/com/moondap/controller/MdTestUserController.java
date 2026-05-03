package com.moondap.controller;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.dto.MdTestDTO;
import com.moondap.service.MdTestCategoryService;
import com.moondap.service.MdTestUserService;
import com.moondap.service.StatService;
import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdTestResultDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        
        if (test == null) return "redirect:/";
        
        // 비공개/초안 상태일 경우 관리자나 작성자만 접근 가능
        if (!"active".equals(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new RuntimeException("해당 테스트에 접근할 권한이 없습니다.");
            }
        }
        
        model.addAttribute("test", test);
        model.addAttribute("isPreview", preview);
        return "test/intro";
    }

    /**
     * 질문지 페이지
     */
    @GetMapping("/{testKey}/questions")
    public String testQuestions(@PathVariable("testKey") String testKey, 
                                @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                                Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        
        if (test == null) return "redirect:/";

        // 비공개/초안 상태일 경우 관리자나 작성자만 접근 가능
        if (!"active".equals(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new RuntimeException("해당 테스트에 접근할 권한이 없습니다.");
            }
        }
        
        model.addAttribute("test", test);
        model.addAttribute("isPreview", preview);
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
        
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        if (test == null) return "redirect:/";
        
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
                        .filter(r -> {
                            try {
                                Long id = Long.parseLong(finalResultCode);
                                if (r.getId().equals(id)) return true;
                            } catch (NumberFormatException e) {}
                            return r.getResultTitle().equals(finalResultCode);
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
        if (!"active".equals(test.getStatus())) {
            if (!isAdminOrAuthor(test.getCreatedBy())) {
                throw new RuntimeException("해당 테스트의 결과에 접근할 권한이 없습니다.");
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

        return "test/result";
    }

    /**
     * 광고 노출 단계를 거쳤음을 세션에 기록
     */
    @PostMapping("/verify-ad")
    @ResponseBody
    public void verifyAd(jakarta.servlet.http.HttpSession session) {
        log.info("광고 단계 확인 완료 (세션 기록)");
        session.setAttribute("AD_VERIFIED", true);
    }

    private boolean isAdminOrAuthor(String createdBy) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || 
            auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return false;
        }
        
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        
        Object principal = auth.getPrincipal();
        if (principal instanceof PrincipalDetails) {
            String username = ((PrincipalDetails) principal).getUsername();
            return username.equals(createdBy);
        }
        return auth.getName().equals(createdBy);
    }
}
