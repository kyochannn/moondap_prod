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
    public String testIntro(@PathVariable("testKey") String testKey, Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        if (test == null || !"active".equals(test.getStatus())) {
            return "redirect:/"; // 비활성 상태거나 없는 경우 메인으로
        }
        model.addAttribute("test", test);
        return "test/intro";
    }

    /**
     * 질문지 페이지
     */
    @GetMapping("/{testKey}/questions")
    public String testQuestions(@PathVariable("testKey") String testKey, Model model) {
        MdTestDTO test = mdTestUserService.getFullTestData(testKey);
        if (test == null) return "redirect:/";
        
        model.addAttribute("test", test);
        return "test/questions";
    }

    /**
     * 답변 제출 및 결과 계산
     */
    @PostMapping("/{testKey}/submit")
    public String submitAnswers(@PathVariable("testKey") String testKey,
                                @RequestParam("answers") String answersJson,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            MdTestDTO test = mdTestUserService.getFullTestData(testKey);
            if (test == null) throw new IllegalArgumentException("존재하지 않는 테스트입니다.");

            List<Integer> answers = objectMapper.readValue(answersJson, new TypeReference<List<Integer>>() {});
            MdTestResultDTO result = mdTestUserService.calculateResult(test.getId(), answers);

            request.getSession().setAttribute("currentTestResult", result);
            request.getSession().setAttribute("currentTestInfo", test);

            return "redirect:/test/" + testKey + "/result";
        } catch (Exception e) {
            log.error("테스트 처리 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "처리 중 오류가 발생했습니다.");
            return "redirect:/test/" + testKey;
        }
    }

    /**
     * 결과 페이지
     */
    @GetMapping("/{testKey}/result")
    public String testResult(@PathVariable("testKey") String testKey, HttpServletRequest request, Model model) {
        MdTestResultDTO result = (MdTestResultDTO) request.getSession().getAttribute("currentTestResult");
        MdTestDTO test = (MdTestDTO) request.getSession().getAttribute("currentTestInfo");

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
