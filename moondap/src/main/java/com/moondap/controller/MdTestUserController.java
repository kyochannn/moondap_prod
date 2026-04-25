package com.moondap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.service.MdTestUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/test")
@RequiredArgsConstructor
public class MdTestUserController {

    private final MdTestUserService mdTestUserService;
    private final ObjectMapper objectMapper;

    /**
     * 테스트 랜딩 페이지 (Intro)
     */
    @GetMapping("/{testKey}")
    public String testIntro(@PathVariable("testKey") String testKey, 
                            @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                            Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        
        // 데이터가 없으면 리다이렉트
        if (test == null) return "redirect:/";
        
        // 미리보기 모드가 아니고 상태가 active가 아니면 리다이렉트
        if (!preview && !"active".equals(test.getStatus())) {
            return "redirect:/";
        }
        
        model.addAttribute("test", test);
        model.addAttribute("isPreview", preview); // 화면에서 미리보기 배지 등을 띄울 때 사용 가능
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
        
        // 미리보기 모드가 아니고 상태가 active가 아니면 리다이렉트
        if (!preview && !"active".equals(test.getStatus())) {
            return "redirect:/test/" + testKey; // 인트로로 보내서 거기서 다시 체크하게 함
        }
        
        model.addAttribute("test", test);
        model.addAttribute("isPreview", preview);
        return "test/questions";
    }

    /**
     * 답변 제출 및 결과 계산
     */
    @PostMapping("/{testKey}/submit")
    public String submitAnswers(@PathVariable("testKey") String testKey,
                                @RequestParam("answers") String answersJson,
                                @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            MdTestDTO test = mdTestUserService.getFullTestData(testKey);
            if (test == null) throw new IllegalArgumentException("존재하지 않는 테스트입니다.");

            List<Integer> answers = objectMapper.readValue(answersJson, new TypeReference<List<Integer>>() {});
            MdTestResultDTO result = mdTestUserService.calculateResult(test.getId(), answers);
            
            // 미리보기 모드가 아닐 때만 참여 수 증가
            if (!preview) {
                mdTestUserService.incrementPlayCount(test.getId());
            }

            request.getSession().setAttribute("currentTestResult", result);
            request.getSession().setAttribute("currentTestInfo", test);

            // 미리보기 모드일 경우 결과 페이지 리다이렉트 시 파라미터 유지
            String redirectUrl = "redirect:/test/" + testKey + "/result";
            if (preview) {
                redirectUrl += "?preview=true";
            }
            return redirectUrl;
        } catch (Exception e) {
            log.error("테스트 처리 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "처리 중 오류가 발생했습니다.");
            return "redirect:/test/" + testKey + (preview ? "?preview=true" : "");
        }
    }

    /**
     * 결과 페이지
     */
    @GetMapping("/{testKey}/result")
    public String testResult(@PathVariable("testKey") String testKey, 
                             @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                             HttpServletRequest request, Model model) {
        MdTestResultDTO result = (MdTestResultDTO) request.getSession().getAttribute("currentTestResult");
        MdTestDTO test = (MdTestDTO) request.getSession().getAttribute("currentTestInfo");
        
        model.addAttribute("isPreview", preview);

        if (result == null || test == null) {
            return "redirect:/test/" + testKey;
        }

        model.addAttribute("result", result);
        model.addAttribute("test", test);
        
        // 공유 URL 등 추가 정보
        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
        model.addAttribute("shareUrl", baseUrl + "/test/" + testKey);

        return "test/result";
    }
}
